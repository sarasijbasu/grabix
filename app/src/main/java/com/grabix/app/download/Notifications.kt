package com.grabix.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.grabix.app.MainActivity
import com.grabix.app.R

object Notifications {
    const val CH_PROGRESS = "downloads_progress"
    const val CH_DONE = "downloads_done"
    const val ID_FOREGROUND = 900 // must not equal Media3 default (1001) or finished ids (2000+)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Active downloads", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(context: Context, tab: String = "downloads"): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun foreground(context: Context, running: Int, queued: Int, title: String?, percent: Int): android.app.Notification {
        val text = buildString {
            append("$running downloading")
            if (queued > 0) append(" · $queued waiting")
        }
        val pauseAll = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, DownloadActionReceiver::class.java).setAction(DownloadActionReceiver.ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: "Preparing downloads")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setContentIntent(openApp(context))
            .addAction(0, "Pause all", pauseAll)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun finished(context: Context, id: Long, title: String, ok: Boolean, error: String? = null) {
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ok) "Downloaded" else "Download failed")
            .setContentText(if (ok) title else "$title — ${error?.take(80) ?: "error"}")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((2000 + id).toInt(), n) }
    }
}
