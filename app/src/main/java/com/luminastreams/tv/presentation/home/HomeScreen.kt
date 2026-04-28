@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(state: HomeState, viewModel: HomeViewModel, navController: NavController, onMovieClick: (String) -> Unit) {
    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val heroUpdateDelayMs = when (DeviceProfile.tier) { DeviceProfile.Tier.LOW -> 520L; DeviceProfile.Tier.MID -> 420L; DeviceProfile.Tier.HIGH -> 260L }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(isRtl) { viewModel.setLanguage(isRtl) }

    // ⚡ FIX: Reclaim focus when returning from any other screen (Details, Search, etc.)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Only reclaim if we're not already on the navbar
                if (!focusState.isNavFocused) {
                    focusState.focusTrigger++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rows by viewModel.uiRows.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        if (currentTab != state.selectedTab) {
            currentTab = state.selectedTab
            currentFilter = state.selectedStudioFilter
            focusState.currentRowIndex = 0
            focusState.isNavFocused = false
            focusState.focusTrigger++
        } else if (currentFilter != state.selectedStudioFilter) {
            currentFilter = state.selectedStudioFilter
        }
    }

    LaunchedEffect(rows, focusState.isNavFocused) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(if (isLow) 800L else heroUpdateDelayMs)
            val m = rows.getOrNull(ri)?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; is RowDef.StudioRibbon -> emptyList() } }?.firstOrNull()
            if (m != null && m.id != focusState.heroMovie?.id) focusState.heroMovie = m
        }
    }

    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) focusState.heroMovie = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }?.firstOrNull()
    }

    // 🚀 Prefetch hero backdrops — download /original/ for first movie of each row in background
    // After first prefetch, all hero transitions are instant from disk cache (0ms)
    val prefetchCtx = LocalContext.current
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val dm = prefetchCtx.resources.displayMetrics
        val sw = dm.widthPixels; val sh = dm.heightPixels
        val seen = mutableSetOf<String>()
        rows.asSequence()
            .mapNotNull { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }
            .mapNotNull { it.firstOrNull() }
            .take(10) // Cap at 10 prefetches to avoid network flood
            .forEach { movie ->
                val raw = (movie.backdropUrl.takeIf { it.isNotBlank() } ?: movie.posterUrl)
                    .replace("/w780/", "/original/")
                if (raw.isNotBlank() && seen.add(raw)) {
                    val req = coil.request.ImageRequest.Builder(prefetchCtx)
                        .data(raw)
                        .size(sw, sh)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    coil.Coil.imageLoader(prefetchCtx).enqueue(req)
                    delay(150) // Stagger to avoid network congestion
                }
            }
    }
    BackHandler(enabled = focusState.isNavFocused) { focusState.isNavFocused = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(BG)
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    when (ev.key) {
                        Key.DirectionDown -> {
                            if (focusState.isNavFocused) {
                                focusState.isNavFocused = false
                                focusState.focusTrigger++
                                return@onPreviewKeyEvent true
                            }
                        }
                        Key.Back, Key.Escape -> {
                            if (focusState.isNavFocused) {
                                focusState.isNavFocused = false
                                focusState.focusTrigger++
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        when {
            state.isLoading -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.retry() }; return@Box }
        }

        BackdropLayer(focusState.heroMovie)

        // STRICT VERTICAL STACKING (Prevents any overlapping UI)
        Column(Modifier.fillMaxSize()) {

            // 1. Navigation (Top Left)
            Ps5TopNav(
                activeTab = state.selectedTab,
                isFocused = focusState.isNavFocused,
                onNavFocus = { focusState.isNavFocused = true; focusState.currentRowIndex = 0 },
                onSearch = { navController.navigate("search") },
                onHomeTab = { viewModel.selectTab("ראשי"); viewModel.setStudioFilter(null) },
                onMoviesTab = { viewModel.selectTab("סרטים"); viewModel.setStudioFilter(null) },
                onSeriesTab = { viewModel.selectTab("סדרות"); viewModel.setStudioFilter(null) },
                onFuzer = { viewModel.selectTab("Fuzer"); viewModel.loadFuzerContent() },
                onWatchlist = { navController.navigate("watchlist") },
                onSettings = { navController.navigate("settings") },
                onIptv = { navController.navigate("iptv") }
            )

            // 2. Hero Information (Takes all middle space, anchors to BottomStart)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 52.dp, end = 52.dp, bottom = 24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                HeroOverlay(focusState.heroMovie)
            }

            // 3. Row Cycler (Strict Height limit at the bottom)
            ConsoleRowCycler(
                rows = rows,
                focusState = focusState,
                activeFilter = currentFilter,
                onMovieClick = onMovieClick,
                onHeroUpdate = { if (it != null) focusState.heroMovie = it },
                onStudioFilterClick = { filter ->
                    if (state.selectedStudioFilter == filter) viewModel.setStudioFilter(null)
                    else {
                        viewModel.setStudioFilter(filter)
                        focusState.currentRowIndex = 1
                        focusState.focusTrigger++
                    }
                },
                onLoadMore = { id -> viewModel.loadMore(id.substringBefore("::")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp) // Fixed height ensures it never crushes the text
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun HomeLoading() {
    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH
    val shimmer: Brush = if (isHighTier) {
        val inf = rememberInfiniteTransition(label = "sk")
        val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")
        Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF282828), Color(0xFF111111)), start = Offset(p * 2400f - 1200f, 0f), end = Offset(p * 2400f, 600f))
    } else Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF1E1E1E), Color(0xFF111111)))

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(top = 14.dp, start = 52.dp, end = 52.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { Box(Modifier.width(100.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
            }
            Spacer(Modifier.height(80.dp))
            Box(Modifier.width(380.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(240.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            repeat(2) { Box(Modifier.fillMaxWidth(0.42f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(6) { Box(Modifier.width(LAND_W).height(LAND_H).clip(RoundedCornerShape(10.dp)).background(shimmer)) } }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("⚠", fontSize = 52.sp)
            Text(message, color = DIM, fontSize = 16.sp, maxLines = 2)
            Surface(onClick = onRetry, colors = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)), modifier = Modifier.height(50.dp).width(160.dp)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr("Retry", "נסה שוב"), color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}