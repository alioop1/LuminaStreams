@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@file:Suppress("ASSIGNED_BUT_NEVER_READ_REFERENCE", "UNUSED_VARIABLE", "UNUSED_VALUE")

package com.luminastreams.tv.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.R
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.flow.distinctUntilChanged

// ═══════════════════════════════════════════════════════════════════
//  PALETTE
// ═══════════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════════
//  LAYOUT
// ═══════════════════════════════════════════════════════════════════
private val NAV_SEARCH_H = 24.dp
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

private const val LAND_W_PX = 560
private const val LAND_H_PX = 316
private const val PORT_W_PX = 296
private const val PORT_H_PX = 444

// ═══════════════════════════════════════════════════════════════════
//  GRADIENT SCRIMS
// ═══════════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════════
//  FOCUS STATE
// ═══════════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════════
//  HOME SCREEN
// ═══════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state:         HomeState,
    viewModel:     HomeViewModel,
    navController: NavController,
    onMovieClick:  (String) -> Unit
) {
    // ✅ תיקון Davey #1: רק fields רלוונטיים כ-keys, לא state שלם
    val rows: List<RowDef> = remember(
        state.selectedTab, state.selectedStudioFilter,
        state.movieTrending, state.movieHBO, state.tvTrending,
        state.movieNetflix, state.movieAmazon, state.moviePremieres,
        state.tvAppleTV, state.movieParamount, state.movieHulu,
        state.movieAppleTV, state.movieDisney, state.movieAction,
        state.movieDrama, state.movieScifi, state.movieTopRated,
        state.tvHBO, state.tvAmazon, state.tvParamount, state.tvHulu,
        state.tvNetflix, state.tvDisney, state.tvPremieres,
        state.tvDrama, state.tvCrime, state.tvScifi, state.tvTopRated
    ) {
        val filter = state.selectedStudioFilter
        when (state.selectedTab) {
            "ראשי" -> buildList {
                if (state.movieTrending.isNotEmpty())  add(RowDef.Regular("movieTrending", "Trending Movies",       state.movieTrending))
                if (state.movieHBO.isNotEmpty())       add(RowDef.Studio("movieHBO",       StudioBrand.HBO,         state.movieHBO))
                if (state.tvTrending.isNotEmpty())     add(RowDef.Regular("tvTrending",    "Popular Shows",         state.tvTrending))
                if (state.movieNetflix.isNotEmpty())   add(RowDef.Studio("movieNetflix",   StudioBrand.NETFLIX,     state.movieNetflix))
                if (state.movieAmazon.isNotEmpty())    add(RowDef.Studio("movieAmazon",    StudioBrand.AMAZON,      state.movieAmazon))
                if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres","New in Theaters",       state.moviePremieres))
                if (state.tvAppleTV.isNotEmpty())      add(RowDef.Studio("tvAppleTV",      StudioBrand.APPLE_TV,    state.tvAppleTV))
            }
            "סרטים" -> buildList {
                add(RowDef.StudioRibbon)
                if (filter == null || filter == "HBO")       if (state.movieHBO.isNotEmpty())       add(RowDef.Studio("movieHBO",       StudioBrand.HBO,       state.movieHBO))
                if (filter == null || filter == "AMAZON")    if (state.movieAmazon.isNotEmpty())    add(RowDef.Studio("movieAmazon",    StudioBrand.AMAZON,    state.movieAmazon))
                if (filter == null || filter == "PARAMOUNT") if (state.movieParamount.isNotEmpty()) add(RowDef.Studio("movieParamount", StudioBrand.PARAMOUNT, state.movieParamount))
                if (filter == null || filter == "HULU")      if (state.movieHulu.isNotEmpty())      add(RowDef.Studio("movieHulu",      StudioBrand.HULU,      state.movieHulu))
                if (filter == null || filter == "NETFLIX")   if (state.movieNetflix.isNotEmpty())   add(RowDef.Studio("movieNetflix",   StudioBrand.NETFLIX,   state.movieNetflix))
                if (filter == null || filter == "APPLE_TV")  if (state.movieAppleTV.isNotEmpty())   add(RowDef.Studio("movieAppleTV",   StudioBrand.APPLE_TV,  state.movieAppleTV))
                if (filter == null || filter == "DISNEY")    if (state.movieDisney.isNotEmpty())    add(RowDef.Studio("movieDisney",    StudioBrand.DISNEY,    state.movieDisney))
                if (filter == null) {
                    if (state.movieTrending.isNotEmpty())  add(RowDef.Regular("movieTrending",  "Trending Now",       state.movieTrending))
                    if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", "New in Theaters",    state.moviePremieres))
                    if (state.movieAction.isNotEmpty())    add(RowDef.Regular("movieAction",    "Action & Adventure", state.movieAction))
                    if (state.movieDrama.isNotEmpty())     add(RowDef.Regular("movieDrama",     "Drama",              state.movieDrama))
                    if (state.movieScifi.isNotEmpty())     add(RowDef.Regular("movieScifi",     "Sci-Fi",             state.movieScifi))
                    if (state.movieTopRated.isNotEmpty())  add(RowDef.Regular("movieTopRated",  "Top Rated",          state.movieTopRated))
                }
            }
            "סדרות" -> buildList {
                add(RowDef.StudioRibbon)
                if (filter == null || filter == "HBO")       if (state.tvHBO.isNotEmpty())       add(RowDef.Studio("tvHBO",       StudioBrand.HBO,       state.tvHBO))
                if (filter == null || filter == "AMAZON")    if (state.tvAmazon.isNotEmpty())    add(RowDef.Studio("tvAmazon",    StudioBrand.AMAZON,    state.tvAmazon))
                if (filter == null || filter == "PARAMOUNT") if (state.tvParamount.isNotEmpty()) add(RowDef.Studio("tvParamount", StudioBrand.PARAMOUNT, state.tvParamount))
                if (filter == null || filter == "HULU")      if (state.tvHulu.isNotEmpty())      add(RowDef.Studio("tvHulu",      StudioBrand.HULU,      state.tvHulu))
                if (filter == null || filter == "NETFLIX")   if (state.tvNetflix.isNotEmpty())   add(RowDef.Studio("tvNetflix",   StudioBrand.NETFLIX,   state.tvNetflix))
                if (filter == null || filter == "APPLE_TV")  if (state.tvAppleTV.isNotEmpty())   add(RowDef.Studio("tvAppleTV",   StudioBrand.APPLE_TV,  state.tvAppleTV))
                if (filter == null || filter == "DISNEY")    if (state.tvDisney.isNotEmpty())    add(RowDef.Studio("tvDisney",    StudioBrand.DISNEY,    state.tvDisney))
                if (filter == null) {
                    if (state.tvTrending.isNotEmpty())  add(RowDef.Regular("tvTrending",  "Trending Shows",    state.tvTrending))
                    if (state.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", "On The Air",        state.tvPremieres))
                    if (state.tvDrama.isNotEmpty())     add(RowDef.Regular("tvDrama",     "Drama",             state.tvDrama))
                    if (state.tvCrime.isNotEmpty())     add(RowDef.Regular("tvCrime",     "Crime & Thriller",  state.tvCrime))
                    if (state.tvScifi.isNotEmpty())     add(RowDef.Regular("tvScifi",     "Sci-Fi & Fantasy",  state.tvScifi))
                    if (state.tvTopRated.isNotEmpty())  add(RowDef.Regular("tvTopRated",  "Top Rated Shows",   state.tvTopRated))
                }
            }
            "Fuzer" -> buildList {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val fuzerItems = state::class.java.getMethod("getFuzerItems").invoke(state) as? List<Movie> ?: emptyList()
                    if (fuzerItems.isNotEmpty()) {
                        val kids       = fuzerItems.filter { it.title.contains("מדובב") }
                        val kidsMovies = kids.filter { it.mediaType == "movie" }
                        val kidsTv     = kids.filter { it.mediaType == "tv" }
                        val nonKids    = fuzerItems.filter { !it.title.contains("מדובב") }
                        val movies     = nonKids.filter { it.mediaType == "movie" }
                        val tv         = nonKids.filter { it.mediaType == "tv" }
                        val israeli    = nonKids.filter {
                            it.title.contains("ישראל") || it.title.contains("עברית") || it.overview.contains("ישראל")
                        }.ifEmpty { nonKids.take(8) }

                        if (movies.isNotEmpty())     add(RowDef.Regular("fuzer_m",  "🔥 סרטים חדשים בטראקר",          movies.take(15)))
                        if (tv.isNotEmpty())         add(RowDef.Regular("fuzer_tv", "📺 סדרות ופרקים חדשים",           tv.take(15)))
                        if (israeli.isNotEmpty())    add(RowDef.Regular("fuzer_il", "⭐ הקולנוע והטלוויזיה הישראלית", israeli))
                        if (kidsMovies.isNotEmpty()) add(RowDef.Regular("fuzer_km", "🧸 סרטים מדובבים לילדים",        kidsMovies))
                        if (kidsTv.isNotEmpty())     add(RowDef.Regular("fuzer_kt", "🎈 סדרות מדובבות לילדים",        kidsTv))
                    }
                } catch (_: Exception) {}
            }
            else -> emptyList()
        }
    }

    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }

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
            delay(140L)
            val m = rows.getOrNull(ri)?.let { r ->
                when (r) {
                    is RowDef.Regular      -> r.movies
                    is RowDef.Studio       -> r.movies
                    is RowDef.StudioRibbon -> emptyList()
                }
            }?.firstOrNull()
            // ✅ תיקון Davey #3: עדכן heroMovie רק אם id השתנה
            if (m != null && m.id != focusState.heroMovie?.id) {
                focusState.heroMovie = m
            }
        }
    }

    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) {
            focusState.heroMovie = when (val r = rows[0]) {
                is RowDef.Regular      -> r.movies.firstOrNull()
                is RowDef.Studio       -> r.movies.firstOrNull()
                is RowDef.StudioRibbon -> null
            }
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

        ContentLayer(
            rows                = rows,
            focusState          = focusState,
            activeTab           = state.selectedTab,
            activeFilter        = state.selectedStudioFilter,
            panelH              = panelH,
            rowHeightFor        = { i -> rowHeightFor(i) },
            onMovieClick        = onMovieClick,
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
                try { viewModel::class.java.getMethod("loadFuzerContent").invoke(viewModel) } catch (_: Exception) {}
            },
            onWatchlist = { navController.navigate("watchlist") },
            onSettings  = { navController.navigate("settings") }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  BACKDROP
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val dns = LocalDensity.current
    val (bwPx, bhPx) = remember(cfg, dns) {
        with(dns) {
            cfg.screenWidthDp.dp.roundToPx().coerceIn(1, 1280) to
                    cfg.screenHeightDp.dp.roundToPx().coerceIn(1, 720)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(BG))
        Crossfade(
            targetState   = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl,
            animationSpec = tween(400, easing = FastOutSlowInEasing), label = "bd"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url) {
                        ImageRequest.Builder(ctx).data(url).size(bwPx, bhPx)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
        Box(Modifier.fillMaxSize().background(heroScrimLeft))
        Box(Modifier.fillMaxSize().background(heroScrimTop))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  HERO OVERLAY
// ═══════════════════════════════════════════════════════════════════
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
                        Text(if (m.mediaType == "tv") "TV Series" else "Movie", color = DIM, fontSize = 13.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF5C518))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment   = Alignment.CenterVertically,
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

// ═══════════════════════════════════════════════════════════════════
//  CONTENT LAYER
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun ContentLayer(
    rows: List<RowDef>, focusState: HomeFocusState, activeTab: String,
    activeFilter: String?,
    panelH: Dp, rowHeightFor: (Int) -> Dp,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onStudioFilterClick: (String?) -> Unit,
    onLoadMore: (String) -> Unit,
    onSearch: () -> Unit,
    onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit
) {
    val firstNavFR   = remember { FocusRequester() }
    val firstCardFRs = remember(rows.size) { List(rows.size) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }

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
        ) {
            RowsPanel(
                rows                = rows,
                focusState          = focusState,
                rowFRs              = firstCardFRs,
                panelH              = panelH,
                rowHeightFor        = rowHeightFor,
                activeFilter        = activeFilter,
                onStudioFilterClick = onStudioFilterClick,
                onLoadMore          = onLoadMore,
                onItemFocus         = onHeroUpdate,
                onItemClick         = onMovieClick
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  TWO-ROW NAV BAR
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TwoRowNavBar(
    activeTab: String, firstNavFR: FocusRequester,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit,
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

    val density      = LocalDensity.current
    val tabPositions = remember { mutableStateMapOf<String, Offset>() }
    val tabWidths    = remember { mutableStateMapOf<String, Dp>() }

    val targetX     = with(density) { (tabPositions[activeTab]?.x ?: 0f).toDp() }
    val targetWidth = tabWidths[activeTab] ?: 0.dp

    val animatedX     by animateDpAsState(targetValue = targetX,     animationSpec = tween(350, easing = FastOutSlowInEasing), label = "pillX")
    val animatedWidth by animateDpAsState(targetValue = targetWidth, animationSpec = tween(350, easing = FastOutSlowInEasing), label = "pillW")

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
            contentAlignment = Alignment.CenterStart
        ) {
            if (animatedWidth > 0.dp) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.offset(x = animatedX).width(animatedWidth).height(NAV_PILL_H),
                    color    = WHITE, shape = RoundedCornerShape(50)
                ) {}
            }
            Row(
                modifier              = Modifier.fillMaxSize(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavPill("Home",      Icons.Default.Home,        activeTab == "ראשי",   firstNavFR, onHomeTab)   { o, w -> tabPositions["ראשי"]      = o; tabWidths["ראשי"]      = w }
                NavPill("Movies",    Icons.Default.Movie,       activeTab == "סרטים",  null,       onMoviesTab)  { o, w -> tabPositions["סרטים"]     = o; tabWidths["סרטים"]     = w }
                NavPill("TV Shows",  Icons.Default.Tv,          activeTab == "סדרות",  null,       onSeriesTab)  { o, w -> tabPositions["סדרות"]     = o; tabWidths["סדרות"]     = w }
                NavPill("Fuzer",     Icons.Default.LocalMovies, activeTab == "Fuzer",  null,       onFuzer)      { o, w -> tabPositions["Fuzer"]     = o; tabWidths["Fuzer"]     = w }
                NavPill("Watchlist", Icons.Default.Bookmark,    false,                 null,       onWatchlist)  { o, w -> tabPositions["Watchlist"] = o; tabWidths["Watchlist"] = w }
                NavPill("Settings",  Icons.Default.Settings,    false,                 null,       onSettings)   { o, w -> tabPositions["Settings"]  = o; tabWidths["Settings"]  = w }
                Spacer(Modifier.weight(1f))
                SearchBarButton(onClick = onSearch)
            }
        }
    }
}

// ── Logo ─────────────────────────────────────────────────────────────────────
@Composable
private fun LuminaLogo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(RED), Alignment.Center) {
            Text("L", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text("LUMINA",  color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 11.sp)
            Text("STREAMS", color = RED,   fontSize = 6.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 7.sp)
        }
    }
}

// ── Search button ─────────────────────────────────────────────────────────────
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
            border        = Border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x70FFFFFF)), shape = RoundedCornerShape(50))
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
            Text("Search movies, shows...", fontSize = 12.sp, letterSpacing = 0.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  NAV PILL
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun NavPill(
    label: String, icon: ImageVector, isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onTabPositioned: (Offset, Dp) -> Unit
) {
    val density      = LocalDensity.current
    val contentColor = if (isSelected) Color(0xFF0C0C0C) else WHITE
    var measured     by remember { mutableStateOf(false) }

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
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier
            .height(NAV_PILL_H)
            .wrapContentWidth()
            .onGloballyPositioned { coords ->
                if (!measured) {
                    measured = true
                    onTabPositioned(coords.positionInParent(), with(density) { coords.size.width.toDp() })
                }
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

// ═══════════════════════════════════════════════════════════════════
//  ROWS PANEL
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun RowsPanel(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>,
    panelH: Dp, rowHeightFor: (Int) -> Dp,
    activeFilter: String?,
    onStudioFilterClick: (String?) -> Unit,
    onLoadMore: (String) -> Unit,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (rows.isEmpty()) return
    val curRow = focusState.currentRowIndex.coerceIn(0, rows.size - 1)

    val targetYOffset: Dp = remember(curRow, rows.size) {
        var acc = 0.dp
        for (i in 0 until curRow) acc += rowHeightFor(i)
        -acc
    }
    val animatedY by animateDpAsState(
        targetValue   = targetYOffset,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label         = "rowsSlide"
    )

    Box(Modifier.fillMaxWidth().height(panelH).clipToBounds()) {
        Box(Modifier.fillMaxWidth().offset(y = animatedY)) {
            var yAccum = 0.dp
            rows.forEachIndexed { i, rowDef ->
                val rh       = rowHeightFor(i)
                val isLand   = (i == 0)
                val isActive = !focusState.isNavFocused && i == curRow
                val yOffset  = yAccum

                key(rowDef.id) {
                    val animatedAlpha by animateFloatAsState(
                        targetValue   = if (i == curRow) 1f else 0.22f,
                        animationSpec = tween(
                            durationMillis = if (i == curRow) 180 else 100,
                            easing         = FastOutSlowInEasing
                        ),
                        label = "a$i"
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rh)
                            .offset(y = yOffset)
                            .alpha(animatedAlpha)
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
                                is RowDef.Regular -> LandscapeRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) })
                                is RowDef.Studio  -> LandscapeStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) })
                                else -> {}
                            }
                        } else {
                            when (rowDef) {
                                is RowDef.Regular -> PortraitRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) })
                                is RowDef.Studio  -> PortraitStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick, onLoadMore = { onLoadMore(rowDef.id) })
                                else -> {}
                            }
                        }
                    }
                }

                yAccum += rh
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  LANDSCAPE ROW
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LandscapeRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    val isAtEnd by remember {
        derivedStateOf {
            val li = rowState.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
        }
    }
    LaunchedEffect(isAtEnd) { if (isAtEnd) onLoadMore() }

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { i, m -> "${m.id}_$i" }) { i, movie ->
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

    val isAtEnd by remember {
        derivedStateOf {
            val li = rowState.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
        }
    }
    LaunchedEffect(isAtEnd) { if (isAtEnd) onLoadMore() }

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { i, m -> "${m.id}_$i" }) { i, movie ->
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

// ═══════════════════════════════════════════════════════════════════
//  PORTRAIT ROW
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PortraitRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }

    val isAtEnd by remember {
        derivedStateOf {
            val li = rowState.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
        }
    }
    LaunchedEffect(isAtEnd) { if (isAtEnd) onLoadMore() }

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { i, m -> "${m.id}_$i" }) { i, movie ->
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

    val isAtEnd by remember {
        derivedStateOf {
            val li = rowState.layoutInfo
            li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4
        }
    }
    LaunchedEffect(isAtEnd) { if (isAtEnd) onLoadMore() }

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { i, m -> "${m.id}_$i" }) { i, movie ->
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

private fun studioLabel(b: StudioBrand) = when (b) {
    StudioBrand.NETFLIX   -> "Netflix Originals"
    StudioBrand.APPLE_TV  -> "Apple TV+ Originals"
    StudioBrand.DISNEY    -> "Disney+ Exclusives"
    StudioBrand.HBO       -> "HBO Max Exclusives"
    StudioBrand.AMAZON    -> "Amazon Originals"
    StudioBrand.PARAMOUNT -> "Paramount+ Exclusives"
    StudioBrand.HULU      -> "Hulu Originals"
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

// ── Studio badge ──────────────────────────────────────────────────────────────
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

// ═══════════════════════════════════════════════════════════════════
//  LANDSCAPE CARD
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LandscapeCard(
    movie: Movie, modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx       = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }

    val zoom by animateFloatAsState(
        targetValue   = if (isFocused.value) 1.06f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "lzoom"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFocused.value) 0.18f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "loverlay"
    )

    val url = movie.backdropUrl.ifBlank { movie.posterUrl }
    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx).data(url)
            .size(LAND_W_PX, LAND_H_PX)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(300)
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
            border = ClickableSurfaceDefaults.border(
                border        = Border.None,
                focusedBorder = Border.None
            ),
            glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
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
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF2E2E2E), CARD_BG))),
                    Alignment.Center
                ) {
                    Text(movie.title, color = WHITE.copy(0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                }
            }

            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color(0xBB000000)),
                            startY = 0f, endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            if (isFocused.value) {
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
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(if (isDubbed) "🎤 מדובב" else "💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(movie.title, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (movie.mediaType == "tv") "TV Show" else "Movie", color = DIM2, fontSize = 11.sp)
            }

            if (movie.rating > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  POSTER CARD
// ═══════════════════════════════════════════════════════════════════
@Composable
fun PosterCard(
    movie: Movie, modifier: Modifier = Modifier,
    cardW: Dp = PORT_W, cardH: Dp = PORT_H,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx       = LocalContext.current
    val isFocused = remember { mutableStateOf(false) }

    val zoom by animateFloatAsState(
        targetValue   = if (isFocused.value) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "pzoom"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFocused.value) 0.15f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "poverlay"
    )

    val url = movie.posterUrl.ifBlank { movie.backdropUrl }
    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx).data(url)
            .size(PORT_W_PX, PORT_H_PX)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(300)
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
                border = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border.None
                ),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
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
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), CARD_BG))),
                        Alignment.Center
                    ) {
                        Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                    }
                }

                if (isFocused.value) {
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
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(if (isDubbed) "🎤 מדובב" else "💎 FUZER", color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                }

                if (movie.rating > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xBB000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            movie.title,
            color      = if (isFocused.value) WHITE else DIM,
            fontSize   = 11.sp,
            fontWeight = if (isFocused.value) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.width(cardW)
        )
        Text(if (movie.mediaType == "tv") "TV Show" else "Movie", color = DIM3, fontSize = 10.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════
//  STUDIO RIBBON
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun StudioRibbonRow(
    isActive: Boolean, cardFR: FocusRequester?,
    activeFilter: String?, onStudioFilterClick: (String?) -> Unit
) {
    val brands   = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    val rowState = rememberLazyListState()

    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            "Browse by Studio",
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
            itemsIndexed(brands) { i, brand ->
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

    Surface(
        onClick = onClick,
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors  = ClickableSurfaceDefaults.colors(containerColor = containerCol, focusedContainerColor = WHITE),
        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border  = ClickableSurfaceDefaults.border(
            border        = Border(androidx.compose.foundation.BorderStroke(1.5.dp, borderCol), shape = RoundedCornerShape(12.dp)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, WHITE), shape = RoundedCornerShape(12.dp))
        ),
        modifier = modifier.width(130.dp).height(65.dp).onFocusChanged { focusState.value = it.isFocused }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            StudioBadge(brand = brand, isActive = true, isLarge = true)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  LOADING SKELETON
// ═══════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF282828), Color(0xFF111111)),
        start = Offset(p * 2400f - 1200f, 0f), end = Offset(p * 2400f, 600f)
    )
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

// ═══════════════════════════════════════════════════════════════════
//  ERROR
// ═══════════════════════════════════════════════════════════════════
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
                    Text("Retry", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
