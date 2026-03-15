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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import coil.size.Scale
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

// ─── palette ──────────────────────────────────────────────────────────────
private val BG   = Color(0xFF000000)
private val CARD = Color(0xFF0D0D0D)
private val RED  = Color(0xFFE50914)
private val RED2 = Color(0xFFB20710)
private val WHITE = Color(0xFFFFFFFF)
private val DIM   = Color(0xAAFFFFFF)
private val GOLD  = Color(0xFFFFC107)

private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}

// ─── HomeFocusState — ARVIO-style state machine, no Compose focus magic ───
@Stable
class HomeFocusState(
    initialRowIndex: Int = 0,
    initialItemIndex: Int = 0
) {
    var isSidebarFocused  by mutableStateOf(false)
    var currentRowIndex   by mutableIntStateOf(initialRowIndex)
    var currentItemIndex  by mutableIntStateOf(initialItemIndex)
    var lastNavEventTime  by mutableLongStateOf(0L)

    companion object {
        val Saver: Saver<HomeFocusState, List<Int>> = Saver(
            save    = { listOf(it.currentRowIndex, it.currentItemIndex) },
            restore = { HomeFocusState(it[0], it[1]) }
        )
    }
}

// ─── gradients (computed once) ────────────────────────────────────────────
private val heroLeftScrim = Brush.horizontalGradient(
    colorStops = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.88f),
        0.15f to Color.Black.copy(alpha = 0.76f),
        0.28f to Color.Black.copy(alpha = 0.52f),
        0.42f to Color.Black.copy(alpha = 0.22f),
        0.55f to Color.Transparent,
        1.00f to Color.Transparent
    )
)
private val heroTopScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.55f),
        0.06f to Color.Black.copy(alpha = 0.28f),
        0.14f to Color.Transparent,
        1.00f to Color.Transparent
    )
)
private val heroBottomScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.82f to Color.Transparent,
        0.91f to Color.Black.copy(alpha = 0.55f),
        1.00f to Color.Black.copy(alpha = 0.90f)
    )
)

// ══════════════════════════════════════════════════════════════════════════
// HomeScreen
// ══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val rows: List<Pair<String, List<Movie>>> = remember(state.selectedTab, state) {
        if (state.selectedTab == "סרטים") listOf(
            "🔥 טרנדינג"     to state.movieTrending,
            "✨ פרמיירה"      to state.moviePremieres,
            "⚡ פעולה"        to state.movieAction,
            "🎭 דרמה"         to state.movieDrama,
            "🚀 מדע בדיוני"   to state.movieScifi,
            "🏆 דירוג עליון" to state.movieTopRated
        ) else listOf(
            "🔥 טרנדינג"     to state.tvTrending,
            "✨ פרמיירה"      to state.tvPremieres,
            "🎭 דרמה"         to state.tvDrama,
            "🔪 פשע"          to state.tvCrime,
            "🚀 מדע בדיוני"   to state.tvScifi,
            "🏆 דירוג עליון" to state.tvTopRated
        )
    }

    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }
    val fastScrollThresholdMs = 650L

    // hero = focused item
    var hero by remember { mutableStateOf<Movie?>(null) }
    val latestRows by rememberUpdatedState(rows)

    // Initialise hero once data arrives
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            hero = latestRows.getOrNull(focusState.currentRowIndex)
                ?.second?.getOrNull(focusState.currentItemIndex)
                ?: latestRows.firstNotNullOfOrNull { it.second.firstOrNull() }
        }
    }

    // Update hero with fast-scroll debounce (ARVIO pattern)
    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex to focusState.currentItemIndex }
            .distinctUntilChanged()
            .collectLatest { (ri, ii) ->
                if (focusState.isSidebarFocused) return@collectLatest
                val now = SystemClock.elapsedRealtime()
                val fast = now - focusState.lastNavEventTime < fastScrollThresholdMs
                delay(if (fast) 700L else 220L)
                val idle = SystemClock.elapsedRealtime() - focusState.lastNavEventTime
                if (idle < fastScrollThresholdMs) return@collectLatest
                hero = latestRows.getOrNull(ri)?.second?.getOrNull(ii)
                    ?: latestRows.getOrNull(ri)?.second?.firstOrNull()
                    ?: latestRows.firstNotNullOfOrNull { it.second.firstOrNull() }
            }
    }

    BackHandler(enabled = focusState.isSidebarFocused) {
        focusState.isSidebarFocused = false
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }

        // ── Layer 0: backdrop + triple scrim ──────────────────────────────
        BackdropLayer(hero)

        // ── Layer 1: input handler + top bar + rows ───────────────────────
        HomeInputLayer(
            rows              = rows,
            focusState        = focusState,
            fastScrollThreshMs = fastScrollThresholdMs,
            activeTab         = state.selectedTab,
            onMovieClick      = onMovieClick,
            onHeroFocus       = { hero = it },
            onSearch          = { navController.navigate("search") },
            onMoviesTab       = { viewModel.selectTab("סרטים") },
            onSeriesTab       = { viewModel.selectTab("סדרות") }
        )

        // ── Layer 2: hero metadata floating above everything (zIndex=3) ───
        HomeHeroLayer(
            hero = hero,
            onPlay = { hero?.id?.let(onMovieClick) }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// BackdropLayer  — crossfade backdrop + 3 scrim passes
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val (bwPx, bhPx) = remember(configuration, density) {
        with(density) {
            configuration.screenWidthDp.dp.roundToPx().coerceIn(1, 3840) to
            configuration.screenHeightDp.dp.roundToPx().coerceIn(1, 2160)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // solid base
        Box(Modifier.fillMaxSize().background(BG))

        // backdrop crossfade — 320 ms, no AnimatedContent overhead
        Crossfade(
            targetState  = hero?.backdropUrl ?: hero?.posterUrl,
            animationSpec = tween(320),
            label        = "backdrop"
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // triple scrim drawn in a single pass
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(brush = heroLeftScrim)
                drawRect(brush = heroTopScrim)
                drawRect(brush = heroBottomScrim)
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HomeHeroLayer  — floats at zIndex 3, bottom-left, above rows
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeHeroLayer(hero: Movie?, onPlay: () -> Unit) {
    val configuration = LocalConfiguration.current
    // rows occupy bottom 36% → hero sits just above them
    val rowsHeight = (configuration.screenHeightDp * 0.36f).dp.coerceIn(240.dp, 320.dp)
    val heroPad   = rowsHeight + 8.dp

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
    ) {
        key(hero?.id) {
            hero?.let { m ->
                HeroInfo(
                    movie    = m,
                    onPlay   = onPlay,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 56.dp, end = 420.dp)
                        .offset(y = -heroPad)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HomeInputLayer  — all d-pad logic, sidebar + top bar + rows
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeInputLayer(
    rows: List<Pair<String, List<Movie>>>,
    focusState: HomeFocusState,
    fastScrollThreshMs: Long,
    activeTab: String,
    onMovieClick: (String) -> Unit,
    onHeroFocus: (Movie) -> Unit,
    onSearch: () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit
) {
    val rootFR = remember { FocusRequester() }
    var rootHasFocus by remember { mutableStateOf(false) }
    var suppressSelectUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        suppressSelectUntilMs = SystemClock.elapsedRealtime() + 900L
        rootFR.safe()
    }

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            focusState.currentRowIndex = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
            val rowSize = rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 0
            if (rowSize > 0) focusState.currentItemIndex = focusState.currentItemIndex.coerceIn(0, rowSize - 1)
            if (!rootHasFocus) rootFR.safe()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFR)
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (focusState.isSidebarFocused) return@onPreviewKeyEvent true
                        if (focusState.currentRowIndex > 0) {
                            focusState.currentRowIndex--
                            focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        } else {
                            focusState.isSidebarFocused = true
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusState.isSidebarFocused) {
                            focusState.isSidebarFocused = false
                        } else if (focusState.currentRowIndex < rows.size - 1) {
                            focusState.currentRowIndex++
                            focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!focusState.isSidebarFocused && focusState.currentItemIndex > 0) {
                            focusState.currentItemIndex--
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (!focusState.isSidebarFocused) {
                            val max = (rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 1) - 1
                            if (focusState.currentItemIndex < max) {
                                focusState.currentItemIndex++
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            }
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (SystemClock.elapsedRealtime() < suppressSelectUntilMs) return@onPreviewKeyEvent true
                        if (!focusState.isSidebarFocused) {
                            rows.getOrNull(focusState.currentRowIndex)
                                ?.second?.getOrNull(focusState.currentItemIndex)
                                ?.let { onMovieClick(it.id) }
                        }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        if (focusState.isSidebarFocused) {
                            focusState.isSidebarFocused = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        // top bar
        LuminaTopBar(
            isFocused    = focusState.isSidebarFocused,
            activeTab    = activeTab,
            onSearchClick = onSearch,
            modifier     = Modifier.fillMaxWidth().align(Alignment.TopStart).zIndex(10f)
        )

        // rows
        HomeRowsLayer(
            rows           = rows,
            focusState     = focusState,
            fastScrollThreshMs = fastScrollThreshMs,
            onItemFocused  = onHeroFocus,
            onItemClick    = onMovieClick
        )

        // sidebar dim overlay
        AnimatedVisibility(
            visible  = focusState.isSidebarFocused,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(19f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.65f)))
        }

        // sidebar panel
        LuminaSidebar(
            open          = focusState.isSidebarFocused,
            activeTab     = activeTab,
            onClose       = { focusState.isSidebarFocused = false },
            onMoviesClick = { focusState.isSidebarFocused = false; onMoviesTab() },
            onSeriesClick = { focusState.isSidebarFocused = false; onSeriesTab() },
            onSearchClick = { focusState.isSidebarFocused = false; onSearch() }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HomeRowsLayer  — rows in bottom 36%, row alpha fade like ARVIO
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeRowsLayer(
    rows: List<Pair<String, List<Movie>>>,
    focusState: HomeFocusState,
    fastScrollThreshMs: Long,
    onItemFocused: (Movie) -> Unit,
    onItemClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val rowsViewportHeight = (configuration.screenHeightDp * 0.36f).dp.coerceIn(240.dp, 320.dp)
    val currentRowIndex = focusState.currentRowIndex

    var isFastScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(focusState.lastNavEventTime) {
        val anchor = focusState.lastNavEventTime
        isFastScrolling = true
        delay(fastScrollThreshMs)
        if (focusState.lastNavEventTime == anchor) isFastScrolling = false
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentRowIndex) {
        val target = currentRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        val dist = kotlin.math.abs(target - listState.firstVisibleItemIndex)
        if (dist <= 1) listState.animateScrollToItem(target)
        else listState.scrollToItem(target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(rowsViewportHeight)
                .clipToBounds()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = rowsViewportHeight),
                modifier = Modifier.fillMaxSize().clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    rows,
                    key = { _, pair -> pair.first }
                ) { index, (title, movies) ->
                    // rows below current fade to 25% — ARVIO signature
                    val targetAlpha = if (index <= currentRowIndex) 1f else 0.25f
                    val rowAlpha by animateFloatAsState(
                        targetValue  = targetAlpha,
                        animationSpec = tween(300),
                        label        = "rowAlpha"
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clipToBounds()
                            .graphicsLayer { alpha = rowAlpha }
                    ) {
                        LuminaContentRow(
                            title           = title,
                            movies          = movies,
                            isCurrentRow    = !focusState.isSidebarFocused && index == currentRowIndex,
                            focusedItemIndex = if (!focusState.isSidebarFocused && index == currentRowIndex) focusState.currentItemIndex else -1,
                            isFastScrolling = isFastScrolling,
                            startPadding    = 56.dp,
                            onItemClick     = onItemClick,
                            onItemFocused   = { movie, itemIdx ->
                                focusState.currentRowIndex  = index
                                focusState.currentItemIndex = itemIdx
                                focusState.isSidebarFocused = false
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                                onItemFocused(movie)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LuminaContentRow
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun LuminaContentRow(
    title: String,
    movies: List<Movie>,
    isCurrentRow: Boolean,
    focusedItemIndex: Int,
    isFastScrolling: Boolean,
    startPadding: androidx.compose.ui.unit.Dp,
    onItemClick: (String) -> Unit,
    onItemFocused: (Movie, Int) -> Unit
) {
    if (movies.isEmpty()) return

    val rowState = rememberLazyListState()
    val currentFocused by rememberUpdatedState(focusedItemIndex)
    val currentIsCurrent by rememberUpdatedState(isCurrentRow)

    val density = LocalDensity.current
    val itemWidth = 110.dp
    val itemSpacing = 10.dp
    val itemSpanPx = remember(density, itemWidth, itemSpacing) {
        with(density) { (itemWidth + itemSpacing).toPx().coerceAtLeast(1f) }
    }

    // scroll to focused card
    LaunchedEffect(focusedItemIndex, isCurrentRow) {
        if (!isCurrentRow || focusedItemIndex < 0) return@LaunchedEffect
        val totalItems = movies.size
        val visible = rowState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxFirst = (totalItems - visible).coerceAtLeast(0)
        val target = focusedItemIndex.coerceAtMost(maxFirst)
        if (isFastScrolling) rowState.scrollToItem(target)
        else rowState.animateScrollToItem(target)
    }

    // page-turn fade
    val rowFade = remember { Animatable(1f) }
    var lastPage by remember { mutableIntStateOf(0) }
    val pageIndex by remember { derivedStateOf { rowState.firstVisibleItemIndex / 6 } }
    LaunchedEffect(pageIndex, isCurrentRow, isFastScrolling) {
        if (!isCurrentRow || isFastScrolling) { rowFade.snapTo(1f); lastPage = pageIndex; return@LaunchedEffect }
        if (pageIndex != lastPage) {
            lastPage = pageIndex
            rowFade.snapTo(0.75f)
            rowFade.animateTo(1f, tween(180))
        }
    }

    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title,
            color = WHITE,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = startPadding, bottom = 8.dp)
        )
        val fadeMod = if (rowFade.value < 0.999f) Modifier.graphicsLayer { alpha = rowFade.value } else Modifier
        LazyRow(
            modifier = fadeMod,
            state = rowState,
            contentPadding = PaddingValues(horizontal = startPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { idx, movie ->
                NfCard(
                    movie       = movie,
                    isFocusedOverride = currentIsCurrent && idx == currentFocused,
                    onFocused   = { onItemFocused(movie, idx) },
                    onClick     = { onItemClick(movie.id) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// NfCard — zoom only, no border, no glow (kept as-is, already correct)
// ══════════════════════════════════════════════════════════════════════════
@Composable
fun NfCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var selfFocused by remember { mutableStateOf(false) }
    val focused = isFocusedOverride || selfFocused

    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.16f else 1.00f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "zoom"
    )

    Box(
        Modifier
            .width(110.dp).height(165.dp)
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
            .zIndex(if (focused) 8f else 0f)
    ) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(containerColor = CARD, focusedContainerColor = CARD),
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border   = ClickableSurfaceDefaults.border(Border.None, Border.None),
            glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = modifier.fillMaxSize()
                .onFocusChanged { fs -> selfFocused = fs.isFocused; if (fs.isFocused) onFocused() }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = remember(movie.posterUrl) {
                        ImageRequest.Builder(ctx)
                            .data(movie.posterUrl).size(220, 330)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true).crossfade(false).build()
                    },
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.75f)))
                    )
                )
                // resolution badge
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(movie.resolutionBadge, color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                // title on focus
                AnimatedVisibility(
                    visible  = focused,
                    enter    = fadeIn(tween(110)) + slideInVertically(tween(130)) { it / 2 },
                    exit     = fadeOut(tween(80)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        movie.title, color = WHITE,
                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// HeroInfo  — title / meta / big buttons (kept same, already good)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroInfo(movie: Movie, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // meta row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (movie.year > 0)
                Text(movie.year.toString(), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (movie.rating > 0f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = GOLD)
                    Text("%.1f".format(movie.rating), color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            Box(
                Modifier.clip(RoundedCornerShape(3.dp))
                    .background(WHITE.copy(0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(movie.resolutionBadge, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // title
        Text(
            movie.title, color = WHITE,
            fontSize = 52.sp, fontWeight = FontWeight.Black,
            lineHeight = 58.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
        )

        // overview
        Text(
            movie.overview, color = DIM,
            fontSize = 14.sp, lineHeight = 22.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(8.dp))

        // action buttons (height=72dp, icon=30dp, text=22sp — unchanged)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onPlay,
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor = RED, contentColor = WHITE,
                    focusedContainerColor = RED2, focusedContentColor = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.80f), 36.dp)),
                modifier = Modifier.height(72.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(30.dp))
                    Text("נגן", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }

            Surface(
                onClick = {},
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE.copy(0.13f), contentColor = WHITE,
                    focusedContainerColor = WHITE.copy(0.24f), focusedContentColor = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WHITE.copy(0.20f), 20.dp)),
                modifier = Modifier.height(72.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(28.dp))
                    Text("פרטים", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Surface(
                onClick = {},
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE.copy(0.13f), contentColor = WHITE,
                    focusedContainerColor = WHITE.copy(0.24f), focusedContentColor = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, Modifier.size(30.dp))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LuminaTopBar  — slim bar, no tabs
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun LuminaTopBar(
    isFocused: Boolean,
    activeTab: String,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 56.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo
        Text(
            "LUMINA",
            color = RED,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // active tab pill
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(WHITE.copy(0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(activeTab, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            // search hint
            Surface(
                onClick = onSearchClick,
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor = WHITE.copy(0.08f),
                    focusedContainerColor = WHITE.copy(0.18f)
                ),
                shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = DIM)
                    Text("חיפוש", color = DIM, fontSize = 13.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// LuminaSidebar  — slides in from left (ARVIO style)
// ══════════════════════════════════════════════════════════════════════════
@Composable
fun LuminaSidebar(
    open: Boolean,
    activeTab: String,
    onClose: () -> Unit,
    onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(260)) { -it } + fadeIn(tween(200)),
        exit     = slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(180)),
        modifier = Modifier.fillMaxHeight().zIndex(20f)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(290.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF040404), Color(0xFF111111)))
                )
        ) {
            // red accent line right edge
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, RED.copy(0.70f), RED.copy(0.70f), Color.Transparent)
                        )
                    )
            )

            Column(Modifier.fillMaxSize().padding(vertical = 56.dp)) {
                Text(
                    "LUMINA",
                    color = RED, fontSize = 22.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 48.dp)
                )

                data class SI(
                    val label: String,
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val active: Boolean,
                    val act: () -> Unit
                )

                val items = listOf(
                    SI("בית",    Icons.Default.Home,   false,                  onClose),
                    SI("סרטים", Icons.Default.Movie,  activeTab == "סרטים",   onMoviesClick),
                    SI("סדרות",  Icons.Default.LiveTv, activeTab == "סדרות",   onSeriesClick),
                    SI("חיפוש",  Icons.Default.Search, false,                  onSearchClick)
                )

                val frs = remember(items.size) { List(items.size) { FocusRequester() } }
                LaunchedEffect(open) { if (open) { delay(80); frs.first().safe() } }

                items.forEachIndexed { i, item ->
                    SidebarRow(
                        label   = item.label,
                        icon    = item.icon,
                        active  = item.active,
                        fr      = frs[i],
                        prevFR  = frs.getOrNull(i - 1),
                        nextFR  = frs.getOrNull(i + 1),
                        onRight = onClose,
                        onClick = item.act
                    )
                }

                Spacer(Modifier.weight(1f))
                Text(
                    "ימין לתוכן ▶",
                    color = WHITE.copy(0.18f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    fr: FocusRequester,
    prevFR: FocusRequester?,
    nextFR: FocusRequester?,
    onRight: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { active -> RED.copy(0.18f); focused -> WHITE.copy(0.09f); else -> Color.Transparent },
        tween(130), label = "sidebarBg"
    )
    val textColor by animateColorAsState(
        if (active || focused) WHITE else DIM, tween(130), label = "sidebarText"
    )

    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .focusRequester(fr)
            .focusProperties {
                if (prevFR != null) up   = prevFR
                if (nextFR != null) down = nextFR
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { kev ->
                if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    kev.key == Key.DirectionRight                -> { onRight(); true }
                    kev.key == Key.Back || kev.key == Key.Escape -> { onRight(); true }
                    else -> false
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                Modifier.width(3.dp).height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) RED else Color.Transparent)
            )
            Icon(icon, null, tint = if (active) RED else textColor, modifier = Modifier.size(24.dp))
            Text(label, color = textColor, fontSize = 18.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Loading + Error
// ══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Restart), "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF161616), Color(0xFF2B2B2B), Color(0xFF161616)),
        start = Offset(p * 1800f - 900f, 0f), end = Offset(p * 1800f, 400f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().padding(top = 140.dp, start = 56.dp, end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(Modifier.fillMaxWidth(0.38f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.44f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.50f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(120.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Box(Modifier.width(130.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            }
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Box(Modifier.fillMaxWidth(0.10f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(8) { Box(Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(6.dp)).background(shimmer)) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

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
                onClick = onRetry,
                colors  = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2),
                shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 16.dp)),
                modifier = Modifier.height(52.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("נסה שוב", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// back-compat aliases
@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(message: String, onRetry: () -> Unit) = HomeError(message, onRetry)
@Composable fun NfSidebar(
    open: Boolean, activeId: String,
    sidebarFirstFR: FocusRequester, onFocusLanded: () -> Unit,
    onClose: () -> Unit, onNavSelect: (String) -> Unit
) = LuminaSidebar(
    open          = open,
    activeTab     = activeId,
    onClose       = onClose,
    onMoviesClick = { onNavSelect("סרטים") },
    onSeriesClick = { onNavSelect("סדרות") },
    onSearchClick = { onNavSelect("search") }
)
