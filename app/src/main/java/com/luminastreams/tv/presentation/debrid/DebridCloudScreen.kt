@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.luminastreams.tv.presentation.debrid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay

private val BG = Color(0xFF060608)
private val CARD_BG = Color(0xFF12141A)
private val ACCENT_CYAN = Color(0xFF00E5FF)
private val READY_GREEN = Color(0xFF10B981)
private val DOWNLOADING_AMBER = Color(0xFFF59E0B)
private val ERROR_RED = Color(0xFFEF4444)

@Composable
fun DebridCloudScreen(
    state: DebridCloudState,
    onEvent: (DebridCloudEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onPlayDirectUrl: (url: String, title: String, backdrop: String, poster: String) -> Unit
) {
    var focusedItem by remember { mutableStateOf<DebridTorrentUiItem?>(null) }
    val backFR = remember { FocusRequester() }
    val gridFR = remember { FocusRequester() }

    // Auto-select the first item for backdrop preview
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty() && focusedItem == null) {
            focusedItem = state.items.first()
        }
    }

    LaunchedEffect(state.items.isNotEmpty()) {
        delay(150)
        if (state.items.isNotEmpty()) {
            runCatching { gridFR.requestFocus() }
        } else {
            runCatching { backFR.requestFocus() }
        }
    }

    // Playback navigation trigger
    LaunchedEffect(state.readyToPlay) {
        state.readyToPlay?.let { target ->
            onPlayDirectUrl(target.url, target.title, target.backdropUrl, target.posterUrl)
            onEvent(DebridCloudEvent.ClearPlayTarget)
        }
    }

    BackHandler {
        if (state.selectedTorrentForFiles != null) {
            onEvent(DebridCloudEvent.DismissFilesDialog)
        } else {
            onNavigateBack()
        }
    }

    val currentFocused = state.items.find { it.id == focusedItem?.id } ?: focusedItem ?: state.items.firstOrNull()

    Box(Modifier.fillMaxSize().background(BG)) {

        // ── 1. CINEMATIC BACKDROP ──
        if (currentFocused != null && !state.isNotAuthenticated) {
            CloudBackdropLayer(currentFocused)
        }

        Column(Modifier.fillMaxSize()) {

            // ── 2. TOP HEADER ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 36.dp, end = 48.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(44.dp).focusRequester(backFR),
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(22.dp))
                }

                Spacer(Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ACCENT_CYAN.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = ACCENT_CYAN,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tr("AllDebrid Cloud", "ספריית ענן AllDebrid"),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (state.items.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (LocalLayoutDirection.current == LayoutDirection.Rtl)
                                        "${state.items.size} טורנטים"
                                    else
                                        "${state.items.size} Torrents",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = tr("Content uploaded from computer / browser", "התוכן שהעלת מהמחשב או הדפדפן"),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                // Refresh Button
                IconButton(
                    onClick = { onEvent(DebridCloudEvent.Refresh) },
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Refresh, "Refresh", Modifier.size(22.dp))
                }
            }

            // ── 3. HERO INFO SECTION ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .padding(horizontal = 48.dp, vertical = 6.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                if (state.isNotAuthenticated) {
                    UnauthenticatedHero(onConnect = onNavigateToAuth)
                } else if (currentFocused != null) {
                    CloudHeroInfo(currentFocused)
                }
            }

            // ── 4. TORRENT GRID SECTION ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.22f)
            ) {
                when {
                    state.isLoading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ACCENT_CYAN)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = tr("Loading your AllDebrid library...", "טוען את ספריית AllDebrid שלך..."),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    state.isNotAuthenticated -> {
                        // Handled in hero
                    }
                    state.items.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 28.dp)
                            ) {
                                Text("☁️", fontSize = 52.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = tr("No Torrents In AllDebrid", "אין טורנטים בחשבון AllDebrid שלך"),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tr(
                                        "Torrents you upload from your computer to AllDebrid will appear here automatically.",
                                        "טורנטים שתעלה ל-AllDebrid מהמחשב יופיעו כאן ויהיו מוכנים להפעלה ישירה."
                                    ),
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    else -> {
                        val gridState = rememberLazyGridState()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 142.dp),
                            state = gridState,
                            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(gridFR)
                                .focusGroup()
                                .focusRestorer()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown) {
                                        val firstVisible = gridState.firstVisibleItemIndex
                                        val firstOffset = gridState.firstVisibleItemScrollOffset
                                        if (firstVisible == 0 && firstOffset == 0) {
                                            runCatching { backFR.requestFocus() }
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    false
                                }
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                DebridTorrentCard(
                                    item = item,
                                    isFocused = focusedItem?.id == item.id,
                                    onFocused = { focusedItem = item },
                                    onClick = { onEvent(DebridCloudEvent.OnTorrentClick(item)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 5. MULTI-FILE EPISODES / FILES PICKER DIALOG ──
        if (state.selectedTorrentForFiles != null) {
            MultiFilesDialog(
                torrent = state.selectedTorrentForFiles,
                files = state.filesForSelectedTorrent,
                isLoading = state.isLoadingFiles,
                onSelectFile = { file ->
                    onEvent(DebridCloudEvent.OnPlayFile(file, state.selectedTorrentForFiles))
                },
                onDeleteTorrent = {
                    onEvent(DebridCloudEvent.DeleteTorrent(state.selectedTorrentForFiles.id))
                },
                onDismiss = {
                    onEvent(DebridCloudEvent.DismissFilesDialog)
                }
            )
        }

        // ── 6. RESOLVING / STREAM UNLOCKING OVERLAY ──
        if (state.resolvingStream) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CARD_BG)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 40.dp, vertical = 28.dp)
                ) {
                    CircularProgressIndicator(color = ACCENT_CYAN, strokeWidth = 3.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = state.resolvingMessage.ifBlank { tr("Loading stream...", "טוען קישור להזרמה...") },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error Banner
        if (state.error != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ERROR_RED.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = state.error,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  COMPONENTS
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CloudBackdropLayer(hero: DebridTorrentUiItem?) {
    val ctx = LocalContext.current
    val rawUrl = hero?.backdropUrl ?: hero?.posterUrl
    val heroUrl = rawUrl?.replace(Regex("/w\\d+/"), "/original/")
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    AnimatedContent(
        targetState = heroUrl,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "cloud_backdrop_fade"
    ) { url ->
        Box(Modifier.fillMaxSize().background(BG)) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(url)
                        .size(1920, 1080)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 400)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cinematic Gradients
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x99000000),
                            0.18f to Color.Transparent
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            *if (isRtl) arrayOf(
                                0.0f to Color.Transparent,
                                0.35f to Color(0x33060608),
                                0.65f to Color(0xCC060608),
                                1.0f to Color(0xF8060608)
                            )
                            else arrayOf(
                                0.0f to Color(0xF8060608),
                                0.35f to Color(0xCC060608),
                                0.65f to Color(0x33060608),
                                1.0f to Color.Transparent
                            )
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.70f to BG.copy(alpha = 0.85f),
                            1.0f to BG
                        )
                    )
            )
        }
    }
}

@Composable
private fun CloudHeroInfo(item: DebridTorrentUiItem) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Status & Metadata Badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                item.statusCode == 4 -> {
                    Badge(
                        text = tr("Ready to Stream", "מוכן לצפייה"),
                        bgColor = READY_GREEN.copy(alpha = 0.2f),
                        textColor = READY_GREEN,
                        icon = Icons.Default.CheckCircle
                    )
                }
                item.statusCode in 1..3 -> {
                    Badge(
                        text = "${tr("Downloading", "בהורדה")} ${item.progressPercent}%",
                        bgColor = DOWNLOADING_AMBER.copy(alpha = 0.2f),
                        textColor = DOWNLOADING_AMBER,
                        icon = Icons.Default.Download
                    )
                }
                else -> {
                    Badge(
                        text = item.status.ifBlank { tr("Pending", "בהמתנה") },
                        bgColor = ERROR_RED.copy(alpha = 0.2f),
                        textColor = ERROR_RED,
                        icon = Icons.Default.Warning
                    )
                }
            }

            Badge(
                text = item.sizeFormatted,
                bgColor = Color.White.copy(alpha = 0.12f),
                textColor = Color.White.copy(alpha = 0.9f)
            )

            if (item.nbLinks > 1) {
                Badge(
                    text = if (LocalLayoutDirection.current == LayoutDirection.Rtl)
                        "${item.nbLinks} קבצים / פרקים"
                    else
                        "${item.nbLinks} Files / Episodes",
                    bgColor = ACCENT_CYAN.copy(alpha = 0.15f),
                    textColor = ACCENT_CYAN,
                    icon = Icons.Default.VideoLibrary
                )
            }

            if (item.uploadDateFormatted.isNotBlank()) {
                Badge(
                    text = item.uploadDateFormatted,
                    bgColor = Color.White.copy(alpha = 0.08f),
                    textColor = Color.White.copy(alpha = 0.6f)
                )
            }

            if (item.rating > 0f) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF5C518))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("%.1f".format(item.rating), color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Title
        val titleSize = when {
            item.displayTitle.length > 30 -> 28.sp
            item.displayTitle.length > 18 -> 34.sp
            else -> 40.sp
        }
        Text(
            text = item.displayTitle,
            color = Color.White,
            fontSize = titleSize,
            fontWeight = FontWeight.Black,
            lineHeight = (titleSize.value * 1.15f).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Raw Filename (Subtitle)
        Text(
            text = item.rawFilename,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(max = 750.dp)
        )

        // TMDB Overview if available
        if (!item.overview.isNullOrBlank()) {
            Text(
                text = item.overview,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 750.dp)
            )
        }
    }
}

@Composable
private fun Badge(
    text: String,
    bgColor: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(13.dp))
        }
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DebridTorrentCard(
    item: DebridTorrentUiItem,
    isFocused: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "debrid_card_scale"
    )

    val imgUrl = item.posterUrl ?: item.backdropUrl

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CARD_BG,
            focusedContainerColor = CARD_BG
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            Glow.None,
            Glow(ACCENT_CYAN.copy(alpha = 0.35f), 12.dp)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Box(Modifier.fillMaxSize()) {
            // 1. Poster Artwork or Elegant Fallback
            if (!imgUrl.isNullOrBlank()) {
                val imageRequest = remember(imgUrl) {
                    ImageRequest.Builder(ctx)
                        .data(imgUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isFocused) 1.0f else 0.88f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1E2230),
                                    Color(0xFF12141C),
                                    Color(0xFF0A0B10)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // 2. Dual Gradients: Top protection for badges, bottom vignette for titles
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.30f to Color.Black.copy(alpha = 0.50f),
                            0.65f to Color.Black.copy(alpha = 0.85f),
                            1.0f to Color.Black.copy(alpha = 0.98f)
                        )
                    )
            )

            // 3. Top Badges (Status pill + Multi-file badge / IMDb rating)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                when (item.statusCode) {
                    4 -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xE60A2A1A))
                                .border(0.6.dp, READY_GREEN.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = READY_GREEN,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = tr("READY", "מוכן"),
                                color = READY_GREEN,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    in 1..3 -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xE6332104))
                                .border(0.6.dp, DOWNLOADING_AMBER.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = DOWNLOADING_AMBER,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "${item.progressPercent}%",
                                color = DOWNLOADING_AMBER,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xE6380A0A))
                                .border(0.6.dp, ERROR_RED.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = ERROR_RED,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = tr("ERROR", "שגיאה"),
                                color = ERROR_RED,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // End Badge: Multi-file pill or IMDb rating
                if (item.nbLinks > 1) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xD905202A))
                            .border(0.6.dp, ACCENT_CYAN.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = ACCENT_CYAN,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "${item.nbLinks}",
                            color = ACCENT_CYAN,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (item.rating > 0f) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xE6141414))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("★", color = Color(0xFFF5C518), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("%.1f".format(item.rating), color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Bottom Content: Title and Meta Info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.sizeFormatted,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.year.isNotBlank()) {
                        Text(
                            text = item.year,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (item.uploadDateFormatted.isNotBlank()) {
                        Text(
                            text = item.uploadDateFormatted,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedHero(onConnect: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(bottom = 20.dp)
    ) {
        Text(
            text = tr("Connect AllDebrid", "חבר חשבון AllDebrid"),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = tr(
                "You need to link your AllDebrid account to browse and play your cloud torrents directly on LuminaStreams.",
                "עליך לחבר את חשבון ה-AllDebrid שלך כדי לצפות בטורנטים שהעלת ישירות מהאפליקציה בטלוויזיה."
            ),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            modifier = Modifier.widthIn(max = 600.dp)
        )
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.colors(
                containerColor = ACCENT_CYAN,
                contentColor = Color.Black,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
        ) {
            Text(
                text = tr("Connect Now", "התחבר עכשיו"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MultiFilesDialog(
    torrent: DebridTorrentUiItem,
    files: List<com.luminastreams.tv.domain.usecase.AllDebridManager.AdFile>,
    isLoading: Boolean,
    onSelectFile: (com.luminastreams.tv.domain.usecase.AllDebridManager.AdFile) -> Unit,
    onDeleteTorrent: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFR = remember { FocusRequester() }

    LaunchedEffect(files.size) {
        if (files.isNotEmpty()) {
            delay(150)
            runCatching { firstFR.requestFocus() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F1117))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = torrent.displayTitle,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tr("Select episode / file to play", "בחר פרק / קובץ להפעלה"),
                            color = ACCENT_CYAN,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Delete button
                    Button(
                        onClick = onDeleteTorrent,
                        colors = ButtonDefaults.colors(
                            containerColor = ERROR_RED.copy(alpha = 0.15f),
                            contentColor = ERROR_RED,
                            focusedContainerColor = ERROR_RED,
                            focusedContentColor = Color.White
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(tr("Delete", "מחק"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Files List
                if (isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = ACCENT_CYAN)
                    }
                } else if (files.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Text(
                            text = tr("No video files found", "לא נמצאו קבצי וידאו בטורנט זה"),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files.size) { index ->
                            val f = files[index]
                            val cleanName = DebridCloudViewModel.cleanTorrentTitle(f.name)
                            val sizeStr = DebridCloudViewModel.formatBytes(f.size)

                            Surface(
                                onClick = { onSelectFile(f) },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = ACCENT_CYAN.copy(alpha = 0.2f),
                                    contentColor = Color.White,
                                    focusedContentColor = Color.White
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { if (index == 0) it.focusRequester(firstFR) else it }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = ACCENT_CYAN,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = cleanName.ifBlank { f.name },
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = f.name,
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Text(
                                        text = sizeStr,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Close Button
                Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text(tr("Close", "סגור"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
