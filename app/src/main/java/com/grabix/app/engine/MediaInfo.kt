package com.grabix.app.engine

/** One selectable video quality. */
data class VideoOption(
    val height: Int,
    val label: String,
    val fps: Int,
    val estimatedBytes: Long,
    val formatSpec: String,
)

/** One selectable audio-only output. */
data class AudioOption(
    val label: String,
    val format: String,   // mp3, m4a, opus, best
    val bitrate: String,  // "320", "192", "128", "0" (best)
    val estimatedBytes: Long,
)

/** A flat entry inside a playlist / channel / search result. */
data class Entry(
    val id: String?,
    val url: String,
    val title: String,
    val durationSec: Long,
    val thumbnail: String?,
    val uploader: String?,
)

data class MediaInfo(
    val id: String?,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnail: String?,
    val site: String?,
    val isPlaylist: Boolean,
    val entries: List<Entry>,
    val videoOptions: List<VideoOption>,
    val audioOptions: List<AudioOption>,
    val subtitleLangs: List<String>,
    /** Full yt-dlp JSON saved to cache (single videos only), reused by the download. */
    val infoJsonPath: String? = null,
)
