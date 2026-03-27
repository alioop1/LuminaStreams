@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import android.graphics.Color as AndroidColor

val CustomSubtitlesIcon: ImageVector
    get() = ImageVector.Builder("Subtitles", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
            moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 14f); lineTo(4f, 14f); close()
            moveTo(10f, 12f); lineTo(20f, 12f); lineTo(20f, 14f); lineTo(10f, 14f); close()
            moveTo(14f, 16f); lineTo(20f, 16f); lineTo(20f, 18f); lineTo(14f, 18f); close()
            moveTo(4f, 16f); lineTo(12f, 16f); lineTo(12f, 18f); lineTo(4f, 18f); close()
        }
    }.build()

val CustomAudioIcon: ImageVector
    get() = ImageVector.Builder("Audio", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f); lineTo(12f, 14.5f)
            curveTo(11.6f, 14.2f, 11.1f, 14f, 10.5f, 14f)
            curveTo(9.1f, 14f, 8f, 15.1f, 8f, 16.5f)
            curveTo(8f, 17.9f, 9.1f, 19f, 10.5f, 19f)
            curveTo(11.9f, 19f, 13f, 17.9f, 13f, 16.5f)
            lineTo(13f, 6f); lineTo(17f, 6f); lineTo(17f, 3f); lineTo(12f, 3f); close()
        }
    }.build()

private val CTRL_BG      = Color(0x99000000)
private val RED          = Color(0xFFE50914)
private val WHITE        = Color(0xFFFFFFFF)
private val DIM          = Color(0xAAFFFFFF)
private val DV_BLUE      = Color(0xFF00B4FF)
private val ATMOS_PURPLE = Color(0xFF7B2FBE)

// ─── Aspect Ratio modes (KODI-style) ───────────────────────────────────────
enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    NATIVE("Native",      AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill",         AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom",         AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_16_9("16:9",   AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_4_3("4:3",     AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
    FIXED_21_9("21:9",   AspectRatioFrameLayout.RESIZE_MODE_FILL);

    fun next(): AspectRatioMode {
        val all = entries.toTypedArray()
        return all[(ordinal + 1) % all.size]
    }
}

enum class ActiveMenu { NONE, AUDIO, EMBEDDED_SUBS, WEB_SUBS, ASPECT_RATIO }

// ─── AFR helper ─────────────────────────────────────────────────────────────
private fun applyAfrForContent(activity: Activity, contentFps: Float) {
    val win     = activity.window ?: return
    val display = win.decorView.display ?: return
    val current = display.mode
    val targetFps = when {
        contentFps in 23.9f..24.1f -> 24f
        contentFps in 25f..25.1f   -> 25f
        contentFps in 29.9f..30.1f -> 30f
        contentFps in 47.9f..48.1f -> 48f
        contentFps in 49.9f..50.1f -> 50f
        contentFps in 59.9f..60.1f -> 60f
        else -> contentFps
    }
    val sameRes = display.supportedModes.filter {
        it.physicalWidth  == current.physicalWidth &&
        it.physicalHeight == current.physicalHeight
    }
    val best = sameRes.minByOrNull { abs(it.refreshRate - targetFps) }
        ?: display.supportedModes.minByOrNull { abs(it.refreshRate - targetFps) }
        ?: return
    if (best.modeId == current.modeId) return
    win.attributes = win.attributes.also { a -> a.preferredDisplayModeId = best.modeId }
}

private fun restoreDisplayMode(activity: Activity) {
    val win = activity.window ?: return
    win.attributes = win.attributes.also { a -> a.preferredDisplayModeId = 0 }
}

// ─── HDR Window setup for Dolby Vision ──────────────────────────────────────
private fun enableHdrWindow(activity: Activity) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            activity.window.attributes = activity.window.attributes.also { lp ->
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val cls = ActivityInfo::class.java
            val colorModeField = runCatching { cls.getField("COLOR_MODE_HDR") }.getOrNull()
            val hdrMode = colorModeField?.getInt(null) ?: 2
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val setColorMode = Activity::class.java.getMethod("setRequestedColorMode", Int::class.java)
            setColorMode.invoke(activity, hdrMode)
        }
    }
}

// ─── Apply Dolby Vision HDR type to SurfaceView (API 33+) ───────────────────
private fun applySurfaceDolbyVision(surfaceView: SurfaceView) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val method = SurfaceView::class.java.getMethod("setHdrOutputMode", Int::class.java)
            method.invoke(surfaceView, 3)
        }
    }
}

/**
 * Apply the chosen AspectRatioMode to an AspectRatioFrameLayout.
 * For FIXED_16_9 / FIXED_4_3 / FIXED_21_9 we override the layout ratio
 * explicitly so the ARFL knows exactly what to letterbox/pillarbox to.
 */
private fun applyResizeMode(
    arfl:           AspectRatioFrameLayout,
    mode:           AspectRatioMode,
    nativeVideoSize: VideoSize
) {
    when (mode) {
        AspectRatioMode.NATIVE -> {
            // Use the actual video dimensions reported by ExoPlayer.
            // This is the key fix for Dolby Vision: we never let the layout
            // stretch or zoom — we always use the real pixel ratio from the track.
            val w = nativeVideoSize.width.takeIf { it > 0 } ?: 1920
            val h = nativeVideoSize.height.takeIf { it > 0 } ?: 1080
            val sar = nativeVideoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            arfl.setAspectRatio(w.toFloat() / h.toFloat() * sar)
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        AspectRatioMode.FILL -> {
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
        AspectRatioMode.ZOOM -> {
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        AspectRatioMode.FIXED_16_9 -> {
            arfl.setAspectRatio(16f / 9f)
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        AspectRatioMode.FIXED_4_3 -> {
            arfl.setAspectRatio(4f / 3f)
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        AspectRatioMode.FIXED_21_9 -> {
            arfl.setAspectRatio(21f / 9f)
            arfl.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }
}

@Composable
fun PlayerScreen(
    videoUrl:       String,
    imdbId:         String,
    onNavigateBack: () -> Unit,
    viewModel:      PlayerViewModel = viewModel()
) {
    val context       = LocalContext.current
    val isRtl         = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state         by viewModel.state.collectAsState()
    val exo           = remember { ExoPlayerWrapper(context) }
    val isPlaying     by exo.isPlaying.collectAsState()
    val error         by exo.playerError.collectAsState()
    val currentTracks by exo.currentTracks.collectAsState()
    val currentCues   by exo.currentCues.collectAsState()
    val isDolbyVision by exo.isDolbyVision.collectAsState()
    val isDolbyAtmos  by exo.isDolbyAtmos.collectAsState()

    // ─── AFR ────────────────────────────────────────────────────────────
    val contentFps    by exo.contentFrameRate.collectAsState()
    val afrEnabled    = remember {
        context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            .getBoolean("afr", false)
    }

    // ─── Aspect ratio state ──────────────────────────────────────────────
    var aspectRatioMode  by remember { mutableStateOf(AspectRatioMode.NATIVE) }
    // Hold real video dimensions so NATIVE mode always uses them
    var nativeVideoSize  by remember { mutableStateOf(VideoSize.UNKNOWN) }
    // Reference to the AspectRatioFrameLayout so we can update it from compose
    val arflRef          = remember { mutableStateOf<AspectRatioFrameLayout?>(null) }

    var surfaceReady      by remember { mutableStateOf(false) }
    var prepared          by remember { mutableStateOf(false) }
    var showControls      by remember { mutableStateOf(true) }
    var activeMenu        by remember { mutableStateOf(ActiveMenu.NONE) }
    var activityTick      by remember { mutableIntStateOf(0) }
    var selectedWebSubUrl by remember { mutableStateOf<String?>(null) }
    var pendingSubIndex   by remember { mutableStateOf<Int?>(null) }
    var subtitleApplied   by remember { mutableStateOf(false) }

    // ─── AR badge toast state ─────────────────────────────────────────────
    var arToastLabel   by remember { mutableStateOf("") }
    var showArToast    by remember { mutableStateOf(false) }

    val watchPrefs    = remember { context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE) }
    val progressKey   = remember(imdbId) { "progress_$imdbId" }
    val savedPosition = remember(imdbId) { watchPrefs.getLong(progressKey, -1L) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumeHandled    by remember { mutableStateOf(false) }

    val backBtnFR   = remember { FocusRequester() }
    val seekBarFR   = remember { FocusRequester() }
    val firstPillFR = remember { FocusRequester() }
    val sideMenuFR  = remember { FocusRequester() }

    // ─── Capture real video size from ExoPlayer and keep NATIVE mode correct ─
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    nativeVideoSize = videoSize
                    // Always reapply current mode so NATIVE refreshes when DV track loads
                    arflRef.value?.let { arfl ->
                        applyResizeMode(arfl, aspectRatioMode, videoSize)
                    }
                }
            }
        }
        exo.player.addListener(listener)
        onDispose { exo.player.removeListener(listener) }
    }

    // ─── Reapply AR mode whenever it changes ─────────────────────────────────
    LaunchedEffect(aspectRatioMode) {
        arflRef.value?.let { applyResizeMode(it, aspectRatioMode, nativeVideoSize) }
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.let { enableHdrWindow(it) }
    }

    LaunchedEffect(videoUrl, imdbId) { viewModel.loadMedia(videoUrl, imdbId) }

    LaunchedEffect(surfaceReady) {
        if (surfaceReady && !prepared) {
            prepared = true
            exo.prepareStream(videoUrl)
            delay(3000)
            if (isPlaying) showControls = false
        }
    }

    LaunchedEffect(contentFps) {
        if (!afrEnabled || contentFps <= 0f) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        applyAfrForContent(activity, contentFps)
    }

    DisposableEffect(Unit) {
        onDispose {
            exo.release()
            if (afrEnabled) {
                (context as? Activity)?.let { restoreDisplayMode(it) }
            }
        }
    }

    LaunchedEffect(prepared) {
        if (prepared && savedPosition > 30_000L && !resumeHandled) {
            delay(800); showResumeDialog = true
        }
    }

    LaunchedEffect(prepared) {
        if (!prepared) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val pos = exo.player.currentPosition
            val dur = exo.player.duration
            if (pos > 10_000L && dur > 0L) {
                if (pos.toFloat() / dur.toFloat() < 0.95f)
                    watchPrefs.edit().putLong(progressKey, pos).apply()
                else
                    watchPrefs.edit().remove(progressKey).apply()
            }
        }
    }

    LaunchedEffect(state.availableSubtitles) {
        if (state.availableSubtitles.isEmpty() || subtitleApplied) return@LaunchedEffect
        val defLang = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            .getString("def_subs", "Hebrew") ?: "Hebrew"
        if (defLang == "None") return@LaunchedEffect
        val langCode = if (defLang == "Hebrew") "heb" else "eng"
        val idx = state.availableSubtitles.indexOfFirst { it.lang.contains(langCode, ignoreCase = true) }
        if (idx >= 0) {
            pendingSubIndex   = idx
            selectedWebSubUrl = state.availableSubtitles[idx].url
        }
    }

    LaunchedEffect(prepared, pendingSubIndex) {
        val idx = pendingSubIndex ?: return@LaunchedEffect
        if (!prepared || subtitleApplied) return@LaunchedEffect
        val subs = state.availableSubtitles
        if (idx >= subs.size) return@LaunchedEffect
        var attempts = 0
        while (exo.player.currentMediaItem == null && attempts < 60) { delay(300); attempts++ }
        if (exo.player.currentMediaItem == null) return@LaunchedEffect
        val langCode = if (
            context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                .getString("def_subs", "Hebrew") == "Hebrew"
        ) "heb" else "eng"
        val candidates = subs.mapIndexedNotNull { i, sub ->
            if (sub.lang.contains(langCode, ignoreCase = true)) i else null
        }
        for (ci in candidates) {
            val sub = subs[ci]
            exo.applySubtitle(sub.url, sub.lang)
            var waitMs = 0
            while (!exo.subtitleApplied.value && waitMs < 2500) { delay(200); waitMs += 200 }
            if (exo.subtitleApplied.value) {
                subtitleApplied   = true
                selectedWebSubUrl = sub.url
                break
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && activeMenu == ActiveMenu.NONE) {
            delay(120); runCatching { seekBarFR.requestFocus() }
        }
    }

    LaunchedEffect(activeMenu) {
        if (activeMenu != ActiveMenu.NONE) {
            delay(200); runCatching { sideMenuFR.requestFocus() }
        } else {
            delay(160); runCatching { seekBarFR.requestFocus() }
        }
    }

    LaunchedEffect(showControls, isPlaying, activityTick) {
        if (showControls && isPlaying && activeMenu == ActiveMenu.NONE && !showResumeDialog) {
            delay(5000); showControls = false
        }
    }

    // ─── AR toast auto-dismiss ────────────────────────────────────────────
    LaunchedEffect(showArToast) {
        if (showArToast) { delay(1800); showArToast = false }
    }

    BackHandler {
        when {
            showResumeDialog              -> showResumeDialog = false
            activeMenu != ActiveMenu.NONE -> activeMenu = ActiveMenu.NONE
            showControls                  -> showControls = false
            else                          -> { exo.pause(); onNavigateBack() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusTarget()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> false
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (activeMenu == ActiveMenu.NONE && !showResumeDialog) {
                            activityTick++
                            if (showControls) { if (isPlaying) exo.pause() else exo.play() }
                            else showControls = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (activeMenu == ActiveMenu.NONE && !showResumeDialog) {
                            activityTick++; showControls = true; true
                        } else false
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) exo.pause() else exo.play()
                        showControls = true; activityTick++; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls && activeMenu == ActiveMenu.NONE && !showResumeDialog) {
                            showControls = true; activityTick++; true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        // ─── Video surface wrapped in AspectRatioFrameLayout ─────────────
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory  = { ctx ->
                // Outer ARFL handles letterboxing / pillarboxing
                val arfl = AspectRatioFrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Default: FIT so native ratio is preserved immediately
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                arflRef.value = arfl

                val surfaceView = SurfaceView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    applySurfaceDolbyVision(this)
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            exo.player.setVideoSurfaceView(this@apply)
                            surfaceReady = true
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            surfaceReady = false
                            exo.player.clearVideoSurface()
                        }
                    })
                }

                // Wrap everything in a black FrameLayout so letterbox bars are black
                val outerFrame = FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }

                val subtitleView = SubtitleView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val textColor = if (exo.useYellowSubtitles) AndroidColor.YELLOW else AndroidColor.WHITE
                    setStyle(
                        CaptionStyleCompat(
                            textColor,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null
                        )
                    )
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * exo.subtitleFontScale)
                    setBottomPaddingFraction(0.08f)
                }

                arfl.addView(surfaceView)
                outerFrame.addView(arfl)
                outerFrame.addView(subtitleView)   // subtitles sit above ARFL, fill full screen
                outerFrame
            },
            update = { outerFrame ->
                val arfl = outerFrame.getChildAt(0) as? AspectRatioFrameLayout
                val sv   = arfl?.getChildAt(0) as? SurfaceView
                val sub  = outerFrame.getChildAt(1) as? SubtitleView
                if (arfl != null) arflRef.value = arfl
                sv?.let  { exo.player.setVideoSurfaceView(it) }
                sub?.setCues(currentCues)
                // Reapply mode on every recompose so it's always in sync
                arfl?.let { applyResizeMode(it, aspectRatioMode, nativeVideoSize) }
            }
        )

        // ─── Dolby Vision / Dolby Atmos badges ──────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 28.dp)
                .zIndex(50f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDolbyVision) DolbyBadge(text = "DOLBY VISION", color = DV_BLUE)
            if (isDolbyAtmos)  DolbyBadge(text = "DOLBY ATMOS",  color = ATMOS_PURPLE)
        }

        // ─── Aspect Ratio toast ──────────────────────────────────────────
        AnimatedVisibility(
            visible  = showArToast,
            enter    = fadeIn(tween(180)) + scaleIn(tween(180)),
            exit     = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.Center).zIndex(60f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text       = arToastLabel,
                    color      = WHITE,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (showResumeDialog) {
            val resumeFR    = remember { FocusRequester() }
            val fromStartFR = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(100); runCatching { resumeFR.requestFocus() } }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.75f)).zIndex(300f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(480.dp)
                        .background(Color(0xFF1A1A24), RoundedCornerShape(24.dp))
                        .padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = RED, modifier = Modifier.size(48.dp))
                    Text("Continue Watching?", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text("Stopped at ${formatTime(savedPosition)}", color = DIM, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) {
                                when {
                                    ev.key == Key.DirectionRight -> { runCatching { fromStartFR.requestFocus() }; true }
                                    ev.key == Key.DirectionLeft  -> { runCatching { resumeFR.requestFocus() }; true }
                                    else -> false
                                }
                            } else false
                        }
                    ) {
                        Surface(
                            onClick  = { showResumeDialog = false; resumeHandled = true; exo.seekTo(savedPosition) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = Color(0xFFFF2A2A), contentColor = WHITE, focusedContentColor = WHITE),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.weight(1f).height(52.dp).focusRequester(resumeFR)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("▶  Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
                        Surface(
                            onClick  = { showResumeDialog = false; resumeHandled = true; watchPrefs.edit().remove(progressKey).apply() },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A38), focusedContainerColor = Color(0xFF3A3A50), contentColor = WHITE, focusedContentColor = WHITE),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.weight(1f).height(52.dp).focusRequester(fromStartFR)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("From Start", fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
                    }
                }
            }
        }

        if (error != null) {
            val errorFR = remember { FocusRequester() }
            LaunchedEffect(error) { if (error != null) { delay(100); runCatching { errorFR.requestFocus() } } }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.88f)).zIndex(200f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(48.dp)
                ) {
                    Box(Modifier.size(80.dp).background(RED.copy(0.15f), CircleShape), Alignment.Center) {
                        Icon(Icons.Default.Warning, null, tint = RED, modifier = Modifier.size(40.dp))
                    }
                    Text("Playback Error", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text(error!!, color = DIM, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick  = { exo.clearError(); onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = Color(0xFFFF2A2A), contentColor = WHITE, focusedContentColor = WHITE),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.height(52.dp).focusRequester(errorFR)
                    ) {
                        Box(Modifier.padding(horizontal = 32.dp).fillMaxHeight(), Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                                Text("Back to Sources", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = showControls && error == null && !showResumeDialog,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(CTRL_BG.copy(0.7f), Color.Transparent, Color.Transparent, CTRL_BG.copy(0.85f)))
                )
            ) {
                AnimatedVisibility(
                    visible  = !isPlaying,
                    enter    = scaleIn(tween(120)) + fadeIn(tween(120)),
                    exit     = scaleOut(tween(100)) + fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(Modifier.size(80.dp).background(Color.Black.copy(0.6f), CircleShape).border(2.dp, WHITE.copy(0.7f), CircleShape), Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = WHITE, modifier = Modifier.size(44.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 32.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                runCatching { seekBarFR.requestFocus() }; true
                            } else false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick  = { exo.pause(); onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(CircleShape),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = CTRL_BG, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        modifier = Modifier.size(48.dp).focusRequester(backBtnFR)
                    ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(22.dp)) } }
                    Spacer(Modifier.width(16.dp))
                    if (!isPlaying) Text("Buffering...", color = DIM, fontSize = 14.sp)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 40.dp)
                ) {
                    PlayerProgressControls(
                        exoWrapper    = exo,
                        isPlaying     = isPlaying,
                        isRtl         = isRtl,
                        seekFR        = seekBarFR,
                        onUpPressed   = { runCatching { backBtnFR.requestFocus() } },
                        onDownPressed = { runCatching { firstPillFR.requestFocus() } }
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                runCatching { seekBarFR.requestFocus() }; true
                            } else false
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ControlPill(icon = CustomAudioIcon, text = "Audio", modifier = Modifier.focusRequester(firstPillFR)) {
                                activeMenu = if (activeMenu == ActiveMenu.AUDIO) ActiveMenu.NONE else ActiveMenu.AUDIO; activityTick++
                            }
                            ControlPill(CustomSubtitlesIcon, "Embedded Subs") {
                                activeMenu = if (activeMenu == ActiveMenu.EMBEDDED_SUBS) ActiveMenu.NONE else ActiveMenu.EMBEDDED_SUBS; activityTick++
                            }
                            ControlPill(
                                icon = Icons.Default.Search,
                                text = when {
                                    state.isSubtitlesLoading           -> "Loading Subs..."
                                    state.availableSubtitles.isEmpty() -> "Web Subs"
                                    else -> "Web Subs (${state.availableSubtitles.size})"
                                }
                            ) {
                                if (!state.isSubtitlesLoading) {
                                    activeMenu = if (activeMenu == ActiveMenu.WEB_SUBS) ActiveMenu.NONE else ActiveMenu.WEB_SUBS
                                }
                                activityTick++
                            }
                            // ─── Aspect Ratio pill ───────────────────────
                            ControlPill(
                                icon = Icons.Default.Fullscreen,
                                text = aspectRatioMode.label
                            ) {
                                // Cycle to next mode on click
                                val next = aspectRatioMode.next()
                                aspectRatioMode = next
                                arToastLabel    = next.label
                                showArToast     = true
                                activityTick++
                            }
                        }
                        ControlPill(Icons.Default.Close, "Exit") { exo.pause(); onNavigateBack() }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = activeMenu != ActiveMenu.NONE && error == null,
            enter    = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(380, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(remember { MutableInteractionSource() }, null) { activeMenu = ActiveMenu.NONE },
                contentAlignment = if (isRtl) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = if (isRtl) 24.dp else 0.dp, top = 24.dp, end = if (isRtl) 0.dp else 24.dp, bottom = 24.dp)
                        .width(460.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF0F0F13).copy(alpha = 0.98f))
                        .padding(horizontal = 36.dp, vertical = 40.dp)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) {
                                when {
                                    ev.key == Key.Back || ev.key == Key.Escape   -> { activeMenu = ActiveMenu.NONE; true }
                                    (!isRtl && ev.key == Key.DirectionLeft)      -> { activeMenu = ActiveMenu.NONE; true }
                                    (isRtl  && ev.key == Key.DirectionRight)     -> { activeMenu = ActiveMenu.NONE; true }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    when (activeMenu) {
                        ActiveMenu.AUDIO -> {
                            SidePanelHeader("Audio Tracks", "Select language")
                            TrackListUi(exo = exo, groups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }, trackType = C.TRACK_TYPE_AUDIO, focusReq = sideMenuFR) { activeMenu = ActiveMenu.NONE }
                        }
                        ActiveMenu.EMBEDDED_SUBS -> {
                            SidePanelHeader("Embedded Subtitles", "From video file")
                            TrackListUi(exo = exo, groups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }, trackType = C.TRACK_TYPE_TEXT, focusReq = sideMenuFR) { activeMenu = ActiveMenu.NONE }
                        }
                        ActiveMenu.WEB_SUBS -> {
                            SidePanelHeader("Web Subtitles", if (state.availableSubtitles.isNotEmpty()) "${state.availableSubtitles.size} online" else "")
                            if (state.availableSubtitles.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                                    Text(if (state.isSubtitlesLoading) "Searching..." else "No subtitles found", color = DIM, fontSize = 15.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusGroup()) {
                                    itemsIndexed(state.availableSubtitles, key = { _, s -> s.url }) { index, sub ->
                                        val isFirst = index == 0
                                        val isLast  = index == state.availableSubtitles.size - 1
                                        TrackItemCard(
                                            title      = "${sub.lang.uppercase()} ${getFlagEmoji(sub.lang)}",
                                            subtitle   = sub.source,
                                            isSelected = sub.url == selectedWebSubUrl,
                                            modifier   = Modifier
                                                .then(if (isFirst) Modifier.focusRequester(sideMenuFR) else Modifier)
                                                .then(if (isFirst) Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier)
                                                .then(if (isLast)  Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier),
                                            onClick = {
                                                exo.applySubtitle(sub.url, sub.lang)
                                                selectedWebSubUrl = sub.url
                                                subtitleApplied   = true
                                                pendingSubIndex   = null
                                                activeMenu        = ActiveMenu.NONE
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        ActiveMenu.ASPECT_RATIO, ActiveMenu.NONE -> {}
                    }
                }
            }
        }
    }
}

// ─── Dolby Badge composable ─────────────────────────────────────────────────
@Composable
private fun DolbyBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text          = text,
            color         = Color.White,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun SidePanelHeader(title: String, subtitle: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).background(RED, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
                if (subtitle.isNotEmpty()) Text(subtitle, color = DIM, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(RED.copy(0.6f), Color(0x08FFFFFF)))))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TrackListUi(
    exo:       ExoPlayerWrapper,
    groups:    List<androidx.media3.common.Tracks.Group>,
    trackType: Int,
    focusReq:  FocusRequester,
    onClose:   () -> Unit
) {
    val trackList = remember(groups) {
        val list = mutableListOf<Triple<androidx.media3.common.Tracks.Group?, Int, String>>()
        if (trackType == C.TRACK_TYPE_TEXT) {
            val isOff = groups.none { it.isSelected }
            list.add(Triple(null, -1, if (isOff) "✅ Turn Off" else "Turn Off"))
        }
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val format   = group.mediaTrackGroup.getFormat(i)
                val lang     = format.language ?: "Unknown"
                val label    = format.label    ?: ""
                val channels = if (format.channelCount > 0) "${format.channelCount}Ch" else ""
                val codec    = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                val dolbyTag = when (format.sampleMimeType) {
                    "audio/eac3-joc"    -> "🎵 ATMOS"
                    "video/dolby-vision" -> "🎬 DV"
                    else -> ""
                }
                val name = listOf(lang.uppercase(), label, channels, codec, dolbyTag)
                    .filter { it.isNotBlank() }.joinToString(" • ")
                list.add(Triple(group, i, name))
            }
        }
        list
    }
    if (trackList.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
            Text("No tracks available", color = DIM, fontSize = 15.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusGroup()) {
            itemsIndexed(trackList) { index, (group, trackIndex, name) ->
                val isFirst    = index == 0
                val isLast     = index == trackList.size - 1
                val isSelected = group?.isTrackSelected(trackIndex) ?: (trackType == C.TRACK_TYPE_TEXT && name.contains("✅"))
                TrackItemCard(
                    title      = name.replace("✅ ", ""),
                    subtitle   = if (group == null) "Disable" else "Internal",
                    isSelected = isSelected,
                    modifier   = Modifier
                        .then(if (isFirst) Modifier.focusRequester(focusReq) else Modifier)
                        .then(if (isFirst) Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier)
                        .then(if (isLast)  Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier),
                    onClick = {
                        val builder = exo.player.trackSelectionParameters.buildUpon()
                        builder.clearOverridesOfType(trackType)
                        if (group == null) {
                            builder.setTrackTypeDisabled(trackType, true)
                        } else {
                            builder.setTrackTypeDisabled(trackType, false)
                            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                        }
                        exo.player.trackSelectionParameters = builder.build()
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackItemCard(
    title:      String,
    subtitle:   String,
    isSelected: Boolean,
    modifier:   Modifier = Modifier,
    onClick:    () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val containerBg by animateColorAsState(
        targetValue = if (focused) Color(0xFF282832) else Color(0x0CFFFFFF),
        animationSpec = tween(200), label = "bgAnim"
    )
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, contentColor = WHITE, focusedContentColor = WHITE),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.7f), 20.dp)),
        modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize().background(containerBg, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, color = WHITE, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                    if (isSelected) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4D90FE), modifier = Modifier.size(18.dp))
                    }
                }
                PremiumBadge(subtitle, if (focused) RED else Color.Gray, isOutline = !focused)
            }
        }
    }
}

@Composable
private fun PremiumBadge(text: String, color: Color, isOutline: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (isOutline) Color.Transparent else color.copy(alpha = 0.25f))
            .border(1.dp, if (isOutline) color.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), color = if (isOutline) color else WHITE, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.5.sp)
    }
}

private fun getFlagEmoji(lang: String) = when (lang.lowercase().take(3)) {
    "heb", "he" -> "\uD83C\uDDEE\uD83C\uDDF1"
    "eng", "en" -> "\uD83C\uDDFA\uD83C\uDDF8"
    "ara", "ar" -> "\uD83C\uDDF8\uD83C\uDDE6"
    "rus", "ru" -> "\uD83C\uDDF7\uD83C\uDDFA"
    "fre", "fr" -> "\uD83C\uDDEB\uD83C\uDDF7"
    "spa", "es" -> "\uD83C\uDDEA\uD83C\uDDF8"
    "ger", "de" -> "\uD83C\uDDE9\uD83C\uDDEA"
    else        -> "\uD83C\uDF10"
}

@Composable
fun PlayerProgressControls(
    exoWrapper:    ExoPlayerWrapper,
    isPlaying:     Boolean,
    isRtl:         Boolean,
    seekFR:        FocusRequester = remember { FocusRequester() },
    onUpPressed:   () -> Unit = {},
    onDownPressed: () -> Unit = {}
) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration   by remember { mutableLongStateOf(1L) }
    var seekFocused     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoWrapper.player.currentPosition
            videoDuration   = exoWrapper.player.duration.coerceAtLeast(1L)
            delay(500)
        }
    }
    LaunchedEffect(Unit) { runCatching { seekFR.requestFocus() } }

    val progress  = (currentPosition.toFloat() / videoDuration.toFloat()).coerceIn(0f, 1f)
    val barHeight by animateDpAsState(if (seekFocused) 10.dp else 5.dp, label = "bh")
    val thumbSize by animateDpAsState(if (seekFocused) 18.dp else 0.dp,  label = "ts")

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("-${formatTime((videoDuration - currentPosition).coerceAtLeast(0L))}", color = DIM, fontSize = 13.sp)
            Text(formatTime(videoDuration), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .focusRequester(seekFR)
                .focusable()
                .onFocusChanged { seekFocused = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val d = if (isRtl) 10_000L else -10_000L
                            val p = (exoWrapper.player.currentPosition + d).coerceIn(0L, videoDuration)
                            exoWrapper.seekTo(p); currentPosition = p; true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val d = if (isRtl) -10_000L else 10_000L
                            val p = (exoWrapper.player.currentPosition + d).coerceIn(0L, videoDuration)
                            exoWrapper.seekTo(p); currentPosition = p; true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPlaying) exoWrapper.pause() else exoWrapper.play(); true
                        }
                        KeyEvent.KEYCODE_DPAD_UP   -> { onUpPressed();   true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { onDownPressed(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(WHITE.copy(if (seekFocused) 0.25f else 0.18f)))
            Box(Modifier.fillMaxWidth(progress).height(barHeight).clip(RoundedCornerShape(50)).background(if (seekFocused) WHITE else RED))
            if (thumbSize > 0.dp) {
                Box(Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)) {
                    Box(Modifier.size(thumbSize).clip(CircleShape).background(WHITE))
                }
            }
        }
    }
}

@Composable
fun ControlPill(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x55000000), focusedContainerColor = RED, contentColor = WHITE, focusedContentColor = WHITE),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 16.dp)),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatTime(ms: Long): String {
    val s   = ms / 1000
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
