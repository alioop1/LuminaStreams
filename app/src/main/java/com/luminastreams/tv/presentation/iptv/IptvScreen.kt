package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.presentation.iptv.settings.IptvSettingsScreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun IptvScreen(
    viewModel: IptvViewModel,
    onPlayChannel: (String) -> Unit
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val showQr by viewModel.showQrScreen.collectAsStateWithLifecycle()
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()

    val focusedEpg by viewModel.focusedEpg.collectAsStateWithLifecycle()
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    if (showQr) {
        IptvSetupOverlay(ipAddress = ipAddress, onClose = { viewModel.closeQrSetup() }, onManualSubmit = { name, url, epg -> viewModel.addManualPlaylist(name, url, epg) })
        return
    }

    val sidebarFocusRequester = remember { FocusRequester() }
    val channelsFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {} }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinimalistHeader(focusedChannel, focusedEpg)

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxHeight().width(280.dp).padding(start = 32.dp, end = 24.dp, top = 8.dp, bottom = 32.dp)) {
                    val displayGroups = listOf("All", "Favorites") + groups.filter { it != "All" && it != "Favorites" } + listOf("EPG Guide", "Settings")
                    CategorySidebar(
                        groups = displayGroups,
                        selectedGroup = selectedGroup,
                        onGroupSelect = { viewModel.selectGroup(it) },
                        contentFocusRequester = channelsFocusRequester,
                        modifier = Modifier.focusRequester(sidebarFocusRequester)
                    )
                }

                Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(top = 8.dp, end = 48.dp, bottom = 32.dp)) {
                    when {
                        selectedGroup == "Settings" -> {
                            IptvSettingsScreen()
                        }
                        selectedGroup == "EPG Guide" -> {
                            EpgGuideScreen(
                                channels = channels,
                                viewModel = viewModel,
                                onPlayChannel = onPlayChannel,
                                modifier = Modifier.focusProperties { enter = { if (channels.isNotEmpty()) firstItemFocusRequester else FocusRequester.Default } }.focusRequester(channelsFocusRequester),
                                firstItemFocus = firstItemFocusRequester
                            )
                        }
                        channels.isEmpty() -> {
                            EmptyStateView(onSetupClick = { viewModel.openQrSetup() })
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 200.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalArrangement = Arrangement.spacedBy(28.dp),
                                modifier = Modifier.focusProperties { enter = { if (channels.isNotEmpty()) firstItemFocusRequester else FocusRequester.Default } }.focusRequester(channelsFocusRequester)
                            ) {
                                gridItems(channels, key = { it.id }) { channel ->
                                    Box(modifier = if (channel == channels.firstOrNull()) Modifier.focusRequester(firstItemFocusRequester) else Modifier) {
                                        PremiumChannelCard(channel = channel, onClick = { onPlayChannel(channel.id) }, onFocus = { focusedChannel = channel; viewModel.onChannelFocused(channel) })
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
private fun MinimalistHeader(channel: ChannelEntity?, epg: EpgProgramEntity?) {
    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val format = SimpleDateFormat("HH:mm", Locale.getDefault()); while (true) { timeStr = format.format(Date()); delay(1000) } }

    Row(modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 48.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Lumina", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (channel != null) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 48.dp), horizontalAlignment = Alignment.End) {
                Text(text = channel.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (epg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = epg.title, color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else { Spacer(modifier = Modifier.weight(1f)) }

        Text(timeStr, color = Color.White.copy(alpha = 0.5f), fontSize = 32.sp, fontWeight = FontWeight.Light)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategorySidebar(groups: List<String>, selectedGroup: String, onGroupSelect: (String) -> Unit, contentFocusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LazyColumn(modifier = modifier.fillMaxSize().focusProperties { if (isRtl) left = contentFocusRequester else right = contentFocusRequester }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { it }) { group ->
            val isSelected = group == selectedGroup
            var isFocused by remember { mutableStateOf(false) }
            val iconText = when (group) { "Settings" -> "⚙ Settings"; "EPG Guide" -> "📅 TV Guide"; else -> group }

            Surface(
                onClick = { onGroupSelect(group) },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    focusedContainerColor = Color.White,
                    contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    focusedContentColor = Color.Black
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }
            ) {
                Text(text = iconText, fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PremiumChannelCard(channel: ChannelEntity, onClick: () -> Unit, onFocus: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    var imageLoadFailed by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1.4f).onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocus() },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color.White),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.2f), elevation = 32.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                if (channel.logoUrl.isNotBlank() && !imageLoadFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).size(200).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Fit,
                        onState = { if (it is AsyncImagePainter.State.Error) imageLoadFailed = true },
                        modifier = Modifier.size(72.dp)
                    )
                } else {
                    Text(text = channel.name.take(1).uppercase(), color = if (isFocused) Color.Black else Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, if (isFocused) Color.White else Color(0xFF1C1C1E)))).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(text = channel.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isFocused) Color.Black else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (channel.number > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "CH ${channel.number}", color = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun EpgGuideScreen(channels: List<ChannelEntity>, viewModel: IptvViewModel, onPlayChannel: (String) -> Unit, modifier: Modifier = Modifier, firstItemFocus: FocusRequester) {
    if (channels.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No channels to display.", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp) }
        return
    }

    val currentTime = System.currentTimeMillis()

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)) {
        itemsIndexed(channels.take(50), key = { _, it -> it.id }) { index, channel ->
            var programs by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }
            LaunchedEffect(channel) { programs = viewModel.getProgramsForChannel(channel, currentTime) }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(84.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).size(150).build(),
                                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp).padding(end = 12.dp)
                            )
                        }
                        Text(text = channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (programs.isEmpty()) {
                        item { EpgProgramBlock(title = "No Schedule Available", startTime = 0L, endTime = 0L, isLive = false, isPast = false, onClick = { onPlayChannel(channel.id) }, itemFocus = if (index == 0) firstItemFocus else null) }
                    } else {
                        itemsIndexed(programs, key = { i, prog -> "${prog.startTime}_$i" }) { i, prog ->
                            val isLive = currentTime in prog.startTime..prog.endTime
                            val isPast = prog.endTime < currentTime
                            EpgProgramBlock(title = prog.title, startTime = prog.startTime, endTime = prog.endTime, isLive = isLive, isPast = isPast, onClick = { onPlayChannel(channel.id) }, itemFocus = if (index == 0 && i == 0) firstItemFocus else null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgProgramBlock(title: String, startTime: Long, endTime: Long, isLive: Boolean, isPast: Boolean, onClick: () -> Unit, itemFocus: FocusRequester?) {
    var isFocused by remember { mutableStateOf(false) }

    val durationMinutes = if (startTime == 0L) 60 else ((endTime - startTime) / 60000)
    val widthDp = (durationMinutes * 6).toInt().coerceIn(200, 500).dp
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val timeString = if (startTime == 0L) "--:--" else "${timeFormat.format(Date(startTime))} - ${timeFormat.format(Date(endTime))}"

    var modifier = Modifier.width(widthDp).height(84.dp).onFocusChanged { isFocused = it.isFocused }
    if (itemFocus != null) modifier = modifier.focusRequester(itemFocus)

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isLive) Color(0xFF0A84FF).copy(alpha = 0.15f) else Color(0xFF151515),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (isLive) Color(0xFF0A84FF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f))),
            focusedBorder = Border(BorderStroke(3.dp, Color(0xFF0A84FF)))
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.3f), elevation = 8.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = timeString, color = if (isFocused) Color.DarkGray else if (isLive) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (isLive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.background(if (isFocused) Color.Black.copy(alpha = 0.1f) else Color(0xFF0A84FF).copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("LIVE", color = if (isFocused) Color.Black else Color(0xFF0A84FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isPast) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.background(if (isFocused) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("CATCHUP", color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, color = if (isFocused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyStateView(onSetupClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(24.dp)); Text("No Channels Found", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp)); Text("Configure your playlist to start watching.", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(40.dp))
            Surface(onClick = onSetupClick, colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)) {
                Text("Setup IPTV", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp))
            }
        }
    }
}

@Composable
fun CrispTextField(value: String, label: String, onValueChange: (String) -> Unit, isLast: Boolean = false) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, fontWeight = FontWeight.Bold) }, keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown) { when (event.nativeKeyEvent.keyCode) { KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }; KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }; else -> false } } else false },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.5f), focusedTextColor = Color.White, unfocusedTextColor = Color.White.copy(alpha = 0.8f), focusedContainerColor = Color.White.copy(alpha = 0.1f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f))
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IptvSetupOverlay(ipAddress: String, onClose: () -> Unit, onManualSubmit: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    val isError = ipAddress.contains("ERROR", ignoreCase = true)

    // FIX: Removed the redundant com.luminastreams qualifier name
    val qrBitmap = remember(ipAddress) {
        if (!isError) {
            QrCodeGenerator.generate(ipAddress, 512).asImageBitmap()
        } else null
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xD9000000)).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)) { onClose(); true } else false }, contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.width(880.dp).height(460.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(32.dp)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)).padding(32.dp)) {
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight().padding(end = 32.dp), verticalArrangement = Arrangement.Center) {
                Text("Manual Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text("Enter your IPTV provider details below.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp); Spacer(modifier = Modifier.height(24.dp))
                CrispTextField(value = name, label = "Playlist Name (Optional)", onValueChange = { name = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = url, label = "M3U Link (Required)", onValueChange = { url = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = epg, label = "EPG Link (Optional)", onValueChange = { epg = it }, isLast = true); Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { onManualSubmit(name, url, epg) }, colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Connect Now", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Column(modifier = Modifier.weight(0.8f).fillMaxHeight().padding(start = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Quick Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text("Scan the QR code with your phone.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(32.dp))

                Box(modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(12.dp)).border(2.dp, if (isError) Color(0xFFFF453A) else Color(0xFF32D74B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (isError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tv, contentDescription = "Error", tint = Color(0xFFFF453A), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Network", color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    } else if (qrBitmap != null) {
                        Image(bitmap = qrBitmap, contentDescription = "QR Code", modifier = Modifier.padding(8.dp).fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (!isError) { Text("Or open this address on your phone:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp); Spacer(modifier = Modifier.height(4.dp)); Text(text = ipAddress, color = Color(0xFF32D74B), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                else { Text("Please connect TV to Wi-Fi", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) }
            }
        }
    }
}