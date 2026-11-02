package com.grabix.app.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import android.provider.MediaStore
import com.grabix.app.MainActivity
import com.grabix.app.ui.theme.isAppDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.grabix.app.GrabixApp
import com.grabix.app.R
import com.grabix.app.ui.theme.GrabixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Full-screen built-in player for video and music. */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private var controller: MediaController? = null

    private var controlsVisible by mutableStateOf(true)
    private var inPip by mutableStateOf(false)
    private var title by mutableStateOf("")
    private var isVideo by mutableStateOf(true)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffle by mutableStateOf(false)
    private var speed by mutableFloatStateOf(1f)
    private var gestureHint by mutableStateOf<String?>(null)
    private var autoPip = true
    private var backgroundPlay = true

    /** Audio files normally get a music-player look; this switches them to the video-player look. */
    private var fullscreen by mutableStateOf(false)
    private var appDark by mutableStateOf(true)
    private var locked by mutableStateOf(false)
    private var fullActive = false
    private var videoAspect by mutableFloatStateOf(16f / 9f)
    private var showSpeed by mutableStateOf(false)
    private var showSleep by mutableStateOf(false)
    private var showFit by mutableStateOf(false)
    private var fit by mutableStateOf(lastFit)
    private var videoW by mutableIntStateOf(0)
    private var videoH by mutableIntStateOf(0)
    private var autoNext by mutableStateOf(true)
    /** Web page the current item came from (downloads only). */
    private var sourceUrl by mutableStateOf<String?>(null)
    private var sourceFor: String? = null

    companion object {
        /** VLC keeps the chosen fit for the session; so does Grabix. */
        private var lastFit = VideoFit.BEST_FIT
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        showSystemBars()

        playerView = PlayerView(this).apply {
            setShowNextButton(true)
            setShowPreviousButton(true)
            setShowSubtitleButton(true)
            setShowShuffleButton(false)
            controllerShowTimeoutMs = 3500
            controllerAutoShow = true
            artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FIT
            defaultArtwork = getDrawable(R.drawable.ic_launcher_foreground)
            setKeepContentOnPlayerReset(true)
            useController = false // Grabix draws its own controls
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                controlsVisible = v == View.VISIBLE
            })
            setFullscreenButtonClickListener { toggleRotation() }
            resizeMode = lastFit.resizeMode
        }
        attachGestures()

        lifecycleScope.launch {
            GrabixApp.instance.settings.flow.collect {
                autoPip = it.autoPip; backgroundPlay = it.backgroundPlay; autoNext = it.autoPlayNext
            }
        }
        lifecycleScope.launch {
            handleIncoming(intent)
            val c = PlayerConnection.get(this@PlayerActivity)
            controller = c
            playerView.player = c
            c.addListener(listener)
            syncState(c)
        }

        setContent {
            val s by GrabixApp.instance.settings.flow.collectAsState(initial = null)
            val theme = s?.theme ?: "system"
            appDark = isAppDark(theme)
            GrabixTheme(theme, dynamic = s?.dynamicColor ?: true, controlBars = false) {
                PlayerScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch { handleIncoming(intent) }
    }

    /** "Open with Grabix Player" from a file manager or another app. */
    private suspend fun handleIncoming(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        val mime = intent.type ?: contentResolver.getType(data) ?: ""
        val name = queryName(data) ?: data.lastPathSegment ?: "Media"
        PlayerConnection.play(
            this,
            listOf(PlayItem(data.toString(), name, null, isVideo = !mime.startsWith("audio"))),
            0,
            openScreen = false
        )
        intent.data = null
    }

    private fun queryName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun syncState(p: Player) {
        title = p.mediaMetadata.title?.toString() ?: ""
        isVideo = p.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_MUSIC
        repeatMode = p.repeatMode
        shuffle = p.shuffleModeEnabled
        speed = p.playbackParameters.speed
        p.videoSize.let {
            if (it.width > 0 && it.height > 0) {
                videoAspect = (it.width * it.pixelWidthHeightRatio / it.height)
                videoW = (it.width * it.pixelWidthHeightRatio).toInt()
                videoH = it.height
            }
        }
        refreshSource(p)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { setPictureInPictureParams(pipParams()) }
        }
    }

    // ---------- gestures: double-tap seek, swipe brightness / volume ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var startVol = 0
        var startBright = 0.5f
        var verticalSide = 0 // -1 left (brightness), 1 right (volume), 0 none

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val cur = window.attributes.screenBrightness
                startBright = if (cur < 0) 0.5f else cur
                verticalSide = 0
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                controlsVisible = !controlsVisible
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!fullActive || locked) return true
                val c = controller ?: return true
                val w = playerView.width
                when {
                    e.x < w / 3f -> { c.seekBack(); hint("⟲ 10s") }
                    e.x > w * 2 / 3f -> { c.seekForward(); hint("10s ⟳") }
                    else -> if (c.isPlaying) c.pause() else c.play()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || inPip || !fullActive || locked) return false
                val totalDy = e1.y - e2.y
                val totalDx = e2.x - e1.x
                if (verticalSide == 0) {
                    if (abs(totalDy) < 30 || abs(totalDy) < abs(totalDx)) return false
                    verticalSide = if (e1.x < playerView.width / 2f) -1 else 1
                }
                val frac = totalDy / playerView.height.coerceAtLeast(1)
                if (verticalSide < 0) {
                    val b = (startBright + frac).coerceIn(0.01f, 1f)
                    window.attributes = window.attributes.apply { screenBrightness = b }
                    hint("Brightness ${(b * 100).toInt()}%")
                } else {
                    val v = (startVol + frac * maxVol * 1.5f).toInt().coerceIn(0, maxVol)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                    hint("Volume ${v * 100 / maxVol.coerceAtLeast(1)}%")
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    private fun hint(text: String) {
        gestureHint = text
    }

    // ---------- PiP / rotation ----------

    private fun pipParams(): PictureInPictureParams {
        val vs = controller?.videoSize
        val ratio = if (vs != null && vs.width > 0 && vs.height > 0) {
            val r = vs.width.toFloat() / vs.height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational(vs.width, vs.height)
            }
        } else Rational(16, 9)
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setAutoEnterEnabled(controller?.isPlaying == true && isVideo)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun enterPip() {
        if (packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPip && isVideo && controller?.isPlaying == true && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    private fun toggleRotation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun applyFit(f: VideoFit) {
        fit = f
        lastFit = f
        if (!inPip) playerView.resizeMode = f.resizeMode
        hint(f.label)
    }

    /** Finds the page a file was downloaded from: item extras first, then the downloads table. */
    private fun refreshSource(p: Player) {
        val item = p.currentMediaItem ?: run { sourceUrl = null; sourceFor = null; return }
        val id = item.mediaId
        if (id == sourceFor) return
        sourceFor = id
        val fromExtras = item.mediaMetadata.extras?.getString(PlayItem.EXTRA_SOURCE)
        if (!fromExtras.isNullOrBlank()) { sourceUrl = fromExtras; return }
        sourceUrl = null
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val path = filePathOf(Uri.parse(id)) ?: return@runCatching null
                    GrabixApp.instance.db.downloads().sourceForPath(path)
                }.getOrNull()
            }
            if (sourceFor == id) sourceUrl = found
        }
    }

    private fun filePathOf(uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path
        "content" -> runCatching {
            @Suppress("DEPRECATION")
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        null -> uri.path?.takeIf { File(it).exists() }
        else -> null
    }

    private fun openSource() {
        val url = sourceUrl ?: return
        controller?.pause()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun toggleAutoNext() {
        val v = !autoNext
        autoNext = v
        lifecycleScope.launch { GrabixApp.instance.settings.setAutoPlayNext(v) }
        hint(if (v) "Auto-play next: on" else "Auto-play next: off")
    }

    private fun showSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.show(WindowInsetsCompat.Type.systemBars())
        // Windowed player follows the app theme; full screen is always on black.
        val light = !appDark && !fullActive
        c.isAppearanceLightStatusBars = light
        c.isAppearanceLightNavigationBars = light
    }

    private fun enterFullscreen() {
        fullscreen = true
        controlsVisible = true
        if (isVideo) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun exitFullscreen() {
        fullscreen = false
        locked = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Release the portrait lock shortly after so auto-rotate keeps working.
        lifecycleScope.launch { delay(1000); requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullActive) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        controller?.let { PlayerConnection.savePosition(it) }
        if (!isInPictureInPictureMode) {
            if (!backgroundPlay) controller?.pause()
        }
    }

    override fun onDestroy() {
        controller?.removeListener(listener)
        playerView.player = null
        super.onDestroy()
    }

    // ---------- UI ----------

    @Composable
    private fun PlayerScreen() {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val full = isVideo && (fullscreen || landscape) && !inPip // music never goes full screen
        SideEffect { fullActive = full }
        LaunchedEffect(full, appDark) { if (full) hideSystemBars() else showSystemBars() }
        // Picture-in-picture always shows the whole picture; forced ratios would stretch it there.
        LaunchedEffect(inPip, fit) {
            playerView.resizeMode = if (inPip) AspectRatioFrameLayout.RESIZE_MODE_FIT else fit.resizeMode
        }

        val ticker = remember { Ticker() }
        LaunchedEffect(Unit) {
            while (true) {
                controller?.let { c ->
                    ticker.playing = c.isPlaying
                    ticker.dur = c.duration.coerceAtLeast(0L)
                    if (!ticker.dragging) ticker.pos = c.currentPosition.coerceAtLeast(0L)
                }
                delay(400)
            }
        }
        LaunchedEffect(gestureHint) {
            if (gestureHint != null) { delay(900); gestureHint = null }
        }
        // VLC-style: controls fade away while playing in full screen.
        LaunchedEffect(controlsVisible, ticker.playing, full) {
            if (full && controlsVisible && ticker.playing) { delay(3500); controlsVisible = false }
        }
        BackHandler(enabled = full) { exitFullscreen() }
        BackHandler(enabled = locked) { /* ignored while the screen is locked */ }

        // One video surface that is moved between the windowed and full-screen layouts.
        val surface = remember { movableContentOf<Modifier> { m -> AndroidView(factory = { playerView }, modifier = m) } }

        Box(Modifier.fillMaxSize().background(if (full || inPip) Color.Black else MaterialTheme.colorScheme.background)) {
            when {
                inPip -> surface(Modifier.fillMaxSize())
                full -> FullscreenLook(surface, ticker)
                else -> WindowedLook(surface, ticker)
            }
            gestureHint?.let {
                Text(
                    it, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        if (showSpeed) SpeedDialog(
            current = speed,
            onPick = { controller?.setPlaybackSpeed(it) },
            onDismiss = { showSpeed = false }
        )
        if (showSleep) {
            val mins = listOf(0, 10, 15, 30, 45, 60, 90)
            ChoiceDialog(
                title = "Sleep timer",
                options = mins.map { if (it == 0) "Off" else "$it minutes" },
                selected = -1,
                onPick = { PlayerConnection.setSleepTimer(mins[it]); showSleep = false; hint(if (mins[it] == 0) "Timer off" else "Stops in ${mins[it]} min") },
                onDismiss = { showSleep = false }
            )
        }
        if (showFit) FitDialog(
            current = fit,
            onPick = { applyFit(it); showFit = false },
            onDismiss = { showFit = false }
        )
    }

    /**
     * Places the video surface according to the chosen fit. Forced ratios and "Original"
     * size the view in Compose; the other modes use the player's own resize modes.
     */
    @Composable
    private fun VideoArea(surface: @Composable (Modifier) -> Unit, modifier: Modifier) {
        val noRipple = remember { MutableInteractionSource() }
        BoxWithConstraints(
            modifier.clip(RoundedCornerShape(0.dp))
                // Taps on the black bars around a resized picture still toggle the controls.
                .clickable(interactionSource = noRipple, indication = null) { controlsVisible = !controlsVisible },
            contentAlignment = Alignment.Center
        ) {
            val ratio = fit.ratio
            when {
                ratio != null -> {
                    val boxRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
                    val m = if (boxRatio > ratio) Modifier.fillMaxHeight().aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    else Modifier.fillMaxWidth().aspectRatio(ratio)
                    surface(m)
                }
                fit == VideoFit.ORIGINAL && videoW > 0 && videoH > 0 -> {
                    val d = LocalDensity.current
                    surface(Modifier.requiredSize(with(d) { videoW.toDp() }, with(d) { videoH.toDp() }))
                }
                else -> surface(Modifier.fillMaxSize())
            }
        }
    }

    // ---------- windowed look: identical for music and video, follows the app theme ----------

    @Composable
    private fun WindowedLook(surface: @Composable (Modifier) -> Unit, t: Ticker) {
        val meta = controller?.mediaMetadata
        val artist = meta?.artist?.toString().orEmpty()
        val cs = MaterialTheme.colorScheme
        CompositionLocalProvider(LocalContentColor provides cs.onBackground) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.background)))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(
                        "Now playing", color = cs.onSurfaceVariant, style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (sourceUrl != null) SourceBtn()
                    if (isVideo) AspectBtn()
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isVideo) {
                        val ar = (fit.ratio ?: videoAspect).coerceIn(0.4f, 2.6f)
                        val m = if (ar >= 1f) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()
                        Box(m.aspectRatio(ar).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                            surface(Modifier.fillMaxSize())
                        }
                    } else {
                        ArtworkCard(meta?.artworkUri, Modifier.fillMaxWidth(0.85f))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    title, color = cs.onBackground, style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                if (artist.isNotBlank()) Text(
                    artist, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                SeekRow(t, inline = false)
                // Options sit between the seek bar and the transport controls.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    SpeedBtn(); SleepBtn(); AutoNextBtn()
                    if (isVideo) OptIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture") { enterPip() }
                    if (isVideo) OptIcon(Icons.Filled.Fullscreen, "Full screen") { enterFullscreen() }
                }
                TransportRow(t, extended = false, onVideo = false, playSize = 76, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
        }
    }

    // ---------- full-screen look: VLC style ----------

    @Composable
    private fun FullscreenLook(surface: @Composable (Modifier) -> Unit, t: Ticker) {
        val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val meta = controller?.mediaMetadata
        val noRipple = remember { MutableInteractionSource() }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (isVideo) {
                    VideoArea(surface, Modifier.fillMaxSize())
                } else {
                    // Music has no picture: show the artwork and let a tap toggle the controls.
                    Box(
                        Modifier.fillMaxSize().clickable(interactionSource = noRipple, indication = null) { controlsVisible = !controlsVisible },
                        contentAlignment = Alignment.Center
                    ) { ArtworkCard(meta?.artworkUri, Modifier.fillMaxHeight(0.55f)) }
                }

                if (locked) {
                    AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                        IconButton(
                            onClick = { locked = false },
                            modifier = Modifier.padding(24.dp).size(56.dp).clip(RoundedCornerShape(28.dp)).background(Color(0x99000000))
                        ) { Icon(Icons.Filled.LockOpen, "Unlock", tint = Color.White) }
                    }
                    return@Box
                }

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { exitFullscreen() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Text(
                            title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                        )
                        if (sourceUrl != null) SourceBtn()
                        SpeedBtn(); SleepBtn(); AutoNextBtn()
                    }
                }

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SeekRow(t, inline = true)
                        if (wide) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1.2f)) { LockBtn(); if (isVideo) AspectBtn(); RepeatBtn(); ShuffleBtn() }
                                TransportRow(t, extended = true, onVideo = true, playSize = 60, modifier = Modifier.weight(1.6f))
                                Row(Modifier.weight(1.2f), horizontalArrangement = Arrangement.End) {
                                    if (isVideo) { RotateBtn(); PipBtn() }; ExitFullBtn()
                                }
                            }
                        } else {
                            TransportRow(t, extended = true, onVideo = true, playSize = 60, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                LockBtn(); if (isVideo) AspectBtn(); RepeatBtn(); ShuffleBtn(); if (isVideo) { RotateBtn(); PipBtn() }; ExitFullBtn()
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- shared pieces ----------

    @Composable
    private fun ArtworkCard(art: Uri?, modifier: Modifier) {
        Box(
            modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(96.dp)
            )
            if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun SeekRow(t: Ticker, inline: Boolean) {
        val shown = if (t.dragging) t.dragValue.toLong() else t.pos
        val slider: @Composable (Modifier) -> Unit = { m ->
            Slider(
                modifier = m,
                value = if (t.dur > 0) shown.coerceIn(0L, t.dur).toFloat() else 0f,
                onValueChange = { t.dragging = true; t.dragValue = it },
                onValueChangeFinished = { controller?.seekTo(t.dragValue.toLong()); t.pos = t.dragValue.toLong(); t.dragging = false },
                valueRange = 0f..(if (t.dur > 0) t.dur.toFloat() else 1f),
                enabled = t.dur > 0
            )
        }
        val faint = LocalContentColor.current.copy(alpha = 0.7f)
        if (inline) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(shown), color = LocalContentColor.current, style = MaterialTheme.typography.labelMedium)
                slider(Modifier.weight(1f).padding(horizontal = 8.dp))
                Text(fmtTime(t.dur), color = LocalContentColor.current, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                slider(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(shown), color = faint, style = MaterialTheme.typography.labelSmall)
                    Text(fmtTime(t.dur), color = faint, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    /**
     * Previous / play / next. On video (full screen) the play button is white on black;
     * in the windowed player it uses the theme's primary colour. The windowed row also
     * carries shuffle and repeat at its ends.
     */
    @Composable
    private fun TransportRow(t: Ticker, extended: Boolean, onVideo: Boolean, playSize: Int, modifier: Modifier) {
        val cs = MaterialTheme.colorScheme
        val playBg = if (onVideo) Color.White else cs.primary
        val playFg = if (onVideo) Color.Black else cs.onPrimary
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (!extended) ShuffleBtn()
            IconButton(onClick = { controller?.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
            }
            if (extended) IconButton(onClick = { controller?.seekBack(); hint("⟲ 10s") }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds", modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                modifier = Modifier.size(playSize.dp).clip(RoundedCornerShape((playSize / 2).dp)).background(playBg)
            ) {
                Icon(
                    if (t.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (t.playing) "Pause" else "Play", tint = playFg, modifier = Modifier.size((playSize * 0.58f).dp)
                )
            }
            if (extended) IconButton(onClick = { controller?.seekForward(); hint("10s ⟳") }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { controller?.seekToNext() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }
            if (!extended) RepeatBtn()
        }
    }

    @Composable
    private fun OptIcon(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
        val activeColor = if (fullActive) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary
        IconButton(onClick = onClick) {
            Icon(icon, desc, tint = if (active) activeColor else LocalContentColor.current)
        }
    }

    @Composable private fun SpeedBtn() = OptIcon(Icons.Filled.Speed, "Playback speed", speed != 1f) { showSpeed = true }

    @Composable
    private fun SleepBtn() {
        val sleepAt by PlayerConnection.sleepEndsAt.collectAsState()
        OptIcon(Icons.Filled.Bedtime, "Sleep timer", sleepAt > 0) { showSleep = true }
    }

    @Composable
    private fun RepeatBtn() = OptIcon(
        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
        repeatMode != Player.REPEAT_MODE_OFF
    ) {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    @Composable
    private fun ShuffleBtn() = OptIcon(Icons.Filled.Shuffle, "Shuffle", shuffle) {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    @Composable
    private fun AutoNextBtn() = OptIcon(
        Icons.AutoMirrored.Filled.PlaylistPlay,
        if (autoNext) "Auto-play next is on" else "Auto-play next is off", autoNext
    ) { toggleAutoNext() }

    @Composable private fun SourceBtn() = OptIcon(Icons.Filled.OpenInBrowser, "Open source page") { openSource() }
    @Composable private fun LockBtn() = OptIcon(Icons.Filled.Lock, "Lock controls") { locked = true; controlsVisible = true }
    @Composable private fun AspectBtn() = OptIcon(Icons.Filled.AspectRatio, "Fit / aspect ratio", fit != VideoFit.BEST_FIT) { showFit = true }
    @Composable private fun RotateBtn() = OptIcon(Icons.Filled.ScreenRotation, "Rotate") { toggleRotation() }
    @Composable private fun PipBtn() = OptIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture") { enterPip() }
    @Composable private fun ExitFullBtn() = OptIcon(Icons.Filled.FullscreenExit, "Exit full screen") { exitFullscreen() }

    private fun fmtTime(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/** VLC-style video fit modes and forced aspect ratios. */
@OptIn(UnstableApi::class)
enum class VideoFit(val label: String, val resizeMode: Int, val ratio: Float? = null) {
    BEST_FIT("Best fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FIT_WIDTH("Fit width", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIT_HEIGHT("Fit height", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
    FILL("Fill screen (crop)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ORIGINAL("Original size (100%)", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    R16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FILL, 16f / 9f),
    R4_3("4:3", AspectRatioFrameLayout.RESIZE_MODE_FILL, 4f / 3f),
    R1_1("1:1", AspectRatioFrameLayout.RESIZE_MODE_FILL, 1f),
    R16_10("16:10", AspectRatioFrameLayout.RESIZE_MODE_FILL, 16f / 10f),
    R5_4("5:4", AspectRatioFrameLayout.RESIZE_MODE_FILL, 5f / 4f),
    R3_2("3:2", AspectRatioFrameLayout.RESIZE_MODE_FILL, 3f / 2f),
    R21_9("21:9", AspectRatioFrameLayout.RESIZE_MODE_FILL, 21f / 9f),
    R221("2.21:1", AspectRatioFrameLayout.RESIZE_MODE_FILL, 2.21f),
    R235("2.35:1", AspectRatioFrameLayout.RESIZE_MODE_FILL, 2.35f),
    R239("2.39:1", AspectRatioFrameLayout.RESIZE_MODE_FILL, 2.39f),
    R9_16("9:16 (vertical)", AspectRatioFrameLayout.RESIZE_MODE_FILL, 9f / 16f),
}

/** Playback position state shared by the seek bar and play button. */
private class Ticker {
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var dragValue by mutableFloatStateOf(0f)
}

/** Fit modes on the left column, aspect ratios as chips; scrolls so it fits in landscape. */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FitDialog(current: VideoFit, onPick: (VideoFit) -> Unit, onDismiss: () -> Unit) {
    val modes = VideoFit.entries.filter { it.ratio == null }
    val ratios = VideoFit.entries.filter { it.ratio != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video fit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { f -> FilterChip(selected = f == current, onClick = { onPick(f) }, label = { Text(f.label) }) }
                }
                Text(
                    "Aspect ratio", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ratios.forEach { f -> FilterChip(selected = f == current, onClick = { onPick(f) }, label = { Text(f.label) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * Speed picker that fits on any screen: preset chips that wrap, plus a fine slider.
 * The old list dialog was cut off in landscape full screen.
 */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)
    var value by remember { mutableFloatStateOf(current) }
    fun label(v: Float) = if (v == 1f) "Normal" else "${String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')}x"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed · ${label(value)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        FilterChip(
                            selected = kotlin.math.abs(p - value) < 0.001f,
                            onClick = { value = p; onPick(p) },
                            label = { Text(label(p)) }
                        )
                    }
                }
                Slider(
                    value = value,
                    onValueChange = { value = (Math.round(it * 20f) / 20f).coerceIn(0.25f, 4f) },
                    onValueChangeFinished = { onPick(value) },
                    valueRange = 0.25f..4f,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { value = 1f; onPick(1f) }) { Text("Reset") } }
    )
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Scrolls so long lists stay reachable in landscape.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, label ->
                    TextButton(onClick = { onPick(i) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
