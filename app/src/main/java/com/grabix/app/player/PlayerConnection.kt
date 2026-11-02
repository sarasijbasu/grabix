package com.grabix.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.grabix.app.GrabixApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Something the built-in player can play. */
data class PlayItem(
    val uri: String,
    val title: String,
    val artist: String? = null,
    val isVideo: Boolean,
    val artworkUri: String? = null,
    /** Web page the file was downloaded from, shown as "Open source" in the player. */
    val sourceUrl: String? = null,
) {
    companion object {
        const val EXTRA_SOURCE = "grabix.source"

        fun fromFile(path: String, title: String, artist: String?, isVideo: Boolean, thumb: String?, source: String? = null) =
            PlayItem(Uri.fromFile(File(path)).toString(), title, artist, isVideo, thumb, source)
    }
}

/**
 * App-wide connection to [PlaybackService]. All screens share one player so
 * music keeps playing while browsing, and a mini-player can show what is on.
 */
object PlayerConnection {
    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt
    private var sleepJob: Job? = null

    private val positions by lazy {
        GrabixApp.instance.getSharedPreferences("play_positions", Context.MODE_PRIVATE)
    }

    private fun connect(context: Context): ListenableFuture<MediaController> {
        future?.let { return it }
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                _controller.value = c
                c.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = restorePosition(c)
                })
            }.onFailure { future = null }
        }, ContextCompat.getMainExecutor(app))
        return f
    }

    /**
     * Drops the app's connection to [PlaybackService]. A bound controller keeps the service
     * alive, so this must run when the app is closed from recents.
     */
    fun release() {
        sleepJob?.cancel()
        _sleepEndsAt.value = 0
        _controller.value?.release()
        _controller.value = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    suspend fun get(context: Context): MediaController = withContext(Dispatchers.Main) { connect(context).await() }

    private fun PlayItem.toMediaItem(): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDisplayTitle(title)
            .setMediaType(if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
            .apply { sourceUrl?.let { setExtras(android.os.Bundle().apply { putString(PlayItem.EXTRA_SOURCE, it) }) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri)
            .setMediaMetadata(meta)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build())
            .build()
    }

    /** Replaces the queue and starts playing. Opens the full-screen player when [openScreen]. */
    suspend fun play(context: Context, items: List<PlayItem>, startIndex: Int = 0, openScreen: Boolean = true) {
        if (items.isEmpty()) return
        val c = get(context)
        withContext(Dispatchers.Main) {
            c.currentMediaItem?.let { savePosition(c) }
            val start = startIndex.coerceIn(0, items.lastIndex)
            val startPos = positions.getLong(items[start].uri, 0L)
            c.setMediaItems(items.map { it.toMediaItem() }, start, startPos)
            c.prepare()
            c.play()
            if (openScreen) {
                context.startActivity(
                    Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun savePosition(c: Player) {
        val id = c.currentMediaItem?.mediaId ?: return
        savePosition(id, c.currentPosition, c.duration)
    }

    /** Remembers where [id] was left so it can resume there next time. [dur] <= 0 means unknown. */
    fun savePosition(id: String, pos: Long, dur: Long) {
        val e = positions.edit()
        // Forget the position when (almost) finished, or for short clips.
        if (dur > 0 && (pos > dur - 10_000 || dur < 60_000)) e.remove(id) else if (pos > 5_000) e.putLong(id, pos)
        e.apply()
        GrabixApp.instance.appScope.launch(Dispatchers.IO) {
            runCatching { GrabixApp.instance.db.history().updatePosition(id, if (pos > 5_000 && !(dur > 0 && pos > dur - 10_000)) pos else 0L, dur.coerceAtLeast(0L)) }
        }
    }

    /** Adds / refreshes an entry in the play history. */
    fun recordHistory(item: MediaItem) {
        val id = item.mediaId
        if (id.isEmpty()) return
        val md = item.mediaMetadata
        val entry = com.grabix.app.data.HistoryEntity(
            uri = id,
            title = md.title?.toString() ?: md.displayTitle?.toString() ?: "Unknown",
            artist = md.artist?.toString(),
            isVideo = md.mediaType != MediaMetadata.MEDIA_TYPE_MUSIC,
            artworkUri = md.artworkUri?.toString(),
            lastPlayedAt = System.currentTimeMillis(),
            positionMs = positions.getLong(id, 0L),
        )
        GrabixApp.instance.appScope.launch(Dispatchers.IO) {
            runCatching { GrabixApp.instance.db.history().upsert(entry) }
        }
    }

    private fun restorePosition(c: Player) {
        val id = c.currentMediaItem?.mediaId ?: return
        val saved = positions.getLong(id, 0L)
        if (saved > 5_000 && c.currentPosition < 2_000) c.seekTo(saved)
    }

    /** Pauses playback after [minutes]. Pass 0 to cancel. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepEndsAt.value = 0; return }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepEndsAt.value = endsAt
        sleepJob = GrabixApp.instance.appScope.launch {
            delay(minutes * 60_000L)
            withContext(Dispatchers.Main) { _controller.value?.pause() }
            _sleepEndsAt.value = 0
        }
    }
}
