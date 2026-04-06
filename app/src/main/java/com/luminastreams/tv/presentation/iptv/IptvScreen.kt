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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

private val IPTV_BG        = Color(0xFF0A0A0F)
private val IPTV_SURFACE   = Color(0xFF14141E)
private val IPTV_CARD_FOCUS= Color(0xFF222236)
private val IPTV_RED       = Color(0xFFF91851)
private val IPTV_LIVE      = Color(0xFFFF2D55)
private val IPTV_BLUE      = Color(0xFF0A84FF)
private val IPTV_GREEN     = Color(0xFF30D158)
private val IPTV_GOLD      = Color(0xFFFFCC02)
private val IPTV_WHITE     = Color(0xFFFFFFFF)
private val IPTV_DIM       = Color(0xB3FFFFFF)
private val IPTV_MUTED     = Color(0x66FFFFFF)
private val IPTV_DIVIDER   = Color(0x1AFFFFFF)

@Composable
fun IptvScreen(
    viewModel: IptvViewModel,
    onNavigateBack: () -> Unit,
    onPlayChannel: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val exo = remember { ExoPlayerWrapper(context) }
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()

    var isFullScreen by remember { mutableStateOf(false) }

    val backFR = remember { FocusRequester() }
    val firstGroupFR = remember { FocusRequester() }
    val channelListFR = remember { FocusRequester() }
    val addPlaylistFR = remember { FocusRequester() }
    val fullScreenFR = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    LaunchedEffect(Unit) { delay(200); runCatching { backFR.requestFocus() } }

    BackHandler {
        when {
            state.showQrCode     -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showAddPlaylist -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showEpgGuide   -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            isFullScreen         -> isFullScreen = false
            else                 -> onNavigateBack()
        }
    }

    LaunchedEffect(state.currentChannel) {
        state.currentChannel?.let { ch ->
            exo.prepareStream(ch.streamUrl)
            exo.play()
        }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) { delay(100); runCatching { fullScreenFR.requestFocus() } }
        else { delay(100); runCatching { channelListFR.requestFocus() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        if (state.currentChannel != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().focusable(false),
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val surfaceView = SurfaceView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            keepScreenOn = true
                            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: android.view.View) { exo.player.setVideoSurfaceView(this@apply) }
                                override fun onViewDetachedFromWindow(v: android.view.View) { exo.player.clearVideoSurface() }
                            })
                        }
                        addView(surfaceView)
                    }
                },
                update = { arLayout -> if (videoAspectRatio > 0f) arLayout.setAspectRatio(videoAspectRatio) }
            )
        }

        AnimatedVisibility(visible = !isFullScreen, enter = fadeIn(), exit = fadeOut()) {
            val bgAlpha = if (state.currentChannel != null) 0.85f else 1f
            Column(Modifier.fillMaxSize().background(IPTV_BG.copy(alpha = bgAlpha))) {

                IptvTopBar(state = state, backFR = backFR, onBack = onNavigateBack, onEvent = viewModel::onEvent)

                if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                    IptvEmptyState(loadState = state.loadState, addPlaylistFR = addPlaylistFR, onEvent = viewModel::onEvent)
                } else {
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                        Column(
                            Modifier.width(380.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .background(IPTV_SURFACE)
                                .padding(vertical = 12.dp)
                        ) {
                            GroupTabRow(groups = state.groups, selectedGroup = state.selectedGroup, firstGroupFR = firstGroupFR) { viewModel.onEvent(IptvEvent.SelectGroup(it)) }
                            IptvSearchBox(query = state.searchQuery, focusManager = focusManager) { viewModel.onEvent(IptvEvent.UpdateSearch(it)) }
                            ChannelList(
                                channels = state.filteredChannels, currentChannel = state.currentChannel, favorites = state.favoriteChannelIds, epgData = state.epgData,
                                channelListFR = channelListFR, loadState = state.loadState,
                                onSelectChannel = { ch -> if (state.currentChannel?.id == ch.id) isFullScreen = true else { viewModel.onEvent(IptvEvent.SelectChannel(ch)); isFullScreen = true } },
                                onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(it)) }, onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(it)) }
                            )
                        }

                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (state.currentChannel != null) {
                                NowPlayingPanel(
                                    channel = state.currentChannel!!, currentProgram = state.currentProgram, nextProgram = state.nextProgram, isFavorite = state.currentChannel!!.id in state.favoriteChannelIds,
                                    onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(state.currentChannel!!.id)) }, onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(state.currentChannel!!)) }, onShowEpg = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }, onShowFullScreen = { isFullScreen = true }
                                )
                                Spacer(Modifier.height(20.dp))
                                EpgTimeline(programs = viewModel.getEpgForChannel(state.currentChannel!!), channelName = state.currentChannel!!.name, epgLoadState = state.epgLoadState, modifier = Modifier.weight(1f))
                            } else {
                                IptvWelcomePanel(channelCount = state.channels.size, playlistName = state.playlists.firstOrNull { it.isActive }?.name ?: "", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (isFullScreen) {
            var showHud by remember { mutableStateOf(true) }
            LaunchedEffect(state.currentChannel) { showHud = true; delay(3500); showHud = false }

            Box(
                Modifier.fillMaxSize().focusRequester(fullScreenFR).focusable().onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                                val list = state.filteredChannels; val idx = list.indexOfFirst { it.id == state.currentChannel?.id }
                                if (idx > 0) viewModel.onEvent(IptvEvent.SelectChannel(list[idx - 1]))
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                val list = state.filteredChannels; val idx = list.indexOfFirst { it.id == state.currentChannel?.id }
                                if (idx >= 0 && idx < list.size - 1) viewModel.onEvent(IptvEvent.SelectChannel(list[idx + 1]))
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showHud = true; true }
                            else -> false
                        }
                    } else false
                }
            ) {
                AnimatedVisibility(visible = showHud && state.currentChannel != null, enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { 50 }) + fadeOut(), modifier = Modifier.align(Alignment.BottomStart).padding(40.dp).zIndex(50f)) {
                    val ch = state.currentChannel!!
                    Row(modifier = Modifier.clip(CircleShape).background(Color.Black.copy(0.75f)).border(1.dp, Color.White.copy(0.1f), CircleShape).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${ch.number}", color = IPTV_MUTED, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(ch.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // ═══ NATIVE DIALOGS (ABSOLUTE FOCUS TRAPS) ═══

        if (state.loadState is IptvLoadState.Loading) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(0.85f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .focusGroup()
                        .focusRestorer()
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusable(),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.luminastreams.tv.ui.components.LoadingIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading playlist...", color = IPTV_DIM, fontSize = 16.sp)
                    }
                }
            }
        }

        if (state.showAddPlaylist) {
            Dialog(
                onDismissRequest = { viewModel.onEvent(IptvEvent.HideAddPlaylist) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(0.85f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .focusGroup()
                        .focusRestorer()
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusable(),
                    Alignment.Center
                ) {
                    AddPlaylistDialog(state = state, focusRequester = addPlaylistFR, focusManager = focusManager, onEvent = viewModel::onEvent)
                }
            }
        }

        if (state.showQrCode && state.qrCodeChannel != null) {
            Dialog(
                onDismissRequest = { viewModel.onEvent(IptvEvent.HideQrCode) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(0.85f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .focusGroup()
                        .focusRestorer()
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusable(),
                    Alignment.Center
                ) {
                    state.qrCodeChannel?.let { ch -> QrCodeDialog(channel = ch, onDismiss = { viewModel.onEvent(IptvEvent.HideQrCode) }) }
                }
            }
        }

        if (state.showEpgGuide && state.currentChannel != null) {
            Dialog(
                onDismissRequest = { viewModel.onEvent(IptvEvent.HideEpgGuide) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(IPTV_BG)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .focusGroup()
                        .focusRestorer()
                        .focusProperties { exit = { FocusRequester.Cancel } }
                        .focusable()
                ) {
                    state.currentChannel?.let { ch -> EpgFullGuide(channel = ch, programs = viewModel.getEpgForChannel(ch), onDismiss = { viewModel.onEvent(IptvEvent.HideEpgGuide) }) }
                }
            }
        }
    }
}

@Composable
private fun IptvTopBar(state: IptvState, backFR: FocusRequester, onBack: () -> Unit, onEvent: (IptvEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).background(Color.Transparent).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            onClick = onBack, shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_DIM, focusedContentColor = IPTV_BG),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(44.dp).focusRequester(backFR)
        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp)) } }

        LiveIndicatorDot()
        Text("Lumina Streams", color = IPTV_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

        if (state.channels.isNotEmpty()) {
            Box(Modifier.clip(CircleShape).background(IPTV_BLUE.copy(0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("${state.channels.size} ch", color = IPTV_BLUE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        state.playlists.firstOrNull { it.isActive }?.let { active ->
            Surface(
                onClick = { onEvent(IptvEvent.RefreshCurrentPlaylist) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_GREEN.copy(0.15f), focusedContainerColor = IPTV_GREEN, contentColor = IPTV_GREEN, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(40.dp).padding(end = 8.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Text("Refresh", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { onEvent(IptvEvent.ShowEditPlaylist(active)) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_GOLD.copy(0.15f), focusedContainerColor = IPTV_GOLD, contentColor = IPTV_GOLD, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(40.dp).padding(end = 8.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Text("Edit", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(
            onClick = { onEvent(IptvEvent.ShowAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_BLUE.copy(0.15f), focusedContainerColor = IPTV_BLUE, contentColor = IPTV_BLUE, focusedContentColor = IPTV_WHITE),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), modifier = Modifier.height(40.dp)
        ) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Text("Add M3U", fontSize = 14.sp, fontWeight = FontWeight.Bold, softWrap = false)
            }
        }
    }
}

@Composable
private fun LiveIndicatorDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "liveDot")
    Box(Modifier.size(10.dp).alpha(alpha).background(IPTV_LIVE, CircleShape))
}

@Composable
private fun GroupTabRow(groups: List<String>, selectedGroup: String, firstGroupFR: FocusRequester, onSelectGroup: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(groups) { idx, group ->
            val isSel = group == selectedGroup
            val icon = when (group) { "All" -> Icons.Default.LiveTv; "Favorites" -> Icons.Default.Favorite; "Recent" -> Icons.Default.History; else -> null }
            Surface(
                onClick = { onSelectGroup(group) },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) IPTV_RED else Color(0x0CFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = if (isSel) IPTV_WHITE else IPTV_DIM, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(44.dp).let { if (idx == 0) it.focusRequester(firstGroupFR) else it }
            ) {
                Row(Modifier.padding(horizontal = 18.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(icon, null, Modifier.size(16.dp))
                    Text(group, fontSize = 14.sp, fontWeight = FontWeight.Bold, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun IptvSearchBox(query: String, focusManager: FocusManager, onQueryChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(CircleShape)
            .background(if (isFocused) IPTV_CARD_FOCUS else Color(0x0AFFFFFF))
            .border(width = if (isFocused) 2.dp else 1.dp, color = if (isFocused) IPTV_WHITE else Color.Transparent, shape = CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = if (isFocused) IPTV_WHITE else IPTV_MUTED)
        BasicTextField(
            value = query, onValueChange = onQueryChange, singleLine = true, textStyle = TextStyle(color = IPTV_WHITE, fontSize = 15.sp), cursorBrush = SolidColor(IPTV_WHITE),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search channels...", color = IPTV_MUTED, fontSize = 15.sp); inner() } },
            modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused }
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
        if (query.isNotEmpty()) Icon(Icons.Default.Close, null, Modifier.size(20.dp).clickable { onQueryChange("") }, tint = IPTV_MUTED)
    }
}

@Composable
private fun ChannelList(channels: List<IptvChannel>, currentChannel: IptvChannel?, favorites: Set<String>, epgData: Map<String, List<EpgProgram>>, channelListFR: FocusRequester, loadState: IptvLoadState, onSelectChannel: (IptvChannel) -> Unit, onToggleFavorite: (String) -> Unit, onShowQr: (IptvChannel) -> Unit) {
    if (loadState is IptvLoadState.Loading) return // handled by full dialog
    if (channels.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("📺", fontSize = 40.sp); Spacer(Modifier.height(8.dp)); Text("No channels", color = IPTV_MUTED, fontSize = 14.sp) } }; return }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().focusRequester(channelListFR)
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val nowProgram = run { val epg = epgData[channel.tvgId] ?: epgData[channel.id]; epg?.firstOrNull { it.isLive } }
            ChannelRow(
                channel = channel, isSelected = currentChannel?.id == channel.id, isFavorite = channel.id in favorites, currentProgram = nowProgram,
                onSelect = { onSelectChannel(channel) }, onFavorite = { onToggleFavorite(channel.id) }, onQr = { onShowQr(channel) }
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: IptvChannel, isSelected: Boolean, isFavorite: Boolean, currentProgram: EpgProgram?, modifier: Modifier = Modifier, onSelect: () -> Unit, onFavorite: () -> Unit, onQr: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) IPTV_RED.copy(0.15f) else Color(0x08FFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier.fillMaxWidth().height(76.dp).onFocusChanged { isFocused = it.isFocused }.onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown) { when (ev.key.nativeKeyCode) { KeyEvent.KEYCODE_F -> { onFavorite(); true }; KeyEvent.KEYCODE_Q -> { onQr(); true }; else -> false } } else false }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${channel.number}", color = if (isFocused) IPTV_BG else IPTV_MUTED, fontSize = 13.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(if (isFocused) Color.Black.copy(0.1f) else Color(0x14FFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
                else Text(channel.name.take(2).uppercase(), color = if (isFocused) IPTV_BG else IPTV_DIM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = if (isFocused) IPTV_BG else IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentProgram != null) Text(currentProgram.title, color = if (isFocused) IPTV_BG.copy(0.7f) else IPTV_MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Text(channel.groupTitle, color = if (isFocused) IPTV_BG.copy(0.7f) else IPTV_MUTED, fontSize = 13.sp, maxLines = 1)
            }
            if (currentProgram != null && !isFocused) {
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.End) {
                    LiveIndicatorDot()
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.3f))) {
                        Box(Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                    }
                }
            }
            if (isFocused || isFavorite) Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(20.dp), tint = if (isFocused && !isFavorite) IPTV_BG else IPTV_RED)
        }
    }
}

@Composable
private fun NowPlayingPanel(channel: IptvChannel, currentProgram: EpgProgram?, nextProgram: EpgProgram?, isFavorite: Boolean, onToggleFavorite: () -> Unit, onShowQr: () -> Unit, onShowEpg: () -> Unit, onShowFullScreen: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE).padding(24.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x1AFFFFFF)), Alignment.Center) {
                    if (channel.logoUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).addHeader("User-Agent", "Mozilla/5.0").crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(72.dp))
                    else Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("● LIVE", color = IPTV_WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Text(channel.groupTitle, color = IPTV_MUTED, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(channel.name, color = IPTV_WHITE, fontSize = 32.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IptvActionButton(icon = Icons.Default.Fullscreen, tint = IPTV_WHITE, onClick = onShowFullScreen)
                    IptvActionButton(icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tint = if (isFavorite) IPTV_RED else IPTV_DIM, onClick = onToggleFavorite)
                    IptvActionButton(icon = Icons.Default.QrCode2, tint = IPTV_BLUE, onClick = onShowQr)
                    IptvActionButton(icon = Icons.Default.CalendarViewWeek, tint = IPTV_GOLD, onClick = onShowEpg)
                }
            }
            if (currentProgram != null) {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x0AFFFFFF)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("NOW", color = IPTV_LIVE, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(currentProgram.title, color = IPTV_WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${timeFormatter.format(Date(currentProgram.startTime))} – ${timeFormatter.format(Date(currentProgram.endTime))}", color = IPTV_MUTED, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
                            Box(Modifier.fillMaxWidth(currentProgram.progressFraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(IPTV_RED, IPTV_LIVE))))
                        }
                    }
                    if (nextProgram != null) {
                        Box(Modifier.width(1.dp).height(80.dp).background(IPTV_DIVIDER))
                        Column(Modifier.weight(0.8f)) {
                            Text("NEXT", color = IPTV_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(nextProgram.title, color = IPTV_DIM, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(timeFormatter.format(Date(nextProgram.startTime)), color = IPTV_MUTED, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = tint, contentColor = tint, focusedContentColor = IPTV_WHITE),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f), modifier = Modifier.size(52.dp)
    ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) } }
}

@Composable
private fun EpgTimeline(programs: List<EpgProgram>, channelName: String, epgLoadState: IptvLoadState, modifier: Modifier = Modifier) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(IPTV_SURFACE).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Programme Guide", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            when (epgLoadState) {
                is IptvLoadState.Loading -> Text("Loading EPG...", color = IPTV_GOLD, fontSize = 14.sp)
                is IptvLoadState.Error -> Text("EPG unavailable", color = IPTV_RED, fontSize = 14.sp)
                else -> {}
            }
        }
        Spacer(Modifier.height(20.dp))
        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No EPG data for this channel", color = IPTV_MUTED, fontSize = 16.sp) }
        } else {
            val filtered = programs.filter { it.endTime > now - 3_600_000 }.take(20)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.startTime }) { prog -> EpgProgramRow(program = prog, timeFormatter = timeFormatter) }
            }
        }
    }
}

@Composable
private fun EpgProgramRow(program: EpgProgram, timeFormatter: SimpleDateFormat) {
    val isLive = program.isLive
    val isPast = System.currentTimeMillis() > program.endTime

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(when { isLive -> IPTV_LIVE.copy(0.15f); isPast -> Color(0x05FFFFFF); else -> Color(0x0AFFFFFF) })
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (isLive) LiveIndicatorDot() else Spacer(Modifier.size(10.dp))
        Column(Modifier.width(88.dp)) {
            Text(timeFormatter.format(Date(program.startTime)), color = if (isLive) IPTV_LIVE else if (isPast) IPTV_MUTED else IPTV_DIM, fontSize = 15.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
            Text(timeFormatter.format(Date(program.endTime)), color = IPTV_MUTED, fontSize = 13.sp)
        }
        Box(Modifier.width(2.dp).height(36.dp).background(if (isLive) IPTV_LIVE else IPTV_DIVIDER))
        Column(Modifier.weight(1f)) {
            Text(program.title, color = if (isPast) IPTV_MUTED else if (isLive) IPTV_WHITE else IPTV_DIM, fontSize = 16.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (program.category.isNotEmpty()) Text(program.category, color = IPTV_MUTED, fontSize = 13.sp)
        }
        if (isLive) {
            Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
                Text("${(program.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(56.dp).height(6.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.3f))) {
                    Box(Modifier.fillMaxWidth(program.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                }
            }
        }
    }
}

@Composable
fun EpgFullGuide(channel: IptvChannel, programs: List<EpgProgram>, onDismiss: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
    val closeFR = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); runCatching { closeFR.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(88.dp).background(IPTV_SURFACE).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Surface(
                onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(52.dp).focusRequester(closeFR)
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(24.dp)) } }
            Text("EPG Guide – ${channel.name}", color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No programme data available", color = IPTV_MUTED, fontSize = 18.sp) }
        } else {
            val grouped = programs.groupBy { dateFormatter.format(Date(it.startTime)) }
            LazyColumn(contentPadding = PaddingValues(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                grouped.forEach { (date, progs) ->
                    item { Text(date, color = IPTV_GOLD, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(progs, key = { it.startTime }) { prog -> EpgFullRow(program = prog, timeFormatter = timeFormatter) }
                }
            }
        }
    }
}

@Composable
private fun EpgFullRow(program: EpgProgram, timeFormatter: SimpleDateFormat) {
    val isLive = program.isLive
    val isPast = System.currentTimeMillis() > program.endTime

    Surface(
        onClick = {}, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = when { isLive -> IPTV_LIVE.copy(0.12f); isPast -> Color(0x06FFFFFF); else -> Color(0x0CFFFFFF) }, focusedContainerColor = when { isLive -> IPTV_LIVE.copy(0.25f); else -> Color(0x1AFFFFFF) }),
        border = ClickableSurfaceDefaults.border(border = if (isLive) Border(BorderStroke(1.dp, IPTV_LIVE.copy(0.4f)), shape = RoundedCornerShape(24.dp)) else Border.None, focusedBorder = Border.None),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(96.dp)) {
                Text(timeFormatter.format(Date(program.startTime)), color = if (isLive) IPTV_LIVE else IPTV_DIM, fontSize = 16.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
                Text("${(program.durationMs / 60_000).toInt()}min", color = IPTV_MUTED, fontSize = 14.sp)
            }
            if (isLive) Box(Modifier.clip(CircleShape).background(IPTV_LIVE).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("LIVE", color = IPTV_WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f)) {
                Text(program.title, color = if (isPast) IPTV_MUTED else IPTV_WHITE, fontSize = 18.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (program.description.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(program.description, color = IPTV_MUTED, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (isLive) {
                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
                    Text("${(program.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(72.dp).height(8.dp).clip(CircleShape).background(IPTV_MUTED.copy(0.3f))) {
                        Box(Modifier.fillMaxWidth(program.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(channel: IptvChannel, onDismiss: () -> Unit) {
    val closeFR = remember { FocusRequester() }
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 320) }

    LaunchedEffect(Unit) { delay(100); runCatching { closeFR.requestFocus() } }

    Column(
        Modifier.width(560.dp).clip(RoundedCornerShape(32.dp)).background(IPTV_SURFACE).border(1.dp, IPTV_BLUE.copy(0.3f), RoundedCornerShape(32.dp)).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Stream QR Code", color = IPTV_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Scan to open on mobile", color = IPTV_MUTED, fontSize = 16.sp)
            }
            Surface(
                onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_DIM, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(48.dp).focusRequester(closeFR)
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(24.dp)) } }
        }
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)).background(IPTV_WHITE).padding(16.dp)) {
            androidx.compose.foundation.Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x0AFFFFFF)).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)), Alignment.Center) {
                if (channel.logoUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).addHeader("User-Agent", "Mozilla/5.0").crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
                else Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(channel.name, color = IPTV_WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(channel.groupTitle, color = IPTV_MUTED, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x08FFFFFF)).padding(16.dp)) {
            Text(channel.streamUrl, color = IPTV_MUTED, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddPlaylistDialog(state: IptvState, focusRequester: FocusRequester, focusManager: FocusManager, onEvent: (IptvEvent) -> Unit) {
    var urlFocused by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    var epgFocused by remember { mutableStateOf(false) }

    val serverUrl = "http://${state.localIpAddress}:8080"
    val qrBitmap = remember(serverUrl) { if (state.localIpAddress.isNotBlank()) QrCodeGenerator.generate(serverUrl, 320) else null }

    LaunchedEffect(Unit) { delay(150); runCatching { focusRequester.requestFocus() } }

    Box(Modifier.width(920.dp).clip(RoundedCornerShape(32.dp)).background(IPTV_SURFACE).border(1.dp, IPTV_BLUE.copy(0.3f), RoundedCornerShape(32.dp)).padding(48.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Add M3U / M3U8 Playlist", color = IPTV_WHITE, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Choose an easy way to enter your links", color = IPTV_MUTED, fontSize = 18.sp)
                }
                Surface(
                    onClick = { onEvent(IptvEvent.HideAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_DIM, focusedContentColor = IPTV_BG),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f), modifier = Modifier.size(48.dp)
                ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(24.dp)) } }
            }
            Spacer(Modifier.height(40.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0x0AFFFFFF)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Option 1: Phone", color = IPTV_BLUE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Scan this code to easily paste long URLs directly to the TV.", color = IPTV_MUTED, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))

                    if (qrBitmap != null) {
                        Box(Modifier.size(220.dp).clip(RoundedCornerShape(20.dp)).background(IPTV_WHITE).padding(16.dp)) {
                            androidx.compose.foundation.Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(serverUrl, color = IPTV_DIM, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Box(Modifier.size(220.dp), Alignment.Center) { Text("Connecting...", color = IPTV_MUTED) }
                    }
                }

                Column(Modifier.weight(1.3f)) {
                    Text("Option 2: Type manually", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    AddDialogField("Playlist Name", state.addPlaylistName, "My IPTV", nameFocused, Icons.AutoMirrored.Filled.PlaylistPlay, focusManager, modifier = Modifier.onFocusChanged { nameFocused = it.isFocused }) { onEvent(IptvEvent.UpdateAddPlaylistName(it)) }
                    Spacer(Modifier.height(20.dp))
                    AddDialogField("M3U/M3U8 URL *", state.addPlaylistUrl, "http://provider.com/list.m3u8", urlFocused, Icons.Default.Link, focusManager, isRequired = true, modifier = Modifier.focusRequester(focusRequester).onFocusChanged { urlFocused = it.isFocused }) { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
                    Spacer(Modifier.height(20.dp))
                    AddDialogField("EPG URL (Optional)", state.addPlaylistEpgUrl, "http://provider.com/epg.xml.gz", epgFocused, Icons.AutoMirrored.Filled.EventNote, focusManager, modifier = Modifier.onFocusChanged { epgFocused = it.isFocused }) { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) }

                    Spacer(Modifier.height(40.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Surface(
                            onClick = { onEvent(IptvEvent.HideAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = Color(0x33FFFFFF), contentColor = IPTV_DIM, focusedContentColor = IPTV_WHITE),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f), modifier = Modifier.weight(1f).height(60.dp)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Cancel", fontSize = 18.sp, fontWeight = FontWeight.Bold) } }
                        Surface(
                            onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE.copy(0.8f) else IPTV_MUTED.copy(0.2f), focusedContainerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE else IPTV_MUTED.copy(0.3f), contentColor = IPTV_WHITE, focusedContentColor = IPTV_WHITE),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f), modifier = Modifier.weight(1.5f).height(60.dp)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.CloudDownload, null, Modifier.size(24.dp)); Text("Load Playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDialogField(label: String, value: String, hint: String, isFocused: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, focusManager: FocusManager, isRequired: Boolean = false, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = IPTV_DIM, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (isRequired) Text("*", color = IPTV_RED, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(if (isFocused) Color(0xFF222236) else Color(0x0AFFFFFF)).border(width = if (isFocused) 3.dp else 1.dp, color = if (isFocused) IPTV_WHITE else IPTV_DIVIDER, shape = CircleShape).padding(horizontal = 20.dp).then(modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (isFocused) IPTV_WHITE else IPTV_MUTED)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true, textStyle = TextStyle(color = IPTV_WHITE, fontSize = 18.sp), cursorBrush = SolidColor(IPTV_WHITE),
                decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = IPTV_MUTED, fontSize = 18.sp); inner() } },
                modifier = Modifier.weight(1f)
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
        }
    }
}

@Composable
private fun IptvEmptyState(loadState: IptvLoadState, addPlaylistFR: FocusRequester, onEvent: (IptvEvent) -> Unit) {
    LaunchedEffect(loadState) { delay(100); runCatching { addPlaylistFR.requestFocus() } }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(160.dp).background(IPTV_BLUE.copy(0.1f), CircleShape).border(2.dp, IPTV_BLUE.copy(0.3f), CircleShape), Alignment.Center) { Icon(Icons.Default.LiveTv, null, Modifier.size(72.dp), tint = IPTV_BLUE) }
            Text("Lumina Streams", color = IPTV_WHITE, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("Add an M3U or M3U8 playlist to start watching\nlive channels with EPG guide support", color = IPTV_MUTED, fontSize = 20.sp, textAlign = TextAlign.Center)
            if (loadState is IptvLoadState.Error) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(IPTV_RED.copy(0.12f)).border(1.dp, IPTV_RED.copy(0.3f), RoundedCornerShape(16.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Error, null, Modifier.size(24.dp), tint = IPTV_RED); Text(loadState.message, color = IPTV_RED, fontSize = 16.sp) }
                }
            }
            Surface(
                onClick = { onEvent(IptvEvent.ShowAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = IPTV_BLUE, focusedContainerColor = IPTV_WHITE, contentColor = IPTV_WHITE, focusedContentColor = IPTV_BG),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f), glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(IPTV_BLUE.copy(0.5f), 24.dp)), modifier = Modifier.height(68.dp).focusRequester(addPlaylistFR)
            ) { Row(Modifier.padding(horizontal = 56.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Default.Add, null, Modifier.size(28.dp)); Text("Add Playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold, softWrap = false) } }
        }
    }
}

@Composable
private fun IptvWelcomePanel(channelCount: Int, playlistName: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(IPTV_BLUE.copy(0.05f), Color.Transparent), radius = 600f)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(96.dp), tint = IPTV_BLUE.copy(0.4f))
            Spacer(Modifier.height(24.dp))
            Text(if (playlistName.isNotBlank()) playlistName else "Ready to Watch", color = IPTV_WHITE, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("$channelCount channels loaded", color = IPTV_MUTED, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("← Select a channel to start watching", color = IPTV_MUTED, fontSize = 16.sp)
        }
    }
}