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
import androidx.compose.ui.draw.blur
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

// ─── Palette ──────────────────────────────────────────────────────────────────
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
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val navFR   = remember { FocusRequester() }
    val tab0FR  = remember { FocusRequester() }

    var hero        by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) { delay(120); navFR.safe() }
    }

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
        if (!state.isLoading) hero = rows.firstNotNullOfOrNull { it.second.firstOrNull() }
    }

    Box(Modifier.fillMaxSize()) {

        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }

        CinematicBg(hero)

        HeroInfo(
            movie  = hero,
            onPlay = { hero?.id?.let(onMovieClick) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight(0.62f)
                .fillMaxWidth(0.55f)
                .padding(start = 80.dp, top = 110.dp)
                .zIndex(3f)
        )

        RowsOverlay(
            rows      = rows,
            rowFRs    = rowFRs,
            rowCard0s = rowCard0s,
            onFocus   = { hero = it },
            onTopEscape = { tab0FR.safe() },
            onClick   = onMovieClick,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .zIndex(4f)
        )

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
                    .data(url)
                    .size(3840, 2160)
                    .scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to BG.copy(0.55f),
                    0.75f to BG.copy(0.90f),
                    1.0f to BG
                ))
                drawRect(Brush.horizontalGradient(
                    0.0f to BG.copy(0.80f),
                    0.55f to BG.copy(0.10f),
                    1.0f to Color.Transparent
                ))
                drawRect(Brush.verticalGradient(
                    0.0f to BG.copy(0.85f),
                    0.18f to Color.Transparent
                ))
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HeroInfo
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroInfo(movie: Movie?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState    = movie,
        transitionSpec = {
            (fadeIn(tween(450)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { 40 }) togetherWith
            (fadeOut(tween(280)) + slideOutVertically(tween(300)) { -20 })
        },
        label = "hero_info"
    ) { m ->
        if (m == null) { Box(modifier); return@AnimatedContent }
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                m.title,
                color = WHITE, fontSize = 58.sp, fontWeight = FontWeight.Black,
                lineHeight = 64.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(GREEN)
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("97% Match", color = BG, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                if (m.rating > 0f) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = GOLD)
                        Text("%.1f".format(m.rating), color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(GLASS)
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("4K HDR", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(m.overview, color = DIM, fontSize = 15.sp, lineHeight = 24.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onPlay,
                colors  = ClickableSurfaceDefaults.colors(
                    containerColor        = WHITE, contentColor          = BG,
                    focusedContainerColor = RED,   focusedContentColor   = WHITE
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp), RoundedCornerShape(50.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow  = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.7f), 28.dp)),
                modifier = Modifier.wrapContentWidth().height(56.dp)
            ) {
                Row(Modifier.padding(horizontal = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Text("▶  נגן", fontSize = 18.sp, fontWeight = FontWeight.Black)
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
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
        Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(BG.copy(0f), Color.Transparent))))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfContentRow
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
        Row(Modifier.padding(start = 56.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(RED))
            Spacer(Modifier.width(8.dp))
            Text(title, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    val scale by animateFloatAsState(
        if (focused) 1.10f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    )
    Box(
        Modifier.width(120.dp).height(180.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 6f else 0f)
    ) {
        Surface(
            onClick = onClick,
            colors  = ClickableSurfaceDefaults.colors(containerColor = CARD, focusedContainerColor = CARD),
            shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border  = ClickableSurfaceDefaults.border(
                border        = Border.None,
                focusedBorder = Border(BorderStroke(2.dp, WHITE), 8.dp)
            ),
            glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(GOLD.copy(0.45f), 18.dp)),
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
                    model = ImageRequest.Builder(ctx).data(movie.posterUrl)
                        .size(240, 360).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.88f)))
                ))
                if (movie.rating > 0f)
                    Box(Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(BG.copy(0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("%.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                AnimatedVisibility(
                    visible  = focused,
                    enter    = fadeIn(tween(140)) + slideInVertically(tween(170)) { it / 2 },
                    exit     = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(movie.title, color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
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
    Row(Modifier.fillMaxWidth().padding(start = 80.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, tab ->
            CinTab(
                label   = tab, selected = selectedTab == tab,
                fr      = if (i == 0) tab0FR else tabFRs[i],
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
            scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            modifier = Modifier
                .focusRequester(fr)
                .focusProperties { if (leftFR != null) left = leftFR; if (rightFR != null) right = rightFR }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (kev.key) { Key.DirectionUp -> { onUp(); true }; Key.DirectionDown -> { onDown(); true }; else -> false }
                }
        ) {
            Text(label, color = color, fontSize = 17.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp))
        }
        Box(Modifier.height(3.dp).width(lineW).clip(RoundedCornerShape(2.dp)).background(RED))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfSidebar
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun NfSidebar(
    open: Boolean, activeTab: String,
    onClose: () -> Unit, onMoviesClick: () -> Unit,
    onSeriesClick: () -> Unit, onSearchClick: () -> Unit
) {
    val firstFR = remember { FocusRequester() }
    AnimatedVisibility(visible = open, enter = fadeIn(tween(180)), exit = fadeOut(tween(180)), modifier = Modifier.zIndex(19f)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.70f)))
    }
    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) { -it },
        exit     = slideOutHorizontally(tween(190, easing = FastOutLinearInEasing)) { -it },
        modifier = Modifier.zIndex(20f)
    ) {
        LaunchedEffect(Unit) { delay(60); firstFR.safe() }
        Box(Modifier.fillMaxHeight().width(260.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF070707), Color(0xFF101010))))) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, RED.copy(0.55f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(vertical = 48.dp)) {
                Text("LUMINA", color = RED, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 36.dp))
                data class SI(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val active: Boolean, val act: () -> Unit)
                val items = listOf(
                    SI("בית",    Icons.Default.Home,      false,               onClose),
                    SI("סרטים", Icons.Default.PlayArrow, activeTab=="סרטים",  onMoviesClick),
                    SI("סדרות",  Icons.Default.LiveTv,    activeTab=="סדרות",  onSeriesClick),
                    SI("חיפוש",  Icons.Default.Search,    false,               onSearchClick)
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
                Text("▶ ימין לתוכן", color = WHITE.copy(0.22f), fontSize = 11.sp,
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
        when { active -> RED.copy(0.14f); focused -> WHITE.copy(0.07f); else -> Color.Transparent }, tween(130))
    val tc by animateColorAsState(if (active || focused) WHITE else DIM, tween(130))
    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)).focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { kev ->
                if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    kev.key == Key.DirectionRight                        -> { onRight(); true }
                    kev.key == Key.Back || kev.key == Key.Escape -> { onRight(); true }
                    else -> false
                }
            }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                .background(if (active) RED else Color.Transparent))
            Icon(icon, null, tint = if (active) RED else tc, modifier = Modifier.size(20.dp))
            Text(label, color = tc, fontSize = 16.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Loading + Error
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeLoading() {
    val inf = rememberInfiniteTransition(label = "sk")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), "sp")
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF161616), Color(0xFF2B2B2B), Color(0xFF161616)),
        start = Offset(p * 1800f - 900f, 0f), end = Offset(p * 1800f, 400f)
    )
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().padding(top = 130.dp, start = 56.dp, end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.fillMaxWidth(0.42f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.28f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.50f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(12.dp))
            repeat(3) {
                Box(Modifier.fillMaxWidth(0.12f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) {
                        Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("⚠️", fontSize = 48.sp)
            Text(message, color = DIM, fontSize = 16.sp)
            Surface(
                onClick = onRetry,
                colors  = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = RED2),
                shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp), RoundedCornerShape(8.dp)),
                scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow    = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 14.dp)),
                modifier = Modifier.height(48.dp).width(150.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("נסה שוב", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
