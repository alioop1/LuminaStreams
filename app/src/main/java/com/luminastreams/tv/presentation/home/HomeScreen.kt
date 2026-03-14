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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

// ── Palette ─────────────────────────────────────────────────────────────────
private val BK  = Color(0xFF000000)
private val RD  = Color(0xFFE50914)
private val DRD = Color(0xFFB20710)
private val WH  = Color(0xFFFFFFFF)
private val DM  = Color(0x99FFFFFF)
private val GL  = Color(0x22FFFFFF)
private val MGN = Color(0xFF46D369)
private val DG  = Color(0xFF141414)

private val IconFilm: ImageVector
    get() = ImageVector.Builder("Film", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(18f, 4f); lineTo(6f, 4f)
            curveTo(4.9f, 4f, 4f, 4.9f, 4f, 6f); lineTo(4f, 18f)
            curveTo(4f, 19.1f, 4.9f, 20f, 6f, 20f); lineTo(18f, 20f)
            curveTo(19.1f, 20f, 20f, 19.1f, 20f, 18f); lineTo(20f, 6f)
            curveTo(20f, 4.9f, 19.1f, 4f, 18f, 4f); close()
            moveTo(10f, 14.5f); lineTo(10f, 9.5f); lineTo(15f, 12f); lineTo(10f, 14.5f); close()
        }
    }.build()

private val IconTv: ImageVector
    get() = ImageVector.Builder("Tv", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(21f, 3f); lineTo(3f, 3f)
            curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f); lineTo(1f, 17f)
            curveTo(1f, 18.1f, 1.9f, 19f, 3f, 19f); lineTo(10f, 19f); lineTo(10f, 21f)
            lineTo(14f, 21f); lineTo(14f, 19f); lineTo(21f, 19f)
            curveTo(22.1f, 19f, 23f, 18.1f, 23f, 17f); lineTo(23f, 5f)
            curveTo(22.1f, 3f, 21.1f, 3f, 21f, 3f); close()
            moveTo(21f, 17f); lineTo(3f, 17f); lineTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 17f); close()
        }
    }.build()

private data class NavItem(val id: String, val label: String, val icon: ImageVector)
private val navItems = listOf(
    NavItem("home",       "בית",      Icons.Default.Home),
    NavItem("movies",    "סרטים",   IconFilm),
    NavItem("series",    "סדרות",    IconTv),
    NavItem("search",    "חיפוש",    Icons.Default.Search),
    NavItem("favorites", "מועדפים", Icons.Default.Favorite),
    NavItem("settings",  "הגדרות",  Icons.Default.Settings)
)

private enum class TileSize { LARGE, MEDIUM, SMALL }
private fun tilePattern(index: Int): TileSize = when (index % 7) {
    0    -> TileSize.LARGE
    3    -> TileSize.MEDIUM
    else -> TileSize.SMALL
}

private fun buildPool(state: HomeState): List<Movie> {
    val src = if (state.selectedTab == "סרטים") {
        (state.movieTrending + state.movieAction + state.movieTopRated +
         state.moviePremieres + state.movieScifi + state.movieDrama).distinctBy { it.id }
    } else {
        (state.tvTrending + state.tvDrama + state.tvCrime +
         state.tvTopRated + state.tvPremieres + state.tvScifi).distinctBy { it.id }
    }
    return src.take(40)
}

private fun FocusRequester.safeRequest() { try { requestFocus() } catch (_: Exception) {} }

// ══════════════════════════════════════════════════════════════════════════════
// HomeScreen — Living Canvas
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val navBarFR    = remember { FocusRequester() }
    val firstTileFR = remember { FocusRequester() }

    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    var sidebarOpen  by remember { mutableStateOf(false) }
    var activeNavId  by remember { mutableStateOf("home") }

    val pool = remember(state) { buildPool(state) }

    var breathePulse by remember { mutableStateOf(0) }
    LaunchedEffect(pool) { while (true) { delay(2_800); breathePulse++ } }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) { delay(200); navBarFR.safeRequest() } }

    val heroMovie = focusedMovie ?: pool.firstOrNull()

    Box(Modifier.fillMaxSize().background(BK)) {
        when {
            state.isLoading     -> NfLoadingSkeleton()
            state.error != null -> NfErrorScreen(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                LivingMosaicBackground(pool = pool, focusedMovie = focusedMovie)

                AnimatedVisibility(
                    visible  = focusedMovie != null,
                    enter    = fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -80 },
                    exit     = fadeOut(tween(220)) + slideOutHorizontally(tween(260)) { -60 },
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(5f)
                ) {
                    heroMovie?.let { m -> HeroInfoPanel(movie = m, onPlayClick = { onMovieClick(m.id) }) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (focusedMovie != null) 0.52f else 1f)
                        .align(Alignment.CenterEnd)
                        .padding(top = 100.dp)
                ) {
                    LivingMosaicGrid(
                        pool         = pool,
                        breathePulse = breathePulse,
                        firstTileFR  = firstTileFR,
                        onFocus      = { movie -> focusedMovie = movie },
                        onUpFromGrid = { navBarFR.safeRequest() },
                        onLeftEdge   = { if (focusedMovie == null) sidebarOpen = true },
                        onClick      = onMovieClick
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(10f)) {
                    LivingTopNav(
                        state         = state,
                        navBarFR      = navBarFR,
                        firstTileFR   = firstTileFR,
                        onTabSelect   = { viewModel.selectTab(it) },
                        onSearchClick = { navController.navigate("search") },
                        onOpenSidebar = { sidebarOpen = true }
                    )
                }
            }
        }

        NfSidebar(
            open           = sidebarOpen,
            activeId       = activeNavId,
            sidebarFirstFR = remember { FocusRequester() },
            onFocusLanded  = {},
            onClose        = { sidebarOpen = false; navBarFR.safeRequest() },
            onNavSelect    = { id ->
                activeNavId = id; sidebarOpen = false
                when (id) {
                    "movies" -> viewModel.selectTab("סרטים")
                    "series" -> viewModel.selectTab("סדרות")
                    "search" -> navController.navigate("search")
                }
                navBarFR.safeRequest()
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun LivingMosaicBackground(pool: List<Movie>, focusedMovie: Movie?) {
    val context = LocalContext.current
    val bgMovie = focusedMovie ?: pool.firstOrNull()
    val overlayAlpha by animateFloatAsState(if (focusedMovie != null) 0.78f else 0.55f, tween(400))

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState    = bgMovie?.backdropUrl ?: bgMovie?.posterUrl,
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(700)) },
            label          = "canvas_bg"
        ) { url ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(url)
                    .size(1920, 1080).scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(if (focusedMovie != null) 0.dp else 32.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(BK.copy(alpha = overlayAlpha)))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun LivingMosaicGrid(
    pool: List<Movie>,
    breathePulse: Int,
    firstTileFR: FocusRequester,
    onFocus: (Movie) -> Unit,
    onUpFromGrid: () -> Unit,
    onLeftEdge: () -> Unit,
    onClick: (String) -> Unit
) {
    val breatheTargets = remember(breathePulse) {
        if (pool.isEmpty()) emptySet()
        else (0 until 6).map { pool.indices.random() }.toSet()
    }

    LazyVerticalGrid(
        columns               = GridCells.Fixed(5),
        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp),
        modifier              = Modifier.fillMaxSize().focusRestorer()
    ) {
        itemsIndexed(pool, key = { _, m -> m.id }) { index, movie ->
            val tileH = when (tilePattern(index)) {
                TileSize.LARGE  -> 240.dp
                TileSize.MEDIUM -> 180.dp
                TileSize.SMALL  -> 140.dp
            }
            val isBreathing = index in breatheTargets
            val breatheAlpha by animateFloatAsState(
                if (isBreathing) 0.55f else 1f, tween(1400, easing = FastOutSlowInEasing)
            )
            LiveTile(
                movie       = movie,
                tileHeight  = tileH,
                tileAlpha   = breatheAlpha,
                isFirst     = index == 0,
                firstTileFR = firstTileFR,
                onFocus     = { onFocus(movie) },
                onUpFromRow = if (index < 5) onUpFromGrid else null,
                onLeftEdge  = if (index % 5 == 0) onLeftEdge else null,
                onClick     = { onClick(movie.id) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun TileContent(movie: Movie, isFocused: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(movie.posterUrl).size(300, 450).scale(Scale.FILL)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(200).build(),
            contentDescription = movie.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.75f)))
            )
        )
        AnimatedVisibility(
            visible  = isFocused,
            enter    = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit     = fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                movie.title, color = WH, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LiveTile(
    movie: Movie,
    tileHeight: androidx.compose.ui.unit.Dp,
    tileAlpha: Float,
    isFirst: Boolean,
    firstTileFR: FocusRequester,
    onFocus: () -> Unit,
    onUpFromRow: (() -> Unit)?,
    onLeftEdge: (() -> Unit)?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(220, easing = FastOutSlowInEasing))
    val alphaAnim by animateFloatAsState(if (isFocused) 1f else tileAlpha, tween(300))
    // ClickableSurfaceDefaults.shape() is @Composable — must be called here inside @Composable
    val tileShape = ClickableSurfaceDefaults.shape(
        shape        = RoundedCornerShape(10.dp),
        focusedShape = RoundedCornerShape(10.dp)
    )

    Box(
        modifier = Modifier
            .height(tileHeight)
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim; alpha = alphaAnim }
            .zIndex(if (isFocused) 10f else 0f)
    ) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = DG,
                focusedContainerColor = DG
            ),
            shape  = tileShape,
            scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                border        = Border.None,
                focusedBorder = Border(BorderStroke(3.dp, WH), 10.dp)
            ),
            glow   = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WH.copy(0.5f), 18.dp)),
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFirst) Modifier.focusRequester(firstTileFR) else Modifier)
                .onFocusChanged { fs -> isFocused = fs.isFocused; if (fs.isFocused) onFocus() }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        kev.key == Key.DirectionUp   && onUpFromRow != null -> { onUpFromRow(); true }
                        kev.key == Key.DirectionLeft && onLeftEdge  != null -> { onLeftEdge();  true }
                        else -> false
                    }
                }
        ) {
            TileContent(movie = movie, isFocused = isFocused)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Hero Info Panel
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HeroInfoPanel(movie: Movie, onPlayClick: () -> Unit) {
    val playShape = ClickableSurfaceDefaults.shape(shape = CircleShape, focusedShape = CircleShape)

    Box(
        modifier = Modifier
            .width(520.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(BK.copy(0.97f), BK.copy(0.0f))))
            .padding(start = 64.dp, end = 32.dp)
            .zIndex(5f),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedContent(
                targetState    = movie.posterUrl,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label          = "hero_poster"
            ) { url ->
                AsyncImage(
                    model              = url,
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .width(160.dp).aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            AnimatedContent(
                targetState    = movie.title,
                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
                label          = "hero_title"
            ) { t ->
                Text(
                    t, color = WH, fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 42.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(MGN)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("97% Match", color = BK, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val r = movie.rating
                if (r > 0f)
                    Text("%.1f ★".format(r), color = Color(0xFFFFC107), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            AnimatedContent(
                targetState    = movie.overview,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label          = "hero_ov"
            ) { ov ->
                Text(
                    ov, color = DM, fontSize = 14.sp,
                    lineHeight = 22.sp, maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                onClick  = onPlayClick,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = WH,
                    contentColor          = BK,
                    focusedContainerColor = RD,
                    focusedContentColor   = WH
                ),
                shape    = playShape,
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RD.copy(0.6f), 20.dp)),
                modifier = Modifier.wrapContentWidth().height(52.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 28.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Text("נגן עכשיו", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Top Nav
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun LivingTopNav(
    state: HomeState,
    navBarFR: FocusRequester,
    firstTileFR: FocusRequester,
    onTabSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    onOpenSidebar: () -> Unit
) {
    val firstTabFR = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                0f   to BK.copy(0.92f),
                0.7f to BK.copy(0.40f),
                1f   to Color.Transparent
            )
        )
    ) {
        TopNavBar(
            rdStatus           = true,
            hasNotifications   = false,
            searchFR           = navBarFR,
            onVoiceSearchClick = {},
            onSearchClick      = onSearchClick,
            onProfileClick     = {},
            onDownPress        = {
                try { firstTabFR.requestFocus() } catch (_: Exception) { firstTileFR.safeRequest() }
            },
            onLeftEdge = { onOpenSidebar() }
        )
        Row(
            modifier              = Modifier.fillMaxWidth().padding(start = 64.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            listOf("סרטים", "סדרות").forEachIndexed { idx, tab ->
                LivingTab(
                    label          = tab,
                    isSelected     = state.selectedTab == tab,
                    focusRequester = if (idx == 0) firstTabFR else null,
                    onUpPress      = { navBarFR.safeRequest() },
                    onDownPress    = { firstTileFR.safeRequest() },
                    onClick        = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun LivingTab(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onUpPress: () -> Unit,
    onDownPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val color by animateColorAsState(if (isSelected || isFocused) WH else DM, tween(150))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (kev.key) {
                        Key.DirectionUp   -> { onUpPress(); true }
                        Key.DirectionDown -> { onDownPress(); true }
                        else              -> false
                    }
                }
        ) {
            Text(
                label,
                color      = color,
                fontSize   = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier   = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        if (isSelected)
            Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(RD))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Sidebar
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun NfSidebar(
    open: Boolean,
    activeId: String,
    sidebarFirstFR: FocusRequester,
    onFocusLanded: () -> Unit,
    onClose: () -> Unit,
    onNavSelect: (String) -> Unit
) {
    AnimatedVisibility(
        visible  = open,
        enter    = fadeIn(tween(180)),
        exit     = fadeOut(tween(180)),
        modifier = Modifier.zIndex(19f)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.72f)))
    }

    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it },
        exit     = slideOutHorizontally(tween(180, easing = FastOutLinearInEasing)) { -it },
        modifier = Modifier.zIndex(20f)
    ) {
        LaunchedEffect(Unit) {
            delay(60)
            try { sidebarFirstFR.requestFocus(); onFocusLanded() } catch (_: Exception) {}
        }
        Box(
            modifier = Modifier.fillMaxHeight().width(300.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0D0D0D), Color(0xFF0D0D0D).copy(0.97f))
                    )
                )
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 52.dp)) {
                Text(
                    "LUMINA", color = RD, fontSize = 24.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 36.dp)
                )
                navItems.forEachIndexed { idx, item ->
                    NfSidebarItem(
                        item         = item,
                        isActive     = item.id == activeId,
                        modifier     = if (idx == 0) Modifier.focusRequester(sidebarFirstFR) else Modifier,
                        onRightPress = onClose,
                        onClick      = { onNavSelect(item.id) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "▶  לחץ ימין לתוכן",
                    color    = Color(0xFF808080),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun NfSidebarItem(
    item: NavItem,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onRightPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg        by animateColorAsState(
        when { isActive -> RD.copy(0.22f); isFocused -> GL; else -> Color.Transparent }, tween(120)
    )
    val textColor by animateColorAsState(if (isFocused || isActive) WH else Color(0xFFB3B3B3), tween(120))
    val barW      by animateDpAsState(if (isActive) 3.dp else 0.dp, tween(120))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { isFocused = it.isFocused },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(barW).height(32.dp).background(RD))
        Spacer(Modifier.width(if (isActive) 10.dp else 16.dp))
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .onPreviewKeyEvent { kev ->
                    if (kev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        kev.key == Key.DirectionRight                -> { onRightPress(); true }
                        kev.key == Key.Back || kev.key == Key.Escape -> { onRightPress(); true }
                        else -> false
                    }
                }
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    item.icon, null,
                    tint     = if (isActive) RD else textColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    item.label,
                    color      = textColor,
                    fontSize   = 18.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Loading + Error
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun NfLoadingSkeleton() {
    val inf   = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        0.15f, 0.55f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        "sh"
    )
    val shimmer = DG.copy(alpha)
    Box(Modifier.fillMaxSize().background(BK)) {
        Column(Modifier.fillMaxSize().padding(top = 100.dp, start = 12.dp, end = 12.dp)) {
            repeat(3) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(5) { col ->
                        val h = when {
                            (row * 5 + col) % 7 == 0 -> 240.dp
                            (row * 5 + col) % 3 == 0 -> 180.dp
                            else                     -> 140.dp
                        }
                        Box(
                            Modifier.weight(1f).height(h)
                                .clip(RoundedCornerShape(10.dp))
                                .background(shimmer)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfErrorScreen(message: String, onRetry: () -> Unit) {
    val errShape = ClickableSurfaceDefaults.shape(
        shape        = RoundedCornerShape(6.dp),
        focusedShape = RoundedCornerShape(6.dp)
    )
    Box(Modifier.fillMaxSize().background(BK), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = DM, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick  = onRetry,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = RD,
                    focusedContainerColor = DRD
                ),
                shape    = errShape,
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(48.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Try Again", color = WH, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
