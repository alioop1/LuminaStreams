@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
package com.luminastreams.tv.presentation.iptv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TopNavBar(
    channelsCount : Int,
    hasEpg        : Boolean,
    topBarFR      : FocusRequester,
    epgBtnFR      : FocusRequester,
    addBtnFR      : FocusRequester,
    setgBtnFR     : FocusRequester,
    gridFR        : FocusRequester,
    onBack        : () -> Unit,
    onSettings    : () -> Unit,
    onEpgGuide    : () -> Unit,
    onAddPlaylist : () -> Unit
) {
    var timeStr by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
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
        NavIconBtn(Icons.AutoMirrored.Filled.ArrowBack, Modifier.size(42.dp).focusRequester(topBarFR), onBack)

        Text("LUMINA IPTV", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        if (channelsCount > 0) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ACCENT.copy(0.18f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("$channelsCount CH", color = ACCENT2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        if (hasEpg) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(GREEN.copy(0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("EPG ✓", color = GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(onClick = onEpgGuide,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
                focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
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
            modifier = Modifier.height(36.dp).focusRequester(addBtnFR)
        ) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(15.dp))
                Text("Add Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        NavIconBtn(Icons.Default.Settings, Modifier.size(42.dp).focusRequester(setgBtnFR), onSettings)
        Text(timeStr, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NavIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF),
            focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
        modifier = modifier) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(19.dp)) }
    }
}

@Composable
fun HeroEpgSection(
    channel         : IptvChannel?,
    epgData         : Map<String, List<EpgProgram>>,
    currTracks      : androidx.media3.common.Tracks? = null,
    isPlayingChannel: Boolean = false
) {
    if (channel == null) return
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val epgs    = epgData[channel.id]
    val nowProg = remember(epgs) { epgs?.firstOrNull { it.isLiveNow } }
    val nextProg = remember(epgs) { epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow } }

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

@Composable
fun DashboardContent(
    channels         : List<IptvChannel>,
    favoriteIds      : Set<String>,
    groups           : List<String>,
    epgData          : Map<String, List<EpgProgram>>,
    currentChannelId : String?,
    gridFR           : FocusRequester,
    navBarFR         : FocusRequester,
    currTracks       : androidx.media3.common.Tracks?,
    onChannelClicked : (IptvChannel) -> Unit
) {
    var focusedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    val currentFocus   = focusedChannel ?: channels.firstOrNull()

    Box {
        HeroEpgSection(
            channel          = currentFocus,
            epgData          = epgData,
            currTracks       = currTracks,
            isPlayingChannel = currentFocus?.id == currentChannelId
        )
    }
    ChannelsDashboard(
        channels         = channels,
        favoriteIds      = favoriteIds,
        groups           = groups,
        gridFR           = gridFR,
        navBarFR         = navBarFR,
        onChannelFocused = { ch -> if (focusedChannel?.id != ch.id) focusedChannel = ch },
        onChannelClicked = onChannelClicked
    )
}

@Composable
fun ChannelsDashboard(
    channels        : List<IptvChannel>,
    favoriteIds     : Set<String>,
    groups          : List<String>,
    gridFR          : FocusRequester,
    navBarFR        : FocusRequester,
    onChannelFocused: (IptvChannel) -> Unit,
    onChannelClicked: (IptvChannel) -> Unit
) {
    val favorites       = remember(channels, favoriteIds) { channels.filter { it.id in favoriteIds } }
    val groupedChannels = remember(channels) { channels.groupBy { it.groupTitle } }

    LazyColumn(contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().focusGroup()) {
        if (favorites.isNotEmpty()) {
            item(key = "fav_row") {
                HorizontalChannelRow("⭐ Favorites", favorites, true, gridFR, navBarFR, onChannelFocused, onChannelClicked)
            }
        }
        groups.filter { it != "All" && it != "Favorites" && it != "Recent" }.forEachIndexed { idx, group ->
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
fun HorizontalChannelRow(
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
                .focusProperties { if (upFR != null) up = upFR }
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "Ch" }) { idx, ch ->
                ChannelCard(
                    channel = ch,
                    logoUrl = ch.logoUrl,
                    modifier = if (isFirstRow && idx == 0) Modifier.focusRequester(rowFR) else Modifier,
                    onFocus  = remember(ch.id) { { onFocus(ch) } },
                    onClick  = remember(ch.id) { { onClick(ch) } }
                )
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel : IptvChannel,
    logoUrl : String,
    modifier: Modifier,
    onFocus : () -> Unit,
    onClick : () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(120, easing = FastOutSlowInEasing), label = "cs")

    Box(modifier.width(165.dp).aspectRatio(16f / 9f)
        .graphicsLayer { scaleX = scale; scaleY = scale }) {
        Surface(onClick = onClick,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG,
                focusedContainerColor = ACCENT, contentColor = WHITE, focusedContentColor = WHITE),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border   = ClickableSurfaceDefaults.border(border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, WHITE), shape = RoundedCornerShape(12.dp))),
            glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
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