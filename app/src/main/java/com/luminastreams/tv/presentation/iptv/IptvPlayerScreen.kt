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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.luminastreams.tv.core.TrustedHttpClient
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
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
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

    val currentChannel by remember { derivedStateOf { channels.find { currentUrl.startsWith(it.streamUrl) } } }
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
        val ch = currentChannel
        if (ch != null) {
            val programs = viewModel.getProgramsForChannel(ch, System.currentTimeMillis())
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

    val iptvHttpClient = remember { TrustedHttpClient.builder().build() }

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
            val httpDataSourceFactory = OkHttpDataSource.Factory(iptvHttpClient).setUserAgent("VLC/3.0.0")
            val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            val urlLower = currentUrl.lowercase()
            val baseUrl = currentChannel?.streamUrl?.lowercase() ?: ""
            val isHls = urlLower.contains(".m3u8") || baseUrl.contains(".m3u8")
                    || urlLower.contains("/live/") || urlLower.contains("/get.php?")
                    || urlLower.contains("/streaming/") || urlLower.contains("type=m3u")

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

    // Lifecycle observer is tied to the lifecycleOwner (safe to re-register on change).
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_PAUSE -> exoPlayer.pause(); Lifecycle.Event.ON_RESUME -> exoPlayer.play(); else -> {} }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
    }

    // Player listener + release are tied to the player instance itself (keyed on Unit
    // so release() is only invoked when this composable actually leaves composition,
    // not when the lifecycleOwner changes underneath a still-remembered player).
    DisposableEffect(Unit) {
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // BehindLiveWindowException — auto-recover by seeking to live edge.
                // Cap retries so a permanently broken stream doesn't spin the CPU forever.
                if (isBehindLiveWindow(error)) {
                    if (retryCount < 5) {
                        retryCount++
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                        return
                    } else {
                        playerError = "Stream fell too far behind live edge"
                        return
                    }
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

                // Auto-fallback: If Progressive source failed (ParserException), retry as HLS
                val isParserError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                        || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                        || error.cause?.javaClass?.simpleName == "UnrecognizedInputFormatException"
                if (isParserError && retryCount < 1) {
                    retryCount++
                    actionMessage = "Retrying as HLS stream..."
                    val httpFactory = OkHttpDataSource.Factory(iptvHttpClient).setUserAgent("VLC/3.0.0")
                    val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                    val hlsSource = HlsMediaSource.Factory(httpFactory)
                        .setExtractorFactory(DefaultHlsExtractorFactory(tsFlags, true))
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(MediaItem.fromUri(currentUrl))
                    exoPlayer.setMediaSource(hlsSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }

                playerError = "Stream Error: ${error.errorCodeName}"
            }
        }
        exoPlayer.addListener(playerListener)
        onDispose { exoPlayer.removeListener(playerListener); exoPlayer.release() }
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
                    Text(text = playerError.orEmpty(), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
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
                            val ch = currentChannel
                            if (ch != null && ch.logoUrl.isNotBlank()) AsyncImage(model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            else Text(currentChannel?.name?.take(1) ?: "", color = Color.Black, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                if ((currentChannel?.number ?: 0) > 0) { Text(text = "${currentChannel?.number}", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(12.dp)) }
                                Text(text = currentChannel?.name ?: "", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val epg = currentEpg
                            if (epg != null) {
                                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
                                Text(text = "${timeFormat.format(Date(epg.startTime))} - ${timeFormat.format(Date(epg.endTime))}  •  ${epg.title}", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Normal)
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
            val ch = currentChannel
            if (ch != null) {
                EpgGuideOverlay(
                    channel = ch,
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
                        if (currentUrl != ch.streamUrl) {
                            currentUrl = ch.streamUrl
                        }
                    }
                )
            }
        }
    }
}
