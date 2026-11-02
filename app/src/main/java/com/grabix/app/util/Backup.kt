package com.grabix.app.util

import android.content.Context
import android.net.Uri
import com.grabix.app.GrabixApp
import com.grabix.app.data.PlaylistEntity
import com.grabix.app.data.PlaylistItemEntity
import com.grabix.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs up settings and playlists to a plain, readable .txt file and restores them.
 *
 * Format (one entry per line, "#" lines are comments):
 *
 *     [settings]
 *     video_quality=1080
 *     theme=dark
 *
 *     [playlist] Road trip
 *     audio<TAB>Song title<TAB>Artist<TAB>content://media/...
 *
 * The private-folder PIN is never written to the file.
 */
object Backup {

    data class Result(val settings: Int, val playlists: Int, val items: Int)

    fun suggestedName(): String =
        "grabix-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".txt"

    private fun clean(s: String?) = s.orEmpty().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    suspend fun export(context: Context, target: Uri) = withContext(Dispatchers.IO) {
        val app = GrabixApp.instance
        val sb = StringBuilder()
        sb.append("# Grabix backup\n")
        sb.append("# app=").append(BuildConfig.VERSION_NAME).append('\n')
        sb.append("# created=").append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())).append('\n')
        sb.append("# Restore it from Me → Backup & restore → Restore from file.\n\n")

        sb.append("[settings]\n")
        app.settings.exportPairs().forEach { (k, v) -> sb.append(k).append('=').append(clean(v)).append('\n') }

        val dao = app.db.playlists()
        dao.allPlaylists().forEach { p ->
            sb.append('\n').append("[playlist] ").append(clean(p.name)).append('\n')
            dao.items(p.id).forEach { i ->
                sb.append(if (i.isVideo) "video" else "audio").append('\t')
                    .append(clean(i.title)).append('\t')
                    .append(clean(i.artist)).append('\t')
                    .append(clean(i.uri)).append('\n')
            }
        }
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(sb.toString().toByteArray()) }
            ?: throw IllegalStateException("Could not write the file")
    }

    suspend fun import(context: Context, source: Uri): Result = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Could not read the file")
        require(text.contains("[settings]") || text.contains("[playlist]")) { "This is not a Grabix backup file" }

        val settingPairs = ArrayList<Pair<String, String>>()
        val playlists = LinkedHashMap<String, MutableList<List<String>>>()
        var section = ""
        var current: MutableList<List<String>>? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            when {
                line.isBlank() || line.startsWith("#") -> Unit
                line == "[settings]" -> { section = "settings"; current = null }
                line.startsWith("[playlist]") -> {
                    section = "playlist"
                    val name = line.removePrefix("[playlist]").trim().ifBlank { "Restored playlist" }
                    current = playlists.getOrPut(name) { ArrayList() }
                }
                section == "settings" && line.contains('=') ->
                    settingPairs += line.substringBefore('=').trim() to line.substringAfter('=')
                section == "playlist" -> {
                    val parts = line.split('\t')
                    if (parts.size >= 4 && parts[3].isNotBlank()) current?.add(parts)
                }
            }
        }

        val app = GrabixApp.instance
        val applied = app.settings.importPairs(settingPairs)

        // Playlists with the same name are merged; songs already in them are skipped.
        val dao = app.db.playlists()
        val existing = dao.allPlaylists()
        var itemCount = 0
        for ((name, rows) in playlists) {
            val id = existing.firstOrNull { it.name == name }?.id ?: dao.insert(PlaylistEntity(name = name))
            val have = dao.items(id).map { it.uri }.toMutableSet()
            var pos = dao.maxPosition(id) + 1
            for (r in rows) {
                val uri = r[3]
                if (!have.add(uri)) continue
                dao.insertItem(
                    PlaylistItemEntity(
                        playlistId = id, uri = uri, title = r[1].ifBlank { uri.substringAfterLast('/') },
                        artist = r[2].ifBlank { null }, isVideo = r[0] == "video", position = pos++
                    )
                )
                itemCount++
            }
        }
        Result(applied, playlists.size, itemCount)
    }
}
