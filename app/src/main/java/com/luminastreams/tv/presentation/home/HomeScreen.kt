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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

private val BG        = Color(0xFF080808)
private val RED       = Color(0xFFE50914)
private val RED2      = Color(0xFFB20710)
private val WHITE     = Color(0xFFFFFFFF)
private val DIM       = Color(0xCCFFFFFF)
private val DIM2      = Color(0x99FFFFFF)
private val DIM3      = Color(0x4DFFFFFF)
private val GOLD      = Color(0xFFFFD700)
private val CARD_BG   = Color(0xFF181818)
private val NAV_HOVER = Color(0x18FFFFFF)
private val NAV_PRESS = Color(0xFF1E1E1E)

private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}
private const val NAV_COUNT = 5

@Stable
class HomeFocusState(initialRowIndex: Int = 0, initialItemIndex: Int = 0) {
    var isNavFocused     by mutableStateOf(false)
    var navItemIndex     by mutableIntStateOf(0)
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

private val leftScrim = Brush.horizontalGradient(colorStops = arrayOf(
    0.00f to Color(0xD9080808), 0.15f to Color(0xB3080808),
    0.30f to Color(0x80080808), 0.48f to Color(0x33080808), 0.62f to Color.Transparent))
private val topScrim = Brush.verticalGradient(colorStops = arrayOf(
    0.00f to Color(0x99080808), 0.10f to Color(0x44080808), 0.22f to Color.Transparent))
private val bottomScrim = Brush.verticalGradient(colorStops = arrayOf(
    0.00f to Color.Transparent, 0.55f to Color.Transparent,
    0.72f to Color(0x66080808), 0.85f to Color(0xCC080808), 1.00f to Color(0xF5080808)))

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
        if (!state.isLoading)
            hero = latestRows.getOrNull(focusState.currentRowIndex)
                ?.second?.getOrNull(focusState.currentItemIndex)
                ?: latestRows.firstNotNullOfOrNull { it.second.firstOrNull() }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { focusState.currentRowIndex to focusState.currentItemIndex }
            .distinctUntilChanged()
            .collectLatest { (ri, ii) ->
                if (focusState.isNavFocused) return@collectLatest
                val fast = SystemClock.elapsedRealtime() - focusState.lastNavEventTime < fastThreshMs
                delay(if (fast) 700L else 200L)
                if (SystemClock.elapsedRealtime() - focusState.lastNavEventTime >= fastThreshMs)
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
            rows        = rows, focusState = focusState, fastThreshMs = fastThreshMs,
            activeTab   = state.selectedTab, onMovieClick = onMovieClick,
            onHeroFocus = { hero = it },
            onSearch    = { navController.navigate("search") },
            onMoviesTab = { viewModel.selectTab("סרטים") },
            onSeriesTab = { viewModel.selectTab("סדרות") },
            onWatchlist = { navController.navigate("watchlist") },
            onSettings  = { navController.navigate("settings") }
        )
        HomeHeroOverlay(hero = hero)
    }
}

@Composable
private fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
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
            targetState   = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label         = "backdrop"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url, bwPx, bhPx) {
                        ImageRequest.Builder(ctx).data(url).size(bwPx, bhPx)
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
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent(); drawRect(leftScrim); drawRect(topScrim); drawRect(bottomScrim)
        })
    }
}

@Composable
private fun HomeHeroOverlay(hero: Movie?) {
    val config = LocalConfiguration.current
    val rowsH  = (config.screenHeightDp * 0.40f).dp.coerceIn(250.dp, 340.dp)
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        key(hero?.id) {
            hero?.let { m ->
                ArvioHeroInfo(
                    movie    = m,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 52.dp, end = 440.dp, bottom = rowsH + 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ArvioHeroInfo(movie: Movie, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.Start) {
        Text(movie.title, color = WHITE, fontSize = 46.sp, fontWeight = FontWeight.Black,
            lineHeight = 52.sp, letterSpacing = 0.3.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            if (movie.year > 0)             { Text(movie.year.toString(), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium); MetaPipe() }
            if (movie.genre.isNotBlank())   { Text(movie.genre,           color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium); MetaPipe() }
            if (movie.resolutionBadge.isNotBlank()) {
                Text(movie.resolutionBadge, color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (movie.rating > 0f) MetaPipe()
            }
            if (movie.rating > 0f) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(GOLD).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("IMDb", color = Color(0xFF1A1A1A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text("%.1f".format(movie.rating), color = Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Text(movie.overview, color = DIM2, fontSize = 15.sp, lineHeight = 23.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 580.dp))
    }
}
@Composable private fun MetaPipe() = Text("  |  ", color = DIM3, fontSize = 13.sp)

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
    onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit
) {
    val rootFR          = remember { FocusRequester() }
    val firstNavFR      = remember { FocusRequester() }
    var rootHasFocus    by remember { mutableStateOf(false) }
    var suppressUntilMs by remember { mutableLongStateOf(0L) }
    val focusManager    = LocalFocusManager.current

    LaunchedEffect(Unit) { suppressUntilMs = SystemClock.elapsedRealtime() + 800L; rootFR.safe() }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            focusState.currentRowIndex = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
            val sz = rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 0
            if (sz > 0) focusState.currentItemIndex = focusState.currentItemIndex.coerceIn(0, sz - 1)
            if (!rootHasFocus) rootFR.safe()
        }
    }
    LaunchedEffect(focusState.isNavFocused) {
        if (focusState.isNavFocused) { delay(40); firstNavFR.safe() }
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
                            focusState.currentRowIndex--; focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        } else { focusState.isNavFocused = true }
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusState.isNavFocused) { focusState.isNavFocused = false; rootFR.safe() }
                        else if (focusState.currentRowIndex < rows.size - 1) {
                            focusState.currentRowIndex++; focusState.currentItemIndex = 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusState.isNavFocused) {
                            if (focusState.navItemIndex > 0) focusManager.moveFocus(FocusDirection.Left)
                            true
                        } else {
                            if (focusState.currentItemIndex > 0) {
                                focusState.currentItemIndex--
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            }
                            true
                        }
                    }
                    Key.DirectionRight -> {
                        if (focusState.isNavFocused) {
                            if (focusState.navItemIndex < NAV_COUNT - 1) focusManager.moveFocus(FocusDirection.Right)
                            true
                        } else {
                            val max = (rows.getOrNull(focusState.currentRowIndex)?.second?.size ?: 1) - 1
                            if (focusState.currentItemIndex < max) {
                                focusState.currentItemIndex++
                                focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            }
                            true
                        }
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (SystemClock.elapsedRealtime() < suppressUntilMs) return@onPreviewKeyEvent true
                        if (!focusState.isNavFocused)
                            rows.getOrNull(focusState.currentRowIndex)
                                ?.second?.getOrNull(focusState.currentItemIndex)
                                ?.let { onMovieClick(it.id) }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        if (focusState.isNavFocused) { focusState.isNavFocused = false; rootFR.safe(); true } else false
                    }
                    else -> false
                }
            }
    ) {
        ArvioTopNav(
            activeTab     = activeTab,
            firstNavFR    = firstNavFR,
            focusState    = focusState,
            onSearchClick = onSearch,
            onMoviesTab   = onMoviesTab,
            onSeriesTab   = onSeriesTab,
            onWatchlist   = onWatchlist,
            onSettings    = onSettings,
            onNavExit     = { focusState.isNavFocused = false; rootFR.safe() },
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

@Composable
private fun LuminaLogo() {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = buildAnnotatedString {
            withStyle(SpanStyle(color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)) { append("LUMINA") }
            withStyle(SpanStyle(color = RED,   fontSize = 13.sp, fontWeight = FontWeight.Bold,  letterSpacing = 2.sp)) { append("STREAMS") }
        })
        Box(
            Modifier.width(56.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                .background(Brush.horizontalGradient(listOf(RED, RED.copy(alpha = 0f))))
        )
    }
}

@Composable
private fun ArvioTopNav(
    activeTab: String,
    firstNavFR: FocusRequester,
    focusState: HomeFocusState,
    onSearchClick: () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
    onNavExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember {
        val c = java.util.Calendar.getInstance()
        "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
    Row(
        modifier
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { onNavExit(); true } else false
            }
            .padding(start = 48.dp, end = 48.dp, top = 0.dp, bottom = 0.dp)
            .height(56.dp)
            .fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LuminaLogo()
        Spacer(Modifier.width(16.dp))
        NavPill(label = "Search",    icon = Icons.Default.Search,   isSelected = false,             index = 0, focusState = focusState, focusRequester = firstNavFR, onClick = onSearchClick)
        NavPill(label = "Movies",    icon = Icons.Default.Movie,    isSelected = activeTab=="סרטים", index = 1, focusState = focusState, onClick = onMoviesTab)
        NavPill(label = "TV Shows",  icon = Icons.Default.LiveTv,   isSelected = activeTab=="סדרות", index = 2, focusState = focusState, onClick = onSeriesTab)
        NavPill(label = "Watchlist", icon = Icons.Default.Bookmark, isSelected = false,             index = 3, focusState = focusState, onClick = onWatchlist)
        NavPill(label = "Settings",  icon = Icons.Default.Settings, isSelected = false,             index = 4, focusState = focusState, onClick = onSettings)
        Spacer(Modifier.weight(1f))
        Text(time, color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

// ─── NavPill ─────────────────────────────────────────────────────────────────
// אין border בכלל. הפס האדום מצויר ישירות מתחת ל-Text (לא מתחת לאייקון).
@Composable
private fun NavPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    index: Int,
    focusState: HomeFocusState,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val showBar   = focused || isSelected
    val iconRed   = focused || isSelected

    LaunchedEffect(focused) { if (focused) focusState.navItemIndex = index }

    val barAlpha     by animateFloatAsState(if (showBar) 1f else 0f,    tween(180), label = "bar")
    val contentAlpha by animateFloatAsState(if (showBar) 1f else 0.50f, tween(180), label = "ca")
    val density      =  LocalDensity.current

    // מיקום וגודל של ה-Text בתוך ה-Surface — כדי לצייר הפס בדיוק מתחתיו
    var textOffsetX by remember { mutableFloatStateOf(0f) }
    var textWidth   by remember { mutableFloatStateOf(0f) }

    val baseMod = Modifier
        .wrapContentWidth()
        .height(56.dp)
        .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
        .onFocusChanged { focused = it.isFocused }

    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = NAV_HOVER,
            pressedContainerColor = NAV_PRESS,
            contentColor          = Color.Transparent,
            focusedContentColor   = Color.Transparent
        ),
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        // ── אפס border לחלוטין ──
        border = ClickableSurfaceDefaults.border(
            border        = Border.None,
            focusedBorder = Border.None
        ),
        glow   = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = baseMod
    ) {
        // Box שמכיל את השורה + צייר פס מתחת לטקסט בדיוק
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .drawBehind {
                    // צייר רק אם יש alpha ויש מיקום ידוע
                    if (barAlpha > 0.01f && textWidth > 0f) {
                        val barH  = with(density) { 3.dp.toPx() }
                        val glowH = with(density) { 12.dp.toPx() }
                        val top   = size.height - barH
                        // glow רך מעל הפס
                        drawRect(
                            brush   = Brush.verticalGradient(
                                listOf(Color.Transparent, RED.copy(alpha = 0.5f * barAlpha)),
                                startY = top - glowH, endY = top
                            ),
                            topLeft = Offset(textOffsetX, top - glowH),
                            size    = Size(textWidth, glowH)
                        )
                        // הפס עצמו
                        drawRoundRect(
                            color        = RED.copy(alpha = barAlpha),
                            topLeft      = Offset(textOffsetX, top),
                            size         = Size(textWidth, barH),
                            cornerRadius = CornerRadius(barH / 2)
                        )
                    }
                }
        ) {
            Row(
                modifier              = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(16.dp)
                        .graphicsLayer { alpha = contentAlpha },
                    tint = if (iconRed) RED else WHITE
                )
                // ── Text: עוקב אחרי מיקומו כדי לדעת היכן לצייר את הפס ──
                Text(
                    text     = label,
                    color    = WHITE,
                    fontSize = 14.sp,
                    fontWeight    = if (showBar) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.2.sp,
                    softWrap      = false,
                    modifier      = Modifier
                        .graphicsLayer { alpha = contentAlpha }
                        .onGloballyPositioned { coords ->
                            textOffsetX = coords.positionInParent().x
                            textWidth   = coords.size.width.toFloat()
                        }
                )
            }
        }
    }
}

// ─── HomeRowsLayer ────────────────────────────────────────────────────────────
@Composable
private fun HomeRowsLayer(
    rows: List<Pair<String, List<Movie>>>,
    focusState: HomeFocusState,
    fastThreshMs: Long,
    onItemFocus: (Movie) -> Unit,
    onItemClick: (String) -> Unit
) {
    val config          = LocalConfiguration.current
    val rowsViewH       = (config.screenHeightDp * 0.40f).dp.coerceIn(250.dp, 340.dp)
    val currentRowIndex = focusState.currentRowIndex
    var isFast by remember { mutableStateOf(false) }
    LaunchedEffect(focusState.lastNavEventTime) {
        val anchor = focusState.lastNavEventTime; isFast = true
        delay(fastThreshMs)
        if (focusState.lastNavEventTime == anchor) isFast = false
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentRowIndex) {
        val target = currentRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        if (kotlin.math.abs(target - listState.firstVisibleItemIndex) <= 1) listState.animateScrollToItem(target)
        else listState.scrollToItem(target)
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(rowsViewH).clipToBounds()) {
            LazyColumn(
                state              = listState,
                contentPadding     = PaddingValues(bottom = rowsViewH),
                modifier           = Modifier.fillMaxSize().clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(rows, key = { _, p -> p.first }) { index, (title, movies) ->
                    val rowAlpha by animateFloatAsState(
                        targetValue   = if (index <= currentRowIndex) 1f else 0.18f,
                        animationSpec = tween(260), label = "rowAlpha"
                    )
                    Box(Modifier.fillMaxWidth().height(220.dp).clipToBounds().graphicsLayer { alpha = rowAlpha }) {
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

@Composable
private fun ArvioContentRow(
    title: String, movies: List<Movie>,
    isCurrentRow: Boolean, focusedItemIndex: Int,
    isFastScrolling: Boolean, onItemClick: (String) -> Unit,
    onItemFocused: (Movie, Int) -> Unit
) {
    if (movies.isEmpty()) return
    val rowState       = rememberLazyListState()
    val currentFocused by rememberUpdatedState(focusedItemIndex)
    val currentIsCur   by rememberUpdatedState(isCurrentRow)
    val cardW = 130.dp; val cardH = 190.dp

    LaunchedEffect(focusedItemIndex, isCurrentRow) {
        if (!isCurrentRow || focusedItemIndex < 0) return@LaunchedEffect
        val visible  = rowState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxFirst = (movies.size - visible).coerceAtLeast(0)
        val target   = focusedItemIndex.coerceAtMost(maxFirst)
        if (isFastScrolling) rowState.scrollToItem(target) else rowState.animateScrollToItem(target)
    }
    val rowFade  = remember { Animatable(1f) }
    var lastPage by remember { mutableIntStateOf(0) }
    val pageIdx  by remember { derivedStateOf { rowState.firstVisibleItemIndex / 5 } }
    LaunchedEffect(pageIdx, isCurrentRow, isFastScrolling) {
        if (!isCurrentRow || isFastScrolling) { rowFade.snapTo(1f); lastPage = pageIdx; return@LaunchedEffect }
        if (pageIdx != lastPage) { lastPage = pageIdx; rowFade.snapTo(0.75f); rowFade.animateTo(1f, tween(200)) }
    }
    Column {
        Text(title, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp, modifier = Modifier.padding(start = 52.dp, bottom = 8.dp))
        val fadeMod = if (rowFade.value < 0.999f) Modifier.graphicsLayer { alpha = rowFade.value } else Modifier
        LazyRow(
            modifier              = fadeMod,
            state                 = rowState,
            contentPadding        = PaddingValues(horizontal = 52.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { idx, movie ->
                ArvioCard(
                    movie            = movie,
                    cardW            = cardW,
                    cardH            = cardH,
                    isFocusedOverride = currentIsCur && idx == currentFocused,
                    onFocused        = { onItemFocused(movie, idx) },
                    onClick          = { onItemClick(movie.id) }
                )
            }
        }
    }
}

@Composable
fun ArvioCard(
    movie: Movie,
    cardW: androidx.compose.ui.unit.Dp = 130.dp,
    cardH: androidx.compose.ui.unit.Dp = 190.dp,
    modifier: Modifier = Modifier,
    isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val ctx     = LocalContext.current
    val density = LocalDensity.current
    val cardWPx = remember(cardW, density) { with(density) { (cardW.roundToPx() * 2).coerceIn(1, 1920) } }
    val cardHPx = remember(cardH, density) { with(density) { (cardH.roundToPx() * 2).coerceIn(1, 1080) } }
    var selfFocused by remember { mutableStateOf(false) }
    val focused      = isFocusedOverride || selfFocused
    val zoom by animateFloatAsState(
        if (focused) 1.07f else 1f,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "zoom"
    )

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Box(Modifier.width(cardW).height(cardH).graphicsLayer { scaleX = zoom; scaleY = zoom }.zIndex(if (focused) 8f else 0f)) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, WHITE.copy(alpha = 0.9f)), shape = RoundedCornerShape(8.dp))
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = Glow(elevationColor = WHITE.copy(alpha = 0.14f), elevation = 12.dp)
                ),
                modifier = Modifier.fillMaxSize()
                    .onFocusChanged { fs -> selfFocused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                AsyncImage(
                    model = remember(movie.posterUrl, movie.backdropUrl, cardWPx, cardHPx) {
                        ImageRequest.Builder(ctx)
                            .data(movie.posterUrl.ifBlank { movie.backdropUrl })
                            .size(cardWPx, cardHPx)
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
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(movie.title, color = if (focused) WHITE else DIM, fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(cardW))
        Text(if (movie.mediaType == "tv") "TV Show" else "Movie", color = DIM3, fontSize = 10.sp)
    }
}

@Composable
fun NfCard(movie: Movie, modifier: Modifier = Modifier, isFocusedOverride: Boolean = false,
    onFocused: () -> Unit = {}, onClick: () -> Unit
) = ArvioCard(movie = movie, modifier = modifier, isFocusedOverride = isFocusedOverride, onFocused = onFocused, onClick = onClick)

@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF252525), Color(0xFF111111)),
        start = Offset(p * 2000f - 1000f, 0f), end = Offset(p * 2000f, 500f))
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 52.dp, end = 52.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { Box(Modifier.width(100.dp).height(44.dp).clip(RoundedCornerShape(6.dp)).background(shimmer)) }
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.width(380.dp).height(52.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Box(Modifier.width(260.dp).height(52.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
            Spacer(Modifier.height(8.dp))
            repeat(3) { Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer)) }
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(8) { Box(Modifier.width(130.dp).height(190.dp).clip(RoundedCornerShape(8.dp)).background(shimmer)) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("⚠️", fontSize = 48.sp)
            Text(message, color = DIM, fontSize = 17.sp)
            Surface(
                onClick  = onRetry,
                colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp), RoundedCornerShape(10.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(elevationColor = RED.copy(alpha = 0.5f), elevation = 16.dp)),
                modifier = Modifier.height(52.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Try Again", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(msg: String, onRetry: () -> Unit) = HomeError(msg, onRetry)
@Composable fun LuminaSidebar(open: Boolean, activeTab: String, onClose: () -> Unit, onMoviesClick: () -> Unit, onSeriesClick: () -> Unit, onSearchClick: () -> Unit) {}
@Composable fun NfSidebar(open: Boolean, activeId: String, sidebarFirstFR: FocusRequester, onFocusLanded: () -> Unit, onClose: () -> Unit, onNavSelect: (String) -> Unit) {}
