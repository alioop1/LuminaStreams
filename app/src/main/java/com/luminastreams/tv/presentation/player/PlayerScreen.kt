@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
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
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.*
import kotlinx.coroutines.delay
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

private val CTRL_BG = Color(0x99000000)
private val RED     = Color(0xFFE50914)
private val WHITE   = Color(0xFFFFFFFF)
private val DIM     = Color(0xAAFFFFFF)

enum class ActiveMenu { NONE, AUDIO, EMBEDDED_SUBS, WEB_SUBS }

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

    var surfaceReady      by remember { mutableStateOf(false) }
    var prepared          by remember { mutableStateOf(false) }
    var showControls      by remember { mutableStateOf(true) }
    var activeMenu        by remember { mutableStateOf(ActiveMenu.NONE) }
    var activityTick      by remember { mutableIntStateOf(0) }
    var selectedWebSubUrl by remember { mutableStateOf<String?>(null) }

    var pendingSubtitle by remember { mutableStateOf<Pair<String, String>?>(null) }

    val backBtnFR   = remember { FocusRequester() }
    val seekBarFR   = remember { FocusRequester() }
    val firstPillFR = remember { FocusRequester() }
    val sideMenuFR  = remember { FocusRequester() }

    LaunchedEffect(videoUrl, imdbId) {
        viewModel.loadMedia(videoUrl, imdbId)
    }

    LaunchedEffect(surfaceReady) {
        if (surfaceReady && !prepared) {
            prepared = true
            exo.prepareStream(videoUrl)
            delay(3000)
            if (isPlaying) showControls = false
        }
    }

    LaunchedEffect(state.availableSubtitles) {
        if (state.availableSubtitles.isEmpty()) return@LaunchedEffect
        val prefs = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        val defaultSubLang = prefs.getString("def_subs", "Hebrew") ?: "Hebrew"
        if (defaultSubLang == "None") return@LaunchedEffect

        val langCode = if (defaultSubLang == "Hebrew") "heb" else "eng"
        val sub = state.availableSubtitles.firstOrNull {
            it.lang.contains(langCode, ignoreCase = true)
        } ?: return@LaunchedEffect

        selectedWebSubUrl = sub.url
        pendingSubtitle   = sub.url to sub.lang
    }

    LaunchedEffect(prepared, pendingSubtitle) {
        val (subUrl, subLang) = pendingSubtitle ?: return@LaunchedEffect
        if (!prepared) return@LaunchedEffect

        var attempts = 0
        while (exo.player.currentMediaItem == null && attempts < 20) {
            delay(300)
            attempts++
        }
        if (exo.player.currentMediaItem != null) {
            exo.applySubtitle(subUrl, subLang)
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
        if (showControls && isPlaying && activeMenu == ActiveMenu.NONE) {
            delay(5000); showControls = false
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    BackHandler {
        when {
            activeMenu != ActiveMenu.NONE -> activeMenu = ActiveMenu.NONE
            showControls -> showControls = false
            else -> { exo.pause(); onNavigateBack() }
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
                        if (activeMenu == ActiveMenu.NONE) {
                            activityTick++
                            if (showControls) { if (isPlaying) exo.pause() else exo.play() }
                            else showControls = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (activeMenu == ActiveMenu.NONE) { activityTick++; showControls = true; true } else false
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) exo.pause() else exo.play()
                        showControls = true; activityTick++; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls && activeMenu == ActiveMenu.NONE) { showControls = true; activityTick++; true } else false
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory  = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val surfaceView = SurfaceView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        keepScreenOn = true
                        addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                exo.player.setVideoSurfaceView(this@apply); surfaceReady = true
                            }
                            override fun onViewDetachedFromWindow(v: android.view.View) {
                                surfaceReady = false; exo.player.clearVideoSurface()
                            }
                        })
                    }

                    val subtitleView = SubtitleView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val textColor = if (exo.useYellowSubtitles) AndroidColor.YELLOW else AndroidColor.WHITE
                        val style = CaptionStyleCompat(
                            textColor,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null
                        )

                        setStyle(style)
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * exo.subtitleFontScale)
                        setBottomPaddingFraction(0.08f)
                    }

                    addView(surfaceView)
                    addView(subtitleView)

                    exo.player.addListener(object : Player.Listener {
                        override fun onCues(cueGroup: CueGroup) {
                            subtitleView.setCues(cueGroup.cues)
                        }
                    })
                }
            },
            update = { frameLayout ->
                val sv = frameLayout.getChildAt(0) as? SurfaceView
                sv?.let { exo.player.setVideoSurfaceView(it) }
            }
        )

        // טיפול באזהרת return@Box: מוחלף ב-if / else
        if (error != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.88f)), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(48.dp)
                ) {
                    Box(Modifier.size(80.dp).background(RED.copy(0.15f), CircleShape), Alignment.Center) {
                        Icon(Icons.Default.Warning, null, tint = RED, modifier = Modifier.size(40.dp))
                    }
                    Text("Playback Error", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(error!!, color = DIM, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick  = { exo.clearError(); onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = Color(0xFFFF2A2A), contentColor = WHITE, focusedContentColor = WHITE),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Text("Back to Sources", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        } else {
            AnimatedVisibility(visible = showControls, enter = fadeIn(tween(200)), exit = fadeOut(tween(350)), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CTRL_BG.copy(0.7f), Color.Transparent, Color.Transparent, CTRL_BG.copy(0.85f))))) {
                    AnimatedVisibility(visible = !isPlaying, enter = scaleIn(tween(120)) + fadeIn(tween(120)), exit = scaleOut(tween(100)) + fadeOut(tween(100)), modifier = Modifier.align(Alignment.Center)) {
                        Box(Modifier.size(80.dp).background(Color.Black.copy(0.6f), CircleShape).border(2.dp, WHITE.copy(0.7f), CircleShape), Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = WHITE, modifier = Modifier.size(44.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 64.dp, vertical = 32.dp).onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { runCatching { seekBarFR.requestFocus() }; true } else false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick  = { exo.pause(); onNavigateBack() },
                            shape    = ClickableSurfaceDefaults.shape(CircleShape),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = CTRL_BG, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            modifier = Modifier.size(48.dp).focusRequester(backBtnFR)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(22.dp)) }
                        }
                        Spacer(Modifier.width(16.dp))
                        if (!isPlaying) Text("Buffering...", color = DIM, fontSize = 14.sp)
                    }

                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 40.dp)) {
                        PlayerProgressControls(exo, isPlaying, isRtl, seekBarFR, { runCatching { backBtnFR.requestFocus() } }, { runCatching { firstPillFR.requestFocus() } })
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) { runCatching { seekBarFR.requestFocus() }; true } else false
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ControlPill(CustomAudioIcon, "Audio", Modifier.focusRequester(firstPillFR)) {
                                    activeMenu = if (activeMenu == ActiveMenu.AUDIO) ActiveMenu.NONE else ActiveMenu.AUDIO
                                    activityTick++
                                }
                                ControlPill(CustomSubtitlesIcon, "Embedded Subs") {
                                    activeMenu = if (activeMenu == ActiveMenu.EMBEDDED_SUBS) ActiveMenu.NONE else ActiveMenu.EMBEDDED_SUBS
                                    activityTick++
                                }
                                ControlPill(
                                    Icons.Default.Search,
                                    when {
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
                            }
                            ControlPill(Icons.Default.Close, "Exit") { exo.pause(); onNavigateBack() }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible  = activeMenu != ActiveMenu.NONE,
                enter    = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(380, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
                exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize().zIndex(200f)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.7f))
                        .clickable(remember { MutableInteractionSource() }, null) { activeMenu = ActiveMenu.NONE },
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
                                        ev.key == Key.Back || ev.key == Key.Escape -> { activeMenu = ActiveMenu.NONE; true }
                                        (!isRtl && ev.key == Key.DirectionLeft)    -> { activeMenu = ActiveMenu.NONE; true }
                                        (isRtl  && ev.key == Key.DirectionRight)   -> { activeMenu = ActiveMenu.NONE; true }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        when (activeMenu) {
                            ActiveMenu.AUDIO -> {
                                SidePanelHeader("Audio Tracks", "Select language")
                                TrackListUi(exo, currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }, C.TRACK_TYPE_AUDIO, sideMenuFR) { activeMenu = ActiveMenu.NONE }
                            }
                            ActiveMenu.EMBEDDED_SUBS -> {
                                SidePanelHeader("Embedded Subtitles", "From video file")
                                TrackListUi(exo, currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }, C.TRACK_TYPE_TEXT, sideMenuFR) { activeMenu = ActiveMenu.NONE }
                            }
                            ActiveMenu.WEB_SUBS -> {
                                SidePanelHeader("Web Subtitles", if (state.availableSubtitles.isNotEmpty()) "${state.availableSubtitles.size} online" else "")
                                if (state.availableSubtitles.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                                        Text(if (state.isSubtitlesLoading) "Searching..." else "No subtitles found", color = DIM, fontSize = 15.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.focusGroup()
                                    ) {
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
                                                    pendingSubtitle   = null
                                                    activeMenu        = ActiveMenu.NONE
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            ActiveMenu.NONE -> {}
                        }
                    }
                }
            }
        }
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
                val name     = listOf(lang.uppercase(), label, channels, codec).filter { it.isNotBlank() }.joinToString(" • ")
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusGroup()
        ) {
            itemsIndexed(trackList) { index, (group, trackIndex, name) ->
                val isFirst    = index == 0
                val isLast     = index == trackList.size - 1
                val isSelected = group?.isTrackSelected(trackIndex)
                    ?: (trackType == C.TRACK_TYPE_TEXT && name.contains("✅"))

                TrackItemCard(
                    title      = name.replace("✅ ", ""),
                    subtitle   = if (group == null) "Disable" else "Internal",
                    isSelected = isSelected,
                    modifier   = Modifier
                        .then(if (isFirst) Modifier.focusRequester(focusReq) else Modifier)
                        .then(if (isFirst) Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier)
                        .then(if (isLast)  Modifier.focusProperties { down = FocusRequester.Cancel } else Modifier),
                    onClick    = {
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
    title:     String,
    subtitle:  String,
    isSelected: Boolean,
    modifier:  Modifier = Modifier,
    onClick:   () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val containerBg by animateColorAsState(
        if (focused) Color(0xFF282832) else Color(0x0CFFFFFF), tween(200), label = "bgAnim"
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
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = WHITE, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                    if (isSelected) { Spacer(Modifier.width(12.dp)); Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4D90FE), modifier = Modifier.size(18.dp)) }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("-${formatTime((videoDuration - currentPosition).coerceAtLeast(0L))}", color = DIM, fontSize = 13.sp)
            Text(formatTime(videoDuration), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth().height(28.dp)
                .focusRequester(seekFR).focusable()
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
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}