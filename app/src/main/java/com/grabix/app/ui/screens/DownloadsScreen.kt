package com.grabix.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import com.grabix.app.GrabixApp
import com.grabix.app.data.DownloadEntity
import com.grabix.app.data.Kind
import com.grabix.app.data.Status
import com.grabix.app.download.DownloadRepository
import com.grabix.app.player.PlayItem
import com.grabix.app.player.PlayerConnection
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.ui.components.Thumb
import com.grabix.app.util.Format
import com.grabix.app.util.Storage
import com.grabix.app.vault.Vault
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DownloadsScreen() {
    val dao = GrabixApp.instance.db.downloads()
    val active by dao.observeActive().collectAsState(initial = emptyList())
    val done by dao.observeCompleted().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmRemoveAll by remember { mutableStateOf(false) }

    // Ongoing (running) downloads first, then queued, paused, and finally failed.
    val ordered = remember(active) {
        fun rank(s: String) = when (s) {
            Status.RUNNING -> 0
            Status.QUEUED, Status.WAITING_WIFI -> 1
            Status.PAUSED -> 2
            else -> 3
        }
        active.sortedWith(compareBy({ rank(it.status) }, { it.createdAt }))
    }
    val anyPausable = active.any { it.status == Status.RUNNING || it.status == Status.QUEUED || it.status == Status.WAITING_WIFI }
    val anyPaused = active.any { it.status == Status.PAUSED }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (tab == 0 && active.any { it.status == Status.FAILED }) {
                TextButton(onClick = { scope.launch { DownloadRepository.clearFailed() } }) { Text("Clear failed") }
            }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("In progress (${active.size})") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Finished (${done.size})") })
        }
        if (tab == 0) {
            if (active.isEmpty()) EmptyState(
                Icons.Filled.CloudDownload, "Nothing downloading",
                "Paste a link on Home or tap Download while browsing."
            ) else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (anyPausable) OutlinedButton(
                        onClick = { scope.launch { DownloadRepository.pauseAll() } },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Filled.Pause, null, Modifier.size(18.dp)); Text("  Pause all") }
                    else if (anyPaused) OutlinedButton(
                        onClick = { scope.launch { active.filter { it.status == Status.PAUSED }.forEach { DownloadRepository.resume(context, it.id) } } },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Text("  Resume all") }
                    OutlinedButton(onClick = { confirmRemoveAll = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DeleteSweep, null, Modifier.size(18.dp)); Text("  Remove all")
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(ordered, key = { it.id }) { ActiveRow(it) }
                }
            }
        } else {
            if (done.isEmpty()) EmptyState(Icons.Filled.DoneAll, "No downloads yet", "Finished videos and music appear here.")
            else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(done, key = { it.id }) { item -> DoneRow(item, done) }
            }
        }
    }

    if (confirmRemoveAll) RemoveAllDialog(
        count = active.size,
        onConfirm = {
            confirmRemoveAll = false
            val ids = active.map { it.id }
            scope.launch { ids.forEach { DownloadRepository.cancel(it) } }
        },
        onDismiss = { confirmRemoveAll = false }
    )
}

@Composable
private fun RemoveAllDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove all ongoing downloads?") },
        text = { Text("$count download(s) will be canceled and their partial files deleted.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } }
    )
}

@Composable
private fun ActiveRow(d: DownloadEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(d.thumbnail, Modifier.width(104.dp).aspectRatio(16f / 9f), isAudio = d.kind == Kind.AUDIO)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val statusText = when (d.status) {
                Status.RUNNING -> buildString {
                    append("${d.progress.toInt()}%")
                    d.speed?.let { append(" · $it") }
                    if (d.etaSec > 0) append(" · ${Format.duration(d.etaSec)} left")
                }
                Status.QUEUED -> "Waiting in queue"
                Status.PAUSED -> "Paused · ${d.progress.toInt()}%"
                Status.WAITING_WIFI -> "Waiting for Wi-Fi"
                Status.FAILED -> d.error ?: "Failed"
                else -> d.status
            }
            Text(
                "${d.qualityLabel} · $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = if (d.status == Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (d.status == Status.RUNNING || d.status == Status.PAUSED) {
                LinearProgressIndicator(
                    progress = { d.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            } else if (d.status == Status.QUEUED) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        when (d.status) {
            Status.RUNNING, Status.QUEUED, Status.WAITING_WIFI ->
                IconButton(onClick = { scope.launch { DownloadRepository.pause(d.id) } }) { Icon(Icons.Filled.Pause, "Pause") }
            Status.PAUSED ->
                IconButton(onClick = { scope.launch { DownloadRepository.resume(context, d.id) } }) { Icon(Icons.Filled.PlayArrow, "Resume") }
            Status.FAILED ->
                IconButton(onClick = { scope.launch { DownloadRepository.retry(context, d.id) } }) { Icon(Icons.Filled.Refresh, "Retry") }
        }
        IconButton(onClick = { scope.launch { DownloadRepository.cancel(d.id) } }) { Icon(Icons.Filled.Close, "Cancel") }
    }
}

@Composable
fun DoneRow(d: DownloadEntity, all: List<DownloadEntity>, inVault: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val file = d.filePath?.let { File(it) }
    val missing = file == null || !file.exists()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !missing) {
                val playable = all.filter { it.filePath != null && File(it.filePath).exists() && it.kind == d.kind }
                val items = playable.map {
                    PlayItem.fromFile(it.filePath!!, it.title, it.uploader, it.kind == Kind.VIDEO, if (it.kind == Kind.AUDIO) it.thumbnail else null, it.url)
                }
                val idx = playable.indexOfFirst { it.id == d.id }.coerceAtLeast(0)
                scope.launch { PlayerConnection.play(context, items, idx) }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(
            d.thumbnail ?: file, Modifier.width(104.dp).aspectRatio(16f / 9f),
            duration = Format.duration(d.durationSec), isAudio = d.kind == Kind.AUDIO
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (missing) "File moved or deleted" else listOfNotNull(d.qualityLabel, Format.bytes(d.fileSize).ifBlank { null }, d.site).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!missing) {
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menu = false; Storage.share(context, file!!) })
                    DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menu = false; Storage.openWith(context, file!!) })
                    DropdownMenuItem(
                        text = { Text(if (inVault) "Move out of private folder" else "Move to private folder") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        onClick = {
                            menu = false
                            scope.launch { if (inVault) Vault.moveOut(context, d) else Vault.moveIn(context, d) }
                        })
                }
                DropdownMenuItem(text = { Text("Download again") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                    onClick = {
                        menu = false
                        scope.launch {
                            GrabixApp.instance.db.downloads().insert(
                                d.copy(id = 0, status = Status.QUEUED, progress = 0f, filePath = null, fileSize = 0,
                                    error = null, inVault = false, createdAt = System.currentTimeMillis(), completedAt = 0)
                            )
                            com.grabix.app.download.DownloadService.start(context)
                        }
                    })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menu = false; confirmDelete = true })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete download?") },
            text = { Text("“${d.title}”") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { DownloadRepository.delete(context, d, deleteFile = true) }
                }) { Text("Delete file") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { DownloadRepository.delete(context, d, deleteFile = false) }
                }) { Text("Remove from list only") }
            }
        )
    }
}
