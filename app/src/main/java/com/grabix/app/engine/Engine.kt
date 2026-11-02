package com.grabix.app.engine

import android.content.Context
import android.util.Log
import com.grabix.app.data.AppSettings
import com.grabix.app.data.DownloadEntity
import com.grabix.app.data.Kind
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed interface EngineState {
    data object Initializing : EngineState
    data class Ready(val version: String?) : EngineState
    data class Error(val message: String) : EngineState
}

/**
 * Wraps the bundled yt-dlp + FFmpeg + aria2c runtime.
 * Everything runs on-device; no external player or helper app is required.
 */
object Engine {
    private const val TAG = "GrabixEngine"

    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state

    @Volatile
    var aria2Available = false
        private set

    private val updateLock = Mutex()

    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            aria2Available = runCatching { Aria2c.getInstance().init(context) }.isSuccess
            _state.value = EngineState.Ready(YoutubeDL.getInstance().version(context))
        } catch (t: Throwable) {
            Log.e(TAG, "Engine init failed", t)
            _state.value = EngineState.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    suspend fun awaitReady(): Boolean =
        state.first { it !is EngineState.Initializing } is EngineState.Ready

    /** Updates yt-dlp to the newest stable release so site changes keep working. */
    suspend fun update(context: Context, nightly: Boolean = false): String = withContext(Dispatchers.IO) {
        updateLock.withLock {
            if (!awaitReady()) return@withContext "Engine not ready"
            try {
                val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
                val version = YoutubeDL.getInstance().version(context)
                _state.value = EngineState.Ready(version)
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "Updated to $version"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date ($version)"
                    else -> "Engine version $version"
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Update failed", t)
                "Update failed: ${t.message}"
            }
        }
    }

    private fun baseRequest(context: Context, url: String, cookieFile: String?): YoutubeDLRequest {
        val r = YoutubeDLRequest(url)
        val cache = File(context.cacheDir, "yt-dlp").apply { mkdirs() }
        r.addOption("--cache-dir", cache.absolutePath)
        r.addOption("--no-check-certificates")
        r.addOption("--socket-timeout", "20")
        if (cookieFile != null && File(cookieFile).exists()) r.addOption("--cookies", cookieFile)
        return r
    }

    /** Reads title, thumbnail, qualities and playlist entries without downloading. */
    suspend fun fetchInfo(context: Context, url: String, cookieFile: String?): MediaInfo =
        withContext(Dispatchers.IO) {
            check(awaitReady()) { "Download engine failed to start" }
            val r = baseRequest(context, url, cookieFile)
            r.addOption("--dump-single-json")
            r.addOption("--flat-playlist")
            r.addOption("--no-warnings")
            val response = YoutubeDL.getInstance().execute(r)
            val raw = response.out.trim().lineSequence().last { it.startsWith("{") }
            val json = JSONObject(raw)
            val info = parseInfo(json, url)
            if (info.isPlaylist) info else info.copy(infoJsonPath = saveInfoJson(context, raw))
        }

    /**
     * Keeps the analysed page so the download can start with --load-info-json instead of
     * starting yt-dlp's extractor again (saves several seconds per download).
     */
    private fun saveInfoJson(context: Context, raw: String): String? = runCatching {
        val dir = File(context.cacheDir, "info").apply { mkdirs() }
        // Old files are useless after a few hours (stream links expire), so tidy up as we go.
        val cutoff = System.currentTimeMillis() - INFO_MAX_AGE_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val f = File(dir, "${System.currentTimeMillis()}_${(0..9999).random()}.json")
        f.writeText(raw)
        f.absolutePath
    }.getOrNull()

    /** Stream links inside a saved info JSON stay valid for a few hours at most. */
    private const val INFO_MAX_AGE_MS = 3 * 60 * 60 * 1000L

    fun usableInfoJson(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        return f.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < INFO_MAX_AGE_MS }
    }

    /** YouTube search inside the app (no browser needed). */
    suspend fun search(context: Context, query: String, count: Int = 25): List<Entry> =
        withContext(Dispatchers.IO) {
            check(awaitReady()) { "Download engine failed to start" }
            val r = baseRequest(context, "ytsearch$count:$query", null)
            r.addOption("--dump-single-json")
            r.addOption("--flat-playlist")
            r.addOption("--no-warnings")
            val response = YoutubeDL.getInstance().execute(r)
            val json = JSONObject(response.out.trim().lineSequence().last { it.startsWith("{") })
            parseEntries(json.optJSONArray("entries"), "youtube")
        }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    /** Metadata fields cleared when the user turns tags off. purl / comment keep the source link. */
    private val CLEAR_META = listOf(
        "title", "date", "description", "synopsis", "track", "artist", "composer", "genre",
        "album", "album_artist", "disc", "show", "season_number", "episode_id", "episode_sort",
    )

    /**
     * Runs a download. [onProgress] receives percent (0-100, or -1 for non-progress lines),
     * ETA seconds and the raw log line. When [useInfoJson] is true and the task has a fresh
     * saved info JSON, the page is not fetched again.
     * Returns the full stdout so the caller can locate the produced file.
     */
    fun download(
        context: Context,
        task: DownloadEntity,
        settings: AppSettings,
        outDir: File,
        processId: String,
        useInfoJson: Boolean = true,
        onProgress: (Float, Long, String) -> Unit,
    ): String {
        val info = if (useInfoJson) usableInfoJson(task.infoJson) else null
        val r = if (info != null) {
            YoutubeDLRequest(emptyList<String>()).also { req ->
                req.addOption("--load-info-json", info.absolutePath)
                req.addOption("--cache-dir", File(context.cacheDir, "yt-dlp").apply { mkdirs() }.absolutePath)
                req.addOption("--no-check-certificates")
                req.addOption("--socket-timeout", "20")
                task.cookieFile?.takeIf { File(it).exists() }?.let { req.addOption("--cookies", it) }
            }
        } else baseRequest(context, task.url, task.cookieFile)
        r.addOption("--no-mtime")
        r.addOption("--newline")
        r.addOption("--continue")
        r.addOption("--no-playlist")
        r.addOption("--windows-filenames")
        r.addOption("--trim-filenames", "160")
        r.addOption("-o", File(outDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)

        // Tags. The source link (purl / comment) is always written so the player can open it.
        r.addOption("--embed-metadata")
        if (!settings.embedMetadata) {
            r.addOption("--parse-metadata", ":" + CLEAR_META.joinToString("") { "(?P<meta_$it>)" })
            r.addOption("--no-embed-chapters")
        }

        if (task.kind == Kind.AUDIO) {
            val fmt = task.audioFormat ?: "mp3"
            // Prefer a stream that is already in the wanted format so no re-encode is needed.
            r.addOption("-f", if (fmt == "m4a") "ba[ext=m4a]/ba/b" else "ba/b")
            r.addOption("-x")
            if (fmt != "best") {
                r.addOption("--audio-format", fmt)
                val br = task.audioBitrate ?: "0"
                r.addOption("--audio-quality", if (br == "0") "0" else "${br}K")
            }
            if (settings.embedThumbnail && fmt != "opus") {
                r.addOption("--embed-thumbnail")
                r.addOption("--convert-thumbnails", "jpg")
            }
        } else {
            r.addOption("-f", task.formatSpec)
            // H.264 + AAC merge into MP4 by stream copy (no re-encode).
            r.addOption("-S", "vcodec:h264,res,acodec:m4a")
            r.addOption("--merge-output-format", "mp4")
            if (task.embedSubs) {
                r.addOption("--write-subs")
                r.addOption("--sub-langs", settings.subLangs.ifBlank { "en.*,en" })
                r.addOption("--embed-subs")
            }
            if (settings.embedThumbnail) r.addOption("--embed-thumbnail")
        }

        if (settings.useAria2 && aria2Available) {
            r.addOption("--downloader", "libaria2c.so")
            r.addOption("--external-downloader-args", "aria2c:--summary-interval=1 -x 16 -s 16 -k 1M")
        } else {
            r.addOption("-N", "8")
        }

        val response = YoutubeDL.getInstance().execute(r, processId) { progress, eta, line ->
            onProgress(progress, eta, line)
        }
        return response.out
    }

    /** Human label for yt-dlp's post-processing steps, shown instead of the speed. */
    fun phaseOf(line: String): String? = when {
        line.startsWith("[Merger]") -> "Merging audio + video"
        line.startsWith("[ExtractAudio]") -> "Converting audio"
        line.startsWith("[VideoConvertor]") || line.startsWith("[VideoRemuxer]") -> "Converting video"
        line.startsWith("[EmbedSubtitle]") -> "Adding subtitles"
        line.startsWith("[ThumbnailsConvertor]") || line.startsWith("[EmbedThumbnail]") -> "Adding cover art"
        line.startsWith("[Metadata]") -> "Writing tags"
        line.startsWith("[Fixup") -> "Fixing file"
        line.startsWith("[info] Writing video subtitles") || line.startsWith("[info] Downloading subtitles") -> "Getting subtitles"
        else -> null
    }

    // ---------- parsing ----------

    private fun parseInfo(json: JSONObject, requestedUrl: String): MediaInfo {
        val type = json.optString("_type")
        val title = json.optString("title").ifBlank { "Untitled" }
        val site = json.optString("extractor_key").ifBlank { json.optString("extractor") }.ifBlank { null }
        val thumb = json.optStringOrNull("thumbnail") ?: bestThumbnail(json.optJSONArray("thumbnails"))
        val uploader = json.optStringOrNull("uploader") ?: json.optStringOrNull("channel")

        if (type == "playlist" || type == "multi_video") {
            val entries = parseEntries(json.optJSONArray("entries"), site)
            return MediaInfo(
                id = json.optStringOrNull("id"),
                url = json.optStringOrNull("webpage_url") ?: requestedUrl,
                title = title,
                uploader = uploader,
                durationSec = 0,
                thumbnail = thumb ?: entries.firstOrNull()?.thumbnail,
                site = site,
                isPlaylist = true,
                entries = entries,
                videoOptions = genericVideoOptions(0),
                audioOptions = audioOptions(0),
                subtitleLangs = emptyList(),
            )
        }

        val duration = json.optDouble("duration", 0.0).toLong()
        val formats = json.optJSONArray("formats") ?: JSONArray()
        val subs = json.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty()

        return MediaInfo(
            id = json.optStringOrNull("id"),
            url = json.optStringOrNull("webpage_url") ?: requestedUrl,
            title = title,
            uploader = uploader,
            durationSec = duration,
            thumbnail = thumb,
            site = site,
            isPlaylist = false,
            entries = emptyList(),
            videoOptions = videoOptions(formats, duration),
            audioOptions = audioOptions(duration),
            subtitleLangs = subs,
        )
    }

    private fun parseEntries(arr: JSONArray?, site: String?): List<Entry> {
        if (arr == null) return emptyList()
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val id = e.optStringOrNull("id")
            var url = e.optStringOrNull("webpage_url") ?: e.optStringOrNull("url") ?: continue
            if (!url.startsWith("http")) {
                url = if ((site ?: "").contains("youtube", true) && id != null) {
                    "https://www.youtube.com/watch?v=$id"
                } else continue
            }
            val thumb = e.optStringOrNull("thumbnail")
                ?: bestThumbnail(e.optJSONArray("thumbnails"))
                ?: if ((site ?: "").contains("youtube", true) && id != null) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null
            out += Entry(
                id = id,
                url = url,
                title = e.optString("title").ifBlank { url },
                durationSec = e.optDouble("duration", 0.0).toLong(),
                thumbnail = thumb,
                uploader = e.optStringOrNull("uploader") ?: e.optStringOrNull("channel"),
            )
        }
        return out
    }

    private fun bestThumbnail(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var best: String? = null
        var bestW = -1
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val w = t.optInt("width", 0)
            val u = t.optStringOrNull("url") ?: continue
            if (w >= bestW) { bestW = w; best = u }
        }
        return best
    }

    private fun videoOptions(formats: JSONArray, duration: Long): List<VideoOption> {
        // Best audio size, added to video-only streams to estimate the merged file.
        var bestAudio = 0L
        val byHeight = HashMap<Int, Pair<Long, Int>>() // height -> (bytes, fps)
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val vcodec = f.optString("vcodec", "none")
            val acodec = f.optString("acodec", "none")
            val size = f.optLong("filesize", 0).takeIf { it > 0 }
                ?: f.optLong("filesize_approx", 0).takeIf { it > 0 }
                ?: (f.optDouble("tbr", 0.0) * 125 * duration).toLong()
            if (vcodec == "none" && acodec != "none") {
                if (size > bestAudio) bestAudio = size
                continue
            }
            if (vcodec == "none") continue
            val h = f.optInt("height", 0)
            if (h <= 0) continue
            val fps = f.optDouble("fps", 0.0).toInt()
            val cur = byHeight[h]
            if (cur == null || size > cur.first) byHeight[h] = size to fps
        }
        if (byHeight.isEmpty()) return genericVideoOptions(duration)

        return byHeight.entries
            .sortedByDescending { it.key }
            .map { (h, v) ->
                val label = when {
                    h >= 2160 -> "4K (${h}p)"
                    h >= 1440 -> "2K (${h}p)"
                    else -> "${h}p"
                } + if (v.second > 30) " ${v.second}fps" else ""
                VideoOption(
                    height = h,
                    label = label,
                    fps = v.second,
                    estimatedBytes = if (v.first > 0) v.first + bestAudio else 0,
                    formatSpec = "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b",
                )
            }
    }

    private fun genericVideoOptions(duration: Long): List<VideoOption> =
        listOf(
            VideoOption(0, "Best quality", 0, 0, "bv*+ba/b"),
            VideoOption(1080, "1080p", 0, 0, "bv*[height<=1080]+ba/b[height<=1080]/b"),
            VideoOption(720, "720p", 0, 0, "bv*[height<=720]+ba/b[height<=720]/b"),
            VideoOption(480, "480p", 0, 0, "bv*[height<=480]+ba/b[height<=480]/b"),
            VideoOption(360, "360p", 0, 0, "bv*[height<=360]+ba/b[height<=360]/b"),
        )

    fun audioOptions(duration: Long): List<AudioOption> {
        fun est(kbps: Int) = if (duration > 0) duration * kbps * 125L else 0L
        return listOf(
            AudioOption("MP3 320 kbps", "mp3", "320", est(320)),
            AudioOption("MP3 192 kbps", "mp3", "192", est(192)),
            AudioOption("MP3 128 kbps", "mp3", "128", est(128)),
            AudioOption("M4A (AAC) best", "m4a", "0", est(160)),
            AudioOption("Original (no conversion)", "best", "0", est(140)),
        )
    }

    /** Picks the produced file from yt-dlp's log output, falling back to a directory scan. */
    fun locateOutput(stdout: String, outDir: File, mediaId: String?, startedAt: Long): File? {
        val patterns = listOf(
            Regex("""\[Merger] Merging formats into "(.+)""""),
            Regex("""\[ExtractAudio] Destination: (.+)"""),
            Regex("""\[VideoConvertor] Converting video .* Destination: (.+)"""),
            Regex("""\[download] Destination: (.+)"""),
            Regex("""\[download] (.+) has already been downloaded"""),
        )
        for (p in patterns) {
            val path = p.findAll(stdout).lastOrNull()?.groupValues?.get(1)?.trim()
            if (path != null) {
                val f = File(path)
                if (f.exists() && f.length() > 0) return f
            }
        }
        val junk = setOf("part", "ytdl", "temp", "tmp", "vtt", "srt", "webp", "jpg", "png")
        return outDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() !in junk && !it.name.contains(".part") }
            ?.filter { f -> if (mediaId != null) f.name.contains("[$mediaId]") else f.lastModified() >= startedAt - 5_000 }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}
