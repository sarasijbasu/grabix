package com.grabix.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Status {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val WAITING_WIFI = "WAITING_WIFI"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
}

object Kind {
    const val VIDEO = "VIDEO"
    const val AUDIO = "AUDIO"
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val mediaId: String? = null,
    val title: String,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val durationSec: Long = 0,
    val site: String? = null,
    val kind: String = Kind.VIDEO,
    /** yt-dlp -f selector */
    val formatSpec: String,
    /** Human label such as "1080p" or "MP3 320kbps" */
    val qualityLabel: String,
    /** mp3 / m4a / opus / null for video */
    val audioFormat: String? = null,
    val audioBitrate: String? = null,
    val embedSubs: Boolean = false,
    val status: String = Status.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = 0,
    val speed: String? = null,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val estimatedSize: Long = 0,
    val error: String? = null,
    val cookieFile: String? = null,
    val inVault: Boolean = false,
    /** Sub-folder (inside the Video / Music folder) used for playlist downloads. */
    val subDir: String? = null,
    /** yt-dlp info JSON saved while the link was analysed; lets the download skip a second page fetch. */
    val infoJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
) {
    val isActive: Boolean
        get() = status == Status.QUEUED || status == Status.RUNNING || status == Status.WAITING_WIFI
}

/** One row per file the user has played; updated every time it is played again. */
@Entity(tableName = "play_history")
data class HistoryEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String? = null,
    val isVideo: Boolean,
    val artworkUri: String? = null,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId")]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val artist: String? = null,
    val isVideo: Boolean,
    val position: Int,
)
