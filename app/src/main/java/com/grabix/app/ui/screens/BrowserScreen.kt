package com.grabix.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grabix.app.ui.MainViewModel
import com.grabix.app.util.Links
import java.io.ByteArrayInputStream
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.grabix.app.player.BrowserAudioService

/** Holds the WebView so the page survives switching tabs. */
class BrowserHolder(private val context: Context) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blockAds = false
        private set
    var backgroundPlay = false
        private set
    private var adScript: ScriptHandler? = null
    private var bgScript: ScriptHandler? = null
    private val docStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val mainHandler = Handler(Looper.getMainLooper())
    var mediaPlaying by mutableStateOf(false)
        private set
    var desktopMode by mutableStateOf(false)
    /** microG mode: hide the WebView marker so Google sign-in pages work. */
    var microG = false
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                // intent:// and app links: hand to the system.
                runCatching {
                    val i = if (scheme == "intent") Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, request.url)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    view.context.startActivity(i)
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (blockAds && AdBlock.isAd(request.url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                sync(view)
                // Older WebViews can't run scripts at document start; inject as early as we can.
                if (!docStartSupported) {
                    if (blockAds && url.contains("youtube.com")) view.evaluateJavascript(BrowserScripts.YOUTUBE_ADBLOCK, null)
                    if (backgroundPlay) view.evaluateJavascript(BrowserScripts.BACKGROUND_PLAY, null)
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                sync(view)
                CookieManager.getInstance().flush()
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { sync(view) }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { this@BrowserHolder.progress = newProgress / 100f }
            override fun onReceivedTitle(view: WebView, t: String?) { this@BrowserHolder.title = t ?: "" }
        }
    }

    private val mobileUa = webView.settings.userAgentString

    /** Called from page JavaScript, so it must stay tiny and harmless. */
    private inner class Bridge {
        @JavascriptInterface
        fun media(playing: Boolean) {
            mainHandler.post { onMedia(playing) }
        }
    }

    init {
        webView.addJavascriptInterface(Bridge(), "GrabixBridge")
        current = java.lang.ref.WeakReference(this)
    }

    private fun onMedia(playing: Boolean) {
        mediaPlaying = playing
        if (!backgroundPlay) return
        if (playing) BrowserAudioService.start(context, title) else BrowserAudioService.stop(context)
    }

    private fun install(js: String, origins: Set<String>): ScriptHandler? =
        if (docStartSupported) runCatching { WebViewCompat.addDocumentStartJavaScript(webView, js, origins) }.getOrNull() else null

    fun applyBlockAds(on: Boolean) {
        if (on == blockAds) return
        blockAds = on
        if (on) adScript = install(BrowserScripts.YOUTUBE_ADBLOCK, setOf("https://*.youtube.com", "https://youtube.com"))
        else { adScript?.remove(); adScript = null }
    }

    fun applyBackgroundPlay(on: Boolean) {
        if (on == backgroundPlay) return
        backgroundPlay = on
        if (on) bgScript = install(BrowserScripts.BACKGROUND_PLAY, setOf("*"))
        else {
            bgScript?.remove(); bgScript = null
            BrowserAudioService.stop(context)
        }
    }

    /** Used by the notification button. */
    fun pauseMedia() { webView.post { webView.evaluateJavascript(BrowserScripts.PAUSE_ALL, null) } }
    fun playMedia() { webView.post { webView.evaluateJavascript(BrowserScripts.PLAY_FIRST, null) } }

    companion object {
        var current: java.lang.ref.WeakReference<BrowserHolder>? = null
    }

    fun sync(v: WebView) {
        url = v.url ?: url
        title = v.title ?: title
        canGoBack = v.canGoBack()
        canGoForward = v.canGoForward()
    }

    fun load(raw: String) {
        val t = raw.trim()
        val target = if (Links.looksLikeUrl(t)) Links.normalize(t)
        else "https://www.google.com/search?q=" + Uri.encode(t)
        webView.loadUrl(target)
        url = target
    }

    private fun applyUserAgent() {
        webView.settings.userAgentString = when {
            desktopMode -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
            microG -> com.grabix.app.util.MicroG.browserUserAgent(mobileUa)
            else -> mobileUa
        }
    }

    fun toggleDesktop() {
        desktopMode = !desktopMode
        applyUserAgent()
        webView.reload()
    }

    fun applyMicroG(on: Boolean) {
        if (on == microG) return
        microG = on
        applyUserAgent()
    }
}

/** Small built-in list of common ad / tracker hosts and request paths. */
object AdBlock {
    private val paths = listOf(
        "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_", "/youtubei/v1/player/ad_break",
        "/ads/ga-audiences", "doubleclick.net/", "/api/stats/atr",
    )

    private val hosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.",
        "pagead2.googlesyndication.com", "adnxs.com", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "mgid.com", "revcontent.com", "zedo.com", "criteo.com", "criteo.net", "amazon-adsystem.com",
        "moatads.com", "scorecardresearch.com", "adform.net", "rubiconproject.com", "pubmatic.com",
        "openx.net", "smartadserver.com", "advertising.com", "yieldmo.com", "inmobi.com",
        "applovin.com", "unityads.unity3d.com", "ads.yahoo.com", "media.net", "onclickads.net",
        "imasdk.googleapis.com", "2mdn.net", "googleadservices.com", "adsrvr.org", "casalemedia.com", "ads.youtube.com",
    )

    fun isAd(uri: Uri): Boolean {
        val h = uri.host ?: return false
        val full = uri.toString()
        if (h.endsWith("youtube.com") && paths.any { full.contains(it) }) return true
        return hosts.any { h == it || h.endsWith(".$it") || (it.endsWith(".") && h.contains(it)) }
    }
}

/** Sites where the current page is very likely a single downloadable video. */
private fun isVideoPage(url: String): Boolean {
    val u = url.lowercase()
    if (!u.startsWith("http")) return false
    return listOf(
        "/watch", "youtu.be/", "/shorts/", "/reel", "/p/", "/tv/", "/videos/", "/video/", "/status/",
        "fb.watch", "/clip/", "vimeo.com/", "dailymotion.com/video", "soundcloud.com/", "/v/", "/embed/",
        "/comments/", "pin.it", "/pin/",
    ).any { u.contains(it) }
}

@Composable
fun BrowserScreen(holder: BrowserHolder, vm: MainViewModel) {
    val focus = LocalFocusManager.current
    val settings by vm.settings.collectAsState()
    var address by remember { mutableStateOf(holder.url) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val microGLogin = com.grabix.app.ui.components.rememberMicroGLogin(vm)

    LaunchedEffect(holder.url, editing) { if (!editing) address = holder.url }
    LaunchedEffect(Unit) { if (holder.url.isEmpty()) holder.load("https://m.youtube.com") }

    BackHandler(enabled = holder.canGoBack) { holder.webView.goBack() }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { holder.webView.goBack() }, enabled = holder.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    TextField(
                        value = address,
                        onValueChange = { address = it; editing = true },
                        singleLine = true,
                        placeholder = { Text("Search or type address") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            editing = false
                            focus.clearFocus()
                            holder.load(address)
                        }),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { holder.webView.reload() }) { Icon(Icons.Filled.Refresh, "Reload") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Forward") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                enabled = holder.canGoForward,
                                onClick = { menu = false; holder.webView.goForward() }
                            )
                            DropdownMenuItem(
                                text = { Text("Home") }, leadingIcon = { Icon(Icons.Filled.Home, null) },
                                onClick = { menu = false; vm.goTo("home") }
                            )
                            DropdownMenuItem(
                                text = { Text(if (holder.desktopMode) "Mobile site" else "Desktop site") },
                                onClick = { menu = false; holder.toggleDesktop() }
                            )
                            DropdownMenuItem(
                                text = { Text("Download this page's video") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = { menu = false; vm.analyze(holder.url) }
                            )
                            if (settings.microG) DropdownMenuItem(
                                text = { Text("Sign in with Google (microG)") },
                                leadingIcon = { Icon(Icons.Filled.AccountCircle, null) },
                                onClick = { menu = false; microGLogin() }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear cookies (sign out)") },
                                onClick = {
                                    menu = false
                                    CookieManager.getInstance().removeAllCookies(null)
                                    holder.webView.clearCache(true)
                                    vm.toast("Signed out of all sites")
                                }
                            )
                        }
                    }
                }
                if (holder.progress in 0.01f..0.99f) {
                    LinearProgressIndicator(progress = { holder.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    (holder.webView.parent as? ViewGroup)?.removeView(holder.webView)
                    holder.webView
                },
                modifier = Modifier.fillMaxSize()
            )
            val showFab = isVideoPage(holder.url)
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { vm.analyze(holder.url) },
                    icon = { Icon(Icons.Filled.Download, null) },
                    text = { Text("Download") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }
}
