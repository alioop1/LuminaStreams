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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
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
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG          = Color(0xFF07070A)
private val SURFACE     = Color(0xFF1A1A24)
private val ACCENT      = Color(0xFF007AFF)
private val ACCENT_RED  = Color(0xFFFF3B30)
private val WHITE       = Color(0xFFFFFFFF)
private val MUTED       = Color(0x88FFFFFF)
private val HUD_BG      = Color(0xD80A0A10)
private val LIVE_RED    = Color(0xFFFF3B30)
// ══════════════════════════════════════════════════════════════════
//  ROOT SCREEN: VOD DASHBOARD STYLE & IMMERSIVE ZAPPING
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvScreen(viewModel: IptvViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val exo = remember { ExoPlayerWrapper(context) }
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()

    var isFullScreen by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    var showToast by remember { mutableStateOf("") }

    // Zapping States
    var showZappingBar by remember { mutableStateOf(false) }
    var showSideMenu   by remember { mutableStateOf(false) }
    var lastActionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val scope = rememberCoroutineScope()

    val topBarFR = remember { FocusRequester() }
    val gridFR = remember { FocusRequester() }
    val playerFR = remember { FocusRequester() }
    val zappingFR = remember { FocusRequester() }
    val sideMenuFR = remember { FocusRequester() }

    val isAnyDialogOpen = state.loadState is IptvLoadState.Loading || state.showAddPlaylist ||
            state.showQrCode || state.showSettings || state.showSleepTimerPicker || state.showParentalPinEntry || state.showEpgGuide

    fun toast(msg: String) { showToast = msg; scope.launch { delay(2500); showToast = "" } }
    fun resetIdleTimer() { lastActionTime = System.currentTimeMillis() }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    LaunchedEffect(Unit) {
        delay(200)
        if (state.channels.isNotEmpty()) runCatching { gridFR.requestFocus() }
        else runCatching { topBarFR.requestFocus() }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            state.currentChannel?.let { ch -> exo.prepareStream(ch.streamUrl); exo.play() }
            delay(100)
            runCatching { playerFR.requestFocus() }
        } else {
            exo.pause()
            exo.player.clearVideoSurface()
            showZappingBar = false
            showSideMenu = false
            delay(100)
            runCatching { gridFR.requestFocus() }
        }
    }

    // Auto-hide Zapping HUD
    LaunchedEffect(lastActionTime, showZappingBar, showSideMenu) {
        if (showZappingBar || showSideMenu) {
            delay(5000L)
            showZappingBar = false
            showSideMenu = false
            if (isFullScreen && !isAnyDialogOpen) runCatching { playerFR.requestFocus() }
        }
    }

    LaunchedEffect(showZappingBar) { if (showZappingBar) { delay(50); runCatching { zappingFR.requestFocus() } } }
    LaunchedEffect(showSideMenu)   { if (showSideMenu)   { delay(50); runCatching { sideMenuFR.requestFocus() } } }

    BackHandler {
        when {
            state.showEpgGuide         -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            state.showSettings         -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showAddPlaylist      -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showQrCode           -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showSleepTimerPicker -> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            showZappingBar || showSideMenu -> {
                showZappingBar = false; showSideMenu = false; runCatching { playerFR.requestFocus() }
            }
            isFullScreen               -> isFullScreen = false
            else                       -> onNavigateBack()
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

        // ── 1. DASHBOARD VIEW ──
        AnimatedVisibility(
            visible = !isFullScreen,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Column(Modifier.fillMaxSize()) {
                TopNavBar(
                    state = state,
                    topBarFR = topBarFR,
                    onBack = onNavigateBack,
                    onSettings = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                    onEpgGuide = { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                    onAddPlaylist = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) }
                )

                if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                    IptvEmptyState(
                        onAddClick = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) },
                        onSettingsClick = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                        emptyStateFR = gridFR
                    )
                } else {
                    HeroEpgSection(
                        channel = focusedChannel ?: state.channels.firstOrNull(),
                        epgData = state.epgData,
                        logos = state.channelLogos
                    )

                    ChannelsDashboard(
                        state = state,
                        gridFR = gridFR,
                        onChannelFocused = { focusedChannel = it },
                        onChannelClicked = { ch ->
                            viewModel.onEvent(IptvEvent.SelectChannel(ch))
                            isFullScreen = true
                        }
                    )
                }
            }
        }

        // ── 2. FULLSCREEN PLAYER & ZAPPING ──
        if (isFullScreen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(playerFR)
                    .focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && !isAnyDialogOpen) {
                            resetIdleTimer()
                            when (ev.key.nativeKeyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (!showZappingBar && !showSideMenu) {
                                        showZappingBar = true; return@onPreviewKeyEvent true
                                    }
                                    false
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (!showZappingBar && !showSideMenu) {
                                        showSideMenu = true; return@onPreviewKeyEvent true
                                    }
                                    false
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    if (!showZappingBar && !showSideMenu) {
                                        showZappingBar = true; return@onPreviewKeyEvent true
                                    }
                                    false
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
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

                // Top Info Bar (Zapping)
                AnimatedVisibility(
                    visible = (showZappingBar || showSideMenu) && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter = fadeIn(tween(300)) + slideInVertically { -it/2 },
                    exit = fadeOut(tween(300)) + slideOutVertically { -it/2 },
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(10f)
                ) {
                    var timeString by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            delay(1000)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(0.8f), Color.Transparent))).padding(horizontal = 48.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.selectedGroup.uppercase(), color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                                modifier = Modifier.size(36.dp)) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp)) }
                            }
                            Surface(onClick = { viewModel.onEvent(IptvEvent.ShowIptvSettings) }, shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                                modifier = Modifier.size(36.dp)) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) }
                            }
                            Text(timeString, color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bottom Zapping Bar
                AnimatedVisibility(
                    visible = showZappingBar && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
                ) {
                    ZappingHud(
                        channels = state.filteredChannels,
                        currentChannel = state.currentChannel,
                        epgData = state.epgData,
                        logos = state.channelLogos,
                        zappingFR = zappingFR,
                        onSelectChannel = { ch ->
                            if (state.currentChannel?.id != ch.id) viewModel.onEvent(IptvEvent.SelectChannel(ch))
                            showZappingBar = false
                            runCatching { playerFR.requestFocus() }
                        },
                        onIdleReset = ::resetIdleTimer
                    )
                }

                // Side Menu
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                AnimatedVisibility(
                    visible = showSideMenu && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter = slideInHorizontally(initialOffsetX = { if (isRtl) it else -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { if (isRtl) it else -it }) + fadeOut(),
                    modifier = Modifier.align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart).zIndex(30f)
                ) {
                    SideGroupMenu(
                        groups = state.groups,
                        selectedGroup = state.selectedGroup,
                        sideMenuFR = sideMenuFR,
                        onSelectGroup = { g ->
                            viewModel.onEvent(IptvEvent.SelectGroup(g))
                            showSideMenu = false
                            showZappingBar = true
                        },
                        onIdleReset = ::resetIdleTimer
                    )
                }
            }
        }

        // ── 3. DIALOGS (With Focus Requesters!) ──
        if (state.loadState is IptvLoadState.Loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(200f), Alignment.Center) {
                com.luminastreams.tv.ui.components.LoadingIndicator()
            }
        }

        if (state.showAddPlaylist) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideAddPlaylist) }) { fr ->
                PlaylistManagerDialog(state, fr, viewModel::onEvent)
            }
        }

        if (state.showSettings) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideIptvSettings) }) { fr ->
                SmartSettingsDialog(state, exo, fr, viewModel::onEvent)
            }
        }

        if (state.showEpgGuide) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideEpgGuide) }) { fr ->
                FullEpgGuideDialog(state, viewModel, fr)
            }
        }

        if (state.showQrCode && state.qrCodeChannel != null) {
            IptvDialog({ viewModel.onEvent(IptvEvent.HideQrCode) }) { fr ->
                ChannelQrDialog(state.qrCodeChannel!!, fr) { viewModel.onEvent(IptvEvent.HideQrCode) }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = showToast.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).zIndex(100f)
        ) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(WHITE.copy(0.15f)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(showToast, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  DIALOG WRAPPER (Auto-Focus)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun IptvDialog(onDismiss: () -> Unit, content: @Composable (FocusRequester) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
            .focusGroup().focusRestorer(), Alignment.Center) {
            content(fr)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  TOP NAV BAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun TopNavBar(
    state: IptvState,
    topBarFR: FocusRequester,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onEpgGuide: () -> Unit,
    onAddPlaylist: () -> Unit
) {
    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    Row(
        Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(onClick = onBack, shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            modifier = Modifier.size(42.dp).focusRequester(topBarFR)) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.ArrowBack, null, Modifier.size(20.dp)) }
        }

        Text("Lumina IPTV", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

        Spacer(Modifier.weight(1f))

        Surface(onClick = onEpgGuide, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            modifier = Modifier.height(36.dp)) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                Text("TV Guide", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(onClick = onAddPlaylist, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(containerColor = ACCENT.copy(0.2f), focusedContainerColor = ACCENT, contentColor = ACCENT, focusedContentColor = WHITE),
            modifier = Modifier.height(36.dp)) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.PlaylistAdd, null, Modifier.size(16.dp))
                Text("Manage Links", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(onClick = onSettings, shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            modifier = Modifier.size(42.dp)) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Settings, null, Modifier.size(20.dp)) }
        }

        Text(timeString, color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
    }
}

// ══════════════════════════════════════════════════════════════════
//  HERO EPG SECTION (No Overdraw)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun HeroEpgSection(
    channel: IptvChannel?,
    epgData: Map<String, List<EpgProgram>>,
    logos: Map<String, String>
) {
    if (channel == null) return
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val epgs = remember(channel.id, epgData) { epgData[channel.tvgId.lowercase()] ?: epgData[channel.id.lowercase()] }
    val nowProg = remember(epgs) { epgs?.firstOrNull { it.isLiveNow } }
    val nextProg = remember(epgs) { epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow } }

    val logo = channel.logoUrl.ifBlank { logos[channel.tvgId.lowercase()] ?: logos[channel.id.lowercase()] ?: "" }

    Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 48.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF14141E)), Alignment.Center) {
                if (logo.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(logo)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.7f),
                        error = { Text(channel.name.take(2).uppercase(), color = WHITE, fontSize = 42.sp, fontWeight = FontWeight.Black) }
                    )
                } else {
                    Text(channel.name.take(2).uppercase(), color = WHITE, fontSize = 42.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.clip(CircleShape).background(ACCENT_RED).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("LIVE", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Text(channel.groupTitle, color = MUTED, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("${channel.number} • ${channel.name}", color = WHITE, fontSize = 36.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(16.dp))
                if (nowProg != null) {
                    Text(nowProg.title, color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${tf.format(Date(nowProg.startTime))} - ${tf.format(Date(nowProg.endTime))}", color = MUTED, fontSize = 13.sp)
                        Box(Modifier.width(200.dp).height(4.dp).clip(CircleShape).background(WHITE.copy(0.2f))) {
                            Box(Modifier.fillMaxWidth(nowProg.progressFraction).fillMaxHeight().background(WHITE))
                        }
                        Text("${nowProg.remainingMinutes}m left", color = MUTED, fontSize = 13.sp)
                    }
                    if (nextProg != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Next: ${nextProg.title} (${tf.format(Date(nextProg.startTime))})", color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(channel.groupTitle.ifBlank { "No EPG Data available" }, color = MUTED, fontSize = 16.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CHANNELS DASHBOARD (LazyRows)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ChannelsDashboard(
    state: IptvState,
    gridFR: FocusRequester,
    onChannelFocused: (IptvChannel) -> Unit,
    onChannelClicked: (IptvChannel) -> Unit
) {
    val listState = rememberLazyListState()
    val favorites = state.channels.filter { it.id in state.favoriteChannelIds }
    val groupedChannels = remember(state.channels) { state.channels.groupBy { it.groupTitle } }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().focusGroup()
    ) {
        if (favorites.isNotEmpty()) {
            item {
                HorizontalChannelRow(
                    title = "Favorites",
                    channels = favorites,
                    logos = state.channelLogos,
                    isFirstRow = true,
                    rowFR = gridFR,
                    onFocus = onChannelFocused,
                    onClick = onChannelClicked
                )
            }
        }

        state.groups.filter { it != "All" && it != "Favorites" && it != "Recent" }.forEachIndexed { index, group ->
            val chs = groupedChannels[group] ?: return@forEachIndexed
            item {
                HorizontalChannelRow(
                    title = group,
                    channels = chs,
                    logos = state.channelLogos,
                    isFirstRow = favorites.isEmpty() && index == 0,
                    rowFR = gridFR,
                    onFocus = onChannelFocused,
                    onClick = onChannelClicked
                )
            }
        }
    }
}

@Composable
private fun HorizontalChannelRow(
    title: String,
    channels: List<IptvChannel>,
    logos: Map<String, String>,
    isFirstRow: Boolean,
    rowFR: FocusRequester,
    onFocus: (IptvChannel) -> Unit,
    onClick: (IptvChannel) -> Unit
) {
    val rowState = rememberLazyListState()
    Column {
        Text(title, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "ChannelCard" }) { idx, ch ->
                val logo = ch.logoUrl.ifBlank { logos[ch.tvgId.lowercase()] ?: logos[ch.id.lowercase()] ?: "" }
                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onClick(ch) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = SURFACE,
                        focusedContainerColor = WHITE,
                        contentColor = WHITE,
                        focusedContentColor = BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(16f / 9f)
                        .let { if (isFirstRow && idx == 0) it.focusRequester(rowFR) else it }
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) onFocus(ch)
                        }
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        if (logo.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logo)
                                    .bitmapConfig(Bitmap.Config.RGB_565)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.6f),
                                error = { Text(ch.name.take(2).uppercase(), color = if(isFocused) BG else WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black) }
                            )
                        } else {
                            Text(ch.name.take(2).uppercase(), color = if(isFocused) BG else WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  ZAPPING HUD & SIDE MENU
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ZappingHud(
    channels: List<IptvChannel>,
    currentChannel: IptvChannel?,
    epgData: Map<String, List<EpgProgram>>,
    logos: Map<String, String>,
    zappingFR: FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit,
    onIdleReset: () -> Unit
) {
    val listState = rememberLazyListState()
    var focusedChannel by remember { mutableStateOf(currentChannel) }

    LaunchedEffect(Unit) {
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }.coerceAtLeast(0)
        listState.scrollToItem(idx)
        focusedChannel = channels.getOrNull(idx)
    }

    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, HUD_BG, Color.Black))).padding(bottom = 36.dp, top = 64.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 48.dp)) {
                focusedChannel?.let { fCh ->
                    val epgs = epgData[fCh.tvgId.lowercase()] ?: epgData[fCh.id.lowercase()]
                    val nowProg = epgs?.firstOrNull { it.isLiveNow }
                    val nextProg = epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow }

                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${fCh.number}", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text(fCh.name, color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (fCh.resolution.isNotBlank()) {
                                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(WHITE.copy(0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(fCh.resolution, color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))

                        if (nowProg != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(nowProg.title, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Box(Modifier.width(180.dp).height(4.dp).clip(CircleShape).background(WHITE.copy(0.2f))) {
                                    Box(Modifier.fillMaxWidth(nowProg.progressFraction).fillMaxHeight().background(WHITE))
                                }
                                Text("${nowProg.remainingMinutes}m left", color = MUTED, fontSize = 13.sp)
                                if (nextProg != null) {
                                    Text(" •  Next: ${nextProg.title}", color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Text(fCh.groupTitle.ifBlank { "No program info available" }, color = MUTED, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().focusGroup()
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "zapCard" }) { idx, ch ->
                    ZappingCard(
                        channel = ch,
                        logos = logos,
                        isCurrent = ch.id == currentChannel?.id,
                        modifier = if (idx == channels.indexOfFirst { it.id == currentChannel?.id }.coerceAtLeast(0)) Modifier.focusRequester(zappingFR) else Modifier,
                        onFocus = { focusedChannel = ch; onIdleReset() },
                        onClick = { onSelectChannel(ch); onIdleReset() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ZappingCard(
    channel: IptvChannel,
    logos: Map<String, String>,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val logo = channel.logoUrl.ifBlank { logos[channel.tvgId.lowercase()] ?: logos[channel.id.lowercase()] ?: "" }
    val ctx = LocalContext.current

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent) WHITE.copy(0.15f) else Color(0x22FFFFFF),
            focusedContainerColor = WHITE,
            contentColor = WHITE,
            focusedContentColor = BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = modifier.width(140.dp).aspectRatio(16f / 9f).onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocus()
        }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (logo.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = remember(logo) {
                        ImageRequest.Builder(ctx).data(logo).bitmapConfig(Bitmap.Config.RGB_565).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.ENABLED).allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.65f),
                    error = { Text(channel.name.take(2).uppercase(), color = if(isFocused) BG else WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black) }
                )
            } else {
                Text(channel.name.take(2).uppercase(), color = if(isFocused) BG else WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            if (isCurrent && !isFocused) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).background(LIVE_RED, CircleShape))
        }
    }
}

@Composable
private fun SideGroupMenu(
    groups: List<String>, selectedGroup: String, sideMenuFR: FocusRequester,
    onSelectGroup: (String) -> Unit, onIdleReset: () -> Unit
) {
    Box(Modifier.width(280.dp).fillMaxHeight().background(HUD_BG)) {
        LazyColumn(contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().focusGroup()) {
            item { Text("CATEGORIES", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) }
            items(groups) { group ->
                val isSel = group == selectedGroup
                Surface(
                    onClick = { onSelectGroup(group) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSel) WHITE.copy(0.15f) else Color.Transparent,
                        focusedContainerColor = WHITE,
                        contentColor = if (isSel) WHITE else MUTED,
                        focusedContentColor = BG
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .let { if(isSel) it.focusRequester(sideMenuFR) else it }
                        .onFocusChanged { if (it.isFocused) onIdleReset() }
                ) {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), Alignment.CenterStart) { Text(group, fontSize = 15.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FULL EPG GUIDE DIALOG (Restored & Fixed)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FullEpgGuideDialog(state: IptvState, viewModel: IptvViewModel, fr: FocusRequester) {
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var selectedCh by remember { mutableStateOf(state.currentChannel ?: state.filteredChannels.firstOrNull()) }

    // Safely fetch programs
    val programs = remember(selectedCh, state.epgData) {
        if (selectedCh != null) {
            val tvg = selectedCh!!.tvgId.lowercase()
            val chId = selectedCh!!.id.lowercase()
            state.epgData[tvg] ?: state.epgData[chId] ?: emptyList()
        } else emptyList()
    }

    Box(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF14141E)).padding(24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Full TV Guide", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Surface(onClick = { viewModel.onEvent(IptvEvent.RefreshEpg) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFFFFD60A).copy(0.15f), focusedContainerColor = Color(0xFFFFD60A), contentColor = Color(0xFFFFD60A), focusedContentColor = BG),
                    modifier = Modifier.height(32.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                        Text("Force Sync EPG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Channel list
                LazyColumn(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(state.filteredChannels, key = { _, ch -> ch.id }) { idx, ch ->
                        val isSel = ch.id == selectedCh?.id
                        Surface(onClick = { selectedCh = ch },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) WHITE.copy(0.12f) else Color(0x1AFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .let { if (idx == 0) it.focusRequester(fr) else it }
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val logo = ch.logoUrl.ifBlank { state.channelLogos[ch.tvgId.lowercase()] ?: state.channelLogos[ch.id.lowercase()] ?: "" }
                                if (logo.isNotBlank()) {
                                    SubcomposeAsyncImage(
                                        model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp),
                                        error = { Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x22FFFFFF)), Alignment.Center) { Text(ch.name.take(2).uppercase(), color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                                    )
                                } else {
                                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x22FFFFFF)), Alignment.Center) {
                                        Text(ch.name.take(2).uppercase(), color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(ch.name, color = WHITE, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                // Programs list
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF))) {
                    if (programs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No EPG data available for this channel", color = MUTED, fontSize = 14.sp) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(programs.sortedBy { it.startTime }, key = { "${it.startTime}_${it.channelId}" }) { p ->
                                val isLive = p.isLiveNow; val isPast = p.isPast
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(when { isLive -> WHITE.copy(0.15f); isPast -> Color.Transparent; else -> Color(0x08FFFFFF) })
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {

                                    if (isLive) Box(Modifier.size(8.dp).background(LIVE_RED, CircleShape)) else Spacer(Modifier.size(8.dp))

                                    Column(Modifier.width(60.dp)) {
                                        Text(tf.format(Date(p.startTime)), color = if (isLive) LIVE_RED else if (isPast) MUTED else WHITE, fontSize = 14.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
                                        Text("${p.durationMinutes}m", color = MUTED, fontSize = 11.sp)
                                    }
                                    Box(Modifier.width(2.dp).height(24.dp).background(if (isLive) LIVE_RED else Color(0x1AFFFFFF)))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.title, color = if (isPast) MUTED else WHITE, fontSize = 15.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (p.category.isNotBlank()) Text(p.category, color = MUTED, fontSize = 11.sp, maxLines = 1)
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

// ══════════════════════════════════════════════════════════════════
//  PLAYLIST MANAGER & SETTINGS (Dialogs)
// ══════════════════════════════════════════════════════════════════
@Composable
fun PlaylistManagerDialog(state: IptvState, fr: FocusRequester, onEvent: (IptvEvent) -> Unit) {
    Box(Modifier.width(600.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF14141E)).padding(32.dp)) {
        Column {
            Text("Manage Links & Playlists", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))

            Text("Playlist Name", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            DialogInput(state.addPlaylistName, "e.g. My Premium List", fr) { onEvent(IptvEvent.UpdateAddPlaylistName(it)) }
            Spacer(Modifier.height(16.dp))

            Text("M3U / M3U8 URL", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            DialogInput(state.addPlaylistUrl, "http://.../playlist.m3u8") { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
            Spacer(Modifier.height(16.dp))

            Text("EPG XML URL (Optional)", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            DialogInput(state.addPlaylistEpgUrl, "http://.../epg.xml") { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { onEvent(IptvEvent.ConfirmAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = ACCENT, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                    modifier = Modifier.weight(1f).height(48.dp)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Save & Connect", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }

                // Existing Playlists Delete Block
                state.playlists.firstOrNull { it.isActive }?.let { activePl ->
                    Surface(onClick = { onEvent(IptvEvent.DeletePlaylist(activePl.id)) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = LIVE_RED.copy(0.2f), focusedContainerColor = LIVE_RED, contentColor = LIVE_RED, focusedContentColor = WHITE),
                        modifier = Modifier.height(48.dp)) {
                        Box(Modifier.padding(horizontal = 24.dp).fillMaxHeight(), Alignment.Center) { Text("Delete Current", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartSettingsDialog(state: IptvState, exo: ExoPlayerWrapper, fr: FocusRequester, onEvent: (IptvEvent) -> Unit) {
    Box(Modifier.width(400.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF14141E)).padding(32.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Playback Settings", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))

            Surface(onClick = { onEvent(IptvEvent.ToggleSubtitles) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(fr)) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtitles / CC", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if (state.subtitlesEnabled) "ON" else "OFF", color = if (state.subtitlesEnabled) ACCENT else MUTED, fontWeight = FontWeight.Bold)
                }
            }

            Surface(onClick = { /* TODO Audio Track */ }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Audio Track", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.Settings, null)
                }
            }

            Surface(onClick = { onEvent(IptvEvent.ShowAddPlaylist) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Edit Playlist URL", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.Edit, null)
                }
            }
        }
    }
}

@Composable
private fun DialogInput(value: String, hint: String, focusRequester: FocusRequester? = null, onValueChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = WHITE, fontSize = 14.sp), cursorBrush = SolidColor(WHITE),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isFocused) Color(0xFF1E1E2E) else Color(0x1AFFFFFF))
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = MUTED, fontSize = 14.sp)
                    inner()
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it })
}

@Composable
private fun ChannelQrDialog(channel: IptvChannel, fr: FocusRequester, onDismiss: () -> Unit) {
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 320) }
    Box(Modifier.width(360.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF12121E)).padding(28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Stream QR", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(WHITE).padding(8.dp)) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(16.dp))
            Surface(onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(42.dp).focusRequester(fr)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════════════
@Composable
fun IptvEmptyState(onAddClick: () -> Unit, onSettingsClick: () -> Unit, emptyStateFR: FocusRequester) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LiveTv, null, Modifier.size(80.dp), tint = Color(0x88FFFFFF))
        Spacer(Modifier.height(20.dp))
        Text("No Channels Loaded", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Text("Add an M3U playlist to start watching Live TV.\nImmersive Zapping is ready.", color = Color(0x88FFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 24.dp)) {
            Surface(
                onClick = onAddClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF007AFF), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                modifier = Modifier.height(52.dp).focusRequester(emptyStateFR)
            ) {
                Row(Modifier.padding(horizontal = 24.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Text("Add Playlist", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                onClick = onSettingsClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                modifier = Modifier.height(52.dp)
            ) {
                Row(Modifier.padding(horizontal = 24.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Settings, null, Modifier.size(20.dp))
                    Text("Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}