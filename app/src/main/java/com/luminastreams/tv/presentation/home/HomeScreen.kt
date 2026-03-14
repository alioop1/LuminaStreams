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

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            hero = rows.firstNotNullOfOrNull { it.second.firstOrNull() }
            delay(150)
            navFR.safe()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }

        CinematicBg(hero)

        // Hero box — 65% height, text anchored bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.48f)
                .fillMaxHeight(0.65f)
                .zIndex(3f),
            contentAlignment = Alignment.BottomStart
        ) {
            HeroInfo(
                movie    = hero,
                onPlay   = { hero?.id?.let(onMovieClick) },
                modifier = Modifier.padding(start = 60.dp, bottom = 24.dp, end = 16.dp)
            )
        }

        // Rows — bottom 40%
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
                .fillMaxHeight(0.40f)
                .zIndex(11f)
        )

        // TopBar + Tabs
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(10f)
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

// ───────────────────────────────────────────────────────────────────────────
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
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(
                0.0f to Color.Transparent, 0.38f to BG.copy(0.30f),
                0.60f to BG.copy(0.75f),   0.80f to BG.copy(0.95f), 1.0f to BG
            ))
            drawRect(Brush.horizontalGradient(
                0.0f to BG.copy(0.88f), 0.45f to BG.copy(0.30f),
                0.75f to BG.copy(0.05f), 1.0f to Color.Transparent
            ))
            drawRect(Brush.verticalGradient(
                0.0f to BG.copy(0.75f), 0.14f to Color.Transparent
            ))
        })
    }
}

// ───────────────────────────────────────────────────────────────────────────
// HeroInfo — Netflix order:
// year | ★ rating | badge  →  TITLE  →  overview  →  [Play] [Details] [+]
// Uses m.year (new field) + m.resolutionBadge (existing field)
// ───────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroInfo(movie: Movie?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState    = movie,
        transitionSpec = {
            (fadeIn(tween(500)) + slideInVertically(tween(550, easing = FastOutSlowInEasing)) { 50 }) togetherWith
            (fadeOut(tween(280)) + slideOutVertically(tween(300)) { -30 })
        },
        label = "hero"
    ) { m ->
        if (m == null) { Box(modifier); return@AnimatedContent }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // ── meta row: year | star rating | resolution badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (m.year > 0) {
                    Text(m.year.toString(), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (m.rating > 0f) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = GOLD)
                        Text("%.1f".format(m.rating), color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp)).background(WHITE.copy(0.15f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(m.resolutionBadge, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── title
            Text(
                m.title,
                color = WHITE, fontSize = 52.sp, fontWeight = FontWeight.Black,
                lineHeight = 58.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )

            // ── overview
            Text(
                m.overview,
                color = DIM, fontSize = 14.sp, lineHeight = 22.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // ── action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play
                Surface(
                    onClick = onPlay,
                    colors  = ClickableSurfaceDefaults.colors(
                        containerColor = RED, contentColor = WHITE,
                        focusedContainerColor = RED2, focusedContentColor = WHITE
                    ),
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.70f), 28.dp)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp))
                        Text("נגן", fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
                // Details
                Surface(
                    onClick = {},
                    colors  = ClickableSurfaceDefaults.colors(
                        containerColor = WHITE.copy(0.14f), contentColor = WHITE,
                        focusedContainerColor = WHITE.copy(0.26f), focusedContentColor = WHITE
                    ),
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                        Text("פרטים", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // + wishlist
                Surface(
                    onClick = {},
                    colors  = ClickableSurfaceDefaults.colors(
                        containerColor = WHITE.copy(0.14f), contentColor = WHITE,
                        focusedContainerColor = WHITE.copy(0.26f), focusedContentColor = WHITE
                    ),
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
        Box(
            Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(BG.copy(0.0f), Color.Transparent)))
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
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
        Row(Modifier.padding(start = 56.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(rowFR).focusRestorer { firstCardFR }
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
    val scaleAnim by animateFloatAsState(
        if (focused) 1.12f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "cs"
    )
    Box(
        Modifier.width(110.dp).height(165.dp)
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim }
            .zIndex(if (focused) 8f else 0f)
    ) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(containerColor = CARD, focusedContainerColor = CARD),
            shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp), RoundedCornerShape(6.dp)),
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border  = ClickableSurfaceDefaults.border(
                border        = Border.None,
                focusedBorder = Border(BorderStroke(2.dp, WHITE), 6.dp)
            ),
            glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WHITE.copy(0.35f), 18.dp)),
            modifier = modifier.fillMaxSize()
                .onFocusChanged { fs -> focused = fs.isFocused; if (fs.isFocused) onFocused() }
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
                        .data(movie.posterUrl).size(220, 330).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.80f)))
                ))
                // resolution badge — uses existing resolutionBadge field
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(movie.resolutionBadge, color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                AnimatedVisibility(
                    visible  = focused,
                    enter    = fadeIn(tween(120)) + slideInVertically(tween(140)) { it / 2 },
                    exit     = fadeOut(tween(90)),
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

// ───────────────────────────────────────────────────────────────────────────
@Composable
private fun CinematicTabs(
    selectedTab: String, tab0FR: FocusRequester,
    onTabSelected: (String) -> Unit, onUpPress: () -> Unit, onDownPress: () -> Unit
) {
    val tabs   = listOf("סרטים", "סדרות")
    val tabFRs = remember { List(tabs.size) { FocusRequester() } }
    Row(
        Modifier.fillMaxWidth().padding(start = 60.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, tab ->
            CinTab(
                label = tab, selected = selectedTab == tab,
                fr = if (i == 0) tab0FR else tabFRs[i],
                leftFR  = if (i > 0) (if (i - 1 == 0) tab0FR else tabFRs[i - 1]) else null,
                rightFR = if (i < tabs.size - 1) tabFRs[i + 1] else null,
                onUp = onUpPress, onDown = onDownPress,
                onClick = { onTabSelected(tab) }
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
    val lineW by animateDpAsState(if (selected) 22.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .focusRequester(fr)
                .focusProperties { if (leftFR != null) left = leftFR; if (rightFR != null) right = rightFR }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (kev.key) {
                        Key.DirectionUp   -> { onUp();   true }
                        Key.DirectionDown -> { onDown(); true }
                        else -> false
                    }
                }
        ) {
            Text(
                label, color = color, fontSize = 16.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
        Box(Modifier.height(3.dp).width(lineW).clip(RoundedCornerShape(2.dp)).background(RED))
    }
}

// ───────────────────────────────────────────────────────────────────────────
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
            Modifier.fillMaxHeight().width(270.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF060606), Color(0xFF0F0F0F))))
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, RED.copy(0.60f), Color.Transparent)))
            )
            Column(Modifier.fillMaxSize().padding(vertical = 50.dp)) {
                Text(
                    "LUMINA", color = RED, fontSize = 21.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 40.dp)
                )
                data class SI(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val act: () -> Unit)
                val items = listOf(
                    SI("בית",    Icons.Default.Home,   false,              onClose),
                    SI("סרטים", Icons.Default.Movie,  activeTab=="סרטים", onMoviesClick),
                    SI("סדרות",  Icons.Default.LiveTv, activeTab=="סדרות", onSeriesClick),
                    SI("חיפוש",  Icons.Default.Search, false,              onSearchClick)
                )
                val localFRs = remember(items.size) { List(items.size) { FocusRequester() } }
                items.forEachIndexed { i, item ->
                    SidebarRow(
                        label = item.label, icon = item.icon, active = item.active,
                        fr = if (i == 0) firstFR else localFRs[i],
                        onRight = onClose, onClick = item.act
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("ימין לתוכן ▶", color = WHITE.copy(0.20f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 28.dp))
            }
        }
    }
}

@Composable
private fun SidebarRow(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean, fr: FocusRequester, onRight: () -> Unit, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { active -> RED.copy(0.16f); focused -> WHITE.copy(0.08f); else -> Color.Transparent }, tween(130))
    val tc by animateColorAsState(if (active || focused) WHITE else DIM, tween(130))
    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp)).focusRequester(fr)
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
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (active) RED else Color.Transparent))
            Icon(icon, null, tint = if (active) RED else tc, modifier = Modifier.size(22.dp))
            Text(label, color = tc, fontSize = 17.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), "sp")
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF161616), Color(0xFF2B2B2B), Color(0xFF161616)),
        start = Offset(p * 1800f - 900f, 0f), end = Offset(p * 1800f, 400f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(top = 140.dp, start = 56.dp, end = 56.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.fillMaxWidth(0.38f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.44f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.50f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.50f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(90.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(Modifier.width(90.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            }
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Box(Modifier.fillMaxWidth(0.10f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
