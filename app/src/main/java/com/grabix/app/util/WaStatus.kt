package com.grabix.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** One WhatsApp status (photo or video) that can be viewed and saved. */
data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val modified: Long,
    val size: Long,
    val app: String,
)

/**
 * Reads WhatsApp / WhatsApp Business statuses. WhatsApp keeps the statuses you have
 * viewed in a hidden ".Statuses" folder. On Android 11+ that folder sits under
 * Android/media, which apps can only read after the user grants access once through the
 * system folder picker; older phones read the folder directly.
 */
object WaStatus {

    data class Source(val app: String, val relPath: String)

    val SOURCES = listOf(
        Source("WhatsApp", "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
        Source("WhatsApp Business", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
    )

    /** Pre-Android 11 locations. */
    private val LEGACY = listOf(
        Source("WhatsApp", "WhatsApp/Media/.Statuses"),
        Source("WhatsApp Business", "WhatsApp Business/Media/.Statuses"),
    )

    private const val EXTERNAL_AUTHORITY = "com.android.externalstorage.documents"
    private const val PREFS = "wa_status"
    private const val KEY_TREES = "trees"

    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov")
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif")

    /** Folder the picker should open at, so the user only has to tap "Use this folder". */
    fun initialUri(source: Source): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_AUTHORITY, "primary:${source.relPath}")

    val needsFolderAccess: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun grantedTrees(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_TREES, emptySet()).orEmpty()

    /** Keeps read access to a folder the user picked, across restarts. */
    fun saveGrant(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_TREES, grantedTrees(context) + tree.toString()).apply()
    }

    /** Package folder name such as "com.whatsapp", matched exactly so Business is not mistaken for WhatsApp. */
    private fun belongsTo(treeUri: String, pkg: String): Boolean =
        Regex("""(^|[/:])""" + Regex.escape(pkg) + """(/|$)""").containsMatchIn(Uri.decode(treeUri))

    private fun pkgOf(source: Source) = source.relPath.substringAfter("Android/media/").substringBefore('/')

    /** True when the user already granted a folder for this app and the grant is still valid. */
    fun hasGrantFor(context: Context, source: Source): Boolean {
        val pkg = pkgOf(source)
        return context.contentResolver.persistedUriPermissions.any { p ->
            p.isReadPermission && belongsTo(p.uri.toString(), pkg) && p.uri.toString() in grantedTrees(context)
        }
    }

    /** All statuses from every source we can reach, newest first. */
    fun load(context: Context): List<StatusItem> {
        val out = ArrayList<StatusItem>()
        // Direct file access (Android 10 and older, or when the folder happens to be readable).
        (LEGACY + SOURCES).forEach { s ->
            val dir = File(Environment.getExternalStorageDirectory(), s.relPath)
            dir.listFiles()?.forEach { f -> fileItem(f, s.app)?.let { out += it } }
        }
        // Folders granted through the system picker.
        if (out.isEmpty() || needsFolderAccess) {
            val persisted = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
            grantedTrees(context).filter { it in persisted }.forEach { t ->
                val tree = Uri.parse(t)
                val app = if (belongsTo(t, "com.whatsapp.w4b")) "WhatsApp Business" else "WhatsApp"
                runCatching { out += listTree(context, tree, app) }
            }
        }
        return out.distinctBy { it.app + "/" + it.name }.sortedByDescending { it.modified }
    }

    private fun fileItem(f: File, app: String): StatusItem? {
        if (!f.isFile || f.name.startsWith(".")) return null
        val ext = f.extension.lowercase()
        val video = ext in videoExt
        if (!video && ext !in imageExt) return null
        return StatusItem(Uri.fromFile(f), f.name, video, f.lastModified(), f.length(), app)
    }

    private val COLS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    private data class Doc(val id: String, val name: String, val mime: String, val modified: Long, val size: Long)

    private fun children(context: Context, tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val list = ArrayList<Doc>()
        context.contentResolver.query(uri, COLS, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                if (id != null) list += Doc(
                    id = id,
                    name = c.getString(1) ?: "",
                    mime = c.getString(2) ?: "",
                    modified = if (c.isNull(3)) 0L else c.getLong(3),
                    size = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        return list
    }

    private fun isMedia(d: Doc): Boolean {
        val ext = d.name.substringAfterLast('.').lowercase()
        return d.mime.startsWith("image/") || d.mime.startsWith("video/") || ext in videoExt || ext in imageExt
    }

    /**
     * Finds the ".Statuses" folder below whatever the user picked (for example
     * Android/media/com.whatsapp or WhatsApp/Media). Searches a few levels deep.
     */
    private fun findStatuses(context: Context, tree: Uri, id: String, depth: Int): String? {
        if (depth > 4) return null
        val kids = children(context, tree, id).filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR }
        kids.firstOrNull { it.name == ".Statuses" }?.let { return it.id }
        // Only walk folders that lead to WhatsApp media, to keep this quick.
        val next = kids.filter { it.name.startsWith("com.whatsapp") || it.name.startsWith("WhatsApp") || it.name == "Media" || it.name == "media" }
        for (k in next) findStatuses(context, tree, k.id, depth + 1)?.let { return it }
        return null
    }

    /** Lists media in a granted folder, looking for ".Statuses" inside it if needed. */
    private fun listTree(context: Context, tree: Uri, app: String): List<StatusItem> {
        val root = DocumentsContract.getTreeDocumentId(tree)
        var docs = children(context, tree, root)
        if (docs.none { isMedia(it) && !it.name.startsWith(".") }) {
            val statuses = findStatuses(context, tree, root, 0) ?: return emptyList()
            docs = children(context, tree, statuses)
        }
        return docs.mapNotNull { d ->
            if (d.name.startsWith(".") || !isMedia(d)) return@mapNotNull null
            val video = d.mime.startsWith("video/") || d.name.substringAfterLast('.').lowercase() in videoExt
            StatusItem(DocumentsContract.buildDocumentUriUsingTree(tree, d.id), d.name, video, d.modified, d.size, app)
        }
    }

    /** Folder where saved statuses go: Download/Grabix/WhatsApp Status. */
    fun saveDir(context: Context): File {
        val d = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grabix"), "WhatsApp Status")
        if ((d.exists() || d.mkdirs()) && Storage.canUse(d)) return d
        return File(context.getExternalFilesDir(null) ?: context.filesDir, "Grabix/WhatsApp Status").apply { mkdirs() }
    }

    fun isSaved(context: Context, item: StatusItem): Boolean = File(saveDir(context), item.name).exists()

    /** Copies a status into [saveDir] and adds it to the gallery. Returns the saved file. */
    fun save(context: Context, item: StatusItem): File {
        val dest = File(saveDir(context), item.name)
        if (!dest.exists()) {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalStateException("Could not read this status")
            if (item.modified > 0) dest.setLastModified(item.modified)
        }
        Storage.scan(context, dest.absolutePath)
        return dest
    }
}
