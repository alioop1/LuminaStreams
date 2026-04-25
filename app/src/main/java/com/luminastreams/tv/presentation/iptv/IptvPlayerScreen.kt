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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.presentation.iptv.IptvViewModel
import kotlinx.coroutines.delay

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StreamTrackInfo(val group: Tracks.Group, val trackIndex: Int, val format: Format, val isSelected: Boolean)

/** Walk the exception cause chain to find BehindLiveWindowException */
@OptIn(UnstableApi::class)
private fun isBehindLiveWindow(e: PlaybackException): Boolean {
    var cause: Throwable? = e
    while (cause != null) {
        if (cause is BehindLiveWindowException) return true
        cause = cause.cause
    }
    return false
}

/** Format milliseconds to HH:MM:SS or MM:SS */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun IptvPlayerScreen(initialChannelUrl: String, viewModel: IptvViewModel, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    var currentUrl by remember { mutableStateOf(initialChannelUrl) }
    var showSettings by remember { mutableStateOf(false) }
    var showEpgGuide by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showOsd by remember { mutableStateOf(false) }
    var osdTimerKey by remember { mutableIntStateOf(0) }

    // Seek bar state for catch-up / VOD
    var isSeekable by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0L) }
    var seekDuration by remember { mutableStateOf(0L) }

    val currentChannel = remember(currentUrl) { channels.find { currentUrl.startsWith(it.streamUrl) } }
    var currentEpg by remember { mutableStateOf<EpgProgramEntity?>(null) }

    // Ticking OSD clock — updates every minute, not on every recompose
    var osdClock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            osdClock = fmt.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val epgFocusRequester = remember { FocusRequester() }
    val view = LocalView.current // Used to force Android OS to respect Compose Focus

    // ⚡ THE BULLETPROOF FOCUS RECLAIMER
    LaunchedEffect(showSettings, showOsd, showEpgGuide, currentUrl) {
        if (showEpgGuide) {
            delay(200)
            runCatching { epgFocusRequester.requestFocus() }
        } else if (!showSettings) {
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

    BackHandler(enabled = showEpgGuide) { showEpgGuide = false }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showOsd && !showEpgGuide) { showOsd = false }
    BackHandler(enabled = !showSettings && !showOsd && !showEpgGuide) { onBackPressed() }

    // Retry counter for stuck/stale streams
    var retryCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentUrl) { retryCount = 0 }

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
            val baseIsHls = currentChannel?.streamUrl?.contains(".m3u8", ignoreCase = true) == true
            val urlIsHls = currentUrl.contains(".m3u8", ignoreCase = true)
            val isHls = urlIsHls || baseIsHls

            val mediaItem = MediaItem.fromUri(currentUrl)
            val mediaSource = if (isHls) {
                HlsMediaSource.Factory(httpDataSourceFactory).setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, true)).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, DefaultExtractorsFactory().setTsExtractorMode(TsExtractor.MODE_MULTI_PMT).setTsExtractorFlags(tsFlags)).createMediaSource(mediaItem)
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Seek bar position ticker — updates every second when OSD is visible
    LaunchedEffect(showOsd, currentUrl) {
        while (showOsd) {
            isSeekable = exoPlayer.isCurrentMediaItemSeekable && exoPlayer.duration > 0 && exoPlayer.duration != C.TIME_UNSET
            if (isSeekable) {
                seekPosition = exoPlayer.currentPosition
                seekDuration = exoPlayer.duration
            }
            delay(1000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_PAUSE -> exoPlayer.pause(); Lifecycle.Event.ON_RESUME -> exoPlayer.play(); Lifecycle.Event.ON_DESTROY -> exoPlayer.release(); else -> {} }
        }
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // BehindLiveWindowException — auto-recover by seeking to live edge
                if (isBehindLiveWindow(error)) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }

                // PlaylistStuckException — HLS stream went stale, auto-retry up to 3 times
                val isStuck = error.cause is androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker.PlaylistStuckException
                if (isStuck && retryCount < 3) {
                    retryCount++
                    actionMessage = "Stream stalled, retrying ($retryCount/3)..."
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }

                playerError = "Stream Error: ${error.errorCodeName}"
            }
        }
        exoPlayer.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { exoPlayer.removeListener(playerListener); lifecycleOwner.lifecycle.removeObserver(lifecycleObserver); exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        val isPlayerRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        // ⚡ THE INVISIBLE FOCUS ANCHOR ⚡
        // This is physically separated from all UI logic. It traps the D-Pad
        // permanently while watching TV so disappearing menus cannot kill the controls.
        Box(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(playerFocusRequester)
                .focusable(enabled = !showSettings && !showEpgGuide)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> { if (!showSettings && !showEpgGuide) viewModel.getNextChannelUrl(currentUrl)?.let { currentUrl = it }; true }
                            KeyEvent.KEYCODE_DPAD_DOWN -> { if (!showSettings && !showEpgGuide) viewModel.getPrevChannelUrl(currentUrl)?.let { currentUrl = it }; true }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (playerError == null && !showSettings) {
                                    if (showEpgGuide) {
                                        // Already in guide, let guide handle it
                                        false
                                    } else if (showOsd) {
                                        // OSD visible → open EPG guide
                                        showOsd = false
                                        showEpgGuide = true
                                        true
                                    } else {
                                        // Nothing visible → show OSD
                                        showOsd = true; osdTimerKey++
                                        true
                                    }
                                } else true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> {
                                if (!showSettings && !showEpgGuide) {
                                    if (isSeekable && showOsd) {
                                        val seekMs = if (isPlayerRtl) -5_000L else 5_000L
                                        exoPlayer.seekTo((exoPlayer.currentPosition + seekMs).coerceIn(0, exoPlayer.duration))
                                        osdTimerKey++
                                    } else {
                                        if (!isPlayerRtl) { showSettings = true; showOsd = false }
                                        else if (!showOsd) { showEpgGuide = true }
                                    }
                                }; true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!showSettings && !showEpgGuide) {
                                    if (isSeekable && showOsd) {
                                        val seekMs = if (isPlayerRtl) 5_000L else -5_000L
                                        exoPlayer.seekTo((exoPlayer.currentPosition + seekMs).coerceIn(0, exoPlayer.duration))
                                        osdTimerKey++
                                    } else {
                                        if (isPlayerRtl) { showSettings = true; showOsd = false }
                                        else if (!showOsd) { showEpgGuide = true }
                                    }
                                }; true
                            }
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
            Column(modifier = Modifier.padding(bottom = 48.dp, start = 64.dp, end = 64.dp).fillMaxWidth().background(Color(0xD9000000), RoundedCornerShape(40.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp)).padding(horizontal = 40.dp, vertical = 24.dp)) {
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
                        Text(text = osdClock, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSeekable) "◄ 5s  •  ► 5s" else "OK for Guide • ◄ EPG • ► Options",
                            color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp
                        )
                    }
                }
                // Seek bar — only for catch-up / VOD streams
                if (isSeekable && seekDuration > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = formatDuration(seekPosition),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.weight(1f).height(6.dp)
                                .background(Color.White.copy(0.15f), RoundedCornerShape(3.dp))
                        ) {
                            val progress = (seekPosition.toFloat() / seekDuration.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier.fillMaxWidth(progress).fillMaxHeight()
                                    .background(Color(0xFF00BCD4), RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatDuration(seekDuration),
                            color = Color.White.copy(0.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
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

        // ─── EPG GUIDE OVERLAY ───
        AnimatedVisibility(
            visible = showEpgGuide,
            enter = fadeIn(tween(400)) + slideInVertically(animationSpec = tween(500, easing = LinearOutSlowInEasing), initialOffsetY = { it }),
            exit = fadeOut(tween(300)) + slideOutVertically(animationSpec = tween(400, easing = FastOutLinearInEasing), targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (currentChannel != null) {
                EpgGuideOverlay(
                    channel = currentChannel,
                    viewModel = viewModel,
                    focusRequester = epgFocusRequester,
                    onClose = { showEpgGuide = false },
                    onPlayCatchup = { catchupUrl ->
                        showEpgGuide = false
                        currentUrl = catchupUrl
                    },
                    onPlayLive = {
                        showEpgGuide = false
                        // Re-load the original stream URL
                        if (currentUrl != currentChannel.streamUrl) {
                            currentUrl = currentChannel.streamUrl
                        }
                    }
                )
            }
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
            Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "אפשרויות" else "Options", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
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

// ─── 🎬 EPG GUIDE OVERLAY — PREMIUM CATCH-UP TV GUIDE ───

private val EpgAccentLive = Color(0xFF00E5FF)
private val EpgAccentCatchup = Color(0xFFFF9500)
private val EpgGlass = Color.White.copy(alpha = 0.08f)
private val EpgDark = Color(0xFF0A0A0C)
private val epgTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgGuideOverlay(
    channel: ChannelEntity,
    viewModel: IptvViewModel,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onPlayCatchup: (String) -> Unit,
    onPlayLive: () -> Unit
) {
    val now = System.currentTimeMillis()

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Date tabs: Today + 6 days back
    val dateTabs = remember(isRtl) {
        (0..6).map { daysBack ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysBack) }
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000
            val label = when (daysBack) {
                0 -> if (isRtl) "היום" else "Today"
                1 -> if (isRtl) "אתמול" else "Yesterday"
                else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(cal.timeInMillis))
            }
            Triple(label, dayStart, dayEnd)
        }
    }

    var selectedDateIndex by remember { mutableIntStateOf(0) }
    var programs by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val firstProgramFR = remember { FocusRequester() }

    // Load programs when date changes
    LaunchedEffect(selectedDateIndex, channel) {
        isLoading = true
        val (_, dayStart, dayEnd) = dateTabs[selectedDateIndex]
        programs = viewModel.getFullDayProgramsForChannel(channel, dayStart, dayEnd)
        isLoading = false
    }

    // Route focus to the first program after load
    LaunchedEffect(programs) {
        if (programs.isNotEmpty()) {
            delay(200)
            runCatching { firstProgramFR.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.85f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                    onClose(); true
                } else false
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, EpgDark.copy(0.95f), EpgDark, EpgDark)
                    )
                )
                .padding(top = 40.dp)
        ) {
            // ── HEADER: Channel info ──
            Row(
                modifier = Modifier.padding(horizontal = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel logo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(channel.name.take(1), color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "מדריך שידורים" else "TV Guide",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = channel.name,
                        color = Color.White.copy(0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                // Catch-up badge
                Box(
                    modifier = Modifier
                        .background(EpgAccentCatchup.copy(0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, EpgAccentCatchup, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↺ הקלטה 7 ימים" else "↺ 7-DAY CATCH-UP", color = EpgAccentCatchup, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── DATE TABS ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 64.dp),
                modifier = Modifier.focusGroup()
            ) {
                itemsIndexed(dateTabs) { idx, (label, _, _) ->
                    val isSelected = idx == selectedDateIndex
                    Surface(
                        onClick = { selectedDateIndex = idx },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) Color.White else EpgGlass,
                            focusedContainerColor = Color.White,
                            contentColor = if (isSelected) Color.Black else Color.White.copy(0.7f),
                            focusedContentColor = Color.Black
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = if (idx == 0) Modifier.focusRequester(focusRequester) else Modifier
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (idx == 0) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(EpgAccentLive, RoundedCornerShape(50))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── PROGRAMS TIMELINE ──
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "טוען לוח שידורים..." else "Loading schedule...", color = Color.White.copy(0.5f), fontSize = 16.sp)
                }
            } else if (programs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "אין לוח שידורים זמין" else "No schedule available", color = Color.White.copy(0.5f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "נתוני EPG לא מכסים תאריך זה" else "EPG data may not cover this date", color = Color.White.copy(0.3f), fontSize = 14.sp)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 48.dp),
                    modifier = Modifier.weight(1f).focusGroup()
                ) {
                    itemsIndexed(programs, key = { i, p -> "${p.startTime}_$i" }) { idx, program ->
                        val isLive = now in program.startTime..program.endTime
                        val isPast = program.endTime < now


                        EpgProgramCard(
                            program = program,
                            isLive = isLive,
                            isPast = isPast,

                            currentTime = now,
                            modifier = if (idx == 0) Modifier.focusRequester(firstProgramFR) else Modifier,
                            onClick = {
                                when {
                                    isLive -> onPlayLive()
                                    isPast -> {
                                        val catchupUrl = viewModel.buildCatchupUrl(channel.streamUrl, program.startTime)
                                        onPlayCatchup(catchupUrl)
                                    }
                                    // Future programs — just play live
                                    else -> onPlayLive()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgProgramCard(
    program: EpgProgramEntity,
    isLive: Boolean,
    isPast: Boolean,

    currentTime: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val durationMinutes = ((program.endTime - program.startTime) / 60000).coerceAtLeast(1)
    val widthDp = (durationMinutes * 5).toInt().coerceIn(200, 500).dp
    val timeString = "${epgTimeFormat.format(Date(program.startTime))} - ${epgTimeFormat.format(Date(program.endTime))}"

    Surface(
        onClick = onClick,
        modifier = modifier.width(widthDp).height(120.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isLive -> EpgAccentLive.copy(alpha = 0.15f)
                isPast -> Color(0xFF1A1A1E)
                else -> EpgGlass
            },
            focusedContainerColor = Color(0xFF1F1F23)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, if (isLive) EpgAccentLive else if (isPast) EpgAccentCatchup else Color.White
                )
            ),
            border = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = (if (isLive) EpgAccentLive else if (isPast) EpgAccentCatchup else Color.White).copy(0.2f),
                elevation = 16.dp
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Time + status badge row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeString,
                        color = when {
                            isLive -> EpgAccentLive
                            isPast -> Color.White.copy(0.4f)
                            else -> Color.White.copy(0.6f)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    when {
                        isLive -> {
                            Box(
                                Modifier
                                    .background(EpgAccentLive, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "● שידור חי" else "● LIVE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        isPast -> {
                            Box(
                                Modifier
                                    .background(EpgAccentCatchup.copy(0.2f), RoundedCornerShape(4.dp))
                                    .border(1.dp, EpgAccentCatchup, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↺ הקלטה" else "↺ CATCHUP", color = EpgAccentCatchup, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        else -> {
                            Box(
                                Modifier
                                    .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "בקרוב" else "UPCOMING", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Title
                Text(
                    text = program.title.ifBlank { if (LocalLayoutDirection.current == LayoutDirection.Rtl) "תוכניה לא ידועה" else "Unknown Program" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description (if available)
                if (program.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = program.description,
                        color = Color.White.copy(0.4f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Duration
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${durationMinutes}min",
                    color = Color.White.copy(0.3f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live progress bar
            if (isLive && program.startTime > 0) {
                val progress = ((currentTime - program.startTime).toFloat() / (program.endTime - program.startTime).toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(EpgAccentLive)
                    )
                }
            } else if (isPast) {
                // Full orange bar for past programs (catch-up available)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(EpgAccentCatchup.copy(0.4f))
                )
            }
        }
    }
}