@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

// ═══════════════════════════════════════════════════════════════════
//  PALETTE
// ═══════════════════════════════════════════════════════════════════
private val BG          = Color(0xFF070707)
private val RED         = Color(0xFFE50914)
private val RED2        = Color(0xFFB20710)
private val WHITE       = Color(0xFFFFFFFF)
private val DIM         = Color(0xCCFFFFFF)
private val DIM2        = Color(0x99FFFFFF)
private val DIM3        = Color(0x33FFFFFF)
private val GOLD        = Color(0xFFFFD700)
private val CARD_BG     = Color(0xFF1C1C1C)
private val NETFLIX_RED = Color(0xFFE50914)
private val APPLE_BG    = Color(0xFF1C1C1E)
private val DISNEY_BLUE = Color(0xFF113CCF)
private val NAV_GLASS   = Color(0x18FFFFFF)
private val NAV_FOCUS   = Color(0x30FFFFFF)

// ═══════════════════════════════════════════════════════════════════
//  LAYOUT
//  NAV: two rows — search row (52dp) + pills row (44dp) = 96dp
//  LANDSCAPE card (first row): 16:9 → 280×158dp
//  PORTRAIT card (other rows): 2:3 → 148×222dp
//  ROW_LANDSCAPE_H: label(20) + pad(8) + card(158) + pad(8) = 194dp
//  ROW_PORTRAIT_H:  label(20) + pad(8) + card(222) + pad(8) = 258dp
// ═══════════════════════════════════════════════════════════════════
private val NAV_SEARCH_H   = 52.dp
private val NAV_PILLS_H    = 44.dp
private val NAV_H          = NAV_SEARCH_H + NAV_PILLS_H   // 96dp

private val LAND_W = 280.dp
private val LAND_H = 158.dp   // 16:9
private val PORT_W = 148.dp
private val PORT_H = 222.dp   // 2:3
private val ROW_LANDSCAPE_H = 194.dp
private val ROW_PORTRAIT_H  = 260.dp

// ═══════════════════════════════════════════════════════════════════
//  GRADIENT SCRIMS
// ═══════════════════════════════════════════════════════════════════
// Full-screen hero: keep the backdrop fully visible through the rows
// The rows sit ON TOP of the backdrop with a semi-transparent layer
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
// Bottom: rows sit here — a glass-dark overlay lets the backdrop bleed through
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
//  ROW TYPES
// ═══════════════════════════════════════════════════════════════════
enum class StudioBrand { NETFLIX, APPLE_TV, DISNEY }
sealed class RowDef {
    data class Regular(val title: String,      val movies: List<Movie>) : RowDef()
    data class Studio (val brand: StudioBrand, val movies: List<Movie>) : RowDef()
}

// ═══════════════════════════════════════════════════════════════════
//  FOCUS STATE
// ═══════════════════════════════════════════════════════════════════
@Stable
class HomeFocusState(initialRow: Int = 0) {
    var isNavFocused     by mutableStateOf(false)
    var currentRowIndex  by mutableIntStateOf(initialRow)
    var heroMovie        by mutableStateOf<Movie?>(null)
    var lastNavEventTime by mutableLongStateOf(0L)
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
    val rows: List<RowDef> = remember(state.selectedTab, state) {
        if (state.selectedTab == "סרטים") buildList {
            if (state.movieTrending.isNotEmpty())  add(RowDef.Regular("Trending Now",          state.movieTrending))
            if (state.movieNetflix.isNotEmpty())   add(RowDef.Studio(StudioBrand.NETFLIX,      state.movieNetflix))
            if (state.moviePremieres.isNotEmpty()) add(RowDef.Regular("New in Theaters",       state.moviePremieres))
            if (state.movieAppleTV.isNotEmpty())   add(RowDef.Studio(StudioBrand.APPLE_TV,     state.movieAppleTV))
            if (state.movieAction.isNotEmpty())    add(RowDef.Regular("Action & Adventure",    state.movieAction))
            if (state.movieDrama.isNotEmpty())     add(RowDef.Regular("Drama",                 state.movieDrama))
            if (state.movieDisney.isNotEmpty())    add(RowDef.Studio(StudioBrand.DISNEY,       state.movieDisney))
            if (state.movieScifi.isNotEmpty())     add(RowDef.Regular("Sci-Fi",                state.movieScifi))
            if (state.movieTopRated.isNotEmpty())  add(RowDef.Regular("Top Rated",             state.movieTopRated))
        } else buildList {
            if (state.tvTrending.isNotEmpty())  add(RowDef.Regular("Trending Shows",    state.tvTrending))
            if (state.tvNetflix.isNotEmpty())   add(RowDef.Studio(StudioBrand.NETFLIX,  state.tvNetflix))
            if (state.tvPremieres.isNotEmpty()) add(RowDef.Regular("On The Air",         state.tvPremieres))
            if (state.tvAppleTV.isNotEmpty())   add(RowDef.Studio(StudioBrand.APPLE_TV, state.tvAppleTV))
            if (state.tvDrama.isNotEmpty())     add(RowDef.Regular("Drama",              state.tvDrama))
            if (state.tvCrime.isNotEmpty())     add(RowDef.Regular("Crime & Thriller",   state.tvCrime))
            if (state.tvDisney.isNotEmpty())    add(RowDef.Studio(StudioBrand.DISNEY,   state.tvDisney))
            if (state.tvScifi.isNotEmpty())     add(RowDef.Regular("Sci-Fi & Fantasy",   state.tvScifi))
            if (state.tvTopRated.isNotEmpty())  add(RowDef.Regular("Top Rated Shows",    state.tvTopRated))
        }
    }

    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }

    // rowH per index — row 0 is landscape, rest portrait
    fun rowHeightFor(i: Int) = if (i == 0) ROW_LANDSCAPE_H else ROW_PORTRAIT_H

    // Panel height = show current row fully + tiny peek of next one
    val panelH = remember(rows.size) {
        if (rows.isEmpty()) ROW_PORTRAIT_H
        else ROW_PORTRAIT_H + 28.dp  // one full portrait row + breathing room
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(140L)
            val m = rows.getOrNull(ri)?.let { r -> when (r) { is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies } }?.firstOrNull()
            if (m != null) focusState.heroMovie = m
        }
    }
    LaunchedEffect(state.isLoading, rows.size) {
        if (!state.isLoading && rows.isNotEmpty() && focusState.heroMovie == null) {
            focusState.heroMovie = when (val r = rows[0]) {
                is RowDef.Regular -> r.movies.firstOrNull()
                is RowDef.Studio  -> r.movies.firstOrNull()
            }
        }
    }

    BackHandler(enabled = focusState.isNavFocused) { focusState.isNavFocused = false }

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.retry() }; return@Box }
        }

        // ── Full-screen backdrop ───────────────────────────────────────
        BackdropLayer(focusState.heroMovie)

        // ── Rows overlay (bottom half of screen gets darkened glass) ───
        Box(Modifier.fillMaxSize().drawBehind { drawRect(rowsOverlay) })

        // ── Hero text ─────────────────────────────────────────────────
        HeroOverlay(focusState.heroMovie, panelH)

        // ── Nav + rows ────────────────────────────────────────────────
        ContentLayer(
            rows        = rows,
            focusState  = focusState,
            activeTab   = state.selectedTab,
            panelH      = panelH,
            rowHeightFor = ::rowHeightFor,
            onMovieClick = onMovieClick,
            onHeroUpdate = { focusState.heroMovie = it },
            onSearch     = { navController.navigate("search") },
            onMoviesTab  = { viewModel.selectTab("סרטים") },
            onSeriesTab  = { viewModel.selectTab("סדרות") },
            onWatchlist  = { navController.navigate("watchlist") },
            onSettings   = { navController.navigate("settings") }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  BACKDROP — full-screen, always rendered behind everything
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val dns = LocalDensity.current
    val (bwPx, bhPx) = remember(cfg, dns) {
        with(dns) { cfg.screenWidthDp.dp.roundToPx().coerceIn(1, 3840) to cfg.screenHeightDp.dp.roundToPx().coerceIn(1, 2160) }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(BG))
        Crossfade(
            targetState   = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl,
            animationSpec = tween(800, easing = FastOutSlowInEasing), label = "bd"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url, bwPx, bhPx) {
                        ImageRequest.Builder(ctx).data(url).size(bwPx, bhPx)
                            .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }
        // left and top scrims only — bottom handled by rowsOverlay separately
        Box(Modifier.fillMaxSize().drawBehind { drawRect(heroScrimLeft); drawRect(heroScrimTop) })
    }
}

// ═══════════════════════════════════════════════════════════════════
//  HERO OVERLAY
//  Matches screenshot: title (serif-weight, large), date·genre·runtime·IMDb,
//  overview text. Sits above the rows panel, below the nav.
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun HeroOverlay(hero: Movie?, panelH: Dp) {
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        hero?.let { m ->
            key(m.id) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start  = 60.dp,
                            end    = 460.dp,
                            top    = NAV_H + 8.dp,
                            bottom = panelH + 28.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Title ──────────────────────────────────────────
                    val tsz = when {
                        m.title.length > 26 -> 36.sp
                        m.title.length > 16 -> 46.sp
                        else                -> 56.sp
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

                    // ── Meta row ───────────────────────────────────────
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (m.year > 0) {
                            Text(m.year.toString(), color = DIM, fontSize = 14.sp)
                            MetaDot()
                        }
                        if (m.genre.isNotBlank()) {
                            Text(m.genre, color = DIM, fontSize = 14.sp)
                            MetaDot()
                        }
                        Text(if (m.mediaType == "tv") "TV Series" else "Movie", color = DIM, fontSize = 14.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            // IMDb badge — matches screenshot style
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF5C518))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)
                                Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // ── Overview ───────────────────────────────────────
                    if (m.overview.isNotBlank()) {
                        Text(
                            text       = m.overview,
                            color      = DIM2,
                            fontSize   = 14.sp,
                            lineHeight = 22.sp,
                            maxLines   = 3,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.widthIn(max = 580.dp)
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
    panelH: Dp, rowHeightFor: (Int) -> Dp,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onSearch: () -> Unit, onMoviesTab: () -> Unit, onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit
) {
    val firstNavFR   = remember { FocusRequester() }
    val firstCardFRs = remember(rows.size) { List(rows.size) { FocusRequester() } }

    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(rows.size) {
        if (!initialFocusDone && rows.isNotEmpty()) {
            delay(380); initialFocusDone = true
            runCatching { firstCardFRs[0].requestFocus() }
        }
    }
    LaunchedEffect(focusState.isNavFocused) {
        if (focusState.isNavFocused) {
            delay(40); runCatching { firstNavFR.requestFocus() }
        } else if (initialFocusDone) {
            val idx = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
            runCatching { firstCardFRs.getOrNull(idx)?.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (ev.key) {
                Key.DirectionUp -> {
                    if (focusState.isNavFocused) return@onPreviewKeyEvent true
                    if (focusState.currentRowIndex > 0) {
                        focusState.currentRowIndex--; focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                    } else focusState.isNavFocused = true
                    true
                }
                Key.DirectionDown -> {
                    if (focusState.isNavFocused) { focusState.isNavFocused = false; true }
                    else if (focusState.currentRowIndex < rows.size - 1) {
                        focusState.currentRowIndex++; focusState.lastNavEventTime = SystemClock.elapsedRealtime(); true
                    } else false
                }
                Key.Back, Key.Escape -> { if (focusState.isNavFocused) { focusState.isNavFocused = false; true } else false }
                else -> false
            }
        }
    ) {
        // ── Two-row nav bar ──────────────────────────────────────────
        TwoRowNavBar(
            activeTab   = activeTab,
            firstNavFR  = firstNavFR,
            onSearch    = onSearch,
            onMoviesTab = onMoviesTab,
            onSeriesTab = onSeriesTab,
            onWatchlist = onWatchlist,
            onSettings  = onSettings,
            onNavExit   = { focusState.isNavFocused = false },
            modifier    = Modifier.fillMaxWidth().height(NAV_H).align(Alignment.TopStart).zIndex(10f)
        )

        // ── Rows panel — pinned to bottom of the Box ─────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(panelH)
                .align(Alignment.BottomStart)
        ) {
            RowsPanel(
                rows         = rows,
                focusState   = focusState,
                rowFRs       = firstCardFRs,
                panelH       = panelH,
                rowHeightFor = rowHeightFor,
                onItemFocus  = onHeroUpdate,
                onItemClick  = onMovieClick
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  TWO-ROW NAV BAR
//  Row 1 (52dp): [Logo] [spacer] [Clock]
//  Row 2 (44dp): [Home] [TV Shows] [Watchlist] [Settings] [spacer] [SearchBtn]
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TwoRowNavBar(
    activeTab: String, firstNavFR: FocusRequester,
    onSearch: () -> Unit, onMoviesTab: () -> Unit, onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit, onNavExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = java.util.Calendar.getInstance()
            time = "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
            delay(30_000)
        }
    }

    Column(modifier = modifier) {
        // ── Row 1: Logo + Clock ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(NAV_SEARCH_H).padding(horizontal = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LuminaLogo()
            Spacer(Modifier.weight(1f))
            Text(
                time,
                color      = WHITE,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // ── Row 2: Pills + Search ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_PILLS_H)
                .padding(horizontal = 52.dp)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { onNavExit(); true } else false
                },
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavPill("Home",      Icons.Default.Home,     activeTab == "סרטים", firstNavFR) { onMoviesTab() }
            NavPill("TV Shows",  Icons.Default.Tv,       activeTab == "סדרות")            { onSeriesTab() }
            NavPill("Watchlist", Icons.Default.Bookmark, false)                            { onWatchlist() }
            NavPill("Settings",  Icons.Default.Settings, false)                            { onSettings() }
            Spacer(Modifier.weight(1f))
            SearchBarButton(onClick = onSearch)
        }
    }
}

// ── Logo ────────────────────────────────────────────────────────────────────
@Composable
private fun LuminaLogo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(RED), Alignment.Center) {
            Text("L", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text("LUMINA",  color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp, lineHeight = 13.sp)
            Text("STREAMS", color = RED,   fontSize = 7.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.4.sp, lineHeight = 8.sp)
        }
    }
}

// ── Search button ───────────────────────────────────────────────────────────
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
            Border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF)), shape = RoundedCornerShape(50)),
            Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x70FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(34.dp).width(260.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(13.dp))
            Text("Search movies, shows...", fontSize = 12.sp, letterSpacing = 0.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  NAV PILL
//  Clean approach: Surface height fixed, content drives width via
//  wrapContentWidth(). No IntrinsicSize tricks needed.
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun NavPill(
    label: String, icon: ImageVector, isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Border visibility rules:
    //   • idle, not selected  → NO border
    //   • selected (active tab) → NO border (background fill is enough)
    //   • focused (D-pad on it) → semi-transparent white border
    val pillBorder = if (isFocused && !isSelected)
        ClickableSurfaceDefaults.border(
            border        = Border.None,
            focusedBorder = Border(
                androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66FFFFFF)),
                shape = RoundedCornerShape(50)
            )
        )
    else
        ClickableSurfaceDefaults.border(Border.None, Border.None)

    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (isSelected) WHITE       else Color.Transparent,
            focusedContainerColor = if (isSelected) WHITE       else NAV_FOCUS,
            pressedContainerColor = Color(0x20FFFFFF),
            contentColor          = if (isSelected) Color(0xFF0C0C0C) else WHITE,
            focusedContentColor   = if (isSelected) Color(0xFF0C0C0C) else WHITE
        ),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border   = pillBorder,
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier
            .height(34.dp)
            .wrapContentWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        // fillMaxSize + center alignment → text/icon always centered inside the border box
        Box(
            Modifier
                .fillMaxHeight()
                .wrapContentWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, null, Modifier.size(14.dp))
                Text(
                    label,
                    fontSize      = 13.sp,
                    fontWeight    = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.2.sp,
                    softWrap      = false,
                    maxLines      = 1
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ROWS PANEL
//  Pinned to the bottom of the screen.
//  Uses spring-animated Y offset to slide rows in/out smoothly.
//  ✅ No LazyColumn nesting — pure Box + offset.
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun RowsPanel(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>,
    panelH: Dp, rowHeightFor: (Int) -> Dp,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (rows.isEmpty()) return
    val curRow = focusState.currentRowIndex.coerceIn(0, rows.size - 1)

    // Compute cumulative Y offset to the current row
    val targetYOffset: Dp = remember(curRow, rows.size) {
        var acc = 0.dp
        for (i in 0 until curRow) acc += rowHeightFor(i)
        -acc
    }
    val animatedY by animateDpAsState(
        targetValue   = targetYOffset,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "rowsSlide"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(panelH)
            .clipToBounds()
    ) {
        // render rows from index 0 upwards — clipping handles visibility
        Box(Modifier.fillMaxWidth().offset(y = animatedY)) {
            var yAccum = 0.dp
            rows.forEachIndexed { i, rowDef ->
                val rh     = rowHeightFor(i)
                val isLand = (i == 0)
                val isActive = !focusState.isNavFocused && i == curRow
                val alpha by animateFloatAsState(
                    targetValue   = if (i == curRow) 1f else 0.22f,
                    animationSpec = tween(200),
                    label         = "alpha_$i"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rh)
                        .offset(y = yAccum)
                        .graphicsLayer { this.alpha = alpha }
                        .zIndex(if (i == curRow) 8f else i.toFloat())
                ) {
                    val cardFR   = rowFRs.getOrNull(i)
                    val onFocus: (Movie) -> Unit = { m ->
                        focusState.currentRowIndex  = i
                        focusState.isNavFocused     = false
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        onItemFocus(m)
                    }
                    if (isLand) {
                        // First row — landscape cards
                        when (rowDef) {
                            is RowDef.Regular -> LandscapeRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                            is RowDef.Studio  -> LandscapeStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                        }
                    } else {
                        when (rowDef) {
                            is RowDef.Regular -> PortraitRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                            is RowDef.Studio  -> PortraitStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                        }
                    }
                }
                yAccum += rh
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  LANDSCAPE ROW  (row index 0, 16:9 cards)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LandscapeRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val tripled  = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (isActive) { delay(60); runCatching { cardFR?.requestFocus() } } }

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(tripled, key = { i, m -> "${m.id}_$i" }) { i, movie ->
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
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val tripled  = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (isActive) { delay(60); runCatching { cardFR?.requestFocus() } } }

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(tripled, key = { i, m -> "${m.id}_$i" }) { i, movie ->
                LandscapeCard(movie = movie, modifier = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, onFocused = { onFocus(movie) }, onClick = { onClick(movie.id) })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  PORTRAIT ROW  (rows 1+, 2:3 cards)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PortraitRow(
    title: String, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val tripled  = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (isActive) { delay(60); runCatching { cardFR?.requestFocus() } } }

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(tripled, key = { i, m -> "${m.id}_$i" }) { i, movie ->
                PosterCard(movie = movie, cardW = PORT_W, cardH = PORT_H, modifier = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, onFocused = { onFocus(movie) }, onClick = { onClick(movie.id) })
            }
        }
    }
}

@Composable
private fun PortraitStudioRow(
    brand: StudioBrand, movies: List<Movie>, isActive: Boolean,
    cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val tripled  = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (isActive) { delay(60); runCatching { cardFR?.requestFocus() } } }

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(tripled, key = { i, m -> "${m.id}_$i" }) { i, movie ->
                PosterCard(movie = movie, cardW = PORT_W, cardH = PORT_H, modifier = if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, onFocused = { onFocus(movie) }, onClick = { onClick(movie.id) })
            }
        }
    }
}

private fun studioLabel(b: StudioBrand) = when (b) {
    StudioBrand.NETFLIX  -> "Netflix Originals"
    StudioBrand.APPLE_TV -> "Apple TV+ Originals"
    StudioBrand.DISNEY   -> "Disney+ Exclusives"
}

// ── Row label helper ─────────────────────────────────────────────────────────
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

// ── Studio badge chips ────────────────────────────────────────────────────────
@Composable
private fun StudioBadge(brand: StudioBrand, isActive: Boolean) {
    val a = if (isActive) 1f else 0.4f
    when (brand) {
        StudioBrand.NETFLIX ->
            Box(Modifier.height(22.dp).width(28.dp).clip(RoundedCornerShape(4.dp)).background(NETFLIX_RED.copy(a)), Alignment.Center) {
                Text("N", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        StudioBrand.APPLE_TV ->
            Box(Modifier.height(22.dp).wrapContentWidth().clip(RoundedCornerShape(11.dp)).background(APPLE_BG.copy(a)).border(0.5.dp, Color(0x88FFFFFF).copy(a), RoundedCornerShape(11.dp)).padding(horizontal = 9.dp), Alignment.Center) {
                Text("tv+", color = WHITE.copy(a), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            }
        StudioBrand.DISNEY ->
            Box(Modifier.height(22.dp).wrapContentWidth().clip(RoundedCornerShape(4.dp)).background(DISNEY_BLUE.copy(a)).padding(horizontal = 8.dp), Alignment.Center) {
                Text("DISNEY+", color = WHITE.copy(a), fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  LANDSCAPE CARD  (row 0 only — 280×158dp, 16:9)
//  Shows title + type overlay on focus. No label below.
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LandscapeCard(
    movie: Movie, modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx     = LocalContext.current
    val density = LocalDensity.current
    val wPx = remember(density) { with(density) { (LAND_W.roundToPx() * 2).coerceIn(1, 1920) } }
    val hPx = remember(density) { with(density) { (LAND_H.roundToPx() * 2).coerceIn(1, 1080) } }

    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "lzoom"
    )

    Box(
        modifier
            .width(LAND_W).height(LAND_H)
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
            .zIndex(if (focused) 8f else 0f)
    ) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border   = ClickableSurfaceDefaults.border(
                Border.None,
                Border(androidx.compose.foundation.BorderStroke(2.5.dp, WHITE.copy(0.92f)), shape = RoundedCornerShape(10.dp))
            ),
            glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(WHITE.copy(0.18f), 18.dp)),
            modifier = Modifier.fillMaxSize().onFocusChanged { fs -> focused = fs.isFocused; if (fs.isFocused) onFocused() }
        ) {
            val url = movie.backdropUrl.ifBlank { movie.posterUrl }
            if (url.isNotBlank()) {
                AsyncImage(
                    model = remember(url, wPx, hPx) {
                        ImageRequest.Builder(ctx).data(url).size(wPx, hPx)
                            .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2E2E2E), CARD_BG))), Alignment.Center) {
                    Text(movie.title, color = WHITE.copy(0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                }
            }

            // Bottom info overlay — always a subtle gradient + title
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color(0xBB000000)),
                        startY = 0f, endY = Float.POSITIVE_INFINITY
                    )
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(movie.title, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (movie.mediaType == "tv") "TV Show" else "Movie", color = DIM2, fontSize = 11.sp)
            }
            if (movie.rating > 0f) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  POSTER CARD  (portrait 2:3, rows 1+)
//  ✅ zoom on outer Box, Surface scale = 1f always
// ═══════════════════════════════════════════════════════════════════
@Composable
fun PosterCard(
    movie: Movie, cardW: Dp = PORT_W, cardH: Dp = PORT_H,
    modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit
) {
    val ctx     = LocalContext.current
    val density = LocalDensity.current
    val wPx = remember(cardW, density) { with(density) { (cardW.roundToPx() * 2).coerceIn(1, 1920) } }
    val hPx = remember(cardH, density) { with(density) { (cardH.roundToPx() * 2).coerceIn(1, 1080) } }

    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.08f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "pzoom"
    )

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Box(Modifier.width(cardW).height(cardH).graphicsLayer { scaleX = zoom; scaleY = zoom }.zIndex(if (focused) 9f else 0f)) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(Border.None, Border(androidx.compose.foundation.BorderStroke(2.5.dp, WHITE.copy(0.92f)), shape = RoundedCornerShape(10.dp))),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(WHITE.copy(0.18f), 18.dp)),
                modifier = Modifier.fillMaxSize().onFocusChanged { fs -> focused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                val url = movie.posterUrl.ifBlank { movie.backdropUrl }
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = remember(url, wPx, hPx) {
                            ImageRequest.Builder(ctx).data(url).size(wPx, hPx)
                                .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true).crossfade(false).build()
                        },
                        contentDescription = movie.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), CARD_BG))), Alignment.Center) {
                        Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                    }
                }
                if (movie.rating > 0f) {
                    Box(Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(movie.title, color = if (focused) WHITE else DIM, fontSize = 11.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(cardW))
        Text(if (movie.mediaType == "tv") "TV Show" else "Movie", color = DIM3, fontSize = 10.sp)
    }
}

// Aliases
@Composable fun ArvioCard(movie: Movie, cardW: Dp = PORT_W, cardH: Dp = PORT_H, modifier: Modifier = Modifier, isFocusedOverride: Boolean = false, onFocused: () -> Unit = {}, onClick: () -> Unit) = PosterCard(movie, cardW, cardH, modifier, onFocused, onClick)
@Composable fun NfCard(movie: Movie, modifier: Modifier = Modifier, isFocusedOverride: Boolean = false, onFocused: () -> Unit = {}, onClick: () -> Unit) = PosterCard(movie, modifier = modifier, onFocused = onFocused, onClick = onClick)

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
            // nav row 1
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Box(Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(56.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
            // nav row 2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { Box(Modifier.width(100.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(260.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer))
            }
            Spacer(Modifier.height(40.dp))
            // hero text
            Box(Modifier.width(380.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(240.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            repeat(2) { Box(Modifier.fillMaxWidth(0.42f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer)) }
            Spacer(Modifier.weight(1f))
            // landscape row
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
            Surface(onClick = onRetry, colors = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f), glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(RED.copy(0.55f), 20.dp)), modifier = Modifier.height(50.dp).width(160.dp)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Retry", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// Legacy stubs
@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(msg: String, onRetry: () -> Unit) = HomeError(msg, onRetry)
@Composable fun LuminaSidebar(open: Boolean, activeTab: String, onClose: () -> Unit, onMoviesClick: () -> Unit, onSeriesClick: () -> Unit, onSearchClick: () -> Unit) {}
@Composable fun NfSidebar(open: Boolean, activeId: String, sidebarFirstFR: FocusRequester, onFocusLanded: () -> Unit, onClose: () -> Unit, onNavSelect: (String) -> Unit) {}