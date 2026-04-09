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
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.presentation.player.ExoPlayerWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Palette
private val BG       = Color(0xFF07070A)
private val SURFACE  = Color(0xFF12121C)
private val SURFACE2 = Color(0xFF1C1C2A)
private val ACCENT   = Color(0xFF3D8BFF)
private val ACCENT2  = Color(0xFF00D4FF)
private val RED      = Color(0xFFFF3B30)
private val WHITE    = Color(0xFFFFFFFF)
private val MUTED    = Color(0x99FFFFFF)
private val MUTED2   = Color(0x33FFFFFF)
private val HUD_BG   = Color(0xF00A0A12)
private val CARD_BG  = Color(0xFF16161F)
private val GREEN    = Color(0xFF30D158)

// ═══════════════════════════════════════════════
// ROOT SCREEN
// ═══════════════════════════════════════════════
@Composable
fun IptvScreen(viewModel: IptvViewModel, onNavigateBack: () -> Unit) {
    val state       by viewModel.state.collectAsState()
    val context     = LocalContext.current
    val exo         = remember { ExoPlayerWrapper(context) }
    val videoAR     by exo.videoAspectRatio.collectAsState()
    val currTracks  by exo.currentTracks.collectAsState()
    val scope       = rememberCoroutineScope()

    var isFullScreen   by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    var showToast      by remember { mutableStateOf("") }
    var showZapping    by remember { mutableStateOf(false) }
    var showSideMenu   by remember { mutableStateOf(false) }
    var lastAction     by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Focus requesters – one per focusable "zone"
    val topBarFR  = remember { FocusRequester() }
    val epgBtnFR  = remember { FocusRequester() }
    val addBtnFR  = remember { FocusRequester() }
    val setgBtnFR = remember { FocusRequester() }
    val gridFR    = remember { FocusRequester() }
    val playerFR  = remember { FocusRequester() }
    val zappingFR = remember { FocusRequester() }
    val sideFR    = remember { FocusRequester() }

    val isAnyDialogOpen = state.loadState is IptvLoadState.Loading || state.showAddPlaylist
            || state.showQrCode || state.showSettings || state.showSleepTimerPicker
            || state.showParentalPinEntry || state.showEpgGuide

    fun toast(msg: String) { showToast = msg; scope.launch { delay(2500); showToast = "" } }
    fun resetIdle() { lastAction = System.currentTimeMillis() }

    val currChIdx by remember(state.currentChannel, state.filteredChannels) {
        derivedStateOf { state.filteredChannels.indexOfFirst { it.id == state.currentChannel?.id } }
    }

    fun switchUp() {
        val i = currChIdx; if (i > 0) {
            val ch = state.filteredChannels[i - 1]
            viewModel.onEvent(IptvEvent.SelectChannel(ch)); exo.prepareStream(ch.streamUrl); exo.play()
            toast("▲ ${ch.name}")
        }
    }
    fun switchDown() {
        val i = currChIdx; if (i < state.filteredChannels.size - 1) {
            val ch = state.filteredChannels[i + 1]
            viewModel.onEvent(IptvEvent.SelectChannel(ch)); exo.prepareStream(ch.streamUrl); exo.play()
            toast("▼ ${ch.name}")
        }
    }

    DisposableEffect(Unit) { onDispose { exo.release() } }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { if (state.channels.isNotEmpty()) gridFR.requestFocus() else topBarFR.requestFocus() }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            state.currentChannel?.let { exo.prepareStream(it.streamUrl); exo.play() }
            delay(80); runCatching { playerFR.requestFocus() }
        } else {
            exo.pause(); exo.player.clearVideoSurface()
            showZapping = false; showSideMenu = false
            delay(80); runCatching { gridFR.requestFocus() }
        }
    }

    LaunchedEffect(state.subtitlesEnabled) {
        exo.player.trackSelectionParameters = exo.player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !state.subtitlesEnabled).build()
    }

    // Auto-hide overlays after 6s idle
    LaunchedEffect(lastAction, showZapping, showSideMenu) {
        if ((showZapping || showSideMenu) && isFullScreen && !isAnyDialogOpen) {
            delay(6_000L); showZapping = false; showSideMenu = false
            runCatching { playerFR.requestFocus() }
        }
    }

    LaunchedEffect(showZapping)  { if (showZapping)  { delay(60); runCatching { zappingFR.requestFocus() } } }
    LaunchedEffect(showSideMenu) { if (showSideMenu) { delay(60); runCatching { sideFR.requestFocus()    } } }

    BackHandler {
        when {
            state.showEpgGuide         -> viewModel.onEvent(IptvEvent.HideEpgGuide)
            state.showSettings         -> viewModel.onEvent(IptvEvent.HideIptvSettings)
            state.showAddPlaylist      -> viewModel.onEvent(IptvEvent.HideAddPlaylist)
            state.showQrCode           -> viewModel.onEvent(IptvEvent.HideQrCode)
            state.showSleepTimerPicker -> viewModel.onEvent(IptvEvent.HideSleepTimerPicker)
            showZapping || showSideMenu -> {
                showZapping = false; showSideMenu = false; runCatching { playerFR.requestFocus() }
            }
            isFullScreen -> isFullScreen = false
            else         -> onNavigateBack()
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

        // ─── 1. DASHBOARD ───────────────────────────────────────
        AnimatedVisibility(!isFullScreen, enter = fadeIn(tween(280)), exit = fadeOut(tween(280))) {
            Column(Modifier.fillMaxSize()) {
                TopNavBar(
                    state      = state,
                    topBarFR   = topBarFR, epgBtnFR  = epgBtnFR,
                    addBtnFR   = addBtnFR, setgBtnFR = setgBtnFR,
                    gridFR     = gridFR,
                    onBack        = onNavigateBack,
                    onSettings    = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                    onEpgGuide    = { viewModel.onEvent(IptvEvent.ShowEpgGuide) },
                    onAddPlaylist = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) }
                )
                if (state.channels.isEmpty() && state.loadState !is IptvLoadState.Loading) {
                    IptvEmptyState(
                        onAddClick      = { viewModel.onEvent(IptvEvent.ShowAddPlaylist) },
                        onSettingsClick = { viewModel.onEvent(IptvEvent.ShowIptvSettings) },
                        emptyStateFR    = gridFR
                    )
                } else {
                    HeroEpgSection(focusedChannel ?: state.channels.firstOrNull(), state.epgData, state.channelLogos)
                    ChannelsDashboard(state, gridFR, topBarFR,
                        onChannelFocused = { focusedChannel = it },
                        onChannelClicked = { ch -> viewModel.onEvent(IptvEvent.SelectChannel(ch)); isFullScreen = true }
                    )
                }
            }
        }

        // ─── 2. FULLSCREEN ──────────────────────────────────────
        if (isFullScreen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .focusRequester(playerFR).focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown || isAnyDialogOpen) return@onPreviewKeyEvent false
                        resetIdle()
                        when (ev.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_UP    -> { if (showZapping) switchUp()   else showZapping = true; true }
                            KeyEvent.KEYCODE_DPAD_DOWN  -> { if (showZapping) switchDown() else showZapping = true; true }
                            KeyEvent.KEYCODE_DPAD_LEFT  -> { if (!showZapping && !showSideMenu) showSideMenu = true; !showZapping }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (!showZapping && !showSideMenu) { showZapping = true; true } else false
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT     -> { switchDown(); true }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { switchUp();   true }
                            // FIX: dedicated GUIDE / MENU keys open the EPG guide directly
                            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> {
                                viewModel.onEvent(IptvEvent.ShowEpgGuide); true
                            }
                            else -> false
                        }
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().focusable(false),
                    factory  = { ctx ->
                        AspectRatioFrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            val sv = SurfaceView(ctx).apply { keepScreenOn = true }
                            sv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: android.view.View)   { exo.player.setVideoSurfaceView(sv) }
                                override fun onViewDetachedFromWindow(v: android.view.View) { exo.player.clearVideoSurface() }
                            })
                            addView(sv)
                        }
                    },
                    update = { arLayout ->
                        if (videoAR > 0f) arLayout.setAspectRatio(videoAR)
                    }
                )

                // Top info (non-interactive)
                AnimatedVisibility(
                    visible  = (showZapping || showSideMenu) && !isAnyDialogOpen,
                    enter    = fadeIn(tween(200)) + slideInVertically { -it / 2 },
                    exit     = fadeOut(tween(180)) + slideOutVertically { -it / 2 },
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(10f)
                ) {
                    var t by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) { while (true) { t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(1000) } }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.88f), Color.Transparent)))
                            .padding(horizontal = 48.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(state.currentChannel?.groupTitle?.uppercase() ?: "", color = MUTED, fontSize = 11.sp, letterSpacing = 2.sp)
                            Text(state.currentChannel?.name ?: "", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (currChIdx >= 0) Text("${currChIdx + 1} / ${state.filteredChannels.size}", color = MUTED, fontSize = 13.sp)
                            Text(t, color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Zapping bar
                AnimatedVisibility(
                    visible  = showZapping && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it }),
                    exit     = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
                ) {
                    ZappingHud(
                        channels = state.filteredChannels, currentChannel = state.currentChannel,
                        epgData = state.epgData, logos = state.channelLogos, zappingFR = zappingFR,
                        onSelectChannel = { ch ->
                            if (state.currentChannel?.id != ch.id) { viewModel.onEvent(IptvEvent.SelectChannel(ch)); exo.prepareStream(ch.streamUrl); exo.play() }
                            showZapping = false; runCatching { playerFR.requestFocus() }
                        },
                        onChannelUp   = ::switchUp,
                        onChannelDown = ::switchDown,
                        onDismiss     = { showZapping = false; runCatching { playerFR.requestFocus() } },
                        onIdleReset   = ::resetIdle,
                        onOpenEpgGuide = { viewModel.onEvent(IptvEvent.ShowEpgGuide) }
                    )
                }

                // Side menu
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                AnimatedVisibility(
                    visible  = showSideMenu && state.channels.isNotEmpty() && !isAnyDialogOpen,
                    enter    = slideInHorizontally(initialOffsetX = { if (isRtl) it else -it }) + fadeIn(tween(180)),
                    exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) it else -it }) + fadeOut(tween(140)),
                    modifier = Modifier.align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart).zIndex(30f)
                ) {
                    SideGroupMenu(
                        groups = state.groups, selectedGroup = state.selectedGroup, sideFR = sideFR,
                        onSelectGroup = { g -> viewModel.onEvent(IptvEvent.SelectGroup(g)); showSideMenu = false; showZapping = true },
                        onDismiss     = { showSideMenu = false; runCatching { playerFR.requestFocus() } },
                        onIdleReset   = ::resetIdle
                    )
                }

                // Hints
                AnimatedVisibility(!showZapping && !showSideMenu && !isAnyDialogOpen, enter = EnterTransition.None, exit = fadeOut(tween(400)),
                    modifier = Modifier.align(Alignment.BottomStart).padding(22.dp).zIndex(5f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HintPill("▲▼  Channel"); HintPill("◀  Groups"); HintPill("OK  Menu"); HintPill("GUIDE  EPG")
                    }
                }
            }
        }

        // ─── DIALOGS ─────────────────────────────────────────────
        if (state.loadState is IptvLoadState.Loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(200f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.luminastreams.tv.ui.components.LoadingIndicator()
                    Text("Loading playlist…", color = MUTED, fontSize = 14.sp)
                }
            }
        }
        if (state.showAddPlaylist) IptvDialog({ viewModel.onEvent(IptvEvent.HideAddPlaylist) }) { fr, dismiss -> PlaylistManagerDialog(state, fr, dismiss, viewModel::onEvent) }
        if (state.showSettings)    IptvDialog({ viewModel.onEvent(IptvEvent.HideIptvSettings) }) { fr, dismiss -> SmartSettingsDialog(state, exo, currTracks, fr, dismiss, viewModel::onEvent) }
        if (state.showEpgGuide)    IptvDialog({ viewModel.onEvent(IptvEvent.HideEpgGuide) }) { fr, dismiss -> FullEpgGuideDialog(state, viewModel, fr, dismiss) }
        if (state.showQrCode && state.qrCodeChannel != null)
            IptvDialog({ viewModel.onEvent(IptvEvent.HideQrCode) }) { fr, _ -> ChannelQrDialog(state.qrCodeChannel!!, fr) { viewModel.onEvent(IptvEvent.HideQrCode) } }

        if (state.epgLoadState is IptvLoadState.Loading) {
            Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).zIndex(50f)
                .clip(RoundedCornerShape(20.dp)).background(SURFACE.copy(0.92f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.luminastreams.tv.ui.components.LoadingIndicator(size = 14.dp)
                    Text("Loading EPG…", color = MUTED, fontSize = 11.sp)
                }
            }
        }

        AnimatedVisibility(showToast.isNotEmpty(), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).zIndex(100f)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(WHITE.copy(0.14f)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(showToast, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════
// DIALOG WRAPPER
// Dims background (tap → dismiss).
// Content receives (firstFR, onDismiss).
// Content MUST trap focus itself with focusGroup + focusProperties{exit={Cancel}} + onPreviewKeyEvent.
// ═══════════════════════════════════════════════
@Composable
private fun IptvDialog(
    onDismiss : () -> Unit,
    content   : @Composable (fr: FocusRequester, onDismiss: () -> Unit) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.86f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            content(fr, onDismiss)
        }
    }
}

// Focus-trap modifier applied to every dialog card
private fun Modifier.dialogCard(onDismiss: () -> Unit): Modifier =
    this.focusGroup()
        .focusProperties { exit = { FocusRequester.Cancel } }
        .onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) { onDismiss(); true }
            else false
        }
        // Swallow clicks so the dim layer doesn't see them
        .clickable(MutableInteractionSource(), indication = null) {}

// ═══════════════════════════════════════════════
// TOP NAV BAR
// Focus chain (LTR): back ←→ epgGuide ←→ addPlaylist ←→ settings
// All buttons: Down → gridFR
// ═══════════════════════════════════════════════
@Composable
private fun TopNavBar(
    state     : IptvState,
    topBarFR  : FocusRequester,
    epgBtnFR  : FocusRequester,
    addBtnFR  : FocusRequester,
    setgBtnFR : FocusRequester,
    gridFR    : FocusRequester,
    onBack        : () -> Unit,
    onSettings    : () -> Unit,
    onEpgGuide    : () -> Unit,
    onAddPlaylist : () -> Unit
) {
    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 48.dp)
            // FIX: focusGroup() tells the TV focus system that these buttons form a cluster,
            // enabling DPAD left/right to move between them. The exit guard stops focus from
            // accidentally escaping upwards (there's nothing above the top bar).
            .focusGroup()
            .focusProperties {
                exit = { dir ->
                    if (dir == FocusDirection.Up) FocusRequester.Cancel else FocusRequester.Default
                }
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back
        NavIconBtn(Icons.Default.ArrowBack,
            Modifier.size(42.dp).focusRequester(topBarFR).focusProperties { right = epgBtnFR; down = gridFR }, onBack)

        Text("LUMINA IPTV", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        if (state.channels.isNotEmpty()) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ACCENT.copy(0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("${state.channels.size} CH", color = ACCENT2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        if (state.epgData.isNotEmpty()) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(GREEN.copy(0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("EPG ✓", color = GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // TV Guide
        Surface(onClick = onEpgGuide,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            modifier = Modifier.height(36.dp).focusRequester(epgBtnFR).focusProperties { left = topBarFR; right = addBtnFR; down = gridFR }
        ) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(15.dp))
                Text("TV Guide", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Add Playlist
        Surface(onClick = onAddPlaylist,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors   = ClickableSurfaceDefaults.colors(containerColor = ACCENT.copy(0.18f), focusedContainerColor = ACCENT, contentColor = ACCENT2, focusedContentColor = WHITE),
            modifier = Modifier.height(36.dp).focusRequester(addBtnFR).focusProperties { left = epgBtnFR; right = setgBtnFR; down = gridFR }
        ) {
            Row(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.PlaylistAdd, null, Modifier.size(15.dp))
                Text("Add Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Settings
        NavIconBtn(Icons.Default.Settings,
            Modifier.size(42.dp).focusRequester(setgBtnFR).focusProperties { left = addBtnFR; down = gridFR }, onSettings)

        Text(timeStr, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
        modifier = modifier) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(19.dp)) }
    }
}

// ═══════════════════════════════════════════════
// HERO EPG  (display only, no focus)
// ═══════════════════════════════════════════════
@Composable
private fun HeroEpgSection(channel: IptvChannel?, epgData: Map<String, List<EpgProgram>>, logos: Map<String, String>) {
    if (channel == null) return
    val tf       = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val epgs     = resolveEpg(channel, epgData)
    val nowProg  = remember(epgs) { epgs?.firstOrNull { it.isLiveNow } }
    val nextProg = remember(epgs) { epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow } }
    val logo     = resolveChannelLogo(channel, logos)

    Row(Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 48.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.size(118.dp).clip(RoundedCornerShape(14.dp)).background(SURFACE2), Alignment.Center) {
            ChannelLogoImage(channel, logo, 98.dp)
        }
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBadge(); Text(channel.groupTitle, color = MUTED, fontSize = 13.sp)
                if (channel.resolution.isNotBlank()) ResBadge(channel.resolution)
            }
            Spacer(Modifier.height(5.dp))
            Text("${channel.number} · ${channel.name}", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(9.dp))
            if (nowProg != null) {
                Text(nowProg.title, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("${tf.format(Date(nowProg.startTime))} – ${tf.format(Date(nowProg.endTime))}", color = MUTED, fontSize = 12.sp)
                    ProgBar(nowProg.progressFraction, 150.dp)
                    Text("${nowProg.remainingMinutes}m left", color = MUTED, fontSize = 12.sp)
                }
                nextProg?.let { Text("Next: ${it.title}  ·  ${tf.format(Date(it.startTime))}", color = MUTED.copy(0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                Text(if (epgs == null) "No EPG data" else "No current program info", color = MUTED, fontSize = 14.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════
// CHANNELS DASHBOARD
// First row Up → navBarFR
// ═══════════════════════════════════════════════
@Composable
private fun ChannelsDashboard(
    state: IptvState, gridFR: FocusRequester, navBarFR: FocusRequester,
    onChannelFocused: (IptvChannel) -> Unit, onChannelClicked: (IptvChannel) -> Unit
) {
    val favorites       = state.channels.filter { it.id in state.favoriteChannelIds }
    val groupedChannels = remember(state.channels) { state.channels.groupBy { it.groupTitle } }

    LazyColumn(contentPadding = PaddingValues(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().focusGroup()) {
        if (favorites.isNotEmpty()) {
            item { HorizontalChannelRow("⭐ Favorites", favorites, state.channelLogos, true, gridFR, navBarFR, onChannelFocused, onChannelClicked) }
        }
        state.groups.filter { it != "All" && it != "Favorites" && it != "Recent" }.forEachIndexed { idx, group ->
            val chs = groupedChannels[group] ?: return@forEachIndexed
            item {
                HorizontalChannelRow(
                    title = group, channels = chs, logos = state.channelLogos,
                    isFirstRow = favorites.isEmpty() && idx == 0,
                    rowFR = gridFR,
                    upFR  = if (favorites.isEmpty() && idx == 0) navBarFR else null,
                    onFocus = onChannelFocused, onClick = onChannelClicked
                )
            }
        }
    }
}

@Composable
private fun HorizontalChannelRow(
    title: String, channels: List<IptvChannel>, logos: Map<String, String>,
    isFirstRow: Boolean, rowFR: FocusRequester, upFR: FocusRequester?,
    onFocus: (IptvChannel) -> Unit, onClick: (IptvChannel) -> Unit
) {
    Column {
        Row(Modifier.padding(start = 48.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.width(3.dp).height(13.dp).background(ACCENT, RoundedCornerShape(2.dp)))
            Text(title, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${channels.size}", color = MUTED, fontSize = 12.sp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.fillMaxWidth().focusGroup().focusProperties {
                if (upFR != null) up = upFR
                exit = { dir -> if (dir == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
            }
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "Ch" }) { idx, ch ->
                ChannelCard(ch, resolveChannelLogo(ch, logos),
                    if (isFirstRow && idx == 0) Modifier.focusRequester(rowFR) else Modifier,
                    { onFocus(ch) }, { onClick(ch) })
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: IptvChannel, logoUrl: String, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(110), label = "cs")
    Box(modifier.width(165.dp).aspectRatio(16f / 9f).graphicsLayer { scaleX = scale; scaleY = scale }) {
        Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(2.dp, WHITE), shape = RoundedCornerShape(12.dp))),
            glow   = ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.45f), 15.dp)),
            modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                ChannelLogoImage(channel, logoUrl, 76.dp, focused)
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (focused) BG.copy(0.7f) else Color.Black.copy(0.6f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("${channel.number}", color = if (focused) BG else MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, if (focused) BG.copy(0.72f) else Color.Black.copy(0.72f))))
                    .padding(6.dp)) {
                    Text(channel.name, color = if (focused) BG else WHITE, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ZAPPING HUD
// DPad Up/Down → channel switch (onPreviewKeyEvent on the container)
// DPad Left/Right → scroll the LazyRow (default)
// Back/Escape → dismiss
// ═══════════════════════════════════════════════
@Composable
private fun ZappingHud(
    channels: List<IptvChannel>, currentChannel: IptvChannel?,
    epgData: Map<String, List<EpgProgram>>, logos: Map<String, String>,
    zappingFR: FocusRequester,
    onSelectChannel: (IptvChannel) -> Unit,
    onChannelUp: () -> Unit, onChannelDown: () -> Unit,
    onDismiss: () -> Unit, onIdleReset: () -> Unit,
    onOpenEpgGuide: () -> Unit          // FIX: EPG shortcut from the zapping HUD
) {
    val listState  = rememberLazyListState()
    var focusedCh  by remember { mutableStateOf(currentChannel) }

    LaunchedEffect(currentChannel?.id) {
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }.coerceAtLeast(0)
        if (idx > 2) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        focusedCh = channels.getOrNull(idx)
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, HUD_BG.copy(0.78f), HUD_BG)))
            .padding(bottom = 26.dp, top = 50.dp)
            // ── All Up/Down/Back handled here, before LazyRow sees them ──
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP    -> { onChannelUp();   onIdleReset(); true }
                    KeyEvent.KEYCODE_DPAD_DOWN  -> { onChannelDown(); onIdleReset(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onDismiss(); true }
                    else -> false
                }
            }
    ) {
        // EPG info
        Box(Modifier.fillMaxWidth().height(98.dp).padding(horizontal = 48.dp)) {
            focusedCh?.let { fCh ->
                val tf       = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val logo     = resolveChannelLogo(fCh, logos)
                val epgs     = resolveEpg(fCh, epgData)
                val nowProg  = epgs?.firstOrNull { it.isLiveNow }
                val nextProg = epgs?.firstOrNull { it.startTime > System.currentTimeMillis() && !it.isLiveNow }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)).background(SURFACE2), Alignment.Center) {
                        ChannelLogoImage(fCh, logo, 40.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${fCh.number} · ${fCh.name}", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (fCh.resolution.isNotBlank()) ResBadge(fCh.resolution)
                        }
                        Spacer(Modifier.height(3.dp))
                        if (nowProg != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text(nowProg.title, color = WHITE, fontSize = 13.sp, maxLines = 1)
                                ProgBar(nowProg.progressFraction, 110.dp)
                                Text("${nowProg.remainingMinutes}m", color = MUTED, fontSize = 12.sp)
                                nextProg?.let { Text("›  ${it.title} · ${tf.format(Date(it.startTime))}", color = MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        } else { Text(fCh.groupTitle, color = MUTED, fontSize = 13.sp) }
                    }
                    // FIX: EPG Guide shortcut button visible in the zapping HUD
                    Surface(
                        onClick  = { onOpenEpgGuide() },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color(0x33FFFFFF),
                            focusedContainerColor = WHITE,
                            contentColor          = WHITE,
                            focusedContentColor   = BG
                        ),
                        modifier = Modifier.height(42.dp).align(Alignment.Bottom)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                            Text("TV Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Channel strip — Left/Right scrolls; Up/Down intercepted above by onPreviewKeyEvent
        LazyRow(
            state = listState, contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
                .focusProperties {
                    // Prevent Up/Down from trying to move focus out of the row
                    exit = { dir ->
                        when (dir) {
                            FocusDirection.Up, FocusDirection.Down -> FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
        ) {
            val currentIdx = channels.indexOfFirst { it.id == currentChannel?.id }.coerceAtLeast(0)
            itemsIndexed(channels, key = { _, ch -> ch.id }, contentType = { _, _ -> "zap" }) { idx, ch ->
                ZappingCard(ch, resolveChannelLogo(ch, logos), ch.id == currentChannel?.id,
                    if (idx == currentIdx) Modifier.focusRequester(zappingFR) else Modifier,
                    { focusedCh = ch; onIdleReset() }, { onSelectChannel(ch); onIdleReset() })
            }
        }
    }
}

@Composable
private fun ZappingCard(channel: IptvChannel, logoUrl: String, isCurrent: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(100), label = "zc")
    Box(modifier.width(126.dp).aspectRatio(16f / 9f).graphicsLayer { scaleX = scale; scaleY = scale }) {
        Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = if (isCurrent) WHITE.copy(0.17f) else Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
            border = ClickableSurfaceDefaults.border(
                border        = if (isCurrent) Border(BorderStroke(2.dp, ACCENT), shape = RoundedCornerShape(10.dp)) else Border.None,
                focusedBorder = Border(BorderStroke(2.dp, WHITE), shape = RoundedCornerShape(10.dp))),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                ChannelLogoImage(channel, logoUrl, 56.dp, focused)
                if (isCurrent && !focused) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).background(RED, CircleShape))
                Box(Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(0.5f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("${channel.number}", color = if (focused) BG else MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// SIDE GROUP MENU
// DPad Right / Back → dismiss
// Up/Down → scroll list (default)
// Focus trapped inside via focusProperties{exit={Cancel}}
// ═══════════════════════════════════════════════
@Composable
private fun SideGroupMenu(
    groups: List<String>, selectedGroup: String, sideFR: FocusRequester,
    onSelectGroup: (String) -> Unit, onDismiss: () -> Unit, onIdleReset: () -> Unit
) {
    Box(
        Modifier.width(278.dp).fillMaxHeight().background(HUD_BG)
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionRight      -> { onDismiss(); true }
                    Key.Back, Key.Escape    -> { onDismiss(); true }
                    else -> false
                }
            }
    ) {
        LazyColumn(contentPadding = PaddingValues(top = 42.dp, bottom = 42.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxSize().focusGroup()) {
            item { Text("CATEGORIES", color = MUTED.copy(0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 12.dp, bottom = 5.dp)) }
            itemsIndexed(groups) { idx, group ->
                val isSel = group == selectedGroup
                Surface(onClick = { onSelectGroup(group) },
                    shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = if (isSel) ACCENT.copy(0.2f) else Color.Transparent, focusedContainerColor = WHITE, contentColor = if (isSel) ACCENT2 else MUTED, focusedContentColor = BG),
                    border = ClickableSurfaceDefaults.border(border = if (isSel) Border(BorderStroke(1.dp, ACCENT.copy(0.45f)), shape = RoundedCornerShape(10.dp)) else Border.None, focusedBorder = Border.None),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .let { m -> if (isSel || (idx == 0 && !groups.contains(selectedGroup))) m.focusRequester(sideFR) else m }
                        .onFocusChanged { if (it.isFocused) onIdleReset() }
                ) {
                    Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.CenterStart) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isSel) Box(Modifier.width(3.dp).height(13.dp).background(ACCENT2, RoundedCornerShape(2.dp)))
                            Text(group, fontSize = 14.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// EPG GUIDE DIALOG
// Left panel (channels): fr → idx0, Up on idx0 → refreshFR
// Right from channel list → programs panel (programsFR)
// Left from programs panel → back to fr
// Back → close
// ═══════════════════════════════════════════════
@Composable
private fun FullEpgGuideDialog(state: IptvState, viewModel: IptvViewModel, fr: FocusRequester, onDismiss: () -> Unit) {
    val tf         = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val refreshFR  = remember { FocusRequester() }
    val programsFR = remember { FocusRequester() }
    var selectedCh by remember { mutableStateOf(state.currentChannel ?: state.filteredChannels.firstOrNull()) }
    val programs   = remember(selectedCh, state.epgData) { selectedCh?.let { resolveEpg(it, state.epgData) } ?: emptyList() }

    Box(
        Modifier.fillMaxWidth(0.93f).fillMaxHeight(0.9f).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(26.dp)
            .dialogCard(onDismiss)
    ) {
        Column {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                Text("TV Guide", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(if (state.epgData.isEmpty()) "No EPG loaded" else "${state.epgData.size} channels", color = MUTED, fontSize = 13.sp)
                Surface(onClick = { viewModel.onEvent(IptvEvent.RefreshEpg) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A28), focusedContainerColor = ACCENT, contentColor = MUTED, focusedContentColor = WHITE),
                    modifier = Modifier.height(32.dp).focusRequester(refreshFR).focusProperties { down = fr }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(13.dp)); Text("Refresh EPG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ACCENT.copy(0.28f)))
            Spacer(Modifier.height(13.dp))

            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                // Channel list
                LazyColumn(
                    Modifier.width(252.dp).fillMaxHeight().focusGroup()
                        .focusProperties {
                            enter = { dir -> if (dir == FocusDirection.Up) refreshFR else FocusRequester.Default }
                            exit  = { dir -> if (dir == FocusDirection.Right) programsFR else FocusRequester.Default }
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(state.filteredChannels, key = { _, ch -> ch.id }) { idx, ch ->
                        val isSel   = ch.id == selectedCh?.id
                        val logo    = resolveChannelLogo(ch, state.channelLogos)
                        val nowProg = resolveEpg(ch, state.epgData)?.firstOrNull { it.isLiveNow }
                        Surface(onClick = { selectedCh = ch },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = if (isSel) ACCENT.copy(0.14f) else Color(0x0DFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                            border   = ClickableSurfaceDefaults.border(border = if (isSel) Border(BorderStroke(1.dp, ACCENT.copy(0.38f)), shape = RoundedCornerShape(10.dp)) else Border.None, focusedBorder = Border.None),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .let { m -> if (idx == 0) m.focusRequester(fr) else m }
                                .focusProperties { if (idx == 0) up = refreshFR }
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(SURFACE2), Alignment.Center) { ChannelLogoImage(ch, logo, 24.dp) }
                                Column(Modifier.weight(1f)) {
                                    Text(ch.name, color = WHITE, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (nowProg != null) Text(nowProg.title, color = MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Programs list
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(SURFACE.copy(0.5f))) {
                    if (programs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CalendarToday, null, tint = MUTED, modifier = Modifier.size(28.dp))
                                Text(if (state.epgData.isEmpty()) "Load EPG in playlist settings" else "No data for this channel", color = MUTED, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxSize().focusGroup()
                                .focusProperties { exit = { dir -> if (dir == FocusDirection.Left) fr else FocusRequester.Default } }
                        ) {
                            itemsIndexed(programs.sortedBy { it.startTime }, key = { _, p -> "${p.startTime}_${p.channelId}" }) { idx, p ->
                                val isLive = p.isLiveNow; val isPast = p.isPast
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                        .background(when { isLive -> ACCENT.copy(0.11f); isPast -> Color.Transparent; else -> Color(0x08FFFFFF) })
                                        .let { m -> if (idx == 0) m.focusRequester(programsFR) else m }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
                                ) {
                                    if (isLive) Box(Modifier.size(7.dp).background(RED, CircleShape)) else Spacer(Modifier.size(7.dp))
                                    Column(Modifier.width(56.dp)) {
                                        Text(tf.format(Date(p.startTime)), color = if (isLive) ACCENT2 else if (isPast) MUTED.copy(0.5f) else WHITE, fontSize = 12.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal)
                                        Text("${p.durationMinutes}m", color = MUTED.copy(0.5f), fontSize = 10.sp)
                                    }
                                    Box(Modifier.width(2.dp).height(20.dp).background(if (isLive) ACCENT else MUTED2))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.title, color = if (isPast) MUTED.copy(0.5f) else WHITE, fontSize = 13.sp, fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (p.category.isNotBlank()) Text(p.category, color = MUTED.copy(0.5f), fontSize = 10.sp)
                                    }
                                    if (isLive) Column(horizontalAlignment = Alignment.End) {
                                        Text("${p.remainingMinutes}m left", color = ACCENT2, fontSize = 11.sp)
                                        Spacer(Modifier.height(3.dp)); ProgBar(p.progressFraction, 70.dp)
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

// ═══════════════════════════════════════════════
// PLAYLIST MANAGER DIALOG
// Form chain: name ↔ url ↔ epg ↔ save ↔ delete | qrClose
// Back → close
// ═══════════════════════════════════════════════
@Composable
fun PlaylistManagerDialog(state: IptvState, fr: FocusRequester, onDismiss: () -> Unit, onEvent: (IptvEvent) -> Unit) {
    val nameFR   = fr
    val urlFR    = remember { FocusRequester() }
    val epgFR    = remember { FocusRequester() }
    val saveFR   = remember { FocusRequester() }
    val deleteFR = remember { FocusRequester() }
    val qrFR     = remember { FocusRequester() }

    val hasActive = state.playlists.any { it.isActive }

    Box(
        Modifier.fillMaxWidth(0.87f).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(34.dp)
            .dialogCard(onDismiss)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            // Form
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                    Text("Manage Playlists", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(20.dp))

                DialogLabel("Playlist Name")
                DialogInput(state.addPlaylistName, "e.g. My IPTV", nameFR, downFR = urlFR) { onEvent(IptvEvent.UpdateAddPlaylistName(it)) }
                Spacer(Modifier.height(13.dp))

                DialogLabel("M3U / M3U8 URL *")
                DialogInput(state.addPlaylistUrl, "http://...", urlFR, upFR = nameFR, downFR = epgFR) { onEvent(IptvEvent.UpdateAddPlaylistUrl(it)) }
                Spacer(Modifier.height(13.dp))

                DialogLabel("EPG XML URL  (optional)")
                DialogInput(state.addPlaylistEpgUrl, "http://.../epg.xml.gz", epgFR, upFR = urlFR, downFR = saveFR) { onEvent(IptvEvent.UpdateAddPlaylistEpgUrl(it)) }
                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Surface(onClick = { if (state.addPlaylistUrl.isNotBlank()) onEvent(IptvEvent.ConfirmAddPlaylist) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = ACCENT, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                        modifier = Modifier.weight(1f).height(52.dp).focusRequester(saveFR)
                            .focusProperties { up = epgFR; right = if (hasActive) deleteFR else qrFR }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Text("Save & Connect", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    state.playlists.firstOrNull { it.isActive }?.let { activePl ->
                        Surface(onClick = { onEvent(IptvEvent.DeletePlaylist(activePl.id)) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = RED.copy(0.14f), focusedContainerColor = RED, contentColor = RED, focusedContentColor = WHITE),
                            modifier = Modifier.height(52.dp).focusRequester(deleteFR).focusProperties { up = epgFR; left = saveFR; right = qrFR }
                        ) {
                            Box(Modifier.padding(horizontal = 17.dp).fillMaxHeight(), Alignment.Center) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp)); Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                state.playlists.firstOrNull { it.isActive }?.let { pl ->
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GREEN.copy(0.08f)).padding(11.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = GREEN, modifier = Modifier.size(14.dp))
                            Column {
                                Text("Active: ${pl.name}", color = GREEN, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${pl.channelCount} channels loaded", color = GREEN.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // QR column
            Column(Modifier.width(225.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Send from Phone", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Scan QR or type URL in your phone browser to send a playlist to your TV", color = MUTED, fontSize = 11.sp, textAlign = TextAlign.Center)
                if (state.localIpAddress.isNotBlank()) {
                    val qrUrl    = "http://${state.localIpAddress}:8080"
                    val qrBitmap = remember(qrUrl) { QrCodeGenerator.generate(qrUrl, 320) }
                    Box(Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(WHITE).padding(8.dp)) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SURFACE2).padding(7.dp), Alignment.Center) {
                        Text(qrUrl, color = ACCENT2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(170.dp).clip(RoundedCornerShape(12.dp)).background(SURFACE2), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Default.Wifi, null, tint = MUTED, modifier = Modifier.size(28.dp))
                            Text("Not on WiFi", color = MUTED, fontSize = 12.sp)
                        }
                    }
                }
                Surface(onClick = onDismiss,
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                    modifier = Modifier.fillMaxWidth().height(40.dp).focusRequester(qrFR).focusProperties { left = saveFR }
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// SETTINGS DIALOG
// subtitlesFR → audio[0..n] → editFR
// Back → close
// ═══════════════════════════════════════════════
@Composable
fun SmartSettingsDialog(
    state: IptvState, exo: ExoPlayerWrapper,
    currentTracks: androidx.media3.common.Tracks,
    fr: FocusRequester, onDismiss: () -> Unit, onEvent: (IptvEvent) -> Unit
) {
    val subtitlesFR   = fr
    val editFR        = remember { FocusRequester() }

    val allAudioTracks = remember(currentTracks) {
        val list = mutableListOf<Pair<String, Pair<androidx.media3.common.Tracks.Group, Int>>>()
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { grp ->
            for (i in 0 until grp.length) {
                val fmt   = grp.mediaTrackGroup.getFormat(i)
                val lang  = fmt.language?.uppercase() ?: "Track ${i + 1}"
                val ch    = if (fmt.channelCount > 0) " · ${fmt.channelCount}ch" else ""
                val atmos = if (fmt.sampleMimeType == "audio/eac3-joc") " · ATMOS" else ""
                list.add("$lang$ch$atmos" to (grp to i))
            }
        }
        list
    }
    val audioFRs = remember(allAudioTracks.size) { List(allAudioTracks.size.coerceAtLeast(1)) { FocusRequester() } }

    Box(
        Modifier.width(455.dp).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F0F18)).padding(30.dp)
            .dialogCard(onDismiss)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(4.dp).height(24.dp).background(ACCENT, RoundedCornerShape(2.dp)))
                Text("Playback Settings", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))

            // Subtitles
            SettingsTile("Subtitles / CC", if (state.subtitlesEnabled) "Enabled" else "Disabled",
                Icons.Default.Subtitles, state.subtitlesEnabled,
                Modifier.focusRequester(subtitlesFR).focusProperties { down = audioFRs.getOrElse(0) { editFR } }
            ) { onEvent(IptvEvent.ToggleSubtitles) }

            Spacer(Modifier.height(5.dp))

            if (allAudioTracks.isEmpty()) {
                SettingsTile("Audio Track", "No audio tracks detected", Icons.Default.VolumeUp, false,
                    Modifier.focusRequester(audioFRs[0]).focusProperties { up = subtitlesFR; down = editFR }) {}
            } else {
                Text("AUDIO TRACKS", color = MUTED.copy(0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(vertical = 3.dp, horizontal = 3.dp))
                allAudioTracks.forEachIndexed { idx, (label, ga) ->
                    val (grp, trackIdx) = ga
                    val isSel  = grp.isTrackSelected(trackIdx)
                    val prevFR = if (idx == 0) subtitlesFR else audioFRs[idx - 1]
                    val nextFR = audioFRs.getOrElse(idx + 1) { editFR }
                    Surface(onClick = {
                        val b = exo.player.trackSelectionParameters.buildUpon()
                        b.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        b.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        b.setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, trackIdx))
                        exo.player.trackSelectionParameters = b.build()
                    },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = if (isSel) ACCENT.copy(0.13f) else Color(0x0DFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                        modifier = Modifier.fillMaxWidth().height(50.dp).focusRequester(audioFRs[idx]).focusProperties { up = prevFR; down = nextFR }
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, null, Modifier.size(17.dp), tint = if (isSel) ACCENT2 else MUTED)
                                Text(label, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (isSel) Icon(Icons.Default.Check, null, tint = ACCENT2, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (idx < allAudioTracks.size - 1) Spacer(Modifier.height(3.dp))
                }
            }

            Spacer(Modifier.height(5.dp))

            // Edit Playlist
            SettingsTile("Edit Playlist URL",
                state.playlists.firstOrNull { it.isActive }?.name ?: "No playlist",
                Icons.Default.Edit, false,
                Modifier.focusRequester(editFR).focusProperties { up = audioFRs.lastOrNull() ?: subtitlesFR }
            ) { onEvent(IptvEvent.ShowAddPlaylist) }
        }
    }
}

@Composable
private fun SettingsTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = if (isActive) ACCENT.copy(0.12f) else Color(0x0DFFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
        modifier = modifier.fillMaxWidth().height(54.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (isActive) ACCENT2 else MUTED)
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(subtitle, fontSize = 12.sp, color = if (isActive) ACCENT2.copy(0.7f) else MUTED)
                }
            }
            if (isActive) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ACCENT.copy(0.28f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text("ON", color = ACCENT2, fontSize = 11.sp, fontWeight = FontWeight.Black)
            } else Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MUTED)
        }
    }
}

// ═══════════════════════════════════════════════
// CHANNEL QR DIALOG  (single button, Back → dismiss)
// ═══════════════════════════════════════════════
@Composable
private fun ChannelQrDialog(channel: IptvChannel, fr: FocusRequester, onDismiss: () -> Unit) {
    val qrBitmap = remember(channel.streamUrl) { QrCodeGenerator.generate(channel.streamUrl, 320) }
    Box(Modifier.width(355.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0F0F18)).padding(26.dp).dialogCard(onDismiss)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(channel.name, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("Stream QR Code", color = MUTED, fontSize = 13.sp)
            Box(Modifier.size(185.dp).clip(RoundedCornerShape(12.dp)).background(WHITE).padding(8.dp)) {
                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Text(channel.streamUrl.take(60) + if (channel.streamUrl.length > 60) "…" else "", color = MUTED, fontSize = 10.sp, textAlign = TextAlign.Center)
            Surface(onClick = onDismiss, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.fillMaxWidth().height(42.dp).focusRequester(fr)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// EMPTY STATE  — Add ←→ Settings
// ═══════════════════════════════════════════════
@Composable
fun IptvEmptyState(onAddClick: () -> Unit, onSettingsClick: () -> Unit, emptyStateFR: FocusRequester) {
    val settingsFR = remember { FocusRequester() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(74.dp).clip(CircleShape).background(ACCENT.copy(0.1f)), Alignment.Center) {
            Icon(Icons.Default.LiveTv, null, Modifier.size(32.dp), tint = ACCENT2)
        }
        Spacer(Modifier.height(17.dp))
        Text("No Channels Loaded", color = WHITE, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Add an M3U playlist to start watching.\nScan the QR code for easy phone setup.", color = MUTED, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(onClick = onAddClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = ACCENT, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                glow   = ClickableSurfaceDefaults.glow(focusedGlow = Glow(ACCENT.copy(0.5f), 18.dp)),
                modifier = Modifier.height(50.dp).focusRequester(emptyStateFR).focusProperties { right = settingsFR }
            ) {
                Row(Modifier.padding(horizontal = 22.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Text("Add Playlist", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(onClick = onSettingsClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x22FFFFFF), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = BG),
                modifier = Modifier.height(50.dp).focusRequester(settingsFR).focusProperties { left = emptyStateFR }
            ) {
                Row(Modifier.padding(horizontal = 22.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.Settings, null, Modifier.size(17.dp)); Text("Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// DIALOG INPUT
// DPad Up/Down moves to adjacent field (passes key before TextField sees it)
// ═══════════════════════════════════════════════
@Composable
fun DialogInput(
    value: String, hint: String,
    focusRequester: FocusRequester? = null,
    upFR: FocusRequester? = null, downFR: FocusRequester? = null,
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = WHITE, fontSize = 14.sp), cursorBrush = SolidColor(ACCENT2),
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

// ═══════════════════════════════════════════════
// SHARED SMALL COMPOSABLES
// ═══════════════════════════════════════════════
@Composable private fun HintPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.5f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = MUTED, fontSize = 11.sp)
    }
}
@Composable private fun LiveBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(RED).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(5.dp).background(WHITE, CircleShape)); Text("LIVE", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}
@Composable private fun ResBadge(res: String) {
    val color = when { res.contains("4K", true) || res.contains("UHD", true) -> Color(0xFFFF6B35); res.contains("FHD", true) || res.contains("1080", true) -> ACCENT; else -> MUTED }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.18f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(res, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun ProgBar(fraction: Float, width: Dp) {
    Box(Modifier.width(width).height(3.dp).clip(CircleShape).background(WHITE.copy(0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(ACCENT, ACCENT2))))
    }
}
@Composable private fun DialogLabel(text: String) {
    Text(text, color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

// ═══════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════
private fun resolveChannelLogo(channel: IptvChannel, logos: Map<String, String>): String {
    if (channel.logoUrl.isNotBlank()) return channel.logoUrl
    return logos[channel.tvgId.lowercase()] ?: logos[channel.tvgName.lowercase()]
    ?: logos[channel.id.lowercase()] ?: logos[channel.name.lowercase()]
    ?: logos.entries.firstOrNull { (k, _) -> channel.name.length >= 4 && k.contains(channel.name.lowercase().take(6)) }?.value ?: ""
}

private fun resolveEpg(channel: IptvChannel, epgData: Map<String, List<EpgProgram>>): List<EpgProgram>? =
    epgData[channel.tvgId.lowercase()] ?: epgData[channel.tvgName.lowercase()]
    ?: epgData[channel.id.lowercase()] ?: epgData[channel.name.lowercase()]

@Composable
private fun ChannelLogoImage(channel: IptvChannel, logoUrl: String, size: Dp, isFocused: Boolean = false) {
    val ctx      = LocalContext.current
    val initials = channel.name.take(2).uppercase()
    val initialsText: @Composable () -> Unit = {
        Text(initials, color = if (isFocused) BG else WHITE, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black)
    }
    if (logoUrl.isNotBlank()) {
        // FIX: SubcomposeAsyncImage with only an `error` slot (slot-API) renders NOTHING on
        // success unless a `success` slot is also provided — that was why logos never appeared.
        // AsyncImage renders the loaded image automatically and falls back via onError.
        var hasError by remember(logoUrl) { mutableStateOf(false) }
        if (hasError) {
            initialsText()
        } else {
            AsyncImage(
                model = remember(logoUrl) {
                    ImageRequest.Builder(ctx).data(logoUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true).build()
                },
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(size),
                onError            = { hasError = true }
            )
        }
    } else {
        initialsText()
    }
}