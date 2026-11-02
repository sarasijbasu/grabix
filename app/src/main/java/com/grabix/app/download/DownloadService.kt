package com.grabix.app.download

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.grabix.app.GrabixApp
import com.grabix.app.data.DownloadEntity
import com.grabix.app.data.Status
import com.grabix.app.engine.Engine
import com.grabix.app.util.Net
import com.grabix.app.util.Storage
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that runs the download queue in the background, with
 * configurable parallel downloads, Wi-Fi-only mode, pause/resume and retry.
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private val jobs = ConcurrentHashMap<Long, Job>()
        private val progressText = ConcurrentHashMap<Long, Pair<String, Int>>()

        fun activeIds(): Set<Long> = jobs.keys.toSet()

        fun start(context: Context) {
            val i = Intent(context, DownloadService::class.java)
            runCatching { ContextCompat.startForegroundService(context, i) }
                .onFailure { Log.w(TAG, "Could not start service", it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground(0, 0, null, 0)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Grabix:downloads")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(jobs.size, 0, null, 0)
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        // Not sticky: once the app is closed from recents Android must not bring the queue back
        // by itself. Unfinished items resume the next time Grabix is opened.
        return START_NOT_STICKY
    }

    /** App swiped away from recents: stop unless the user wants downloads to finish in the background. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scope.launch {
            if (!GrabixApp.instance.settings.current().downloadAfterClose) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { stopSelfSafely() }
            }
        }
    }

    private fun goForeground(running: Int, queued: Int, title: String?, pct: Int) {
        val n = Notifications.foreground(this, running, queued, title, pct)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.ID_FOREGROUND, n, type)
    }

    private suspend fun runLoop() {
        val app = GrabixApp.instance
        val dao = app.db.downloads()
        if (!Engine.awaitReady()) {
            dao.pending().forEach { dao.setStatus(it.id, Status.FAILED, "Download engine failed to start") }
            stopSelfSafely()
            return
        }
        var idleTicks = 0
        while (scope.isActive) {
            val settings = app.settings.current()
            val pending = dao.pending()
            val wifiOk = !settings.wifiOnly || Net.isUnmetered(this)

            for (task in pending) {
                if (jobs.size >= settings.maxConcurrent) break
                if (jobs.containsKey(task.id)) continue
                if (!wifiOk) {
                    if (task.status != Status.WAITING_WIFI) dao.setStatus(task.id, Status.WAITING_WIFI, null)
                    continue
                }
                // Register before starting so a fast-failing task can't remove its entry
                // first and leave a stale job permanently occupying a download slot.
                val job = scope.launch(start = CoroutineStart.LAZY) { runTask(task) }
                jobs[task.id] = job
                job.start()
            }

            refreshNotification(pending.count { !jobs.containsKey(it.id) })

            if (jobs.isEmpty() && pending.isEmpty()) {
                idleTicks++
                if (idleTicks >= 3 && dao.countUnfinished() == 0) break
            } else idleTicks = 0
            delay(1000)
        }
        stopSelfSafely()
    }

    private fun refreshNotification(queued: Int) {
        val first = progressText.values.firstOrNull()
        val n = Notifications.foreground(this, jobs.size, queued, first?.first, first?.second ?: 0)
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.ID_FOREGROUND, n) }
    }

    private suspend fun runTask(task: DownloadEntity) {
        val app = GrabixApp.instance
        val dao = app.db.downloads()
        val settings = app.settings.current()
        val outDir = Storage.outputDir(this, settings, task.kind, task.subDir)
        val startedAt = System.currentTimeMillis()
        dao.setStatus(task.id, Status.RUNNING, null)
        progressText[task.id] = task.title to 0

        var lastWrite = 0L
        var lastPct = task.progress
        val speedRegex = Regex("""at\s+([\d.]+\s?[KMG]i?B/s)""")
        val onProgress: (Float, Long, String) -> Unit = { pct, eta, line ->
            val phase = Engine.phaseOf(line)
            val now = System.currentTimeMillis()
            if (pct >= 0f) lastPct = pct.coerceIn(0f, 100f)
            // Post-processing lines are always shown so the user sees what is happening after 100%.
            if (phase != null || now - lastWrite > 600) {
                lastWrite = now
                val label = phase ?: speedRegex.find(line)?.groupValues?.get(1)
                val p = if (phase != null) 100f else lastPct
                progressText[task.id] = (if (phase != null) "${task.title} · $phase" else task.title) to p.toInt()
                scope.launch { dao.updateProgress(task.id, p, if (phase != null) 0 else eta, label) }
            }
        }
        try {
            val usedInfo = Engine.usableInfoJson(task.infoJson) != null
            val out = try {
                Engine.download(this, task, settings, outDir, task.id.toString(), useInfoJson = true, onProgress = onProgress)
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (t: Throwable) {
                // The saved page may be stale; fall back to a normal fetch once.
                if (!usedInfo || dao.get(task.id)?.status != Status.RUNNING) throw t
                Log.i(TAG, "Saved info failed for ${task.id}, retrying with the link", t)
                Engine.download(this, task, settings, outDir, task.id.toString(), useInfoJson = false, onProgress = onProgress)
            }
            val file = Engine.locateOutput(out, outDir, task.mediaId, startedAt)
                ?: throw IllegalStateException("Finished, but the output file was not found")
            Storage.scan(this, file.absolutePath)
            task.infoJson?.let { runCatching { File(it).delete() } }
            val latest = dao.get(task.id) ?: return
            dao.update(
                latest.copy(
                    status = Status.DONE, progress = 100f, etaSec = 0, speed = null,
                    filePath = file.absolutePath, fileSize = file.length(),
                    completedAt = System.currentTimeMillis(), error = null, infoJson = null,
                )
            )
            Notifications.finished(this, task.id, task.title, ok = true)
        } catch (e: YoutubeDL.CanceledException) {
            // Paused or canceled by the user; status already set by the repository.
        } catch (t: Throwable) {
            val current = dao.get(task.id)
            if (current != null && current.status == Status.RUNNING) {
                val msg = cleanError(t.message)
                dao.setStatus(task.id, Status.FAILED, msg)
                Notifications.finished(this, task.id, task.title, ok = false, error = msg)
            }
            Log.w(TAG, "Task ${task.id} failed", t)
        } finally {
            jobs.remove(task.id)
            progressText.remove(task.id)
        }
    }

    private fun cleanError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown error"
        val line = raw.lineSequence().lastOrNull { it.contains("ERROR") } ?: raw.lineSequence().last()
        return line.substringAfter("ERROR:").trim().take(300).ifBlank { "Unknown error" }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Mark anything still running as queued so it resumes next time.
        val running = jobs.keys.toList()
        running.forEach { Engine.cancel(it.toString()) }
        if (running.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                running.forEach { id ->
                    val d = GrabixApp.instance.db.downloads().get(id)
                    if (d?.status == Status.RUNNING) GrabixApp.instance.db.downloads().setStatus(id, Status.QUEUED, null)
                }
            }
        }
        jobs.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}

/** Handles notification buttons. */
class DownloadActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PAUSE_ALL = "com.grabix.app.PAUSE_ALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PAUSE_ALL) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { DownloadRepository.pauseAll() } finally { pending.finish() }
            }
        }
    }
}

@Suppress("unused")
private fun File.safeName() = name
