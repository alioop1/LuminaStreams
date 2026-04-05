@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ═══ PALETTE ═════════════════════════════════════════════════════════════════
private val IPTV_BG        = Color(0xFF050508)
private val IPTV_SURFACE   = Color(0xFF0C0C14)
private val IPTV_CARD_FOCUS= Color(0xFF1E1E2E)
private val IPTV_RED       = Color(0xFFE50914)
private val IPTV_LIVE      = Color(0xFFFF2D55)
private val IPTV_BLUE      = Color(0xFF0A84FF)
private val IPTV_GREEN     = Color(0xFF30D158)
private val IPTV_GOLD      = Color(0xFFFFCC02)
private val IPTV_WHITE     = Color(0xFFFFFFFF)
private val IPTV_DIM       = Color(0xAAFFFFFF)
private val IPTV_MUTED     = Color(0x55FFFFFF)
private val IPTV_DIVIDER   = Color(0x1AFFFFFF)

@Composable
fun IptvScreen(
    viewModel: IptvViewModel,
    onNavigateBack: () -> Unit,
    onPlayChannel: (String, String) -> Unit  // url, title
) {
    val state by viewModel.state.collectAsState()

    val backFR = remember { FocusRequester() }
    val firstGroupFR = remember { FocusRequester() }
    val firstChannelFR = remember { FocusRequester() }
    val addPlaylistFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { backFR.requestFocus() }
    }

    BackHandler {
        when {
            state.showQrCode     -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showAddPlaylist -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showEpgGuide   -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            else                 -> onNavigateBack()
        }
    }

    // Handle channel selection → play
    LaunchedEffect(state.currentChannel) {
        state.currentChannel?.let { ch ->
            onPlayChannel(ch.streamUrl, ch.name)
        }
    }

    Box(Modifier.fillMaxSize().background(IPTV_BG)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top Bar ──────────────────────────────────────────────────────
            IptvTopBar(
                state = state,
                backFR = backFR,
                onBack = onNavigateBack,
                onEvent = viewModel::onEvent
            )

            if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                // Empty state - show playlist setup
                IptvEmptyState(
                    loadState = state.loadState,
                    addPlaylistFR = addPlaylistFR,
                    onEvent = viewModel::onEvent
                )
            } else {
                Row(Modifier.fillMaxSize()) {
                    // ── Left: Groups + Channels ─────────────────────────────
                    Column(
                        Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .background(IPTV_SURFACE)
                    ) {
                        // Group tabs
                        GroupTabRow(
                            groups = state.groups,
                            selectedGroup = state.selectedGroup,
                            firstGroupFR = firstGroupFR,
                            onSelectGroup = { viewModel.onEvent(IptvEvent.SelectGroup(it)) }
                        )

                        // Search box
                        IptvSearchBox(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.onEvent(IptvEvent.UpdateSearch(it)) }
                        )

                        // Channel list
                        ChannelList(
                            channels = state.filteredChannels,
                            currentChannel = state.currentChannel,
                            favorites = state.favoriteChannelIds,
                            epgData = state.epgData,
                            firstChannelFR = firstChannelFR,
                            loadState = state.loadState,
                            onSelectChannel = { viewModel.onEvent(IptvEvent.SelectChannel(it)) },
                            onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(it)) },
                            onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(it)) }
                        )
                    }

                    // ── Right: Now Playing + EPG ────────────────────────────
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 0.dp)
                    ) {
                        if (state.currentChannel != null) {
                            NowPlayingPanel(
                                channel = state.currentChannel!!,
                                currentProgram = state.currentProgram,
                                nextProgram = state.nextProgram,
                                isFavorite = state.currentChannel!!.id in state.favoriteChannelIds,
                                onToggleFavorite = { viewModel.onEvent(IptvEvent.ToggleFavorite(state.currentChannel!!.id)) },
                                onShowQr = { viewModel.onEvent(IptvEvent.ShowQrCode(state.currentChannel!!)) },
                                onShowEpg = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }
                            )
                        }

                        // EPG timeline for current channel
                        if (state.currentChannel != null) {
                            val programs = viewModel.getEpgForChannel(state.currentChannel!!)
                            EpgTimeline(
                                programs = programs,
                                channelName = state.currentChannel!!.name,
                                epgLoadState = state.epgLoadState,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            IptvWelcomePanel(
                                channelCount = state.channels.size,
                                playlistName = state.playlists.firstOrNull { it.isActive }?.name ?: "",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ── Loading overlay ──────────────────────────────────────────────────
        if (state.loadState is IptvLoadState.Loading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.6f)),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.luminastreams.tv.ui.components.LoadingIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Loading playlist...", color = IPTV_DIM, fontSize = 16.sp)
                }
            }
        }

        // ── Add Playlist Dialog ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showAddPlaylist,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(300f)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.8f)),
                Alignment.Center
            ) {
                AddPlaylistDialog(
                    state = state,
                    focusRequester = addPlaylistFR,
                    onEvent = viewModel::onEvent
                )
            }
        }

        // ── QR Code Dialog ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showQrCode && state.qrCodeChannel != null,
            enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(400f)
        ) {
            state.qrCodeChannel?.let { ch ->
                QrCodeDialog(
                    channel = ch,
                    onDismiss = { viewModel.onEvent(IptvEvent.HideQrCode) }
                )
            }
        }

        // ── EPG Full Guide ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showEpgGuide && state.currentChannel != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(380)) + fadeIn(tween(280)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            state.currentChannel?.let { ch ->
                val programs = viewModel.getEpgForChannel(ch)
                EpgFullGuide(
                    channel = ch,
                    programs = programs,
                    onDismiss = { viewModel.onEvent(IptvEvent.HideEpgGuide) }
                )
            }
        }
    }
}

// ═══ TOP BAR ═════════════════════════════════════════════════════════════════
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
            .height(68.dp)
            .background(IPTV_SURFACE)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x1AFFFFFF),
                focusedContainerColor = IPTV_WHITE,
                contentColor = IPTV_DIM,
                focusedContentColor = IPTV_BG
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.size(40.dp).focusRequester(backFR)
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            }
        }

        // Live dot indicator
        LiveIndicatorDot()

        Text(
            "IPTV Live TV",
            color = IPTV_WHITE,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        if (state.channels.isNotEmpty()) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(IPTV_BLUE.copy(0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "${state.channels.size} ch",
                    color = IPTV_BLUE,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // EPG status badge
        if (state.epgLoadState is IptvLoadState.Success) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(IPTV_GREEN.copy(0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("EPG ✓", color = IPTV_GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (state.epgLoadState is IptvLoadState.Loading) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(IPTV_GOLD.copy(0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("EPG...", color = IPTV_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Playlists dropdown chip
        state.playlists.firstOrNull { it.isActive }?.let { active ->
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(14.dp), tint = IPTV_DIM)
                    Text(active.name, color = IPTV_DIM, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Add playlist button
        Surface(
            onClick = { onEvent(IptvEvent.ShowAddPlaylist) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = IPTV_BLUE.copy(0.15f),
                focusedContainerColor = IPTV_BLUE,
                contentColor = IPTV_BLUE,
                focusedContentColor = IPTV_WHITE
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Text("Add M3U", fontSize = 13.sp, fontWeight = FontWeight.Bold, softWrap = false)
            }
        }
    }
}

// ═══ LIVE DOT ════════════════════════════════════════════════════════════════
@Composable
private fun LiveIndicatorDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveDot"
    )
    Box(
        Modifier.size(8.dp)
            .alpha(alpha)
            .background(IPTV_LIVE, CircleShape)
    )
}

// ═══ GROUP TABS ══════════════════════════════════════════════════════════════
@Composable
private fun GroupTabRow(
    groups: List<String>,
    selectedGroup: String,
    firstGroupFR: FocusRequester,
    onSelectGroup: (String) -> Unit
) {
    val rowState = rememberLazyListState()

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        itemsIndexed(groups) { idx, group ->
            val isSel = group == selectedGroup
            val icon = when (group) {
                "All" -> Icons.Default.LiveTv
                "Favorites" -> Icons.Default.Favorite
                "Recent" -> Icons.Default.History
                else -> null
            }
            Surface(
                onClick = { onSelectGroup(group) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSel) IPTV_RED.copy(0.25f) else Color(0x0AFFFFFF),
                    focusedContainerColor = if (isSel) IPTV_RED else Color(0x22FFFFFF),
                    contentColor = if (isSel) IPTV_WHITE else IPTV_DIM,
                    focusedContentColor = IPTV_WHITE
                ),
                border = ClickableSurfaceDefaults.border(
                    border = if (isSel) Border(BorderStroke(1.dp, IPTV_RED.copy(0.5f)), shape = RoundedCornerShape(8.dp)) else Border.None,
                    focusedBorder = Border.None
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(34.dp)
                    .let { if (idx == 0) it.focusRequester(firstGroupFR) else it }
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (icon != null) Icon(icon, null, Modifier.size(13.dp))
                    Text(
                        group,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// ═══ SEARCH BOX ══════════════════════════════════════════════════════════════
@Composable
private fun IptvSearchBox(query: String, onQueryChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF181828) else Color(0x0AFFFFFF))
            .border(
                1.dp,
                if (focused) IPTV_BLUE.copy(0.5f) else IPTV_DIVIDER,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = if (focused) IPTV_BLUE else IPTV_MUTED)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = IPTV_WHITE, fontSize = 13.sp),
            cursorBrush = SolidColor(IPTV_BLUE),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Search channels...", color = IPTV_MUTED, fontSize = 13.sp)
                    inner()
                }
            },
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close, null,
                Modifier.size(14.dp).clickable { onQueryChange("") },
                tint = IPTV_MUTED
            )
        }
    }
}

// ═══ CHANNEL LIST ════════════════════════════════════════════════════════════
@Composable
private fun ChannelList(
    channels: List<IptvChannel>,
    currentChannel: IptvChannel?,
    favorites: Set<String>,
    epgData: Map<String, List<EpgProgram>>,
    firstChannelFR: FocusRequester,
    loadState: IptvLoadState,
    onSelectChannel: (IptvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onShowQr: (IptvChannel) -> Unit
) {
    if (loadState is IptvLoadState.Loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            com.luminastreams.tv.ui.components.LoadingIndicator(size = 40.dp)
        }
        return
    }

    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📺", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("No channels", color = IPTV_MUTED, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { idx, channel ->
            val nowProgram = run {
                val epg = epgData[channel.tvgId] ?: epgData[channel.id]
                epg?.firstOrNull { it.isLive }
            }
            ChannelRow(
                channel = channel,
                isSelected = currentChannel?.id == channel.id,
                isFavorite = channel.id in favorites,
                currentProgram = nowProgram,
                modifier = if (idx == 0) Modifier.focusRequester(firstChannelFR) else Modifier,
                onSelect = { onSelectChannel(channel) },
                onFavorite = { onToggleFavorite(channel.id) },
                onQr = { onShowQr(channel) }
            )
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
    var focused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> IPTV_RED.copy(0.18f)
            focused    -> IPTV_CARD_FOCUS
            else       -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "chBg"
    )

    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key) {
                        Key.F -> { onFavorite(); true }
                        Key.Q -> { onQr(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        Box(
            Modifier.fillMaxSize().background(bgColor)
                .padding(start = 0.dp)
        ) {
            // Selected indicator
            if (isSelected) {
                Box(
                    Modifier.width(3.dp).fillMaxHeight()
                        .background(IPTV_RED, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                )
            }

            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Channel number
                Text(
                    "${channel.number}",
                    color = IPTV_MUTED,
                    fontSize = 10.sp,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.End
                )

                // Logo
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0x14FFFFFF)),
                    Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(channel.logoUrl).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Text(
                            channel.name.take(2).uppercase(),
                            color = IPTV_DIM,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Channel info
                Column(Modifier.weight(1f)) {
                    Text(
                        channel.name,
                        color = if (isSelected || focused) IPTV_WHITE else IPTV_DIM,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentProgram != null) {
                        Text(
                            currentProgram.title,
                            color = IPTV_MUTED,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            channel.groupTitle,
                            color = IPTV_MUTED,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Live indicator + EPG progress
                if (currentProgram != null) {
                    Column(
                        Modifier.width(44.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        LiveIndicatorDot()
                        Spacer(Modifier.height(4.dp))
                        // Mini progress bar
                        Box(
                            Modifier.width(32.dp).height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(IPTV_MUTED.copy(0.3f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(currentProgram.progressFraction)
                                    .fillMaxHeight()
                                    .background(IPTV_LIVE)
                            )
                        }
                    }
                }

                // Favorite icon (shown on focus)
                if (focused || isFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        Modifier.size(16.dp),
                        tint = if (isFavorite) IPTV_RED else IPTV_MUTED
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(IPTV_DIVIDER))
}

// ═══ NOW PLAYING PANEL ═══════════════════════════════════════════════════════
@Composable
private fun NowPlayingPanel(
    channel: IptvChannel,
    currentProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShowQr: () -> Unit,
    onShowEpg: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D1A), IPTV_BG)))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Channel logo
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1AFFFFFF)),
                    Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(IPTV_LIVE)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("● LIVE", color = IPTV_WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Text(
                            channel.groupTitle,
                            color = IPTV_MUTED,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        channel.name,
                        color = IPTV_WHITE,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IptvActionButton(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        tint = if (isFavorite) IPTV_RED else IPTV_DIM,
                        onClick = onToggleFavorite
                    )
                    IptvActionButton(
                        icon = Icons.Default.QrCode2,
                        tint = IPTV_BLUE,
                        onClick = onShowQr
                    )
                    IptvActionButton(
                        icon = Icons.Default.CalendarViewWeek,
                        tint = IPTV_GOLD,
                        onClick = onShowEpg
                    )
                }
            }

            // Current program
            if (currentProgram != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Now
                    Column(Modifier.weight(1f)) {
                        Text("NOW", color = IPTV_LIVE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(currentProgram.title, color = IPTV_WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${timeFormatter.format(Date(currentProgram.startTime))} – ${timeFormatter.format(Date(currentProgram.endTime))}",
                            color = IPTV_MUTED, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        // Progress bar
                        Box(
                            Modifier.fillMaxWidth().height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x22FFFFFF))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(currentProgram.progressFraction)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(listOf(IPTV_RED, IPTV_LIVE))
                                    )
                            )
                        }
                    }

                    // Next
                    if (nextProgram != null) {
                        Box(Modifier.width(1.dp).height(60.dp).background(IPTV_DIVIDER))
                        Column(Modifier.weight(0.8f)) {
                            Text("NEXT", color = IPTV_MUTED, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(nextProgram.title, color = IPTV_DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                timeFormatter.format(Date(nextProgram.startTime)),
                                color = IPTV_MUTED, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(IPTV_DIVIDER))
}

@Composable
private fun IptvActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = tint,
            contentColor = tint,
            focusedContentColor = IPTV_WHITE
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        modifier = Modifier.size(38.dp)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp))
        }
    }
}

// ═══ EPG TIMELINE ════════════════════════════════════════════════════════════
@Composable
private fun EpgTimeline(
    programs: List<EpgProgram>,
    channelName: String,
    epgLoadState: IptvLoadState,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier
            .fillMaxWidth()
            .background(IPTV_BG)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Programme Guide",
                color = IPTV_WHITE,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            when (epgLoadState) {
                is IptvLoadState.Loading -> Text("Loading EPG...", color = IPTV_GOLD, fontSize = 11.sp)
                is IptvLoadState.Error -> Text("EPG unavailable", color = IPTV_RED, fontSize = 11.sp)
                else -> {}
            }
        }

        Spacer(Modifier.height(12.dp))

        if (programs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.EventNote, null, Modifier.size(40.dp), tint = IPTV_MUTED)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (epgLoadState is IptvLoadState.Loading) "Loading EPG data..."
                        else "No EPG data for this channel",
                        color = IPTV_MUTED,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val filtered = programs.filter { it.endTime > now - 3_600_000 }.take(20)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.startTime }) { prog ->
                    EpgProgramRow(program = prog, timeFormatter = timeFormatter)
                }
            }
        }
    }
}

@Composable
private fun EpgProgramRow(program: EpgProgram, timeFormatter: SimpleDateFormat) {
    val isLive = program.isLive
    val isPast = System.currentTimeMillis() > program.endTime

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isLive -> IPTV_LIVE.copy(0.1f)
                    isPast -> Color(0x05FFFFFF)
                    else -> Color(0x0AFFFFFF)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isLive) {
            LiveIndicatorDot()
        } else {
            Spacer(Modifier.size(8.dp))
        }

        Column(Modifier.width(80.dp)) {
            Text(
                timeFormatter.format(Date(program.startTime)),
                color = if (isLive) IPTV_LIVE else if (isPast) IPTV_MUTED else IPTV_DIM,
                fontSize = 12.sp,
                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                timeFormatter.format(Date(program.endTime)),
                color = IPTV_MUTED,
                fontSize = 10.sp
            )
        }

        Box(Modifier.width(2.dp).height(28.dp).background(if (isLive) IPTV_LIVE else IPTV_DIVIDER))

        Column(Modifier.weight(1f)) {
            Text(
                program.title,
                color = if (isPast) IPTV_MUTED else if (isLive) IPTV_WHITE else IPTV_DIM,
                fontSize = 13.sp,
                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program.category.isNotEmpty()) {
                Text(program.category, color = IPTV_MUTED, fontSize = 10.sp)
            }
        }

        if (isLive) {
            // Progress
            Column(Modifier.width(50.dp), horizontalAlignment = Alignment.End) {
                Text(
                    "${(program.progressFraction * 100).toInt()}%",
                    color = IPTV_LIVE,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier.width(40.dp).height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(IPTV_MUTED.copy(0.3f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(program.progressFraction)
                            .fillMaxHeight()
                            .background(IPTV_LIVE)
                    )
                }
            }
        }
    }
}

// ═══ EPG FULL GUIDE ══════════════════════════════════════════════════════════
@Composable
fun EpgFullGuide(
    channel: IptvChannel,
    programs: List<EpgProgram>,
    onDismiss: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
    val closeFR = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { closeFR.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(IPTV_BG)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .background(IPTV_SURFACE)
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x1AFFFFFF),
                        focusedContainerColor = IPTV_WHITE,
                        contentColor = IPTV_WHITE,
                        focusedContentColor = IPTV_BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(40.dp).focusRequester(closeFR)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    }
                }
                Text("EPG Guide – ${channel.name}", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            if (programs.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No programme data available", color = IPTV_MUTED, fontSize = 16.sp)
                }
            } else {
                val grouped = programs.groupBy { dateFormatter.format(Date(it.startTime)) }
                LazyColumn(
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (date, progs) ->
                        item {
                            Text(
                                date,
                                color = IPTV_GOLD,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(progs, key = { it.startTime }) { prog ->
                            EpgFullRow(program = prog, timeFormatter = timeFormatter)
                        }
                    }
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
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isLive -> IPTV_LIVE.copy(0.12f)
                isPast -> Color(0x06FFFFFF)
                else   -> Color(0x0CFFFFFF)
            },
            focusedContainerColor = when {
                isLive -> IPTV_LIVE.copy(0.25f)
                else   -> Color(0x1AFFFFFF)
            }
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isLive) Border(BorderStroke(1.dp, IPTV_LIVE.copy(0.4f)), shape = RoundedCornerShape(10.dp)) else Border.None,
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.width(80.dp)) {
                Text(
                    timeFormatter.format(Date(program.startTime)),
                    color = if (isLive) IPTV_LIVE else IPTV_DIM,
                    fontSize = 14.sp,
                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal
                )
                val dur = (program.durationMs / 60_000).toInt()
                Text("${dur}min", color = IPTV_MUTED, fontSize = 11.sp)
            }

            if (isLive) {
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(IPTV_LIVE)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = IPTV_WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    program.title,
                    color = if (isPast) IPTV_MUTED else IPTV_WHITE,
                    fontSize = 15.sp,
                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (program.description.isNotEmpty()) {
                    Text(
                        program.description,
                        color = IPTV_MUTED,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (program.category.isNotEmpty()) {
                        Text(program.category, color = IPTV_BLUE, fontSize = 10.sp)
                    }
                    if (program.rating.isNotEmpty()) {
                        Text(program.rating, color = IPTV_GOLD, fontSize = 10.sp)
                    }
                }
            }

            if (isLive) {
                Column(Modifier.width(60.dp), horizontalAlignment = Alignment.End) {
                    Text("${(program.progressFraction * 100).toInt()}%", color = IPTV_LIVE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(IPTV_MUTED.copy(0.3f))) {
                        Box(Modifier.fillMaxWidth(program.progressFraction).fillMaxHeight().background(IPTV_LIVE))
                    }
                }
            }
        }
    }
}

// ═══ QR CODE DIALOG ══════════════════════════════════════════════════════════
@Composable
fun QrCodeDialog(channel: IptvChannel, onDismiss: () -> Unit) {
    val closeFR = remember { FocusRequester() }
    val qrBitmap = remember(channel.streamUrl) {
        QrCodeGenerator.generate(channel.streamUrl, 320)
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { closeFR.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.85f))
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onDismiss() },
        Alignment.Center
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0F1A))
                .border(1.dp, IPTV_BLUE.copy(0.3f), RoundedCornerShape(24.dp))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Stream QR Code", color = IPTV_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Scan to open on mobile", color = IPTV_MUTED, fontSize = 13.sp)
                }
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x1AFFFFFF),
                        focusedContainerColor = IPTV_WHITE,
                        contentColor = IPTV_DIM,
                        focusedContentColor = IPTV_BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(36.dp).focusRequester(closeFR)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // QR Code
            Box(
                Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(IPTV_WHITE)
                    .padding(12.dp)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code for ${channel.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(20.dp))

            // Channel info below QR
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x0AFFFFFF))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1AFFFFFF)),
                    Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(model = channel.logoUrl, contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                    } else {
                        Text(channel.name.take(2).uppercase(), color = IPTV_WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column {
                    Text(channel.name, color = IPTV_WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(channel.groupTitle, color = IPTV_MUTED, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // URL display
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x08FFFFFF))
                    .padding(10.dp)
            ) {
                Text(
                    channel.streamUrl,
                    color = IPTV_MUTED,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Open any video player app and scan this code",
                color = IPTV_MUTED,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══ ADD PLAYLIST DIALOG ═════════════════════════════════════════════════════
@Composable
private fun AddPlaylistDialog(
    state: IptvState,
    focusRequester: FocusRequester,
    onEvent: (IptvEvent) -> Unit
) {
    var urlFocused by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    var epgFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        Modifier
            .width(680.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F1A))
            .border(1.dp, IPTV_BLUE.copy(0.3f), RoundedCornerShape(24.dp))
            .padding(40.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Add M3U Playlist", color = IPTV_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Enter your IPTV playlist URL", color = IPTV_MUTED, fontSize = 13.sp)
                }
                Surface(
                    onClick = { onEvent(IptvEvent.HideAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = IPTV_WHITE, contentColor = IPTV_DIM, focusedContentColor = IPTV_BG),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Name field
            AddDialogField(
                label = "Playlist Name",
                value = state.addPlaylistName,
                hint = "My IPTV",
                isFocused = nameFocused,
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistName(it)) },
                modifier = Modifier.onFocusChanged { nameFocused = it.isFocused }
            )

            Spacer(Modifier.height(12.dp))

            // M3U URL field
            AddDialogField(
                label = "M3U Playlist URL *",
                value = state.addPlaylistUrl,
                hint = "http://provider.com/playlist.m3u",
                isFocused = urlFocused,
                icon = Icons.Default.Link,
                isRequired = true,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { urlFocused = it.isFocused },
                onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
            )

            Spacer(Modifier.height(12.dp))

            // EPG URL field
            AddDialogField(
                label = "EPG URL (optional)",
                value = state.addPlaylistEpgUrl,
                hint = "http://provider.com/epg.xml.gz",
                isFocused = epgFocused,
                icon = Icons.AutoMirrored.Filled.EventNote,
                onValueChange = { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) },
                modifier = Modifier.onFocusChanged { epgFocused = it.isFocused }
            )

            Spacer(Modifier.height(8.dp))

            // Tip
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(IPTV_BLUE.copy(0.08f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(14.dp), tint = IPTV_BLUE)
                Text(
                    "Scan the QR code on any channel to open its stream URL on your mobile device",
                    color = IPTV_BLUE,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { onEvent(IptvEvent.HideAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = Color(0x33FFFFFF), contentColor = IPTV_DIM, focusedContentColor = IPTV_WHITE),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
                Surface(
                    onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE.copy(0.8f) else IPTV_MUTED.copy(0.2f),
                        focusedContainerColor = if (state.addPlaylistUrl.isNotBlank()) IPTV_BLUE else IPTV_MUTED.copy(0.3f),
                        contentColor = IPTV_WHITE, focusedContentColor = IPTV_WHITE
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.weight(2f).height(52.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
                            Text("Load Playlist", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDialogField(
    label: String,
    value: String,
    hint: String,
    isFocused: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isRequired: Boolean = false,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = IPTV_DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (isRequired) Text("*", color = IPTV_RED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFocused) Color(0xFF181828) else Color(0x0AFFFFFF))
                .border(
                    1.dp,
                    if (isFocused) IPTV_BLUE.copy(0.6f) else IPTV_DIVIDER,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp)
                .then(modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (isFocused) IPTV_BLUE else IPTV_MUTED)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = IPTV_WHITE, fontSize = 13.sp),
                cursorBrush = SolidColor(IPTV_BLUE),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(hint, color = IPTV_MUTED, fontSize = 13.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ═══ EMPTY STATE ═════════════════════════════════════════════════════════════
@Composable
private fun IptvEmptyState(
    loadState: IptvLoadState,
    addPlaylistFR: FocusRequester,
    onEvent: (IptvEvent) -> Unit
) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                Modifier.size(100.dp)
                    .background(IPTV_BLUE.copy(0.1f), CircleShape)
                    .border(2.dp, IPTV_BLUE.copy(0.3f), CircleShape),
                Alignment.Center
            ) {
                Icon(Icons.Default.LiveTv, null, Modifier.size(48.dp), tint = IPTV_BLUE)
            }

            Text("Welcome to IPTV", color = IPTV_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                "Add an M3U playlist to start watching\nlive channels with EPG guide support",
                color = IPTV_MUTED,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            if (loadState is IptvLoadState.Error) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(IPTV_RED.copy(0.12f))
                        .border(1.dp, IPTV_RED.copy(0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Error, null, Modifier.size(18.dp), tint = IPTV_RED)
                        Text(loadState.message, color = IPTV_RED, fontSize = 13.sp)
                    }
                }
            }

            Surface(
                onClick = { onEvent(IptvEvent.ShowAddPlaylist) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = IPTV_BLUE,
                    focusedContainerColor = IPTV_WHITE,
                    contentColor = IPTV_WHITE,
                    focusedContentColor = IPTV_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(IPTV_BLUE.copy(0.5f), 20.dp)),
                modifier = Modifier.height(56.dp).focusRequester(addPlaylistFR)
            ) {
                Row(
                    Modifier.padding(horizontal = 32.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Text("Add M3U Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold, softWrap = false)
                }
            }

            // Sample playlists hint
            Column(
                Modifier.width(480.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0AFFFFFF))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("💡 Compatible formats:", color = IPTV_DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "M3U / M3U8 playlist files",
                    "EPG XML / XMLTV guide (gzip supported)",
                    "Most IPTV providers"
                ).forEach { tip ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", color = IPTV_BLUE, fontSize = 12.sp)
                        Text(tip, color = IPTV_MUTED, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ═══ WELCOME PANEL (no channel selected) ═════════════════════════════════════
@Composable
private fun IptvWelcomePanel(
    channelCount: Int,
    playlistName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(IPTV_BLUE.copy(0.05f), Color.Transparent),
                    radius = 400f
                )
            ),
        Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(72.dp), tint = IPTV_BLUE.copy(0.4f))
            Spacer(Modifier.height(16.dp))
            Text(
                if (playlistName.isNotBlank()) playlistName else "IPTV Ready",
                color = IPTV_WHITE,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$channelCount channels loaded",
                color = IPTV_MUTED,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text("← Select a channel to start watching", color = IPTV_MUTED, fontSize = 13.sp)
        }
    }
}