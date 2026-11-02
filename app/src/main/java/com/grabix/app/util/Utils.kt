package com.grabix.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.grabix.app.BuildConfig
import com.grabix.app.data.AppSettings
import com.grabix.app.data.Kind
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object Storage {

    /**
     * Where finished downloads are saved, per the user's storage choice. [subDir] is a
     * playlist folder created inside the Video / Music folder.
     */
    fun outputDir(context: Context, settings: AppSettings, kind: String, subDir: String?): File {
        val base = outputDir(context, settings, kind)
        val name = subDir?.let { safeFolderName(it) }?.takeIf { it.isNotEmpty() } ?: return base
        val d = File(base, name)
        return if (canUse(d)) d else base
    }

    /** Removes characters that are not allowed in folder names on Android / FAT SD cards. */
    fun safeFolderName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim().trim('.')
            .take(80)

    /** Where finished downloads are saved, per the user's storage choice. */
    fun outputDir(context: Context, settings: AppSettings, kind: String): File {
        val custom = if (kind == Kind.AUDIO) settings.audioDir else settings.videoDir
        if (!custom.isNullOrBlank()) {
            val d = File(custom)
            if (canUse(d)) return d
        }
        val sub = if (kind == Kind.AUDIO) "Music" else "Video"
        val base: File? = when (settings.storage) {
            "sd" -> context.getExternalFilesDirs(null).getOrNull(1)?.let { File(it, "Grabix") }
            else -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grabix")
        }
        val target = base?.let { File(it, sub) }
        if (target != null && (target.exists() || target.mkdirs()) && target.canWrite()) return target
        // Fallback that always works without permissions.
        return File(context.getExternalFilesDir(null) ?: context.filesDir, "Grabix/$sub").apply { mkdirs() }
    }

    /** True if we can create (and delete) files in [dir], the way yt-dlp will. */
    fun canUse(dir: File): Boolean = runCatching {
        if (!dir.exists() && !dir.mkdirs()) return false
        val probe = File(dir, ".grabix_write_test.part")
        val ok = probe.createNewFile() || probe.exists()
        probe.delete()
        ok
    }.getOrDefault(false)

    /** Turns a folder picked with the system picker into a real path (phone storage or SD card). */
    fun treeUriToPath(uri: Uri): String? {
        val docId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val root = if (volume == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    /** Default phone-storage location, e.g. Download/Grabix/Video. */
    fun defaultPhoneDir(kind: String): File =
        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grabix"), if (kind == Kind.AUDIO) "Music" else "Video")

    fun sdDir(context: Context, kind: String): File? =
        context.getExternalFilesDirs(null).getOrNull(1)?.let { File(File(it, "Grabix"), if (kind == Kind.AUDIO) "Music" else "Video") }

    fun hasSdCard(context: Context): Boolean = context.getExternalFilesDirs(null).getOrNull(1) != null

    fun vaultDir(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }

    fun scan(context: Context, vararg paths: String) {
        MediaScannerConnection.scanFile(context, paths, null, null)
    }

    fun moveFile(src: File, destDir: File): File {
        destDir.mkdirs()
        var dest = File(destDir, src.name)
        var n = 1
        while (dest.exists()) {
            dest = File(destDir, "${src.nameWithoutExtension} ($n).${src.extension}")
            n++
        }
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = false)
            src.delete()
        }
        return dest
    }

    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
            ?: if (isAudioExt(file.extension)) "audio/*" else "video/*"

    fun isAudioExt(ext: String) = ext.lowercase(Locale.ROOT) in setOf("mp3", "m4a", "aac", "opus", "ogg", "flac", "wav", "weba")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)

    fun share(context: Context, file: File) {
        val uri = uriFor(context, file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWith(context: Context, file: File) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

object Net {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** True on Wi-Fi / Ethernet / any unmetered network. */
    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

object Cookies {
    /**
     * Exports the in-app browser's cookies for [url] in Netscape format so yt-dlp can
     * download content that needs a login (Instagram, Facebook, private videos).
     */
    fun exportFor(context: Context, url: String): String? = runCatching {
        val uri = Uri.parse(url)
        val host = uri.host ?: return null
        val raw = CookieManager.getInstance().getCookie(url) ?: return null
        if (raw.isBlank()) return null
        val domain = "." + host.removePrefix("www.").removePrefix("m.")
        val expiry = (System.currentTimeMillis() / 1000) + 30L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        raw.split(";").map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            sb.append(domain).append('\t').append("TRUE").append('\t').append("/").append('\t')
                .append("TRUE").append('\t').append(expiry).append('\t')
                .append(name).append('\t').append(value).append('\n')
        }
        val dir = File(context.cacheDir, "cookies").apply { mkdirs() }
        val file = File(dir, "${host.replace(Regex("[^a-zA-Z0-9.]"), "_")}.txt")
        file.writeText(sb.toString())
        file.absolutePath
    }.getOrNull()
}

object Format {
    fun bytes(b: Long): String {
        if (b <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i <= 1) String.format(Locale.US, "%.0f %s", v, units[i])
        else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun duration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

object Links {
    private val urlRegex = Regex("""https?://[^\s"'<>]+""")

    fun extract(text: String?): String? = text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ')') }

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http://") || t.startsWith("https://") ||
            (!t.contains(' ') && t.contains('.') && t.substringAfterLast('.').length in 2..6)
    }

    fun normalize(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }
}
