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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

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

private const val BACKDROP_W     = 3840
private const val BACKDROP_H     = 2160
private const val BACKDROP_W_LOW = 1920
private const val BACKDROP_H_LOW = 1080
private const val LAND_IMG_W     = 600
private const val LAND_IMG_H     = 340
private const val PORT_IMG_W     = 300
private const val PORT_IMG_H     = 450

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
        0.30f to Color(0x60070707),
        0.60f to Color(0xA8070707),
        0.85f to Color(0xD8070707),
        1.00f to Color(0xF4070707)
    )
)

@Composable
fun tr(en: String, he: String): String {
    return if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en
}

@Stable
class HomeFocusState(initialRow: Int = 0) {
    var isNavFocused    by mutableStateOf(false)
    var currentRowIndex by mutableIntStateOf(initialRow)
    var heroMovie       by mutableStateOf<Movie?>(null)

    companion object {
        val Saver: Saver<HomeFocusState, Int> = Saver(
            save    = { it.currentRowIndex },
            restore = { HomeFocusState(it) }
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }

    var currentTab by remember { mutableStateOf(state.selectedTab) }
    var currentFilter by remember { mutableStateOf(state.selectedStudioFilter) }
    var contentAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(state.selectedTab, state.selectedStudioFilter) {
        if (currentTab != state.selectedTab || currentFilter != state.selectedStudioFilter) {
            contentAlpha = 0f
            delay(300)
            currentTab = state.selectedTab
            currentFilter = state.selectedStudioFilter
            focusState.currentRowIndex = 0
            delay(30)
            contentAlpha = 1f
        }
    }

    val maxItems = DeviceProfile.animConfig.maxRowItems

    val rows: List<RowDef> = buildList {
        val filter = currentFilter
        when (currentTab) {
            "ראשי" -> {
                if (state.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", tr("Trending Movies", "סרטים פופולריים"), state.movieTrending.take(maxItems)))
                if (state.movieHBO.isNotEmpty()) add(RowDef.Studio("movieHBO", StudioBrand.HBO, state.movieHBO.take(maxItems)))
                if (state.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", tr("Popular Shows", "סדרות פופולריות"), state.tvTrending.take(maxItems)))
                if (state.movieNetflix.isNotEmpty()) add(RowDef.Studio("movieNetflix", StudioBrand.NETFLIX, state.movieNetflix.take(maxItems)))
                if (state.movieAmazon.isNotEmpty()) add(RowDef.Studio("movieAmazon", StudioBrand.AMAZON, state.movieAmazon.take(maxItems)))
                if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", tr("New in Theaters", "בקולנוע"), state.moviePremieres.take(maxItems)))
                if (state.tvAppleTV.isNotEmpty()) add(RowDef.Studio("tvAppleTV", StudioBrand.APPLE_TV, state.tvAppleTV.take(maxItems)))
            }
            "סרטים" -> {
                add(RowDef.StudioRibbon)
                if (filter == null || filter == "HBO") if (state.movieHBO.isNotEmpty()) add(RowDef.Studio("movieHBO", StudioBrand.HBO, state.movieHBO.take(maxItems)))
                if (filter == null || filter == "AMAZON") if (state.movieAmazon.isNotEmpty()) add(RowDef.Studio("movieAmazon", StudioBrand.AMAZON, state.movieAmazon.take(maxItems)))
                if (filter == null || filter == "PARAMOUNT") if (state.movieParamount.isNotEmpty()) add(RowDef.Studio("movieParamount", StudioBrand.PARAMOUNT, state.movieParamount.take(maxItems)))
                if (filter == null || filter == "HULU") if (state.movieHulu.isNotEmpty()) add(RowDef.Studio("movieHulu", StudioBrand.HULU, state.movieHulu.take(maxItems)))
                if (filter == null || filter == "NETFLIX") if (state.movieNetflix.isNotEmpty()) add(RowDef.Studio("movieNetflix", StudioBrand.NETFLIX, state.movieNetflix.take(maxItems)))
                if (filter == null || filter == "APPLE_TV") if (state.movieAppleTV.isNotEmpty()) add(RowDef.Studio("movieAppleTV", StudioBrand.APPLE_TV, state.movieAppleTV.take(maxItems)))
                if (filter == null || filter == "DISNEY") if (state.movieDisney.isNotEmpty()) add(RowDef.Studio("movieDisney", StudioBrand.DISNEY, state.movieDisney.take(maxItems)))
                if (filter == null) {
                    if (state.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", tr("Trending Now", "פופולרי עכשיו"), state.movieTrending.take(maxItems)))
                    if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", tr("New in Theaters", "בקולנוע"), state.moviePremieres.take(maxItems)))
                    if (state.movieAction.isNotEmpty()) add(RowDef.Regular("movieAction", tr("Action & Adventure", "פעולה והרפתקאות"), state.movieAction.take(maxItems)))
                    if (state.movieDrama.isNotEmpty()) add(RowDef.Regular("movieDrama", tr("Drama", "דרמה"), state.movieDrama.take(maxItems)))
                    if (state.movieScifi.isNotEmpty()) add(RowDef.Regular("movieScifi", tr("Sci-Fi", "מדע בדיוני"), state.movieScifi.take(maxItems)))
                    if (state.movieTopRated.isNotEmpty()) add(RowDef.Regular("movieTopRated", tr("Top Rated", "דירוג גבוה"), state.movieTopRated.take(maxItems)))
                }
            }
            "סדרות" -> {
                add(RowDef.StudioRibbon)
                if (filter == null || filter == "HBO") if (state.tvHBO.isNotEmpty()) add(RowDef.Studio("tvHBO", StudioBrand.HBO, state.tvHBO.take(maxItems)))
                if (filter == null || filter == "AMAZON") if (state.tvAmazon.isNotEmpty()) add(RowDef.Studio("tvAmazon", StudioBrand.AMAZON, state.tvAmazon.take(maxItems)))
                if (filter == null || filter == "PARAMOUNT") if (state.tvParamount.isNotEmpty()) add(RowDef.Studio("tvParamount", StudioBrand.PARAMOUNT, state.tvParamount.take(maxItems)))
                if (filter == null || filter == "HULU") if (state.tvHulu.isNotEmpty()) add(RowDef.Studio("tvHulu", StudioBrand.HULU, state.tvHulu.take(maxItems)))
                if (filter == null || filter == "NETFLIX") if (state.tvNetflix.isNotEmpty()) add(RowDef.Studio("tvNetflix", StudioBrand.NETFLIX, state.tvNetflix.take(maxItems)))
                if (filter == null || filter == "APPLE_TV") if (state.tvAppleTV.isNotEmpty()) add(RowDef.Studio("tvAppleTV", StudioBrand.APPLE_TV, state.tvAppleTV.take(maxItems)))
                if (filter == null || filter == "DISNEY") if (state.tvDisney.isNotEmpty()) add(RowDef.Studio("tvDisney", StudioBrand.DISNEY, state.tvDisney.take(maxItems)))
                if (filter == null) {
                    if (state.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", tr("Trending Shows", "סדרות פופולריות"), state.tvTrending.take(maxItems)))
                    if (state.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", tr("On The Air", "משודר כעת"), state.tvPremieres.take(maxItems)))
                    if (state.tvDrama.isNotEmpty()) add(RowDef.Regular("tvDrama", tr("Drama", "דרמה"), state.tvDrama.take(maxItems)))
                    if (state.tvCrime.isNotEmpty()) add(RowDef.Regular("tvCrime", tr("Crime & Thriller", "פשע ומתח"), state.tvCrime.take(maxItems)))
                    if (state.tvScifi.isNotEmpty()) add(RowDef.Regular("tvScifi", tr("Sci-Fi & Fantasy", "מדע בדיוני ופנטזיה"), state.tvScifi.take(maxItems)))
                    if (state.tvTopRated.isNotEmpty()) add(RowDef.Regular("tvTopRated", tr("Top Rated Shows", "סדרות מומלצות"), state.tvTopRated.take(maxItems)))
                }
            }
            "Fuzer" -> {
                val newContent = (state.fuzerMovies + state.fuzerSeries).sortedByDescending { it.id }
                if (newContent.isNotEmpty()) add(RowDef.Regular("fuzer_new", tr("🆕 New Content", "🆕 תוכן חדש"), newContent.take(maxItems)))
                if (state.fuzerMovies.isNotEmpty()) add(RowDef.Regular("fuzer_m", tr("🎬 Movies", "🎬 סרטים"), state.fuzerMovies.take(maxItems)))
                if (state.fuzerMoviesHD.isNotEmpty()) add(RowDef.Regular("fuzer_mhd", tr("🎬 Movies HD", "🎬 סרטים HD"), state.fuzerMoviesHD.take(maxItems)))
                if (state.fuzerMovies4K.isNotEmpty()) add(RowDef.Regular("fuzer_m4k", tr("✨ Movies 4K", "✨ סרטים 4K"), state.fuzerMovies4K.take(maxItems)))
                if (state.fuzerDubbedMovies.isNotEmpty()) add(RowDef.Regular("fuzer_dm", tr("🎤 Dubbed Movies", "🎤 סרטים מדובבים"), state.fuzerDubbedMovies.take(maxItems)))
                if (state.fuzerSeries.isNotEmpty()) add(RowDef.Regular("fuzer_tv", tr("📺 TV Shows", "📺 סדרות"), state.fuzerSeries.take(maxItems)))
                if (state.fuzerSeriesHD.isNotEmpty()) add(RowDef.Regular("fuzer_shd", tr("📺 TV Shows HD", "📺 סדרות HD"), state.fuzerSeriesHD.take(maxItems)))
                if (state.fuzerSeries4K.isNotEmpty()) add(RowDef.Regular("fuzer_s4k", tr("✨ TV Shows 4K", "✨ סדרות 4K"), state.fuzerSeries4K.take(maxItems)))
                if (state.fuzerDubbedSeries.isNotEmpty()) add(RowDef.Regular("fuzer_ds", tr("🎤 Dubbed Shows", "🎤 סדרות מדובבות"), state.fuzerDubbedSeries.take(maxItems)))
            }
        }
    }

    fun rowHeightFor(i: Int) = when (rows.getOrNull(i)) {
        is RowDef.StudioRibbon -> 110.dp
        else -> if (i == 0 && rows.getOrNull(i) !is RowDef.StudioRibbon) ROW_LANDSCAPE_H else ROW_PORTRAIT_H
    }

    val panelH = remember(rows.size) {
        if (rows.isEmpty()) ROW_PORTRAIT_H else ROW_PORTRAIT_H + 16.dp
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(200L)
            val m = rows.getOrNull(ri)?.let { r ->
                when (r) {
                    is RowDef.Regular -> r.movies
                    is RowDef.Studio -> r.movies
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
                    is RowDef.Studio -> r.movies
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
                    is RowDef.Studio -> r.movies
                    else -> null
                }
            }?.firstOrNull()
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
        Box(Modifier.fillMaxSize().background(rowsOverlay))
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
            onMovieClick = { id ->
                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
                    context.imageLoader.memoryCache?.clear()
                }
                onMovieClick(id)
            },
            onHeroUpdate = { focusState.heroMovie = it },
            onStudioFilterClick = { filter ->
                if (state.selectedStudioFilter == filter) viewModel.setStudioFilter(null)
                else viewModel.setStudioFilter(filter)
            },
            onLoadMore = { id -> viewModel.loadMore(id) },
            onSearch = { navController.navigate("search") },
            onHomeTab = { viewModel.selectTab("ראשי"); viewModel.setStudioFilter(null) },
            onMoviesTab = { viewModel.selectTab("סרטים"); viewModel.setStudioFilter(null) },
            onSeriesTab = { viewModel.selectTab("סדרות"); viewModel.setStudioFilter(null) },
            onFuzer = {
                viewModel.selectTab("Fuzer")
                viewModel.loadFuzerContent()
            },
            onWatchlist = { navController.navigate("watchlist") },
            onSettings = { navController.navigate("settings") },
            onIptv = { navController.navigate("iptv") }
        )
    }
}

@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    val backdropDuration = if (isLow) 0 else DeviceProfile.animConfig.backdropDuration.coerceAtLeast(80)

    var shownUrl by remember { mutableStateOf<String?>(null) }
    var targetUrl by remember { mutableStateOf<String?>(null) }
    var crossAlpha by remember { mutableStateOf(1f) }

    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl

    LaunchedEffect(heroUrl) {
        if (heroUrl == shownUrl) return@LaunchedEffect
        targetUrl = heroUrl
        if (backdropDuration > 0 && shownUrl != null) {
            val steps = 8
            val stepMs = (backdropDuration / 3L / steps).coerceAtLeast(8L)
            for (i in steps downTo 0) {
                crossAlpha = i / steps.toFloat()
                delay(stepMs)
            }
        } else {
            crossAlpha = 0f
        }
        shownUrl = targetUrl
        if (backdropDuration > 0) {
            val steps = 12
            val stepMs = (backdropDuration * 2L / 3L / steps).coerceAtLeast(8L)
            for (i in 0..steps) {
                crossAlpha = i / steps.toFloat()
                delay(stepMs)
            }
        }
        crossAlpha = 1f
    }

    val animAlpha by animateFloatAsState(
        targetValue = crossAlpha,
        animationSpec = tween(0),
        label = "bd"
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(BG))

        if (!shownUrl.isNullOrBlank()) {
            key(shownUrl) {
                AsyncImage(
                    model = remember(shownUrl) {
                        ImageRequest.Builder(ctx)
                            .data(shownUrl)
                            .size(if (isLow) BACKDROP_W_LOW else BACKDROP_W, if (isLow) BACKDROP_H_LOW else BACKDROP_H)
                            .scale(Scale.FILL)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true)
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(animAlpha)
                        .graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                )
            }
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
                        else -> 44.sp
                    }
                    Text(
                        text = m.title,
                        color = WHITE,
                        fontSize = tsz,
                        fontWeight = FontWeight.Black,
                        lineHeight = (tsz.value * 1.15f).sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (m.overview.isNotBlank()) {
                        Text(
                            text = m.overview,
                            color = DIM2,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 640.dp)
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
    panelH: Dp, rowHeightFor: (Int) -> Dp,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit
) {
    val firstNavFR = remember { FocusRequester() }
    val firstCardFRs = remember(rows.size) { List(rows.size) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }

    val animatedContentAlpha by animateFloatAsState(
        targetValue = contentAlpha,
        animationSpec = tween(250, easing = LinearEasing),
        label = "contentAlpha"
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
            delay(380)
            initialFocusDone = true
            if (!focusState.isNavFocused) {
                val idx = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
                runCatching { firstCardFRs.getOrNull(idx)?.requestFocus() }
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
                        } else false
                    }
                    else -> false
                }
            }
            .alpha(animatedContentAlpha)
    ) {
    }
}

@Composable
private fun HomeLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Loading...", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = "Fetching latest content", color = DIM2, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HomeError(message: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "Something went wrong", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!message.isNullOrBlank()) {
                Text(text = message, color = DIM2, fontSize = 14.sp)
            }
            Surface(
                onClick = onRetry,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = RED, contentColor = WHITE)
            ) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(text = "Retry", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
