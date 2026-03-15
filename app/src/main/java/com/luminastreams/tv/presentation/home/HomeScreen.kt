@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
private val BG       = Color(0xFF0A0A0A)
private val RED      = Color(0xFFE50914)
private val RED2     = Color(0xFFB20710)
private val WHITE    = Color(0xFFFFFFFF)
private val DIM      = Color(0xB3FFFFFF)
private val DIM2     = Color(0x80FFFFFF)
private val GOLD     = Color(0xFFFFD700)
private val PILL_SEL = Color(0xFFFFFFFF)
private val PILL_UNS = Color(0x1AFFFFFF)

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

// ─── Scrims ────────────────────────────────────────────────────────────────────
private val leftScrim = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.92f),
        0.18f to Color.Black.copy(alpha = 0.80f),
        0.35f to Color.Black.copy(alpha = 0.55f),
        0.50f to Color.Black.copy(alpha = 0.20f),
        0.62f to Color.Transparent,
        1.00f to Color.Transparent
    )
)
private val topScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.70f),
        0.10f to Color.Black.copy(alpha = 0.35f),
        0.22f to Color.Transparent,
        1.00f to Color.Transparent
    )
)
private val bottomScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.78f to Color.Transparent,
        0.88f to Color.Black.copy(alpha = 0.60f),
        1.00f to Color.Black.copy(alpha = 0.95f)
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
// BackdropLayer
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
        Box(Modifier.fillMaxSize().background(BG))
        Crossfade(
            targetState   = hero?.backdropUrl ?: hero?.posterUrl,
            animationSpec = tween(400),
            label         = "backdrop"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = remember(url, bwPx, bhPx) {
                        ImageRequest.Builder(ctx)
                            .data(url).size(bwPx, bhPx)
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
            drawRect(brush = leftScrim)
            drawRect(brush = topScrim)
            drawRect(brush = bottomScrim)
        })
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeHeroOverlay
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeHeroOverlay(hero: Movie?, onPlay: () -> Unit) {
    val config = LocalConfiguration.current
    val rowsH  = (config.screenHeightDp * 0.38f).dp.coerceIn(250.dp, 340.dp)
    val offset = rowsH + 12.dp
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        key(hero?.id) {
            hero?.let { m ->
                ArvioHeroInfo(
                    movie    = m,
                    onPlay   = onPlay,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, end = 440.dp)
                        .offset(y = -offset)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// ArvioHeroInfo
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ArvioHeroInfo(movie: Movie, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text       = movie.title,
            color      = WHITE,
            fontSize   = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (movie.year > 0) {
                Text(text = movie.year.toString(), color = DIM, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                MetaDivider()
            }
            if (movie.genre.isNotBlank()) {
                Text(text = movie.genre, color = DIM, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                MetaDivider()
            }
            Box(
                Modifier.clip(RoundedCornerShape(4.dp))
                    .background(WHITE.copy(0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = movie.resolutionBadge, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (movie.rating > 0f) {
                MetaDivider()
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(GOLD)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "IMDb",  color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "%.1f".format(movie.rating), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Text(
            text       = movie.overview,
            color      = DIM,
            fontSize   = 14.sp,
            lineHeight = 22.sp,
            maxLines   = 3,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Surface(
                onClick  = onPlay,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE, contentColor = Color.Black,
                    focusedContainerColor = WHITE, focusedContentColor = Color.Black
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WHITE.copy(0.45f), 28.dp)),
                modifier = Modifier.height(52.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 28.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Text("Play", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                onClick  = {},
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE.copy(0.18f), contentColor = WHITE,
                    focusedContainerColor = WHITE.copy(0.30f), focusedContentColor = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(52.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 22.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(22.dp))
                    Text("More Info", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
            Surface(
                onClick  = {},
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE.copy(0.18f), contentColor = WHITE,
                    focusedContainerColor = WHITE.copy(0.30f), focusedContentColor = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MetaDivider() {
    Box(Modifier.width(1.dp).height(14.dp).background(DIM2))
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
// ArvioTopNav
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
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(0.72f), Color.Black.copy(0.40f), Color.Transparent)
            )
        ).padding(horizontal = 40.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(RED)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("LUMINA", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            }
            Spacer(Modifier.width(8.dp))
            NavTab(label = "Search",    icon = Icons.Default.Search,   selected = false,                    onClick = onSearchClick)
            NavTab(label = "Home",      icon = Icons.Default.Home,     selected = true,                     onClick = {})
            NavTab(label = "Watchlist", icon = Icons.Default.Bookmark, selected = false,                    onClick = {})
            NavTab(label = "Movies",    icon = Icons.Default.Movie,    selected = activeTab == "סרטים",   onClick = onMoviesTab)
            NavTab(label = "TV",        icon = Icons.Default.LiveTv,   selected = activeTab == "סדרות",   onClick = onSeriesTab)
            Spacer(Modifier.weight(1f))
            NavTab(label = "Settings",  icon = Icons.Default.Settings, selected = false,                    onClick = {})
            val time = remember {
                val c = java.util.Calendar.getInstance()
                "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
            }
            Text(time, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
        }
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
        targetValue   = when { selected -> PILL_SEL; focused -> WHITE.copy(0.22f); else -> PILL_UNS },
        animationSpec = tween(160),
        label         = "navBg"
    )
    val textColor by animateColorAsState(
        targetValue   = if (selected) Color.Black else WHITE,
        animationSpec = tween(160),
        label         = "navText"
    )
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = bgColor, focusedContainerColor = bgColor),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50), RoundedCornerShape(50)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier.height(38.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(17.dp))
            Text(text = label, color = textColor, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HomeRowsLayer
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
    val rowsViewH       = (config.screenHeightDp * 0.40f).dp.coerceIn(260.dp, 340.dp)
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

    Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
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
                    val targetAlpha = if (index <= currentRowIndex) 1f else 0.20f
                    val rowAlpha by animateFloatAsState(
                        targetValue   = targetAlpha,
                        animationSpec = tween(280),
                        label         = "rowAlpha"
                    )
                    Box(
                        Modifier.fillMaxWidth().height(210.dp).clipToBounds()
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
    val cardW = 200.dp
    val cardH = 115.dp

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
            rowFade.snapTo(0.7f)
            rowFade.animateTo(1f, tween(160))
        }
    }

    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = title, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
        )
        val fadeMod = if (rowFade.value < 0.999f) Modifier.graphicsLayer { alpha = rowFade.value } else Modifier
        LazyRow(
            modifier              = fadeMod,
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
// ArvioCard  — landscape 16:9
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun ArvioCard(
    movie: Movie,
    cardW: androidx.compose.ui.unit.Dp = 200.dp,
    cardH: androidx.compose.ui.unit.Dp = 115.dp,
    modifier: Modifier = Modifier,
    isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var selfFocused by remember { mutableStateOf(false) }
    val focused = isFocusedOverride || selfFocused

    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.08f else 1.00f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "cardZoom"
    )
    val borderAlpha by animateFloatAsState(
        targetValue   = if (focused) 1f else 0f,
        animationSpec = tween(160),
        label         = "borderAlpha"
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
                    containerColor        = Color(0xFF1A1A1A),
                    focusedContainerColor  = Color(0xFF1A1A1A)
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    Border.None,
                    focusedBorder = Border(
                        BorderStroke(2.dp, WHITE),
                        shape = RoundedCornerShape(8.dp)
                    )
                ),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier.fillMaxSize()
                    .onFocusChanged { fs -> selfFocused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = remember(movie.backdropUrl, movie.posterUrl) {
                            ImageRequest.Builder(ctx)
                                .data(movie.backdropUrl.ifBlank { movie.posterUrl })
                                .size(400, 230)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true).crossfade(false).build()
                        },
                        contentDescription = movie.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text       = movie.title,
            color      = if (focused) WHITE else DIM,
            fontSize   = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.width(cardW)
        )
        Text(
            text       = if (movie.mediaType == "tv") "TV Show" else "Movie",
            color      = DIM2,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NfCard  — back-compat alias
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
// HomeLoading
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF141414), Color(0xFF2A2A2A), Color(0xFF141414)),
        start = Offset(p * 1800f - 900f, 0f),
        end   = Offset(p * 1800f, 400f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().padding(top = 120.dp, start = 48.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(5) { Box(Modifier.width(90.dp).height(36.dp).clip(RoundedCornerShape(50)).background(shimmer)) }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.32f).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.48f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.42f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(110.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Box(Modifier.width(110.dp).height(48.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            }
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Box(Modifier.fillMaxWidth(0.12f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(6) { Box(Modifier.width(200.dp).height(115.dp).clip(RoundedCornerShape(8.dp)).background(shimmer)) }
                }
                Spacer(Modifier.height(16.dp))
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
