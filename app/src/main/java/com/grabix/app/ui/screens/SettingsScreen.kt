package com.grabix.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.grabix.app.BuildConfig
import com.grabix.app.GrabixApp
import com.grabix.app.data.Kind
import com.grabix.app.engine.Engine
import com.grabix.app.engine.EngineState
import com.grabix.app.player.ChoiceDialog
import com.grabix.app.ui.MainViewModel
import com.grabix.app.ui.components.SectionLabel
import com.grabix.app.ui.components.rememberMicroGLogin
import com.grabix.app.util.Backup
import com.grabix.app.util.MicroG
import com.grabix.app.util.Storage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onOpenVault: () -> Unit, onOpenStatus: () -> Unit = {}) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val repo = GrabixApp.instance.settings
    val scope = rememberCoroutineScope()
    val engine by Engine.state.collectAsState()
    var updating by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickKind by remember { mutableStateOf<String?>(null) }
    val microGLogin = rememberMicroGLogin(vm)
    val microGApps = remember(s.microG) { MicroG.installed(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            runCatching { Backup.export(context, uri) }
                .onSuccess { vm.toast("Backup saved") }
                .onFailure { vm.toast("Backup failed: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Backup.import(context, uri) }
                .onSuccess { r -> vm.toast("Restored ${r.settings} settings, ${r.playlists} playlists (${r.items} new items)") }
                .onFailure { vm.toast("Restore failed: ${it.message}") }
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val kind = pickKind
        pickKind = null
        if (uri != null && kind != null) {
            val path = Storage.treeUriToPath(uri)
            when {
                path == null -> vm.toast("Pick a folder on phone storage or an SD card")
                !Storage.canUse(File(path)) ->
                    vm.toast("Grabix can't save into that folder. Try one inside Movies, Music or Download.")
                else -> scope.launch {
                    if (kind == Kind.AUDIO) repo.setAudioDir(path) else repo.setVideoDir(path)
                    vm.toast("Saving to $path")
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Me", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp, 12.dp)) }

        item { SectionLabel("Tools") }
        item { ValueRow("WhatsApp Status", "View and save statuses from WhatsApp and WhatsApp Business") { onOpenStatus() } }

        item { SectionLabel("Downloads") }
        item { ValueRow("Save videos to", Storage.outputDir(context, s, Kind.VIDEO).absolutePath) { dialog = "dir_video" } }
        item { ValueRow("Save music to", Storage.outputDir(context, s, Kind.AUDIO).absolutePath) { dialog = "dir_audio" } }
        item { ValueRow("Default video quality", if (s.videoQuality == 0) "Best available" else "${s.videoQuality}p") { dialog = "quality" } }
        item { ValueRow("Default music format", "${s.audioFormat.uppercase()} ${if (s.audioFormat == "mp3") s.audioBitrate + " kbps" else ""}") { dialog = "audio" } }
        item { ValueRow("Downloads at the same time", "${s.maxConcurrent}") { dialog = "parallel" } }
        item { SwitchRow("Wi-Fi only", "Wait for Wi-Fi before downloading", s.wifiOnly) { scope.launch { repo.setWifiOnly(it) } } }
        item {
            SwitchRow(
                "Turbo mode (aria2c)", if (Engine.aria2Available) "Multi-connection downloads, often faster" else "Not available on this device",
                s.useAria2 && Engine.aria2Available
            ) { scope.launch { repo.setUseAria2(it) } }
        }
        item { SwitchRow("Embed subtitles", "Adds subtitles inside the video file when available", s.embedSubs) { scope.launch { repo.setEmbedSubs(it) } } }
        item { ValueRow("Subtitle languages", s.subLangs) { dialog = "subs" } }
        item { SwitchRow("Embed cover art", "Thumbnail as album art / video cover. Off = faster, smaller files", s.embedThumbnail) { scope.launch { repo.setEmbedThumbnail(it) } } }
        item {
            SwitchRow(
                "Embed metadata", "Title, artist, date and description tags. The source link is always kept",
                s.embedMetadata
            ) { scope.launch { repo.setEmbedMetadata(it) } }
        }
        item {
            SwitchRow(
                "Keep downloading after closing", "Off = closing Grabix from recents stops downloads; they resume next time",
                s.downloadAfterClose
            ) { scope.launch { repo.setDownloadAfterClose(it) } }
        }

        item { SectionLabel("Player") }
        item { SwitchRow("Background play", "Keep playing when you leave the player or the browser", s.backgroundPlay) { scope.launch { repo.setBackgroundPlay(it) } } }
        item { SwitchRow("Auto-play next", "Start the next video or song when one ends", s.autoPlayNext) { scope.launch { repo.setAutoPlayNext(it) } } }
        item { SwitchRow("Auto picture-in-picture", "Float the video when you press Home", s.autoPip) { scope.launch { repo.setAutoPip(it) } } }

        item { SectionLabel("Browser & privacy") }
        item { SwitchRow("Block ads in browser", "Blocks common ad and tracker hosts", s.blockAds) { scope.launch { repo.setBlockAds(it) } } }
        item { ValueRow("Private folder", if (s.vaultPinHash == null) "Not set up" else "PIN set") { onOpenVault() } }
        if (s.vaultPinHash != null) item {
            SwitchRow("Unlock with fingerprint / face", "Uses your phone's screen lock", s.vaultBiometric) { scope.launch { repo.setVaultBiometric(it) } }
        }

        item { SectionLabel("Google account") }
        item {
            SwitchRow(
                "microG services",
                when {
                    !s.microG -> "Sign in to Google in the browser through microG (GmsCore), like ReVanced"
                    microGApps.isEmpty() -> "microG not found. Install GmsCore, or Google's sign-in page is used"
                    else -> "Using " + microGApps.joinToString { it.label }
                },
                s.microG
            ) { scope.launch { repo.setMicroG(it) } }
        }
        if (s.microG) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Button(onClick = { microGLogin() }) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(18.dp))
                    Text("  Sign in with Google")
                }
            }
        }

        item { SectionLabel("Appearance") }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                val modes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    modes.forEachIndexed { i, (key, label) ->
                        SegmentedButton(
                            selected = s.theme == key,
                            onClick = { scope.launch { repo.setTheme(key) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size)
                        ) { Text(label) }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
            SwitchRow("Material You colours", "Match colours to your wallpaper", s.dynamicColor) { scope.launch { repo.setDynamicColor(it) } }
        }

        item { SectionLabel("Backup & restore") }
        item { ValueRow("Export settings & playlists", "Saves a simple .txt file you can keep or share") { exportLauncher.launch(Backup.suggestedName()) } }
        item { ValueRow("Restore from file", "Reads a Grabix backup .txt file") { importLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) } }

        item { SectionLabel("Download engine") }
        item {
            val ver = when (val e = engine) {
                is EngineState.Ready -> "yt-dlp ${e.version ?: "?"}"
                is EngineState.Error -> "Error: ${e.message}"
                EngineState.Initializing -> "Starting…"
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Engine version", style = MaterialTheme.typography.titleSmall)
                    Text(ver, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (updating) CircularProgressIndicator(Modifier.padding(8.dp))
                else TextButton(onClick = {
                    updating = true
                    scope.launch {
                        val r = Engine.update(context)
                        if (!r.startsWith("Update failed")) repo.setLastUpdate(System.currentTimeMillis())
                        updating = false
                        vm.toast(r)
                    }
                }) { Text("Update now") }
            }
        }
        item { SwitchRow("Auto-update engine", "Checks once a day so sites keep working", s.autoUpdateEngine) { scope.launch { repo.setAutoUpdate(it) } } }

        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Text(
                "Grabix ${BuildConfig.VERSION_NAME} · No ads · No tracking · No extra apps needed.\n" +
                    "Download only content you own or have permission to save, and follow each site's terms.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    when (dialog) {
        "dir_video", "dir_audio" -> {
            val kind = if (dialog == "dir_audio") Kind.AUDIO else Kind.VIDEO
            val hasSd = Storage.hasSdCard(context)
            val labels = buildList {
                add("Phone · ${Storage.defaultPhoneDir(kind).path.substringAfter("0/")}")
                if (hasSd) add("SD card · app folder")
                add("Choose a folder…")
            }
            ChoiceDialog(if (kind == Kind.AUDIO) "Save music to" else "Save videos to", labels, -1,
                onPick = { i ->
                    val last = labels.lastIndex
                    dialog = null
                    when {
                        i == last -> { pickKind = kind; treePicker.launch(null) }
                        i == 0 -> scope.launch {
                            if (kind == Kind.AUDIO) repo.setAudioDir(null) else repo.setVideoDir(null)
                            repo.setStorage("internal")
                        }
                        else -> Storage.sdDir(context, kind)?.let { d ->
                            scope.launch { if (kind == Kind.AUDIO) repo.setAudioDir(d.path) else repo.setVideoDir(d.path) }
                        }
                    }
                }, onDismiss = { dialog = null })
        }
        "quality" -> {
            val q = listOf(0, 2160, 1440, 1080, 720, 480, 360, 240, 144)
            ChoiceDialog("Default video quality", q.map { if (it == 0) "Best available" else "${it}p" }, q.indexOf(s.videoQuality),
                onPick = { scope.launch { repo.setVideoQuality(q[it]) }; dialog = null }, onDismiss = { dialog = null })
        }
        "audio" -> {
            val opts = listOf("mp3" to "320", "mp3" to "192", "mp3" to "128", "m4a" to "0", "best" to "0")
            ChoiceDialog("Default music format", listOf("MP3 320 kbps", "MP3 192 kbps", "MP3 128 kbps", "M4A (AAC)", "Original"),
                opts.indexOf(s.audioFormat to s.audioBitrate),
                onPick = { scope.launch { repo.setAudioFormat(opts[it].first); repo.setAudioBitrate(opts[it].second) }; dialog = null },
                onDismiss = { dialog = null })
        }
        "parallel" -> ChoiceDialog("Downloads at the same time", listOf("1", "2", "3", "4", "5"), s.maxConcurrent - 1,
            onPick = { scope.launch { repo.setMaxConcurrent(it + 1) }; dialog = null }, onDismiss = { dialog = null })
        "subs" -> {
            val opts = listOf("en.*,en", "hi,en.*", "bn,en.*", "all")
            ChoiceDialog("Subtitle languages", listOf("English", "Hindi + English", "Bengali + English", "All available"),
                opts.indexOf(s.subLangs),
                onPick = { scope.launch { repo.setSubLangs(opts[it]) }; dialog = null }, onDismiss = { dialog = null })
        }
        "theme" -> {
            val t = listOf("system", "light", "dark")
            ChoiceDialog("Theme", listOf("System", "Light", "Dark"), t.indexOf(s.theme),
                onPick = { scope.launch { repo.setTheme(t[it]) }; dialog = null }, onDismiss = { dialog = null })
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
