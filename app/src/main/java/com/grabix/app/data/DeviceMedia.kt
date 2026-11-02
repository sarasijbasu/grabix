package com.grabix.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceItem(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val size: Long,
    val isVideo: Boolean,
    val folder: String?,
    val dateAdded: Long,
)

/** Reads videos and music already on the phone, so Grabix works as the only player. */
object DeviceMedia {

    suspend fun videos(context: Context): List<DeviceItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED,
        )
        val out = mutableListOf<DeviceItem>()
        runCatching {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val size = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val bucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val added = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    out += DeviceItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(id)),
                        title = c.getString(name)?.substringBeforeLast('.') ?: "Video",
                        artist = null,
                        durationMs = c.getLong(dur),
                        size = c.getLong(size),
                        isVideo = true,
                        folder = c.getString(bucket),
                        dateAdded = c.getLong(added),
                    )
                }
            }
        }
        out
    }

    suspend fun audio(context: Context): List<DeviceItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val out = mutableListOf<DeviceItem>()
        runCatching {
            context.contentResolver.query(
                collection, projection, "${MediaStore.Audio.Media.DURATION} > 15000", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    out += DeviceItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(id)),
                        title = c.getString(title) ?: "Track",
                        artist = c.getString(artist)?.takeUnless { it == "<unknown>" },
                        durationMs = c.getLong(dur),
                        size = c.getLong(size),
                        isVideo = false,
                        folder = c.getString(album),
                        dateAdded = c.getLong(added),
                    )
                }
            }
        }
        out
    }
}
