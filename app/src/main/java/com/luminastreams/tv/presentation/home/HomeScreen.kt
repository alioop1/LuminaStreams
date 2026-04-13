@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@file:Suppress("ASSIGNED_BUT_NEVER_READ_REFERENCE", "UNUSED_VARIABLE", "UNUSED_VALUE")

package com.luminastreams.tv.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch


private val BG        = Color(0xFF070707)
private val RED       = Color(0xFFE50914)
private val RED2      = Color(0xFFB20710)
private val WHITE     = Color(0xFFFFFFFF)
private val DIM       = Color(0xCCFFFFFF)
private val DIM2      = Color(0x99FFFFFF)
private val DIM3      = Color(0x33FFFFFF)
private val GOLD      = Color(0xFFFFD700)
private val CARD_BG   = Color(0xFF1C1C1C)
private val NAV_GLASS = Color(0x18FFFFFF)
private val NAV_FOCUS = Color(0x30FFFFFF)

private val NAV_SEARCH_H = 28.dp
private val NAV_PILLS_H  = 34.dp
private val NAV_PILL_H   = 28.dp
private val NAV_GAP      = 12.dp
private val NAV_H        = NAV_SEARCH_H + NAV_PILLS_H + NAV_GAP

private val LAND_W = 280.dp
private val LAND_H = 158.dp
private val PORT_W = 148.dp
private val PORT_H = 222.dp
private val ROW_LANDSCAPE_H = 194.dp
private val ROW_PORTRAIT_H  = 260.dp

private val heroScrimLeft = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xD0070707),
        0.28f to Color(0x90070707),
        0.50f to Color(0x40070707),
        0.68f to Color.Transparent
    )
)
private val heroScrimTop = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xF0050505),
        0.15f to Color(0xC0050505),
        0.30f to Color(0x70050505),
        0.50f to Color.Transparent
    )
)
private val rowsOverlay = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.50f to Color(0xA8070707),
        1.00f to Color(0xF4070707)
    )
)
private val placeholderBrush = Brush.verticalGradient(listOf(Color(0xFF2A2A2A), CARD_BG))
private val cardBottomGradient = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Transparent, Color(0xBB000000)),
    startY = 0f, endY = Float.POSITIVE_INFINITY
)

@Composable
fun tr(en: String, he: String): String {
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en
}

private fun mergeStudioContent(vararg groups: List<Movie>): List<Movie> =
    groups.asSequence()
        .flatMap { it.asSequence() }
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .toList()

@Composable
private fun RememberPagedRowLoad(
    rowState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(rowState) {
        snapshotFlow {
            val li = rowState.layoutInfo
            val total = li.totalItemsCount
            val lastVisible = li.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 4
        }
            .distinctUntilChanged()
            .collectLatest { shouldLoad ->
                if (shouldLoad) currentOnLoadMore()
            }
    }
}

@Stable
class HomeFocusState(initialRow: Int = 0) {
    var isNavFocused    by mutableStateOf(false)
    var currentRowIndex by mutableIntStateOf(initialRow)
    var heroMovie       by mutableStateOf<Movie?>(null)
    var focusTrigger    by mutableIntStateOf(0) // <-- הוסף את השורה הזו

    companion object {
        val Saver: Saver<HomeFocusState, Int> = Saver(
            save    = { it.currentRowIndex },
            restore = { HomeFocusState(it) }
        )
    }
}

@Composable
fun HomeScreen(
    state:         HomeState,
    viewModel:     HomeViewModel,
    navController: NavController,
    onMovieClick:  (String) -> Unit
) {
    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }

    var currentTab    by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }
    var contentAlpha  by remember { mutableStateOf(1f) }

    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val heroUpdateDelayMs = when (DeviceProfile.tier) {
        DeviceProfile.Tier.LOW  -> 520L
        DeviceProfile.Tier.MID  -> 420L
        DeviceProfile.Tier.HIGH -> 260L
    }

    val homeHbo      = remember(state.movieHBO, state.tvHBO)             { mergeStudioContent(state.movieHBO,      state.tvHBO)      }
    val homeNetflix  = remember(state.movieNetflix, state.tvNetflix)      { mergeStudioContent(state.movieNetflix,  state.tvNetflix)  }
    val homeAmazon   = remember(state.movieAmazon, state.tvAmazon)        { mergeStudioContent(state.movieAmazon,   state.tvAmazon)   }
    val homeAppleTv  = remember(state.movieAppleTV, state.tvAppleTV)      { mergeStudioContent(state.movieAppleTV,  state.tvAppleTV)  }
    val homeDisney   = remember(state.movieDisney, state.tvDisney)        { mergeStudioContent(state.movieDisney,   state.tvDisney)   }
    val homeParamount= remember(state.movieParamount, state.tvParamount)  { mergeStudioContent(state.movieParamount,state.tvParamount)}
    val homeHulu     = remember(state.movieHulu, state.tvHulu)            { mergeStudioContent(state.movieHulu,     state.tvHulu)     }

    val amazonMovies = remember(state.movieAmazon, state.tvAmazon) { state.movieAmazon.ifEmpty { state.tvAmazon } }
    val amazonSeries = remember(state.tvAmazon, state.movieAmazon) { state.tvAmazon.ifEmpty { state.movieAmazon } }

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        val tabChanged = currentTab != state.selectedTab
        val filterChanged = currentFilter != state.selectedStudioFilter

        if (tabChanged || filterChanged) {
            if (!isLow) contentAlpha = 0f
            if (!isLow) delay(250)

            currentTab    = state.selectedTab
            currentFilter = state.selectedStudioFilter

            val targetIndex = if (state.selectedTab == "סרטים" || state.selectedTab == "סדרות") 1 else 0

            if (tabChanged) {
                // רק במעבר טאב: מאפסים את הפוקוס לשורת האולפנים העליונה
                focusState.currentRowIndex = 0
            } else if (filterChanged) {
                // בבחירת אולפן: מעבירים את הפוקוס לשורת ה-Landscape
                focusState.currentRowIndex = targetIndex
                focusState.focusTrigger++ // קורא לטריגר ממחלקת המצבים
            }

            if (!isLow) { delay(30); contentAlpha = 1f }
        }
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val rows: List<RowDef> = remember(
        state, currentTab, currentFilter,
        homeHbo, homeNetflix, homeAmazon, homeAppleTv, homeDisney, homeParamount, homeHulu,
        amazonMovies, amazonSeries, isRtl
    ) {
        // פונקציית עזר מקומית שלא דורשת Composable
        val _tr = { en: String, he: String -> if (isRtl) he else en }

        buildList {
            val filter = currentFilter
            when (currentTab) {
                "ראשי" -> {
                    if (state.movieTrending.isNotEmpty())   add(RowDef.Regular("movieTrending", _tr("Trending Movies", "סרטים פופולריים"), state.movieTrending))
                    if (homeHbo.isNotEmpty())               add(RowDef.Studio("homeHBO",        StudioBrand.HBO,       homeHbo))
                    if (state.tvTrending.isNotEmpty())      add(RowDef.Regular("tvTrending",    _tr("Popular Shows",    "סדרות פופולריות"), state.tvTrending))
                    if (homeNetflix.isNotEmpty())           add(RowDef.Studio("homeNetflix",    StudioBrand.NETFLIX,   homeNetflix))
                    if (homeAmazon.isNotEmpty())            add(RowDef.Studio("homeAmazon",     StudioBrand.AMAZON,    homeAmazon))
                    if (homeAppleTv.isNotEmpty())           add(RowDef.Studio("homeAppleTv",    StudioBrand.APPLE_TV,  homeAppleTv))
                    if (homeDisney.isNotEmpty())            add(RowDef.Studio("homeDisney",     StudioBrand.DISNEY,    homeDisney))
                    if (homeParamount.isNotEmpty())         add(RowDef.Studio("homeParamount",  StudioBrand.PARAMOUNT, homeParamount))
                    if (homeHulu.isNotEmpty())              add(RowDef.Studio("homeHulu",       StudioBrand.HULU,      homeHulu))
                    if (state.moviePremieres.isNotEmpty())  add(RowDef.Regular("moviePremieres",_tr("New in Theaters", "בקולנוע"), state.moviePremieres))
                }
                "סרטים" -> {
                    add(RowDef.StudioRibbon)

                    if (filter != null) {
                        when (filter) {
                            "HBO"       -> if (state.movieHBO.isNotEmpty())       add(RowDef.Studio("movieHBO",       StudioBrand.HBO,       state.movieHBO))
                            "AMAZON"    -> if (amazonMovies.isNotEmpty())         add(RowDef.Studio("movieAmazon",    StudioBrand.AMAZON,    amazonMovies))
                            "PARAMOUNT" -> if (state.movieParamount.isNotEmpty()) add(RowDef.Studio("movieParamount", StudioBrand.PARAMOUNT, state.movieParamount))
                            "HULU"      -> if (state.movieHulu.isNotEmpty())      add(RowDef.Studio("movieHulu",      StudioBrand.HULU,      state.movieHulu))
                            "NETFLIX"   -> if (state.movieNetflix.isNotEmpty())   add(RowDef.Studio("movieNetflix",   StudioBrand.NETFLIX,   state.movieNetflix))
                            "APPLE_TV"  -> if (state.movieAppleTV.isNotEmpty())   add(RowDef.Studio("movieAppleTV",   StudioBrand.APPLE_TV,  state.movieAppleTV))
                            "DISNEY"    -> if (state.movieDisney.isNotEmpty())    add(RowDef.Studio("movieDisney",    StudioBrand.DISNEY,    state.movieDisney))
                        }
                    } else {
                        if (state.movieAction.isNotEmpty()) add(RowDef.Regular("movieAction", _tr("Action & Adventure", "פעולה והרפתקאות"), state.movieAction))
                    }

                    if (state.movieTrending.isNotEmpty())  add(RowDef.Regular("movieTrending",  _tr("Trending Now", "פופולרי עכשיו"), state.movieTrending))
                    if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", _tr("In Theaters", "בקולנוע"), state.moviePremieres))
                    if (state.movieAnimation.isNotEmpty()) add(RowDef.Regular("movieAnimation", _tr("Animations", "אנימציה"), state.movieAnimation))
                }
                "סדרות" -> {
                    add(RowDef.StudioRibbon)

                    if (filter != null) {
                        when (filter) {
                            "HBO"       -> if (state.tvHBO.isNotEmpty())       add(RowDef.Studio("tvHBO",       StudioBrand.HBO,       state.tvHBO))
                            "AMAZON"    -> if (amazonSeries.isNotEmpty())      add(RowDef.Studio("tvAmazon",    StudioBrand.AMAZON,    amazonSeries))
                            "PARAMOUNT" -> if (state.tvParamount.isNotEmpty()) add(RowDef.Studio("tvParamount", StudioBrand.PARAMOUNT, state.tvParamount))
                            "HULU"      -> if (state.tvHulu.isNotEmpty())      add(RowDef.Studio("tvHulu",      StudioBrand.HULU,      state.tvHulu))
                            "NETFLIX"   -> if (state.tvNetflix.isNotEmpty())   add(RowDef.Studio("tvNetflix",   StudioBrand.NETFLIX,   state.tvNetflix))
                            "APPLE_TV"  -> if (state.tvAppleTV.isNotEmpty())   add(RowDef.Studio("tvAppleTV",   StudioBrand.APPLE_TV,  state.tvAppleTV))
                            "DISNEY"    -> if (state.tvDisney.isNotEmpty())    add(RowDef.Studio("tvDisney",    StudioBrand.DISNEY,    state.tvDisney))
                        }
                    } else {
                        if (state.tvDrama.isNotEmpty()) add(RowDef.Regular("tvDrama", _tr("Drama", "דרמה"), state.tvDrama))
                    }

                    if (state.tvTrending.isNotEmpty())  add(RowDef.Regular("tvTrending",  _tr("Trending Shows", "סדרות פופולריות"), state.tvTrending))
                    if (state.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", _tr("New Episodes", "פרקים חדשים"), state.tvPremieres))
                    if (state.tvAnimation.isNotEmpty()) add(RowDef.Regular("tvAnimation", _tr("Animations", "אנימציה"), state.tvAnimation))
                }
                "Fuzer" -> {
                    val newContent = (state.fuzerMovies + state.fuzerSeries).sortedByDescending { it.id }
                    if (newContent.isNotEmpty()) add(RowDef.Regular("fuzer_new", _tr("🆕 New Content", "🆕 תוכן חדש"), newContent))
                    if (state.fuzerMovies.isNotEmpty()) add(RowDef.Regular("fuzer_m", _tr("🎬 Movies", "🎬 סרטים"), state.fuzerMovies))
                    if (state.fuzerMoviesHD.isNotEmpty()) add(RowDef.Regular("fuzer_mhd", _tr("🎬 Movies HD", "🎬 סרטים HD"), state.fuzerMoviesHD))
                    if (state.fuzerMovies4K.isNotEmpty()) add(RowDef.Regular("fuzer_m4k", _tr("✨ Movies 4K", "✨ סרטים 4K"), state.fuzerMovies4K))
                    if (state.fuzerDubbedMovies.isNotEmpty()) add(RowDef.Regular("fuzer_dm", _tr("🎤 Dubbed Movies", "🎤 סרטים מדובבים"), state.fuzerDubbedMovies))
                    if (state.fuzerSeries.isNotEmpty()) add(RowDef.Regular("fuzer_tv", _tr("📺 TV Shows", "📺 סדרות"), state.fuzerSeries))
                    if (state.fuzerSeriesHD.isNotEmpty()) add(RowDef.Regular("fuzer_shd", _tr("📺 TV Shows HD", "📺 סדרות HD"), state.fuzerSeriesHD))
                    if (state.fuzerSeries4K.isNotEmpty()) add(RowDef.Regular("fuzer_s4k", _tr("✨ TV Shows 4K", "✨ סדרות 4K"), state.fuzerSeries4K))
                    if (state.fuzerDubbedSeries.isNotEmpty()) add(RowDef.Regular("fuzer_ds", _tr("🎤 Dubbed Shows", "🎤 סדרות מדובבות"), state.fuzerDubbedSeries))
                }
            }
        }
    }

    val firstContentIndex = remember(rows) { rows.indexOfFirst { it !is RowDef.StudioRibbon } }

    fun rowHeightFor(i: Int) = when (rows.getOrNull(i)) {
        is RowDef.StudioRibbon -> 110.dp
        else -> if (i == firstContentIndex) ROW_LANDSCAPE_H else ROW_PORTRAIT_H
    }

    val panelH = remember(rows, focusState.currentRowIndex) {
        when (rows.getOrNull(focusState.currentRowIndex)) {
            is RowDef.StudioRibbon -> 126.dp
            null -> ROW_PORTRAIT_H
            else -> ROW_PORTRAIT_H + 16.dp
        }
    }

    LaunchedEffect(rows, focusState.isNavFocused) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(heroUpdateDelayMs)
            val m = rows.getOrNull(ri)?.let { r ->
                when (r) {
                    is RowDef.Regular      -> r.movies
                    is RowDef.Studio       -> r.movies
                    is RowDef.StudioRibbon -> emptyList()
                }
            }?.firstOrNull()
            if (m != null && m.id != focusState.heroMovie?.id) {
                focusState.heroMovie = m
            }
        }
    }

    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) {
            focusState.heroMovie = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r ->
                when (r) {
                    is RowDef.Regular -> r.movies
                    is RowDef.Studio  -> r.movies
                    else -> null
                }
            }?.firstOrNull()
        }
    }

    LaunchedEffect(rows) {
        if (focusState.isNavFocused || focusState.heroMovie == null) {
            val m = rows.firstOrNull { it !is RowDef.StudioRibbon }?.let { r ->
                when (r) {
                    is RowDef.Regular -> r.movies
                    is RowDef.Studio  -> r.movies
                    else -> null
                }
            }?.firstOrNull()
            if (m != null) focusState.heroMovie = m
        }
    }

    BackHandler(enabled = focusState.isNavFocused) { focusState.isNavFocused = false }

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.retry() }; return@Box }
        }

        BackdropLayer(focusState.heroMovie)
        Box(Modifier.fillMaxSize().background(rowsOverlay))
        HeroOverlay(focusState.heroMovie, panelH)

        val context = LocalContext.current
        ContentLayer(
            rows                = rows,
            contentAlpha        = contentAlpha,
            focusState          = focusState,
            activeTab           = state.selectedTab,
            activeFilter        = currentFilter,
            panelH              = panelH,
            rowHeightFor        = { i -> rowHeightFor(i) },
            firstContentIndex   = firstContentIndex,
            onMovieClick        = { id ->
                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
                    context.imageLoader.memoryCache?.clear()
                }
                onMovieClick(id)
            },
            onHeroUpdate        = { focusState.heroMovie = it },
            onStudioFilterClick = { filter ->
                if (state.selectedStudioFilter == filter) viewModel.setStudioFilter(null)
                else viewModel.setStudioFilter(filter)
            },
            onLoadMore  = { id -> viewModel.loadMore(id) },
            onSearch    = { navController.navigate("search") },
            onHomeTab   = { viewModel.selectTab("ראשי");   viewModel.setStudioFilter(null) },
            onMoviesTab = { viewModel.selectTab("סרטים");  viewModel.setStudioFilter(null) },
            onSeriesTab = { viewModel.selectTab("סדרות");  viewModel.setStudioFilter(null) },
            onFuzer     = {
                viewModel.selectTab("Fuzer")
                viewModel.loadFuzerContent()
            },
            onWatchlist = { navController.navigate("watchlist") },
            onSettings  = { navController.navigate("settings") },
            onIptv      = { navController.navigate("iptv") }
        )
    }
}

@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val tier = DeviceProfile.tier
    val isLow = tier == DeviceProfile.Tier.LOW
    val allowDualBackdrop = false
    val backdropDuration = if (isLow) 0 else DeviceProfile.animConfig.backdropDuration.coerceAtLeast(80)

    var shownUrl   by remember { mutableStateOf<String?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var swapping   by remember { mutableStateOf(false) }

    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl

    LaunchedEffect(heroUrl) {
        if (heroUrl == shownUrl) return@LaunchedEffect
        pendingUrl = heroUrl
        swapping = true
        if (!isLow && backdropDuration > 0 && shownUrl != null) {
            delay((backdropDuration / 3L).coerceAtLeast(16L))
        }
        shownUrl = heroUrl
        if (!isLow) delay(backdropDuration.toLong().coerceAtLeast(16L))
        swapping = false
    }

    val overlayAlpha by animateFloatAsState(
        targetValue   = if (swapping) 0f else 1f,
        animationSpec = if (isLow) snap() else tween(backdropDuration, easing = LinearEasing),
        label         = "bdAlpha"
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(BG))
        if (!shownUrl.isNullOrBlank()) {
            AsyncImage(
                model = remember(shownUrl) {
                    ImageRequest.Builder(ctx)
                        .data(shownUrl)
                        .size(Size.ORIGINAL)
                        .scale(Scale.FILL)
                        .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                        .dispatcher(kotlinx.coroutines.Dispatchers.IO)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true)
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }

        if (allowDualBackdrop && swapping && !pendingUrl.isNullOrBlank() && pendingUrl != shownUrl) {
            AsyncImage(
                model = remember(pendingUrl) {
                    ImageRequest.Builder(ctx)
                        .data(pendingUrl)
                        .size(Size.ORIGINAL)
                        .scale(Scale.FILL)
                        .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                        .dispatcher(kotlinx.coroutines.Dispatchers.IO)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true)
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - overlayAlpha }
            )
        }

        Box(Modifier.fillMaxSize().background(heroScrimLeft))
        Box(Modifier.fillMaxSize().background(heroScrimTop))
    }
}

@Composable
private fun HeroOverlay(hero: Movie?, panelH: Dp) {
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        hero?.let { m ->
            key(m.id) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 60.dp, end = 400.dp, bottom = panelH + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val tsz = when {
                        m.title.length > 26 -> 28.sp
                        m.title.length > 16 -> 34.sp
                        else                -> 44.sp
                    }
                    Text(
                        text          = m.title,
                        color         = WHITE,
                        fontSize      = tsz,
                        fontWeight    = FontWeight.Black,
                        lineHeight    = (tsz.value * 1.15f).sp,
                        letterSpacing = (-0.3).sp,
                        maxLines      = 2,
                        overflow      = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.year > 0) { Text(m.year.toString(), color = DIM, fontSize = 13.sp); MetaDot() }
                        if (m.genre.isNotBlank()) { Text(m.genre, color = DIM, fontSize = 13.sp); MetaDot() }
                        Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = DIM, fontSize = 13.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF5C518))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (m.overview.isNotBlank()) {
                        Text(
                            text      = m.overview,
                            color     = DIM2,
                            fontSize  = 13.sp,
                            lineHeight = 20.sp,
                            maxLines  = 4,
                            overflow  = TextOverflow.Ellipsis,
                            modifier  = Modifier.widthIn(max = 640.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun MetaDot() = Text("  ·  ", color = DIM3, fontSize = 14.sp)

@Composable
private fun ContentLayer(
    rows: List<RowDef>, contentAlpha: Float,
    focusState: HomeFocusState, activeTab: String, activeFilter: String?,
    panelH: Dp, rowHeightFor: (Int) -> Dp, firstContentIndex: Int,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit
) {
    val firstNavFR   = remember { FocusRequester() }
    val firstCardFRs = remember { List(30) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }


    // 1. הגדרת CoroutineScope עבור העברת הפוקוס האוטומטית
    val scope = rememberCoroutineScope()

    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH
    val animatedContentAlpha by animateFloatAsState(
        targetValue   = contentAlpha,
        animationSpec = if (isHighTier) tween(250, easing = LinearEasing) else snap(),
        label         = "contentAlpha"
    )

    LaunchedEffect(Unit) {
        delay(150)
        if (focusState.isNavFocused) {
            runCatching { firstNavFR.requestFocus() }
        } else if (rows.isNotEmpty()) {
            val idx = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
            runCatching { firstCardFRs.getOrNull(idx)?.requestFocus() }
        }
    }

    LaunchedEffect(rows.size) {
        if (!initialFocusDone && rows.isNotEmpty()) {
            delay(380); initialFocusDone = true
            if (!focusState.isNavFocused) {
                val idx = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
                runCatching { firstCardFRs.getOrNull(idx)?.requestFocus() }
            }
        }
    }

    LaunchedEffect(focusState.focusTrigger) {
        if (focusState.focusTrigger > 0) {
            focusState.currentRowIndex = firstContentIndex
            focusState.isNavFocused = false

            // מנסה לתפוס את הפוקוס בבטחה תוך כדי שהשורה מתרנדרת על המסך
            for (i in 1..5) {
                delay(80)
                val success = runCatching {
                    firstCardFRs.getOrNull(firstContentIndex)?.requestFocus()
                    true
                }.getOrDefault(false)

                if (success) break
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        if (focusState.isNavFocused) return@onPreviewKeyEvent true
                        if (focusState.currentRowIndex <= 0) {
                            focusState.isNavFocused = true
                            runCatching { firstNavFR.requestFocus() }
                            true
                        } else {
                            focusState.currentRowIndex--
                            runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        if (focusState.isNavFocused) {
                            focusState.isNavFocused = false
                            focusState.currentRowIndex = 0
                            runCatching { firstCardFRs.getOrNull(0)?.requestFocus() }
                            true
                        } else {
                            if (rows.isNotEmpty() && focusState.currentRowIndex < rows.size - 1) {
                                focusState.currentRowIndex++
                                runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                                true
                            } else true
                        }
                    }
                    Key.Back, Key.Escape -> {
                        if (focusState.isNavFocused) {
                            focusState.isNavFocused = false
                            runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        TwoRowNavBar(
            activeTab  = activeTab,
            firstNavFR = firstNavFR,
            onSearch    = onSearch,
            onHomeTab   = onHomeTab,
            onMoviesTab = onMoviesTab,
            onSeriesTab = onSeriesTab,
            onFuzer     = onFuzer,
            onWatchlist = onWatchlist,
            onSettings  = onSettings,
            onIptv      = onIptv,
            onNavFocus  = { focusState.isNavFocused = true; focusState.currentRowIndex = 0 },
            modifier    = Modifier
                .fillMaxWidth()
                .height(NAV_H)
                .align(Alignment.TopStart)
                .zIndex(10f)
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(panelH)
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = animatedContentAlpha }
        ) {
            RowsPanel(
                rows                = rows,
                focusState          = focusState,
                rowFRs              = firstCardFRs,
                panelH              = panelH,
                rowHeightFor        = rowHeightFor,
                firstContentIndex   = firstContentIndex,
                activeFilter        = activeFilter,
                onStudioFilterClick = onStudioFilterClick,
                onLoadMore          = onLoadMore,
                onItemFocus         = onHeroUpdate,
                onItemClick         = onMovieClick
            )
        }
    }
}
@Composable
private fun TwoRowNavBar(
    activeTab: String, firstNavFR: FocusRequester,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit,
    onNavFocus: () -> Unit, modifier: Modifier = Modifier
) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = java.util.Calendar.getInstance()
            time = "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    val tabPositions = remember { mutableStateMapOf<String, Float>() }
    val tabWidths    = remember { mutableStateMapOf<String, Dp>() }

    val targetX     = tabPositions[activeTab] ?: 0f
    val targetWidth = tabWidths[activeTab] ?: 0.dp

    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH
    val pillSpec   = if (isHighTier) tween<Float>(300, easing = FastOutSlowInEasing) else snap()
    val pillDpSpec = if (isHighTier) tween<Dp>(300, easing = FastOutSlowInEasing)    else snap()

    val animatedX     by animateFloatAsState(targetValue = targetX,     animationSpec = pillSpec,   label = "pillX")
    val animatedWidth by animateDpAsState(targetValue = targetWidth,    animationSpec = pillDpSpec, label = "pillW")

    Column(modifier = modifier.onFocusChanged { if (it.hasFocus) onNavFocus() }) {
        Row(
            modifier          = Modifier.fillMaxWidth().height(NAV_SEARCH_H).padding(horizontal = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuminaLogo()
            Spacer(Modifier.weight(1f))
            Text(time, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(NAV_GAP))

        Box(
            modifier         = Modifier.fillMaxWidth().height(NAV_PILLS_H).padding(horizontal = 52.dp),
            contentAlignment = AbsoluteAlignment.CenterLeft
        ) {
            if (animatedWidth > 0.dp) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .width(animatedWidth)
                        .height(NAV_PILL_H)
                        .graphicsLayer { translationX = animatedX },
                    color    = WHITE, shape = RoundedCornerShape(50)
                ) {}
            }
            Row(
                modifier              = Modifier.fillMaxSize(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavPill(tr("Home", "ראשי"),      Icons.Default.Home,        activeTab == "ראשי",   firstNavFR, onHomeTab)   { o, w -> tabPositions["ראשי"]      = o; tabWidths["ראשי"]      = w }
                NavPill(tr("Movies", "סרטים"),    Icons.Default.Movie,       activeTab == "סרטים",  null,       onMoviesTab)  { o, w -> tabPositions["סרטים"]     = o; tabWidths["סרטים"]     = w }
                NavPill(tr("TV Shows", "סדרות"),  Icons.Default.Tv,          activeTab == "סדרות",  null,       onSeriesTab)  { o, w -> tabPositions["סדרות"]     = o; tabWidths["סדרות"]     = w }
                NavPill("Fuzer",                 Icons.Default.LocalMovies, activeTab == "Fuzer",  null,       onFuzer)      { o, w -> tabPositions["Fuzer"]     = o; tabWidths["Fuzer"]     = w }
                NavPill(tr("Live TV", "טלוויזיה חיה"),  Icons.Default.Cast,        false,                 null,       onIptv)       { o, w -> tabPositions["iptv"]      = o; tabWidths["iptv"]      = w }
                NavPill(tr("Watchlist", "רשימת צפייה"), Icons.Default.Bookmark,    false,                 null,       onWatchlist)  { o, w -> tabPositions["Watchlist"] = o; tabWidths["Watchlist"] = w }
                NavPill(tr("Settings", "הגדרות"),  Icons.Default.Settings,    false,                 null,       onSettings)   { o, w -> tabPositions["Settings"]  = o; tabWidths["Settings"]  = w }
                Spacer(Modifier.weight(1f))
                SearchBarButton(onClick = onSearch)
            }
        }
    }
}

@Composable
private fun LuminaLogo() {
    Image(
        painter = painterResource(id = com.luminastreams.tv.R.drawable.logo_lumina_unified),
        contentDescription = "Lumina Logo",
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(64.dp)
    )
}

@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = NAV_GLASS,
            focusedContainerColor = Color(0x44FFFFFF),
            contentColor          = DIM2,
            focusedContentColor   = WHITE
        ),
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            border        = Border(BorderStroke(1.dp, Color(0x25FFFFFF)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(1.5.dp, Color(0x70FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(34.dp).width(260.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(13.dp))
            Text(tr("Search movies, shows...", "חיפוש סרטים וסדרות..."), fontSize = 12.sp, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun NavPill(
    label: String, icon: ImageVector, isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onTabPositioned: (Float, Dp) -> Unit
) {
    val density      = LocalDensity.current
    val contentColor = if (isSelected) Color(0xFF0C0C0C) else WHITE

    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = NAV_FOCUS,
            pressedContainerColor = Color(0x20FFFFFF),
            contentColor          = contentColor,
            focusedContentColor   = contentColor
        ),
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border        = Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, Color(0x66FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier
            .height(NAV_PILL_H)
            .wrapContentWidth()
            .onGloballyPositioned { coords ->
                onTabPositioned(coords.positionInParent().x, with(density) { coords.size.width.toDp() })
            }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        Box(Modifier.fillMaxHeight().wrapContentWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(14.dp))
                Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.2.sp, softWrap = false, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RowsPanel(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>,
    panelH: Dp, rowHeightFor: (Int) -> Dp, firstContentIndex: Int,
    activeFilter: String?,
    onStudioFilterClick: (String?) -> Unit,
    onLoadMore: (String) -> Unit,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (rows.isEmpty()) return
    val curRow = focusState.currentRowIndex.coerceIn(0, rows.size - 1)

    val renderMargin = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> 3
        DeviceProfile.Tier.MID  -> 1
        DeviceProfile.Tier.LOW  -> 1
    }

    val targetYOffset: Dp = remember(curRow, rows.size) {
        var acc = 0.dp
        for (i in 0 until curRow) acc += rowHeightFor(i)
        -acc
    }

    val slideSpec = when (DeviceProfile.tier) {
        DeviceProfile.Tier.LOW  -> snap()
        DeviceProfile.Tier.MID  -> tween<Dp>(200, easing = FastOutSlowInEasing)
        DeviceProfile.Tier.HIGH -> tween<Dp>(400, easing = FastOutSlowInEasing)
    }
    val animatedY by animateDpAsState(
        targetValue   = targetYOffset,
        animationSpec = slideSpec,
        label         = "rowsSlide"
    )

    Box(Modifier.fillMaxWidth().height(panelH).clipToBounds()) {
        val density = LocalDensity.current
        Box(Modifier.fillMaxWidth().graphicsLayer {
            translationY = with(density) { animatedY.toPx() }
        }) {
            var yAccum = 0.dp
            rows.forEachIndexed { i, rowDef ->
                val rh       = rowHeightFor(i)
                val isLand   = (i == firstContentIndex)
                val isActive = !focusState.isNavFocused && i == curRow
                val yOffset  = yAccum
                val inWindow = kotlin.math.abs(i - curRow) <= renderMargin

                key(rowDef.id) {
                    if (!inWindow) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rh)
                                .offset(y = yOffset)
                        )
                    } else {
                        val rowAlpha         = if (i == curRow) 1f else 0.22f
                        val useAnimatedAlpha = DeviceProfile.animConfig.enableRowFade &&
                                DeviceProfile.tier == DeviceProfile.Tier.HIGH

                        val animatedAlpha by animateFloatAsState(
                            targetValue   = rowAlpha,
                            animationSpec = if (useAnimatedAlpha)
                                tween(
                                    durationMillis = if (i == curRow)
                                        DeviceProfile.animConfig.rowFadeDuration
                                    else
                                        (DeviceProfile.animConfig.rowFadeDuration / 2).coerceAtLeast(60),
                                    easing = FastOutSlowInEasing
                                )
                            else
                                snap(),
                            label = "a$i"
                        )

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rh)
                                .offset(y = yOffset)
                                .graphicsLayer {
                                    alpha = if (useAnimatedAlpha) animatedAlpha else rowAlpha
                                }
                        ) {
                            val cardFR = rowFRs.getOrNull(i)
                            val onFocus: (Movie) -> Unit = { m ->
                                focusState.currentRowIndex = i
                                focusState.isNavFocused    = false
                                onItemFocus(m)
                            }

                            if (rowDef is RowDef.StudioRibbon) {
                                StudioRibbonRow(isActive, cardFR, activeFilter, onStudioFilterClick)
                            } else if (isLand) {
                                when (rowDef) {
                                    is RowDef.Regular -> LandscapeRow(
                                        rowDef.title, rowDef.movies, isActive, cardFR,
                                        onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) }
                                    )
                                    is RowDef.Studio  -> LandscapeStudioRow(
                                        rowDef.brand, rowDef.movies, isActive, cardFR,
                                        onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) }
                                    )
                                    else -> {}
                                }
                            } else {
                                when (rowDef) {
                                    is RowDef.Regular -> PortraitRow(
                                        rowDef.title, rowDef.movies, isActive, cardFR,
                                        onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) }
                                    )
                                    is RowDef.Studio  -> PortraitStudioRow(
                                        rowDef.brand, rowDef.movies, isActive, cardFR,
                                        onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) }
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                yAccum += rh
            }
        }
    }
}

@Composable
private fun LandscapeRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    RememberPagedRowLoad(rowState = rowState, onLoadMore = onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                LandscapeCard(
                    movie     = movie,
                    modifier  = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    onFocused = { onFocus(movie) },
                    onClick   = { onClick(movie.id) }
                )
            }
        }
    }
}

@Composable
private fun LandscapeStudioRow(
    brand: StudioBrand, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    RememberPagedRowLoad(rowState = rowState, onLoadMore = onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                LandscapeCard(
                    movie     = movie,
                    modifier  = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    onFocused = { onFocus(movie) },
                    onClick   = { onClick(movie.id) }
                )
            }
        }
    }
}

@Composable
private fun PortraitRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    RememberPagedRowLoad(rowState = rowState, onLoadMore = onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                PosterCard(
                    movie     = movie,
                    modifier  = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    cardW     = PORT_W, cardH = PORT_H,
                    onFocused = { onFocus(movie) },
                    onClick   = { onClick(movie.id) }
                )
            }
        }
    }
}

@Composable
private fun PortraitStudioRow(
    brand: StudioBrand, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    RememberPagedRowLoad(rowState = rowState, onLoadMore = onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                PosterCard(
                    movie     = movie,
                    modifier  = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    cardW     = PORT_W, cardH = PORT_H,
                    onFocused = { onFocus(movie) },
                    onClick   = { onClick(movie.id) }
                )
            }
        }
    }
}

@Composable
private fun studioLabel(b: StudioBrand): String {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun _tr(en: String, he: String) = if (isRtl) he else en

    return when (b) {
        StudioBrand.NETFLIX   -> _tr("Netflix Originals", "מקור של נטפליקס")
        StudioBrand.APPLE_TV  -> _tr("Apple TV+ Originals", "מקור של אפל TV")
        StudioBrand.DISNEY    -> _tr("Disney+ Exclusives", "בלעדי לדיסני+")
        StudioBrand.HBO       -> _tr("HBO Max Exclusives", "בלעדי ל-HBO")
        StudioBrand.AMAZON    -> _tr("Amazon Originals", "מקור של אמאזון")
        StudioBrand.PARAMOUNT -> _tr("Paramount+ Exclusives", "בלעדי לפרמאונט")
        StudioBrand.HULU      -> _tr("Hulu Originals", "מקור של הולו")
    }
}

@Composable
private fun RowLabel(title: String, isActive: Boolean, modifier: Modifier = Modifier) {
    Text(
        text          = title,
        color         = WHITE.copy(alpha = if (isActive) 1f else 0.38f),
        fontSize      = 14.sp,
        fontWeight    = if (isActive) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = 0.3.sp,
        modifier      = modifier
    )
}

@Composable
private fun StudioBadge(brand: StudioBrand, isActive: Boolean, isLarge: Boolean = false) {
    val a = if (isActive) 1f else 0.4f
    val imageRes = when (brand) {
        StudioBrand.NETFLIX   -> R.drawable.logo_netflix
        StudioBrand.APPLE_TV  -> R.drawable.logo_appletv
        StudioBrand.DISNEY    -> R.drawable.logo_disney
        StudioBrand.HBO       -> R.drawable.logo_hbo
        StudioBrand.AMAZON    -> R.drawable.logo_amazon
        StudioBrand.PARAMOUNT -> R.drawable.logo_paramount
        StudioBrand.HULU      -> R.drawable.logo_hulu
    }
    Box(
        modifier         = Modifier
            .height(if (isLarge) 32.dp else 22.dp)
            .width(if (isLarge) 80.dp else 50.dp)
            .alpha(a),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter            = painterResource(id = imageRes),
            contentDescription = brand.name,
            contentScale       = ContentScale.Fit,
            colorFilter        = null,
            modifier           = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LandscapeCard(
    movie: Movie, modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx       = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }

    val cardAnimSpec = remember {
        if (DeviceProfile.tier == DeviceProfile.Tier.HIGH)
            spring<Float>(stiffness = Spring.StiffnessMediumLow)
        else
            tween<Float>(durationMillis = 90, easing = LinearEasing)
    }

    val zoom by animateFloatAsState(
        targetValue   = if (isFocused.value) 1.06f else 1f,
        animationSpec = cardAnimSpec,
        label         = "lzoom"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFocused.value && DeviceProfile.tier == DeviceProfile.Tier.HIGH) 0.18f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "loverlay"
    )

    val url = movie.backdropUrl.ifBlank { movie.posterUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            // מבטל שמירה בזיכרון RAM למכשירים חלשים, משתמש רק בדיסק
            .memoryCachePolicy(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    Box(
        modifier
            .width(LAND_W).height(LAND_H)
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
    ) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(
                containerColor        = CARD_BG,
                focusedContainerColor = CARD_BG
            ),
            shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
            glow   = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { fs ->
                    isFocused.value = fs.isFocused
                    if (fs.isFocused) onFocused()
                }
        ) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model              = imageRequest,
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) {
                    Text(movie.title, color = WHITE.copy(0.5f), fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                }
            }

            Box(Modifier.fillMaxSize().background(cardBottomGradient))

            if (isFocused.value && DeviceProfile.tier == DeviceProfile.Tier.HIGH) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(WHITE.copy(overlayAlpha * 1.8f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, WHITE.copy(overlayAlpha * 4f), Color.Transparent)
                            )
                        )
                )
            }

            if (movie.id.startsWith("http")) {
                val isDubbed = movie.title.contains("מדובב")
                Box(
                    Modifier
                        .align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE,
                        fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(movie.title, color = WHITE, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = DIM2, fontSize = 11.sp)
            }

            if (movie.rating > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd).padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold)
                }
            }

            movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                val displayProg = if (prog >= 0.95f) 1f else prog
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(displayProg.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(RED)
                    )
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    movie: Movie, modifier: Modifier = Modifier,
    cardW: Dp = PORT_W, cardH: Dp = PORT_H,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx       = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }

    val cardAnimSpec = remember {
        if (DeviceProfile.tier == DeviceProfile.Tier.HIGH)
            spring<Float>(stiffness = Spring.StiffnessMediumLow)
        else
            tween<Float>(durationMillis = 90, easing = LinearEasing)
    }

    val zoom by animateFloatAsState(
        targetValue   = if (isFocused.value) 1.08f else 1f,
        animationSpec = cardAnimSpec,
        label         = "pzoom"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFocused.value && DeviceProfile.tier == DeviceProfile.Tier.HIGH) 0.15f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "poverlay"
    )

    val url = movie.posterUrl.ifBlank { movie.backdropUrl }
    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Box(Modifier.width(cardW).height(cardH).graphicsLayer { scaleX = zoom; scaleY = zoom }) {
            Surface(
                onClick = onClick,
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor        = CARD_BG,
                    focusedContainerColor = CARD_BG
                ),
                shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                glow   = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { fs ->
                        isFocused.value = fs.isFocused
                        if (fs.isFocused) onFocused()
                    }
            ) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model              = imageRequest,
                        contentDescription = movie.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) {
                        Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp))
                    }
                }

                if (isFocused.value && DeviceProfile.tier == DeviceProfile.Tier.HIGH) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(WHITE.copy(overlayAlpha * 2f), Color.Transparent, Color.Transparent)
                                )
                            )
                    )
                }

                if (movie.id.startsWith("http")) {
                    val isDubbed = movie.title.contains("מדובב")
                    Box(
                        Modifier
                            .align(Alignment.TopStart).padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE,
                            fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }

                if (movie.rating > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd).padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xBB000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }

                movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                    val displayProg = if (prog >= 0.95f) 1f else prog
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x55000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(displayProg.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(RED)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            movie.title,
            color      = if (isFocused.value) WHITE else DIM2,
            fontSize   = 11.sp,
            fontWeight = if (isFocused.value) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.width(cardW)
        )
        Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = DIM3, fontSize = 10.sp)
    }
}

@Composable
private fun StudioRibbonRow(
    isActive: Boolean, cardFR: FocusRequester?,
    activeFilter: String?, onStudioFilterClick: (String?) -> Unit
) {
    val brands   = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    val rowState = rememberLazyListState()

    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            tr("Browse by Studio", "סנן לפי אולפן"),
            color      = WHITE.copy(if (isActive) 1f else 0.4f),
            fontSize   = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            modifier   = Modifier.padding(start = 52.dp, bottom = 12.dp)
        )
        LazyRow(
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(brands, key = { _, b -> b.name }) { i, brand ->
                val isSelected = activeFilter == brand.name
                StudioLogoButton(
                    brand      = brand,
                    isSelected = isSelected,
                    modifier   = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    onClick    = { onStudioFilterClick(brand.name) }
                )
            }
        }
    }
}

@Composable
private fun StudioLogoButton(brand: StudioBrand, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val focusState   = remember { mutableStateOf(false) }
    val containerCol = if (isSelected) WHITE.copy(0.15f) else CARD_BG
    val borderCol    = if (isSelected) WHITE else Color.Transparent

    val btnScale = if (DeviceProfile.tier == DeviceProfile.Tier.HIGH) 1.08f else 1.0f

    Surface(
        onClick = onClick,
        scale   = ClickableSurfaceDefaults.scale(focusedScale = btnScale),
        colors  = ClickableSurfaceDefaults.colors(containerColor = containerCol, focusedContainerColor = WHITE),
        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border  = ClickableSurfaceDefaults.border(
            border        = Border(BorderStroke(1.5.dp, borderCol), shape = RoundedCornerShape(12.dp)),
            focusedBorder = Border(BorderStroke(2.5.dp, WHITE), shape = RoundedCornerShape(12.dp))
        ),
        modifier = modifier.width(130.dp).height(65.dp).onFocusChanged { focusState.value = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            StudioBadge(brand = brand, isActive = true, isLarge = true)
        }
    }
}

@Composable
fun HomeLoading() {
    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    val shimmer: Brush = if (isHighTier) {
        val inf = rememberInfiniteTransition(label = "sk")
        val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")
        Brush.linearGradient(
            listOf(Color(0xFF111111), Color(0xFF282828), Color(0xFF111111)),
            start = Offset(p * 2400f - 1200f, 0f), end = Offset(p * 2400f, 600f)
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF1E1E1E), Color(0xFF111111)))
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { Box(Modifier.width(LAND_W).height(LAND_H).clip(RoundedCornerShape(10.dp)).background(shimmer)) }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(8) { Box(Modifier.width(PORT_W).height(PORT_H).clip(RoundedCornerShape(10.dp)).background(shimmer)) }
            }
        }
    }
}

@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("⚠", fontSize = 52.sp)
            Text(message, color = DIM, fontSize = 16.sp, maxLines = 2)
            Surface(
                onClick  = onRetry,
                colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(RED.copy(0.55f), 20.dp)),
                modifier = Modifier.height(50.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(tr("Retry", "נסה שוב"), color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}