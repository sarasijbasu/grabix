package com.grabix.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.grabix.app.MainActivity
import com.grabix.app.R
import com.grabix.app.ui.screens.BrowserHolder

/**
 * Keeps the app alive while a web page is playing audio/video, so playback in the in-app
 * browser continues after you leave the app or lock the screen.
 */
class BrowserAudioService : Service() {

    companion object {
        private const val CHANNEL = "browser_audio"
        private const val NOTIFICATION_ID = 1900
        private const val ACTION_PAUSE = "com.grabix.app.BROWSER_PAUSE"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, BrowserAudioService::class.java).putExtra(EXTRA_TITLE, title)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BrowserAudioService::class.java)) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** App closed from recents: stop the web page's sound and go away. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        BrowserHolder.current?.get()?.pauseMedia()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            BrowserHolder.current?.get()?.pauseMedia()
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Browser" }
        goForeground(title)
        return START_NOT_STICKY
    }

    private fun goForeground(title: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Browser playback", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "browser")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pause = PendingIntent.getService(
            this, 11,
            Intent(this, BrowserAudioService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Playing in Grabix browser")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Pause", pause)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }
}
