@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.iptv

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG          = Color(0xFF04040A)
private val SURFACE     = Color(0x8811111E)
private val SURFACE2    = Color(0x551A1A2E)
private val CARD_FOCUS  = Color(0xFF007AFF)
private val RED         = Color(0xFFE50914)
private val LIVE        = Color(0xFFFF2A2A)
private val BLUE        = Color(0xFF0A84FF)
private val GREEN       = Color(0xFF30D158)
private val GOLD        = Color(0xFFFFD60A)
private val PURPLE      = Color(0xFFBF5AF2)
private val ORANGE      = Color(0xFFFF9F0A)
private val WHITE       = Color(0xFFFFFFFF)
private val DIM         = Color(0xB3FFFFFF)
private val MUTED       = Color(0x66FFFFFF)
private val DIVIDER     = Color(0x14FFFFFF)

// ══════════════════════════════════════════════════════════════════
//  ROOT SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvScreen(viewModel: IptvViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val exo = remember { ExoPlayerWrapper(context) }
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()
    var isFullScreen by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val backFR         = remember { FocusRequester() }
    val channelListFR  = remember { FocusRequester() }
    val addPlaylistFR  = remember { FocusRequester() }
    val fullScreenFR   = remember { FocusRequester() }

    fun toast(msg: String) { showToast = msg; scope.launch { delay(2500); showToast = "" } }

    DisposableEffect(Unit) { onDispose { exo.release() } }
    LaunchedEffect(Unit) { delay(200); runCatching { backFR.requestFocus() } }

    BackHandler {
        when {
            state.showSettings        -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showParentalPinEntry-> viewModel.onEvent(IptvEvent.DismissParentalPin)
            state.showSleepTimerPicker-> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            state.showMultiView       -> viewModel.onEvent(IptvEvent.ToggleMultiView)
            state.showQrCode          -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showAddPlaylist     -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showEpgGuide        -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            isFullScreen              -> isFullScreen = false
            else                      -> onNavigateBack()
        }
    }

    LaunchedEffect(state.currentChannel) {
        state.currentChannel?.let { ch -> exo.prepareStream(ch.streamUrl); exo.play() } ?: exo.pause()
    }
    LaunchedEffect(isFullScreen) {
        delay(120)
        runCatching { if (isFullScreen) fullScreenFR.requestFocus() else channelListFR.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

        // ── Video layer ──
        if (state.currentChannel != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().focusable(false),
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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

        // ── Main UI ──
        AnimatedVisibility(!isFullScreen, enter = fadeIn(), exit = fadeOut()) {
            val bgAlpha = if (state.currentChannel != null) 0.88f else 1f
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(BG.copy(bgAlpha), Color.Black.copy(bgAlpha + 0.08f))))) {
                Column(Modifier.fillMaxSize()) {
                    IptvTopBar(state, backFR, onNavigateBack, viewModel::onEvent)
                    if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                        IptvEmptyState(state, addPlaylistFR, viewModel::onEvent)
                    } else {
                        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // ── Left panel ──
                            Column(Modifier.weight(0.32f).fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp)).background(SURFACE).padding(vertical = 12.dp)) {
                                GroupTabRow(state.groups, state.selectedGroup) { viewModel.onEvent(IptvEvent.SelectGroup(it)) }
                                IptvSearchBox(state.searchQuery, focusManager) { viewModel.onEvent(IptvEvent.UpdateSearch(it)) }
                                ChannelToolbar(
                                    state.channelSortMode, state.showChannelGrid,
                                    { viewModel.onEvent(IptvEvent.SetChannelSort(it)) },
                                    { viewModel.onEvent(IptvEvent.ToggleChannelGrid) }
                                )
                                // FEATURE 1 – channel count badge
                                ChannelCountBadge(state.filteredChannels.size, state.channels.size)
                                if (state.showChannelGrid) {
                                    ChannelGrid(state.filteredChannels, state.currentChannel, channelListFR) {
                                        viewModel.onEvent(IptvEvent.SelectChannel(it)); isFullScreen = true
                                    }
                                } else {
                                    ChannelList(
                                        channels       = state.filteredChannels,
                                        currentChannel = state.currentChannel,
                                        favorites      = state.favoriteChannelIds,
                                        epgData        = state.epgData,
                                        channelLogos   = state.channelLogos,
                                        channelListFR  = channelListFR,
                                        onSelectChannel = { ch ->
                                            if (state.currentChannel?.id == ch.id) isFullScreen = true
                                            else { viewModel.onEvent(IptvEvent.SelectChannel(ch)); isFullScreen = true }
                                        },
                                        onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(it)) },
                                        onShowQr         = { viewModel.onEvent(IptvEvent.ShowQrCode(it)) }
                                    )
                                }
                            }
                            // ── Right panel ──
                            Column(Modifier.weight(0.68f).fillMaxHeight()) {
                                if (state.currentChannel != null) {
                                    NowPlayingPanel(
                                        state           = state,
                                        onToggleFav     = { viewModel.onEvent(IptvEvent.ToggleFavorite(state.currentChannel!!.id)) },
                                        onShowQr        = { viewModel.onEvent(IptvEvent.ShowQrCode(state.currentChannel!!)) },
                                        onShowEpg       = { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                                        onFullScreen    = { isFullScreen = true },
                                        onSleepTimer    = { viewModel.onEvent(IptvEvent.ShowSleepTimerPicker) },
                                        onRecording     = { viewModel.onEvent(IptvEvent.ToggleRecording) },
                                        onSettings      = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                                        onSubtitles     = { viewModel.onEvent(IptvEvent.ToggleSubtitles) },
                                        onToast         = ::toast
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    val epgPrograms = remember(state.epgData, state.currentChannel?.id, state.epgDayOffset) {
                                        viewModel.getEpgForChannel(state.currentChannel!!, state.epgData)
                                    }
                                    EpgTimeline(epgPrograms, state.epgLoadState, state.epgDayOffset,
                                        { viewModel.onEvent(IptvEvent.SetEpgDayOffset(it)) }, Modifier.weight(1f))
                                } else {
                                    IptvWelcomePanel(
                                        channelCount = state.channels.size,
                                        playlistName = state.playlists.firstOrNull { it.isActive }?.name ?: "",
                                        epgStatus    = state.epgLoadState,
                                        modifier     = Modifier.weight(1f)
                                    )
                                }
                                // FEATURE 2 – sleep timer bar
                                if (state.sleepTimer != SleepTimer.OFF && state.sleepTimerRemainingMs > 0) {
                                    SleepTimerIndicator(state.sleepTimerRemainingMs) {
                                        viewModel.onEvent(IptvEvent.DismissSleepTimer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Fullscreen ──
        if (isFullScreen) {
            FullscreenOverlay(
                state        = state,
                fullScreenFR = fullScreenFR,
                onChannelUp   = { val i = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }; viewModel.onEvent(IptvEvent.ChannelUp(i)) },
                onChannelDown = { val i = state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id }; viewModel.onEvent(IptvEvent.ChannelDown(i)) },
                onExit        = { isFullScreen = false },
                onShowEpg     = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }
            )
        }

        // ── Dialogs ──
        if (state.loadState is IptvLoadState.Loading) {
            IptvDialog({}) { Box(Modifier.fillMaxSize(), Alignment.Center) {
                com.luminastreams.tv.ui.components.LoadingIndicator() } }
        }
        if (state.showAddPlaylist) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideAddPlaylist) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    AddPlaylistDialog(state, addPlaylistFR, focusManager, viewModel::onEvent)
                }
            }
        }
        if (state.showQrCode && state.qrCodeChannel != null) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideQrCode) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ChannelQrDialog(state.qrCodeChannel!!) { viewModel.onEvent(IptvEvent.HideQrCode) }
                }
            }
        }
        if (state.showEpgGuide) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideEpgGuide) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    FullEpgGuideDialog(state, viewModel)
                }
            }
        }
        if (state.showSettings) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideIptvSettings) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    IptvSettingsDialog(state, viewModel::onEvent)
                }
            }
        }
        if (state.showSleepTimerPicker) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideSleepTimerPicker) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    SleepTimerDialog(state.sleepTimer) { viewModel.onEvent(IptvEvent.SetSleepTimer(it)) }
                }
            }
        }
        if (state.showParentalPinEntry) {
            IptvDialog({ viewModel.onEvent(IptvEvent.DismissParentalPin) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ParentalPinDialog { viewModel.onEvent(IptvEvent.EnterParentalPin(it)) }
                }
            }
        }

        // FEATURE 3 – toast overlay
        AnimatedVisibility(showToast.isNotEmpty(), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).zIndex(200f)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(WHITE.copy(0.12f)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(showToast, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  DIALOG WRAPPER
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.88f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
            .focusGroup().focusRestorer()
            .focusProperties { exit = { FocusRequester.Cancel } }) { content() }
    }
}

// ══════════════════════════════════════════════════════════════════
//  TOP BAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvTopBar(state: IptvState, backFR: FocusRequester, onBack: () -> Unit, onEvent: (IptvEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp)
        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
        .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        IptvCircleBtn(Icons.AutoMirrored.Filled.ArrowBack, onBack, Modifier.focusRequester(backFR))
        LiveDot()
        Text("Lumina TV", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

        // FEATURE 4 – EPG status pill
        EpgStatusPill(state.epgLoadState)

        Spacer(Modifier.weight(1f))

        // FEATURE 5 – recording indicator
        if (state.isRecording) {
            RecordingPill()
        }

        state.playlists.firstOrNull { it.isActive }?.let {
            IptvPillBtn("Refresh", Icons.Default.Refresh, WHITE) { onEvent(IptvEvent.RefreshCurrentPlaylist) }
        }
        IptvPillBtn("Add M3U", Icons.Default.Add, BLUE) { onEvent(IptvEvent.ShowAddPlaylist) }
        IptvCircleBtn(Icons.Default.Settings, { onEvent(IptvEvent.ShowIptvSettings) })
    }
}

@Composable
private fun EpgStatusPill(epgState: IptvLoadState) {
    val (label, color) = when (epgState) {
        is IptvLoadState.Loading -> "EPG Loading…" to GOLD
        is IptvLoadState.Success -> "EPG ✓" to GREEN
        is IptvLoadState.Error   -> "EPG ✗" to RED
        else -> return
    }
    val inf = rememberInfiniteTransition(label = "epg")
    val alpha by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(0.15f))
        .alpha(if (epgState is IptvLoadState.Loading) alpha else 1f)
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordingPill() {
    val inf = rememberInfiniteTransition(label = "rec")
    val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "r")
    Box(Modifier.clip(RoundedCornerShape(50)).background(RED.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).alpha(alpha).background(RED, CircleShape))
            Text("REC", color = RED, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL COUNT BADGE  (FEATURE 1)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelCountBadge(filtered: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$filtered", color = BLUE, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("/ $total channels", color = MUTED, fontSize = 11.sp)
    }
}

// ══════════════════════════════════════════════════════════════════
//  TOOLBAR / GROUPS / SEARCH
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelToolbar(sortMode: ChannelSortMode, isGridView: Boolean, onSort: (ChannelSortMode) -> Unit, onToggleGrid: () -> Unit) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Surface(onClick = { showSortMenu = !showSortMenu },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = DIM, focusedContentColor = BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(30.dp)) {
                Row(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(14.dp))
                    Text(sortMode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (showSortMenu) {
                Box(Modifier.padding(top = 34.dp).zIndex(100f)) {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C2E)).padding(8.dp)) {
                        ChannelSortMode.entries.forEach { mode ->
                            Surface(onClick = { onSort(mode); showSortMenu = false },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = if (sortMode == mode) BLUE.copy(0.2f) else Color.Transparent, focusedContainerColor = BLUE, contentColor = if (sortMode == mode) BLUE else DIM, focusedContentColor = WHITE),
                                modifier = Modifier.fillMaxWidth().height(36.dp)) {
                                Box(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), Alignment.CenterStart) {
                                    Text(mode.label, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(onClick = onToggleGrid,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = if (isGridView) BLUE.copy(0.3f) else Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = if (isGridView) BLUE else DIM, focusedContentColor = BG),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(30.dp)) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, null, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun GroupTabRow(groups: List<String>, selectedGroup: String, onSelectGroup: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(groups) { _, group ->
            val isSel = group == selectedGroup
            // FEATURE 6 – group icons
            val icon = when (group) {
                "All"       -> Icons.Default.LiveTv
                "Favorites" -> Icons.Default.Favorite
                "Recent"    -> Icons.Default.History
                else        -> null
            }
            Surface(onClick = { onSelectGroup(group) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) WHITE else Color(0x1AFFFFFF), focusedContainerColor = CARD_FOCUS, contentColor = if (isSel) BG else DIM, focusedContentColor = WHITE),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(34.dp)) {
                Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (icon != null) Icon(icon, null, Modifier.size(14.dp))
                    Text(group, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun IptvSearchBox(query: String, focusManager: FocusManager, onQueryChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(40.dp)
        .clip(RoundedCornerShape(50))
        .background(if (isFocused) CARD_FOCUS else Color(0x1AFFFFFF))
        .border(if (isFocused) 1.5.dp else 0.dp, if (isFocused) WHITE else Color.Transparent, RoundedCornerShape(50))
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = if (isFocused) WHITE else DIM)
        BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
            textStyle = TextStyle(color = WHITE, fontSize = 14.sp), cursorBrush = SolidColor(WHITE),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search channels…", color = MUTED, fontSize = 14.sp); inner() } },
            modifier = Modifier.weight(1f)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        focusManager.moveFocus(FocusDirection.Down); true
                    } else false
                })
        if (query.isNotEmpty()) {
            Icon(Icons.Default.Close, null, Modifier.size(16.dp).clickable { onQueryChange("") }, tint = if (isFocused) WHITE else DIM)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL LIST & GRID
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelList(channels: List<IptvChannel>, currentChannel: IptvChannel?, favorites: Set<String>,
    epgData: Map<String, List<EpgProgram>>, channelLogos: Map<String, String>, channelListFR: FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit, onToggleFavorite: (String) -> Unit, onShowQr: (IptvChannel) -> Unit) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No channels found", color = MUTED, fontSize = 14.sp) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().focusRequester(channelListFR)) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val resolvedLogo = channel.logoUrl.ifBlank {
                channelLogos[channel.tvgId.lowercase()] ?: channelLogos[channel.id.lowercase()] ?: ""
            }
            val nowProgram = run {
                val epg = epgData[channel.tvgId.lowercase()] ?: epgData[channel.id.lowercase()]
                epg?.firstOrNull { it.isLiveNow }
            }
            ChannelRow(
                channel    = channel.copy(logoUrl = resolvedLogo),
                isSelected = currentChannel?.id == channel.id,
                isFavorite = channel.id in favorites,
                currentProgram = nowProgram,
                modifier   = if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier,
                onSelect   = { onSelectChannel(channel) },
                onFavorite = { onToggleFavorite(channel.id) },
                onQr       = { onShowQr(channel) }
            )
        }
    }
}

@Composable
private fun ChannelGrid(channels: List<IptvChannel>, currentChannel: IptvChannel?,
    channelListFR: FocusRequester, onSelectChannel: (IptvChannel) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            Surface(onClick = { onSelectChannel(channel) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = if (currentChannel?.id == channel.id) WHITE.copy(0.12f) else Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.aspectRatio(1.4f).then(if (idx == 0) Modifier.focusRequester(channelListFR) else Modifier)) {
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(channel.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: IptvChannel, isSelected: Boolean, isFavorite: Boolean,
    currentProgram: EpgProgram?, modifier: Modifier, onSelect: () -> Unit, onFavorite: () -> Unit, onQr: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) WHITE.copy(0.14f) else Color.Transparent,
            focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth().height(64.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_F -> { onFavorite(); true }
                        KeyEvent.KEYCODE_Q -> { onQr(); true }
                        else -> false
                    }
                } else false
            }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${channel.number}", color = if (isFocused) BG else MUTED,
                fontSize = 11.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (isFocused) Color.Black.copy(0.1f) else Color(0x22FFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank())
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(30.dp))
                else
                    Text(channel.name.take(2).uppercase(), color = if (isFocused) BG else WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = if (isFocused) BG else WHITE, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentProgram != null) {
                    // FEATURE 7 – EPG progress bar per channel row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(currentProgram.title, color = if (isFocused) BG.copy(0.75f) else DIM,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(channel.groupTitle, color = if (isFocused) BG.copy(0.6f) else MUTED, fontSize = 11.sp)
                }
            }
            // progress micro-bar
            if (currentProgram != null) {
                Column(Modifier.width(36.dp), horizontalAlignment = Alignment.End) {
                    if (!isFocused) LiveDot()
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(32.dp).height(2.dp).clip(CircleShape).background(MUTED.copy(0.2f))) {
                        Box(Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight().background(LIVE))
                    }
                }
            }
            if (isFocused || isFavorite)
                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    null, Modifier.size(14.dp), tint = if (isFocused && !isFavorite) BG else RED)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NOW PLAYING PANEL
// ══════════════════════════════════════════════════════════════════
@Composable
private fun NowPlayingPanel(
    state: IptvState,
    onToggleFav: () -> Unit,
    onShowQr: () -> Unit,
    onShowEpg: () -> Unit,
    onFullScreen: () -> Unit,
    onSleepTimer: () -> Unit,
    onRecording: () -> Unit,
    onSettings: () -> Unit,
    onSubtitles: () -> Unit,
    onToast: (String) -> Unit
) {
    val ch = state.currentChannel ?: return
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val cp = state.currentProgram
    val np = state.nextProgram
    val logo = ch.logoUrl.ifBlank { state.channelLogos[ch.tvgId.lowercase()] ?: state.channelLogos[ch.id.lowercase()] ?: "" }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1E))))
        .padding(22.dp)) {
        Column(Modifier.fillMaxWidth()) {
            // Channel header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)), Alignment.Center) {
                    if (logo.isNotBlank())
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(logo).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(50.dp))
                    else
                        Text(ch.name.take(2).uppercase(), color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(CircleShape).background(LIVE).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("● LIVE", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(ch.groupTitle, color = DIM, fontSize = 12.sp)
                        // FEATURE 8 – resolution badge
                        if (ch.resolution.isNotBlank()) {
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(PURPLE.copy(0.25f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(ch.resolution, color = PURPLE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(ch.name, color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IptvActionBtn(Icons.Default.Fullscreen, WHITE, onFullScreen)
                    IptvActionBtn(if (ch.id in state.favoriteChannelIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (ch.id in state.favoriteChannelIds) RED else DIM, onToggleFav)
                    IptvActionBtn(Icons.Default.CalendarViewWeek, GOLD, onShowEpg)
                    // FEATURE 9 – subtitles toggle button
                    IptvActionBtn(if (state.subtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                        if (state.subtitlesEnabled) BLUE else DIM, onSubtitles)
                    IptvActionBtn(Icons.Default.Timer, ORANGE, onSleepTimer)
                    IptvActionBtn(if (state.isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                        if (state.isRecording) RED else MUTED, onRecording)
                    IptvActionBtn(Icons.Default.QrCode, GREEN, onShowQr)
                    IptvActionBtn(Icons.Default.Settings, MUTED, onSettings)
                }
            }

            // Now/Next program block
            if (cp != null) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0FFFFFFF)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (cp.posterUrl.isNotBlank())
                        AsyncImage(model = cp.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text("NOW", color = LIVE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(cp.title, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tf.format(Date(cp.startTime))} – ${tf.format(Date(cp.endTime))}", color = DIM, fontSize = 11.sp)
                        // FEATURE 10 – remaining time label
                        Text("${cp.remainingMinutes}m left", color = MUTED, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
                            Box(Modifier.fillMaxWidth(cp.progressFraction).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(RED, LIVE))))
                        }
                    }
                    if (np != null) {
                        Box(Modifier.width(1.dp).height(80.dp).background(DIVIDER))
                        Column(Modifier.weight(0.65f)) {
                            Text("NEXT", color = MUTED, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(np.title, color = DIM, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(tf.format(Date(np.startTime)), color = MUTED, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = tint, focusedContentColor = BG),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(40.dp)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(18.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EPG TIMELINE
// ══════════════════════════════════════════════════════════════════
@Composable
private fun EpgTimeline(programs: List<EpgProgram>, epgLoadState: IptvLoadState, dayOffset: Int,
    onDayChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val df = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SURFACE).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TV Guide", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in -1..2) {
                    val lbl = when (d) {
                        0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"
                        else -> df.format(Date(now + d * 86_400_000L)).take(5)
                    }
                    Surface(onClick = { onDayChange(d) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (dayOffset == d) WHITE else Color(0x1AFFFFFF), focusedContainerColor = CARD_FOCUS, contentColor = if (dayOffset == d) BG else DIM, focusedContentColor = WHITE),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(30.dp)) {
                        Box(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), Alignment.Center) {
                            Text(lbl, fontSize = 11.sp, fontWeight = if (dayOffset == d) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (epgLoadState is IptvLoadState.Loading) "Loading EPG…" else "No guide data", color = MUTED, fontSize = 14.sp)
            }
        } else {
            val dayStart = run { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, dayOffset); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.timeInMillis }
            val dayPrograms = programs.filter { it.endTime > dayStart && it.startTime < dayStart + 86_400_000L }.sortedBy { it.startTime }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(dayPrograms, key = { "${it.startTime}_${it.channelId}" }) { p -> EpgProgramRow(p, tf) }
            }
        }
    }
}

@Composable
private fun EpgProgramRow(p: EpgProgram, tf: SimpleDateFormat) {
    val isLive = p.isLiveNow; val isPast = p.isPast
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(when { isLive -> WHITE.copy(0.09f); isPast -> Color.Transparent; else -> Color(0x08FFFFFF) })
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isLive) LiveDot() else Spacer(Modifier.size(8.dp))
        Column(Modifier.width(58.dp)) {
            Text(tf.format(Date(p.startTime)), color = if (isLive) LIVE else if (isPast) MUTED else WHITE, fontSize = 13.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
            Text("${p.durationMinutes}m", color = MUTED, fontSize = 10.sp)
        }
        Box(Modifier.width(2.dp).height(24.dp).background(if (isLive) LIVE else DIVIDER))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = if (isPast) MUTED else WHITE, fontSize = 14.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // FEATURE 11 – EPG category tag
            if (p.category.isNotBlank()) Text(p.category, color = MUTED, fontSize = 10.sp, maxLines = 1)
        }
        if (isLive) {
            Column(Modifier.width(46.dp), horizontalAlignment = Alignment.End) {
                Text("${(p.progressFraction * 100).toInt()}%", color = LIVE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(42.dp).height(3.dp).clip(CircleShape).background(MUTED.copy(0.2f))) {
                    Box(Modifier.fillMaxWidth(p.progressFraction).fillMaxHeight().background(LIVE))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FULLSCREEN OVERLAY
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FullscreenOverlay(state: IptvState, fullScreenFR: FocusRequester,
    onChannelUp: () -> Unit, onChannelDown: () -> Unit, onExit: () -> Unit, onShowEpg: () -> Unit) {
    var showHud by remember { mutableStateOf(true) }
    val ch = state.currentChannel ?: return
    LaunchedEffect(ch.id) { showHud = true; delay(5000); showHud = false }

    Box(Modifier.fillMaxSize().focusRequester(fullScreenFR).focusable()
        .onKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown) {
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP    -> { onChannelUp(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { onChannelDown(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER       -> { showHud = true; true }
                    KeyEvent.KEYCODE_G                                         -> { onShowEpg(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE             -> { onExit(); true }
                    else -> false
                }
            } else false
        }) {
        AnimatedVisibility(showHud,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.BottomStart).zIndex(50f)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))).padding(32.dp)) {
                Column {
                    // FEATURE 12 – channel logo in fullscreen HUD
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val logo = ch.logoUrl.ifBlank { state.channelLogos[ch.tvgId.lowercase()] ?: "" }
                        if (logo.isNotBlank()) {
                            AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.clip(CircleShape).background(LIVE).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text("● LIVE", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Text("${ch.number}  ${ch.name}", color = WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    state.currentProgram?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        Text(p.title, color = DIM, fontSize = 15.sp)
                        // FEATURE 13 – progress bar in fullscreen HUD
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.width(260.dp).height(4.dp).clip(CircleShape).background(WHITE.copy(0.2f))) {
                                Box(Modifier.fillMaxWidth(p.progressFraction).fillMaxHeight().background(LIVE))
                            }
                            Text("${p.remainingMinutes}m left", color = MUTED, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("▲▼ Change channel   G = Guide   Back = Exit", color = MUTED, fontSize = 11.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  ADD PLAYLIST DIALOG  (FEATURE 14 – QR scan for TV)
// ══════════════════════════════════════════════════════════════════
@Composable
fun AddPlaylistDialog(state: IptvState, focusRequester: FocusRequester, focusManager: FocusManager, onEvent: (IptvEvent) -> Unit) {
    var activeTab by remember { mutableStateOf(0) } // 0=Manual, 1=QR
    Box(Modifier.width(560.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF12121E)).padding(32.dp)) {
        Column {
            Text("Add Playlist", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))

            // Tab row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Manual Entry", "QR / Phone").forEachIndexed { idx, label ->
                    Surface(onClick = { activeTab = idx },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (activeTab == idx) BLUE else Color(0x1AFFFFFF), focusedContainerColor = BLUE, contentColor = WHITE, focusedContentColor = WHITE),
                        modifier = Modifier.height(36.dp)) {
                        Box(Modifier.padding(horizontal = 20.dp).fillMaxHeight(), Alignment.Center) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            if (activeTab == 0) {
                // ── Manual entry ──
                DialogLabel("Playlist Name")
                DialogInput(state.addPlaylistName, "e.g. My IPTV", focusRequester) { onEvent(IptvEvent.UpdateAddPlaylistName(it)) }
                Spacer(Modifier.height(14.dp))
                DialogLabel("M3U / M3U8 URL *")
                DialogInput(state.addPlaylistUrl, "http://…/playlist.m3u8") { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
                Spacer(Modifier.height(14.dp))
                DialogLabel("EPG URL (optional)")
                DialogInput(state.addPlaylistEpgUrl, "http://…/epg.xml.gz") { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) }
                Spacer(Modifier.height(24.dp))
                Surface(onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = BLUE, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            } else {
                // ── QR / Phone transfer ──
                QrAddSection(state.localIpAddress)
            }
        }
    }
}

@Composable
private fun QrAddSection(localIp: String) {
    val url = if (localIp.isNotBlank()) "http://$localIp:8080" else ""
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (url.isNotBlank()) {
            val qrBitmap = remember(url) { QrCodeGenerator.generate(url, 320) }
            Text("Scan with your phone to send the playlist URL directly to this TV",
                color = DIM, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(200.dp).clip(RoundedCornerShape(16.dp)).background(WHITE).padding(12.dp)) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Text(url, color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Keep this screen open. The TV will load your playlist automatically once submitted.",
                color = MUTED, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Icon(Icons.Default.WifiOff, null, Modifier.size(48.dp), tint = MUTED)
            Spacer(Modifier.height(12.dp))
            Text("No network connection detected", color = MUTED, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, color = DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DialogInput(value: String, hint: String, focusRequester: FocusRequester? = null, onValueChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = WHITE, fontSize = 14.sp), cursorBrush = SolidColor(WHITE),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isFocused) Color(0xFF1E1E3A) else Color(0x1AFFFFFF))
                .border(if (isFocused) 1.5.dp else 0.dp, if (isFocused) BLUE else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = MUTED, fontSize = 14.sp)
                    inner()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it })
}

// ══════════════════════════════════════════════════════════════════
//  CHANNEL QR DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelQrDialog(channel: IptvChannel, onDismiss: () -> Unit) {
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 320) }
    Box(Modifier.width(380.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF12121E)).padding(28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Stream QR Code", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(channel.name, color = DIM, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(190.dp).clip(RoundedCornerShape(14.dp)).background(WHITE).padding(10.dp)) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Text("Scan to open stream on phone", color = MUTED, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Surface(onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Close", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FULL EPG GUIDE DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FullEpgGuideDialog(state: IptvState, viewModel: IptvViewModel) {
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var selectedCh by remember { mutableStateOf(state.currentChannel) }
    val programs = remember(selectedCh, state.epgData) {
        if (selectedCh != null) viewModel.getEpgForChannel(selectedCh!!, state.epgData) else emptyList()
    }
    Box(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF0E0E1C)).padding(24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TV Guide", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                // FEATURE 15 – refresh EPG button in guide
                Surface(onClick = { viewModel.onEvent(IptvEvent.RefreshEpg) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = GOLD.copy(0.15f), focusedContainerColor = GOLD, contentColor = GOLD, focusedContentColor = BG),
                    modifier = Modifier.height(32.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                        Text("Refresh EPG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Channel list
                LazyColumn(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.channels, key = { it.id }) { ch ->
                        val isSel = ch.id == selectedCh?.id
                        Surface(onClick = { selectedCh = ch },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) WHITE.copy(0.12f) else Color.Transparent, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                            modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val logo = ch.logoUrl.ifBlank { state.channelLogos[ch.tvgId.lowercase()] ?: "" }
                                if (logo.isNotBlank())
                                    AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
                                else
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x22FFFFFF)), Alignment.Center) {
                                        Text(ch.name.take(2).uppercase(), color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                Text(ch.name, color = WHITE, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                // Program list
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(SURFACE)) {
                    if (programs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No guide data", color = MUTED, fontSize = 14.sp) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(programs.sortedBy { it.startTime }, key = { "${it.startTime}_${it.channelId}" }) { p ->
                                EpgProgramRow(p, tf)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SETTINGS DIALOG  (10 new features)
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvSettingsDialog(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    var section by remember { mutableStateOf(0) }
    val sections = listOf("Playlists", "Playback", "EPG", "Parental", "Display")

    Box(Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.85f).clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF0E0E1C)).padding(28.dp)) {
        Column {
            Text("Settings", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            // Section tabs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(sections) { idx, label ->
                    Surface(onClick = { section = idx },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (section == idx) WHITE else Color(0x1AFFFFFF), focusedContainerColor = CARD_FOCUS, contentColor = if (section == idx) BG else DIM, focusedContentColor = WHITE),
                        modifier = Modifier.height(34.dp)) {
                        Box(Modifier.padding(horizontal = 18.dp).fillMaxHeight(), Alignment.Center) {
                            Text(label, fontSize = 13.sp, fontWeight = if (section == idx) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.weight(1f)) {
                when (section) {
                    0 -> SettingsPlaylists(state, onEvent)
                    1 -> SettingsPlayback(state, onEvent)
                    2 -> SettingsEpg(state, onEvent)
                    3 -> SettingsParental(state, onEvent)
                    4 -> SettingsDisplay(state, onEvent)
                }
            }
        }
    }
}

// SETTINGS FEATURE 1 – Playlist manager (list, switch, delete)
@Composable
private fun SettingsPlaylists(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(onClick = { onEvent(IptvEvent.ShowAddPlaylist) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = BLUE.copy(0.2f), focusedContainerColor = BLUE, contentColor = BLUE, focusedContentColor = WHITE),
                modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Add New Playlist", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        items(state.playlists, key = { it.id }) { pl ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (pl.isActive) WHITE.copy(0.08f) else Color(0x0AFFFFFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pl.isActive) Box(Modifier.size(7.dp).background(GREEN, CircleShape))
                        Text(pl.name, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${pl.channelCount} channels", color = MUTED, fontSize = 12.sp)
                    // SETTINGS FEATURE 2 – EPG URL shown per playlist
                    if (pl.epgUrl.isNotBlank()) Text("EPG: ${pl.epgUrl.take(40)}…", color = MUTED, fontSize = 10.sp)
                }
                if (!pl.isActive) {
                    Surface(onClick = { onEvent(IptvEvent.SelectPlaylist(pl.id)) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = GREEN.copy(0.15f), focusedContainerColor = GREEN, contentColor = GREEN, focusedContentColor = WHITE),
                        modifier = Modifier.height(32.dp)) {
                        Box(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), Alignment.Center) { Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                // SETTINGS FEATURE 3 – edit playlist
                Surface(onClick = { onEvent(IptvEvent.ShowEditPlaylist(pl)) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = GOLD, contentColor = DIM, focusedContentColor = BG),
                    modifier = Modifier.size(32.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) }
                }
                Surface(onClick = { onEvent(IptvEvent.DeletePlaylist(pl.id)) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = RED.copy(0.15f), focusedContainerColor = RED, contentColor = RED, focusedContentColor = WHITE),
                    modifier = Modifier.size(32.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Delete, null, Modifier.size(14.dp)) }
                }
            }
        }
    }
}

// SETTINGS FEATURE 4 – stream quality picker + subtitles + audio track
@Composable
private fun SettingsPlayback(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroup("Stream Quality") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamQuality.entries.forEach { q ->
                    Surface(onClick = { onEvent(IptvEvent.SetStreamQuality(q)) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (state.streamQuality == q) BLUE.copy(0.3f) else Color(0x1AFFFFFF), focusedContainerColor = BLUE, contentColor = if (state.streamQuality == q) BLUE else DIM, focusedContentColor = WHITE),
                        modifier = Modifier.height(34.dp)) {
                        Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.Center) {
                            Text(q.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // SETTINGS FEATURE 5 – subtitles toggle
        SettingsToggleRow("Subtitles / CC", state.subtitlesEnabled, Icons.Default.ClosedCaption, BLUE) {
            onEvent(IptvEvent.ToggleSubtitles)
        }
        // SETTINGS FEATURE 6 – recording toggle
        SettingsToggleRow("Recording", state.isRecording, Icons.Default.FiberManualRecord, RED) {
            onEvent(IptvEvent.ToggleRecording)
        }
    }
}

// SETTINGS FEATURE 7 – EPG management panel
@Composable
private fun SettingsEpg(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsGroup("EPG Status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val (label, color) = when (state.epgLoadState) {
                    is IptvLoadState.Loading -> "Loading…" to GOLD
                    is IptvLoadState.Success -> "Loaded ✓" to GREEN
                    is IptvLoadState.Error   -> "Error ✗" to RED
                    else -> "Not loaded" to MUTED
                }
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(onClick = { onEvent(IptvEvent.RefreshEpg) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = GOLD.copy(0.15f), focusedContainerColor = GOLD, contentColor = GOLD, focusedContentColor = BG),
            modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Text("Force Refresh EPG Now", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        // SETTINGS FEATURE 8 – EPG cache info
        SettingsGroup("Cache Info") {
            val pl = state.playlists.firstOrNull { it.isActive }
            Text(if (pl?.epgUrl?.isNotBlank() == true) "EPG URL: ${pl.epgUrl}" else "No EPG URL set", color = MUTED, fontSize = 12.sp)
            Text("Cache TTL: 4 hours  •  Programs: ${state.epgData.values.sumOf { it.size }}", color = MUTED, fontSize = 12.sp)
        }
    }
}

// SETTINGS FEATURE 9 – Parental lock with PIN setup
@Composable
private fun SettingsParental(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    var pinInput by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsToggleRow("Parental Lock", state.parentalLockEnabled, Icons.Default.Lock, PURPLE) {
            onEvent(IptvEvent.SetParentalLock(!state.parentalLockEnabled, state.parentalPin))
        }
        if (state.parentalLockEnabled) {
            SettingsGroup("Set / Change PIN") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DialogInput(pinInput, "Enter 4-digit PIN") { if (it.length <= 4) pinInput = it }
                    Surface(onClick = { if (pinInput.length == 4) { onEvent(IptvEvent.SetParentalLock(true, pinInput)); pinInput = "" } },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = PURPLE.copy(0.2f), focusedContainerColor = PURPLE, contentColor = PURPLE, focusedContentColor = WHITE),
                        modifier = Modifier.height(46.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), Alignment.Center) { Text("Save PIN", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// SETTINGS FEATURE 10 – Display / UI preferences
@Composable
private fun SettingsDisplay(state: IptvState, onEvent: (IptvEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsToggleRow("Grid View", state.showChannelGrid, Icons.Default.GridView, BLUE) {
            onEvent(IptvEvent.ToggleChannelGrid)
        }
        SettingsGroup("Channel Sort") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelSortMode.entries.forEach { mode ->
                    Surface(onClick = { onEvent(IptvEvent.SetChannelSort(mode)) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (state.channelSortMode == mode) WHITE.copy(0.15f) else Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = if (state.channelSortMode == mode) WHITE else DIM, focusedContentColor = BG),
                        modifier = Modifier.height(32.dp)) {
                        Box(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), Alignment.Center) {
                            Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        SettingsGroup("Active Playlist") {
            val pl = state.playlists.firstOrNull { it.isActive }
            Text(pl?.name ?: "None", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("${pl?.channelCount ?: 0} channels  •  Last updated: ${
                if ((pl?.lastUpdated ?: 0) > 0) SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(pl!!.lastUpdated)) else "N/A"
            }", color = MUTED, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0AFFFFFF)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        content()
    }
}

@Composable
private fun SettingsToggleRow(label: String, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onToggle: () -> Unit) {
    Surface(onClick = onToggle, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x0AFFFFFF), focusedContainerColor = color.copy(0.15f), contentColor = WHITE, focusedContentColor = WHITE),
        modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) color else MUTED)
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            // Toggle pill
            Box(Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                .background(if (enabled) color else Color(0x33FFFFFF))) {
                Box(Modifier.size(20.dp).align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp).clip(CircleShape).background(WHITE))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SLEEP TIMER DIALOG
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SleepTimerDialog(current: SleepTimer, onSelect: (SleepTimer) -> Unit) {
    Box(Modifier.width(380.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF12121E)).padding(28.dp)) {
        Column {
            Text("Sleep Timer", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepTimer.entries.forEach { timer ->
                    Surface(onClick = { onSelect(timer) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (current == timer) PURPLE.copy(0.2f) else Color(0x1AFFFFFF), focusedContainerColor = PURPLE, contentColor = if (current == timer) PURPLE else DIM, focusedContentColor = WHITE),
                        modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                            Text(timer.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (current == timer) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
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
private fun ParentalPinDialog(onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Box(Modifier.width(340.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF12121E)).padding(28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = PURPLE)
            Spacer(Modifier.height(12.dp))
            Text("Enter PIN", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))
            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < pin.length) PURPLE else Color(0x33FFFFFF)))
                }
            }
            Spacer(Modifier.height(16.dp))
            // Numpad
            val keys = listOf("1","2","3","4","5","6","7","8","9","⌫","0","✓")
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(220.dp)) {
                items(keys.size) { i ->
                    val k = keys[i]
                    Surface(onClick = {
                        when (k) {
                            "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            "✓" -> if (pin.length == 4) onSubmit(pin)
                            else -> if (pin.length < 4) pin += k
                        }
                    }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (k == "✓") PURPLE.copy(0.2f) else Color(0x1AFFFFFF),
                            focusedContainerColor = if (k == "✓") PURPLE else WHITE,
                            contentColor = WHITE, focusedContentColor = if (k == "✓") WHITE else BG),
                        modifier = Modifier.height(52.dp)) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(k, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  MISC COMPONENTS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun LiveDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "d")
    Box(Modifier.size(8.dp).alpha(alpha).background(LIVE, CircleShape))
}

@Composable
private fun IptvCircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: Color = WHITE, size: Dp = 40.dp) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = CARD_FOCUS, contentColor = tint, focusedContentColor = WHITE),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

@Composable
private fun IptvPillBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(containerColor = color.copy(0.18f), focusedContainerColor = color, contentColor = color, focusedContentColor = WHITE),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(36.dp)) {
        Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(15.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepTimerIndicator(remainingMs: Long, onDismiss: () -> Unit) {
    val fraction = (remainingMs / 1000f) / 7200f // max 2h
    Surface(onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = PURPLE.copy(0.15f), contentColor = PURPLE),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BedtimeOff, null, Modifier.size(14.dp))
                Text("Sleep in ${remainingMs / 60000}m ${(remainingMs % 60000) / 1000}s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Tap to cancel", color = MUTED, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(MUTED.copy(0.2f))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(PURPLE))
            }
        }
    }
}

@Composable
fun IptvWelcomePanel(channelCount: Int, playlistName: String, epgStatus: IptvLoadState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(SURFACE), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(72.dp), tint = BLUE.copy(0.4f))
            Text(playlistName.ifEmpty { "Ready to Watch" }, color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("$channelCount channels loaded", color = MUTED, fontSize = 15.sp)
            EpgStatusPill(epgStatus)
        }
    }
}

@Composable
fun IptvEmptyState(state: IptvState, addPlaylistFR: FocusRequester, onEvent: (IptvEvent) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(80.dp), tint = MUTED)
            Text("No playlist loaded", color = DIM, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Add an M3U playlist to get started.\nYou can paste a URL or scan a QR code from your phone.", color = MUTED, fontSize = 14.sp, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { onEvent(IptvEvent.ShowAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = BLUE, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                    modifier = Modifier.height(52.dp).focusRequester(addPlaylistFR)) {
                    Row(Modifier.padding(horizontal = 28.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Text("Add M3U Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
