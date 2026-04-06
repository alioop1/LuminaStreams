
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.iptv

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════
//  IPTV PALETTE
// ══════════════════════════════════════════════════════════════════
private val IPTV_BG         = Color(0xFF08080F)
private val IPTV_SURFACE    = Color(0xFF111120)
private val IPTV_CARD       = Color(0xFF1A1A2E)
private val IPTV_CARD_FOCUS = Color(0xFF252540)
private val IPTV_RED        = Color(0xFFF02050)
private val IPTV_LIVE       = Color(0xFFFF2050)
private val IPTV_BLUE       = Color(0xFF0A84FF)
private val IPTV_GREEN      = Color(0xFF30D158)
private val IPTV_GOLD       = Color(0xFFFFCC02)
private val IPTV_PURPLE     = Color(0xFF9B5DE5)
private val IPTV_WHITE      = Color(0xFFFFFFFF)
private val IPTV_DIM        = Color(0xB3FFFFFF)
private val IPTV_MUTED      = Color(0x66FFFFFF)
private val IPTV_DIVIDER    = Color(0x14FFFFFF)

// ══════════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvScreen(
    viewModel: IptvViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val exo = remember { ExoPlayerWrapper(context) }
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()

    var isFullScreen by remember { mutableStateOf(false) }

    // Focus requesters
    val backFR = remember { FocusRequester() }
    val channelListFR = remember { FocusRequester() }
    val addPlaylistFR = remember { FocusRequester() }
    val fullScreenFR = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { backFR.requestFocus() }
    }

    BackHandler {
        when {
            state.showSettings       -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showParentalPinEntry -> viewModel.onEvent(IptvEvent.DismissParentalPin)
            state.showSleepTimerPicker -> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            state.showMultiView      -> viewModel.onEvent(IptvEvent.ToggleMultiView)
            state.showQrCode         -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showAddPlaylist    -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showEpgGuide       -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            isFullScreen             -> isFullScreen = false
            else                     -> onNavigateBack()
        }
    }

    LaunchedEffect(state.currentChannel) {
        state.currentChannel?.let { ch ->
            exo.prepareStream(ch.streamUrl)
            exo.play()
        } ?: run {
            exo.pause()
        }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            delay(120)
            runCatching { fullScreenFR.requestFocus() }
        } else {
            delay(120)
            runCatching { channelListFR.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Video background
        if (state.currentChannel != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().focusable(false),
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val sv = SurfaceView(ctx)
                        sv.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        sv.keepScreenOn = true
                        sv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                exo.player.setVideoSurfaceView(sv)
                            }
                            override fun onViewDetachedFromWindow(v: android.view.View) {
                                exo.player.clearVideoSurface()
                            }
                        })
                        addView(sv)
                    }
                },
                update = { layout ->
                    if (videoAspectRatio > 0f) layout.setAspectRatio(videoAspectRatio)
                }
            )
        }

        // ── Main UI (hidden in fullscreen)
        AnimatedVisibility(
            visible = !isFullScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val bgAlpha = if (state.currentChannel != null) 0.9f else 1f
            Column(
                Modifier.fillMaxSize().background(IPTV_BG.copy(alpha = bgAlpha))
            ) {
                IptvTopBar(
                    state = state,
                    backFR = backFR,
                    onBack = onNavigateBack,
                    onEvent = viewModel::onEvent
                )

                if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                    IptvEmptyState(
                        loadState = state.loadState,
                        addPlaylistFR = addPlaylistFR,
                        onEvent = viewModel::onEvent
                    )
                } else {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left panel: channel list
                        Column(
                            Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(IPTV_SURFACE)
                                .padding(vertical = 8.dp)
                        ) {
                            GroupTabRow(
                                groups = state.groups,
                                selectedGroup = state.selectedGroup,
                                onSelectGroup = { viewModel.onEvent(IptvEvent.SelectGroup(it)) }
                            )
                            IptvSearchBox(
                                query = state.searchQuery,
                                focusManager = focusManager,
                                onQueryChange = { viewModel.onEvent(IptvEvent.UpdateSearch(it)) }
                            )
                            // Sort & grid toggle toolbar
                            ChannelToolbar(
                                sortMode = state.channelSortMode,
                                isGridView = state.showChannelGrid,
                                onSort = { viewModel.onEvent(IptvEvent.SetChannelSort(it)) },
                                onToggleGrid = { viewModel.onEvent(IptvEvent.ToggleChannelGrid) }
                            )
                            if (state.showChannelGrid) {
                                ChannelGrid(
                                    channels = state.filteredChannels,
                                    currentChannel = state.currentChannel,
                                    channelListFR = channelListFR,
                                    onSelectChannel = { ch ->
                                        viewModel.onEvent(IptvEvent.SelectChannel(ch))
                                        isFullScreen = true
                                    }
                                )
                            } else {
                                ChannelList(
                                    channels = state.filteredChannels,
                                    currentChannel = state.currentChannel,
                                    favorites = state.favoriteChannelIds,
                                    epgData = state.epgData,
                                    channelLogos = state.channelLogos,
                                    channelListFR = channelListFR,
                                    onSelectChannel = { ch ->
                                        if (state.currentChannel?.id == ch.id) {
                                            isFullScreen = true
                                        } else {
                                            viewModel.onEvent(IptvEvent.SelectChannel(ch))
                                            isFullScreen = true
                                        }
                                    },
                                    onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(it)) },
                                    onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(it)) }
                                )
                            }
                        }

                        // Right panel: now playing + EPG
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (state.currentChannel != null) {
                                NowPlayingPanel(
                                    state = state,
                                    onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(state.currentChannel!!.id)) },
                                    onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(state.currentChannel!!)) },
                                    onShowEpg = { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                                    onShowFullScreen = { isFullScreen = true },
                                    onSleepTimer = { viewModel.onEvent(IptvEvent.ShowSleepTimerPicker) },
                                    onToggleRecording = { viewModel.onEvent(IptvEvent.ToggleRecording) },
                                    onSettings = { viewModel.onEvent(IptvEvent.ShowIptvSettings) }
                                )
                                Spacer(Modifier.height(12.dp))
                                val epgPrograms = remember(state.epgData, state.currentChannel?.id, state.epgDayOffset) {
                                    viewModel.getEpgForChannel(state.currentChannel!!, state.epgData)
                                }
                                EpgTimeline(
                                    programs = epgPrograms,
                                    epgLoadState = state.epgLoadState,
                                    dayOffset = state.epgDayOffset,
                                    onDayChange = { viewModel.onEvent(IptvEvent.SetEpgDayOffset(it)) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                IptvWelcomePanel(
                                    channelCount = state.channels.size,
                                    playlistName = state.playlists.firstOrNull { it.isActive }?.name ?: "",
                                    epgStatus = state.epgLoadState,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Sleep timer indicator
                            if (state.sleepTimer != SleepTimer.OFF && state.sleepTimerRemainingMs > 0) {
                                SleepTimerIndicator(
                                    remainingMs = state.sleepTimerRemainingMs,
                                    onDismiss = { viewModel.onEvent(IptvEvent.DismissSleepTimer) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Fullscreen player overlay
        if (isFullScreen) {
            FullscreenOverlay(
                state = state,
                fullScreenFR = fullScreenFR,
                onChannelUp = {
                    val idx = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }
                    viewModel.onEvent(IptvEvent.ChannelUp(idx))
                },
                onChannelDown = {
                    val idx = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }
                    viewModel.onEvent(IptvEvent.ChannelDown(idx))
                },
                onExit = { isFullScreen = false }
            )
        }

        // ══ DIALOGS ══════════════════════════════════════════════

        // Loading dialog
        if (state.loadState is IptvLoadState.Loading) {
            IptvDialog(onDismiss = { }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        com.luminastreams.tv.ui.components.LoadingIndicator()
                        Text("Loading playlist...", color = IPTV_DIM, fontSize = 16.sp)
                    }
                }
            }
        }

        // Add playlist dialog
        if (state.showAddPlaylist) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.HideAddPlaylist) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    AddPlaylistDialog(
                        state = state,
                        focusRequester = addPlaylistFR,
                        focusManager = focusManager,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }

        // QR code dialog
        if (state.showQrCode && state.qrCodeChannel != null) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.HideQrCode) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    state.qrCodeChannel?.let { ch ->
                        QrCodeDialog(channel = ch, onDismiss = { viewModel.onEvent(IptvEvent.HideQrCode) })
                    }
                }
            }
        }

        // EPG full guide dialog
        if (state.showEpgGuide && state.currentChannel != null) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.HideEpgGuide) }) {
                state.currentChannel?.let { ch ->
                    val fullGuidePrograms = remember(state.epgData, ch.id) {
                        viewModel.getEpgForChannel(ch, state.epgData)
                    }
                    EpgFullGuide(
                        channel = ch,
                        programs = fullGuidePrograms,
                        dayOffset = state.epgDayOffset,
                        onDayChange = { viewModel.onEvent(IptvEvent.SetEpgDayOffset(it)) },
                        onDismiss = { viewModel.onEvent(IptvEvent.HideEpgGuide) }
                    )
                }
            }
        }

        // Sleep timer picker dialog
        if (state.showSleepTimerPicker) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.HideSleepTimerPicker) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    SleepTimerPickerDialog(
                        currentTimer = state.sleepTimer,
                        onSelect = { viewModel.onEvent(IptvEvent.SetSleepTimer(it)) },
                        onDismiss = { viewModel.onEvent(IptvEvent.HideSleepTimerPicker) }
                    )
                }
            }
        }

        // Parental pin dialog
        if (state.showParentalPinEntry) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.DismissParentalPin) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ParentalPinDialog(
                        onConfirm = { pin -> viewModel.onEvent(IptvEvent.EnterParentalPin(pin)) },
                        onDismiss = { viewModel.onEvent(IptvEvent.DismissParentalPin) }
                    )
                }
            }
        }

        // IPTV Settings dialog
        if (state.showSettings) {
            IptvDialog(onDismiss = { viewModel.onEvent(IptvEvent.HideIptvSettings) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    IptvSettingsDialog(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onDismiss = { viewModel.onEvent(IptvEvent.HideIptvSettings) }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  DIALOG WRAPPER
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.88f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .focusGroup()
                .focusRestorer()
                .focusProperties { exit = { FocusRequester.Cancel } }
        ) {
            content()
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  TOP BAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvTopBar(
    state: IptvState,
    backFR: FocusRequester,
    onBack: () -> Unit,
    onEvent: (IptvEvent) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(IPTV_SURFACE.copy(0.95f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Back button
        IptvCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier.focusRequester(backFR),
            tint = IPTV_DIM
        )

        // Live dot + title
        LiveDot()
        Text("Lumina Live TV", color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)

        // Channel count
        if (state.channels.isNotEmpty()) {
            ChannelBadge("${state.channels.size}")
        }

        Spacer(Modifier.weight(1f))

        // Recording indicator
        if (state.isRecording) {
            RecordingIndicator()
        }

        // Sleep timer remaining
        if (state.sleepTimer != SleepTimer.OFF && state.sleepTimerRemainingMs > 0) {
            val mins = (state.sleepTimerRemainingMs / 60_000).toInt()
            val secs = ((state.sleepTimerRemainingMs % 60_000) / 1000).toInt()
            Box(
                Modifier.clip(CircleShape).background(IPTV_PURPLE.copy(0.2f)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.BedtimeOff, null, Modifier.size(14.dp), tint = IPTV_PURPLE)
                    Text(if (mins > 0) "${mins}m" else "${secs}s", color = IPTV_PURPLE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Action buttons
        state.playlists.firstOrNull { it.isActive }?.let { active ->
            IptvPillButton(
                label = "Refresh",
                icon = Icons.Default.Refresh,
                color = IPTV_GREEN,
                onClick = { onEvent(IptvEvent.RefreshCurrentPlaylist) }
            )
            IptvPillButton(
                label = "Edit",
                icon = Icons.Default.Edit,
                color = IPTV_GOLD,
                onClick = { onEvent(IptvEvent.ShowEditPlaylist(active)) }
            )
        }

        IptvPillButton(
            label = "Add M3U",
            icon = Icons.Default.Add,
            color = IPTV_BLUE,
            onClick = { onEvent(IptvEvent.ShowAddPlaylist) }
        )
    }
}

@Composable
private fun IptvCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = IPTV_WHITE,
    size: Dp = 44.dp
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = IPTV_WHITE,
            contentColor = tint,
            focusedContentColor = IPTV_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        modifier = modifier.size(size)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, null, Modifier.size(size * 0.45f))
        }
    }
}

@Composable
private fun IptvPillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color.copy(0.15f),
            focusedContainerColor = color,
            contentColor = color,
            focusedContentColor = IPTV_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, softWrap = false)
        }
    }
}

@Composable
private fun LiveDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "d")
    Box(Modifier.size(10.dp).alpha(alpha).background(IPTV_LIVE, CircleShape))
}

@Composable
private fun ChannelBadge(text: String) {
    Box(
        Modifier.clip(CircleShape).background(IPTV_BLUE.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = IPTV_BLUE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordingIndicator() {
    val inf = rememberInfiniteTransition(label = "rec")
    val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "r")
    Row(
        Modifier.clip(CircleShape).background(IPTV_RED.copy(0.2f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).alpha(alpha).background(IPTV_RED, CircleShape))
        Text("REC", color = IPTV_RED, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL TOOLBAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelToolbar(
    sortMode: ChannelSortMode,
    isGridView: Boolean,
    onSort: (ChannelSortMode) -> Unit,
    onToggleGrid: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val gridToggleFR = remember { FocusRequester() }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort button with dropdown
        Box {
            Surface(
                onClick = { showSortMenu = !showSortMenu },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x0CFFFFFF),
                    focusedContainerColor = IPTV_CARD_FOCUS,
                    contentColor = IPTV_DIM,
                    focusedContentColor = IPTV_WHITE
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(14.dp))
                    Text(sortMode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, softWrap = false)
                }
            }
            if (showSortMenu) {
                SortDropdown(
                    currentMode = sortMode,
                    onSelect = { onSort(it); showSortMenu = false }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Grid/List toggle
        Surface(
            onClick = onToggleGrid,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isGridView) IPTV_BLUE.copy(0.2f) else Color(0x0CFFFFFF),
                focusedContainerColor = IPTV_CARD_FOCUS,
                contentColor = if (isGridView) IPTV_BLUE else IPTV_MUTED,
                focusedContentColor = IPTV_WHITE
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.size(32.dp).focusRequester(gridToggleFR)
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    null, Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SortDropdown(
    currentMode: ChannelSortMode,
    onSelect: (ChannelSortMode) -> Unit
) {
    Box(
        Modifier
            .offset(y = 36.dp)
            .zIndex(100f)
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C2E))
            .border(1.dp, IPTV_DIVIDER, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp)
    ) {
        Column {
            ChannelSortMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                Surface(
                    onClick = { onSelect(mode) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) IPTV_BLUE.copy(0.15f) else Color.Transparent,
                        focusedContainerColor = IPTV_CARD_FOCUS,
                        contentColor = if (isSelected) IPTV_BLUE else IPTV_DIM,
                        focusedContentColor = IPTV_WHITE
                    ),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(mode.label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  GROUP TABS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun GroupTabRow(
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(groups) { _, group ->
            val isSel = group == selectedGroup
            val icon = when (group) {
                "All" -> Icons.Default.LiveTv
                "Favorites" -> Icons.Default.Favorite
                "Recent" -> Icons.Default.History
                else -> null
            }
            Surface(
                onClick = { onSelectGroup(group) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSel) IPTV_RED else Color(0x0CFFFFFF),
                    focusedContainerColor = IPTV_WHITE,
                    contentColor = if (isSel) IPTV_WHITE else IPTV_DIM,
                    focusedContentColor = IPTV_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (icon != null) Icon(icon, null, Modifier.size(14.dp))
                    Text(group, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, softWrap = false, maxLines = 1)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SEARCH BOX
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvSearchBox(query: String, focusManager: FocusManager, onQueryChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(42.dp)
            .clip(CircleShape)
            .background(if (isFocused) IPTV_CARD_FOCUS else Color(0x0AFFFFFF))
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) IPTV_WHITE else Color.Transparent,
                shape = CircleShape
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = if (isFocused) IPTV_WHITE else IPTV_MUTED)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = IPTV_WHITE, fontSize = 14.sp),
            cursorBrush = SolidColor(IPTV_WHITE),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Search channels...", color = IPTV_MUTED, fontSize = 14.sp)
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }
                            KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }
                            else -> false
                        }
                    } else false
                }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close, null,
                Modifier.size(18.dp).clickable { onQueryChange("") },
                tint = IPTV_MUTED
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL LIST 
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelList(
    channels: List<IptvChannel>,
    currentChannel: IptvChannel?,
    favorites: Set<String>,
    epgData: Map<String, List<EpgProgram>>,
    channelLogos: Map<String, String>,
    channelListFR: FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onShowQr: (IptvChannel) -> Unit
) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📺", fontSize = 36.sp)
                Text("No channels", color = IPTV_MUTED, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().focusRequester(channelListFR)
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val resolvedLogo = channel.logoUrl.ifBlank {
                channelLogos[channel.tvgId.lowercase()]
                    ?: channelLogos[channel.tvgName.lowercase()]
                    ?: channelLogos[channel.id.lowercase()]
                    ?: channelLogos[channel.name.lowercase()]
                    ?: ""
            }

            val nowProgram = run {
                val epg = epgData[channel.tvgId.lowercase()]
                    ?: epgData[channel.tvgName.lowercase()]
                    ?: epgData[channel.id.lowercase()]
                    ?: epgData[channel.name.lowercase()]
                epg?.firstOrNull { it.isLiveNow }
            }

            ChannelRow(
                channel = channel.copy(logoUrl = resolvedLogo),
                isSelected = currentChannel?.id == channel.id,
                isFavorite = channel.id in favorites,
                currentProgram = nowProgram,
                modifier = if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier,
                onSelect = { onSelectChannel(channel) },
                onFavorite = { onToggleFavorite(channel.id) },
                onQr = { onShowQr(channel) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL GRID
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelGrid(
    channels: List<IptvChannel>,
    currentChannel: IptvChannel?,
    channelListFR: FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val isSelected = currentChannel?.id == channel.id
            Surface(
                onClick = { onSelectChannel(channel) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) IPTV_RED.copy(0.2f) else IPTV_CARD,
                    focusedContainerColor = IPTV_CARD_FOCUS,
                    contentColor = IPTV_WHITE,
                    focusedContentColor = IPTV_WHITE
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier
                    .aspectRatio(1.4f)
                    .then(if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(channel.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        channel.name,
                        color = IPTV_WHITE,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: IptvChannel,
    isSelected: Boolean,
    isFavorite: Boolean,
    currentProgram: EpgProgram?,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onQr: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) IPTV_RED.copy(0.12f) else Color(0x08FFFFFF),
            focusedContainerColor = IPTV_WHITE,
            contentColor = IPTV_WHITE,
            focusedContentColor = IPTV_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = if (isSelected) Border(BorderStroke(1.dp, IPTV_RED.copy(0.4f)), shape = RoundedCornerShape(14.dp)) else Border.None,
            focusedBorder = Border.None
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_F -> { onFavorite(); true }
                        KeyEvent.KEYCODE_Q -> { onQr(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel number
            Text(
                "${channel.number}",
                color = if (isFocused) IPTV_BG else IPTV_MUTED,
                fontSize = 11.sp,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold
            )

            // Logo
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFocused) Color.Black.copy(0.1f) else Color(0x12FFFFFF)),
                Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                            .crossfade(true)
                            .placeholder(android.R.color.transparent)
                            .error(android.R.color.transparent)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Text(
                        channel.name.take(2).uppercase(),
                        color = if (isFocused) IPTV_BG else IPTV_DIM,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Name + program
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        channel.name,
                        color = if (isFocused) IPTV_BG else IPTV_WHITE,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.resolution.isNotEmpty()) {
                        val badgeColor = when (channel.resolution) {
                            "4K" -> IPTV_GOLD
                            "FHD" -> IPTV_BLUE
                            else -> IPTV_GREEN
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(3.dp))
                                .background(badgeColor.copy(if (isFocused) 0.3f else 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(channel.resolution, color = if (isFocused) IPTV_BG else badgeColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (channel.hasArchive) {
                        Icon(Icons.Default.VideoLibrary, null, Modifier.size(12.dp), tint = if (isFocused) IPTV_BG.copy(0.6f) else IPTV_PURPLE.copy(0.7f))
                    }
                }
                if (currentProgram != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            currentProgram.title,
                            color = if (isFocused) IPTV_BG.copy(0.7f) else IPTV_MUTED,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentProgram.remainingMinutes > 0) {
                            Text("• ${currentProgram.remainingMinutes}m left", color = if (isFocused) IPTV_BG.copy(0.5f) else IPTV_MUTED.copy(0.6f), fontSize = 10.sp)
                        }
                    }
                } else {
                    Text(channel.groupTitle, color = if (isFocused) IPTV_BG.copy(0.6f) else IPTV_MUTED, fontSize = 12.sp)
                }
            }

            // Right side: live indicator + progress
            if (currentProgram != null && !isFocused) {
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                    LiveDot()
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.2f))) {
                        Box(Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                    }
                }
            }

            // Favorite icon
            if (isFocused || isFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null,
                    Modifier.size(18.dp),
                    tint = if (isFocused && !isFavorite) IPTV_BG else IPTV_RED
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NOW PLAYING PANEL
// ══════════════════════════════════════════════════════════════════
@Composable
private fun NowPlayingPanel(
    state: IptvState,
    onToggleFavorite: () -> Unit,
    onShowQr: () -> Unit,
    onShowEpg: () -> Unit,
    onShowFullScreen: () -> Unit,
    onSleepTimer: () -> Unit,
    onToggleRecording: () -> Unit,
    onSettings: () -> Unit
) {
    val channel = state.currentChannel ?: return
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isFavorite = channel.id in state.favoriteChannelIds
    val currentProgram = state.currentProgram
    val nextProgram = state.nextProgram

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IPTV_SURFACE)
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)),
                    Alignment.Center
                ) {
                    val resolvedLogo = channel.logoUrl.ifBlank {
                        state.channelLogos[channel.tvgId.lowercase()]
                            ?: state.channelLogos[channel.tvgName.lowercase()]
                            ?: state.channelLogos[channel.id.lowercase()]
                            ?: state.channelLogos[channel.name.lowercase()]
                            ?: ""
                    }
                    if (resolvedLogo.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(resolvedLogo)
                                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .crossfade(true)
                                .build(),
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(60.dp)
                        )
                    } else {
                        Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("● LIVE", color = IPTV_WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Text(channel.groupTitle, color = IPTV_MUTED, fontSize = 13.sp)
                        if (channel.resolution.isNotEmpty()) {
                            val resColor = when (channel.resolution) { "4K" -> IPTV_GOLD; "FHD" -> IPTV_BLUE; else -> IPTV_GREEN }
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(resColor.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(channel.resolution, color = resColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(channel.name, color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (channel.number > 0) {
                        Text("Channel ${channel.number}", color = IPTV_MUTED, fontSize = 13.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IptvActionBtn(Icons.Default.Fullscreen, IPTV_WHITE, onClick = onShowFullScreen)
                    IptvActionBtn(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (isFavorite) IPTV_RED else IPTV_DIM,
                        onClick = onToggleFavorite
                    )
                    IptvActionBtn(Icons.Default.QrCode2, IPTV_BLUE, onClick = onShowQr)
                    IptvActionBtn(Icons.Default.CalendarViewWeek, IPTV_GOLD, onClick = onShowEpg)
                    IptvActionBtn(
                        Icons.Default.BedtimeOff,
                        if (state.sleepTimer != SleepTimer.OFF) IPTV_PURPLE else IPTV_DIM,
                        onClick = onSleepTimer
                    )
                    IptvActionBtn(
                        Icons.Default.FiberManualRecord,
                        if (state.isRecording) IPTV_RED else IPTV_DIM,
                        onClick = onToggleRecording
                    )
                    IptvActionBtn(Icons.Default.Settings, IPTV_MUTED, onClick = onSettings)
                }
            }

            if (currentProgram != null) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x0AFFFFFF))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (currentProgram.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentProgram.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = currentProgram.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(60.dp).height(80.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Text("NOW", color = IPTV_LIVE, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(currentProgram.title, color = IPTV_WHITE, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (currentProgram.episodeNum.isNotEmpty()) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(IPTV_PURPLE.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(currentProgram.episodeNum, color = IPTV_PURPLE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("${timeFormatter.format(Date(currentProgram.startTime))} – ${timeFormatter.format(Date(currentProgram.endTime))}", color = IPTV_MUTED, fontSize = 13.sp)
                        if (currentProgram.category.isNotEmpty()) {
                            Text(currentProgram.category, color = IPTV_MUTED.copy(0.6f), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x20FFFFFF))) {
                            Box(
                                Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight()
                                    .background(Brush.horizontalGradient(listOf(IPTV_RED, IPTV_LIVE)))
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("${currentProgram.remainingMinutes} min remaining", color = IPTV_MUTED, fontSize = 11.sp)
                    }

                    if (nextProgram != null) {
                        Box(Modifier.width(1.dp).height(80.dp).background(IPTV_DIVIDER))
                        Column(Modifier.weight(0.75f)) {
                            Text("NEXT", color = IPTV_MUTED, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(nextProgram.title, color = IPTV_DIM, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(timeFormatter.format(Date(nextProgram.startTime)), color = IPTV_MUTED, fontSize = 12.sp)
                            Text("${nextProgram.durationMinutes} min", color = IPTV_MUTED.copy(0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = tint,
            contentColor = tint,
            focusedContentColor = IPTV_WHITE
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.size(46.dp)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EPG TIMELINE
// ══════════════════════════════════════════════════════════════════
@Composable
private fun EpgTimeline(
    programs: List<EpgProgram>,
    epgLoadState: IptvLoadState,
    dayOffset: Int,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val now = System.currentTimeMillis()

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(IPTV_SURFACE).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Programme Guide", color = IPTV_WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in -1..2) {
                    val dayLabel = when (d) {
                        0 -> "Today"
                        1 -> "Tmrw"
                        -1 -> "Yest"
                        else -> dateFormatter.format(Date(now + d * 86_400_000L)).take(5)
                    }
                    Surface(
                        onClick = { onDayChange(d) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (dayOffset == d) IPTV_BLUE.copy(0.25f) else Color(0x0AFFFFFF),
                            focusedContainerColor = IPTV_BLUE,
                            contentColor = if (dayOffset == d) IPTV_BLUE else IPTV_MUTED,
                            focusedContentColor = IPTV_WHITE
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp).fillMaxHeight(), Alignment.Center) {
                            Text(dayLabel, fontSize = 11.sp, fontWeight = if (dayOffset == d) FontWeight.Bold else FontWeight.Normal, softWrap = false)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            when (epgLoadState) {
                is IptvLoadState.Loading -> { Icon(Icons.Default.Sync, null, Modifier.size(14.dp), tint = IPTV_GOLD) }
                is IptvLoadState.Error -> { Icon(Icons.Default.ErrorOutline, null, Modifier.size(14.dp), tint = IPTV_RED) }
                else -> {}
            }
        }

        Spacer(Modifier.height(16.dp))

        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(40.dp), tint = IPTV_MUTED)
                    Text(
                        when (epgLoadState) {
                            is IptvLoadState.Loading -> "Loading EPG..."
                            is IptvLoadState.Error -> "EPG unavailable"
                            else -> "No programme data"
                        },
                        color = IPTV_MUTED, fontSize = 14.sp
                    )
                }
            }
        } else {
            val dayStart = run {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            val dayEnd = dayStart + 86_400_000L

            val dayPrograms = programs
                .filter { it.endTime > dayStart && it.startTime < dayEnd }
                .sortedBy { it.startTime }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayPrograms, key = { "${it.startTime}_${it.channelId}" }) { prog ->
                EpgProgramRow(prog, timeFormatter)
                }
            }
        }
    }
}

@Composable
private fun EpgProgramRow(program: EpgProgram, timeFormatter: SimpleDateFormat) {
    val isLive = program.isLiveNow
    val isPast = program.isPast

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when { isLive -> IPTV_LIVE.copy(0.1f); isPast -> Color(0x05FFFFFF); else -> Color(0x0AFFFFFF) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isLive) LiveDot() else Spacer(Modifier.size(10.dp))
        Column(Modifier.width(78.dp)) {
            Text(timeFormatter.format(Date(program.startTime)), color = if (isLive) IPTV_LIVE else if (isPast) IPTV_MUTED else IPTV_DIM, fontSize = 14.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
            Text("${program.durationMinutes}m", color = IPTV_MUTED, fontSize = 11.sp)
        }
        Box(Modifier.width(2.dp).height(32.dp).background(if (isLive) IPTV_LIVE else IPTV_DIVIDER))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    program.title,
                    color = if (isPast) IPTV_MUTED else if (isLive) IPTV_WHITE else IPTV_DIM,
                    fontSize = 14.sp,
                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (program.episodeNum.isNotEmpty()) {
                    Text(program.episodeNum, color = IPTV_PURPLE, fontSize = 10.sp)
                }
                if (program.category.isNotEmpty()) {
                    Text(program.category, color = IPTV_MUTED.copy(0.6f), fontSize = 10.sp)
                }
            }
        }
        if (isLive) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
                Text("${(program.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.25f))) {
                    Box(Modifier.fillMaxWidth(program.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FULLSCREEN OVERLAY
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FullscreenOverlay(
    state: IptvState,
    fullScreenFR: FocusRequester,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onExit: () -> Unit
) {
    var showHud by remember { mutableStateOf(true) }
    val ch = state.currentChannel ?: return

    LaunchedEffect(ch.id) { showHud = true; delay(4000); showHud = false }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fullScreenFR)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { onChannelUp(); true }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { onChannelDown(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showHud = true; true }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onExit(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomStart).zIndex(50f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
                    .padding(32.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("● LIVE", color = IPTV_WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Text("${ch.number}  ${ch.name}", color = IPTV_WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }

                    state.currentProgram?.let { prog ->
                        Spacer(Modifier.height(8.dp))
                        Text(prog.title, color = IPTV_DIM, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(300.dp).height(4.dp).clip(CircleShape).background(IPTV_WHITE.copy(0.2f))) {
                            Box(Modifier.fillMaxWidth(prog.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("▲▼ Channel • BACK Exit", color = IPTV_MUTED, fontSize = 12.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SLEEP TIMER INDICATOR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SleepTimerIndicator(remainingMs: Long, onDismiss: () -> Unit) {
    val mins = (remainingMs / 60_000).toInt()
    val secs = ((remainingMs % 60_000) / 1000).toInt()
    val displayText = when {
        mins > 0 -> "$mins min ${secs}s"
        else -> "${secs}s"
    }

    Surface(
        onClick = onDismiss,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = IPTV_PURPLE.copy(0.15f),
            focusedContainerColor = IPTV_RED.copy(0.15f),
            contentColor = IPTV_PURPLE,
            focusedContentColor = IPTV_RED
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.BedtimeOff, null, Modifier.size(18.dp))
            Text("Sleep: $displayText", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Tap to cancel", color = IPTV_MUTED, fontSize = 12.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SLEEP TIMER PICKER DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
fun SleepTimerPickerDialog(
    currentTimer: SleepTimer,
    onSelect: (SleepTimer) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFR.requestFocus() } }

    Box(
        Modifier
            .width(400.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(IPTV_SURFACE)
            .border(1.dp, IPTV_PURPLE.copy(0.3f), RoundedCornerShape(24.dp))
            .padding(28.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BedtimeOff, null, Modifier.size(24.dp), tint = IPTV_PURPLE)
                Spacer(Modifier.width(10.dp))
                Text("Sleep Timer", color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                IptvCircleButton(Icons.Default.Close, onDismiss, tint = IPTV_DIM, size = 40.dp)
            }
            Spacer(Modifier.height(20.dp))
            SleepTimer.entries.forEachIndexed { idx, timer ->
                val isSelected = timer == currentTimer
                Surface(
                    onClick = { onSelect(timer) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) IPTV_PURPLE.copy(0.2f) else Color(0x0AFFFFFF),
                        focusedContainerColor = IPTV_PURPLE,
                        contentColor = if (isSelected) IPTV_PURPLE else IPTV_DIM,
                        focusedContentColor = IPTV_WHITE
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 3.dp)
                        .then(if (idx == 0) Modifier.focusRequester(firstFR) else Modifier)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timer.label, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PARENTAL PIN DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
fun ParentalPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFR = remember { FocusRequester() }
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFR.requestFocus() } }

    Box(
        Modifier
            .width(380.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(IPTV_SURFACE)
            .border(1.dp, IPTV_RED.copy(0.3f), RoundedCornerShape(24.dp))
            .padding(28.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = IPTV_RED)
            Spacer(Modifier.height(12.dp))
            Text("Parental Lock", color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Enter PIN to continue", color = IPTV_MUTED, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(16.dp).background(
                            if (i < pin.length) IPTV_RED else IPTV_MUTED.copy(0.3f),
                            CircleShape
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val digits = listOf("1","2","3","4","5","6","7","8","9","⌫","0","✓")
            var firstSet = false
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { d ->
                        Surface(
                            onClick = {
                                when (d) {
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "✓" -> if (pin.length == 4) onConfirm(pin)
                                    else -> if (pin.length < 4) pin += d
                                }
                            },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0x0CFFFFFF),
                                focusedContainerColor = if (d == "✓") IPTV_GREEN else IPTV_WHITE,
                                contentColor = IPTV_WHITE,
                                focusedContentColor = IPTV_BG
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                            modifier = Modifier.size(56.dp).then(
                                if (!firstSet && d == "1") { firstSet = true; Modifier.focusRequester(firstFR) } else Modifier
                            )
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(d, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x0AFFFFFF), focusedContainerColor = IPTV_RED,
                    contentColor = IPTV_DIM, focusedContentColor = IPTV_WHITE
                ),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Cancel", fontSize = 14.sp) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  IPTV SETTINGS DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvSettingsDialog(
    state: IptvState,
    onEvent: (IptvEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFR = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFR.requestFocus() } }

    Box(
        Modifier
            .width(520.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(IPTV_SURFACE)
            .border(1.dp, IPTV_BLUE.copy(0.25f), RoundedCornerShape(24.dp))
            .padding(28.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, Modifier.size(22.dp), tint = IPTV_BLUE)
                Spacer(Modifier.width(10.dp))
                Text("IPTV Settings", color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                IptvCircleButton(Icons.Default.Close, onDismiss, tint = IPTV_DIM, size = 40.dp)
            }
            Spacer(Modifier.height(20.dp))

            Text("Stream Quality", color = IPTV_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(StreamQuality.entries) { idx, quality ->
                    val isSelected = state.streamQuality == quality
                    Surface(
                        onClick = { onEvent(IptvEvent.SetStreamQuality(quality)) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) IPTV_BLUE.copy(0.25f) else Color(0x0AFFFFFF),
                            focusedContainerColor = IPTV_BLUE,
                            contentColor = if (isSelected) IPTV_BLUE else IPTV_DIM,
                            focusedContentColor = IPTV_WHITE
                        ),
                        modifier = Modifier.height(44.dp).then(if (idx == 0) Modifier.focusRequester(firstFR) else Modifier)
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.Center) {
                            Text(quality.label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, softWrap = false)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            IptvSettingRow(
                title = "Subtitles",
                subtitle = "Show subtitles when available",
                icon = Icons.Default.Subtitles,
                isEnabled = state.subtitlesEnabled,
                onToggle = { onEvent(IptvEvent.ToggleSubtitles) }
            )

            Spacer(Modifier.height(12.dp))

            IptvSettingRow(
                title = "Parental Lock",
                subtitle = "Require PIN for adult channels",
                icon = Icons.Default.Lock,
                isEnabled = state.parentalLockEnabled,
                onToggle = {
                    if (!state.parentalLockEnabled) {
                        onEvent(IptvEvent.SetParentalLock(true, if (state.parentalPin.isEmpty()) "1234" else state.parentalPin))
                    } else {
                        onEvent(IptvEvent.SetParentalLock(false, state.parentalPin))
                    }
                }
            )
        }
    }
}

@Composable
private fun IptvSettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0AFFFFFF),
            focusedContainerColor = Color(0x18FFFFFF),
            contentColor = IPTV_WHITE,
            focusedContentColor = IPTV_WHITE
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = IPTV_BLUE)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = IPTV_WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = IPTV_MUTED, fontSize = 12.sp)
            }
            val thumbBias by animateFloatAsState(if (isEnabled) 1f else -1f, tween(200), label = "tb")
            val trackColor by animateColorAsState(if (isEnabled) IPTV_BLUE else Color(0x33FFFFFF), tween(200), label = "tc")
            Box(
                Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(trackColor).padding(3.dp),
                contentAlignment = androidx.compose.ui.BiasAlignment(horizontalBias = thumbBias, verticalBias = 0f)
            ) {
                Box(Modifier.size(18.dp).background(IPTV_WHITE, CircleShape))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EPG FULL GUIDE
// ══════════════════════════════════════════════════════════════════
@Composable
fun EpgFullGuide(
    channel: IptvChannel,
    programs: List<EpgProgram>,
    dayOffset: Int,
    onDayChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    val closeFR = remember { FocusRequester() }
    val now = System.currentTimeMillis()

    LaunchedEffect(Unit) { delay(150); runCatching { closeFR.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(IPTV_BG)
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        Row(
            Modifier.fillMaxWidth().height(80.dp).background(IPTV_SURFACE).padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE,
                    contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier = Modifier.size(48.dp).focusRequester(closeFR)
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(22.dp)) } }

            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).addHeader("User-Agent", "Mozilla/5.0").crossfade(true).build(),
                        contentDescription = channel.name, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp)
                    )
                } else {
                    Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text("${channel.name} – Programme Guide", color = IPTV_WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in -1..3) {
                    val label = when (d) {
                        0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"
                        else -> dateFormatter.format(Date(now + d * 86_400_000L))
                    }
                    Surface(
                        onClick = { onDayChange(d) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (dayOffset == d) IPTV_BLUE.copy(0.25f) else Color(0x0AFFFFFF),
                            focusedContainerColor = IPTV_BLUE,
                            contentColor = if (dayOffset == d) IPTV_BLUE else IPTV_MUTED,
                            focusedContentColor = IPTV_WHITE
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.Center) {
                            Text(label, fontSize = 12.sp, fontWeight = if (dayOffset == d) FontWeight.Bold else FontWeight.Normal, softWrap = false)
                        }
                    }
                }
            }
        }

        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(56.dp), tint = IPTV_MUTED)
                    Text("No programme data available", color = IPTV_MUTED, fontSize = 18.sp)
                }
            }
        } else {
            val dayStart = run {
                val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            val dayEnd = dayStart + 86_400_000L
            val dayPrograms = programs.filter { it.endTime > dayStart && it.startTime < dayEnd }.sortedBy { it.startTime }

            val grouped = dayPrograms.groupBy { dateFormatter.format(Date(it.startTime)) }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.focusGroup()
            ) {
                grouped.forEach { (date, progs) ->
                    item {
                        Text(date, color = IPTV_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(progs, key = { "${it.startTime}_${it.channelId}" }) { prog ->
                    EpgFullRow(program = prog, timeFormatter = timeFormatter)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgFullRow(program: EpgProgram, timeFormatter: SimpleDateFormat) {
    val isLive = program.isLiveNow
    val isPast = program.isPast

    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when { isLive -> IPTV_LIVE.copy(0.1f); isPast -> Color(0x05FFFFFF); else -> Color(0x0AFFFFFF) },
            focusedContainerColor = when { isLive -> IPTV_LIVE.copy(0.2f); else -> Color(0x18FFFFFF) }
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isLive) Border(BorderStroke(1.dp, IPTV_LIVE.copy(0.35f)), shape = RoundedCornerShape(16.dp)) else Border.None,
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (program.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(program.posterUrl).crossfade(true).build(),
                    contentDescription = program.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(56.dp).height(76.dp).clip(RoundedCornerShape(8.dp))
                )
            }

            Column(Modifier.width(88.dp)) {
                Text(timeFormatter.format(Date(program.startTime)), color = if (isLive) IPTV_LIVE else IPTV_DIM, fontSize = 15.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
                Text("${program.durationMinutes}min", color = IPTV_MUTED, fontSize = 13.sp)
            }

            if (isLive) {
                Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("LIVE", color = IPTV_WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        program.title,
                        color = if (isPast) IPTV_MUTED else IPTV_WHITE,
                        fontSize = 17.sp,
                        fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (program.episodeNum.isNotEmpty()) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(IPTV_PURPLE.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(program.episodeNum, color = IPTV_PURPLE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (program.category.isNotEmpty()) {
                    Text(program.category, color = IPTV_MUTED, fontSize = 12.sp)
                }
                if (program.description.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(program.description, color = IPTV_MUTED.copy(0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (program.actors.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Cast: ${program.actors}", color = IPTV_MUTED.copy(0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (isLive) {
                Column(Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                    Text("${(program.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(68.dp).height(7.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.25f))) {
                        Box(Modifier.fillMaxWidth(program.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${program.remainingMinutes}m left", color = IPTV_MUTED, fontSize = 10.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  QR CODE DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
fun QrCodeDialog(channel: IptvChannel, onDismiss: () -> Unit) {
    val closeFR = remember { FocusRequester() }
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 300) }

    LaunchedEffect(Unit) { delay(150); runCatching { closeFR.requestFocus() } }

    Column(
        Modifier
            .width(520.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(IPTV_SURFACE)
            .border(1.dp, IPTV_BLUE.copy(0.25f), RoundedCornerShape(28.dp))
            .padding(32.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Stream QR Code", color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Scan to open on mobile device", color = IPTV_MUTED, fontSize = 14.sp)
            }
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE,
                    contentColor = IPTV_DIM, focusedContentColor = IPTV_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier = Modifier.size(44.dp).focusRequester(closeFR)
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(20.dp)) } }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.size(260.dp).clip(RoundedCornerShape(20.dp)).background(IPTV_WHITE).padding(14.dp)
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0AFFFFFF)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).addHeader("User-Agent", "Mozilla/5.0").crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp)
                    )
                } else {
                    Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text(channel.name, color = IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(channel.groupTitle, color = IPTV_MUTED, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(channel.streamUrl, color = IPTV_MUTED, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ══════════════════════════════════════════════════════════════════
//  ADD PLAYLIST DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
fun AddPlaylistDialog(
    state: IptvState,
    focusRequester: FocusRequester,
    focusManager: FocusManager,
    onEvent: (IptvEvent) -> Unit
) {
    var urlFocused by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    var epgFocused by remember { mutableStateOf(false) }

    val serverUrl = "http://${state.localIpAddress}:8080"
    val qrBitmap = remember(serverUrl) {
        if (state.localIpAddress.isNotBlank()) QrCodeGenerator.generate(serverUrl, 280) else null
    }

    LaunchedEffect(Unit) { delay(200); runCatching { focusRequester.requestFocus() } }

    Box(
        Modifier
            .width(900.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(IPTV_SURFACE)
            .border(1.dp, IPTV_BLUE.copy(0.25f), RoundedCornerShape(28.dp))
            .padding(40.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Add M3U / M3U8 Playlist", color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Connect from phone or enter URL manually", color = IPTV_MUTED, fontSize = 16.sp)
                }
                Surface(
                    onClick = { onEvent(IptvEvent.HideAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE,
                        contentColor = IPTV_DIM, focusedContentColor = IPTV_BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(44.dp)
                ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(20.dp)) } }
            }

            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(
                    Modifier.weight(0.85f).clip(RoundedCornerShape(20.dp)).background(Color(0x0AFFFFFF)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Option 1: From Phone", color = IPTV_BLUE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Scan the QR to open the form on your phone and send links to this TV.", color = IPTV_MUTED, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    if (qrBitmap != null) {
                        Box(Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(IPTV_WHITE).padding(12.dp)) {
                            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(serverUrl, color = IPTV_DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Box(Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x0AFFFFFF)), Alignment.Center) {
                            Text("Detecting IP...", color = IPTV_MUTED)
                        }
                    }
                }

                Column(Modifier.weight(1.2f)) {
                    Text("Option 2: Enter Manually", color = IPTV_WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))

                    AddDialogField(
                        label = "Playlist Name",
                        value = state.addPlaylistName,
                        hint = "e.g. My IPTV",
                        isFocused = nameFocused,
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        focusManager = focusManager,
                        modifier = Modifier.onFocusChanged { nameFocused = it.isFocused },
                        onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistName(it)) }
                    )

                    Spacer(Modifier.height(16.dp))

                    AddDialogField(
                        label = "M3U/M3U8 URL *",
                        value = state.addPlaylistUrl,
                        hint = "http://provider.com/list.m3u",
                        isFocused = urlFocused,
                        icon = Icons.Default.Link,
                        focusManager = focusManager,
                        modifier = Modifier.focusRequester(focusRequester).onFocusChanged { urlFocused = it.isFocused },
                        isRequired = true,
                        onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
                    )

                    Spacer(Modifier.height(16.dp))

                    AddDialogField(
                        label = "EPG URL (Optional)",
                        value = state.addPlaylistEpgUrl,
                        hint = "http://provider.com/epg.xml.gz",
                        isFocused = epgFocused,
                        icon = Icons.AutoMirrored.Filled.EventNote,
                        focusManager = focusManager,
                        modifier = Modifier.onFocusChanged { epgFocused = it.isFocused },
                        onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) }
                    )

                    Spacer(Modifier.height(28.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            onClick = { onEvent(IptvEvent.HideAddPlaylist) },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0x14FFFFFF), focusedContainerColor = Color(0x2AFFFFFF),
                                contentColor = IPTV_DIM, focusedContentColor = IPTV_WHITE
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Bold) } }

                        Surface(
                            onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE.copy(0.8f) else IPTV_MUTED.copy(0.2f),
                                focusedContainerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE else IPTV_MUTED.copy(0.3f),
                                contentColor = IPTV_WHITE, focusedContentColor = IPTV_WHITE
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            modifier = Modifier.weight(1.6f).height(56.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.CloudDownload, null, Modifier.size(22.dp))
                                    Text("Load Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDialogField(
    label: String, value: String, hint: String, isFocused: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusManager: FocusManager,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = IPTV_DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (isRequired) Text("*", color = IPTV_RED, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color(0xFF1E1E32) else Color(0x0AFFFFFF))
                .border(width = if (isFocused) 2.dp else 1.dp, color = if (isFocused) IPTV_WHITE else IPTV_DIVIDER, shape = CircleShape)
                .padding(horizontal = 18.dp)
                .then(modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (isFocused) IPTV_WHITE else IPTV_MUTED)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = IPTV_WHITE, fontSize = 16.sp),
                cursorBrush = SolidColor(IPTV_WHITE),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(hint, color = IPTV_MUTED, fontSize = 16.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }
                            KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }
                            else -> false
                        }
                    } else false
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvEmptyState(
    loadState: IptvLoadState,
    addPlaylistFR: FocusRequester,
    onEvent: (IptvEvent) -> Unit
) {
    LaunchedEffect(loadState) { delay(150); runCatching { addPlaylistFR.requestFocus() } }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(140.dp)
                    .background(IPTV_BLUE.copy(0.08f), CircleShape)
                    .border(1.5.dp, IPTV_BLUE.copy(0.25f), CircleShape),
                Alignment.Center
            ) {
                Icon(Icons.Default.LiveTv, null, Modifier.size(64.dp), tint = IPTV_BLUE.copy(0.6f))
            }
            Text("Lumina Live TV", color = IPTV_WHITE, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text(
                "Add an M3U or M3U8 playlist to start watching\nlive channels with full EPG guide support",
                color = IPTV_MUTED, fontSize = 18.sp, textAlign = TextAlign.Center
            )

            if (loadState is IptvLoadState.Error) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(IPTV_RED.copy(0.1f))
                        .border(1.dp, IPTV_RED.copy(0.25f), RoundedCornerShape(14.dp)).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Error, null, Modifier.size(22.dp), tint = IPTV_RED)
                        Text(loadState.message, color = IPTV_RED, fontSize = 15.sp)
                    }
                }
            }

            Surface(
                onClick = { onEvent(IptvEvent.ShowAddPlaylist) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = IPTV_BLUE, focusedContainerColor = IPTV_WHITE,
                    contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(IPTV_BLUE.copy(0.45f), 20.dp)),
                modifier = Modifier.height(64.dp).focusRequester(addPlaylistFR)
            ) {
                Row(Modifier.padding(horizontal = 48.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(26.dp))
                    Text("Add Playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  WELCOME PANEL
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvWelcomePanel(
    channelCount: Int,
    playlistName: String,
    epgStatus: IptvLoadState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(IPTV_SURFACE)
            .background(Brush.radialGradient(listOf(IPTV_BLUE.copy(0.04f), Color.Transparent), radius = 600f)),
        Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(88.dp), tint = IPTV_BLUE.copy(0.35f))
            Spacer(Modifier.height(4.dp))
            Text(playlistName.ifEmpty { "Ready to Watch" }, color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("$channelCount channels loaded", color = IPTV_MUTED, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            when (epgStatus) {
                is IptvLoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Sync, null, Modifier.size(14.dp), tint = IPTV_GOLD)
                    Text("Loading EPG guide...", color = IPTV_GOLD, fontSize = 13.sp)
                }
                is IptvLoadState.Success -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = IPTV_GREEN)
                    Text("EPG guide loaded", color = IPTV_GREEN, fontSize = 13.sp)
                }
                is IptvLoadState.Error -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = IPTV_MUTED)
                    Text("EPG unavailable", color = IPTV_MUTED, fontSize = 13.sp)
                }
                else -> {}
            }
            Text("← Select a channel to start watching", color = IPTV_MUTED, fontSize = 14.sp)
        }
    }
}