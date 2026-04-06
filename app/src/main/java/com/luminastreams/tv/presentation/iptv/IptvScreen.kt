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
//  PREMIUM IPTV PALETTE
// ══════════════════════════════════════════════════════════════════
private val IPTV_BG         = Color(0xFF04040A) // Deep Netflix/Apple TV Black
private val IPTV_SURFACE    = Color(0x7311111E) // Premium Glassmorphism
private val IPTV_CARD       = Color(0x33FFFFFF)
private val IPTV_CARD_FOCUS = Color(0xFF007AFF) // Sleek Apple Blue Focus
private val IPTV_RED        = Color(0xFFE50914) // Netflix Red
private val IPTV_LIVE       = Color(0xFFFF2A2A)
private val IPTV_BLUE       = Color(0xFF0A84FF)
private val IPTV_GREEN      = Color(0xFF30D158)
private val IPTV_GOLD       = Color(0xFFFFD60A)
private val IPTV_PURPLE     = Color(0xFFBF5AF2)
private val IPTV_WHITE      = Color(0xFFFFFFFF)
private val IPTV_DIM        = Color(0xB3FFFFFF)
private val IPTV_MUTED      = Color(0x66FFFFFF)
private val IPTV_DIVIDER    = Color(0x14FFFFFF)

@Composable
fun IptvScreen(viewModel: IptvViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val exo = remember { ExoPlayerWrapper(context) }
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()
    var isFullScreen by remember { mutableStateOf(false) }

    val backFR = remember { FocusRequester() }
    val channelListFR = remember { FocusRequester() }
    val addPlaylistFR = remember { FocusRequester() }
    val fullScreenFR = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { exo.release() } }
    LaunchedEffect(Unit) { delay(200); runCatching { backFR.requestFocus() } }

    BackHandler {
        when {
            state.showSettings -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showParentalPinEntry -> viewModel.onEvent(IptvEvent.DismissParentalPin)
            state.showSleepTimerPicker -> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            state.showMultiView -> viewModel.onEvent(IptvEvent.ToggleMultiView)
            state.showQrCode -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showAddPlaylist -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showEpgGuide -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            isFullScreen -> isFullScreen = false
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(state.currentChannel) {
        state.currentChannel?.let { ch -> exo.prepareStream(ch.streamUrl); exo.play() } ?: exo.pause()
    }
    LaunchedEffect(isFullScreen) {
        delay(120); runCatching { if (isFullScreen) fullScreenFR.requestFocus() else channelListFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(IPTV_BG)) {
        if (state.currentChannel != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().focusable(false),
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val sv = SurfaceView(ctx).apply { keepScreenOn = true }
                        sv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) { exo.player.setVideoSurfaceView(sv) }
                            override fun onViewDetachedFromWindow(v: android.view.View) { exo.player.clearVideoSurface() }
                        })
                        addView(sv)
                    }
                },
                update = { if (videoAspectRatio > 0f) it.setAspectRatio(videoAspectRatio) }
            )
        }

        AnimatedVisibility(visible = !isFullScreen, enter = fadeIn(), exit = fadeOut()) {
            val bgAlpha = if (state.currentChannel != null) 0.85f else 1f
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(IPTV_BG.copy(alpha = bgAlpha), Color.Black.copy(alpha = bgAlpha + 0.1f))))) {
                Column(Modifier.fillMaxSize()) {
                    IptvTopBar(state, backFR, onNavigateBack, viewModel::onEvent)
                    if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                        IptvEmptyState(state.loadState, addPlaylistFR, viewModel::onEvent)
                    } else {
                        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Left Panel
                            Column(Modifier.weight(0.32f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE).padding(vertical = 12.dp)) {
                                GroupTabRow(state.groups, state.selectedGroup) { viewModel.onEvent(IptvEvent.SelectGroup(it)) }
                                IptvSearchBox(state.searchQuery, focusManager) { viewModel.onEvent(IptvEvent.UpdateSearch(it)) }
                                ChannelToolbar(state.channelSortMode, state.showChannelGrid, { viewModel.onEvent(IptvEvent.SetChannelSort(it)) }, { viewModel.onEvent(IptvEvent.ToggleChannelGrid) })
                                if (state.showChannelGrid) {
                                    ChannelGrid(state.filteredChannels, state.currentChannel, channelListFR) { viewModel.onEvent(IptvEvent.SelectChannel(it)); isFullScreen = true }
                                } else {
                                    ChannelList(state.filteredChannels, state.currentChannel, state.favoriteChannelIds, state.epgData, state.channelLogos, channelListFR,
                                        onSelectChannel = { ch -> if (state.currentChannel?.id == ch.id) isFullScreen = true else { viewModel.onEvent(IptvEvent.SelectChannel(ch)); isFullScreen = true } },
                                        onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(it)) },
                                        onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(it)) }
                                    )
                                }
                            }
                            // Right Panel
                            Column(Modifier.weight(0.68f).fillMaxHeight()) {
                                if (state.currentChannel != null) {
                                    NowPlayingPanel(state,
                                        { viewModel.onEvent(IptvEvent.ToggleFavorite(state.currentChannel!!.id)) },
                                        { viewModel.onEvent(IptvEvent.ShowQrCode(state.currentChannel!!)) },
                                        { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                                        { isFullScreen = true },
                                        { viewModel.onEvent(IptvEvent.ShowSleepTimerPicker) },
                                        { viewModel.onEvent(IptvEvent.ToggleRecording) },
                                        { viewModel.onEvent(IptvEvent.ShowIptvSettings) }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    val epgPrograms = remember(state.epgData, state.currentChannel?.id, state.epgDayOffset) { viewModel.getEpgForChannel(state.currentChannel!!, state.epgData) }
                                    EpgTimeline(epgPrograms, state.epgLoadState, state.epgDayOffset, { viewModel.onEvent(IptvEvent.SetEpgDayOffset(it)) }, Modifier.weight(1f))
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
        }

        if (isFullScreen) {
            FullscreenOverlay(state, fullScreenFR,
                { val idx = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }; viewModel.onEvent(IptvEvent.ChannelUp(idx)) },
                { val idx = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }; viewModel.onEvent(IptvEvent.ChannelDown(idx)) },
                { isFullScreen = false }
            )
        }

        if (state.loadState is IptvLoadState.Loading) { IptvDialog(onDismiss = {}) { Box(Modifier.fillMaxSize(), Alignment.Center) { com.luminastreams.tv.ui.components.LoadingIndicator() } } }
        if (state.showAddPlaylist) { IptvDialog({ viewModel.onEvent(IptvEvent.HideAddPlaylist) }) { Box(Modifier.fillMaxSize(), Alignment.Center) { AddPlaylistDialog(state, addPlaylistFR, focusManager, viewModel::onEvent) } } }
    }
}

@Composable
private fun IptvDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)).clickable(remember { MutableInteractionSource() }, null) { onDismiss() }.focusGroup().focusRestorer().focusProperties { exit = { FocusRequester.Cancel } }) { content() }
    }
}

// ══════════════════════════════════════════════════════════════════
//  TOP BAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvTopBar(state: IptvState, backFR: FocusRequester, onBack: () -> Unit, onEvent: (IptvEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent))).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IptvCircleButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, Modifier.focusRequester(backFR), IPTV_WHITE, 40.dp)
        LiveDot()
        Text("Lumina TV", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.weight(1f))
        state.playlists.firstOrNull { it.isActive }?.let { active ->
            IptvPillButton("Refresh", Icons.Default.Refresh, IPTV_WHITE, { onEvent(IptvEvent.RefreshCurrentPlaylist) })
        }
        IptvPillButton("Add M3U", Icons.Default.Add, IPTV_BLUE, { onEvent(IptvEvent.ShowAddPlaylist) })
    }
}

@Composable
private fun IptvCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = IPTV_WHITE, size: Dp = 40.dp) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x26FFFFFF), focusedContainerColor = IPTV_CARD_FOCUS, contentColor = tint, focusedContentColor = IPTV_WHITE), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

@Composable
private fun IptvPillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)), colors = ClickableSurfaceDefaults.colors(containerColor = color.copy(0.2f), focusedContainerColor = color, contentColor = color, focusedContentColor = IPTV_WHITE), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(36.dp)) {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "d")
    Box(Modifier.size(8.dp).alpha(alpha).background(IPTV_LIVE, CircleShape))
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL TOOLBAR & SEARCH
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelToolbar(sortMode: ChannelSortMode, isGridView: Boolean, onSort: (ChannelSortMode) -> Unit, onToggleGrid: () -> Unit) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Surface(onClick = { showSortMenu = !showSortMenu }, shape = ClickableSurfaceDefaults.shape(CircleShape), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_DIM, focusedContentColor = IPTV_BG), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(30.dp)) {
                Row(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(14.dp))
                    Text(sortMode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(onClick = onToggleGrid, shape = ClickableSurfaceDefaults.shape(CircleShape), colors = ClickableSurfaceDefaults.colors(containerColor = if (isGridView) IPTV_BLUE.copy(0.3f) else Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = if (isGridView) IPTV_BLUE else IPTV_DIM, focusedContentColor = IPTV_BG), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(30.dp)) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, null, Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun GroupTabRow(groups: List<String>, selectedGroup: String, onSelectGroup: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(groups) { _, group ->
            val isSel = group == selectedGroup
            Surface(onClick = { onSelectGroup(group) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)), colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) IPTV_WHITE else Color(0x1AFFFFFF), focusedContainerColor = IPTV_CARD_FOCUS, contentColor = if (isSel) IPTV_BG else IPTV_DIM, focusedContentColor = IPTV_WHITE), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(34.dp)) {
                Box(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), Alignment.Center) { Text(group, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun IptvSearchBox(query: String, focusManager: FocusManager, onQueryChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(40.dp).clip(RoundedCornerShape(50)).background(if (isFocused) IPTV_CARD_FOCUS else Color(0x1AFFFFFF)).border(width = if (isFocused) 1.5.dp else 0.dp, color = if (isFocused) IPTV_WHITE else Color.Transparent, shape = RoundedCornerShape(50)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = if (isFocused) IPTV_WHITE else IPTV_DIM)
        BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true, textStyle = TextStyle(color = IPTV_WHITE, fontSize = 14.sp), cursorBrush = SolidColor(IPTV_WHITE), decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search channels...", color = IPTV_MUTED, fontSize = 14.sp); inner() } }, modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused }.onPreviewKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && ev.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_DOWN) { focusManager.moveFocus(FocusDirection.Down); true } else false })
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL LIST (PREMIUM LOOK)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelList(channels: List<IptvChannel>, currentChannel: IptvChannel?, favorites: Set<String>, epgData: Map<String, List<EpgProgram>>, channelLogos: Map<String, String>, channelListFR: FocusRequester, onSelectChannel: (IptvChannel) -> Unit, onToggleFavorite: (String) -> Unit, onShowQr: (IptvChannel) -> Unit) {
    if (channels.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No channels found", color = IPTV_MUTED, fontSize = 14.sp) }; return }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().focusRequester(channelListFR)) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val resolvedLogo = channel.logoUrl.ifBlank { channelLogos[channel.tvgId.lowercase()] ?: channelLogos[channel.id.lowercase()] ?: "" }
            val nowProgram = run { val epg = epgData[channel.tvgId.lowercase()] ?: epgData[channel.id.lowercase()]; epg?.firstOrNull { it.isLiveNow } }
            ChannelRow(channel.copy(logoUrl = resolvedLogo), currentChannel?.id == channel.id, channel.id in favorites, nowProgram, if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier, { onSelectChannel(channel) }, { onToggleFavorite(channel.id) }, { onShowQr(channel) })
        }
    }
}

@Composable
private fun ChannelGrid(channels: List<IptvChannel>, currentChannel: IptvChannel?, channelListFR: FocusRequester, onSelectChannel: (IptvChannel) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            Surface(onClick = { onSelectChannel(channel) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (currentChannel?.id == channel.id) IPTV_WHITE.copy(0.1f) else Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.aspectRatio(1.4f).then(if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier)) {
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (channel.logoUrl.isNotBlank()) { AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(8.dp)) }
                    Text(channel.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: IptvChannel, isSelected: Boolean, isFavorite: Boolean, currentProgram: EpgProgram?, modifier: Modifier = Modifier, onSelect: () -> Unit, onFavorite: () -> Unit, onQr: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(onClick = onSelect, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) IPTV_WHITE.copy(0.15f) else Color.Transparent, focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f), modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { isFocused = it.isFocused }.onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown) { when (ev.key.nativeKeyCode) { KeyEvent.KEYCODE_F -> { onFavorite(); true }; KeyEvent.KEYCODE_Q -> { onQr(); true }; else -> false } } else false }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${channel.number}", color = if (isFocused) IPTV_BG else IPTV_MUTED, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (isFocused) Color.Black.copy(0.1f) else Color(0x26FFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank()) AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                else Text(channel.name.take(2).uppercase(), color = if (isFocused) IPTV_BG else IPTV_WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(channel.name, color = if (isFocused) IPTV_BG else IPTV_WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (currentProgram != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(currentProgram.title, color = if (isFocused) IPTV_BG.copy(0.8f) else IPTV_DIM, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else Text(channel.groupTitle, color = if (isFocused) IPTV_BG.copy(0.6f) else IPTV_MUTED, fontSize = 12.sp)
            }
            if (currentProgram != null && !isFocused) {
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.End) {
                    LiveDot()
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.2f))) { Box(Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight().background(IPTV_LIVE)) }
                }
            }
            if (isFocused || isFavorite) Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(16.dp), tint = if (isFocused && !isFavorite) IPTV_BG else IPTV_RED)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NOW PLAYING PANEL (PREMIUM)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun NowPlayingPanel(state: IptvState, onToggleFavorite: () -> Unit, onShowQr: () -> Unit, onShowEpg: () -> Unit, onShowFullScreen: () -> Unit, onSleepTimer: () -> Unit, onToggleRecording: () -> Unit, onSettings: () -> Unit) {
    val ch = state.currentChannel ?: return
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val cp = state.currentProgram; val np = state.nextProgram
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF1E1E2E), Color(0xFF11111E)))).padding(24.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x26FFFFFF)), Alignment.Center) {
                    val logo = ch.logoUrl.ifBlank { state.channelLogos[ch.tvgId.lowercase()] ?: state.channelLogos[ch.id.lowercase()] ?: "" }
                    if (logo.isNotBlank()) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
                    else Text(ch.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("● LIVE", color = IPTV_WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                        Text(ch.groupTitle, color = IPTV_DIM, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(ch.name, color = IPTV_WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IptvActionBtn(Icons.Default.Fullscreen, IPTV_WHITE, onShowFullScreen)
                    IptvActionBtn(if (ch.id in state.favoriteChannelIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (ch.id in state.favoriteChannelIds) IPTV_RED else IPTV_DIM, onToggleFavorite)
                    IptvActionBtn(Icons.Default.CalendarViewWeek, IPTV_GOLD, onShowEpg)
                }
            }
            if (cp != null) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x12FFFFFF)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (cp.posterUrl.isNotBlank()) AsyncImage(model = cp.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).height(85.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("NOW", color = IPTV_LIVE, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(cp.title, color = IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tf.format(Date(cp.startTime))} – ${tf.format(Date(cp.endTime))}", color = IPTV_DIM, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x33FFFFFF))) { Box(Modifier.fillMaxWidth(cp.progressFraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(IPTV_RED, IPTV_LIVE)))) }
                    }
                    if (np != null) {
                        Box(Modifier.width(1.dp).height(85.dp).background(IPTV_DIVIDER))
                        Column(Modifier.weight(0.7f)) {
                            Text("NEXT", color = IPTV_MUTED, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(np.title, color = IPTV_DIM, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(tf.format(Date(np.startTime)), color = IPTV_MUTED, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape), colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x26FFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = tint, focusedContentColor = IPTV_BG), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EPG TIMELINE
// ══════════════════════════════════════════════════════════════════
@Composable
private fun EpgTimeline(programs: List<EpgProgram>, epgLoadState: IptvLoadState, dayOffset: Int, onDayChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val df = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TV Guide", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in -1..2) {
                    val lbl = when (d) { 0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"; else -> df.format(Date(now + d * 86_400_000L)).take(5) }
                    Surface(onClick = { onDayChange(d) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)), colors = ClickableSurfaceDefaults.colors(containerColor = if (dayOffset == d) IPTV_WHITE else Color(0x1AFFFFFF), focusedContainerColor = IPTV_CARD_FOCUS, contentColor = if (dayOffset == d) IPTV_BG else IPTV_DIM, focusedContentColor = IPTV_WHITE), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(32.dp)) {
                        Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.Center) { Text(lbl, fontSize = 12.sp, fontWeight = if (dayOffset == d) FontWeight.Bold else FontWeight.Medium) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (programs.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(if (epgLoadState is IptvLoadState.Loading) "Loading EPG..." else "No guide data available", color = IPTV_MUTED, fontSize = 14.sp) } }
        else {
            val dS = run { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, dayOffset); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.timeInMillis }
            val dP = programs.filter { it.endTime > dS && it.startTime < dS + 86_400_000L }.sortedBy { it.startTime }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(dP, key = { "${it.startTime}_${it.channelId}" }) { p -> EpgProgramRow(p, tf) } }
        }
    }
}

@Composable
private fun EpgProgramRow(p: EpgProgram, tf: SimpleDateFormat) {
    val iL = p.isLiveNow; val iP = p.isPast
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(when { iL -> IPTV_WHITE.copy(0.1f); iP -> Color.Transparent; else -> Color(0x0AFFFFFF) }).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (iL) LiveDot() else Spacer(Modifier.size(8.dp))
        Column(Modifier.width(64.dp)) {
            Text(tf.format(Date(p.startTime)), color = if (iL) IPTV_LIVE else if (iP) IPTV_MUTED else IPTV_WHITE, fontSize = 14.sp, fontWeight = if (iL) FontWeight.Bold else FontWeight.Normal)
            Text("${p.durationMinutes}m", color = IPTV_MUTED, fontSize = 11.sp)
        }
        Box(Modifier.width(2.dp).height(28.dp).background(if (iL) IPTV_LIVE else IPTV_DIVIDER))
        Column(Modifier.weight(1f)) { Text(p.title, color = if (iP) IPTV_MUTED else IPTV_WHITE, fontSize = 15.sp, fontWeight = if (iL) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (iL) {
            Column(Modifier.width(50.dp), horizontalAlignment = Alignment.End) {
                Text("${(p.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(46.dp).height(4.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.25f))) { Box(Modifier.fillMaxWidth(p.progressFraction).fillMaxHeight().background(IPTV_LIVE)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  OVERLAYS & DIALOGS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FullscreenOverlay(state: IptvState, fullScreenFR: FocusRequester, onChannelUp: () -> Unit, onChannelDown: () -> Unit, onExit: () -> Unit) {
    var showHud by remember { mutableStateOf(true) }
    val ch = state.currentChannel ?: return
    LaunchedEffect(ch.id) { showHud = true; delay(4000); showHud = false }
    Box(Modifier.fillMaxSize().focusRequester(fullScreenFR).focusable().onKeyEvent { ev ->
        if (ev.type == KeyEventType.KeyDown) {
            when (ev.key.nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { onChannelUp(); true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { onChannelDown(); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showHud = true; true }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onExit(); true }
                else -> false
            }
        } else false
    }) {
        AnimatedVisibility(visible = showHud, enter = fadeIn(tween(200)), exit = fadeOut(tween(400)), modifier = Modifier.align(Alignment.BottomStart).zIndex(50f)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))).padding(32.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("● LIVE", color = IPTV_WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                        Text("${ch.number}  ${ch.name}", color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    state.currentProgram?.let { p ->
                        Spacer(Modifier.height(8.dp)); Text(p.title, color = IPTV_DIM, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(300.dp).height(4.dp).clip(CircleShape).background(IPTV_WHITE.copy(0.2f))) { Box(Modifier.fillMaxWidth(p.progressFraction).fillMaxHeight().background(IPTV_LIVE)) }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPlaylistDialog(state: IptvState, focusRequester: FocusRequester, focusManager: FocusManager, onEvent: (IptvEvent) -> Unit) {
    Box(Modifier.width(500.dp).clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE).padding(32.dp)) {
        Column {
            Text("Add Playlist", color = IPTV_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            BasicTextField(value = state.addPlaylistUrl, onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }, textStyle = TextStyle(color = IPTV_WHITE, fontSize = 16.sp), modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AFFFFFF)).padding(14.dp).focusRequester(focusRequester))
            Spacer(Modifier.height(24.dp))
            Surface(onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_BLUE, focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun IptvWelcomePanel(channelCount: Int, playlistName: String, epgStatus: IptvLoadState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(80.dp), tint = IPTV_BLUE.copy(0.4f))
            Text(playlistName.ifEmpty { "Ready to Watch" }, color = IPTV_WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("$channelCount Channels Loaded", color = IPTV_MUTED, fontSize = 16.sp)
        }
    }
}

@Composable
fun IptvEmptyState(loadState: IptvLoadState, addPlaylistFR: FocusRequester, onEvent: (IptvEvent) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Surface(onClick = { onEvent(IptvEvent.ShowAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_BLUE, focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG), modifier = Modifier.height(56.dp).focusRequester(addPlaylistFR)) {
            Box(Modifier.padding(horizontal = 32.dp).fillMaxHeight(), Alignment.Center) { Text("Add M3U Playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SleepTimerIndicator(remainingMs: Long, onDismiss: () -> Unit) {
    Surface(onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_PURPLE.copy(0.15f), contentColor = IPTV_PURPLE), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BedtimeOff, null, Modifier.size(14.dp))
            Text("Sleep: ${remainingMs / 60000}m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}