@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LaunchedEffect(isRtl) {
        viewModel.setLanguage(isRtl)
    }

    val rows by viewModel.uiRows.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }
    var contentAlpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        val tabChanged    = currentTab    != state.selectedTab
        val filterChanged = currentFilter != state.selectedStudioFilter

        if (tabChanged) {
            if (!isLow) contentAlpha = 0f
            if (!isLow) delay(250)
            currentTab = state.selectedTab
            currentFilter = state.selectedStudioFilter
            // FIX: Stop stealing focus! Let the user stay on the Top Bar.
            focusState.currentRowIndex = 0
            if (!isLow) { delay(30); contentAlpha = 1f }
        } else if (filterChanged) {
            currentFilter = state.selectedStudioFilter
        }
    }

    val firstContentIndex = remember(rows) { rows.indexOfFirst { it !is RowDef.StudioRibbon } }

    // FIX: Increased the height allocated to the Studio Ribbon to prevent overlapping clipping
    fun rowHeightFor(i: Int) = when (rows.getOrNull(i)) { is RowDef.StudioRibbon -> 130.dp; else -> if (i == firstContentIndex) ROW_LANDSCAPE_H else ROW_PORTRAIT_H }

    val maxPanelH = remember(rows) {
        var max = 0.dp
        for (i in rows.indices) {
            val h = rowHeightFor(i)
            if (h > max) max = h
        }
        if (max == 0.dp) 300.dp else max
    }

    val targetHeroBottom = remember(rows, focusState.currentRowIndex) {
        val h = rowHeightFor(focusState.currentRowIndex.coerceIn(0, maxOf(0, rows.size - 1)))
        // FIX: Increased the offsets (from 210 to 270, and 20 to 50) to give the hero text more breathing room
        if (rows.getOrNull(focusState.currentRowIndex) is RowDef.StudioRibbon) 270.dp else h + 50.dp
    }
    val animatedHeroBottomPadding by animateDpAsState(targetValue = targetHeroBottom, animationSpec = tween(300, easing = FastOutSlowInEasing))

    LaunchedEffect(rows, focusState.isNavFocused) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest

            val debounceDelay = if (isLow) 800L else heroUpdateDelayMs
            delay(debounceDelay)

            val m = rows.getOrNull(ri)?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; is RowDef.StudioRibbon -> emptyList() } }?.firstOrNull()
            if (m != null && m.id != focusState.heroMovie?.id) focusState.heroMovie = m
        }
    }
    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) focusState.heroMovie = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }?.firstOrNull()
    }
    LaunchedEffect(rows) {
        if (focusState.isNavFocused || focusState.heroMovie == null) {
            val m = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }?.firstOrNull()
            if (m != null) focusState.heroMovie = m
        }
    }
    BackHandler(enabled = focusState.isNavFocused) { focusState.isNavFocused = false }

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.retry() }; return@Box }
        }
        BackdropLayer(focusState.heroMovie)
        HeroOverlay(focusState.heroMovie, animatedHeroBottomPadding)

        ContentLayer(
            rows = rows,
            contentAlpha = contentAlpha,
            focusState = focusState,
            activeTab = state.selectedTab,
            activeFilter = currentFilter,
            maxPanelH = maxPanelH,
            rowHeightFor = { i -> rowHeightFor(i) },
            firstContentIndex = firstContentIndex,
            onMovieClick = onMovieClick,
            onHeroUpdate = { if (it != null) focusState.heroMovie = it },
            onStudioFilterClick = { filter ->
                if (state.selectedStudioFilter == filter) {
                    viewModel.setStudioFilter(null)
                } else {
                    viewModel.setStudioFilter(filter)
                    // FIX: Push focus down to the newly generated row (Row 1)
                    // and fire the retry loop to enforce the UI scrolling down
                    focusState.currentRowIndex = 1
                    focusState.focusTrigger++
                }
            },
            onLoadMore = { id ->
                val realId = id.substringBefore("::")
                viewModel.loadMore(realId)
            },
            onSearch = { navController.navigate("search") },
            onHomeTab = { viewModel.selectTab("ראשי"); viewModel.setStudioFilter(null) },
            onMoviesTab = { viewModel.selectTab("סרטים"); viewModel.setStudioFilter(null) },
            onSeriesTab = { viewModel.selectTab("סדרות"); viewModel.setStudioFilter(null) },
            onFuzer = { viewModel.selectTab("Fuzer"); viewModel.loadFuzerContent() },
            onWatchlist = { navController.navigate("watchlist") },
            onSettings = { navController.navigate("settings") },
            onIptv = { navController.navigate("iptv") }
        )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Box(Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(56.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { Box(Modifier.width(100.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(260.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer))
            }
            Spacer(Modifier.height(40.dp))
            Box(Modifier.width(380.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(240.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            repeat(2) { Box(Modifier.fillMaxWidth(0.42f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(6) { Box(Modifier.width(LAND_W).height(LAND_H).clip(RoundedCornerShape(10.dp)).background(shimmer)) } }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(8) { Box(Modifier.width(PORT_W).height(PORT_H).clip(RoundedCornerShape(10.dp)).background(shimmer)) } }
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