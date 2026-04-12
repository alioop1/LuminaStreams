@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.*
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ZappingHud(
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
fun ZappingCard(
    channel  : IptvChannel,
    logoUrl  : String,
    isCurrent: Boolean,
    modifier : Modifier,
    onFocus  : () -> Unit,
    onClick  : () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(120, easing = FastOutSlowInEasing), label = "zc")
    val glowAlpha by animateFloatAsState(if (focused) 1f else if (isCurrent) 0.55f else 0f, tween(120), label = "gla")

    Box(modifier.width(120.dp).height(82.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
        if (glowAlpha > 0f) {
            Box(Modifier.matchParentSize().padding(4.dp).clip(RoundedCornerShape(14.dp))
                .background(if (focused) ACCENT.copy(glowAlpha * 0.35f) else ACCENT.copy(glowAlpha * 0.25f)))
        }
        Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor        = if (isCurrent) ACCENT.copy(0.18f) else WHITE.copy(0.07f),
                focusedContainerColor = Color(0xFF1A1A2E),
                contentColor = WHITE, focusedContentColor = WHITE),
            border = ClickableSurfaceDefaults.border(
                border = if (isCurrent) Border(border = BorderStroke(1.5.dp, ACCENT.copy(0.7f)),
                    shape = RoundedCornerShape(12.dp)) else Border.None,
                focusedBorder = Border(border = BorderStroke(2.dp, ACCENT), shape = RoundedCornerShape(12.dp))),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            glow   = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
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

@Composable
fun SideGroupMenu(
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

@Composable
fun PlayerQuickSettings(
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
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(17.dp),
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