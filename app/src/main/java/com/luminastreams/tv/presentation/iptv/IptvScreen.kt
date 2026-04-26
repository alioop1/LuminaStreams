@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs

// 🎨 PS5 PREMIUM PALETTE
private val ColorOledBlack = Color(0xFF000000)
private val ColorPs5Gray = Color(0xFF1F1F23)
private val ColorGlass = Color.White.copy(alpha = 0.08f)
private val ColorTextMain = Color.White
private val ColorLiveBlue = Color(0xFF00E5FF)
private val ColorCatchup = Color(0xFFFF9500) // DVR Time Travel Orange
private val epgBlockTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

// ⚡ FEATURE 2: Dynamic Ambient Color Generator
private fun getAmbientGlow(channelName: String?): Color {
    if (channelName == null) return Color.Transparent
    val premiumHues = listOf(
        Color(0xFFE50914), // Crimson
        Color(0xFF00E5FF), // Cyber Blue
        Color(0xFF8A2BE2), // Deep Purple
        Color(0xFFFF9500), // Sunset Orange
        Color(0xFF32D74B)  // Neon Green
    )
    return premiumHues[abs(channelName.hashCode()) % premiumHues.size].copy(alpha = 0.15f)
}

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
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()

    val focusedEpg by viewModel.focusedEpg.collectAsStateWithLifecycle()
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    // ⚡ FEATURE 10: Interaction Tracker for Cinematic Screensaver
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScreensaverActive by remember { mutableStateOf(false) }

    LaunchedEffect(lastInteractionTime) {
        delay(120_000L)
        isScreensaverActive = true
    }

    if (showQr) {
        IptvSetupOverlay(ipAddress = ipAddress, onClose = { viewModel.closeQrSetup() }, onManualSubmit = { name, url, epg -> viewModel.addManualPlaylist(name, url, epg) })
        return
    }

    val categoryFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); try { categoryFocusRequester.requestFocus() } catch (_: Exception) {} }

    // Dynamic layout direction based on app language setting
    val context = LocalContext.current
    val appLang = remember {
        context.getSharedPreferences("lumina_settings", android.content.Context.MODE_PRIVATE)
            .getString("app_lang", "he") ?: "he"
    }
    val isHeb = appLang == "he"
    val layoutDir = if (isHeb) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorOledBlack)
            .onPreviewKeyEvent {
                lastInteractionTime = System.currentTimeMillis()
                if (isScreensaverActive) { isScreensaverActive = false; true } else false
            }
    ) {
        // ⚡ FEATURE 2: Dynamic Ambient Glow Crossfade
        val ambientGlow by remember { derivedStateOf { getAmbientGlow(focusedChannel?.name) } }
        Crossfade(targetState = ambientGlow, animationSpec = tween(1000), label = "ambient_glow") { glowColor ->
            Box(modifier = Modifier.fillMaxWidth().height(500.dp).background(Brush.verticalGradient(listOf(glowColor, Color.Transparent))))
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ─── TOP BAR: PS5 HORIZONTAL RIBBON ───
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 48.dp, end = 48.dp)) {
                // Always use English keys internally, translate only for display
                val builtinGroups = listOf(
                    "All" to (if (isHeb) "הכל" else "All"),
                    "Favorites" to (if (isHeb) "מועדפים" else "Favorites")
                )
                val userGroups = groups.filter { it != "All" && it != "Favorites" }.map { it to it }
                val systemGroups = listOf(
                    "EPG Guide" to (if (isHeb) "מדריך שידורים" else "EPG Guide"),
                    "Settings" to (if (isHeb) "הגדרות" else "Settings")
                )
                val displayGroups = builtinGroups + userGroups + systemGroups

                Ps5CategoryRibbon(
                    groups = displayGroups,
                    selectedGroup = selectedGroup,
                    onGroupSelect = { viewModel.selectGroup(it) },
                    gridFocusRequester = gridFocusRequester,
                    firstItemFocusRequester = categoryFocusRequester,
                    modifier = Modifier
                )
            }

            // ─── MIDDLE: MASSIVE HERO SECTION ───
            Ps5HeroSection(channel = focusedChannel, epg = focusedEpg)

            // ─── BOTTOM: CINEMATIC CONTENT GRID ───
            Box(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, bottom = 48.dp)) {
                Crossfade(targetState = selectedGroup, animationSpec = tween(400), label = "fade") { currentGroup ->
                    when {
                        currentGroup == "Settings" -> {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(ColorPs5Gray)) {
                                IptvSettingsScreen(
                                    activePlaylist = activePlaylist,
                                    onUpdatePlaylist = { name, url, epg -> viewModel.updatePlaylist(name, url, epg) },
                                    onDeletePlaylist = { viewModel.deletePlaylist() },
                                    onAddPlaylist = { viewModel.openQrSetup() }
                                )
                            }
                        }
                        currentGroup == "EPG Guide" -> {
                            EpgGuideScreen(
                                channels = channels, viewModel = viewModel, onPlayChannel = onPlayChannel,
                                // ⚡ FIX 3: Removed focusRequester from the wrapper
                                modifier = Modifier.focusGroup(),
                                firstItemFocus = gridFocusRequester
                            )
                        }
                        channels.isEmpty() -> { EmptyStateView(onSetupClick = { viewModel.openQrSetup() }) }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 280.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                gridItems(channels, key = { it.id }) { channel ->
                                    Ps5ChannelCard(
                                        channel = channel,
                                        isFavorite = channel.isFavorite,
                                        modifier = if (channel == channels.firstOrNull()) Modifier.focusRequester(gridFocusRequester) else Modifier,
                                        onClick = { onPlayChannel(channel.id) },
                                        onFocus = { focusedChannel = channel; viewModel.onChannelFocused(channel) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isScreensaverActive,
            enter = fadeIn(tween(1500)), exit = fadeOut(tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            CinematicScreensaver(channel = focusedChannel)
        }
    }
    } // end CompositionLocalProvider
}

// ─── PS5 COMPONENTS ───

@Composable
private fun Ps5CategoryRibbon(groups: List<Pair<String, String>>, selectedGroup: String, onGroupSelect: (String) -> Unit, gridFocusRequester: FocusRequester, firstItemFocusRequester: FocusRequester? = null, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth().focusProperties { down = gridFocusRequester },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(groups, key = { it.first }) { (key, displayName) ->
            val isSelected = key == selectedGroup
            val isFirstItem = key == "All"

            val isFocused = remember { mutableStateOf(false) }

            Surface(
                onClick = { onGroupSelect(key) },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) ColorTextMain else Color.Transparent,
                    focusedContainerColor = ColorTextMain,
                    contentColor = if (isSelected) Color.Black else ColorTextMain.copy(0.5f),
                    focusedContentColor = Color.Black
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border.None, border = Border.None),
                modifier = Modifier
                    .then(if (isFirstItem && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                    .onFocusChanged { isFocused.value = it.isFocused }
            ) {
                Text(
                    text = displayName,
                    fontWeight = if (isFocused.value || isSelected) FontWeight.Black else FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun Ps5HeroSection(channel: ChannelEntity?, epg: EpgProgramEntity?) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 56.dp, vertical = 32.dp)) {
        if (channel != null) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Text(text = channel.name, color = ColorTextMain, fontSize = 56.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-1).sp)

                if (epg != null) {
                    val currentTime = System.currentTimeMillis()
                    val isLive = currentTime in epg.startTime..epg.endTime

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.background(ColorLiveBlue.copy(0.2f), RoundedCornerShape(4.dp)).border(1.dp, ColorLiveBlue, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "עכשיו בשידור" else "NOW PLAYING", color = ColorLiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = epg.title, color = ColorTextMain.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // ⚡ FEATURE 3: Live TV Progress Bar
                    if (isLive && epg.startTime > 0) {
                        val progress = ((currentTime - epg.startTime).toFloat() / (epg.endTime - epg.startTime).toFloat()).coerceIn(0f, 1f)
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.4f).height(4.dp).background(ColorGlass, RoundedCornerShape(50))) {
                            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(ColorLiveBlue, RoundedCornerShape(50)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Ps5ChannelCard(channel: ChannelEntity, isFavorite: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit, onFocus: () -> Unit, onFavoriteToggle: () -> Unit = {}) {
    val isFocused = remember { mutableStateOf(false) }
    val imageLoadFailed = remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f/9f)
            .onPreviewKeyEvent { event ->
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    onFavoriteToggle(); true
                } else false
            }
            .onFocusChanged {
                isFocused.value = it.isFocused
                if (it.isFocused) onFocus()
            },
        colors = ClickableSurfaceDefaults.colors(containerColor = ColorPs5Gray, focusedContainerColor = ColorPs5Gray),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp), focusedShape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White)), border = Border(BorderStroke(0.dp, Color.Transparent))),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.25f), elevation = 24.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(0.05f), Color.Transparent), radius = 400f)))
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                if (channel.logoUrl.isNotBlank() && !imageLoadFailed.value) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).size(300).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Fit,
                        onState = { if (it is AsyncImagePainter.State.Error) imageLoadFailed.value = true },
                        modifier = Modifier.size(90.dp).alpha(if (isFocused.value) 1f else 0.7f)
                    )
                } else {
                    Text(text = channel.name.take(1).uppercase(), color = ColorTextMain.copy(alpha = if(isFocused.value) 1f else 0.5f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                }
            }
            // Favorite heart icon — top end corner
            if (isFavorite || isFocused.value) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(if (isFavorite) Color(0xFFFF2D55).copy(0.9f) else Color.Black.copy(0.5f), CircleShape)
                        .size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(if(isFocused.value) 0.8f else 0.4f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = channel.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextMain, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (channel.number > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "ערוץ ${channel.number}" else "CH ${channel.number}", color = ColorTextMain.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ─── EPG WITH DVR CAPABILITIES ───

@Composable
fun EpgGuideScreen(channels: List<ChannelEntity>, viewModel: IptvViewModel, onPlayChannel: (String) -> Unit, modifier: Modifier = Modifier, firstItemFocus: FocusRequester) {
    if (channels.isEmpty()) return
    val currentTime = System.currentTimeMillis()

    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 64.dp)) {
        itemsIndexed(channels.take(50), key = { _, it -> it.id }) { index, channel ->
            var programs by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }
            LaunchedEffect(channel) {
                delay(index * 30L)
                programs = viewModel.getProgramsForChannel(channel, currentTime)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(220.dp).height(80.dp).background(ColorGlass, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.logoUrl.isNotBlank()) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).size(150).build(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp).padding(end = 12.dp))
                        Text(text = channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (programs.isEmpty()) {
                        item { EpgProgramBlock(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "אין לוח שידורים" else "No Schedule Available", 0L, 0L, false, false, currentTime, { onPlayChannel(channel.id) }, if (index == 0) firstItemFocus else null) }
                    } else {
                        itemsIndexed(programs, key = { i, prog -> "${prog.startTime}_$i" }) { i, prog ->
                            val isLive = currentTime in prog.startTime..prog.endTime
                            val isPast = prog.endTime < currentTime
                            EpgProgramBlock(prog.title, prog.startTime, prog.endTime, isLive, isPast, currentTime, {
                                // In the future: if (isPast) onPlayCatchup(channel.id, prog.startTime) else onPlayChannel(channel.id)
                                onPlayChannel(channel.id)
                            }, if (index == 0 && i == 0) firstItemFocus else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpgProgramBlock(title: String, startTime: Long, endTime: Long, isLive: Boolean, isPast: Boolean, currentTime: Long, onClick: () -> Unit, itemFocus: FocusRequester?) {
    // ⚡ UNUSED VARIABLE REMOVED FROM HERE
    val durationMinutes = if (startTime == 0L) 60 else ((endTime - startTime) / 60000)
    val widthDp = (durationMinutes * 6).toInt().coerceIn(240, 600).dp
    val timeString = if (startTime == 0L) "--:--" else "${epgBlockTimeFormat.format(Date(startTime))} - ${epgBlockTimeFormat.format(Date(endTime))}"

    var modifier = Modifier.width(widthDp).height(80.dp) // ⚡ MODIFIER CLEANED UP
    if (itemFocus != null) modifier = modifier.focusRequester(itemFocus)

    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp), focusedShape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isLive) ColorLiveBlue.copy(alpha = 0.15f) else if (isPast) ColorGlass else ColorPs5Gray,
            focusedContainerColor = ColorPs5Gray
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)), border = Border(BorderStroke(0.dp, Color.Transparent)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 14.dp), verticalArrangement = Arrangement.Top) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = timeString, color = if (isLive) ColorLiveBlue else Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (isLive) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.background(ColorLiveBlue, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "שידור חי" else "LIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    } else if (isPast) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.background(ColorCatchup.copy(0.2f), RoundedCornerShape(4.dp)).border(1.dp, ColorCatchup, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "↺ הקלטה" else "↺ CATCHUP", color = ColorCatchup, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isLive && startTime > 0) {
                val progress = ((currentTime - startTime).toFloat() / (endTime - startTime).toFloat()).coerceIn(0f, 1f)
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(ColorGlass)) {
                    Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(ColorLiveBlue))
                }
            }
        }
    }
}

// ─── ⚡ FEATURE 10: CINEMATIC SCREENSAVER ───

@Composable
fun CinematicScreensaver(channel: ChannelEntity?) {
    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val format = SimpleDateFormat("HH:mm", Locale.getDefault()); while (true) { timeStr = format.format(Date()); delay(1000) } }

    Box(modifier = Modifier.fillMaxSize().background(ColorOledBlack), contentAlignment = Alignment.Center) {

        // Panning Background Glow
        val infiniteTransition = rememberInfiniteTransition(label = "pan")
        val alpha by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 0.5f, animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "glow")

        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(getAmbientGlow(channel?.name).copy(alpha = alpha), Color.Transparent), radius = 800f)))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = timeStr, color = ColorTextMain, fontSize = 120.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp)
            if (channel != null) {
                Spacer(modifier = Modifier.height(32.dp))
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).crossfade(true).build(), contentDescription = null, modifier = Modifier.height(80.dp).alpha(0.6f))
                } else {
                    Text(text = channel.name, color = ColorTextMain.copy(0.4f), fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }
        }
    }
}

// --- SETUP OVERLAYS ---

@Composable
private fun EmptyStateView(onSetupClick: () -> Unit) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(24.dp)); Text(if (isHeb) "לא נמצאו ערוצים" else "No Channels Found", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "הגדר את הפלייליסט שלך כדי להתחיל לצפות." else "Configure your playlist to start watching.", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(40.dp))
            Surface(onClick = onSetupClick, colors = ClickableSurfaceDefaults.colors(containerColor = ColorGlass, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)) {
                Text(if (isHeb) "הגדר IPTV" else "Setup IPTV", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp))
            }
        }
    }
}

@Composable
fun CrispTextField(value: String, label: String, onValueChange: (String) -> Unit, isLast: Boolean = false) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.Bold, color = Color.White.copy(0.6f)) },
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown) { when (event.nativeKeyEvent.keyCode) { KeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }; KeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }; else -> false } } else false },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.4f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White, focusedContainerColor = Color.White.copy(alpha = 0.1f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f))
    )
}

@Composable
fun IptvSetupOverlay(ipAddress: String, onClose: () -> Unit, onManualSubmit: (String, String, String) -> Unit) {
    val isHeb = LocalLayoutDirection.current == LayoutDirection.Rtl
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    val isError = ipAddress.contains("ERROR", ignoreCase = true)
    val qrBitmap = remember(ipAddress) { if (!isError) { QrCodeGenerator.generate(ipAddress, 512).asImageBitmap() } else null }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xD9000000)).onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)) { onClose(); true } else false }, contentAlignment = Alignment.Center) {
        Row(modifier = Modifier.width(880.dp).height(460.dp).background(ColorPs5Gray, RoundedCornerShape(32.dp)).border(1.dp, ColorGlass, RoundedCornerShape(32.dp)).padding(32.dp)) {
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight().padding(end = 32.dp), verticalArrangement = Arrangement.Center) {
                Text(if (isHeb) "הגדרה ידנית" else "Manual Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "הזן את פרטי ספק ה-IPTV שלך למטה." else "Enter your IPTV provider details below.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp); Spacer(modifier = Modifier.height(24.dp))
                CrispTextField(value = name, label = if (isHeb) "שם פלייליסט (אופציונלי)" else "Playlist Name (Optional)", onValueChange = { name = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = url, label = if (isHeb) "קישור M3U (נדרש)" else "M3U Link (Required)", onValueChange = { url = it }); Spacer(modifier = Modifier.height(16.dp)); CrispTextField(value = epg, label = if (isHeb) "קישור EPG (אופציונלי)" else "EPG Link (Optional)", onValueChange = { epg = it }, isLast = true); Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { onManualSubmit(name, url, epg) }, colors = ButtonDefaults.colors(containerColor = ColorGlass, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black), shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)), modifier = Modifier.fillMaxWidth().height(48.dp)) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (isHeb) "התחבר עכשיו" else "Connect Now", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } }
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ColorGlass))
            Column(modifier = Modifier.weight(0.8f).fillMaxHeight().padding(start = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isHeb) "הגדרה מהירה" else "Quick Setup", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "סרוק את קוד ה-QR עם הטלפון שלך." else "Scan the QR code with your phone.", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(12.dp)).border(2.dp, if (isError) Color(0xFFFF453A) else Color(0xFF32D74B), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (isError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tv, contentDescription = "Error", tint = Color(0xFFFF453A), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp)); Text(if (isHeb) "אין רשת" else "No Network", color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    } else if (qrBitmap != null) { Image(bitmap = qrBitmap, contentDescription = "QR Code", modifier = Modifier.padding(8.dp).fillMaxSize()) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (!isError) { Text(if (isHeb) "או פתח את הכתובת הזו בטלפון:" else "Or open this address on your phone:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp); Spacer(modifier = Modifier.height(4.dp)); Text(text = ipAddress, color = Color(0xFF32D74B), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                else { Text(if (isHeb) "חבר את הטלוויזיה ל-Wi-Fi" else "Please connect TV to Wi-Fi", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) }
            }
        }
    }
}