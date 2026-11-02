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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabix.app.GrabixApp
import com.grabix.app.data.DeviceItem
import com.grabix.app.data.DeviceMedia
import com.grabix.app.data.Kind
import com.grabix.app.data.PlaylistEntity
import com.grabix.app.data.PlaylistItemEntity
import com.grabix.app.player.PlayItem
import com.grabix.app.player.PlayerConnection
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.ui.components.Thumb
import com.grabix.app.util.Format
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LibraryScreen(onOpenVault: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Music", "On this phone", "Playlists")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenVault) {
                Icon(Icons.Filled.Lock, null, Modifier.size(18.dp))
                Text("  Private")
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> DownloadedList(Kind.VIDEO)
            1 -> DownloadedList(Kind.AUDIO)
            2 -> DeviceList()
            3 -> PlaylistsTab()
        }
    }
}

@Composable
private fun DownloadedList(kind: String) {
    val all by GrabixApp.instance.db.downloads().observeCompleted().collectAsState(initial = emptyList())
    val items = all.filter { it.kind == kind && it.filePath != null && File(it.filePath).exists() }
    if (items.isEmpty()) {
        EmptyState(
            if (kind == Kind.VIDEO) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
            if (kind == Kind.VIDEO) "No downloaded videos" else "No downloaded music",
            "Your downloads show up here, ready to play offline."
        )
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            PlayAllBar(items.size) { shuffle ->
                val list = items.map {
                    PlayItem.fromFile(it.filePath!!, it.title, it.uploader, kind == Kind.VIDEO, if (kind == Kind.AUDIO) it.thumbnail else null, it.url)
                }
                scope.launch {
                    PlayerConnection.play(context, if (shuffle) list.shuffled() else list, 0)
                }
            }
        }
        items(items, key = { it.id }) { DoneRow(it, items) }
    }
}

@Composable
private fun PlayAllBar(count: Int, onPlay: (shuffle: Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$count items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp))
        TextButton(onClick = { onPlay(true) }) { Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)); Text(" Shuffle") }
        TextButton(onClick = { onPlay(false) }) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Text(" Play all") }
    }
}

@Composable
private fun DeviceList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<DeviceItem>?>(null) }
    var addTo by remember { mutableStateOf<DeviceItem?>(null) }

    LaunchedEffect(videos) {
        items = null
        items = if (videos) DeviceMedia.videos(context) else DeviceMedia.audio(context)
    }

    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilterChip(selected = videos, onClick = { videos = true }, label = { Text("Videos") })
            FilterChip(selected = !videos, onClick = { videos = false }, label = { Text("Music") }, modifier = Modifier.padding(start = 8.dp))
        }
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                Icons.Filled.PhoneAndroid, "Nothing found",
                "Allow media access in Android settings if your files don't show up."
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                item {
                    PlayAllBar(list.size) { shuffle ->
                        val q = list.map { PlayItem(it.uri.toString(), it.title, it.artist, it.isVideo) }
                        scope.launch { PlayerConnection.play(context, if (shuffle) q.shuffled() else q, 0) }
                    }
                }
                items(list, key = { it.uri.toString() }) { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val q = list.map { PlayItem(it.uri.toString(), it.title, it.artist, it.isVideo) }
                                scope.launch { PlayerConnection.play(context, q, list.indexOf(d)) }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(
                            d.uri, Modifier.width(if (d.isVideo) 104.dp else 56.dp).aspectRatio(if (d.isVideo) 16f / 9f else 1f),
                            duration = Format.duration(d.durationMs / 1000), isAudio = !d.isVideo
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(d.artist, d.folder, Format.bytes(d.size).ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                        IconButton(onClick = { addTo = d }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") }
                    }
                }
            }
        }
    }

    addTo?.let { item ->
        AddToPlaylistDialog(
            onDismiss = { addTo = null },
            onPick = { playlistId ->
                scope.launch {
                    val dao = GrabixApp.instance.db.playlists()
                    dao.insertItem(
                        PlaylistItemEntity(
                            playlistId = playlistId, uri = item.uri.toString(), title = item.title,
                            artist = item.artist, isVideo = item.isVideo, position = dao.maxPosition(playlistId) + 1
                        )
                    )
                    GrabixApp.instance.let { android.widget.Toast.makeText(it, "Added", android.widget.Toast.LENGTH_SHORT).show() }
                }
                addTo = null
            }
        )
    }
}

@Composable
fun AddToPlaylistDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val dao = GrabixApp.instance.db.playlists()
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                playlists.forEach { p ->
                    TextButton(onClick = { onPick(p.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(p.name, modifier = Modifier.fillMaxWidth())
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("New playlist") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = {
                scope.launch { onPick(dao.insert(PlaylistEntity(name = newName.trim()))) }
            }) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = GrabixApp.instance.db.playlists()
    val playlists by dao.observePlaylists().collectAsState(initial = emptyList())
    var openId by remember { mutableLongStateOf(-1L) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    if (openId >= 0) {
        PlaylistDetail(openId, onBack = { openId = -1 })
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, null); Text(" New playlist")
            }
        }
        if (playlists.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "No playlists", "Add songs or videos from “On this phone”.")
        }
        items(playlists, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { openId = p.id }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 16.dp))
                IconButton(onClick = {
                    scope.launch {
                        val list = dao.items(p.id).map { PlayItem(it.uri, it.title, it.artist, it.isVideo) }
                        PlayerConnection.play(context, list, 0)
                    }
                }) { Icon(Icons.Filled.PlayArrow, "Play") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                            menu = false
                            scope.launch { dao.clearItems(p.id); dao.deletePlaylist(p.id) }
                        })
                    }
                }
            }
        }
    }

    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        title = { Text("New playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch { dao.insert(PlaylistEntity(name = name.trim())); name = ""; creating = false }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistDetail(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = GrabixApp.instance.db.playlists()
    val items by dao.observeItems(id).collectAsState(initial = emptyList())
    androidx.activity.compose.BackHandler(onBack = onBack)
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Playlists") }
            }
            PlayAllBar(items.size) { shuffle ->
                val q = items.map { PlayItem(it.uri, it.title, it.artist, it.isVideo) }
                scope.launch { PlayerConnection.play(context, if (shuffle) q.shuffled() else q, 0) }
            }
        }
        items(items, key = { it.id }) { it2 ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    val q = items.map { PlayItem(it.uri, it.title, it.artist, it.isVideo) }
                    scope.launch { PlayerConnection.play(context, q, items.indexOf(it2)) }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (it2.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(it2.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    it2.artist?.let { a -> Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = { scope.launch { dao.deleteItem(it2.id) } }) { Icon(Icons.Filled.Delete, "Remove") }
            }
        }
    }
}
