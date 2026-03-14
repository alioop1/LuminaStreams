@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.luminastreams.tv.ui.components.TopNavBar
import kotlinx.coroutines.delay

// ─── Palette
private val BG    = Color(0xFF000000)
private val CARD  = Color(0xFF0D0D0D)
private val RED   = Color(0xFFE50914)
private val RED2  = Color(0xFFB20710)
private val WHITE = Color(0xFFFFFFFF)
private val DIM   = Color(0xAAFFFFFF)
private val GOLD  = Color(0xFFFFC107)
private val GREEN = Color(0xFF46D369)
private val GLASS = Color(0x1AFFFFFF)

private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}

// ═══════════════════════════════════════════════════════════════════════════
// ROOT — HomeScreen
//
// Layout (z-order, bottom → top):
//  0  CinematicBg   — fullscreen backdrop + vignettes
//  3  HeroInfo      — top-left, 55% width, 68% height
//  4  RowsOverlay   — bottom 48%, lazy rows
// 10  TopBar+Tabs   — always on top of content
// 19  Sidebar dim   — dark overlay
// 20  Sidebar panel — slide-in from left
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val navFR  = remember { FocusRequester() }
    val tab0FR = remember { FocusRequester() }

    var hero        by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen by remember { mutableStateOf(false) }

    val rows: List<Pair<String, List<Movie>>> = remember(state.selectedTab, state) {
        if (state.selectedTab == "סרטים") listOf(
            "🔥 טרנדינג" to state.movieTrending,
            "✨ פרמיירה" to state.moviePremieres,
            "⚡ פעולה" to state.movieAction,
            "🎭 דרמה" to state.movieDrama,
            "🚀 מדע בדיוני" to state.movieScifi,
            "🏆 דירוג עליון" to state.movieTopRated
        ) else listOf(
            "🔥 טרנדינג" to state.tvTrending,
            "✨ פרמיירה" to state.tvPremieres,
            "🎭 דרמה" to state.tvDrama,
            "🔪 פשע" to state.tvCrime,
            "🚀 מדע בדיוני" to state.tvScifi,
            "🏆 דירוג עליון" to state.tvTopRated
        )
    }

    val rowFRs    = remember(rows.size) { List(rows.size) { FocusRequester() } }
    val rowCard0s = remember(rows.size) { List(rows.size) { FocusRequester() } }

    // boot focus + seed hero after load
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            hero = rows.firstNotNullOfOrNull { it.second.firstOrNull() }
            delay(150)
            navFR.safe()
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // IMPORTANT: Sidebar uses fillMaxSize + zIndex — must be OUTSIDE
    // the main content Box so it can truly cover everything
    // ───────────────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize()) {

        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }

        // z=0 — fullscreen backdrop
        CinematicBg(hero)

        // z=3 — hero info (left side, big on 4K)
        // fillMaxWidth(0.52f) → ~1990px on 3840 = half screen
        // padding top=130dp → sits below the topbar+tabs (~120dp)
        HeroInfo(
            movie    = hero,
            onPlay   = { hero?.id?.let(onMovieClick) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight(0.68f)
                .fillMaxWidth(0.52f)
                .padding(start = 72.dp, top = 130.dp, end = 24.dp)
                .zIndex(3f)
        )

        // z=4 — content rows (bottom 48% of screen)
        RowsOverlay(
            rows        = rows,
            rowFRs      = rowFRs,
            rowCard0s   = rowCard0s,
            onFocus     = { hero = it },
            onTopEscape = { tab0FR.safe() },
            onClick     = onMovieClick,
            modifier    = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .zIndex(4f)
        )

        // z=10 — TopNavBar + tabs (transparent, always visible)
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            TopNavBar(
                rdStatus           = true,
                hasNotifications   = false,
                searchFR           = navFR,
                onVoiceSearchClick = {},
                onSearchClick      = { navController.navigate("search") },
                onProfileClick     = {},
                onDownPress        = { tab0FR.safe() },
                onLeftEdge         = { sidebarOpen = true }
            )
            CinematicTabs(
                selectedTab   = state.selectedTab,
                tab0FR        = tab0FR,
                onTabSelected = { viewModel.selectTab(it) },
                onUpPress     = { navFR.safe() },
                onDownPress   = { rowFRs.firstOrNull()?.safe() }
            )
        }

        // z=19+20 — sidebar dim + panel
        // AnimatedVisibility with fillMaxSize works correctly inside a Box
        // because Box stacks children — NOT inside a Column
        SidebarDimOverlay(sidebarOpen)
        NfSidebar(
            open          = sidebarOpen,
            activeTab     = state.selectedTab,
            onClose       = { sidebarOpen = false; navFR.safe() },
            onMoviesClick = { sidebarOpen = false; viewModel.selectTab("סרטים"); navFR.safe() },
            onSeriesClick = { sidebarOpen = false; viewModel.selectTab("סדרות"); navFR.safe() },
            onSearchClick = { sidebarOpen = false; navController.navigate("search") }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CinematicBg
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun CinematicBg(movie: Movie?) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState    = movie?.backdropUrl ?: movie?.posterUrl,
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(700)) },
            label          = "cbg"
        ) { url ->
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(url).size(3840, 2160).scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Vignettes: bottom (for rows), left (for hero text), top (for bar)
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            // bottom — strong dark for rows legibility
            drawRect(Brush.verticalGradient(
                0.0f  to Color.Transparent,
                0.42f to BG.copy(0.50f),
                0.68f to BG.copy(0.88f),
                1.0f  to BG
            ))
            // left — gradient for hero text
            drawRect(Brush.horizontalGradient(
                0.0f  to BG.copy(0.82f),
                0.50f to BG.copy(0.15f),
                1.0f  to Color.Transparent
            ))
            // top — subtle shade for nav
            drawRect(Brush.verticalGradient(
                0.0f  to BG.copy(0.80f),
                0.16f to Color.Transparent
            ))
        })
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HeroInfo — 4K optimised
// Title: 72sp (~200px on 4K), fills left half of screen
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroInfo(movie: Movie?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState    = movie,
        transitionSpec = {
            (fadeIn(tween(500)) + slideInVertically(tween(550, easing = FastOutSlowInEasing)) { 60 }) togetherWith
            (fadeOut(tween(300)) + slideOutVertically(tween(320)) { -30 })
        },
        label = "hero_info"
    ) { m ->
        if (m == null) { Box(modifier); return@AnimatedContent }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // ── BIG title — 72sp on 4K = cinematic
            Text(
                m.title,
                color      = WHITE,
                fontSize   = 72.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 80.sp,
                maxLines   = 3,
                overflow   = TextOverflow.Ellipsis
            )

            // ── badge row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // match %
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(GREEN)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("97% Match", color = BG, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                // star + rating
                if (m.rating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Star, null, Modifier.size(17.dp), tint = GOLD)
                        Text("%.1f".format(m.rating), color = GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // 4K badge
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(GLASS)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("4K HDR", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── overview — bigger text for TV
            Text(
                m.overview,
                color      = DIM,
                fontSize   = 17.sp,
                lineHeight = 27.sp,
                maxLines   = 3,
                overflow   = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // ── play button — tall pill
            Surface(
                onClick  = onPlay,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = WHITE,
                    contentColor          = BG,
                    focusedContainerColor = RED,
                    focusedContentColor   = WHITE
                ),
                shape    = ClickableSurfaceDefaults.shape(
                    shape        = RoundedCornerShape(50.dp),
                    focusedShape = RoundedCornerShape(50.dp)
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.65f), 32.dp)),
                modifier = Modifier.height(62.dp).wrapContentWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp))
                    Text("נגן", fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RowsOverlay
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun RowsOverlay(
    rows: List<Pair<String, List<Movie>>>,
    rowFRs: List<FocusRequester>,
    rowCard0s: List<FocusRequester>,
    onFocus: (Movie) -> Unit,
    onTopEscape: () -> Unit,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(rows.size) { i ->
                val (title, movies) = rows[i]
                NfContentRow(
                    title       = title,
                    movies      = movies,
                    rowFR       = rowFRs[i],
                    firstCardFR = rowCard0s[i],
                    onFocus     = onFocus,
                    onUpPress   = { if (i == 0) onTopEscape() else rowFRs[i - 1].safe() },
                    onDownPress = if (i < rows.size - 1) { { rowFRs[i + 1].safe() } } else null,
                    onClick     = onClick
                )
            }
        }
        // feather top edge
        Box(
            Modifier.fillMaxWidth().height(36.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(BG.copy(0.0f), Color.Transparent)))
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfContentRow
// Cards: scale via graphicsLayer (NO layout impact) + spacing 16dp
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfContentRow(
    title: String,
    movies: List<Movie>,
    rowFR: FocusRequester       = remember { FocusRequester() },
    firstCardFR: FocusRequester = remember { FocusRequester() },
    onFocus: (Movie) -> Unit    = {},
    onUpPress: (() -> Unit)?    = null,
    onDownPress: (() -> Unit)?  = null,
    onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        // row title
        Row(
            Modifier.padding(start = 60.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(RED))
            Spacer(Modifier.width(10.dp))
            Text(title, color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        // cards row
        // vertical padding = half of scale overshoot so focused card doesn’t clip
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 60.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFR)
                .focusRestorer { firstCardFR }
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { idx, movie ->
                NfCard(
                    movie       = movie,
                    modifier    = if (idx == 0) Modifier.focusRequester(firstCardFR) else Modifier,
                    onFocused   = { onFocus(movie) },
                    onUpPress   = onUpPress,
                    onDownPress = onDownPress,
                    onClick     = { onClick(movie.id) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfCard
// KEY FIX: scale is applied via graphicsLayer (visual only, no layout shift)
// focusedScale on Surface is set to 1f so TV framework doesn’t also push cards
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onUpPress: (() -> Unit)? = null,
    onDownPress: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    // graphicsLayer scale = visual only, neighbours don’t move
    val scaleAnim by animateFloatAsState(
        targetValue = if (focused) 1.10f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "card_scale"
    )

    Box(
        Modifier
            .width(130.dp)
            .height(195.dp)
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim }
            .zIndex(if (focused) 8f else 0f)
    ) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(
                containerColor        = CARD,
                focusedContainerColor = CARD
            ),
            shape   = ClickableSurfaceDefaults.shape(
                shape        = RoundedCornerShape(10.dp),
                focusedShape = RoundedCornerShape(10.dp)
            ),
            // focusedScale = 1f because we handle scale in graphicsLayer above
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border  = ClickableSurfaceDefaults.border(
                border        = Border.None,
                // thin white outline — does NOT push neighbours
                focusedBorder = Border(BorderStroke(2.5.dp, WHITE), 10.dp)
            ),
            glow    = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(GOLD.copy(0.50f), 22.dp)
            ),
            modifier = modifier
                .fillMaxSize()
                .onFocusChanged { fs ->
                    focused = fs.isFocused
                    if (fs.isFocused) onFocused()
                }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        kev.key == Key.DirectionUp   && onUpPress   != null -> { onUpPress();   true }
                        kev.key == Key.DirectionDown && onDownPress != null -> { onDownPress(); true }
                        else -> false
                    }
                }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(movie.posterUrl)
                        .size(260, 390)
                        .scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // bottom gradient
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.85f)))
                    )
                )
                // rating chip
                if (movie.rating > 0f)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BG.copy(0.72f))
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "%.1f".format(movie.rating),
                            color = GOLD, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                // title reveal on focus
                AnimatedVisibility(
                    visible  = focused,
                    enter    = fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 2 },
                    exit     = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        movie.title, color = WHITE,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CinematicTabs
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun CinematicTabs(
    selectedTab: String, tab0FR: FocusRequester,
    onTabSelected: (String) -> Unit, onUpPress: () -> Unit, onDownPress: () -> Unit
) {
    val tabs   = listOf("סרטים", "סדרות")
    val tabFRs = remember { List(tabs.size) { FocusRequester() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 72.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, tab ->
            CinTab(
                label    = tab, selected = selectedTab == tab,
                fr       = if (i == 0) tab0FR else tabFRs[i],
                leftFR   = if (i > 0) (if (i - 1 == 0) tab0FR else tabFRs[i - 1]) else null,
                rightFR  = if (i < tabs.size - 1) tabFRs[i + 1] else null,
                onUp     = onUpPress, onDown = onDownPress,
                onClick  = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun CinTab(
    label: String, selected: Boolean, fr: FocusRequester,
    leftFR: FocusRequester?, rightFR: FocusRequester?,
    onUp: () -> Unit, onDown: () -> Unit, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (selected || focused) WHITE else DIM, tween(140))
    val lineW by animateDpAsState(if (selected) 24.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .focusRequester(fr)
                .focusProperties { if (leftFR != null) left = leftFR; if (rightFR != null) right = rightFR }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (kev.key) {
                        Key.DirectionUp   -> { onUp();   true }
                        Key.DirectionDown -> { onDown(); true }
                        else              -> false
                    }
                }
        ) {
            Text(
                label, color = color, fontSize = 18.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
            )
        }
        Box(Modifier.height(3.dp).width(lineW).clip(RoundedCornerShape(2.dp)).background(RED))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Sidebar dim overlay (separate composable so it doesn’t block focus)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun SidebarDimOverlay(open: Boolean) {
    AnimatedVisibility(
        visible  = open,
        enter    = fadeIn(tween(200)),
        exit     = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize().zIndex(19f)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.72f)))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfSidebar — panel only (no dim here)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfSidebar(
    open: Boolean, activeTab: String,
    onClose: () -> Unit, onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit, onSearchClick: () -> Unit
) {
    val firstFR = remember { FocusRequester() }

    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it },
        exit     = slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { -it },
        modifier = Modifier.fillMaxHeight().zIndex(20f)
    ) {
        LaunchedEffect(Unit) { delay(80); firstFR.safe() }

        Box(
            Modifier
                .fillMaxHeight()
                .width(270.dp)
                .background(Brush.horizontalGradient(
                    listOf(Color(0xFF060606), Color(0xFF0F0F0F))
                ))
        ) {
            // red right edge accent
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, RED.copy(0.60f), Color.Transparent)
                    ))
            )

            Column(Modifier.fillMaxSize().padding(vertical = 50.dp)) {
                // logo
                Text(
                    "LUMINA",
                    color = RED, fontSize = 21.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 40.dp)
                )

                data class SI(
                    val label: String,
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val active: Boolean,
                    val act: () -> Unit
                )
                val items = listOf(
                    SI("בית",    Icons.Default.Home,    false,               onClose),
                    SI("סרטים", Icons.Default.Movie,   activeTab=="סרטים",  onMoviesClick),
                    SI("סדרות",  Icons.Default.LiveTv,  activeTab=="סדרות",  onSeriesClick),
                    SI("חיפוש",  Icons.Default.Search,  false,               onSearchClick)
                )
                val localFRs = remember(items.size) { List(items.size) { FocusRequester() } }
                items.forEachIndexed { i, item ->
                    SidebarRow(
                        label   = item.label,
                        icon    = item.icon,
                        active  = item.active,
                        fr      = if (i == 0) firstFR else localFRs[i],
                        onRight = onClose,
                        onClick = item.act
                    )
                }

                Spacer(Modifier.weight(1f))
                Text(
                    "▶  ימין לתוכן",
                    color = WHITE.copy(0.20f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 28.dp)
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
    onRight: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { active -> RED.copy(0.16f); focused -> WHITE.copy(0.08f); else -> Color.Transparent },
        tween(130)
    )
    val tc by animateColorAsState(if (active || focused) WHITE else DIM, tween(130))

    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { kev ->
                if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    kev.key == Key.DirectionRight              -> { onRight(); true }
                    kev.key == Key.Back || kev.key == Key.Escape -> { onRight(); true }
                    else -> false
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.width(3.dp).height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) RED else Color.Transparent)
            )
            Icon(icon, null, tint = if (active) RED else tc, modifier = Modifier.size(22.dp))
            Text(
                label, color = tc, fontSize = 17.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Loading + Error
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF161616), Color(0xFF2B2B2B), Color(0xFF161616)),
        start = Offset(p * 1800f - 900f, 0f),
        end   = Offset(p * 1800f, 400f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().padding(top = 140.dp, start = 60.dp, end = 60.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(Modifier.fillMaxWidth(0.40f).height(32.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.24f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.48f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(14.dp))
            repeat(3) {
                Box(Modifier.fillMaxWidth(0.10f).height(16.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(7) {
                        Box(Modifier.width(130.dp).height(195.dp).clip(RoundedCornerShape(10.dp)).background(shimmer))
                    }
                }
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

@Composable fun NfLoadingSkeleton() = HomeLoading()
@Composable fun NfErrorScreen(message: String, onRetry: () -> Unit) = HomeError(message, onRetry)

// Legacy overload for DiscoveryScreen
@Composable
fun NfSidebar(
    open: Boolean, activeId: String,
    sidebarFirstFR: FocusRequester,
    onFocusLanded: () -> Unit,
    onClose: () -> Unit,
    onNavSelect: (String) -> Unit
) = NfSidebar(
    open          = open,
    activeTab     = activeId,
    onClose       = onClose,
    onMoviesClick = { onNavSelect("סרטים") },
    onSeriesClick = { onNavSelect("סדרות") },
    onSearchClick = { onNavSelect("search") }
)
