package com.luminastreams.tv.presentation.player

import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.presentation.iptv.IptvViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StreamTrackInfo(val group: Tracks.Group, val trackIndex: Int, val format: Format, val isSelected: Boolean)

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun IptvPlayerScreen(initialChannelUrl: String, viewModel: IptvViewModel, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channels by viewModel.channels.collectAsState()

    var currentUrl by remember { mutableStateOf(initialChannelUrl) }
    var showSettings by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showOsd by remember { mutableStateOf(false) }
    var osdTimerKey by remember { mutableIntStateOf(0) }

    val currentChannel = remember(currentUrl) { channels.find { currentUrl.startsWith(it.streamUrl) } }
    var currentEpg by remember { mutableStateOf<EpgProgramEntity?>(null) }

    val playerFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val view = LocalView.current // Used to force Android OS to respect Compose Focus

    // ⚡ THE BULLETPROOF FOCUS RECLAIMER
    LaunchedEffect(showSettings, showOsd, currentUrl) {
        if (!showSettings) {
            // Force the underlying Android Window to route keys to Compose
            view.requestFocus()
            runCatching { playerFocusRequester.requestFocus() }

            // Wait out the OSD 300ms fade animation and lock focus again
            delay(400)
            view.requestFocus()
            runCatching { playerFocusRequester.requestFocus() }
        } else {
            delay(100)
            runCatching { settingsFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(actionMessage) { if (actionMessage != null) { delay(3500); actionMessage = null } }

    // Auto-hide OSD after 5 seconds
    LaunchedEffect(showOsd, osdTimerKey, currentUrl) {
        if (showOsd && !showSettings) { delay(5000); showOsd = false }
    }

    LaunchedEffect(currentChannel) {
        if (currentChannel != null) {
            val programs = viewModel.getProgramsForChannel(currentChannel, System.currentTimeMillis())
            currentEpg = programs.firstOrNull { System.currentTimeMillis() in it.startTime..it.endTime } ?: programs.firstOrNull()
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showOsd) { showOsd = false }
    BackHandler(enabled = !showSettings && !showOsd) { onBackPressed() }

    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val trackSelector = DefaultTrackSelector(context).apply { setParameters(buildUponParameters().setSelectUndeterminedTextLanguage(true).setExceedRendererCapabilitiesIfNecessary(true).setExceedAudioConstraintsIfNecessary(true)) }
        ExoPlayer.Builder(context, renderersFactory).setTrackSelector(trackSelector).build().apply { playWhenReady = true }
    }

    LaunchedEffect(currentUrl) {
        playerError = null
        showSettings = false
        showOsd = true
        osdTimerKey++

        val isDifferentUrl = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != currentUrl

        if (isDifferentUrl) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent("VLC/3.0.0")
            val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            val isHls = currentChannel?.streamUrl?.contains(".m3u8", ignoreCase = true) == true

            val mediaItem = MediaItem.Builder().setUri(currentUrl).setMimeType(if (isHls) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else androidx.media3.common.MimeTypes.VIDEO_MP2T).build()
            val mediaSource = if (isHls) { HlsMediaSource.Factory(httpDataSourceFactory).setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, true)).createMediaSource(mediaItem) } else { ProgressiveMediaSource.Factory(httpDataSourceFactory, DefaultExtractorsFactory().setTsExtractorMode(TsExtractor.MODE_MULTI_PMT).setTsExtractorFlags(tsFlags)).createMediaSource(mediaItem) }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_PAUSE -> exoPlayer.pause(); Lifecycle.Event.ON_RESUME -> exoPlayer.play(); Lifecycle.Event.ON_DESTROY -> exoPlayer.release(); else -> {} }
        }
        val playerListener = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { playerError = "Stream Error: ${error.errorCodeName}" } }
        exoPlayer.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { exoPlayer.removeListener(playerListener); lifecycleOwner.lifecycle.removeObserver(lifecycleObserver); exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ⚡ THE INVISIBLE FOCUS ANCHOR ⚡
        // This is physically separated from all UI logic. It traps the D-Pad
        // permanently while watching TV so disappearing menus cannot kill the controls.
        Box(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(playerFocusRequester)
                .focusable(enabled = !showSettings)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> { if (!showSettings) viewModel.getNextChannelUrl(currentUrl)?.let { currentUrl = it }; true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { if (!showSettings) viewModel.getPrevChannelUrl(currentUrl)?.let { currentUrl = it }; true }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (playerError == null && !showSettings) {
                                    if (showOsd) { showOsd = false } else { showOsd = true; osdTimerKey++ }
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> { if (!showSettings) { showSettings = true; showOsd = false }; true }
                            else -> false
                        }
                    } else false
                }
        )

        // RAW VIDEO PLAYER
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                    resizeMode = currentResizeMode

                    // Nuke all ExoPlayer focus capabilities
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    clearFocus()
                }
            },
            update = { view -> view.resizeMode = currentResizeMode },
            modifier = Modifier.fillMaxSize()
        )

        // --- OVERLAYS ---

        AnimatedVisibility(
            visible = actionMessage != null,
            enter = fadeIn(tween(500)) + slideInVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing), initialOffsetY = { -80 }),
            exit = fadeOut(tween(300)) + slideOutVertically(animationSpec = tween(300, easing = FastOutLinearInEasing), targetOffsetY = { -80 }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Box(modifier = Modifier.background(Color(0xD9000000), RoundedCornerShape(32.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)).padding(horizontal = 32.dp, vertical = 16.dp)) {
                Text(text = actionMessage ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (playerError != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Playback Interrupted", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = playerError!!, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = showOsd && playerError == null && !showSettings,
            enter = fadeIn(tween(500)) + slideInVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing), initialOffsetY = { 80 }),
            exit = fadeOut(tween(300)) + slideOutVertically(animationSpec = tween(300, easing = FastOutLinearInEasing), targetOffsetY = { 80 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(modifier = Modifier.padding(bottom = 48.dp, start = 64.dp, end = 64.dp).fillMaxWidth().background(Color(0xD9000000), RoundedCornerShape(40.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp)).padding(horizontal = 40.dp, vertical = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(88.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                            if (currentChannel?.logoUrl?.isNotBlank() == true) AsyncImage(model = currentChannel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(currentChannel?.name?.take(1) ?: "", color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                if ((currentChannel?.number ?: 0) > 0) { Text(text = "${currentChannel?.number}", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(12.dp)) }
                                Text(text = currentChannel?.name ?: "", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (currentEpg != null) {
                                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
                                Text(text = "${timeFormat.format(Date(currentEpg!!.startTime))} - ${timeFormat.format(Date(currentEpg!!.endTime))}  •  ${currentEpg!!.title}", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Normal)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        Text(text = dateFormat.format(Date()), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "OK for Guide • ► for Options", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(500)) + slideInHorizontally(animationSpec = tween(500, easing = LinearOutSlowInEasing), initialOffsetX = { 100 }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(animationSpec = tween(300, easing = FastOutLinearInEasing), targetOffsetX = { 100 }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerSettingsOverlay(player = exoPlayer, currentResizeMode = currentResizeMode, modifier = Modifier.focusRequester(settingsFocusRequester), onResizeModeChange = { currentResizeMode = it }, onClose = { showSettings = false; showOsd = true; osdTimerKey++ })
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerSettingsOverlay(player: ExoPlayer, currentResizeMode: Int, modifier: Modifier = Modifier, onResizeModeChange: (Int) -> Unit, onClose: () -> Unit) {
    var audioTracks by remember { mutableStateOf<List<StreamTrackInfo>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<StreamTrackInfo>>(emptyList()) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val closeKey = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val tempAudio = mutableListOf<StreamTrackInfo>(); val tempSubs = mutableListOf<StreamTrackInfo>()
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i); val isSelected = group.isTrackSelected(i)
                        if (group.type == C.TRACK_TYPE_AUDIO) tempAudio.add(StreamTrackInfo(group, i, format, isSelected))
                        else if (group.type == C.TRACK_TYPE_TEXT) tempSubs.add(StreamTrackInfo(group, i, format, isSelected))
                    }
                }
                audioTracks = tempAudio; subTracks = tempSubs
            }
        }
        player.addListener(listener); listener.onTracksChanged(player.currentTracks); onDispose { player.removeListener(listener) }
    }

    Box(modifier = modifier.fillMaxHeight().width(400.dp).background(Color(0xD9000000), RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp)).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp)).padding(40.dp)
        .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE || event.nativeKeyEvent.keyCode == closeKey)) { onClose(); true } else false }
    ) {
        Column {
            Text("Options", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(modifier = Modifier.height(40.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("ASPECT RATIO", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp))
                    listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT to "Original", AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch", AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop").forEach { (mode, label) ->
                        SettingsButton(text = label, isSelected = currentResizeMode == mode, onClick = { onResizeModeChange(mode) })
                    }
                }
                if (audioTracks.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(24.dp)); Text("AUDIO", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp)) }
                    itemsIndexed(audioTracks) { index, trackInfo ->
                        val label = trackInfo.format.label ?: trackInfo.format.language?.uppercase()?.replace("UND", "UNKNOWN") ?: "UNKNOWN"
                        SettingsButton(text = "Track ${index + 1}: $label", isSelected = trackInfo.isSelected, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).addOverride(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex)).build() })
                    }
                }
                if (subTracks.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(24.dp)); Text("SUBTITLES", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp))
                        SettingsButton(text = "Off", isSelected = !subTracks.any { it.isSelected }, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() })
                    }
                    itemsIndexed(subTracks) { index, trackInfo ->
                        val label = trackInfo.format.label ?: trackInfo.format.language?.uppercase()?.replace("UND", "UNKNOWN") ?: "UNKNOWN"
                        SettingsButton(text = "Subtitle ${index + 1}: $label", isSelected = trackInfo.isSelected, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex)).build() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) { Text("✓", color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(12.dp)) }
            Text(text = text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp)
        }
    }
}