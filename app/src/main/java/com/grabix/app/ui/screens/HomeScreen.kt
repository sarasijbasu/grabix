package com.grabix.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabix.app.engine.Entry
import com.grabix.app.ui.MainViewModel
import com.grabix.app.ui.components.EmptyState
import com.grabix.app.ui.components.SectionLabel
import com.grabix.app.ui.components.Thumb
import com.grabix.app.util.Format

data class Site(val name: String, val url: String, val color: Color)

val QuickSites = listOf(
    Site("YouTube", "https://m.youtube.com", Color(0xFFE53935)),
    Site("Instagram", "https://www.instagram.com", Color(0xFFD81B60)),
    Site("Facebook", "https://m.facebook.com/watch", Color(0xFF1E66F5)),
    Site("X", "https://x.com", Color(0xFF222222)),
    Site("TikTok", "https://www.tiktok.com", Color(0xFF00A3A3)),
    Site("Vimeo", "https://vimeo.com", Color(0xFF1AB7EA)),
    Site("Dailymotion", "https://www.dailymotion.com", Color(0xFF0D47A1)),
    Site("SoundCloud", "https://m.soundcloud.com", Color(0xFFFF6D00)),
    Site("Reddit", "https://www.reddit.com", Color(0xFFFF4500)),
    Site("Twitch", "https://m.twitch.tv", Color(0xFF7B42F6)),
    Site("Bilibili", "https://m.bilibili.tv", Color(0xFF00A1D6)),
    Site("Pinterest", "https://www.pinterest.com", Color(0xFFC62828)),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpenStatus: () -> Unit = {}) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val search by vm.search.collectAsState()
    val settings by vm.settings.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        focus.clearFocus()
        vm.submit(text)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // Hero header with the input box. Uses the Material colour scheme so it follows
            // light / dark mode and Material You wallpaper colours.
            val cs = MaterialTheme.colorScheme
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surfaceContainer)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Download, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp)) }
                    Text(
                        "Grabix", color = cs.onPrimaryContainer, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text("No ads", color = cs.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Paste a link from any site, or search YouTube",
                    color = cs.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Link or search words") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) IconButton(onClick = { text = ""; vm.clearSearch() }) { Icon(Icons.Filled.Close, "Clear") }
                        else IconButton(onClick = {
                            clipboardText(context)?.let { text = it; vm.submit(it) } ?: vm.toast("Clipboard is empty")
                        }) { Icon(Icons.Filled.ContentPaste, "Paste") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        clipboardText(context)?.let { text = it; vm.submit(it) } ?: vm.toast("Clipboard is empty")
                    }) { Icon(Icons.Filled.ContentPaste, "Paste link") }
                    TextButton(onClick = { submit() }) {
                        Text(if (text.isNotBlank() && !text.contains(' ') && text.contains('.')) "Get video" else "Search", color = cs.onPrimaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = onOpenStatus) {
                        Icon(Icons.Filled.Forum, null, Modifier.size(18.dp))
                        Text(" Status")
                    }
                }
            }
        }

        if (search.query.isEmpty()) {
            item { SectionLabel("Sites", Modifier.padding(top = 8.dp)) }
            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    maxItemsInEachRow = 4
                ) {
                    QuickSites.forEach { site -> SiteTile(site) { vm.openInBrowser(site.url) } }
                }
            }
            if (settings.searchHistory.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Recent searches", Modifier.weight(1f))
                        TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
                    }
                }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        settings.searchHistory.forEach { q ->
                            AssistChip(
                                onClick = { text = q; vm.runSearch(q) },
                                label = { Text(q, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Copy a video link in any app, or tap Share → Grabix.\n" +
                            "2. Pick a resolution (up to 4K) or MP3.\n" +
                            "3. Watch or listen in the built-in player — online browsing included.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            item {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Results for “${search.query}”", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearSearch(); text = "" }) { Text("Close") }
                }
            }
            when {
                search.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                search.error != null -> item { EmptyState(Icons.Filled.SearchOff, "Search failed", search.error ?: "") }
                search.results.isEmpty() -> item { EmptyState(Icons.Filled.SearchOff, "Nothing found", "Try different words.") }
                else -> items(search.results, key = { it.url }) { e ->
                    ResultRow(e, onOpen = { vm.openInBrowser(e.url) }, onDownload = { vm.analyze(e.url) })
                }
            }
        }
    }
}

@Composable
private fun SiteTile(site: Site, onClick: () -> Unit) {
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(site.color),
            contentAlignment = Alignment.Center
        ) {
            Text(site.name.take(1), color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            site.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ResultRow(e: Entry, onOpen: () -> Unit, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(
            e.thumbnail,
            Modifier.width(140.dp).aspectRatio(16f / 9f),
            duration = Format.duration(e.durationSec)
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            e.uploader?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Watch") }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onDownload, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Download") }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
