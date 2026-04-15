@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.imageLoader
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

// הפונקציה הבלעדית שבונה את 3 הקטגוריות (חדש, אנימציה, הכי טוב) עבור כל אולפן!
fun generateStudioRows(baseId: String, brand: StudioBrand, movies: List<Movie>, _tr: (String, String) -> String): List<RowDef> {
    val list = mutableListOf<RowDef>()
    // מוודא שאין סרטים כפולים כדי למנוע קריסות קומפוז!
    val uniqueMovies = movies.distinctBy { it.id }
    if (uniqueMovies.isEmpty()) return list

    // 1. שורה ראשונה (LANDSCAPE) - שורת האולפן שמציגה את התוכן החדש/הנוכחי ללא סוף
    list.add(RowDef.Studio("${baseId}::new", brand, uniqueMovies))

    // 2. שורה שנייה (PORTRAIT) - אנימציה מתוך תוכן האולפן
    val ani = uniqueMovies.filter {
        it.genre.contains("Animation", ignoreCase = true) ||
                it.genre.contains("Kids", ignoreCase = true) ||
                it.genre.contains("Family", ignoreCase = true) ||
                it.genre.contains("אנימציה")
    }
    if (ani.isNotEmpty()) {
        list.add(RowDef.Regular("${baseId}::ani", _tr("Animation", "אנימציה"), ani))
    }

    // 3. שורה שלישית (PORTRAIT) - הכי טוב בכל הזמנים מתוך תוכן האולפן
    val top = uniqueMovies.filter { it.rating > 0f }.sortedByDescending { it.rating }
    if (top.isNotEmpty()) {
        list.add(RowDef.Regular("${baseId}::top", _tr("Best of All Time", "הכי טוב בכל הזמנים"), top))
    }

    return list
}

@Composable
fun HomeScreen(state: HomeState, viewModel: HomeViewModel, navController: NavController, onMovieClick: (String) -> Unit) {
    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }
    var currentTab by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }
    var contentAlpha by remember { mutableStateOf(1f) }
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val heroUpdateDelayMs = when (DeviceProfile.tier) { DeviceProfile.Tier.LOW -> 520L; DeviceProfile.Tier.MID -> 420L; DeviceProfile.Tier.HIGH -> 260L }

    val homeHbo       = remember(state.movieHBO, state.tvHBO)             { mergeStudioContent(state.movieHBO, state.tvHBO) }
    val homeNetflix   = remember(state.movieNetflix, state.tvNetflix)      { mergeStudioContent(state.movieNetflix, state.tvNetflix) }
    val homeAmazon    = remember(state.movieAmazon, state.tvAmazon)        { mergeStudioContent(state.movieAmazon, state.tvAmazon) }
    val homeAppleTv   = remember(state.movieAppleTV, state.tvAppleTV)      { mergeStudioContent(state.movieAppleTV, state.tvAppleTV) }
    val homeDisney    = remember(state.movieDisney, state.tvDisney)        { mergeStudioContent(state.movieDisney, state.tvDisney) }
    val homeParamount = remember(state.movieParamount, state.tvParamount)  { mergeStudioContent(state.movieParamount,state.tvParamount)}
    val homeHulu      = remember(state.movieHulu, state.tvHulu)            { mergeStudioContent(state.movieHulu, state.tvHulu) }

    val amazonMovies  = remember(state.movieAmazon, state.tvAmazon) { state.movieAmazon.ifEmpty { state.tvAmazon } }
    val amazonSeries  = remember(state.tvAmazon, state.movieAmazon) { state.tvAmazon.ifEmpty { state.movieAmazon } }

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        val tabChanged = currentTab != state.selectedTab
        val filterChanged = currentFilter != state.selectedStudioFilter
        if (tabChanged || filterChanged) {
            if (!isLow) contentAlpha = 0f
            if (!isLow) delay(250)
            currentTab = state.selectedTab; currentFilter = state.selectedStudioFilter
            val targetIndex = if (state.selectedTab == "סרטים" || state.selectedTab == "סדרות") 1 else 0
            if (tabChanged) focusState.currentRowIndex = 0 else if (filterChanged) { focusState.currentRowIndex = targetIndex; focusState.focusTrigger++ }
            if (!isLow) { delay(30); contentAlpha = 1f }
        }
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rows: List<RowDef> = remember(state, homeHbo, homeNetflix, homeAmazon, homeAppleTv, homeDisney, homeParamount, homeHulu, amazonMovies, amazonSeries, currentTab, currentFilter, isRtl) {
        val _tr = { en: String, he: String -> if (isRtl) he else en }
        buildList {
            val filter = currentFilter
            when (currentTab) {
                "ראשי" -> {
                    if (state.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", _tr("Trending Movies", "סרטים פופולריים"), state.movieTrending.distinctBy { it.id }))
                    if (homeHbo.isNotEmpty()) add(RowDef.Studio("homeHBO", StudioBrand.HBO, homeHbo))
                    if (state.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", _tr("Popular Shows", "סדרות פופולריות"), state.tvTrending.distinctBy { it.id }))
                    if (homeNetflix.isNotEmpty()) add(RowDef.Studio("homeNetflix", StudioBrand.NETFLIX, homeNetflix))
                    if (homeAmazon.isNotEmpty()) add(RowDef.Studio("homeAmazon", StudioBrand.AMAZON, homeAmazon))
                    if (homeAppleTv.isNotEmpty()) add(RowDef.Studio("homeAppleTv", StudioBrand.APPLE_TV, homeAppleTv))
                    if (homeDisney.isNotEmpty()) add(RowDef.Studio("homeDisney", StudioBrand.DISNEY, homeDisney))
                    if (homeParamount.isNotEmpty()) add(RowDef.Studio("homeParamount", StudioBrand.PARAMOUNT, homeParamount))
                    if (homeHulu.isNotEmpty()) add(RowDef.Studio("homeHulu", StudioBrand.HULU, homeHulu))
                    if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres",_tr("New in Theaters", "בקולנוע"), state.moviePremieres.distinctBy { it.id }))
                }
                "סרטים" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        val amzId = if (state.movieAmazon.isNotEmpty()) "movieAmazon" else "tvAmazon"
                        when (filter) {
                            "HBO"       -> addAll(generateStudioRows("movieHBO", StudioBrand.HBO, state.movieHBO, _tr))
                            "AMAZON"    -> addAll(generateStudioRows(amzId, StudioBrand.AMAZON, amazonMovies, _tr))
                            "PARAMOUNT" -> addAll(generateStudioRows("movieParamount", StudioBrand.PARAMOUNT, state.movieParamount, _tr))
                            "HULU"      -> addAll(generateStudioRows("movieHulu", StudioBrand.HULU, state.movieHulu, _tr))
                            "NETFLIX"   -> addAll(generateStudioRows("movieNetflix", StudioBrand.NETFLIX, state.movieNetflix, _tr))
                            "APPLE_TV"  -> addAll(generateStudioRows("movieAppleTV", StudioBrand.APPLE_TV, state.movieAppleTV, _tr))
                            "DISNEY"    -> addAll(generateStudioRows("movieDisney", StudioBrand.DISNEY, state.movieDisney, _tr))
                        }
                    } else {
                        if (state.movieAction.isNotEmpty()) add(RowDef.Regular("movieAction", _tr("Action & Adventure", "פעולה והרפתקאות"), state.movieAction.distinctBy { it.id }))
                        if (state.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", _tr("Trending Now", "פופולרי עכשיו"), state.movieTrending.distinctBy { it.id }))
                        if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", _tr("In Theaters", "בקולנוע"), state.moviePremieres.distinctBy { it.id }))
                        if (state.movieAnimation.isNotEmpty()) add(RowDef.Regular("movieAnimation", _tr("Animations", "אנימציה"), state.movieAnimation.distinctBy { it.id }))
                    }
                }
                "סדרות" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        val amzId = if (state.tvAmazon.isNotEmpty()) "tvAmazon" else "movieAmazon"
                        when (filter) {
                            "HBO"       -> addAll(generateStudioRows("tvHBO", StudioBrand.HBO, state.tvHBO, _tr))
                            "AMAZON"    -> addAll(generateStudioRows(amzId, StudioBrand.AMAZON, amazonSeries, _tr))
                            "PARAMOUNT" -> addAll(generateStudioRows("tvParamount", StudioBrand.PARAMOUNT, state.tvParamount, _tr))
                            "HULU"      -> addAll(generateStudioRows("tvHulu", StudioBrand.HULU, state.tvHulu, _tr))
                            "NETFLIX"   -> addAll(generateStudioRows("tvNetflix", StudioBrand.NETFLIX, state.tvNetflix, _tr))
                            "APPLE_TV"  -> addAll(generateStudioRows("tvAppleTV", StudioBrand.APPLE_TV, state.tvAppleTV, _tr))
                            "DISNEY"    -> addAll(generateStudioRows("tvDisney", StudioBrand.DISNEY, state.tvDisney, _tr))
                        }
                    } else {
                        if (state.tvDrama.isNotEmpty()) add(RowDef.Regular("tvDrama", _tr("Drama", "דרמה"), state.tvDrama.distinctBy { it.id }))
                        if (state.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", _tr("Trending Shows", "סדרות פופולריות"), state.tvTrending.distinctBy { it.id }))
                        if (state.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", _tr("New Episodes", "פרקים חדשים"), state.tvPremieres.distinctBy { it.id }))
                        if (state.tvAnimation.isNotEmpty()) add(RowDef.Regular("tvAnimation", _tr("Animations", "אנימציה"), state.tvAnimation.distinctBy { it.id }))
                    }
                }
                "Fuzer" -> {
                    val newContent = (state.fuzerMovies + state.fuzerSeries).sortedByDescending { it.id }.distinctBy { it.id }
                    if (newContent.isNotEmpty()) add(RowDef.Regular("fuzer_new", _tr("🆕 New Content", "🆕 תוכן חדש"), newContent))
                    if (state.fuzerMovies.isNotEmpty()) add(RowDef.Regular("fuzer_m", _tr("🎬 Movies", "🎬 סרטים"), state.fuzerMovies.distinctBy { it.id }))
                    if (state.fuzerMoviesHD.isNotEmpty()) add(RowDef.Regular("fuzer_mhd", _tr("🎬 Movies HD", "🎬 סרטים HD"), state.fuzerMoviesHD.distinctBy { it.id }))
                    if (state.fuzerMovies4K.isNotEmpty()) add(RowDef.Regular("fuzer_m4k", _tr("✨ Movies 4K", "✨ סרטים 4K"), state.fuzerMovies4K.distinctBy { it.id }))
                    if (state.fuzerDubbedMovies.isNotEmpty()) add(RowDef.Regular("fuzer_dm", _tr("🎤 Dubbed Movies", "🎤 סרטים מדובבים"), state.fuzerDubbedMovies.distinctBy { it.id }))
                    if (state.fuzerSeries.isNotEmpty()) add(RowDef.Regular("fuzer_tv", _tr("📺 TV Shows", "📺 סדרות"), state.fuzerSeries.distinctBy { it.id }))
                    if (state.fuzerSeriesHD.isNotEmpty()) add(RowDef.Regular("fuzer_shd", _tr("📺 TV Shows HD", "📺 סדרות HD"), state.fuzerSeriesHD.distinctBy { it.id }))
                    if (state.fuzerSeries4K.isNotEmpty()) add(RowDef.Regular("fuzer_s4k", _tr("✨ TV Shows 4K", "✨ סדרות 4K"), state.fuzerSeries4K.distinctBy { it.id }))
                    if (state.fuzerDubbedSeries.isNotEmpty()) add(RowDef.Regular("fuzer_ds", _tr("🎤 Dubbed Shows", "🎤 סדרות מדובבות"), state.fuzerDubbedSeries.distinctBy { it.id }))
                }
            }
        }
    }

    val firstContentIndex = remember(rows) { rows.indexOfFirst { it !is RowDef.StudioRibbon } }
    fun rowHeightFor(i: Int) = when (rows.getOrNull(i)) { is RowDef.StudioRibbon -> 110.dp; else -> if (i == firstContentIndex) ROW_LANDSCAPE_H else ROW_PORTRAIT_H }
    val panelH = remember(rows, focusState.currentRowIndex) { when (rows.getOrNull(focusState.currentRowIndex)) { is RowDef.StudioRibbon -> 126.dp; null -> ROW_PORTRAIT_H; else -> ROW_PORTRAIT_H + 16.dp } }

    LaunchedEffect(rows, focusState.isNavFocused) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(heroUpdateDelayMs)
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
        when { state.isLoading -> { HomeLoading(); return@Box }; state.error != null -> { HomeError(state.error!!) { viewModel.retry() }; return@Box } }
        BackdropLayer(focusState.heroMovie)
        HeroOverlay(focusState.heroMovie, panelH)
        val context = LocalContext.current

        ContentLayer(
            rows = rows,
            contentAlpha = contentAlpha,
            focusState = focusState,
            activeTab = state.selectedTab,
            activeFilter = currentFilter,
            panelH = panelH,
            rowHeightFor = { i -> rowHeightFor(i) },
            firstContentIndex = firstContentIndex,
            onMovieClick = { id ->
                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) context.imageLoader.memoryCache?.clear()
                onMovieClick(id)
            },
            onHeroUpdate = { focusState.heroMovie = it },
            onStudioFilterClick = { filter ->
                if (state.selectedStudioFilter == filter) viewModel.setStudioFilter(null) else viewModel.setStudioFilter(filter)
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