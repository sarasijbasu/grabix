package com.grabix.app.download

import android.content.Context
import com.grabix.app.GrabixApp
import com.grabix.app.data.DownloadEntity
import com.grabix.app.data.Kind
import com.grabix.app.data.Status
import com.grabix.app.engine.AudioOption
import com.grabix.app.engine.Engine
import com.grabix.app.engine.Entry
import com.grabix.app.engine.MediaInfo
import com.grabix.app.engine.VideoOption
import com.grabix.app.util.Storage
import java.io.File

/** Single entry point for creating and controlling downloads. */
object DownloadRepository {
    private val dao get() = GrabixApp.instance.db.downloads()

    /**
     * [folder] is used for playlists: every item is saved into Video/<folder>.
     * Single videos carry the analysed info JSON so the download starts without a second fetch.
     */
    suspend fun enqueueVideo(
        context: Context, info: MediaInfo, option: VideoOption, embedSubs: Boolean, cookieFile: String?,
        folder: String? = null,
    ): Int {
        val items = if (info.isPlaylist) info.entries.map { it.toTask(info, Kind.VIDEO, option.formatSpec, option.label, null, null, embedSubs, cookieFile, folder) }
        else listOf(
            DownloadEntity(
                url = info.url, mediaId = info.id, title = info.title, thumbnail = info.thumbnail,
                uploader = info.uploader, durationSec = info.durationSec, site = info.site,
                kind = Kind.VIDEO, formatSpec = option.formatSpec, qualityLabel = option.label,
                embedSubs = embedSubs, estimatedSize = option.estimatedBytes, cookieFile = cookieFile,
                infoJson = info.infoJsonPath,
            )
        )
        dao.insertAll(items)
        DownloadService.start(context)
        return items.size
    }

    suspend fun enqueueAudio(
        context: Context, info: MediaInfo, option: AudioOption, cookieFile: String?,
        folder: String? = null,
    ): Int {
        val items = if (info.isPlaylist) info.entries.map { it.toTask(info, Kind.AUDIO, "ba/b", option.label, option.format, option.bitrate, false, cookieFile, folder) }
        else listOf(
            DownloadEntity(
                url = info.url, mediaId = info.id, title = info.title, thumbnail = info.thumbnail,
                uploader = info.uploader, durationSec = info.durationSec, site = info.site,
                kind = Kind.AUDIO, formatSpec = "ba/b", qualityLabel = option.label,
                audioFormat = option.format, audioBitrate = option.bitrate,
                estimatedSize = option.estimatedBytes, cookieFile = cookieFile,
                infoJson = info.infoJsonPath,
            )
        )
        dao.insertAll(items)
        DownloadService.start(context)
        return items.size
    }

    private fun Entry.toTask(
        parent: MediaInfo, kind: String, spec: String, label: String,
        audioFormat: String?, bitrate: String?, subs: Boolean, cookies: String?, folder: String?,
    ) = DownloadEntity(
        url = url, mediaId = id, title = title, thumbnail = thumbnail,
        uploader = uploader ?: parent.uploader, durationSec = durationSec, site = parent.site,
        kind = kind, formatSpec = spec, qualityLabel = label, audioFormat = audioFormat,
        audioBitrate = bitrate, embedSubs = subs, cookieFile = cookies,
        subDir = folder?.let { Storage.safeFolderName(it) }?.ifBlank { null },
    )

    suspend fun pause(id: Long) {
        dao.setStatus(id, Status.PAUSED, null)
        Engine.cancel(id.toString())
    }

    suspend fun pauseAll() {
        dao.pending().forEach { dao.setStatus(it.id, Status.PAUSED, null) }
        DownloadService.activeIds().forEach { pause(it) }
    }

    suspend fun resume(context: Context, id: Long) {
        dao.setStatus(id, Status.QUEUED, null)
        DownloadService.start(context)
    }

    suspend fun retry(context: Context, id: Long) = resume(context, id)

    suspend fun cancel(id: Long) {
        val item = dao.get(id)
        dao.setStatus(id, Status.CANCELED, null)
        Engine.cancel(id.toString())
        // Remove partial files for this item.
        item?.mediaId?.let { mid ->
            listOf(Kind.VIDEO, Kind.AUDIO).forEach { k ->
                val dir = Storage.outputDir(GrabixApp.instance, GrabixApp.instance.settings.current(), k, item.subDir)
                dir.listFiles()?.filter { it.name.contains("[$mid]") && (it.name.contains(".part") || it.name.endsWith(".ytdl")) }
                    ?.forEach { it.delete() }
            }
        }
        dao.delete(id)
    }

    /** Deletes the record and, if [deleteFile], the file on disk. */
    suspend fun delete(context: Context, item: DownloadEntity, deleteFile: Boolean) {
        if (deleteFile) item.filePath?.let { p ->
            val f = File(p)
            if (f.exists()) f.delete()
            Storage.scan(context, p)
        }
        dao.delete(item.id)
    }

    suspend fun clearFailed() = dao.clearFailed()
}
