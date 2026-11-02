package com.grabix.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.grabix.app.player.PlayItem
import com.grabix.app.player.PlayerConnection
import com.grabix.app.ui.MainViewModel
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.ui.components.Thumb
import com.grabix.app.util.StatusItem
import com.grabix.app.util.Storage
import com.grabix.app.util.WaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** View and save WhatsApp / WhatsApp Business statuses. */
@Composable
fun StatusScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<StatusItem>?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<StatusItem?>(null) }
    var saved by remember { mutableStateOf(setOf<String>()) }
    // Sources (WhatsApp / Business) that still need folder access on Android 11+.
    var missing by remember { mutableStateOf<List<WaStatus.Source>>(emptyList()) }

    BackHandler(onBack = onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            WaStatus.saveGrant(context, uri)
            reload++
        }
    }

    LaunchedEffect(reload) {
        items = null
        missing = if (WaStatus.needsFolderAccess) WaStatus.SOURCES.filterNot { WaStatus.hasGrantFor(context, it) } else emptyList()
        val list = withContext(Dispatchers.IO) { runCatching { WaStatus.load(context) }.getOrDefault(emptyList()) }
        saved = withContext(Dispatchers.IO) { list.filter { WaStatus.isSaved(context, it) }.map { it.name }.toSet() }
        items = list
    }

    fun save(item: StatusItem) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { WaStatus.save(context, item) } }
                .onSuccess { saved = saved + item.name; vm.toast("Saved to Download/Grabix/WhatsApp Status") }
                .onFailure { vm.toast(it.message ?: "Could not save") }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("WhatsApp Status", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
        }

        val all = items
        val photos = all.orEmpty().filter { !it.isVideo }
        val videos = all.orEmpty().filter { it.isVideo }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Videos (${videos.size})") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Photos (${photos.size})") })
        }
        val shown = if (tab == 0) videos else photos

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (missing.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                AccessCard(missing, onPick = { source -> picker.launch(WaStatus.initialUri(source)) })
            }
            when {
                all == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Filled.PlayCircle, "No statuses yet",
                        "Open WhatsApp and view some statuses first. They show up here for 24 hours."
                    )
                }
                else -> items(shown, key = { it.app + it.uri }) { s ->
                    StatusTile(s, isSaved = s.name in saved, onOpen = {
                        if (s.isVideo) {
                            val q = videos.map { PlayItem(it.uri.toString(), it.name, it.app, isVideo = true) }
                            scope.launch { PlayerConnection.play(context, q, videos.indexOf(s)) }
                        } else preview = s
                    }, onSave = { save(s) })
                }
            }
        }
    }

    preview?.let { p ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = p.uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { WaStatus.save(context, p) } }
                                .onSuccess { f -> saved = saved + p.name; Storage.share(context, f) }
                                .onFailure { vm.toast(it.message ?: "Could not share") }
                        }
                    }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                    IconButton(onClick = { save(p) }) {
                        Icon(if (p.name in saved) Icons.Filled.CheckCircle else Icons.Filled.Download, "Save", tint = Color.White)
                    }
                    IconButton(onClick = { preview = null }) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun AccessCard(missing: List<WaStatus.Source>, onPick: (WaStatus.Source) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Allow access to statuses", style = MaterialTheme.typography.titleSmall)
            Text(
                "Android keeps WhatsApp's status folder private. Tap a button, then tap “Use this folder” " +
                    "and “Allow” on the next screen. You only need to do this once per app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEachIndexed { i, src ->
                    if (i == 0) Button(onClick = { onPick(src) }) {
                        Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Text(" ${src.app}")
                    } else FilledTonalButton(onClick = { onPick(src) }) { Text(src.app) }
                }
            }
        }
    }
}

@Composable
private fun StatusTile(s: StatusItem, isSaved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
    ) {
        Thumb(s.uri, Modifier.fillMaxSize(), isAudio = false, corner = 12.dp)
        if (s.isVideo) Icon(
            Icons.Filled.PlayCircle, null, tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(36.dp)
        )
        IconButton(
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
        ) {
            Icon(
                if (isSaved) Icons.Filled.CheckCircle else Icons.Filled.Download,
                if (isSaved) "Saved" else "Save", tint = Color.White, modifier = Modifier.size(20.dp)
            )
        }
    }
}
