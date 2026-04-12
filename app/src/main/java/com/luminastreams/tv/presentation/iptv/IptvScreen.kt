@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Text
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    var isFullScreen       by remember { mutableStateOf(false) }
    var showToast          by remember { mutableStateOf("") }
    var showZapping        by remember { mutableStateOf(false) }
    var showSideMenu       by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var arMode             by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var lastAction         by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

    fun toast(msg: String) { showToast = msg; scope.launch { delay(2500); showToast = "" } }
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
        AnimatedVisibility(!isFullScreen, enter = fadeIn(tween(280)), exit = fadeOut(tween(280))) {
            Column(Modifier.fillMaxSize()) {
                TopNavBar(
                    channelsCount = state.channels.size,
                    hasEpg        = state.epgData.isNotEmpty(),
                    topBarFR      = topBarFR, epgBtnFR  = epgBtnFR,
                    addBtnFR      = addBtnFR, setgBtnFR = setgBtnFR,
                    gridFR        = gridFR,
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

        if (isFullScreen) {
            var surfaceReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                snapshotFlow { surfaceReady }.first { it }
                state.currentChannel?.let { channel ->
                    exo.prepareStream(channel.streamUrl)
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

                AnimatedVisibility(
                    visible  = (showZapping || showSideMenu) && !isAnyDialogOpen,
                    enter    = fadeIn(tween(260)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it },
                    exit     = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
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

                AnimatedVisibility(
                    visible  = showZapping && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it }),
                    exit     = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it }),
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

                AnimatedVisibility(
                    visible  = showSideMenu && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(180)),
                    exit     = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(30f)
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

                AnimatedVisibility(
                    visible  = showPlayerSettings,
                    enter    = slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200)),
                    exit     = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.CenterEnd).zIndex(35f)
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

        state.qrCodeChannel?.let { channel ->
            if (state.showQrCode) {
                IptvDialog({ viewModel.onEvent(IptvEvent.HideQrCode) }) { fr, _ ->
                    ChannelQrDialog(channel, fr) { viewModel.onEvent(IptvEvent.HideQrCode) }
                }
            }
        }

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
            enter    = fadeIn() + slideInVertically { it },
            exit     = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).zIndex(100f)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(WHITE.copy(0.14f))
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(showToast, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}