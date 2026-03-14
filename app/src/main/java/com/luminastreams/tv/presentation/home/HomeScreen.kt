@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.foundation.ExperimentalTvFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

private val BG    = Color(0xFF000000)
private val CARD  = Color(0xFF0D0D0D)
private val RED   = Color(0xFFE50914)
private val RED2  = Color(0xFFB20710)
private val WHITE = Color(0xFFFFFFFF)
private val DIM   = Color(0xAAFFFFFF)
private val GOLD  = Color(0xFFFFC107)

private fun FocusRequester.safe() = try { requestFocus() } catch (_: Exception) {}

// ═══════════════════════════════════════════════════════════════════════════
// HomeScreen
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val navFR  = remember { FocusRequester() }
    var hero        by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen by remember { mutableStateOf(false) }

    // טאבים הוסרו — רק rows שמנוהלים דרך סיידבאר
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

    // סיידבאר נסגר אוטומטית כשהפוקוס יורד לשורות
    // sidebarOpen = false מוסן ב-onTopEscape של RowsOverlay
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            hero = rows.firstNotNullOfOrNull { it.second.firstOrNull() }
            delay(150); navFR.safe()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading     -> { HomeLoading(); return@Box }
            state.error != null -> { HomeError(state.error) { viewModel.selectTab(state.selectedTab) }; return@Box }
        }

        // z=0 backdrop
        CinematicBg(hero)

        // z=3 hero text — bottom-left of 65% height zone
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.50f)
                .fillMaxHeight(0.65f)
                .zIndex(3f),
            contentAlignment = Alignment.BottomStart
        ) {
            HeroInfo(
                movie  = hero,
                onPlay = { hero?.id?.let(onMovieClick) },
                modifier = Modifier.padding(start = 60.dp, bottom = 28.dp, end = 16.dp)
            )
        }

        // z=11 rows — bottom 40%
        // onTopEscape closes sidebar (sidebar should not be visible when in rows)
        RowsOverlay(
            rows        = rows,
            rowFRs      = rowFRs,
            rowCard0s   = rowCard0s,
            onFocus     = { hero = it },
            onTopEscape = { sidebarOpen = false; navFR.safe() },
            onClick     = onMovieClick,
            modifier    = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .zIndex(11f)
        )

        // z=10 TopNavBar only (no tabs strip)
        TopNavBar(
            rdStatus           = true,
            hasNotifications   = false,
            searchFR           = navFR,
            onVoiceSearchClick = {},
            onSearchClick      = { navController.navigate("search") },
            onProfileClick     = {},
            onDownPress        = { rowFRs.firstOrNull()?.safe() },
            onLeftEdge         = { sidebarOpen = true },
            modifier           = Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(10f)
        )

        // z=19+20 sidebar
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
            drawRect(Brush.verticalGradient(0.0f to BG.copy(0.75f), 0.14f to Color.Transparent))
        })
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HeroInfo — meta | TITLE | overview | [PLAY BIG] [DETAILS BIG] [+]
// כפתורי נגן+פרטים גדולים — height=72dp, איקון 30dp, טקסט 22sp
// ═══════════════════════════════════════════════════════════════════════════
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // meta row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (m.year > 0)
                    Text(m.year.toString(), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (m.rating > 0f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = GOLD)
                        Text("%.1f".format(m.rating), color = GOLD, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(WHITE.copy(0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(m.resolutionBadge, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // title
            Text(m.title, color = WHITE, fontSize = 52.sp, fontWeight = FontWeight.Black,
                lineHeight = 58.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)

            // overview
            Text(m.overview, color = DIM, fontSize = 14.sp, lineHeight = 22.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(8.dp))

            // ── action buttons — BIG (height=72dp, icon=30dp, text=22sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {

                // PLAY — filled red, very tall
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

                // DETAILS — ghost, same height
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

                // + icon only, same height
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
        Box(Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(BG.copy(0.0f), Color.Transparent))))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NfContentRow + NfCard
// NfCard: ZOOM ONLY (graphicsLayer scale) — אין border, אין glow לבן, רק scale smooth
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
        Text(title, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
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

    // smooth spring zoom — 1.00 → 1.16, no border, no glow
    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.16f else 1.00f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "zoom"
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
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),   // layout scale OFF, graphicsLayer handles it
            border   = ClickableSurfaceDefaults.border(Border.None, Border.None),  // אין מסגרת
            glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),        // אין glow
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
                // subtle bottom gradient
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.75f)))
                ))
                // resolution badge — top-right
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(movie.resolutionBadge, color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                // title shown on focus
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

// ═══════════════════════════════════════════════════════════════════════════
// Sidebar
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun SidebarDimOverlay(open: Boolean) {
    AnimatedVisibility(
        visible  = open,
        enter    = fadeIn(tween(220)),
        exit     = fadeOut(tween(220)),
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
        enter    = slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200)),
        exit     = slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { -it } + fadeOut(tween(180)),
        modifier = Modifier.fillMaxHeight().zIndex(20f)
    ) {
        LaunchedEffect(Unit) { delay(80); firstFR.safe() }
        Box(
            Modifier.fillMaxHeight().width(290.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF040404), Color(0xFF111111)))
                )
        ) {
            // red accent line on right edge
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent, RED.copy(0.70f), RED.copy(0.70f), Color.Transparent
                    )))
            )

            Column(Modifier.fillMaxSize().padding(vertical = 56.dp)) {
                // logo
                Text(
                    "LUMINA", color = RED, fontSize = 22.sp,
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
                    SI("בית",    Icons.Default.Home,   false,               onClose),
                    SI("סרטים", Icons.Default.Movie,  activeTab == "סרטים", onMoviesClick),
                    SI("סדרות",  Icons.Default.LiveTv, activeTab == "סדרות", onSeriesClick),
                    SI("חיפוש",  Icons.Default.Search, false,               onSearchClick)
                )
                val localFRs = remember(items.size) { List(items.size) { FocusRequester() } }
                items.forEachIndexed { i, item ->
                    SidebarRow(
                        label   = item.label,
                        icon    = item.icon,
                        active  = item.active,
                        fr      = if (i == 0) firstFR else localFRs[i],
                        prevFR  = if (i > 0) (if (i - 1 == 0) firstFR else localFRs[i - 1]) else null,
                        nextFR  = if (i < items.size - 1) localFRs[i + 1] else null,
                        onRight = onClose,
                        onClick = item.act
                    )
                }

                Spacer(Modifier.weight(1f))
                Text("ימין לתוכן ▶", color = WHITE.copy(0.18f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp))
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
    val bgColor by animateColorAsState(
        when { active -> RED.copy(0.18f); focused -> WHITE.copy(0.09f); else -> Color.Transparent },
        tween(130)
    )
    val textColor by animateColorAsState(if (active || focused) WHITE else DIM, tween(130))

    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = bgColor, focusedContainerColor = bgColor),
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
                    kev.key == Key.DirectionRight                 -> { onRight(); true }
                    kev.key == Key.Back || kev.key == Key.Escape  -> { onRight(); true }
                    else -> false
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // active indicator bar
            Box(Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp))
                .background(if (active) RED else Color.Transparent))
            Icon(icon, null,
                tint     = if (active) RED else textColor,
                modifier = Modifier.size(24.dp))
            Text(label,
                color      = textColor,
                fontSize   = 18.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
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
        Column(Modifier.fillMaxSize().padding(top = 140.dp, start = 56.dp, end = 56.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
