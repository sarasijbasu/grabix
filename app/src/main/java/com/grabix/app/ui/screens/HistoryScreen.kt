package com.grabix.app.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabix.app.GrabixApp
import com.grabix.app.data.HistoryEntity
import com.grabix.app.player.PlayItem
import com.grabix.app.player.PlayerConnection
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.ui.components.Thumb
import com.grabix.app.util.Format
import kotlinx.coroutines.launch

/** Everything the user has played, newest first. Tapping an item resumes it where it was left. */
@Composable
fun HistoryScreen(asTab: Boolean = false, onBack: () -> Unit) {
    val dao = GrabixApp.instance.db.history()
    val items by dao.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = if (asTab) 16.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!asTab) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                "History",
                style = if (asTab) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(vertical = if (asTab) 8.dp else 0.dp)
            )
            if (items.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
        }
        if (items.isEmpty()) {
            EmptyState(Icons.Filled.History, "Nothing played yet", "Videos and music you play show up here.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.uri }) { h ->
                    HistoryRow(h, onRemove = { scope.launch { dao.delete(h.uri) } })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear play history?") },
            text = { Text("Your files are not deleted. Resume positions are kept.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { dao.clear() } }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryRow(h: HistoryEntity, onRemove: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    PlayerConnection.play(context, listOf(PlayItem(h.uri, h.title, h.artist, h.isVideo, h.artworkUri)))
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(h.artworkUri ?: h.uri, Modifier.width(104.dp).aspectRatio(16f / 9f), isAudio = !h.isVideo)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val whenText = DateUtils.getRelativeTimeSpanString(
                h.lastPlayedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
            val resume = if (h.positionMs > 5_000) " · left at ${Format.duration(h.positionMs / 1000)}" else ""
            Text(
                (if (h.isVideo) "Video · " else "Music · ") + whenText + resume,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
            )
            if (h.durationMs > 0 && h.positionMs > 5_000) {
                LinearProgressIndicator(
                    progress = { (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove from history") }
    }
}
