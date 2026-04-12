@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
package com.luminastreams.tv.presentation.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.tv.material3.*
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IptvDialog(
    onDismiss : () -> Unit,
    content   : @Composable (fr: FocusRequester, onDismiss: () -> Unit) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.86f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            content(fr, onDismiss)
        }
    }
}

fun Modifier.dialogCard(onDismiss: () -> Unit): Modifier =
    this.focusGroup()
        .focusProperties { exit = { FocusRequester.Cancel } }
        .onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                onDismiss(); true
            } else false
        }
        .pointerInput(Unit) { detectTapGestures { } }

@Composable
fun DialogLabel(text: String) {
    Text(text, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp))
}

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

@Composable
fun FullEpgGuideDialog(
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
                                .focusProperties {
                                    exit = { dir ->
                                        if (dir == if (isRtl) FocusDirection.Right else FocusDirection.Left) fr
                                        else FocusRequester.Default
                                    }
                                }
                        ) {
                            itemsIndexed(sortedPrograms, key = { _, p -> "${p.startTime}_${p.channelId}" }) { idx, p ->
                                val isLive = p.isLiveNow; val isPast = p.isPast
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                    .background(when { isLive -> ACCENT.copy(0.11f); isPast -> Color.Transparent; else -> Color(0x08FFFFFF) })
                                    .let { m -> if (idx == 0) m.focusRequester(programsFR) else m }
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
                SettingsTile("Audio Track", "No audio tracks detected", Icons.AutoMirrored.Filled.VolumeUp, false,
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
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(17.dp),
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
fun SettingsTile(
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

@Composable
fun ChannelQrDialog(channel: IptvChannel, fr: FocusRequester, onDismiss: () -> Unit) {
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