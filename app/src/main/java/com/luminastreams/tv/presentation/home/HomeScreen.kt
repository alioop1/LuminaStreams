@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// ─── Palette ───────────────────────────────────────────────────────────────────
private val BG        = Color(0xFF080808)
private val RED       = Color(0xFFE50914)
private val RED2      = Color(0xFFB20710)
private val WHITE     = Color(0xFFFFFFFF)
private val DIM       = Color(0xCCFFFFFF)
private val DIM2      = Color(0x80FFFFFF)
private val DIM3      = Color(0x4DFFFFFF)
private val GOLD      = Color(0xFFFFD700)
private val PILL_SEL  = Color(0xFFFFFFFF)
private val PILL_UNS  = Color(0x14FFFFFF)
private val CARD_BG   = Color(0xFF181818)

private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}

// ─── Focus state ───────────────────────────────────────────────────────────────
@Stable
class HomeFocusState(
    initialRowIndex: Int = 0,
    initialItemIndex: Int = 0
) {
    var isNavFocused     by mutableStateOf(false)
    var currentRowIndex  by mutableIntStateOf(initialRowIndex)
    var currentItemIndex by mutableIntStateOf(initialItemIndex)
    var lastNavEventTime by mutableLongStateOf(0L)

    companion object {
        val Saver: Saver<HomeFocusState, List<Int>> = Saver(
            save    = { listOf(it.currentRowIndex, it.currentItemIndex) },
            restore = { HomeFocusState(it[0], it[1]) }
        )
    }
}

// ─── Scrims — premium cinema grade ────────────────────────────────────────────
// Left: strong on far-left so text is always readable, fades out gently
private val leftScrim = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xE6080808),
        0.12f to Color(0xCC080808),
        0.28f to Color(0x99080808),
        0.44f to Color(0x55080808),
        0.58f to Color(0x22080808),
        0.70f to Color.Transparent
    )
)
// Top: just enough to keep nav readable without killing the image
private val topScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xB3080808),
        0.08f to Color(0x66080808),
        0.18f to Color(0x11080808),
        0.28f to Color.Transparent
    )
)
// Bottom: fades image into rows section
private val bottomScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.52f to Color.Transparent,
        0.70f to Color(0x66080808),
        0.82f to Color(0xCC080808),
        1.00f to Color(0xF5080808)
    )
)

// ══════════════════════════════════════════════════════════════════════════════
// HomeScreen
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val rows: List<Pair<String, List<Movie>>> = remember(state.selectedTab, state) {
        if (state.selectedTab == "סרטים") listOf(
            "Trending Movies" to state.movieTrending,
            "New Releases"    to state.moviePremieres,
            "Action"          to state.movieAction,
            "Drama"           to state.movieDrama,
            "Sci-Fi"          to state.movieScifi,
            "Top Rated"       to state.movieTopRated
        ) else listOf(
            "Trending TV"  to state.tvTrending,
            "New Episodes" to state.tvPremieres,
            "Drama"        to state.tvDrama,
            "Crime"        to state.tvCrime,
            "Sci-Fi"       to state.tvScifi,
            "Top Rated"    to state.tvTopRated
        )
    }

    val focusState   = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }
    val fastThreshMs = 650L
    var hero         by remember { mutableStateOf<Movie?>(null) }
    val latestRows   by rememberUpdatedState(rows)

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            hero = latestRows.getOrNull(focusState.currentRowIndex)
                ?.second?.getOrNull(focusState.currentItemIndex)
                ?: latestRows.firstNotNullOfOrNull { it.second.firstOrNull() }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex to focusState.currentItemIndex }
            .distinctUntilChanged()
            .collectLatest { (ri, ii) ->
                if (focusState.isNavFocused) return@collectLatest
                val now  = SystemClock.elapsedRealtime()
                val fast = now - focusState.lastNavEventTime < fastThreshMs
                delay(if (fast) 700L else 200L)
                val idle = SystemClock.elapsedRealtime() - focusState.lastNavEventTime
                if (idle < fastThreshMs) return@collectLatest
                hero = latestRows.getOrNull(ri)?.second?.getOrNull(ii)
                    ?: latestRows.getOrNull(ri)?.second?.firstOrNull()
                    ?: latestRows.firstNotNullOfOrNull { it.second.firstOrNull() }
            }
    }

    BackHandler(enabled = focusState.isNavFocused) { focusState.isNavFocused = false }

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }
        BackdropLayer(hero)
        HomeInputLayer(
            rows         = rows,
            focusState   = focusState,
            fastThreshMs = fastThreshMs,
            activeTab    = state.selectedTab,
            onMovieClick = onMovieClick,
            onHeroFocus  = { hero = it },
            onSearch     = { navController.navigate("search") },
            onMoviesTab  = { viewModel.selectTab("סרטים") },
            onSeriesTab  = { viewModel.selectTab("סדרות") }
        )
        HomeHeroOverlay(hero = hero, onPlay = { hero?.id?.let(onMovieClick) })
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// BackdropLayer — full-bleed, image visible, premium scrims on top
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx     = LocalContext.current
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val (bwPx, bhPx) = remember(config, density) {
        with(density) {
            config.screenWidthDp.dp.roundToPx().coerceIn(1, 3840) to
            config.screenHeightDp.dp.roundToPx().coerceIn(1, 2160)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // solid BG base so no flash on first load
        Box(Modifier.fillMaxSize().background(BG))

        Crossfade(
            targetState   = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label         = "backdrop"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url, bwPx, bhPx) {
                        ImageRequest.Builder(ctx)
                            .data(url)
                            .size(bwPx, bhPx)
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
        }

        // layered scrims — order matters: left > top > bottom
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(brush = leftScrim)
                drawRect(brush = topScrim)
                drawRect(brush = bottomScrim)
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeHeroOverlay — sits above rows, vertically centred in hero zone
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeHeroOverlay(hero: Movie?, onPlay: () -> Unit) {
    val config = LocalConfiguration.current
    // rows zone = bottom 36% of screen; hero text sits above that
    val rowsH  = (config.screenHeightDp * 0.36f).dp.coerceIn(220.dp, 310.dp)
    val heroBottomPad = rowsH + 24.dp

    Box(Modifier.fillMaxSize().zIndex(3f)) {
        key(hero?.id) {
            hero?.let { m ->
                ArvioHeroInfo(
                    movie    = m,
                    onPlay   = onPlay,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 52.dp, end = 480.dp, bottom = heroBottomPad)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ArvioHeroInfo — premium UHD typography
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ArvioHeroInfo(movie: Movie, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Title — large, cinematic
        Text(
            text       = movie.title,
            color      = WHITE,
            fontSize   = 54.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 60.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis
        )

        // Metadata row
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (movie.rating > 0f) {
                // IMDb badge — gold, premium
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(GOLD)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("IMDb",  color = Color(0xFF1A1A1A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text("%.1f".format(movie.rating), color = Color(0xFF1A1A1A), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (movie.year > 0) {
                MetaDot()
                Text(movie.year.toString(), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            if (movie.genre.isNotBlank()) {
                MetaDot()
                Text(movie.genre, color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            // Resolution badge
            MetaDot()
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DIM3)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(movie.resolutionBadge, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }

        // Overview
        Text(
            text       = movie.overview,
            color      = DIM2,
            fontSize   = 14.sp,
            lineHeight = 22.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.widthIn(max = 560.dp)
        )

        Spacer(Modifier.height(4.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Play — solid white, primary CTA
            Surface(
                onClick  = onPlay,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = WHITE,
                    contentColor          = Color.Black,
                    focusedContainerColor = WHITE,
                    focusedContentColor   = Color.Black
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WHITE.copy(0.35f), 24.dp)),
                modifier = Modifier.height(54.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 32.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp))
                    Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // More Info — frosted glass
            Surface(
                onClick  = {},
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color(0x33FFFFFF),
                    contentColor          = WHITE,
                    focusedContainerColor = Color(0x55FFFFFF),
                    focusedContentColor   = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier.height(54.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                    Text("More Info", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Watchlist — icon-only, frosted
            Surface(
                onClick  = {},
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color(0x33FFFFFF),
                    contentColor          = WHITE,
                    focusedContainerColor = Color(0x55FFFFFF),
                    focusedContentColor   = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier.size(54.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MetaDot() {
    Box(
        Modifier
            .size(4.dp)
            .clip(RoundedCornerShape(50))
            .background(DIM3)
    )
}

@Composable
private fun MetaDivider() {
    Box(Modifier.width(1.dp).height(13.dp).background(DIM3))
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeInputLayer
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeInputLayer(
    rows: List<Pair<String, List<Movie>>>,
    focusState: HomeFocusState,
    fastThreshMs: Long,
    activeTab: String,
    onMovieClick: (String) -> Unit,
    onHeroFocus: (Movie) -> Unit,
    onSearch: () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit
) {
    val rootFR = remember { FocusRequester() }
    var rootHasFocus    by remember { mutableStateOf(false) }
    var suppressUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        suppressUntilMs = SystemClock.elapsedRealtime() + 800L
        rootFR.safe()
    }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            focusState.currentRowIndex = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
            val sz = rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 0
            if (sz > 0) focusState.currentItemIndex = focusState.currentItemIndex.coerceIn(0, sz - 1)
            if (!rootHasFocus) rootFR.safe()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFR)
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        if (focusState.isNavFocused) return@onPreviewKeyEvent true
                        if (focusState.currentRowIndex > 0) {
                            focusState.currentRowIndex--
                            focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        } else focusState.isNavFocused = true
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusState.isNavFocused) focusState.isNavFocused = false
                        else if (focusState.currentRowIndex < rows.size - 1) {
                            focusState.currentRowIndex++
                            focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!focusState.isNavFocused && focusState.currentItemIndex > 0) {
                            focusState.currentItemIndex--
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!focusState.isNavFocused) {
                            val max = (rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 1) - 1
                            if (focusState.currentItemIndex < max) {
                                focusState.currentItemIndex++
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            }
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (SystemClock.elapsedRealtime() < suppressUntilMs) return@onPreviewKeyEvent true
                        if (!focusState.isNavFocused) {
                            rows.getOrNull(focusState.currentRowIndex)
                                ?.second?.getOrNull(focusState.currentItemIndex)
                                ?.let { onMovieClick(it.id) }
                        }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        if (focusState.isNavFocused) { focusState.isNavFocused = false; true } else false
                    }
                    else -> false
                }
            }
    ) {
        ArvioTopNav(
            isActive      = focusState.isNavFocused,
            activeTab     = activeTab,
            onSearchClick = onSearch,
            onMoviesTab   = onMoviesTab,
            onSeriesTab   = onSeriesTab,
            modifier      = Modifier.fillMaxWidth().align(Alignment.TopStart).zIndex(10f)
        )
        HomeRowsLayer(
            rows         = rows,
            focusState   = focusState,
            fastThreshMs = fastThreshMs,
            onItemFocus  = onHeroFocus,
            onItemClick  = onMovieClick
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ArvioTopNav — slim, transparent, premium
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ArvioTopNav(
    isActive: Boolean,
    activeTab: String,
    onSearchClick: () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .padding(horizontal = 44.dp, vertical = 20.dp)
            .fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Wordmark
        Text(
            text          = "LUMINA",
            color         = WHITE,
            fontSize      = 18.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 4.sp,
            modifier      = Modifier.padding(end = 8.dp)
        )

        NavTab(label = "Home",      icon = Icons.Default.Home,     selected = true,                   onClick = {})
        NavTab(label = "Movies",    icon = Icons.Default.Movie,    selected = activeTab == "סרטים", onClick = onMoviesTab)
        NavTab(label = "TV",        icon = Icons.Default.LiveTv,   selected = activeTab == "סדרות", onClick = onSeriesTab)
        NavTab(label = "Watchlist", icon = Icons.Default.Bookmark, selected = false,                  onClick = {})
        NavTab(label = "Search",    icon = Icons.Default.Search,   selected = false,                  onClick = onSearchClick)

        Spacer(Modifier.weight(1f))

        NavTab(label = "Settings",  icon = Icons.Default.Settings, selected = false,                  onClick = {})

        val time = remember {
            val c = java.util.Calendar.getInstance()
            "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
        }
        Text(
            text       = time,
            color      = DIM,
            fontSize   = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun NavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue   = when {
            selected -> PILL_SEL
            focused  -> Color(0x33FFFFFF)
            else     -> PILL_UNS
        },
        animationSpec = tween(180),
        label         = "navBg"
    )
    val contentColor by animateColorAsState(
        targetValue   = if (selected) Color.Black else WHITE,
        animationSpec = tween(180),
        label         = "navContent"
    )
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = bgColor,
            focusedContainerColor = bgColor
        ),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50), RoundedCornerShape(50)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(36.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, Modifier.size(15.dp), tint = contentColor)
            Text(label, color = contentColor, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeRowsLayer — compact, anchored to bottom
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeRowsLayer(
    rows: List<Pair<String, List<Movie>>>,
    focusState: HomeFocusState,
    fastThreshMs: Long,
    onItemFocus: (Movie) -> Unit,
    onItemClick: (String) -> Unit
) {
    val config          = LocalConfiguration.current
    // rows zone: 36% of screen height, compact and premium
    val rowsViewH       = (config.screenHeightDp * 0.36f).dp.coerceIn(220.dp, 310.dp)
    val currentRowIndex = focusState.currentRowIndex

    var isFast by remember { mutableStateOf(false) }
    LaunchedEffect(focusState.lastNavEventTime) {
        val anchor = focusState.lastNavEventTime
        isFast = true
        delay(fastThreshMs)
        if (focusState.lastNavEventTime == anchor) isFast = false
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentRowIndex) {
        val target = currentRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        val dist   = kotlin.math.abs(target - listState.firstVisibleItemIndex)
        if (dist <= 1) listState.animateScrollToItem(target)
        else           listState.scrollToItem(target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 0.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(rowsViewH)
                .clipToBounds()
        ) {
            LazyColumn(
                state               = listState,
                contentPadding      = PaddingValues(bottom = rowsViewH),
                modifier            = Modifier.fillMaxSize().clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(rows, key = { _, p -> p.first }) { index, (title, movies) ->
                    val targetAlpha = if (index <= currentRowIndex) 1f else 0.18f
                    val rowAlpha by animateFloatAsState(
                        targetValue   = targetAlpha,
                        animationSpec = tween(260),
                        label         = "rowAlpha"
                    )
                    Box(
                        Modifier.fillMaxWidth().height(190.dp).clipToBounds()
                            .graphicsLayer { alpha = rowAlpha }
                    ) {
                        ArvioContentRow(
                            title            = title,
                            movies           = movies,
                            isCurrentRow     = !focusState.isNavFocused && index == currentRowIndex,
                            focusedItemIndex = if (!focusState.isNavFocused && index == currentRowIndex) focusState.currentItemIndex else -1,
                            isFastScrolling  = isFast,
                            onItemClick      = onItemClick,
                            onItemFocused    = { movie, itemIdx ->
                                focusState.currentRowIndex  = index
                                focusState.currentItemIndex = itemIdx
                                focusState.isNavFocused     = false
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                                onItemFocus(movie)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ArvioContentRow
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ArvioContentRow(
    title: String,
    movies: List<Movie>,
    isCurrentRow: Boolean,
    focusedItemIndex: Int,
    isFastScrolling: Boolean,
    onItemClick: (String) -> Unit,
    onItemFocused: (Movie, Int) -> Unit
) {
    if (movies.isEmpty()) return

    val rowState       = rememberLazyListState()
    val currentFocused by rememberUpdatedState(focusedItemIndex)
    val currentIsCur   by rememberUpdatedState(isCurrentRow)

    // Premium 16:9 card — compact, clean
    val cardW = 218.dp
    val cardH = 123.dp

    LaunchedEffect(focusedItemIndex, isCurrentRow) {
        if (!isCurrentRow || focusedItemIndex < 0) return@LaunchedEffect
        val total    = movies.size
        val visible  = rowState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxFirst = (total - visible).coerceAtLeast(0)
        val target   = focusedItemIndex.coerceAtMost(maxFirst)
        if (isFastScrolling) rowState.scrollToItem(target)
        else rowState.animateScrollToItem(target)
    }

    val rowFade  = remember { Animatable(1f) }
    var lastPage by remember { mutableIntStateOf(0) }
    val pageIdx  by remember { derivedStateOf { rowState.firstVisibleItemIndex / 5 } }
    LaunchedEffect(pageIdx, isCurrentRow, isFastScrolling) {
        if (!isCurrentRow || isFastScrolling) { rowFade.snapTo(1f); lastPage = pageIdx; return@LaunchedEffect }
        if (pageIdx != lastPage) {
            lastPage = pageIdx
            rowFade.snapTo(0.75f)
            rowFade.animateTo(1f, tween(200))
        }
    }

    Column(Modifier.padding(bottom = 0.dp)) {
        // Row label — small, caps-like, premium feel
        Text(
            text       = title,
            color      = DIM,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier   = Modifier.padding(start = 52.dp, bottom = 8.dp)
        )
        val fadeMod = if (rowFade.value < 0.999f) Modifier.graphicsLayer { alpha = rowFade.value } else Modifier
        LazyRow(
            modifier              = fadeMod,
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 52.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { idx, movie ->
                ArvioCard(
                    movie             = movie,
                    cardW             = cardW,
                    cardH             = cardH,
                    isFocusedOverride = currentIsCur && idx == currentFocused,
                    onFocused         = { onItemFocused(movie, idx) },
                    onClick           = { onItemClick(movie.id) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ArvioCard — premium 16:9 thumbnail with glow focus state
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun ArvioCard(
    movie: Movie,
    cardW: androidx.compose.ui.unit.Dp = 218.dp,
    cardH: androidx.compose.ui.unit.Dp = 123.dp,
    modifier: Modifier = Modifier,
    isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var selfFocused by remember { mutableStateOf(false) }
    val focused = isFocusedOverride || selfFocused

    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.07f else 1.00f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label         = "cardZoom"
    )

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Box(
            Modifier.width(cardW).height(cardH)
                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                .zIndex(if (focused) 8f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = CARD_BG,
                    focusedContainerColor = CARD_BG
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border(
                        BorderStroke(1.5.dp, WHITE.copy(alpha = 0.90f)),
                        shape = RoundedCornerShape(6.dp)
                    )
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = Glow(WHITE.copy(0.18f), 16.dp)
                ),
                modifier = Modifier.fillMaxSize()
                    .onFocusChanged { fs -> selfFocused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = remember(movie.backdropUrl, movie.posterUrl) {
                            ImageRequest.Builder(ctx)
                                .data(movie.backdropUrl.ifBlank { movie.posterUrl })
                                .size(440, 248)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true)
                                .crossfade(false)
                                .build()
                        },
                        contentDescription = movie.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                    // subtle bottom gradient for text readability
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(0.50f)
                            )
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text       = movie.title,
            color      = if (focused) WHITE else DIM,
            fontSize   = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.width(cardW)
        )
        Text(
            text       = if (movie.mediaType == "tv") "TV Show" else "Movie",
            color      = DIM3,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NfCard — back-compat alias
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun NfCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) = ArvioCard(movie = movie, modifier = modifier, isFocusedOverride = isFocusedOverride, onFocused = onFocused, onClick = onClick)

// ══════════════════════════════════════════════════════════════════════════════
// HomeLoading — premium shimmer skeleton
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF252525), Color(0xFF111111)),
        start = Offset(p * 2000f - 1000f, 0f),
        end   = Offset(p * 2000f, 500f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().padding(top = 100.dp, start = 52.dp, end = 52.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nav skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { Box(Modifier.width(80.dp).height(34.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
            }
            Spacer(Modifier.height(40.dp))
            // Title
            Box(Modifier.width(380.dp).height(52.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(280.dp).height(52.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            // Meta
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(60.dp).height(22.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(Modifier.width(90.dp).height(22.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
            Box(Modifier.width(520.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.width(460.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(120.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).background(shimmer))
                Box(Modifier.width(140.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).background(shimmer))
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(shimmer))
            }
            Spacer(Modifier.weight(1f))
            // Row label + cards x2
            repeat(2) {
                Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { Box(Modifier.width(218.dp).height(123.dp).clip(RoundedCornerShape(6.dp)).background(shimmer)) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeError
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⚠️", fontSize = 48.sp)
            Text(message, color = DIM, fontSize = 17.sp)
            Surface(
                onClick  = onRetry,
                colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 16.dp)),
                modifier = Modifier.height(52.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Try Again", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Back-compat aliases
// ══════════════════════════════════════════════════════════════════════════════
@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(msg: String, onRetry: () -> Unit) = HomeError(msg, onRetry)
@Composable fun LuminaSidebar(
    open: Boolean, activeTab: String,
    onClose: () -> Unit, onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit, onSearchClick: () -> Unit
) {}
@Composable fun NfSidebar(
    open: Boolean, activeId: String,
    sidebarFirstFR: FocusRequester, onFocusLanded: () -> Unit,
    onClose: () -> Unit, onNavSelect: (String) -> Unit
) {}
