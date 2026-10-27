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
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private val TMDB_WIDTH_REGEX = Regex("/w\\d+/")

@Composable
fun HomeScreen(state: HomeState, viewModel: HomeViewModel, navController: NavController, onMovieClick: (String) -> Unit) {
    val focusState = remember { HomeFocusState() }
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val heroUpdateDelayMs = when (DeviceProfile.tier) { DeviceProfile.Tier.LOW -> 520L; DeviceProfile.Tier.MID -> 420L; DeviceProfile.Tier.HIGH -> 260L }
    val appContext = LocalContext.current.applicationContext

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(isRtl) { viewModel.setLanguage(isRtl) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContinueWatching(appContext)
                resumeCount++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Restore focus when returning from other screens (settings, details, etc.)
    LaunchedEffect(resumeCount) {
        if (resumeCount > 0) {
            focusState.isNavFocused = false
            delay(500) // Wait for Compose to fully re-attach FocusRequesters
            focusState.focusTrigger++
        }
    }



    val rows by viewModel.uiRows.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }

    var pendingFilterFocus by remember { mutableStateOf(false) }
    var pendingTabFocus by remember { mutableStateOf(false) } // 🚀 מנגנון חדש לסנכרון הפוקוס בעת מעבר עמוד

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        if (currentTab != state.selectedTab) {
            currentTab = state.selectedTab
            currentFilter = state.selectedStudioFilter
            focusState.currentRowIndex = 0
            focusState.isNavFocused = false
            pendingTabFocus = true // מסמן שיש להמתין לעדכון השורות לפני הקפצת הפוקוס
        } else if (currentFilter != state.selectedStudioFilter) {
            currentFilter = state.selectedStudioFilter
        }
    }

    // מפעיל את הפוקוס רק כשהשורות מוכנות ורונדרו במלואן
    LaunchedEffect(rows) {
        if (pendingTabFocus && rows.isNotEmpty()) {
            delay(150) // מוודא שה-Compose סיים לרנדר את הלוגואים של הסטודיו
            pendingTabFocus = false
            focusState.currentRowIndex = 0
            focusState.focusTrigger++
        }
        if (pendingFilterFocus && rows.size > 1) {
            delay(150)
            pendingFilterFocus = false
            focusState.currentRowIndex = 1 // מדלג לשורת הפוסטרים (1)
            focusState.focusTrigger++
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(if (isLow) 800L else heroUpdateDelayMs)
            val currentRows = viewModel.uiRows.value
            val m = currentRows.getOrNull(ri)?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; is RowDef.StudioRibbon -> emptyList() } }?.firstOrNull()
            if (m != null && m.id != focusState.heroMovie?.id) focusState.heroMovie = m
        }
    }

    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) focusState.heroMovie = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }?.firstOrNull()
    }

    LaunchedEffect(focusState.heroMovie?.id) {
        val hero = focusState.heroMovie ?: return@LaunchedEffect
        viewModel.enrichHeroData(hero)
    }

    val prefetchCtx = LocalContext.current
    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        
        // Only prefetch the VERY FIRST hero image to make the initial screen load faster.
        // Doing this for 10 full-res images crashes the memory cache and causes the app to get stuck.
        val firstHero = rows.firstOrNull { it !is RowDef.StudioRibbon }
            ?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; else -> null } }
            ?.firstOrNull() ?: return@LaunchedEffect
            
        val raw = (firstHero.backdropUrl.takeIf { it.isNotBlank() } ?: firstHero.posterUrl)
            .replace(TMDB_WIDTH_REGEX, "/original/")
            
        if (raw.isNotBlank()) {
            val req = coil.request.ImageRequest.Builder(prefetchCtx)
                .data(raw)
                // Downsample slightly instead of full display metrics to save memory
                .size(1920, 1080)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
            coil.Coil.imageLoader(prefetchCtx).enqueue(req)
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
                        Key.DirectionUp -> {
                            if (!focusState.isNavFocused && focusState.currentRowIndex == 0) {
                                focusState.isNavFocused = true
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

        var isTrailerPlaying by remember { mutableStateOf(false) }

        BackdropLayer(
            hero = focusState.heroMovie,
            trailerKey = state.heroTrailerKey.takeIf { state.heroEnrichedId == focusState.heroMovie?.id },
            isNavFocused = focusState.isNavFocused,
            onTrailerPlayingChanged = { isTrailerPlaying = it }
        )

        Column(Modifier.fillMaxSize()) {

            Ps5TopNav(
                isFocused = focusState.isNavFocused,
                onNavFocus = { focusState.isNavFocused = true; focusState.currentRowIndex = 0 },
                onSearch = { navController.navigate("search") },
                onWatchlist = { navController.navigate("watchlist") },
                onSettings = { navController.navigate("settings") },
                onIptv = { navController.navigate("iptv") },
                onMovies = { viewModel.selectTab("movies") },
                onSeries = { viewModel.selectTab("series") },
                onHome = { viewModel.selectTab("home") },
                onFuzer = { viewModel.selectTab("Fuzer") },
                onDebrid = { navController.navigate("debrid") },
                onProfileClick = { navController.navigate("auth") },
                selectedTab = state.selectedTab
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 52.dp, end = 52.dp, bottom = 24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                HeroOverlay(
                    hero = focusState.heroMovie,
                    isEnriching = state.heroEnrichedId != focusState.heroMovie?.id,
                    logoUrl = state.heroLogoUrl.takeIf { state.heroEnrichedId == focusState.heroMovie?.id },
                    runtime = state.heroRuntime.takeIf { state.heroEnrichedId == focusState.heroMovie?.id },
                    cast = state.heroCast.takeIf { state.heroEnrichedId == focusState.heroMovie?.id } ?: emptyList(),
                    localizedOverview = state.heroOverview.takeIf { state.heroEnrichedId == focusState.heroMovie?.id },
                    localizedGenre = state.heroGenre.takeIf { state.heroEnrichedId == focusState.heroMovie?.id },
                    isTrailerPlaying = isTrailerPlaying
                )
            }

            val rememberedOnMovieClick = remember<(String) -> Unit> { { id -> onMovieClick(id) } }
            val rememberedOnHeroUpdate: (com.luminastreams.tv.domain.model.Movie?) -> Unit = remember { { if (it != null) focusState.heroMovie = it } }
            val rememberedOnLoadMore = remember<(String) -> Unit> { { id -> viewModel.loadMore(id.substringBefore("::")) } }

            ConsoleRowCycler(
                rows = rows,
                focusState = focusState,
                activeFilter = currentFilter,
                onMovieClick = rememberedOnMovieClick,
                onHeroUpdate = rememberedOnHeroUpdate,
                onStudioFilterClick = { filter ->
                    if (state.selectedStudioFilter == filter) {
                        viewModel.setStudioFilter(null)
                        pendingFilterFocus = false
                    } else {
                        viewModel.setStudioFilter(filter)
                        pendingFilterFocus = true
                    }
                },
                onLoadMore = rememberedOnLoadMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .padding(bottom = 16.dp)
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