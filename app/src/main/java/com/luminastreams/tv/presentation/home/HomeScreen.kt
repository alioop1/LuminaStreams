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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ══════════════════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════════════════
private val BG          = Color(0xFF080808)
private val RED         = Color(0xFFE50914)
private val RED2        = Color(0xFFB20710)
private val WHITE       = Color(0xFFFFFFFF)
private val DIM         = Color(0xCCFFFFFF)
private val DIM2        = Color(0x99FFFFFF)
private val DIM3        = Color(0x33FFFFFF)
private val GOLD        = Color(0xFFFFD700)
private val CARD_BG     = Color(0xFF181818)
private val NETFLIX_RED = Color(0xFFE50914)
private val APPLE_BG    = Color(0xFF1C1C1E)
private val DISNEY_BLUE = Color(0xFF113CCF)

// ── Layout constants ─────────────────────────────────────────────────────────
//  NAV: search row 56dp + pills row 48dp = 104dp total
private val NAV_SEARCH_H = 56.dp
private val NAV_PILLS_H  = 48.dp
private val NAV_H        = NAV_SEARCH_H + NAV_PILLS_H   // 104 dp

//  ROW: label 22dp + cards 195dp + label-bottom-pad 8dp + card vertical-pad 8dp = 233dp
//  We give 240dp per row item so labels never clip.
private val ROW_ITEM_H   = 240.dp

//  CARD sizes
private val CARD_W = 130.dp
private val CARD_H = 190.dp   // slightly shorter so label fits inside ROW_ITEM_H

// ── Scrims ────────────────────────────────────────────────────────────────────
private val leftScrim = Brush.horizontalGradient(colorStops = arrayOf(
    0.00f to Color(0xE0080808), 0.22f to Color(0xC0080808),
    0.40f to Color(0x80080808), 0.58f to Color(0x26080808),
    0.72f to Color.Transparent))

// topScrim — opaque for the full nav height, fades to transparent by ~38%
private val topScrim = Brush.verticalGradient(colorStops = arrayOf(
    0.00f to Color(0xF5050505),
    0.15f to Color(0xF0050505),
    0.26f to Color(0xCC050505),
    0.38f to Color(0x66050505),
    0.52f to Color.Transparent))

private val bottomScrim = Brush.verticalGradient(colorStops = arrayOf(
    0.00f to Color.Transparent,
    0.42f to Color.Transparent,
    0.62f to Color(0x88080808),
    0.80f to Color(0xCC080808),
    1.00f to Color(0xF8080808)))

// ══════════════════════════════════════════════════════════════════════════════
//  ROW TYPES
// ══════════════════════════════════════════════════════════════════════════════
enum class StudioBrand { NETFLIX, APPLE_TV, DISNEY }
sealed class RowDef {
    data class Regular(val title: String,      val movies: List<Movie>) : RowDef()
    data class Studio (val brand: StudioBrand, val movies: List<Movie>) : RowDef()
}

// ══════════════════════════════════════════════════════════════════════════════
//  FOCUS STATE
// ══════════════════════════════════════════════════════════════════════════════
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

// ══════════════════════════════════════════════════════════════════════════════
//  HOME SCREEN
// ══════════════════════════════════════════════════════════════════════════════
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
            if (state.movieTopRated.isNotEmpty())  add(RowDef.Regular("Top Rated of All Time", state.movieTopRated))
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
    val config     = LocalConfiguration.current

    // rowsViewH = height of the sliding rows panel at the bottom.
    // Must be tall enough to fully show cards + labels without clipping.
    // ROW_ITEM_H (240) + a bit of breathing room (32dp) = 272dp minimum.
    val rowsViewH = remember(config.screenHeightDp) {
        (config.screenHeightDp * 0.42f).dp.coerceIn(272.dp, 370.dp)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex }.distinctUntilChanged().collectLatest { ri ->
            if (focusState.isNavFocused) return@collectLatest
            delay(180L)
            val movies = when (val r = rows.getOrNull(ri)) {
                is RowDef.Regular -> r.movies; is RowDef.Studio -> r.movies; null -> emptyList()
            }
            if (focusState.heroMovie == null) focusState.heroMovie = movies.firstOrNull()
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
        BackdropLayer(focusState.heroMovie)
        HeroOverlay(focusState.heroMovie, rowsViewH)
        ContentLayer(
            rows         = rows,
            focusState   = focusState,
            activeTab    = state.selectedTab,
            rowsViewH    = rowsViewH,
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

// ══════════════════════════════════════════════════════════════════════════════
//  BACKDROP
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val dns = LocalDensity.current
    val (bwPx, bhPx) = remember(cfg, dns) {
        with(dns) {
            cfg.screenWidthDp.dp.roundToPx().coerceIn(1, 3840) to
            cfg.screenHeightDp.dp.roundToPx().coerceIn(1, 2160)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(BG))
        Crossfade(
            targetState   = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl,
            animationSpec = tween(700, easing = FastOutSlowInEasing), label = "bd"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url, bwPx, bhPx) {
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
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(leftScrim); drawRect(topScrim); drawRect(bottomScrim)
        })
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  HERO OVERLAY
//  Sits between the nav (top) and the rows panel (bottom).
//  bottom padding = rowsViewH + 20dp gap
//  top    padding = NAV_H     + 12dp gap  → text never slides under the nav
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroOverlay(hero: Movie?, rowsViewH: Dp) {
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        hero?.let { m ->
            key(m.id) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start  = 52.dp,
                            end    = 420.dp,
                            top    = NAV_H + 12.dp,   // never overlap the nav
                            bottom = rowsViewH + 20.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // title — capped at 38sp so long titles fit on 2 lines cleanly
                    Text(
                        text       = m.title,
                        color      = WHITE,
                        fontSize   = 38.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 44.sp,
                        letterSpacing = 0.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    // meta row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.year > 0) {
                            Text(m.year.toString(), color = DIM, fontSize = 13.sp)
                            Text("  ·  ", color = DIM3, fontSize = 13.sp)
                        }
                        if (m.genre.isNotBlank()) {
                            Text(m.genre, color = DIM, fontSize = 13.sp)
                            Text("  ·  ", color = DIM3, fontSize = 13.sp)
                        }
                        if (m.rating > 0f) {
                            Row(
                                Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(GOLD)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("IMDb", color = Color(0xFF1A1A1A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Text("%.1f".format(m.rating), color = Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    // overview
                    Text(
                        text     = m.overview,
                        color    = DIM2,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 560.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CONTENT LAYER
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ContentLayer(
    rows: List<RowDef>, focusState: HomeFocusState, activeTab: String, rowsViewH: Dp,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onSearch: () -> Unit, onMoviesTab: () -> Unit, onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit, onSettings: () -> Unit
) {
    val firstNavFR   = remember { FocusRequester() }
    val firstCardFRs = remember(rows.size) { List(rows.size) { FocusRequester() } }

    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(rows.size) {
        if (!initialFocusDone && rows.isNotEmpty()) {
            delay(350)
            initialFocusDone = true
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
                        focusState.currentRowIndex--
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                    } else focusState.isNavFocused = true
                    true
                }
                Key.DirectionDown -> {
                    if (focusState.isNavFocused) {
                        focusState.isNavFocused = false; true
                    } else if (focusState.currentRowIndex < rows.size - 1) {
                        focusState.currentRowIndex++
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime(); true
                    } else false
                }
                Key.Back, Key.Escape -> {
                    if (focusState.isNavFocused) { focusState.isNavFocused = false; true } else false
                }
                else -> false
            }
        }
    ) {
        TopNavBar(
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
        RowsLayer(
            rows      = rows,
            focusState = focusState,
            rowFRs    = firstCardFRs,
            rowsViewH = rowsViewH,
            onItemFocus = onHeroUpdate,
            onItemClick = onMovieClick
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  TOP NAV BAR
//  Row 1 (NAV_SEARCH_H = 56dp): Logo | SearchBar | Clock
//  Row 2 (NAV_PILLS_H  = 48dp): Home | TV Shows | Watchlist | Settings
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun TopNavBar(
    activeTab:   String,
    firstNavFR:  FocusRequester,
    onSearch:    () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings:  () -> Unit,
    onNavExit:   () -> Unit,
    modifier:    Modifier = Modifier
) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = java.util.Calendar.getInstance()
            time = "%02d:%02d".format(
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE)
            )
            delay(30_000)
        }
    }

    Column(modifier = modifier) {
        // ── Row 1: Logo + Search + Clock ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_SEARCH_H)
                .padding(horizontal = 48.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LuminaLogo()
            SearchBarButton(Modifier.weight(1f), onSearch)
            Text(time, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        // ── Row 2: Pills ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_PILLS_H)
                .padding(horizontal = 48.dp)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                        onNavExit(); true
                    } else false
                },
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavPill("Home",      Icons.Default.Home,     activeTab == "סרטים", firstNavFR) { onMoviesTab() }
            NavPill("TV Shows",  Icons.Default.LiveTv,   activeTab == "סדרות")            { onSeriesTab() }
            NavPill("Watchlist", Icons.Default.Bookmark, false)                            { onWatchlist() }
            NavPill("Settings",  Icons.Default.Settings, false)                            { onSettings() }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SEARCH BAR BUTTON
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SearchBarButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color(0x33FFFFFF),
            focusedContainerColor = Color(0x55FFFFFF),
            pressedContainerColor = Color(0x44FFFFFF),
            contentColor          = DIM2,
            focusedContentColor   = WHITE
        ),
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            Border(androidx.compose.foundation.BorderStroke(1.dp,   Color(0x22FFFFFF)), shape = RoundedCornerShape(50)),
            Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x88FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(15.dp))
            Text("Search movies, shows...", fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  LUMINA LOGO
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun LuminaLogo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(RED), Alignment.Center) {
            Text("L", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text("LUMINA",  color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 13.sp)
            Text("STREAMS", color = RED,   fontSize = 7.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 8.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  NAV PILL
//
//  ROOT FIX for pill text clipping:
//    • The Surface modifier does NOT use wrapContentWidth — TV Surface on
//      some API levels ignores it and stretches to fill the parent Row.
//    • Instead we use Modifier.widthIn(min=0.dp) and let the INNER Row with
//      IntrinsicSize.Min dictate the width via the content measurement pass.
//    • focusedScale = 1f so the focused pill does NOT expand and push its
//      siblings off-screen.
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun NavPill(
    label: String,
    icon:  ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    // Measure the intrinsic width of content (icon + gap + text + h-padding)
    // before composing the Surface so we can constrain it precisely.
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (isSelected) WHITE            else Color(0x55FFFFFF),
            focusedContainerColor = if (isSelected) WHITE            else Color(0x88FFFFFF),
            pressedContainerColor = Color(0x33FFFFFF),
            contentColor          = if (isSelected) Color(0xFF0D0D0D) else WHITE,
            focusedContentColor   = if (isSelected) Color(0xFF0D0D0D) else WHITE
        ),
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(Border.None, Border.None),
        glow   = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier
            .height(36.dp)
            // intrinsicSize trick: force Surface to be exactly as wide as its content
            .width(IntrinsicSize.Min)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text          = label,
                fontSize      = 13.sp,
                fontWeight    = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
                softWrap      = false,
                maxLines      = 1
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ROWS LAYER
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RowsLayer(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>,
    rowsViewH: Dp, onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    val curRow = focusState.currentRowIndex
    var isFast by remember { mutableStateOf(false) }
    LaunchedEffect(focusState.lastNavEventTime) {
        val snap = focusState.lastNavEventTime; isFast = true
        delay(600L); if (focusState.lastNavEventTime == snap) isFast = false
    }
    val listState = rememberLazyListState()
    LaunchedEffect(curRow) {
        val t = curRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        if (isFast) listState.scrollToItem(t) else listState.animateScrollToItem(t)
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(rowsViewH)
                // clipToBounds so rows above this panel are invisible
                .clipToBounds()
        ) {
            LazyColumn(
                state          = listState,
                // bottom padding = rowsViewH so the last row scrolls fully into view
                contentPadding = PaddingValues(bottom = rowsViewH),
                modifier       = Modifier.fillMaxSize()
            ) {
                itemsIndexed(rows, key = { i, r ->
                    when (r) {
                        is RowDef.Regular -> "R_${r.title}_$i"
                        is RowDef.Studio  -> "S_${r.brand.name}_$i"
                    }
                }) { index, rowDef ->
                    val alpha by animateFloatAsState(
                        targetValue   = if (index <= curRow) 1f else 0.10f,
                        animationSpec = tween(250),
                        label         = "row_alpha"
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_ITEM_H)
                            .graphicsLayer { this.alpha = alpha }
                            .zIndex(if (!focusState.isNavFocused && index == curRow) 10f else index.toFloat())
                    ) {
                        val isActive = !focusState.isNavFocused && index == curRow
                        val cardFR   = rowFRs.getOrNull(index)
                        val onFocus: (Movie) -> Unit = { m ->
                            focusState.currentRowIndex  = index
                            focusState.isNavFocused     = false
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            onItemFocus(m)
                        }
                        when (rowDef) {
                            is RowDef.Regular -> ContentRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                            is RowDef.Studio  -> StudioRow(rowDef.brand,  rowDef.movies, isActive, cardFR, onFocus, onItemClick)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CONTENT ROW
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ContentRow(
    title: String, movies: List<Movie>, isActiveRow: Boolean,
    firstCardFR: FocusRequester?,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val extMovies = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val startIdx  = if (movies.size >= 2) movies.size else 0
    val rowState  = rememberLazyListState(initialFirstVisibleItemIndex = startIdx.coerceAtLeast(0))
    LaunchedEffect(isActiveRow) {
        if (isActiveRow) { delay(80); runCatching { firstCardFR?.requestFocus() } }
    }
    Column {
        Text(
            text          = title,
            color         = WHITE.copy(alpha = if (isActiveRow) 1f else 0.40f),
            fontSize      = 13.sp,
            fontWeight    = if (isActiveRow) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp,
            modifier      = Modifier.padding(start = 48.dp, bottom = 8.dp, top = 4.dp)
        )
        LazyRow(
            state                 = rowState,
            // FIX: start padding = 48dp matches the label, so first card is flush left
            contentPadding        = PaddingValues(start = 48.dp, end = 48.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(extMovies, key = { idx, m -> "${m.id}_$idx" }) { idx, movie ->
                PosterCard(
                    movie     = movie,
                    cardW     = CARD_W,
                    cardH     = CARD_H,
                    modifier  = if (idx == startIdx && firstCardFR != null) Modifier.focusRequester(firstCardFR) else Modifier,
                    onFocused = { onItemFocus(movie) },
                    onClick   = { onItemClick(movie.id) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  STUDIO ROW
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun StudioRow(
    brand: StudioBrand, movies: List<Movie>, isActiveRow: Boolean,
    firstCardFR: FocusRequester?,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val extMovies = remember(movies) { if (movies.size < 2) movies else movies + movies + movies }
    val startIdx  = if (movies.size >= 2) movies.size else 0
    val rowState  = rememberLazyListState(initialFirstVisibleItemIndex = startIdx.coerceAtLeast(0))
    LaunchedEffect(isActiveRow) {
        if (isActiveRow) { delay(80); runCatching { firstCardFR?.requestFocus() } }
    }
    Column {
        Row(
            Modifier.padding(start = 48.dp, bottom = 8.dp, top = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioLogo(brand, isActiveRow)
            Text(
                text = when (brand) {
                    StudioBrand.NETFLIX  -> "Netflix Originals"
                    StudioBrand.APPLE_TV -> "Apple TV+ Originals"
                    StudioBrand.DISNEY   -> "Disney+ Exclusives"
                },
                color         = WHITE.copy(alpha = if (isActiveRow) 0.85f else 0.35f),
                fontSize      = 12.sp,
                fontWeight    = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
        LazyRow(
            state                 = rowState,
            contentPadding        = PaddingValues(start = 48.dp, end = 48.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(extMovies, key = { idx, m -> "${m.id}_$idx" }) { idx, movie ->
                PosterCard(
                    movie     = movie,
                    cardW     = CARD_W,
                    cardH     = CARD_H,
                    modifier  = if (idx == startIdx && firstCardFR != null) Modifier.focusRequester(firstCardFR) else Modifier,
                    onFocused = { onItemFocus(movie) },
                    onClick   = { onItemClick(movie.id) }
                )
            }
        }
    }
}

// ── Studio logo badges ────────────────────────────────────────────────────────
@Composable
private fun StudioLogo(brand: StudioBrand, isActive: Boolean) {
    val a = if (isActive) 1f else 0.4f
    when (brand) {
        StudioBrand.NETFLIX ->
            Box(Modifier.height(20.dp).width(26.dp).clip(RoundedCornerShape(3.dp)).background(NETFLIX_RED.copy(a)), Alignment.Center) {
                Text("N", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        StudioBrand.APPLE_TV ->
            Box(
                Modifier.height(20.dp).wrapContentWidth()
                    .clip(RoundedCornerShape(10.dp)).background(APPLE_BG.copy(a))
                    .border(0.5.dp, Color(0x88FFFFFF).copy(a), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp),
                Alignment.Center
            ) { Text("tv+", color = WHITE.copy(a), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic) }
        StudioBrand.DISNEY ->
            Box(
                Modifier.height(20.dp).wrapContentWidth()
                    .clip(RoundedCornerShape(3.dp)).background(DISNEY_BLUE.copy(a))
                    .padding(horizontal = 7.dp),
                Alignment.Center
            ) { Text("DISNEY+", color = WHITE.copy(a), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp) }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  POSTER CARD
//  cardH = CARD_H (190dp) — fits inside ROW_ITEM_H with label beneath it.
//  zoom spring on the Box, NOT on the Surface, so the surface border + glow
//  scales together without TV Surface adding its own scale on top.
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun PosterCard(
    movie:    Movie,
    cardW:    Dp = CARD_W,
    cardH:    Dp = CARD_H,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick:   () -> Unit
) {
    val ctx     = LocalContext.current
    val density = LocalDensity.current
    val wPx = remember(cardW, density) { with(density) { (cardW.roundToPx() * 2).coerceIn(1, 1920) } }
    val hPx = remember(cardH, density) { with(density) { (cardH.roundToPx() * 2).coerceIn(1, 1080) } }
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.07f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "zoom"
    )

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Box(
            Modifier
                .width(cardW)
                .height(cardH)
                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                .zIndex(if (focused) 8f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = CARD_BG,
                    focusedContainerColor = CARD_BG
                ),
                shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border(
                        androidx.compose.foundation.BorderStroke(2.5.dp, WHITE.copy(0.92f)),
                        shape = RoundedCornerShape(9.dp)
                    )
                ),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(WHITE.copy(0.20f), 16.dp)),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { fs -> focused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                AsyncImage(
                    model = remember(movie.posterUrl, movie.backdropUrl, wPx, hPx) {
                        ImageRequest.Builder(ctx)
                            .data(movie.posterUrl.ifBlank { movie.backdropUrl })
                            .size(wPx, hPx)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = movie.title,
                    contentScale       = ContentScale.FillBounds,
                    modifier           = Modifier.fillMaxSize()
                )
                // rating badge — top-right corner
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
        // ── card label ────────────────────────────────────────────────
        Spacer(Modifier.height(5.dp))
        Text(
            text       = movie.title,
            color      = if (focused) WHITE else DIM,
            fontSize   = 11.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.width(cardW)
        )
        Text(
            text     = if (movie.mediaType == "tv") "TV Show" else "Movie",
            color    = DIM3,
            fontSize = 10.sp
        )
    }
}

// Aliases kept for backward compat
@Composable fun ArvioCard(movie: Movie, cardW: Dp = CARD_W, cardH: Dp = CARD_H, modifier: Modifier = Modifier, isFocusedOverride: Boolean = false, onFocused: () -> Unit = {}, onClick: () -> Unit) = PosterCard(movie, cardW, cardH, modifier, onFocused, onClick)
@Composable fun NfCard(movie: Movie, modifier: Modifier = Modifier, isFocusedOverride: Boolean = false, onFocused: () -> Unit = {}, onClick: () -> Unit) = PosterCard(movie, modifier = modifier, onFocused = onFocused, onClick = onClick)

// ══════════════════════════════════════════════════════════════════════════════
//  LOADING SKELETON
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF101010), Color(0xFF292929), Color(0xFF101010)),
        start = Offset(p * 2400f - 1200f, 0f), end = Offset(p * 2400f, 600f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().padding(top = 16.dp, start = 48.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(50)).background(shimmer))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { Box(Modifier.width(100.dp).height(36.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.width(380.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(200.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            repeat(3) { Box(Modifier.fillMaxWidth(0.44f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmer)) }
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Box(Modifier.width(110.dp).height(11.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(8) { Box(Modifier.width(CARD_W).height(CARD_H).clip(RoundedCornerShape(9.dp)).background(shimmer)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ERROR
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
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

// Legacy stubs
@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(msg: String, onRetry: () -> Unit) = HomeError(msg, onRetry)
@Composable fun LuminaSidebar(open: Boolean, activeTab: String, onClose: () -> Unit, onMoviesClick: () -> Unit, onSeriesClick: () -> Unit, onSearchClick: () -> Unit) {}
@Composable fun NfSidebar(open: Boolean, activeId: String, sidebarFirstFR: FocusRequester, onFocusLanded: () -> Unit, onClose: () -> Unit, onNavSelect: (String) -> Unit) {}
