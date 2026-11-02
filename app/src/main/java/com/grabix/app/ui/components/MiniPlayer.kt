package com.grabix.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.grabix.app.player.PlayerActivity
import com.grabix.app.player.PlayerConnection
import kotlinx.coroutines.delay

/** Small "now playing" bar shown above the bottom navigation. */
@Composable
fun MiniPlayer() {
    val context = LocalContext.current
    val controller by PlayerConnection.controller.collectAsState()
    val c = controller ?: return
    var title by remember { mutableStateOf(c.mediaMetadata.title?.toString()) }
    var artist by remember { mutableStateOf(c.mediaMetadata.artist?.toString()) }
    var playing by remember { mutableStateOf(c.isPlaying) }
    var hasItem by remember { mutableStateOf(c.mediaItemCount > 0) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(c) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                title = player.mediaMetadata.title?.toString()
                artist = player.mediaMetadata.artist?.toString()
                playing = player.isPlaying
                hasItem = player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE
            }
        }
        c.addListener(l)
        onDispose { c.removeListener(l) }
    }
    LaunchedEffect(c, playing) {
        while (true) {
            val d = c.duration
            progress = if (d > 0) (c.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    if (!hasItem) return

    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title ?: "Playing", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    artist?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
                }
                IconButton(onClick = { c.seekToNextMediaItem() }, enabled = c.hasNextMediaItem()) {
                    Icon(Icons.Filled.SkipNext, "Next")
                }
                IconButton(onClick = { c.stop(); c.clearMediaItems() }) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(20.dp))
                }
            }
        }
    }
}
