package com.grabix.app.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.grabix.app.GrabixApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the single ExoPlayer instance. Running as a MediaSessionService gives
 * background playback, lock-screen / notification controls and headset buttons.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** "Play next automatically" from settings; off = stop at the end of each item. */
    private var autoNext = true

    @OptIn(UnstableApi::class)
    private fun applyAutoNext() {
        val p = exo ?: return
        // Repeat-one keeps looping the same item even when auto-next is off.
        p.setPauseAtEndOfMediaItems(!autoNext && p.repeatMode != Player.REPEAT_MODE_ONE)
    }

    /** While playing, remember the position every few seconds so a resume is never far off. */
    private val saver = object : Runnable {
        override fun run() {
            saveNow()
            handler.postDelayed(this, 4_000)
        }
    }

    private fun saveNow() {
        val p = session?.player ?: return
        val item = p.currentMediaItem ?: return
        PlayerConnection.savePosition(item.mediaId, p.currentPosition, p.duration.let { if (it == C.TIME_UNSET) 0L else it })
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            GrabixApp.instance.settings.flow.map { it.autoPlayNext }.distinctUntilChanged().collect {
                autoNext = it
                applyAutoNext()
            }
        }
        ensureSession()
    }

    /** Creates the player + session, also after they were released by [onTaskRemoved]. */
    @OptIn(UnstableApi::class)
    private fun ensureSession(): MediaSession {
        session?.let { return it }
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player
        applyAutoNext()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(saver)
                if (isPlaying) handler.postDelayed(saver, 4_000) else saveNow()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { PlayerConnection.recordHistory(it) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) = applyAutoNext()

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                // Moving to another track: remember where the previous one was left.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SKIP) {
                    val prev = oldPosition.mediaItem ?: return
                    val finished = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    // A track that played to its end is forgotten (dur = 1 makes it count as finished).
                    PlayerConnection.savePosition(prev.mediaId, oldPosition.positionMs, if (finished) 1L else 0L)
                }
            }
        })

        val openPlayer = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val built = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer)
            .setCallback(object : MediaSession.Callback {
                // Make sure every item handed to the session has a playable URI.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration != null) item
                        else {
                            val uri = item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId)
                            item.buildUpon().setUri(uri).build()
                        }
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
        session = built
        return built
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = ensureSession()

    /**
     * The app was swiped away from recents ("Clear all"): stop playback, remove the media
     * notification and let the service end. Background play only applies while the app is
     * still in recents (Home button, screen off).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveNow()
        handler.removeCallbacks(saver)
        session?.player?.let { p ->
            p.pause()
            p.stop()
        }
        // A bound controller keeps the service alive: drop ours, and release the session so
        // System UI / Bluetooth controllers are disconnected too (media3 docs, onTaskRemoved).
        PlayerConnection.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        exo = null
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saver)
        saveNow()
        session?.run {
            player.release()
            release()
        }
        session = null
        exo = null
        scope.cancel()
        super.onDestroy()
    }
}
