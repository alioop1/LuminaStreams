@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.iptv

import com.luminastreams.tv.core.DeviceProfile
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.text.SimpleDateFormat
import java.util.*

// ── Palette ────────────────────────────────────────────────────────────────────
private val BG       = Color(0xFF07070A)
private val SURFACE  = Color(0xFF12121C)
private val SURFACE2 = Color(0xFF1C1C2A)
private val ACCENT   = Color(0xFF3D8BFF)
private val ACCENT2  = Color(0xFF00D4FF)
private val RED      = Color(0xFFFF3B30)
private val WHITE    = Color(0xFFFFFFFF)
private val MUTED    = Color(0x99FFFFFF)
private val MUTED2   = Color(0x33FFFFFF)
private val HUD_BG   = Color(0xF00A0A12)
private val CARD_BG  = Color(0xFF16161F)
private val GREEN    = Color(0xFF30D158)

// ── Dynamic Backgrounds (computed ONCE at class-load time, NOT on every recomposition) ──
private val CardGradientNormal: Brush = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) SolidColor(Color.Black.copy(0.85f)) else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.72f)))
private val CardGradientFocused: Brush = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) SolidColor(BG) else Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.72f)))
private val ZappingGradientNorm: Brush = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) SolidColor(Color.Black.copy(0.85f)) else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
private val ZappingHudBg: Brush = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) SolidColor(Color.Black.copy(0.95f)) else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f), Color.Black.copy(0.96f)))
private val TopBarGradient: Brush = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) SolidColor(Color.Black.copy(0.92f)) else Brush.verticalGradient(listOf(Color.Black.copy(0.92f), Color.Black.copy(0.55f), Color.Transparent))

private val ProgBarBrush        = Brush.horizontalGradient(listOf(ACCENT, ACCENT2))
private val ZappingDividerBrush = Brush.horizontalGradient(listOf(Color.Transparent, ACCENT.copy(0.35f), ACCENT.copy(0.5f), ACCENT.copy(0.35f), Color.Transparent))
private val ZappingLogoBgBrush  = Brush.linearGradient(listOf(Color.White.copy(0.10f), Color.White.copy(0.04f)))
private val PQSBgBrushLtr       = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xF0080810)))
private val PQSBgBrushRtl       = Brush.horizontalGradient(listOf(Color(0xF0080810), Color.Transparent))

// ═══════════════════════════════════════════════════════════════════════════════
// ROOT SCREEN
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun IptvScreen(viewModel: IptvViewModel, onNavigateBack: () -> Unit) {
    val ch  by viewModel.channelState.collectAsStateWithLifecycle()
    val pl  by viewModel.playerState.collectAsStateWithLifecycle()
    val ui  by viewModel.uiState.collectAsStateWithLifecycle()

    val state = remember(ch, pl, ui) { IptvState(
        playlists = ch.playlists, activePlaylistId = ch.activePlaylistId,
        channels = ch.channels, groups = ch.groups, selectedGroup = ch.selectedGroup,
        filteredChannels = ch.filteredChannels, searchQuery = ch.searchQuery,
        epgData = ch.epgData, epgLoadState = ch.epgLoadState,
        channelLogos = ch.channelLogos, favoriteChannelIds = ch.favoriteChannelIds,
        recentChannelIds = ch.recentChannelIds, channelSortMode = ch.channelSortMode,
        loadState = ch.loadState, viewMode = ch.viewMode,
        currentChannel = pl.currentChannel, currentProgram = pl.currentProgram,
        nextProgram = pl.nextProgram, isRecording = pl.isRecording,
        recordingChannelId = pl.recordingChannelId, subtitlesEnabled = pl.subtitlesEnabled,
        audioTrackIndex = pl.audioTrackIndex, streamQuality = pl.streamQuality,
        sleepTimer = pl.sleepTimer, sleepTimerRemainingMs = pl.sleepTimerRemainingMs,
        multiViewChannels = pl.multiViewChannels, showMultiView = pl.showMultiView,
        showAddPlaylist = ui.showAddPlaylist, showEpgGuide = ui.showEpgGuide,
        showQrCode = ui.showQrCode, qrCodeChannel = ui.qrCodeChannel,
        showSleepTimerPicker = ui.showSleepTimerPicker, showSettings = ui.showSettings,
        showParentalPinEntry = ui.showParentalPinEntry,
        pendingLockedChannel = ui.pendingLockedChannel,
        parentalLockEnabled = ui.parentalLockEnabled, parentalPin = ui.parentalPin,
        addPlaylistName = ui.addPlaylistName, addPlaylistUrl = ui.addPlaylistUrl,
        addPlaylistEpgUrl = ui.addPlaylistEpgUrl, localIpAddress = ui.localIpAddress,
        epgDayOffset = ui.epgDayOffset,
    ) }
    val context     = LocalContext.current
    val exo         = remember { ExoPlayerWrapper(context) }
    val videoAR     by exo.videoAspectRatio.collectAsState()
    val currTracks  by exo.currentTracks.collectAsState()
    val scope       = rememberCoroutineScope()
    val isLow       = DeviceProfile.tier == DeviceProfile.Tier.LOW

    var isFullScreen       by remember { mutableStateOf(false) }
    var showToast          by remember { mutableStateOf("") }
    var showZapping        by remember { mutableStateOf(false) }
    var showSideMenu       by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var arMode             by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var lastAction         by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val toastJob           = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }

    val topBarFR         = remember { FocusRequester() }
    val epgBtnFR         = remember { FocusRequester() }
    val addBtnFR         = remember { FocusRequester() }
    val setgBtnFR        = remember { FocusRequester() }
    val gridFR           = remember { FocusRequester() }
    val playerFR         = remember { FocusRequester() }
    val zappingFR        = remember { FocusRequester() }
    val sideFR           = remember { FocusRequester() }
    val playerSettingsFR = remember { FocusRequester() }

    val isAnyDialogOpen = state.loadState is IptvLoadState.Loading || state.showAddPlaylist
            || state.showQrCode || state.showSettings || state.showSleepTimerPicker
            || state.showParentalPinEntry || state.showEpgGuide || showPlayerSettings

    fun toast(msg: String) { showToast = msg; toastJob[0]?.cancel(); toastJob[0] = scope.launch { delay(2500); showToast = "" } }
    fun resetIdle() { lastAction = System.currentTimeMillis() }

    val currChIdx by remember(state.currentChannel, state.filteredChannels) {
        derivedStateOf { state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id } }
    }

    fun switchUp() {
        val i = currChIdx; if (i > 0) {
            val channel = state.filteredChannels[i - 1]
            viewModel.onEvent(IptvEvent.SelectChannel(channel)); exo.prepareStream(channel.streamUrl); exo.play()
            toast("▲ ${channel.name}")
        }
    }
    fun switchDown() {
        val i = currChIdx; if (i < state.filteredChannels.size - 1) {
            val channel = state.filteredChannels[i + 1]
            viewModel.onEvent(IptvEvent.SelectChannel(channel)); exo.prepareStream(channel.streamUrl); exo.play()
            toast("▼ ${channel.name}")
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { if (state.channels.isNotEmpty()) gridFR.requestFocus() else topBarFR.requestFocus() }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            delay(80); runCatching { playerFR.requestFocus() }
        } else {
            exo.pause()
            showZapping = false; showSideMenu = false; showPlayerSettings = false
            delay(80); runCatching { gridFR.requestFocus() }
        }
    }

    LaunchedEffect(state.subtitlesEnabled) {
        if (state.subtitlesEnabled) {
            val params = exo.player.trackSelectionParameters.buildUpon()
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            exo.player.trackSelectionParameters = params.build()
        } else {
            exo.disableSubtitles()
        }
    }

    val activePlaylist = state.playlists.firstOrNull { it.isActive }
    val activeEpgUrl   = activePlaylist?.epgUrl?.trim() ?: ""
    LaunchedEffect(activeEpgUrl, activePlaylist?.id) {
        if (activeEpgUrl.isNotBlank()) {
            delay(800L)
            viewModel.onEvent(IptvEvent.RefreshEpg)
        }
    }

    LaunchedEffect(lastAction, showZapping, showSideMenu) {
        if ((showZapping || showSideMenu) && isFullScreen && !isAnyDialogOpen) {
            delay(6_000L); showZapping = false; showSideMenu = false
            runCatching { playerFR.requestFocus() }
        }
    }

    LaunchedEffect(showZapping)        { if (showZapping)        { delay(60); runCatching { zappingFR.requestFocus()       } } }
    LaunchedEffect(showSideMenu)       { if (showSideMenu)       { delay(60); runCatching { sideFR.requestFocus()          } } }
    LaunchedEffect(showPlayerSettings) { if (showPlayerSettings) { delay(80); runCatching { playerSettingsFR.requestFocus() } } }

    BackHandler {
        when {
            showPlayerSettings          -> showPlayerSettings = false
            state.showEpgGuide          -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            state.showSettings          -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showAddPlaylist       -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showQrCode            -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showSleepTimerPicker  -> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            showZapping || showSideMenu -> {
                showZapping = false; showSideMenu = false; runCatching { playerFR.requestFocus() }
            }
            isFullScreen -> isFullScreen = false
            else         -> onNavigateBack()
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

        // ── 1. DASHBOARD ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !isFullScreen,
            enter = if (isLow) fadeIn(snap()) else fadeIn(tween(280)),
            exit = if (isLow) fadeOut(snap()) else fadeOut(tween(280))
        ) {
            Column(Modifier.fillMaxSize()) {
                TopNavBar(
                    state     = state,
                    topBarFR  = topBarFR, epgBtnFR  = epgBtnFR,
                    addBtnFR  = addBtnFR, setgBtnFR = setgBtnFR,
                    gridFR    = gridFR,
                    onBack        = onNavigateBack,
                    onSettings    = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                    onEpgGuide    = { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                    onAddPlaylist = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) }
                )
                if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                    IptvEmptyState(
                        onAddClick      = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) },
                        onSettingsClick = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                        emptyStateFR    = gridFR
                    )
                } else {
                    DashboardContent(
                        channels         = ch.channels,
                        filteredChannels = ch.filteredChannels,
                        favoriteIds      = ch.favoriteChannelIds,
                        groups           = ch.groups,
                        epgData          = ch.epgData,
                        currentChannelId = pl.currentChannel?.id,
                        gridFR           = gridFR,
                        navBarFR         = topBarFR,
                        currTracks       = currTracks,
                        onChannelClicked = { chClicked ->
                            viewModel.onEvent(IptvEvent.SelectChannel(chClicked))
                            isFullScreen = true
                        }
                    )
                }
            }
        }

        // ── 2. FULLSCREEN PLAYER ──────────────────────────────────────────────
        if (isFullScreen) {
            var surfaceReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                snapshotFlow { surfaceReady }.first { it }
                state.currentChannel?.let { ch ->
                    exo.prepareStream(ch.streamUrl)
                    exo.play()
                }
            }

            Box(
                Modifier.fillMaxSize().background(Color.Black).focusRequester(playerFR).focusable()
                    .onPreviewKeyEvent { ev ->
                        resetIdle()

                        if (ev.type == KeyEventType.KeyDown) {
                            when (ev.key.nativeKeyCode) {
                                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP,
                                KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> {
                                    showZapping = false; showSideMenu = false; showPlayerSettings = false
                                    runCatching { playerFR.requestFocus() }
                                    switchDown(); return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN,
                                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                                    showZapping = false; showSideMenu = false; showPlayerSettings = false
                                    runCatching { playerFR.requestFocus() }
                                    switchUp(); return@onPreviewKeyEvent true
                                }
                            }
                        }

                        if (isAnyDialogOpen || showZapping || showSideMenu || showPlayerSettings)
                            return@onPreviewKeyEvent false

                        if (ev.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            ev.key.nativeKeyCode == KeyEvent.KEYCODE_ENTER) {
                            if (ev.type == KeyEventType.KeyUp) showZapping = true
                            return@onPreviewKeyEvent true
                        }

                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_UP        -> { switchUp();   true }
                            KeyEvent.KEYCODE_DPAD_DOWN      -> { switchDown(); true }
                            KeyEvent.KEYCODE_DPAD_LEFT      -> { showSideMenu = true; true }
                            KeyEvent.KEYCODE_DPAD_RIGHT     -> { showPlayerSettings = true; true }
                            KeyEvent.KEYCODE_MEDIA_NEXT     -> { switchDown(); true }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { switchUp(); true }
                            KeyEvent.KEYCODE_GUIDE,
                            KeyEvent.KEYCODE_MENU           -> { viewModel.onEvent(IptvEvent.ShowEpgGuide); true }
                            else -> false
                        }
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().focusable(false),
                    factory  = { ctx ->
                        AspectRatioFrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            val sv = SurfaceView(ctx).apply { keepScreenOn = true }
                            sv.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                    exo.player.setVideoSurfaceHolder(holder)
                                    surfaceReady = true
                                }
                                override fun surfaceChanged(h: android.view.SurfaceHolder, fmt: Int, w: Int, ht: Int) {}
                                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
                            })
                            addView(sv)
                        }
                    },
                    update = { arLayout ->
                        arLayout.resizeMode = arMode
                        if (videoAR > 0f) arLayout.setAspectRatio(videoAR)
                    }
                )

                // ── Top HUD ──
                val enterTop = if (isLow) fadeIn(snap()) else fadeIn(tween(260)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it }
                val exitTop  = if (isLow) fadeOut(snap()) else fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it }

                AnimatedVisibility(
                    visible  = (showZapping || showSideMenu) && !isAnyDialogOpen,
                    enter    = enterTop,
                    exit     = exitTop,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(10f)
                ) {
                    val currentCh   = state.currentChannel
                    val epgs = currentCh?.let { state.epgData[it.id] }
                    val now  = epgs?.firstOrNull { it.isLiveNow }
                    Box(
                        Modifier.fillMaxWidth()
                            .background(TopBarGradient)
                            .padding(start = 44.dp, end = 44.dp, top = 22.dp, bottom = 32.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(WHITE.copy(0.08f)), Alignment.Center) {
                                    if (currentCh != null) ChannelLogoImage(currentCh, currentCh.logoUrl, 36.dp)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LiveBadge()
                                        if (currentCh?.resolution?.isNotBlank() == true) ResBadge(currentCh.resolution)
                                        if (currChIdx >= 0) Text("CH ${currChIdx + 1}",
                                            color = WHITE.copy(0.4f), fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                                        StreamTracksBadges(currTracks)
                                    }
                                    Text(currentCh?.name ?: "", color = WHITE, fontSize = 22.sp,
                                        fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (now != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(now.title, color = WHITE.copy(0.65f), fontSize = 12.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            ProgBar(now.progressFraction, 80.dp)
                                            Text("${now.remainingMinutes}m left", color = WHITE.copy(0.45f), fontSize = 11.sp)
                                        }
                                    } else {
                                        Text(currentCh?.groupTitle?.uppercase() ?: "", color = WHITE.copy(0.38f),
                                            fontSize = 11.sp, letterSpacing = 1.8.sp)
                                    }
                                }
                            }
                            FullscreenClock()
                        }
                    }
                }

                // ── Zapping bar ──
                val enterZap = if (isLow) fadeIn(snap()) else fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it })
                val exitZap  = if (isLow) fadeOut(snap()) else fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it })

                AnimatedVisibility(
                    visible  = showZapping && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = enterZap,
                    exit     = exitZap,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
                ) {
                    ZappingHud(
                        channels        = state.filteredChannels,
                        currentChannel  = state.currentChannel,
                        epgData         = state.epgData,
                        currTracks      = currTracks,
                        zappingFR       = zappingFR,
                        onSelectChannel = { c ->
                            if (state.currentChannel?.id != c.id) {
                                viewModel.onEvent(IptvEvent.SelectChannel(c))
                                exo.prepareStream(c.streamUrl); exo.play()
                            }
                            showZapping = false; runCatching { playerFR.requestFocus() }
                        },
                        onOpenCategories = { showZapping = false; showSideMenu = true },
                        onDismiss        = { showZapping = false; runCatching { playerFR.requestFocus() } },
                        onIdleReset      = ::resetIdle,
                        onOpenEpgGuide   = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }
                    )
                }

                // ── Side group menu ──
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val enterSide = if (isLow) fadeIn(snap()) else slideInHorizontally(initialOffsetX = { if (isRtl) it else -it }) + fadeIn(tween(180))
                val exitSide  = if (isLow) fadeOut(snap()) else slideOutHorizontally(targetOffsetX = { if (isRtl) it else -it }) + fadeOut(tween(140))

                AnimatedVisibility(
                    visible  = showSideMenu && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = enterSide,
                    exit     = exitSide,
                    modifier = Modifier.align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart).zIndex(30f)
                ) {
                    SideGroupMenu(
                        groups        = state.groups,
                        selectedGroup = state.selectedGroup,
                        sideFR        = sideFR,
                        onSelectGroup = { g ->
                            viewModel.onEvent(IptvEvent.SelectGroup(g))
                            showSideMenu = false; showZapping = true
                        },
                        onDismiss   = { showSideMenu = false; runCatching { playerFR.requestFocus() } },
                        onIdleReset = ::resetIdle
                    )
                }

                // ── Player quick settings ──
                val isRtlPQS = LocalLayoutDirection.current == LayoutDirection.Rtl
                val enterPQS = if (isLow) fadeIn(snap()) else slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { if (isRtlPQS) -it else it } + fadeIn(tween(200))
                val exitPQS  = if (isLow) fadeOut(snap()) else slideOutHorizontally(tween(180)) { if (isRtlPQS) -it else it } + fadeOut(tween(160))

                AnimatedVisibility(
                    visible  = showPlayerSettings,
                    enter    = enterPQS,
                    exit     = exitPQS,
                    modifier = Modifier.align(if (isRtlPQS) Alignment.CenterStart else Alignment.CenterEnd).zIndex(35f)
                ) {
                    PlayerQuickSettings(
                        exo         = exo,
                        currTracks  = currTracks,
                        arMode      = arMode,
                        subtitlesOn = state.subtitlesEnabled,
                        settingsFR  = playerSettingsFR,
                        onArChange  = { arMode = it },
                        onSubtitles = { viewModel.onEvent(IptvEvent.ToggleSubtitles) },
                        onDismiss   = { showPlayerSettings = false; runCatching { playerFR.requestFocus() } }
                    )
                }

                // ── Hints ──
                var showHints by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(5_000L); showHints = false }
                AnimatedVisibility(
                    showHints && !showZapping && !showSideMenu && !isAnyDialogOpen,
                    enter    = EnterTransition.None,
                    exit     = fadeOut(tween(800)),
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 32.dp, vertical = 24.dp).zIndex(5f)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HintPill("↑↓", "CH +/-")
                        HintPill("OK", "Channels")
                        HintPill("←", "Categories")
                        HintPill("→", "Settings")
                    }
                }
            }
        }

        // ── Dialogs ────────────────────────────────────────────────────────────
        if (state.loadState is IptvLoadState.Loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(200f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.luminastreams.tv.ui.components.LoadingIndicator()
                    Text("Loading playlist…", color = MUTED, fontSize = 14.sp)
                }
            }
        }
        if (state.showAddPlaylist) IptvDialog({ viewModel.onEvent(IptvEvent.HideAddPlaylist) }) { fr, dismiss ->
            PlaylistManagerDialog(state, fr, dismiss, viewModel::onEvent)
        }
        if (state.showSettings) IptvDialog({ viewModel.onEvent(IptvEvent.HideIptvSettings) }) { fr, dismiss ->
            SmartSettingsDialog(state, exo, currTracks, fr, dismiss, viewModel::onEvent)
        }
        if (state.showEpgGuide) IptvDialog({ viewModel.onEvent(IptvEvent.HideEpgGuide) }) { fr, dismiss ->
            FullEpgGuideDialog(state, viewModel, fr, dismiss)
        }
        if (state.showQrCode && state.qrCodeChannel != null) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideQrCode) }) { fr, _ ->
                ChannelQrDialog(state.qrCodeChannel!!, fr) { viewModel.onEvent(IptvEvent.HideQrCode) }
            }
        }

        // ── EPG loading toast ──
        var epgLoadingVisible by remember { mutableStateOf(false) }
        LaunchedEffect(state.epgLoadState) {
            if (state.epgLoadState is IptvLoadState.Loading) {
                epgLoadingVisible = true; delay(30_000L); epgLoadingVisible = false
            } else {
                epgLoadingVisible = false
            }
        }
        if (epgLoadingVisible) {
            Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).zIndex(50f)
                .clip(RoundedCornerShape(20.dp)).background(SURFACE.copy(0.92f))
                .padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.luminastreams.tv.ui.components.LoadingIndicator(size = 14.dp)
                    Text("Loading EPG…", color = MUTED, fontSize = 11.sp)
                }
            }
        }

        AnimatedVisibility(showToast.isNotEmpty(),
            enter    = if (isLow) fadeIn(snap()) else fadeIn() + slideInVertically { it },
            exit     = if (isLow) fadeOut(snap()) else fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).zIndex(100f)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(WHITE.copy(0.14f))
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(showToast, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CLOCK
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun FullscreenClock() {
    var t by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            t = fmt.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Text(t, color = WHITE, fontSize = 38.sp, fontWeight = FontWeight.Thin, letterSpacing = (-1).sp)
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOG WRAPPER
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun IptvDialog(
    onDismiss : () -> Unit,
    content   : @Composable (fr: FocusRequester, onDismiss: () -> Unit) -> Unit
) {
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(if (isLow) Color.Black.copy(0.95f) else Color.Black.copy(0.86f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            content(fr, onDismiss)
        }
    }
}

private fun Modifier.dialogCard(onDismiss: () -> Unit): Modifier =
    this.focusGroup()
        .focusProperties { exit = { FocusRequester.Cancel } }
        .onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                onDismiss(); true
            } else false
        }
        .pointerInput(Unit) { detectTapGestures { } }

// ═══════════════════════════════════════════════════════════════════════════════
// TOP NAV BAR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun TopNavBar(
    state     : IptvState,
    topBarFR  : FocusRequester,
    epgBtnFR  : FocusRequester,
    addBtnFR  : FocusRequester,
    setgBtnFR : FocusRequester,
    gridFR    : FocusRequester,
    onBack        : () -> Unit,
    onSettings    : () -> Unit,
    onEpgGuide    : () -> Unit,
    onAddPlaylist : () -> Unit
) {
    var timeStr by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isHigh = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    LaunchedEffect(Unit) {
        while (true) {
            timeStr = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 48.dp)
            .focusGroup()
            .focusProperties {
                exit = { dir ->
                    when (dir) {
                        FocusDirection.Up   -> FocusRequester.Cancel
                        FocusDirection.Down -> gridFR
                        else                -> FocusRequester.Default
                    }
                }
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NavIconBtn(Icons.Default.ArrowBack, Modifier.size(42.dp).focusRequester(topBarFR), onBack, isHigh)

        Text("LUMINA IPTV", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        if (state.channels.isNotEmpty()) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ACCENT.copy(0.18f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${state.channels.size} CH", color = ACCENT2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        if (state.epgData.isNotEmpty()) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(GREEN.copy(0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("EPG ✓", color = GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(onClick = onEpgGuide,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
                focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
            glow     = if (isHigh) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.4f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier.height(36.dp).focusRequester(epgBtnFR)
        ) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(15.dp))
                Text("TV Guide", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(onClick = onAddPlaylist,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = ACCENT.copy(0.18f),
                focusedContainerColor = ACCENT, contentColor = ACCENT2, focusedContentColor = WHITE),
            glow     = if (isHigh) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.4f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier.height(36.dp).focusRequester(addBtnFR)
        ) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.PlaylistAdd, null, Modifier.size(15.dp))
                Text("Add Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        NavIconBtn(Icons.Default.Settings, Modifier.size(42.dp).focusRequester(setgBtnFR), onSettings, isHigh)
        Text(timeStr, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
    isHighTier: Boolean
) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
            focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
        glow   = if (isHighTier) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.4f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = modifier) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(19.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HERO EPG SECTION
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroEpgSection(
    channel         : IptvChannel?,
    epgData         : Map<String, List<EpgProgram>>,
    currTracks      : androidx.media3.common.Tracks? = null,
    isPlayingChannel: Boolean = false
) {
    if (channel == null) return
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val epgs    = epgData[channel.id]
    val now     = remember(epgs) { System.currentTimeMillis() }
    val nowProg = remember(epgs) { epgs?.firstOrNull { it.isLiveNow } }
    val nextProg = remember(epgs) { epgs?.firstOrNull { it.startTime > now && !it.isLiveNow } }

    Row(Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 48.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.size(118.dp).clip(RoundedCornerShape(14.dp)).background(SURFACE2), Alignment.Center) {
            ChannelLogoImage(channel, channel.logoUrl, 98.dp)
        }
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBadge()
                Text(channel.groupTitle, color = MUTED, fontSize = 13.sp)
                if (channel.resolution.isNotBlank()) ResBadge(channel.resolution)
                if (isPlayingChannel && currTracks != null) StreamTracksBadges(currTracks)
            }
            Spacer(Modifier.height(5.dp))
            Text("${channel.number} · ${channel.name}", color = WHITE, fontSize = 28.sp,
                fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            if (nowProg != null) {
                Text(nowProg.title, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("${tf.format(Date(nowProg.startTime))} – ${tf.format(Date(nowProg.endTime))}",
                        color = MUTED, fontSize = 12.sp)
                    ProgBar(nowProg.progressFraction, 150.dp)
                    Text("${nowProg.remainingMinutes}m left", color = MUTED, fontSize = 12.sp)
                }
                nextProg?.let {
                    Text("Next: ${it.title}  ·  ${tf.format(Date(it.startTime))}",
                        color = MUTED.copy(0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(if (epgs == null) "No EPG data" else "No current program info", color = MUTED, fontSize = 14.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DASHBOARD CONTENT - HIGH PERFORMANCE WRAPPER
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun DashboardContent(
    channels         : List<IptvChannel>,
    filteredChannels : List<IptvChannel>,
    favoriteIds      : Set<String>,
    groups           : List<String>,
    epgData          : Map<String, List<EpgProgram>>,
    currentChannelId : String?,
    gridFR           : FocusRequester,
    navBarFR         : FocusRequester,
    currTracks       : androidx.media3.common.Tracks?,
    onChannelClicked : (IptvChannel) -> Unit
) {
    val focusedChannelState = remember { mutableStateOf<IptvChannel?>(null) }

    Box {
        HeroEpgWrapper(
            focusedState     = focusedChannelState,
            currentChannelId = currentChannelId,
            epgData          = epgData,
            currTracks       = currTracks,
            channels         = channels
        )
    }
    ChannelsDashboard(
        channels         = channels,
        filteredChannels = filteredChannels,
        favoriteIds      = favoriteIds,
        groups           = groups,
        gridFR           = gridFR,
        navBarFR         = navBarFR,
        onChannelFocused = { ch ->
            if (focusedChannelState.value?.id != ch.id) focusedChannelState.value = ch
        },
        onChannelClicked = onChannelClicked
    )
}

@Composable
private fun HeroEpgWrapper(
    focusedState    : State<IptvChannel?>,
    currentChannelId: String?,
    epgData         : Map<String, List<EpgProgram>>,
    currTracks      : androidx.media3.common.Tracks?,
    channels        : List<IptvChannel>
) {
    var debouncedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    val focus = focusedState.value

    LaunchedEffect(focus) {
        if (focus != null) {
            delay(150L)
            debouncedChannel = focus
        }
    }
    val currentFocus = debouncedChannel ?: focus ?: channels.firstOrNull()

    HeroEpgSection(
        channel          = currentFocus,
        epgData          = epgData,
        currTracks       = currTracks,
        isPlayingChannel = currentFocus?.id == currentChannelId
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// CHANNELS DASHBOARD
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChannelsDashboard(
    channels        : List<IptvChannel>,
    filteredChannels: List<IptvChannel>,
    favoriteIds     : Set<String>,
    groups          : List<String>,
    gridFR          : FocusRequester,
    navBarFR        : FocusRequester,
    onChannelFocused: (IptvChannel) -> Unit,
    onChannelClicked: (IptvChannel) -> Unit
) {
    val favorites by produceState(initialValue = emptyList(), key1 = channels, key2 = favoriteIds) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            channels.filter { it.id in favoriteIds }
        }
    }
    // Use remember instead of a second produceState — the ViewModel already processed channels
    // and groupBy on a large list duplicates that work on the UI's Default dispatcher.
    val groupedChannels = remember(channels) { channels.groupBy { it.groupTitle } }
    val filteredGroups  = remember(groups) { groups.filter { it != "All" && it != "Favorites" && it != "Recent" } }

    LazyColumn(contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().focusGroup()) {
        if (favorites.isNotEmpty()) {
            item(key = "fav_row") {
                HorizontalChannelRow("⭐ Favorites", favorites, true, gridFR, navBarFR, onChannelFocused, onChannelClicked)
            }
        }
        filteredGroups.forEachIndexed { idx, group ->
            val chs = groupedChannels[group] ?: return@forEachIndexed
            item(key = "group_$group") {
                HorizontalChannelRow(
                    title      = group,
                    channels   = chs,
                    isFirstRow = favorites.isEmpty() && idx == 0,
                    rowFR      = gridFR,
                    upFR       = if (favorites.isEmpty() && idx == 0) navBarFR else null,
                    onFocus    = onChannelFocused,
                    onClick    = onChannelClicked
                )
            }
        }
    }
}

@Composable
private fun HorizontalChannelRow(
    title     : String,
    channels  : List<IptvChannel>,
    isFirstRow: Boolean,
    rowFR     : FocusRequester,
    upFR      : FocusRequester?,
    onFocus   : (IptvChannel) -> Unit,
    onClick   : (IptvChannel) -> Unit
) {
    Column {
        Row(Modifier.padding(start = 48.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.width(3.dp).height(13.dp).background(ACCENT, RoundedCornerShape(2.dp)))
            Text(title, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${channels.size}", color = MUTED, fontSize = 12.sp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
                .let { m -> if (isFirstRow) m.focusRequester(rowFR) else m }
                .focusProperties { if (upFR != null) up = upFR }
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "Ch" }) { _, ch ->
                ChannelCard(
                    channel = ch,
                    logoUrl = ch.logoUrl,
                    modifier = Modifier,
                    onFocus  = remember(ch.id) { { onFocus(ch) } },
                    onClick  = remember(ch.id) { { onClick(ch) } }
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel : IptvChannel,
    logoUrl : String,
    modifier: Modifier,
    onFocus : () -> Unit,
    onClick : () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isLow  = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val isHigh = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    val scale by animateFloatAsState(
        targetValue = if (focused) (if (isHigh) 1.08f else 1.04f) else 1f,
        animationSpec = if (isLow) snap() else tween(120, easing = FastOutSlowInEasing),
        label = "cs"
    )
    val dynamicGlow = if (isHigh && focused) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.45f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None)

    // במכשירי LOW: הימנעות מוחלטת מיצירת Offscreen Buffer באמצעות graphicsLayer!
    val modifierWithScale = if (isLow) modifier else modifier.graphicsLayer { scaleX = scale; scaleY = scale }

    Box(modifierWithScale.width(165.dp).aspectRatio(16f / 9f)) {
        Surface(onClick = onClick,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG,
                focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border   = ClickableSurfaceDefaults.border(border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, WHITE), shape = RoundedCornerShape(12.dp))),
            glow     = dynamicGlow,
            modifier = Modifier.fillMaxSize()
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                ChannelLogoImage(channel, logoUrl, 76.dp, focused)
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (focused) BG.copy(0.7f) else Color.Black.copy(0.6f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("${channel.number}", color = if (focused) BG else MUTED,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(if (focused) CardGradientFocused else CardGradientNormal)
                    .padding(6.dp)) {
                    Text(channel.name, color = if (focused) BG else WHITE,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// ZAPPING HUD
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ZappingHud(
    channels       : List<IptvChannel>,
    currentChannel : IptvChannel?,
    epgData        : Map<String, List<EpgProgram>>,
    currTracks     : androidx.media3.common.Tracks?,
    zappingFR      : FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit,
    onOpenCategories: () -> Unit,
    onDismiss      : () -> Unit,
    onIdleReset    : () -> Unit,
    onOpenEpgGuide : () -> Unit
) {
    val initialIdx = remember(currentChannel?.id) {
        channels.indexOfFirst { it.id == currentChannel?.id }.let { if (it > 2) it - 2 else 0 }.coerceAtLeast(0)
    }
    val listState  = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)
    var focusedCh  by remember { mutableStateOf(currentChannel) }
    val scope      = rememberCoroutineScope()
    val tf         = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isHigh     = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    Box(
        Modifier.fillMaxWidth()
            .background(ZappingHudBg)
            .focusGroup()
            .focusProperties {
                exit = { dir ->
                    when (dir) {
                        FocusDirection.Up   -> { onOpenCategories(); onIdleReset(); FocusRequester.Cancel }
                        FocusDirection.Down -> { onDismiss(); FocusRequester.Cancel }
                        else                -> FocusRequester.Default
                    }
                }
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                onIdleReset()
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onDismiss(); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        val target = (listState.firstVisibleItemIndex + 7).coerceAtMost(channels.lastIndex)
                        scope.launch { listState.animateScrollToItem(target) }; true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        val target = (listState.firstVisibleItemIndex - 7).coerceAtLeast(0)
                        scope.launch { listState.animateScrollToItem(target) }; true
                    }
                    else -> false
                }
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp, top = 44.dp)) {
            key(focusedCh?.id) {
                focusedCh?.let { fCh ->
                    val epgs     = epgData[fCh.id]
                    val nowProg  = epgs?.firstOrNull { it.isLiveNow }
                    val nextProg = epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow }

                    Row(Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                            .background(ZappingLogoBgBrush),
                            Alignment.Center) {
                            ChannelLogoImage(fCh, fCh.logoUrl, 54.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiveBadge()
                                if (fCh.resolution.isNotBlank()) ResBadge(fCh.resolution)
                                Text("CH ${fCh.number}", color = WHITE.copy(0.35f), fontSize = 11.sp, letterSpacing = 1.sp)
                                if (fCh.id == currentChannel?.id && currTracks != null) StreamTracksBadges(currTracks)
                            }
                            Text(fCh.name, color = WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (nowProg != null) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(nowProg.title, color = WHITE.copy(0.7f), fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, false))
                                    ProgBar(nowProg.progressFraction, 90.dp)
                                    Text("${nowProg.remainingMinutes}m", color = WHITE.copy(0.45f), fontSize = 12.sp)
                                }
                                nextProg?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.size(4.dp).background(ACCENT, CircleShape))
                                        Text("Next: ${it.title}", color = WHITE.copy(0.38f), fontSize = 11.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(tf.format(Date(it.startTime)), color = ACCENT.copy(0.7f), fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Text(fCh.groupTitle.uppercase(), color = WHITE.copy(0.3f), fontSize = 11.sp, letterSpacing = 1.8.sp)
                            }
                        }
                        Surface(onClick = { onOpenEpgGuide() },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = WHITE.copy(0.10f),
                                focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                            glow     = if (isHigh) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.4f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                            modifier = Modifier.height(44.dp).align(Alignment.Bottom)
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                                Text("TV Guide", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 44.dp).height(1.dp)
                .background(ZappingDividerBrush))
            Spacer(Modifier.height(18.dp))

            LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().focusGroup()) {
                val currentIdx = channels.indexOfFirst { it.id == currentChannel?.id }.coerceAtLeast(0)
                itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "zap" }) { idx, ch ->
                    ZappingCard(ch, ch.logoUrl, ch.id == currentChannel?.id,
                        if (idx == currentIdx) Modifier.focusRequester(zappingFR) else Modifier,
                        { focusedCh = ch; onIdleReset() },
                        { onSelectChannel(ch); onIdleReset() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ZappingCard(
    channel  : IptvChannel,
    logoUrl  : String,
    isCurrent: Boolean,
    modifier : Modifier,
    onFocus  : () -> Unit,
    onClick  : () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isLow  = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val isHigh = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    val scale by animateFloatAsState(
        targetValue = if (focused) (if (isHigh) 1.10f else 1.05f) else 1f,
        animationSpec = if (isLow) snap() else tween(120, easing = FastOutSlowInEasing),
        label = "zc"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else if (isCurrent) 0.55f else 0f,
        animationSpec = if (isLow) snap() else tween(120),
        label = "gla"
    )
    val dynamicGlow = if (isHigh && focused) ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.4f), 12.dp)) else ClickableSurfaceDefaults.glow(Glow.None, Glow.None)

    // Only allocate a graphicsLayer when the card is actually scaled (focused/animating).
    // Applying graphicsLayer unconditionally to 500+ cards in a LazyRow creates GPU layer
    // overhead on every frame even when all cards are at scale 1.0.
    val zapCardMod = if (!isLow && scale != 1f)
        modifier.width(120.dp).height(82.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    else
        modifier.width(120.dp).height(82.dp)
    Box(zapCardMod) {
        if (glowAlpha > 0f && !isLow) { // הסרת השכבה השקופה כליל במכשירי LOW
            Box(Modifier.matchParentSize().padding(4.dp).clip(RoundedCornerShape(14.dp))
                .background(if (focused) ACCENT.copy(glowAlpha * 0.35f) else ACCENT.copy(glowAlpha * 0.25f)))
        }
        Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor        = if (isCurrent) ACCENT.copy(if (isLow) 0.4f else 0.18f) else WHITE.copy(0.07f),
                focusedContainerColor = Color(0xFF1A1A2E),
                contentColor = WHITE, focusedContentColor = WHITE),
            border = ClickableSurfaceDefaults.border(
                border = if (isCurrent) Border(border = BorderStroke(1.5.dp, ACCENT.copy(0.7f)),
                    shape = RoundedCornerShape(12.dp)) else Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, ACCENT), shape = RoundedCornerShape(12.dp))),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            glow   = dynamicGlow,
            modifier = Modifier.fillMaxSize()
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                ChannelLogoImage(channel, logoUrl, 48.dp, focused)
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(0.45f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("${channel.number}", color = WHITE.copy(0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (!isCurrent || focused) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(ZappingGradientNorm).padding(horizontal = 7.dp, vertical = 5.dp)) {
                        Text(channel.name, color = WHITE, fontSize = 10.sp,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (isCurrent) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp)
                    .background(RED, CircleShape))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SIDE GROUP MENU
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SideGroupMenu(
    groups       : List<String>,
    selectedGroup: String,
    sideFR       : FocusRequester,
    onSelectGroup: (String) -> Unit,
    onDismiss    : () -> Unit,
    onIdleReset  : () -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isSelectedPresent = selectedGroup in groups

    Box(
        Modifier.width(278.dp).fillMaxHeight().background(HUD_BG)
            .focusGroup()
            .focusProperties {
                exit = { dir ->
                    if (dir == if (isRtl) FocusDirection.Left else FocusDirection.Right) {
                        onDismiss(); FocusRequester.Cancel
                    } else FocusRequester.Cancel
                }
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onDismiss(); true }
                    else -> false
                }
            }
    ) {
        LazyColumn(contentPadding = PaddingValues(top = 42.dp, bottom = 42.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxSize().focusGroup()) {
            item {
                Text("CATEGORIES", color = MUTED.copy(0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, modifier = Modifier.padding(start = 12.dp, bottom = 5.dp))
            }
            itemsIndexed(groups, key = { _, g -> g }) { idx, group ->
                val isSel = group == selectedGroup
                Surface(onClick = { onSelectGroup(group) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (isSel) ACCENT.copy(0.2f) else Color.Transparent,
                        focusedContainerColor = ACCENT,
                        contentColor          = if (isSel) ACCENT2 else MUTED,
                        focusedContentColor   = WHITE),
                    border = ClickableSurfaceDefaults.border(
                        border = if (isSel) Border(border = BorderStroke(1.dp, ACCENT.copy(0.45f)),
                            shape = RoundedCornerShape(10.dp)) else Border.None,
                        focusedBorder = Border.None),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .let { m -> if (isSel || (idx == 0 && !isSelectedPresent)) m.focusRequester(sideFR) else m }
                        .onFocusChanged { if (it.isFocused) onIdleReset() }
                ) {
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.CenterStart) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (isSel) Box(Modifier.width(3.dp).height(13.dp)
                                .background(ACCENT2, RoundedCornerShape(2.dp)))
                            Text(group, fontSize = 14.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EPG GUIDE DIALOG
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun FullEpgGuideDialog(
    state    : IptvState,
    viewModel: IptvViewModel,
    fr       : FocusRequester,
    onDismiss: () -> Unit
) {
    val tf         = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val refreshFR  = remember { FocusRequester() }
    val programsFR = remember { FocusRequester() }

    val epgChannels = remember(state.epgData, state.filteredChannels) {
        if (state.epgData.isEmpty()) state.filteredChannels
        else state.filteredChannels.filter { ch -> state.epgData[ch.id]?.isNotEmpty() == true }
            .ifEmpty { state.filteredChannels }
    }

    var selectedCh by remember { mutableStateOf(state.currentChannel ?: epgChannels.firstOrNull()) }

    LaunchedEffect(epgChannels.size) {
        if (selectedCh == null || epgChannels.none { it.id == selectedCh?.id }) {
            selectedCh = state.currentChannel?.let { oc -> epgChannels.firstOrNull { it.id == oc.id } }
                ?: epgChannels.firstOrNull()
        }
    }

    val chListState  = rememberLazyListState()
    val currentChIdx = epgChannels.indexOfFirst { it.id == selectedCh?.id }.coerceAtLeast(0)
    LaunchedEffect(currentChIdx) {
        chListState.scrollToItem((currentChIdx - 1).coerceAtLeast(0))
    }

    val sortedPrograms = remember(selectedCh, state.epgData) {
        selectedCh?.let { state.epgData[it.id] }?.sortedBy { it.startTime } ?: emptyList()
    }

    val progListState = rememberLazyListState()
    val liveProgIdx   = remember(sortedPrograms) {
        sortedPrograms.indexOfFirst { it.isLiveNow }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedCh?.id) {
        if (liveProgIdx > 0) progListState.scrollToItem((liveProgIdx - 1).coerceAtLeast(0))
    }

    Box(
        Modifier.fillMaxWidth(0.93f).fillMaxHeight(0.9f).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(26.dp)
            .dialogCard(onDismiss)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                Text("TV Guide", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(if (state.epgData.isEmpty()) "No EPG loaded" else "${state.epgData.size} channels",
                    color = MUTED, fontSize = 13.sp)
                Surface(onClick = { viewModel.onEvent(IptvEvent.RefreshEpg) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A28),
                        focusedContainerColor = ACCENT, contentColor = MUTED, focusedContentColor = WHITE),
                    modifier = Modifier.height(32.dp).focusRequester(refreshFR).focusProperties { down = fr }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(13.dp))
                        Text("Refresh EPG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ACCENT.copy(0.28f)))
            Spacer(Modifier.height(13.dp))

            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                LazyColumn(
                    state    = chListState,
                    modifier = Modifier.width(252.dp).fillMaxHeight().focusGroup()
                        .focusProperties {
                            exit = { dir ->
                                when (dir) {
                                    if (isRtl) FocusDirection.Left else FocusDirection.Right -> programsFR
                                    FocusDirection.Up -> refreshFR
                                    else -> FocusRequester.Default
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(epgChannels, key = { _, ch -> ch.id }) { idx, ch ->
                        val isSel   = ch.id == selectedCh?.id
                        val nowProg = state.epgData[ch.id]?.firstOrNull { it.isLiveNow }
                        Surface(onClick = { selectedCh = ch },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = if (isSel) ACCENT.copy(0.14f) else Color(0x0DFFFFFF),
                                focusedContainerColor = ACCENT,
                                contentColor = WHITE, focusedContentColor = WHITE),
                            border = ClickableSurfaceDefaults.border(
                                border = if (isSel) Border(border = BorderStroke(1.dp, ACCENT.copy(0.38f)),
                                    shape = RoundedCornerShape(10.dp)) else Border.None,
                                focusedBorder = Border.None),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .let { m -> if (idx == currentChIdx) m.focusRequester(fr) else m }
                                .focusProperties { if (idx == 0) up = refreshFR }
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                                    .background(SURFACE2), Alignment.Center) {
                                    ChannelLogoImage(ch, ch.logoUrl, 24.dp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(ch.name, color = WHITE, fontSize = 13.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (nowProg != null) Text(nowProg.title, color = MUTED, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(13.dp))
                    .background(SURFACE.copy(0.5f))) {
                    if (sortedPrograms.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CalendarToday, null, tint = MUTED, modifier = Modifier.size(28.dp))
                                Text(if (state.epgData.isEmpty()) "Load EPG in playlist settings" else "No data for this channel",
                                    color = MUTED, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(state = progListState, contentPadding = PaddingValues(11.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxSize().focusGroup()
                                .focusRequester(programsFR)
                                .focusProperties {
                                    exit = { dir ->
                                        if (dir == if (isRtl) FocusDirection.Right else FocusDirection.Left) fr
                                        else FocusRequester.Default
                                    }
                                }
                        ) {
                            itemsIndexed(sortedPrograms, key = { _, p -> "${p.startTime}_${p.channelId}" }) { _, p ->
                                val isLive = p.isLiveNow; val isPast = p.isPast
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                    .background(when { isLive -> ACCENT.copy(0.11f); isPast -> Color.Transparent; else -> Color(0x08FFFFFF) })
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                                ) {
                                    if (isLive) Box(Modifier.size(7.dp).background(RED, CircleShape))
                                    else Spacer(Modifier.size(7.dp))
                                    Column(Modifier.width(56.dp)) {
                                        Text(tf.format(Date(p.startTime)),
                                            color = if (isLive) ACCENT2 else if (isPast) MUTED.copy(0.5f) else WHITE,
                                            fontSize = 12.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
                                        Text("${p.durationMinutes}m", color = MUTED.copy(0.5f), fontSize = 10.sp)
                                    }
                                    Box(Modifier.width(2.dp).height(20.dp).background(if (isLive) ACCENT else MUTED2))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.title, color = if (isPast) MUTED.copy(0.5f) else WHITE,
                                            fontSize = 13.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (p.category.isNotBlank()) Text(p.category, color = MUTED.copy(0.5f), fontSize = 10.sp)
                                    }
                                    if (isLive) Column(horizontalAlignment = Alignment.End) {
                                        Text("${p.remainingMinutes}m left", color = ACCENT2, fontSize = 11.sp)
                                        Spacer(Modifier.height(3.dp))
                                        ProgBar(p.progressFraction, 70.dp)
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

// ═══════════════════════════════════════════════════════════════════════════════
// PLAYLIST MANAGER DIALOG
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun PlaylistManagerDialog(
    state    : IptvState,
    fr       : FocusRequester,
    onDismiss: () -> Unit,
    onEvent  : (IptvEvent) -> Unit
) {
    val nameFR   = fr
    val urlFR    = remember { FocusRequester() }
    val epgFR    = remember { FocusRequester() }
    val saveFR   = remember { FocusRequester() }
    val deleteFR = remember { FocusRequester() }
    val qrFR     = remember { FocusRequester() }
    val hasActive = state.playlists.any { it.isActive }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        Modifier.fillMaxWidth(0.87f).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(34.dp)
            .dialogCard(onDismiss)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                    Text("Manage Playlists", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(20.dp))
                DialogLabel("Playlist Name")
                DialogInput(state.addPlaylistName, "e.g. My IPTV", nameFR, downFR = urlFR) {
                    onEvent(IptvEvent.UpdateAddPlaylistName(it))
                }
                Spacer(Modifier.height(13.dp))
                DialogLabel("M3U / M3U8 URL *")
                DialogInput(state.addPlaylistUrl, "http://...", urlFR, upFR = nameFR, downFR = epgFR) {
                    onEvent(IptvEvent.UpdateAddPlaylistUrl(it))
                }
                Spacer(Modifier.height(13.dp))
                DialogLabel("EPG XML URL  (optional)")
                DialogInput(state.addPlaylistEpgUrl, "http://.../epg.xml.gz", epgFR, upFR = urlFR, downFR = saveFR) {
                    onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it))
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Surface(
                        onClick = {
                            if (state.addPlaylistUrl.isNotBlank()) {
                                onEvent(IptvEvent.ConfirmAddPlaylist)
                                if (state.addPlaylistEpgUrl.isNotBlank()) onEvent(IptvEvent.RefreshEpg)
                            }
                        },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = ACCENT,
                            focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                        modifier = Modifier.weight(1f).height(52.dp).focusRequester(saveFR)
                            .focusProperties {
                                up = epgFR
                                right = if (isRtl) FocusRequester.Default else if (hasActive) deleteFR else qrFR
                                left  = if (isRtl) if (hasActive) deleteFR else qrFR else FocusRequester.Default
                            }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Text("Save & Connect", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    state.playlists.firstOrNull { it.isActive }?.let { activePl ->
                        Surface(
                            onClick = { onEvent(IptvEvent.DeletePlaylist(activePl.id)) },
                            shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors  = ClickableSurfaceDefaults.colors(containerColor = RED.copy(0.14f),
                                focusedContainerColor = RED, contentColor = RED, focusedContentColor = WHITE),
                            modifier = Modifier.height(52.dp).focusRequester(deleteFR)
                                .focusProperties {
                                    up    = epgFR
                                    left  = if (isRtl) qrFR else saveFR
                                    right = if (isRtl) saveFR else qrFR
                                }
                        ) {
                            Box(Modifier.padding(horizontal = 17.dp).fillMaxHeight(), Alignment.Center) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                                    Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                state.playlists.firstOrNull { it.isActive }?.let { pl ->
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(GREEN.copy(0.08f)).padding(11.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = GREEN, modifier = Modifier.size(14.dp))
                            Column {
                                Text("Active: ${pl.name}", color = GREEN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${pl.channelCount} channels loaded", color = GREEN.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Column(Modifier.width(225.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Send from Phone", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Scan QR or type URL in your phone browser to send a playlist to your TV",
                    color = MUTED, fontSize = 11.sp, textAlign = TextAlign.Center)
                if (state.localIpAddress.isNotBlank()) {
                    val qrUrl    = "http://${state.localIpAddress}:8080"
                    val qrBitmap = remember(qrUrl) { QrCodeGenerator.generate(qrUrl, 320) }
                    Box(Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(WHITE).padding(8.dp)) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR",
                            modifier = Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(SURFACE2).padding(7.dp), Alignment.Center) {
                        Text(qrUrl, color = ACCENT2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(SURFACE2), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Default.Wifi, null, tint = MUTED, modifier = Modifier.size(28.dp))
                            Text("Not on WiFi", color = MUTED, fontSize = 12.sp)
                        }
                    }
                }
                Surface(onClick = onDismiss,
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
                        focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                    modifier = Modifier.fillMaxWidth().height(40.dp).focusRequester(qrFR)
                        .focusProperties {
                            left  = if (isRtl) FocusRequester.Default else saveFR
                            right = if (isRtl) saveFR else FocusRequester.Default
                        }
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PLAYER QUICK SETTINGS (ASPECT RATIO, AUDIO, SUBS)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun PlayerQuickSettings(
    exo        : ExoPlayerWrapper,
    currTracks : androidx.media3.common.Tracks,
    arMode     : Int,
    subtitlesOn: Boolean,
    settingsFR : FocusRequester,
    onArChange : (Int) -> Unit,
    onSubtitles: () -> Unit,
    onDismiss  : () -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val subtitleTracks = remember(currTracks) {
        buildList {
            currTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { grp ->
                for (i in 0 until grp.length) {
                    val fmt  = grp.mediaTrackGroup.getFormat(i)
                    val lang = fmt.language?.uppercase() ?: "Sub ${i + 1}"
                    val role = when {
                        fmt.roleFlags and 0x00000004 != 0 -> " · Forced"
                        fmt.roleFlags and 0x00000008 != 0 -> " · CC"
                        else -> ""
                    }
                    add(Triple("$lang$role", grp, i))
                }
            }
        }
    }

    val audioTracks = remember(currTracks) {
        buildList {
            currTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { grp ->
                for (i in 0 until grp.length) {
                    val fmt  = grp.mediaTrackGroup.getFormat(i)
                    val isSupported = grp.isTrackSupported(i)
                    val lang = fmt.language?.uppercase() ?: "Track ${i + 1}"
                    val ch   = if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""
                    val atm  = if (fmt.sampleMimeType == "audio/eac3-joc") " · Atmos" else ""
                    val suppStr = if (!isSupported) " (Unsupported)" else ""
                    add(Triple("$lang$ch$atm$suppStr", grp, i) to isSupported)
                }
            }
        }
    }

    val arModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT          to "Fit",
            AspectRatioFrameLayout.RESIZE_MODE_FILL         to "Fill",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM         to "Zoom",
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH  to "Fixed W",
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fixed H"
        )
    }

    Box(
        Modifier.width(300.dp).fillMaxHeight()
            .background(if (isRtl) PQSBgBrushRtl else PQSBgBrushLtr)
            .focusGroup()
            .focusProperties {
                exit = { dir ->
                    if (dir == if (isRtl) FocusDirection.Right else FocusDirection.Left) {
                        onDismiss(); FocusRequester.Cancel
                    } else FocusRequester.Cancel
                }
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onDismiss(); true }
                    else -> false
                }
            }
    ) {
        Column(
            Modifier
                .align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(start = if (isRtl) 32.dp else 16.dp, end = if (isRtl) 16.dp else 32.dp, top = 40.dp, bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp)) {
                Box(Modifier.width(3.dp).height(20.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                Text("Player Settings", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }

            Text("ASPECT RATIO", color = MUTED.copy(0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                arModes.forEachIndexed { idx, (mode, label) ->
                    val isSel = mode == arMode
                    Surface(
                        onClick = { onArChange(mode) },
                        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors  = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSel) ACCENT.copy(0.25f) else WHITE.copy(0.07f),
                            focusedContainerColor = ACCENT,
                            contentColor          = if (isSel) ACCENT2 else WHITE.copy(0.7f),
                            focusedContentColor   = WHITE),
                        border  = ClickableSurfaceDefaults.border(
                            border = if (isSel) Border(border = BorderStroke(1.dp, ACCENT.copy(0.6f)),
                                shape = RoundedCornerShape(8.dp)) else Border.None,
                            focusedBorder = Border.None),
                        modifier = Modifier.height(34.dp)
                            .let { m -> if (idx == 0) m.focusRequester(settingsFR) else m }
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp).fillMaxHeight(), Alignment.Center) {
                            Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("SUBTITLES", color = MUTED.copy(0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 2.dp))
            if (subtitleTracks.isNotEmpty()) {
                val subOffSel = !subtitlesOn
                Surface(
                    onClick = {
                        val params = exo.player.trackSelectionParameters.buildUpon()
                        params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        exo.player.trackSelectionParameters = params.build()
                        if (subtitlesOn) onSubtitles()
                    },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (subOffSel) WHITE.copy(0.12f) else WHITE.copy(0.05f),
                        focusedContainerColor = ACCENT,
                        contentColor = WHITE, focusedContentColor = WHITE),
                    border   = ClickableSurfaceDefaults.border(
                        border = if (subOffSel) Border(border = BorderStroke(1.dp, WHITE.copy(0.3f)),
                            shape = RoundedCornerShape(10.dp)) else Border.None,
                        focusedBorder = Border.None),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SubtitlesOff, null, Modifier.size(17.dp),
                                tint = if (subOffSel) WHITE else MUTED)
                            Text("Off", fontSize = 13.sp, fontWeight = if (subOffSel) FontWeight.Bold else FontWeight.Normal)
                        }
                        if (subOffSel) Icon(Icons.Default.Check, null, tint = WHITE.copy(0.6f), modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                subtitleTracks.forEach { (label, grp, trackIdx) ->
                    val isSel = subtitlesOn && grp.isTrackSelected(trackIdx)
                    Surface(
                        onClick = {
                            val params = exo.player.trackSelectionParameters.buildUpon()
                            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            params.setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, trackIdx))
                            exo.player.trackSelectionParameters = params.build()
                            if (!subtitlesOn) onSubtitles()
                        },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSel) ACCENT.copy(0.18f) else WHITE.copy(0.06f),
                            focusedContainerColor = ACCENT,
                            contentColor = WHITE, focusedContentColor = WHITE),
                        border   = ClickableSurfaceDefaults.border(
                            border = if (isSel) Border(border = BorderStroke(1.dp, ACCENT.copy(0.5f)),
                                shape = RoundedCornerShape(10.dp)) else Border.None,
                            focusedBorder = Border.None),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Subtitles, null, Modifier.size(17.dp),
                                    tint = if (isSel) ACCENT2 else MUTED)
                                Text(label, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (isSel) Icon(Icons.Default.Check, null, tint = ACCENT2, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                Surface(
                    onClick  = onSubtitles,
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (subtitlesOn) ACCENT.copy(0.18f) else WHITE.copy(0.06f),
                        focusedContainerColor = ACCENT,
                        contentColor = WHITE, focusedContentColor = WHITE),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, null, Modifier.size(17.dp),
                                tint = if (subtitlesOn) ACCENT2 else MUTED)
                            Text(if (subtitlesOn) "Subtitles ON" else "No subs in stream", fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (audioTracks.isNotEmpty()) {
                Text("AUDIO / LANGUAGE", color = MUTED.copy(0.55f), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 2.dp))
                audioTracks.forEach { (info, isSupported) ->
                    val (label, grp, trackIdx) = info
                    val isSel = grp.isTrackSelected(trackIdx)
                    Surface(
                        onClick = {
                            if (isSupported) {
                                val params = exo.player.trackSelectionParameters.buildUpon()
                                params.setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, trackIdx))
                                exo.player.trackSelectionParameters = params.build()

                                if (exo.player.isCurrentMediaItemLive) {
                                    exo.player.seekToDefaultPosition()
                                } else {
                                    exo.player.seekTo(exo.player.currentPosition)
                                }
                            }
                        },
                        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors  = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSel) ACCENT.copy(0.18f) else WHITE.copy(0.06f),
                            focusedContainerColor = if (isSupported) ACCENT else RED.copy(0.8f),
                            contentColor = if (isSupported) WHITE else MUTED.copy(0.4f),
                            focusedContentColor = WHITE),
                        border  = ClickableSurfaceDefaults.border(
                            border = if (isSel) Border(border = BorderStroke(1.dp, ACCENT.copy(0.5f)),
                                shape = RoundedCornerShape(10.dp)) else Border.None,
                            focusedBorder = Border.None),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, null, Modifier.size(17.dp),
                                    tint = if (isSel) ACCENT2 else if (!isSupported) MUTED.copy(0.4f) else MUTED)
                                Text(label, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (isSel) Icon(Icons.Default.Check, null, tint = ACCENT2, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                HintPill(if (isRtl) "→" else "←", "Close")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOBAL SETTINGS DIALOG
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SmartSettingsDialog(
    state        : IptvState,
    exo          : ExoPlayerWrapper,
    currentTracks: androidx.media3.common.Tracks,
    fr           : FocusRequester,
    onDismiss    : () -> Unit,
    onEvent      : (IptvEvent) -> Unit
) {
    val subtitlesFR = fr
    val editFR      = remember { FocusRequester() }
    val closeFR     = remember { FocusRequester() }

    val allAudioTracks = remember(currentTracks) {
        buildList {
            currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { grp ->
                for (i in 0 until grp.length) {
                    val fmt   = grp.mediaTrackGroup.getFormat(i)
                    val isSupported = grp.isTrackSupported(i)
                    val lang  = fmt.language?.uppercase() ?: "Track ${i + 1}"
                    val ch    = if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""
                    val atmos = if (fmt.sampleMimeType == "audio/eac3-joc") " · ATMOS" else ""
                    val suppStr = if (!isSupported) " (Unsupported)" else ""
                    add(Triple("$lang$ch$atmos$suppStr", grp, i) to isSupported)
                }
            }
        }
    }
    val audioFRs = remember(allAudioTracks.size) {
        List(allAudioTracks.size.coerceAtLeast(1)) { FocusRequester() }
    }

    Box(
        Modifier.width(455.dp).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(30.dp)
            .dialogCard(onDismiss)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                Text("Playback Settings", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))

            SettingsTile("Subtitles / CC", if (state.subtitlesEnabled) "Enabled" else "Disabled",
                Icons.Default.Subtitles, state.subtitlesEnabled,
                Modifier.focusRequester(subtitlesFR)
                    .focusProperties { down = audioFRs.getOrElse(0) { editFR }; up = closeFR }
            ) { onEvent(IptvEvent.ToggleSubtitles) }

            Spacer(Modifier.height(5.dp))

            if (allAudioTracks.isEmpty()) {
                SettingsTile("Audio Track", "No audio tracks detected", Icons.Default.VolumeUp, false,
                    Modifier.focusRequester(audioFRs[0])
                        .focusProperties { up = subtitlesFR; down = editFR }) {}
            } else {
                Text("AUDIO TRACKS", color = MUTED.copy(0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp, modifier = Modifier.padding(vertical = 3.dp, horizontal = 3.dp))
                allAudioTracks.forEachIndexed { idx, pair ->
                    val (info, isSupported) = pair
                    val (label, grp, trackIdx) = info
                    val isSel   = grp.isTrackSelected(trackIdx)
                    val prevFR  = if (idx == 0) subtitlesFR else audioFRs[idx - 1]
                    val nextFR  = audioFRs.getOrElse(idx + 1) { editFR }
                    Surface(
                        onClick = {
                            if (isSupported) {
                                val params = exo.player.trackSelectionParameters.buildUpon()
                                params.setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, trackIdx))
                                exo.player.trackSelectionParameters = params.build()

                                if (exo.player.isCurrentMediaItemLive) {
                                    exo.player.seekToDefaultPosition()
                                } else {
                                    exo.player.seekTo(exo.player.currentPosition)
                                }
                            }
                        },
                        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors  = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSel) ACCENT.copy(0.13f) else Color(0x0DFFFFFF),
                            focusedContainerColor = if (isSupported) ACCENT else RED.copy(0.8f),
                            contentColor = if (isSupported) WHITE else MUTED.copy(0.4f),
                            focusedContentColor = WHITE),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                            .focusRequester(audioFRs[idx])
                            .focusProperties { up = prevFR; down = nextFR }
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, null, Modifier.size(17.dp),
                                    tint = if (isSel) ACCENT2 else if (!isSupported) MUTED.copy(0.4f) else MUTED)
                                Text(label, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (isSel) Icon(Icons.Default.Check, null, tint = ACCENT2, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (idx < allAudioTracks.size - 1) Spacer(Modifier.height(3.dp))
                }
            }

            Spacer(Modifier.height(5.dp))
            SettingsTile("Edit Playlist URL",
                state.playlists.firstOrNull { it.isActive }?.name ?: "No playlist",
                Icons.Default.Edit, false,
                Modifier.focusRequester(editFR)
                    .focusProperties { up = audioFRs.lastOrNull() ?: subtitlesFR; down = closeFR }
            ) { onEvent(IptvEvent.ShowAddPlaylist) }

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick  = onDismiss,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color(0x22FFFFFF),
                    focusedContainerColor = WHITE,
                    contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .focusRequester(closeFR)
                    .focusProperties { up = editFR; down = subtitlesFR }
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Close", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsTile(
    title    : String,
    subtitle : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    isActive : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = if (isActive) ACCENT.copy(0.12f) else Color(0x0DFFFFFF),
            focusedContainerColor = ACCENT,
            contentColor = WHITE, focusedContentColor = WHITE),
        modifier = modifier.fillMaxWidth().height(54.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (isActive) ACCENT2 else MUTED)
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(subtitle, fontSize = 12.sp, color = if (isActive) ACCENT2.copy(0.7f) else MUTED)
                }
            }
            if (isActive) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ACCENT.copy(0.28f))
                .padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("ON", color = ACCENT2, fontSize = 11.sp, fontWeight = FontWeight.Black)
            } else Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MUTED)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CHANNEL QR DIALOG
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChannelQrDialog(channel: IptvChannel, fr: FocusRequester, onDismiss: () -> Unit) {
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 320) }
    Box(Modifier.width(355.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0F0F18))
        .padding(26.dp).dialogCard(onDismiss)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(channel.name, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Stream QR Code", color = MUTED, fontSize = 13.sp)
            Box(Modifier.size(185.dp).clip(RoundedCornerShape(12.dp)).background(WHITE).padding(8.dp)) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Text(channel.streamUrl.take(60) + if (channel.streamUrl.length > 60) "…" else "",
                color = MUTED, fontSize = 10.sp, textAlign = TextAlign.Center)
            Surface(onClick = onDismiss,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF),
                    focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                modifier = Modifier.fillMaxWidth().height(42.dp).focusRequester(fr)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun IptvEmptyState(onAddClick: () -> Unit, onSettingsClick: () -> Unit, emptyStateFR: FocusRequester) {
    val settingsFR = remember { FocusRequester() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(74.dp).clip(CircleShape).background(ACCENT.copy(0.1f)), Alignment.Center) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(32.dp), tint = ACCENT2)
        }
        Spacer(Modifier.height(17.dp))
        Text("No Channels Loaded", color = WHITE, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Add an M3U playlist to start watching.\nScan the QR code for easy phone setup.",
            color = MUTED, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(onClick = onAddClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = ACCENT,
                    focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier.height(50.dp).focusRequester(emptyStateFR)
                    .focusProperties { if (isRtl) left = settingsFR else right = settingsFR }
            ) {
                Row(Modifier.padding(horizontal = 22.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                    Text("Add Playlist", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(onClick = onSettingsClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
                    focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
                modifier = Modifier.height(50.dp).focusRequester(settingsFR)
                    .focusProperties { if (isRtl) right = emptyStateFR else left = emptyStateFR }
            ) {
                Row(Modifier.padding(horizontal = 22.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Settings, null, Modifier.size(17.dp))
                    Text("Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOG INPUT
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DialogInput(
    value         : String,
    hint          : String,
    focusRequester: FocusRequester? = null,
    upFR          : FocusRequester? = null,
    downFR        : FocusRequester? = null,
    onValueChange : (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value = value, onValueChange = onValueChange,
        textStyle = TextStyle(color = WHITE, fontSize = 14.sp),
        cursorBrush = SolidColor(ACCENT2),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) SURFACE2 else SURFACE)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) ACCENT.copy(0.7f) else MUTED2, RoundedCornerShape(12.dp))
                .padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = MUTED.copy(0.38f), fontSize = 14.sp)
                    inner()
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .let { m ->
                if (upFR != null || downFR != null)
                    m.focusProperties { if (upFR != null) up = upFR; if (downFR != null) down = downFR }
                else m
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp   -> { upFR?.let   { runCatching { it.requestFocus() } }; upFR   != null }
                    Key.DirectionDown -> { downFR?.let { runCatching { it.requestFocus() } }; downFR != null }
                    else -> false
                }
            }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// STREAM TRACKS BADGES
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun StreamTracksBadges(currTracks: androidx.media3.common.Tracks) {
    val audioLangs = remember(currTracks) {
        currTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { grp -> (0 until grp.length).mapNotNull { grp.mediaTrackGroup.getFormat(it).language?.uppercase() } }
            .filter { it.length <= 4 }.toSet().toList()
    }
    val subLangs = remember(currTracks) {
        currTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { grp -> (0 until grp.length).mapNotNull { grp.mediaTrackGroup.getFormat(it).language?.uppercase() } }
            .filter { it.length <= 4 }.toSet().toList()
    }
    if (audioLangs.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.VolumeUp, null, tint = ACCENT2, modifier = Modifier.size(13.dp))
            Text(audioLangs.joinToString(", "), color = WHITE.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (subLangs.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Subtitles, null, tint = ACCENT2, modifier = Modifier.size(13.dp))
            Text(subLangs.joinToString(", "), color = WHITE.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SMALL SHARED COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════
@Composable private fun HintPill(key: String, label: String) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(WHITE.copy(0.08f))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(WHITE.copy(0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(key, color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = WHITE.copy(0.5f), fontSize = 10.sp)
    }
}

@Composable private fun LiveBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(RED).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(5.dp).background(WHITE, CircleShape))
            Text("LIVE", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable private fun ResBadge(res: String) {
    val color = when {
        res.contains("4K", true) || res.contains("UHD", true) -> Color(0xFFFF6B35)
        res.contains("FHD", true) || res.contains("1080", true) -> ACCENT
        else -> MUTED
    }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.18f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(res, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ProgBar(fraction: Float, width: Dp) {
    Box(Modifier.width(width).height(3.dp).clip(CircleShape).background(WHITE.copy(0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
            .background(ProgBarBrush))
    }
}

@Composable private fun DialogLabel(text: String) {
    Text(text, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ChannelLogoImage(
    channel  : IptvChannel,
    logoUrl  : String,
    size     : Dp,
    isFocused: Boolean = false
) {
    val ctx      = LocalContext.current
    val isLow    = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val initials = channel.name.take(2).uppercase()
    if (logoUrl.isNotBlank()) {
        var hasError by remember(logoUrl) { mutableStateOf(false) }
        if (hasError) {
            Text(initials, color = if (isFocused) BG else WHITE,
                fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black)
        } else {
            AsyncImage(
                model = remember(logoUrl) {
                    ImageRequest.Builder(ctx).data(logoUrl)
                        .apply {
                            // Memory cache MUST be enabled on all devices — disabling it causes
                            // every logo to be re-decoded from disk on every scroll recomposition,
                            // which is the primary source of lag on Mecool/low-end devices.
                            memoryCachePolicy(CachePolicy.ENABLED)
                            diskCachePolicy(CachePolicy.ENABLED)
                            // Use a smaller decode size on LOW to reduce memory pressure,
                            // but never disable the memory cache.
                            if (isLow) size(192) else size(512)
                        }
                        .allowHardware(true)
                        .crossfade(false)
                        .build()
                },
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(size),
                onError            = { hasError = true }
            )
        }
    } else {
        Text(initials, color = if (isFocused) BG else WHITE,
            fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black)
    }
}