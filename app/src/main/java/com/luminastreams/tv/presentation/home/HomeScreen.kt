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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
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

// ─── Palette ──────────────────────────────────────────────────────────────────
private val C_BG    = Color(0xFF000000)
private val C_CARD  = Color(0xFF141414)
private val C_RED   = Color(0xFFE50914)
private val C_RED2  = Color(0xFFB20710)
private val C_WHITE = Color(0xFFFFFFFF)
private val C_DIM   = Color(0xAAFFFFFF)
private val C_GOLD  = Color(0xFFFFC107)
private val C_GREEN = Color(0xFF46D369)

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}

/**
 * A single content row — title + reusable.
 * Used by HomeScreen and DiscoveryScreen.
 */
@Composable
fun NfContentRow(
    title: String,
    movies: List<Movie>,
    rowFR: FocusRequester = remember { FocusRequester() },
    firstCardFR: FocusRequester = remember { FocusRequester() },
    onFocus: (Movie) -> Unit = {},
    onUpPress: (() -> Unit)? = null,
    onDownPress: (() -> Unit)? = null,
    onClick: (String) -> Unit
) {
    if (movies.isEmpty()) return
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Row title
        Row(
            modifier = Modifier.padding(start = 56.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp).height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(C_RED)
            )
            Spacer(Modifier.width(10.dp))
            Text(title, color = C_WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // ── Cards
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFR)
                .focusRestorer { firstCardFR }
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { idx, movie ->
                NfCard(
                    movie = movie,
                    modifier = if (idx == 0) Modifier.focusRequester(firstCardFR) else Modifier,
                    onFocused = {
                        onFocus(movie)
                        // auto-scroll so focused card is visible
                    },
                    onUpPress = onUpPress,
                    onDownPress = onDownPress,
                    onClick = { onClick(movie.id) }
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
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    )
    val shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp))

    Box(
        Modifier
            .width(130.dp).height(195.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 5f else 0f)
    ) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = C_CARD,
                focusedContainerColor = C_CARD
            ),
            shape = shape,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(BorderStroke(2.dp, C_WHITE), 8.dp)
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(C_RED.copy(0.5f), 16.dp)
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
                        kev.key == Key.DirectionUp && onUpPress != null -> { onUpPress(); true }
                        kev.key == Key.DirectionDown && onDownPress != null -> { onDownPress(); true }
                        else -> false
                    }
                }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(movie.posterUrl)
                        .size(260, 390).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, C_BG.copy(0.85f)))
                    )
                )
                if (movie.rating > 0f)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(C_BG.copy(0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("%.1f".format(movie.rating), color = C_GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                AnimatedVisibility(
                    visible = focused,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 2 },
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        movie.title, color = C_WHITE, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }
        }
    }
}

// ─── HomeScreen ───────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    // Focus requesters
    val navFR      = remember { FocusRequester() } // TopNavBar search icon
    val tab0FR     = remember { FocusRequester() } // first tab
    val row0FR     = remember { FocusRequester() } // first content row
    val row0card0  = remember { FocusRequester() } // first card of first row

    var heroMovie  by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen by remember { mutableStateOf(false) }

    // auto-focus nav on load
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) { delay(150); navFR.safe() }
    }

    // rows data — depends on selected tab
    val rows: List<Pair<String, List<Movie>>> = remember(state) {
        if (state.selectedTab == "סרטים") listOf(
            "טרנדינג" to state.movieTrending,
            "פרמיירה" to state.moviePremieres,
            "פעולה" to state.movieAction,
            "דרמה" to state.movieDrama,
            "מדע בדיוני" to state.movieScifi,
            "דירוג עליון" to state.movieTopRated
        ) else listOf(
            "טרנדינג" to state.tvTrending,
            "פרמיירה" to state.tvPremieres,
            "דרמה" to state.tvDrama,
            "פשע" to state.tvCrime,
            "מדע בדיוני" to state.tvScifi,
            "דירוג עליון" to state.tvTopRated
        )
    }

    // per-row FocusRequesters
    val rowFRs     = remember(rows.size) { List(rows.size) { FocusRequester() } }
    val rowCard0s  = remember(rows.size) { List(rows.size) { FocusRequester() } }

    Box(Modifier.fillMaxSize().background(C_BG)) {
        when {
            state.isLoading -> HomeLoading()
            state.error != null -> HomeError(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                // ── Background (blur backdrop of hero) ──────────────────────
                HeroBg(heroMovie)

                // ── Scrollable content column ────────────────────────────────
                Column(Modifier.fillMaxSize()) {

                    // ── TopNavBar (fixed height 80dp)
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

                    // ── Tabs row ─────────────────────────────────────────────
                    TabsRow(
                        selectedTab   = state.selectedTab,
                        tab0FR        = tab0FR,
                        onTabSelected = { viewModel.selectTab(it) },
                        onUpPress     = { navFR.safe() },
                        onDownPress   = { rowFRs.firstOrNull()?.safe() }
                    )

                    // ── Hero banner ──────────────────────────────────────────
                    HeroBanner(
                        movie = heroMovie,
                        onPlay = { heroMovie?.id?.let(onMovieClick) }
                    )

                    // ── Content rows ─────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Spacer(Modifier.height(0.dp))
                        rows.forEachIndexed { i, (title, movies) ->
                            NfContentRow(
                                title       = title,
                                movies      = movies,
                                rowFR       = rowFRs[i],
                                firstCardFR = rowCard0s[i],
                                onFocus     = { heroMovie = it },
                                onUpPress   = {
                                    if (i == 0) tab0FR.safe()
                                    else rowFRs[i - 1].safe()
                                },
                                onDownPress = {
                                    if (i < rows.size - 1) rowFRs[i + 1].safe()
                                },
                                onClick = onMovieClick
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }

        // ── Sidebar overlay ──────────────────────────────────────────────────
        NfSidebar(
            open           = sidebarOpen,
            activeTab      = state.selectedTab,
            onClose        = { sidebarOpen = false; navFR.safe() },
            onMoviesClick  = { sidebarOpen = false; viewModel.selectTab("סרטים"); navFR.safe() },
            onSeriesClick  = { sidebarOpen = false; viewModel.selectTab("סדרות"); navFR.safe() },
            onSearchClick  = { sidebarOpen = false; navController.navigate("search") }
        )
    }
}

// ─── HeroBg ───────────────────────────────────────────────────────────────────
@Composable
private fun HeroBg(movie: Movie?) {
    val ctx = LocalContext.current
    AnimatedContent(
        targetState    = movie?.backdropUrl ?: movie?.posterUrl,
        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(600)) },
        label          = "hero_bg"
    ) { url ->
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(url)
                .size(1920, 1080).scale(Scale.FILL)
                .memoryCachePolicy(CachePolicy.ENABLED).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp)
        )
    }
    Box(Modifier.fillMaxSize().background(C_BG.copy(0.80f)))
}

// ─── TabsRow ──────────────────────────────────────────────────────────────────
@Composable
private fun TabsRow(
    selectedTab: String,
    tab0FR: FocusRequester,
    onTabSelected: (String) -> Unit,
    onUpPress: () -> Unit,
    onDownPress: () -> Unit
) {
    val tabs = listOf("סרטים", "סדרות")
    val frs  = remember { List(tabs.size) { FocusRequester() } }

    // wire tab0FR → tabs[0]
    LaunchedEffect(Unit) { /* tab0FR IS tabs[0] FR below */ }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, tab ->
            TabItem(
                label      = tab,
                selected   = selectedTab == tab,
                fr         = if (i == 0) tab0FR else frs[i],
                leftFR     = if (i > 0) frs[i - 1] else null,
                rightFR    = if (i < tabs.size - 1) frs[i + 1] else null,
                onUpPress  = onUpPress,
                onDownPress= onDownPress,
                onClick    = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun TabItem(
    label: String, selected: Boolean,
    fr: FocusRequester,
    leftFR: FocusRequester?, rightFR: FocusRequester?,
    onUpPress: () -> Unit, onDownPress: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(if (selected || focused) C_WHITE else C_DIM, tween(150))
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
                .focusProperties {
                    if (leftFR  != null) left  = leftFR
                    if (rightFR != null) right = rightFR
                }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (kev.key) {
                        Key.DirectionUp   -> { onUpPress();   true }
                        Key.DirectionDown -> { onDownPress(); true }
                        else -> false
                    }
                }
        ) {
            Text(
                label, color = textColor,
                fontSize = 16.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
            )
        }
        Box(Modifier.height(3.dp).width(lineW).clip(RoundedCornerShape(2.dp)).background(C_RED))
    }
}

// ─── Hero Banner ──────────────────────────────────────────────────────────────
@Composable
private fun HeroBanner(movie: Movie?, onPlay: () -> Unit) {
    if (movie == null) { Spacer(Modifier.height(220.dp)); return }
    val ctx = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        // backdrop
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(movie.backdropUrl ?: movie.posterUrl)
                .size(1920, 480).scale(Scale.FILL)
                .memoryCachePolicy(CachePolicy.ENABLED).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // left gradient
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(C_BG.copy(0.95f), C_BG.copy(0f)))
        ))
        // bottom gradient
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, C_BG))
        ))

        // info
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp)
                .width(380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedContent(
                targetState    = movie.title,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label          = "hero_title"
            ) { t ->
                Text(t, color = C_WHITE, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp))
                        .background(C_GREEN)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) { Text("97% Match", color = C_BG, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                if (movie.rating > 0f)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(12.dp), tint = C_GOLD)
                        Text("%.1f".format(movie.rating), color = C_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
            }
            AnimatedContent(
                targetState    = movie.overview,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                label          = "hero_ov"
            ) { ov ->
                Text(ov, color = C_DIM, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─── Sidebar ──────────────────────────────────────────────────────────────────
@Composable
fun NfSidebar(
    open: Boolean,
    activeTab: String,
    onClose: () -> Unit,
    onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val firstFR = remember { FocusRequester() }

    AnimatedVisibility(
        visible  = open,
        enter    = fadeIn(tween(180)),
        exit     = fadeOut(tween(180)),
        modifier = Modifier.zIndex(19f)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.72f)),
            contentAlignment = Alignment.CenterStart
        ) {}
    }

    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) { -it },
        exit     = slideOutHorizontally(tween(190, easing = FastOutLinearInEasing)) { -it },
        modifier = Modifier.zIndex(20f)
    ) {
        LaunchedEffect(Unit) { delay(60); firstFR.safe() }

        Box(
            Modifier
                .fillMaxHeight()
                .width(260.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF080808), Color(0xFF111111))
                    )
                )
        ) {
            // right edge red line
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight().width(2.dp)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, C_RED.copy(0.6f), Color.Transparent)
                    ))
            )

            Column(
                Modifier.fillMaxSize().padding(vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "LUMINA",
                    color = C_RED, fontSize = 20.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 32.dp)
                )

                data class SItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val action: () -> Unit)
                val items = listOf(
                    SItem("בית",     Icons.Default.Home,     false,              onClose),
                    SItem("סרטים",  Icons.Default.PlayArrow, activeTab=="סרטים", onMoviesClick),
                    SItem("סדרות",   Icons.Default.Tv,        activeTab=="סדרות", onSeriesClick),
                    SItem("חיפוש",   Icons.Default.Search,   false,              onSearchClick)
                )
                items.forEachIndexed { i, item ->
                    SidebarRow(
                        label    = item.label,
                        icon     = item.icon,
                        active   = item.active,
                        fr       = if (i == 0) firstFR else remember { FocusRequester() },
                        onRight  = onClose,
                        onClick  = item.action
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "▶ ימין לתוכן",
                    color = C_WHITE.copy(0.25f), fontSize = 11.sp,
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
        when { active -> C_RED.copy(0.15f); focused -> C_WHITE.copy(0.07f); else -> Color.Transparent },
        tween(130)
    )
    val tc by animateColorAsState(if (active || focused) C_WHITE else C_DIM, tween(130))

    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg
        ),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { kev ->
                if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    kev.key == Key.DirectionRight -> { onRight(); true }
                    kev.key == Key.Back || kev.key == Key.Escape -> { onRight(); true }
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (active) Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(C_RED))
            else Spacer(Modifier.width(3.dp))
            Icon(icon, null, tint = if (active) C_RED else tc, modifier = Modifier.size(20.dp))
            Text(label, color = tc, fontSize = 16.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ─── Loading ──────────────────────────────────────────────────────────────────
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        "sp"
    )
    Box(Modifier.fillMaxSize().background(C_BG)) {
        Column(Modifier.fillMaxSize().padding(top = 120.dp, start = 56.dp, end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            repeat(3) {
                // row skeleton
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF1C1C1C), Color(0xFF2E2E2E), Color(0xFF1C1C1C)),
                        start = androidx.compose.ui.geometry.Offset(p * 1600f - 800f, 0f),
                        end   = androidx.compose.ui.geometry.Offset(p * 1600f, 200f)
                    ))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) {
                        Box(Modifier.width(130.dp).height(195.dp).clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(
                                listOf(Color(0xFF1C1C1C), Color(0xFF2A2A2A), Color(0xFF1C1C1C)),
                                start = androidx.compose.ui.geometry.Offset(p * 1600f - 800f, 0f),
                                end   = androidx.compose.ui.geometry.Offset(p * 1600f, 300f)
                            ))
                        )
                    }
                }
            }
        }
    }
}

// ─── Error ────────────────────────────────────────────────────────────────────
@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    val shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp))
    Box(Modifier.fillMaxSize().background(C_BG), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⚠️", fontSize = 48.sp)
            Text(message, color = C_DIM, fontSize = 17.sp)
            Surface(
                onClick = onRetry,
                colors  = ClickableSurfaceDefaults.colors(containerColor = C_RED, focusedContainerColor = C_RED2),
                shape   = shape,
                scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(C_RED.copy(0.5f), 14.dp)),
                modifier= Modifier.height(48.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("נסה שוב", color = C_WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── NfErrorScreen alias (used by other screens) ──────────────────────────────
@Composable
fun NfErrorScreen(message: String, onRetry: () -> Unit) = HomeError(message, onRetry)

// ─── NfLoadingSkeleton alias ──────────────────────────────────────────────────
@Composable
fun NfLoadingSkeleton() = HomeLoading()
