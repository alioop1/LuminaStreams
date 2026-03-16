@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.tv.material3.*
import kotlinx.coroutines.delay

val CustomPauseIcon: ImageVector
    get() = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f); lineTo(10f, 19f); lineTo(10f, 5f); lineTo(6f, 5f); close()
            moveTo(14f, 19f); lineTo(18f, 19f); lineTo(18f, 5f); lineTo(14f, 5f); close()
        }
    }.build()

val CustomSubtitlesIcon: ImageVector
    get() = ImageVector.Builder("Subtitles", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
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

private val CTRL_BG = Color(0x99000000)
private val RED     = Color(0xFFE50914)
private val WHITE   = Color(0xFFFFFFFF)
private val DIM     = Color(0xAAFFFFFF)

// ══════════════════════════════════════════════════════════════════
//  PLAYER SCREEN
//  Focus flow (D-pad):
//    [Back btn] ← UP from seekbar (when controls visible)
//    [Seekbar]  ← default focus when controls shown
//                 DOWN → pill row
//    [Pills]    ← UP → seekbar
//    [Sub panel]← dedicated FR on first item, ESC/BACK closes panel
// ══════════════════════════════════════════════════════════════════
@Composable
fun PlayerScreen(
    videoUrl: String,
    imdbId: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val context    = LocalContext.current
    val isRtl      = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state      by viewModel.state.collectAsState()
    val exoWrapper = remember { ExoPlayerWrapper(context) }
    val isPlaying  by exoWrapper.isPlaying.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showSubMenu  by remember { mutableStateOf(false) }
    var activityTick by remember { mutableIntStateOf(0) }

    // Focus requesters for all navigable elements
    val backBtnFR   = remember { FocusRequester() }   // ← back arrow top-left
    val seekBarFR   = remember { FocusRequester() }   // ← progress bar
    val firstPillFR = remember { FocusRequester() }   // ← first pill (Audio)
    val firstSubFR  = remember { FocusRequester() }   // ← first subtitle row

    LaunchedEffect(videoUrl, imdbId) {
        viewModel.loadMedia(videoUrl, imdbId)
        exoWrapper.prepareStream(videoUrl)
        delay(3000)
        if (isPlaying) showControls = false
    }

    // Auto-apply Hebrew subtitle if found
    LaunchedEffect(state.availableSubtitles) {
        state.availableSubtitles.firstOrNull { it.lang.contains("heb", true) }?.let { sub ->
            exoWrapper.applySubtitle(sub.url, sub.lang)
        }
    }

    // When controls appear → focus seekbar
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(120)
            runCatching { seekBarFR.requestFocus() }
        }
    }

    // When subtitle panel opens → focus first row; closes → back to seekbar
    LaunchedEffect(showSubMenu) {
        if (showSubMenu) {
            delay(200)
            runCatching { firstSubFR.requestFocus() }
        } else {
            delay(160)
            runCatching { seekBarFR.requestFocus() }
        }
    }

    DisposableEffect(Unit) { onDispose { exoWrapper.release() } }

    // Auto-hide controls after 5s while playing
    LaunchedEffect(showControls, isPlaying, activityTick) {
        if (showControls && isPlaying && !showSubMenu) {
            delay(5000)
            showControls = false
        }
    }

    BackHandler {
        when {
            showSubMenu  -> showSubMenu  = false
            showControls -> showControls = false
            else         -> { exoWrapper.pause(); onNavigateBack() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && !showSubMenu) {
                    activityTick++
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (showControls) {
                                if (isPlaying) exoWrapper.pause() else exoWrapper.play()
                            } else showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP   -> { showControls = true; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { showControls = true; true }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (isPlaying) exoWrapper.pause() else exoWrapper.play()
                            showControls = true; true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!showControls) showControls = true; false }
                        KeyEvent.KEYCODE_DPAD_LEFT  -> { if (!showControls) showControls = true; false }
                        else -> false
                    }
                } else false
            }
    ) {
        // ── Video surface ───────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory  = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoWrapper.player
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams  = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn  = true
                }
            },
            update = { pv -> if (pv.player != exoWrapper.player) pv.player = exoWrapper.player }
        )

        // ── Controls overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(CTRL_BG.copy(0.7f), Color.Transparent, Color.Transparent, CTRL_BG.copy(0.85f))
                    )
                )
            ) {
                // Paused indicator
                AnimatedVisibility(
                    visible  = !isPlaying,
                    enter    = scaleIn(tween(120)) + fadeIn(tween(120)),
                    exit     = scaleOut(tween(100)) + fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        Modifier.size(80.dp)
                            .background(Color.Black.copy(0.6f), CircleShape)
                            .border(2.dp, WHITE.copy(0.7f), CircleShape),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = WHITE, modifier = Modifier.size(44.dp))
                    }
                }

                // ── TOP BAR: Back btn + title/buffering ─────────────
                // Back button has its own FocusRequester.
                // D-pad UP from seekbar will call backBtnFR.requestFocus().
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 32.dp)
                        // Pressing DOWN from top bar → back to seekbar
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                runCatching { seekBarFR.requestFocus() }
                                true
                            } else false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick  = { exoWrapper.pause(); onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(CircleShape),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = CTRL_BG,
                            focusedContainerColor = WHITE,
                            contentColor          = WHITE,
                            focusedContentColor   = Color.Black
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(backBtnFR)  // ← now reachable via D-pad
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, null, Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = if (!isPlaying) "Buffering..." else "",
                        color = DIM, fontSize = 14.sp
                    )
                }

                // ── BOTTOM: seekbar + pills ──────────────────────────
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 40.dp)
                ) {
                    PlayerProgressControls(
                        exoWrapper    = exoWrapper,
                        isPlaying     = isPlaying,
                        isRtl         = isRtl,
                        seekFR        = seekBarFR,
                        // UP from seekbar → back button
                        onUpPressed   = { runCatching { backBtnFR.requestFocus() } },
                        onDownPressed = { runCatching { firstPillFR.requestFocus() } }
                    )
                    Spacer(Modifier.height(20.dp))

                    // Pill row — UP returns to seekbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                    runCatching { seekBarFR.requestFocus() }
                                    true
                                } else false
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ControlPill(
                                icon     = CustomAudioIcon,
                                text     = "Audio",
                                modifier = Modifier.focusRequester(firstPillFR)
                            ) {
                                try {
                                    TrackSelectionDialogBuilder(
                                        ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog),
                                        "Select Audio Track", exoWrapper.player, C.TRACK_TYPE_AUDIO
                                    ).build().show()
                                } catch (_: Exception) {}
                                activityTick++
                            }
                            ControlPill(CustomSubtitlesIcon, "Embedded Subs") {
                                try {
                                    TrackSelectionDialogBuilder(
                                        ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog),
                                        "Select Subtitles", exoWrapper.player, C.TRACK_TYPE_TEXT
                                    ).build().show()
                                } catch (_: Exception) {}
                                activityTick++
                            }
                            // Always visible — shows loading state or count
                            ControlPill(
                                icon = Icons.Default.Search,
                                text = when {
                                    state.isSubtitlesLoading           -> "Loading Subs..."
                                    state.availableSubtitles.isEmpty() -> "Web Subs"
                                    else -> "Web Subs (${state.availableSubtitles.size})"
                                }
                            ) {
                                if (!state.isSubtitlesLoading) showSubMenu = true
                                activityTick++
                            }
                        }
                        ControlPill(Icons.Default.Close, "Exit") {
                            exoWrapper.pause(); onNavigateBack()
                        }
                    }
                }
            }
        }

        // ── Subtitle picker panel ────────────────────────────────────
        AnimatedVisibility(
            visible  = showSubMenu,
            enter    = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(250)),
            exit     = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.7f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { showSubMenu = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(420.dp)
                        .background(Color(0xFF0E0E0E))
                        .border(1.dp, Color(0x22FFFFFF))
                        .padding(32.dp)
                        // Block click-through so tapping inside doesn't close
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {}
                        // ESC/BACK key inside panel closes it
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.Back || ev.key == Key.Escape)
                            ) {
                                showSubMenu = false; true
                            } else false
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(4.dp).height(28.dp)
                                .background(RED, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Subtitles", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(24.dp))

                    if (state.availableSubtitles.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            Alignment.Center
                        ) {
                            Text(
                                text = if (state.isSubtitlesLoading)
                                    "Searching for subtitles..."
                                else
                                    "No subtitles found",
                                color = DIM, fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.availableSubtitles, key = { it.url }) { sub ->
                                val isFirst = sub == state.availableSubtitles.first()
                                Surface(
                                    onClick = {
                                        exoWrapper.applySubtitle(sub.url, sub.lang)
                                        showSubMenu = false
                                    },
                                    colors  = ClickableSurfaceDefaults.colors(
                                        containerColor        = Color(0xFF1A1A1A),
                                        focusedContainerColor = RED,
                                        contentColor          = WHITE,
                                        focusedContentColor   = WHITE
                                    ),
                                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .let { if (isFirst) it.focusRequester(firstSubFR) else it }
                                ) {
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${sub.lang.uppercase()} ${getFlagEmoji(sub.lang)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize   = 16.sp
                                        )
                                        Text(sub.source, color = DIM, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  HELPERS
// ══════════════════════════════════════════════════════════════════
private fun getFlagEmoji(lang: String): String = when (lang.lowercase().take(3)) {
    "heb", "he" -> "\uD83C\uDDEE\uD83C\uDDF1"
    "eng", "en" -> "\uD83C\uDDFA\uD83C\uDDF8"
    "ara", "ar" -> "\uD83C\uDDF8\uD83C\uDDE6"
    "rus", "ru" -> "\uD83C\uDDF7\uD83C\uDDFA"
    "fre", "fr" -> "\uD83C\uDDEB\uD83C\uDDF7"
    "spa", "es" -> "\uD83C\uDDEA\uD83C\uDDF8"
    "ger", "de" -> "\uD83C\uDDE9\uD83C\uDDEA"
    else        -> "\uD83C\uDF10"
}

// ── Progress controls ─────────────────────────────────────────────
// Added onUpPressed callback so seekbar can delegate UP to back button
@Composable
fun PlayerProgressControls(
    exoWrapper:    ExoPlayerWrapper,
    isPlaying:     Boolean,
    isRtl:         Boolean,
    seekFR:        FocusRequester = remember { FocusRequester() },
    onUpPressed:   () -> Unit     = {},
    onDownPressed: () -> Unit     = {}
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
    val barHeight by animateDpAsState(if (seekFocused) 10.dp else 5.dp,  label = "bar_h")
    val thumbSize by animateDpAsState(if (seekFocused) 18.dp else 0.dp, label = "thumb")

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("-${formatTime((videoDuration - currentPosition).coerceAtLeast(0L))}", color = DIM, fontSize = 13.sp)
            Text(formatTime(videoDuration),   color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .focusRequester(seekFR)
                .onFocusChanged { seekFocused = it.isFocused }
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val delta = if (isRtl) 10_000L else -10_000L
                                val pos = (exoWrapper.player.currentPosition + delta).coerceIn(0L, videoDuration)
                                exoWrapper.seekTo(pos); currentPosition = pos; true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val delta = if (isRtl) -10_000L else 10_000L
                                val pos = (exoWrapper.player.currentPosition + delta).coerceIn(0L, videoDuration)
                                exoWrapper.seekTo(pos); currentPosition = pos; true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (isPlaying) exoWrapper.pause() else exoWrapper.play(); true
                            }
                            KeyEvent.KEYCODE_DPAD_UP   -> { onUpPressed();   true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { onDownPressed(); true }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50))
                    .background(WHITE.copy(if (seekFocused) 0.25f else 0.18f))
            )
            Box(
                Modifier.fillMaxWidth(progress).height(barHeight).clip(RoundedCornerShape(50))
                    .background(if (seekFocused) WHITE else RED)
            )
            if (thumbSize > 0.dp) {
                Box(Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)) {
                    Box(Modifier.size(thumbSize).clip(CircleShape).background(WHITE))
                }
            }
        }
    }
}

// ── Control pill ─────────────────────────────────────────────────
@Composable
fun ControlPill(
    icon:     ImageVector,
    text:     String,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color(0x55000000),
            focusedContainerColor = RED,
            contentColor          = WHITE,
            focusedContentColor   = WHITE
        ),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 16.dp)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}