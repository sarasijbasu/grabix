package com.grabix.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.grabix.app.data.Status
import com.grabix.app.download.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.grabix.app.ui.MainViewModel
import com.grabix.app.ui.components.FormatSheet
import com.grabix.app.ui.components.MiniPlayer
import com.grabix.app.ui.screens.BrowserHolder
import com.grabix.app.ui.screens.BrowserScreen
import com.grabix.app.ui.screens.DownloadsScreen
import com.grabix.app.ui.screens.HomeScreen
import com.grabix.app.ui.screens.LibraryScreen
import com.grabix.app.ui.screens.SettingsScreen
import com.grabix.app.ui.screens.HistoryScreen
import com.grabix.app.ui.screens.StatusScreen
import com.grabix.app.ui.screens.VaultScreen
import com.grabix.app.ui.theme.GrabixTheme
import com.grabix.app.util.Links

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        /** Opens a page in the Grabix browser (used by the player's "Open source" button). */
        const val EXTRA_OPEN_URL = "open_url"
    }

    private val vm: MainViewModel by viewModels()
    private var requestedTab by mutableStateOf<String?>(null)
    private var requestedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        resumeQueue()

        setContent {
            val settings by vm.settings.collectAsState()
            GrabixTheme(settings.theme, dynamic = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppShell(vm, requestedTab, requestedUrl, onUrlHandled = { requestedUrl = null }) { requestedTab = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Downloads stopped by closing the app from recents resume when it is opened again.
     * (The process can survive the swipe, so GrabixApp.onCreate does not always run.)
     */
    private fun resumeQueue() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = GrabixApp.instance.db.downloads()
            if (DownloadService.activeIds().isEmpty()) dao.requeueInterrupted()
            if (dao.countUnfinished() > 0) DownloadService.start(this@MainActivity)
        }
    }

    /** Links shared from other apps go straight to the quality picker. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TAB)?.let { requestedTab = it }
        intent.getStringExtra(EXTRA_OPEN_URL)?.let { url ->
            intent.removeExtra(EXTRA_OPEN_URL)
            requestedUrl = url
            return
        }
        val link = when (intent.action) {
            Intent.ACTION_SEND -> Links.extract(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (link != null) {
            requestedTab = "home"
            vm.analyze(link)
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("home", "Home", Icons.Filled.Home),
    NavTab("browser", "Browse", Icons.Filled.Public),
    NavTab("downloads", "Downloads", Icons.Filled.Download),
    NavTab("history", "History", Icons.Filled.History),
    NavTab("library", "Library", Icons.Filled.VideoLibrary),
    NavTab("settings", "Me", Icons.Filled.Person),
)

@Composable
private fun AppShell(
    vm: MainViewModel,
    requestedTab: String?,
    requestedUrl: String?,
    onUrlHandled: () -> Unit,
    onTabHandled: () -> Unit,
) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf("home") }
    var showVault by rememberSaveable { mutableStateOf(false) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    val browser = remember { BrowserHolder(context) }
    val snackbar = remember { SnackbarHostState() }
    val analyze by vm.analyze.collectAsState()
    val settings by vm.settings.collectAsState()
    val active by GrabixApp.instance.db.downloads().observeActive().collectAsState(initial = emptyList())
    LaunchedEffect(settings.blockAds, settings.backgroundPlay) {
        browser.applyBlockAds(settings.blockAds)
        browser.applyBackgroundPlay(settings.backgroundPlay)
    }
    LaunchedEffect(settings.microG) { browser.applyMicroG(settings.microG) }
    val runningCount = active.count { it.status == Status.RUNNING || it.status == Status.QUEUED }

    // First-run permissions: notifications + media access.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        permLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(requestedTab) {
        requestedTab?.let { route = it; showVault = false; showStatus = false; onTabHandled() }
    }
    LaunchedEffect(requestedUrl) {
        requestedUrl?.let { browser.load(it); route = "browser"; showVault = false; showStatus = false; onUrlHandled() }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.navigate.collect { route = it; showVault = false; showStatus = false } }
    LaunchedEffect(Unit) { vm.browserUrl.collect { browser.load(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer()
                NavigationBar {
                    tabs.forEach { t ->
                        // Icons only: labels removed to keep the bar clean with six tabs.
                        NavigationBarItem(
                            selected = route == t.route && !showVault && !showStatus,
                            onClick = { route = t.route; showVault = false; showStatus = false },
                            icon = {
                                if (t.route == "downloads" && runningCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$runningCount") } }) { Icon(t.icon, t.label) }
                                } else Icon(t.icon, t.label)
                            },
                            label = null,
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showStatus) {
                StatusScreen(vm, onBack = { showStatus = false })
            } else if (showVault) {
                VaultScreen(onBack = { showVault = false })
            } else when (route) {
                "home" -> HomeScreen(vm, onOpenStatus = { showStatus = true })
                "browser" -> BrowserScreen(browser, vm)
                "downloads" -> DownloadsScreen()
                "history" -> HistoryScreen(asTab = true, onBack = { route = "home" })
                "library" -> LibraryScreen(onOpenVault = { showVault = true })
                "settings" -> SettingsScreen(vm, onOpenVault = { showVault = true }, onOpenStatus = { showStatus = true })
            }
        }
    }

    FormatSheet(analyze, settings, vm)
}
