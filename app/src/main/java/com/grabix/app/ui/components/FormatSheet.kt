package com.grabix.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabix.app.data.AppSettings
import com.grabix.app.engine.MediaInfo
import com.grabix.app.ui.AnalyzeState
import com.grabix.app.ui.MainViewModel
import com.grabix.app.util.Format
import com.grabix.app.util.Storage

/** Bottom sheet: pick video resolution or audio format, then download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(state: AnalyzeState, settings: AppSettings, vm: MainViewModel) {
    if (state is AnalyzeState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.dismissSheet() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            when (state) {
                is AnalyzeState.Loading -> Loading(state.url)
                is AnalyzeState.Error -> ErrorBox(state, vm)
                is AnalyzeState.Loaded -> Picker(state.info, state.cookieFile, settings, vm)
                AnalyzeState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Loading(url: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text("Reading link…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ErrorBox(state: AnalyzeState.Error, vm: MainViewModel) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Text("Couldn't get this video", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            state.message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.dismissSheet(); vm.openInBrowser(state.url) }) { Text("Open in browser") }
            Button(onClick = { vm.analyze(state.url) }) { Text("Try again") }
        }
    }
}

@Composable
private fun Picker(info: MediaInfo, cookieFile: String?, settings: AppSettings, vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val defaultVideo = remember(info) {
        val opts = info.videoOptions
        val pref = settings.videoQuality
        if (pref <= 0) 0 else opts.indexOfFirst { it.height in 1..pref }.takeIf { it >= 0 } ?: (opts.lastIndex.coerceAtLeast(0))
    }
    val defaultAudio = remember(info) {
        info.audioOptions.indexOfFirst { it.format == settings.audioFormat && (it.bitrate == settings.audioBitrate || it.format != "mp3") }
            .coerceAtLeast(0)
    }
    var videoIdx by remember(info) { mutableIntStateOf(defaultVideo) }
    var audioIdx by remember(info) { mutableIntStateOf(defaultAudio) }
    var subs by remember(info) { mutableStateOf(settings.embedSubs && (info.subtitleLangs.isNotEmpty() || info.isPlaylist)) }
    // Playlists go into their own folder; the user names it before the download starts.
    var askFolder by remember(info) { mutableStateOf(false) }
    var folder by remember(info) { mutableStateOf(Storage.safeFolderName(info.title).ifBlank { "Playlist" }) }

    fun start(folderName: String?) {
        if (tab == 0) info.videoOptions.getOrNull(videoIdx)?.let { vm.downloadVideo(info, it, subs, cookieFile, folderName) }
        else info.audioOptions.getOrNull(audioIdx)?.let { vm.downloadAudio(info, it, cookieFile, folderName) }
    }

    // Header
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(
            info.thumbnail,
            Modifier.width(128.dp).aspectRatio(16f / 9f),
            duration = Format.duration(info.durationSec)
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(info.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(info.uploader, info.site).joinToString(" · ")
            if (sub.isNotBlank()) Text(
                sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 4.dp)
            )
            if (info.isPlaylist) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    " Playlist · ${info.entries.size} items", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 16.dp)) {
        Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Video") }, icon = { Icon(Icons.Filled.Movie, null) })
        Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Music / MP3") }, icon = { Icon(Icons.Filled.Headphones, null) })
    }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (tab == 0) {
            items(info.videoOptions.size) { i ->
                val o = info.videoOptions[i]
                OptionRow(o.label, Format.bytes(o.estimatedBytes), badge = qualityBadge(o.height), selected = i == videoIdx) { videoIdx = i }
            }
        } else {
            items(info.audioOptions.size) { i ->
                val o = info.audioOptions[i]
                OptionRow(o.label, Format.bytes(o.estimatedBytes), badge = null, selected = i == audioIdx) { audioIdx = i }
            }
        }
    }

    if (tab == 0) {
        Row(
            Modifier.fillMaxWidth().clickable { subs = !subs }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = subs, onCheckedChange = { subs = it })
            Text(
                if (info.subtitleLangs.isEmpty() && !info.isPlaylist) "Embed subtitles (none found)" else "Embed subtitles",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { if (info.isPlaylist) askFolder = true else start(null) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            if (info.isPlaylist) "Download all ${info.entries.size}" else "Download",
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (askFolder) FolderDialog(
        name = folder, isAudio = tab == 1, count = info.entries.size,
        onName = { folder = it },
        onConfirm = { askFolder = false; start(Storage.safeFolderName(folder)) },
        onDismiss = { askFolder = false }
    )
}

@Composable
private fun FolderDialog(
    name: String, isAudio: Boolean, count: Int,
    onName: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder for this playlist") },
        text = {
            Column {
                Text(
                    "$count items will be saved in ${if (isAudio) "Music" else "Video"}/${Storage.safeFolderName(name).ifBlank { "…" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name, onValueChange = onName, singleLine = true,
                    label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = Storage.safeFolderName(name).isNotEmpty(), onClick = onConfirm) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun qualityBadge(h: Int): String? = when {
    h >= 2160 -> "4K"
    h >= 1440 -> "2K"
    h >= 1080 -> "FHD"
    h >= 720 -> "HD"
    else -> null
}

@Composable
private fun OptionRow(label: String, size: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (badge != null) Text(
                badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
