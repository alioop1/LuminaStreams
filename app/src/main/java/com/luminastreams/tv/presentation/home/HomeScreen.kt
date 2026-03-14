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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalConfiguration
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

// ── Palette ───────────────────────────────────────────────────────
private val NfRed       = Color(0xFFE50914)
private val NfDarkRed   = Color(0xFFB20710)
private val NfBlack     = Color(0xFF000000)
private val NfDarkGray  = Color(0xFF141414)
private val NfSidebarBg = Color(0xFF0D0D0D)
private val NfMidGray   = Color(0xFF808080)
private val NfLightGray = Color(0xFFB3B3B3)
private val NfWhite     = Color(0xFFFFFFFF)
private val GlassWhite  = Color(0x33FFFFFF)
private val MatchGreen  = Color(0xFF46D369)

// ── Icons ─────────────────────────────────────────────────────────
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

// ── NavItem ───────────────────────────────────────────────────────
private data class NavItem(val id: String, val label: String, val icon: ImageVector)
private val navItems = listOf(
    NavItem("home",       "בית",      Icons.Default.Home),
    NavItem("movies",    "סרטים",   IconFilm),
    NavItem("series",    "סדרות",    IconTv),
    NavItem("search",    "חיפוש",    Icons.Default.Search),
    NavItem("favorites", "מועדפים", Icons.Default.Favorite),
    NavItem("settings",  "הגדרות",  Icons.Default.Settings)
)

/**
 * Build the ordered list of (rowTitle, movies) outside of any composable
 * to avoid re-creating it inside LazyColumn item lambdas (bug #7).
 */
private fun buildRows(state: HomeState): List<Pair<String, List<Movie>>> = buildList {
    if (state.selectedTab == "סרטים") {
        if (state.movieTrending.isNotEmpty())   add("🔥 Trending Now"      to state.movieTrending)
        if (state.moviePremieres.isNotEmpty())  add("🎬 New Releases"       to state.moviePremieres)
        if (state.movieAction.isNotEmpty())     add("💥 Action & Adventure" to state.movieAction)
        if (state.movieTopRated.isNotEmpty())   add("⭐ Top Rated"           to state.movieTopRated)
        if (state.movieComedy.isNotEmpty())     add("😂 Comedy"             to state.movieComedy)
        if (state.movieDrama.isNotEmpty())      add("🎭 Drama"              to state.movieDrama)
        if (state.movieScifi.isNotEmpty())      add("🚀 Sci-Fi"             to state.movieScifi)
        if (state.movieHorror.isNotEmpty())     add("👻 Horror"             to state.movieHorror)
        if (state.movieAnimation.isNotEmpty())  add("🎨 Animation"          to state.movieAnimation)
    } else {
        if (state.tvTrending.isNotEmpty())      add("🔥 Trending Series"    to state.tvTrending)
        if (state.tvPremieres.isNotEmpty())     add("🆕 New Episodes"        to state.tvPremieres)
        if (state.tvDrama.isNotEmpty())         add("🎭 Drama Series"        to state.tvDrama)
        if (state.tvComedy.isNotEmpty())        add("😂 Comedy"             to state.tvComedy)
        if (state.tvCrime.isNotEmpty())         add("🔪 Crime & Thriller"   to state.tvCrime)
        if (state.tvScifi.isNotEmpty())         add("🚀 Sci-Fi & Fantasy"   to state.tvScifi)
        if (state.tvDocumentary.isNotEmpty())   add("📽 Documentary"         to state.tvDocumentary)
        if (state.tvTopRated.isNotEmpty())      add("⭐ Top Rated"           to state.tvTopRated)
    }
    if (state.discoveryResults.isNotEmpty())
        add("🎯 ${state.selectedGenreName}" to state.discoveryResults)
}

// ── HomeScreen ────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val screenH    = LocalConfiguration.current.screenHeightDp
    // Hero occupies 88% — cinematic
    val heroHeight = (screenH * 0.88f).dp

    // Focus anchors
    val navBarFR       = remember { FocusRequester() }
    val firstRowFR     = remember { FocusRequester() }
    val sidebarFirstFR = remember { FocusRequester() }
    val playBtnFR      = remember { FocusRequester() }

    // Sidebar
    var sidebarOpen by remember { mutableStateOf(false) }
    var activeNavId by remember { mutableStateOf("home") }

    // Guard: true once the first content row is laid out
    var firstRowReady by remember { mutableStateOf(false) }

    // ── Hero pool & auto-cycle (bug #1 fix: only cycle when no card focused) ──
    val heroPool = remember(state.selectedTab, state.movieTrending, state.tvTrending) {
        if (state.selectedTab == "סרטים") state.movieTrending else state.tvTrending
    }
    var heroIdx by remember(state.selectedTab) { mutableIntStateOf(0) }
    LaunchedEffect(heroPool) {
        if (heroPool.size > 1) {
            while (true) {
                delay(8_000)
                // "hero" rowTitle means user is NOT browsing a card row
                if (state.focusedRowTitle != "hero" && state.focusedRowTitle.isNotEmpty()) continue
                heroIdx = (heroIdx + 1) % heroPool.size
                viewModel.updateFocusedItem(heroPool[heroIdx], "hero", false)
            }
        }
    }

    // Initial focus after load
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            delay(150)
            try { navBarFR.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Bug #1: debounce sidebar open so a single LEFT keypress doesn't fire twice ──
    var sidebarCooldown by remember { mutableStateOf(false) }
    fun openSidebar() {
        if (sidebarCooldown || sidebarOpen) return
        sidebarCooldown = true
        sidebarOpen = true
        // cooldown resets inside the LaunchedEffect in NfSidebar after focus lands
    }
    fun closeSidebar() {
        sidebarOpen = false
        sidebarCooldown = false
        try { navBarFR.requestFocus() } catch (_: Exception) {}
    }
    fun safeNav(fr: FocusRequester) { try { fr.requestFocus() } catch (_: Exception) {} }

    // Pre-compute rows outside composable lambdas (bug #7)
    val allRows = remember(state) { buildRows(state) }

    Box(Modifier.fillMaxSize().background(NfBlack)) {

        // ── Content ──────────────────────────────────────────────
        when {
            state.isLoading     -> NfLoadingSkeleton()
            state.error != null -> NfErrorScreen(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                val displayItem = state.focusedItem
                    ?: heroPool.firstOrNull()
                    ?: state.movieTrending.firstOrNull()

                LazyColumn(
                    modifier          = Modifier.fillMaxSize(),
                    contentPadding    = PaddingValues(bottom = 80.dp),
                    userScrollEnabled = true
                ) {
                    item(key = "hero") {
                        NfHeroBanner(
                            movie           = displayItem,
                            heroHeight      = heroHeight,
                            playBtnFR       = playBtnFR,
                            onPlayClick     = { displayItem?.id?.let(onMovieClick) },
                            onMoreInfoClick = { displayItem?.id?.let(onMovieClick) },
                            onDownPress     = { if (firstRowReady) safeNav(firstRowFR) },
                            onUpPress       = { safeNav(navBarFR) }
                        )
                    }

                    allRows.forEachIndexed { idx, (rowTitle, movies) ->
                        item(key = rowTitle) {
                            // Bug #8: use LaunchedEffect(rowTitle) instead of SideEffect
                            if (idx == 0) LaunchedEffect(rowTitle) { firstRowReady = true }
                            NfContentRow(
                                title       = rowTitle,
                                movies      = movies,
                                rowModifier = if (idx == 0) Modifier.focusRequester(firstRowFR) else Modifier,
                                onUpFromRow = if (idx == 0) ({ safeNav(playBtnFR) }) else null,
                                onLeftEdge  = { openSidebar() },
                                onFocus     = { movie -> viewModel.updateFocusedItem(movie, rowTitle, true) },
                                onClick     = onMovieClick
                            )
                        }
                    }
                }

                // TopNav floats above the scroll
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                        .align(Alignment.TopCenter)
                ) {
                    NfTopNavWrapper(
                        state          = state,
                        navBarFR       = navBarFR,
                        firstRowFR     = firstRowFR,
                        firstRowReady  = firstRowReady,
                        sidebarOpen    = sidebarOpen,
                        onOpenSidebar  = {
                            openSidebar()
                            // Sidebar LaunchedEffect will requestFocus after node attaches
                        },
                        onTabSelect    = { viewModel.selectTab(it) },
                        onSearchClick  = { navController.navigate("search") },
                        onProfileClick = {}
                    )
                }
            }
        }

        // ── Sidebar — always in composition tree ──────────────────
        NfSidebar(
            open           = sidebarOpen,
            activeId       = activeNavId,
            sidebarFirstFR = sidebarFirstFR,
            onFocusLanded  = { sidebarCooldown = false },   // release debounce
            onClose        = { closeSidebar() },
            onNavSelect    = { id ->
                activeNavId = id
                sidebarOpen = false
                sidebarCooldown = false
                when (id) {
                    "movies" -> viewModel.selectTab("סרטים")
                    "series" -> viewModel.selectTab("סדרות")
                    "search" -> navController.navigate("search")
                    else     -> {}
                }
                if (firstRowReady) safeNav(firstRowFR) else safeNav(navBarFR)
            }
        )
    }
}

// ── TopNav wrapper ────────────────────────────────────────────────
@Composable
private fun NfTopNavWrapper(
    state: HomeState,
    navBarFR: FocusRequester,
    firstRowFR: FocusRequester,
    firstRowReady: Boolean,
    sidebarOpen: Boolean,
    onOpenSidebar: () -> Unit,
    onTabSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val tabs = listOf("סרטים", "סדרות")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(
                0f to NfBlack.copy(alpha = 0.96f),
                0.7f to NfBlack.copy(alpha = 0.45f),
                1f to Color.Transparent
            ))
    ) {
        // Bug #6 fix: navBarFR anchors the FIRST focusable in the bar (mic button),
        // NOT searchFR — TopNavBar internally uses searchFR only as a secondary anchor.
        // We pass navBarFR as searchFR here so the mic/voice button gets the initial focus,
        // which is the leftmost focusable item and natural entry point.
        TopNavBar(
            rdStatus           = true,
            hasNotifications   = false,
            searchFR           = navBarFR,
            onVoiceSearchClick = {},
            onSearchClick      = onSearchClick,
            onProfileClick     = onProfileClick,
            onDownPress        = {
                if (firstRowReady) try { firstRowFR.requestFocus() } catch (_: Exception) {}
            },
            onLeftEdge         = {
                if (!sidebarOpen) onOpenSidebar()
            }
        )
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                NfNavTab(
                    label       = tab,
                    isSelected  = state.selectedTab == tab,
                    onDownPress = {
                        if (firstRowReady) try { firstRowFR.requestFocus() } catch (_: Exception) {}
                    },
                    onClick     = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun NfNavTab(
    label: String,
    isSelected: Boolean,
    onDownPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (isSelected || isFocused) NfWhite else NfLightGray, tween(150)
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.key == Key.DirectionDown && kev.type == KeyEventType.KeyDown) {
                        onDownPress(); true
                    } else false
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
            Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(NfWhite))
    }
}

// ── Sidebar ────────────────────────────────────────────────────────
@Composable
fun NfSidebar(
    open: Boolean,
    activeId: String,
    sidebarFirstFR: FocusRequester,
    onFocusLanded: () -> Unit,
    onClose: () -> Unit,
    onNavSelect: (String) -> Unit
) {
    // Scrim
    AnimatedVisibility(
        visible  = open,
        enter    = fadeIn(tween(180)),
        exit     = fadeOut(tween(180)),
        modifier = Modifier.zIndex(19f)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
    }

    // Panel
    AnimatedVisibility(
        visible  = open,
        enter    = slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it },
        exit     = slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { -it },
        modifier = Modifier.zIndex(20f)
    ) {
        // Bug #3 fix: request focus AFTER the node is attached to the tree.
        // LaunchedEffect(Unit) runs after the first composition of this block.
        LaunchedEffect(Unit) {
            delay(80)
            try {
                sidebarFirstFR.requestFocus()
                onFocusLanded()   // release cooldown only after focus actually lands
            } catch (_: Exception) {}
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(Brush.horizontalGradient(
                    listOf(NfSidebarBg, NfSidebarBg.copy(alpha = 0.97f))
                ))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 52.dp)
            ) {
                Text(
                    "LUMINA",
                    color         = NfRed,
                    fontSize      = 24.sp,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 6.sp,
                    modifier      = Modifier.padding(start = 32.dp, bottom = 36.dp)
                )
                navItems.forEachIndexed { idx, item ->
                    NfSidebarItem(
                        item         = item,
                        isActive     = item.id == activeId,
                        // Bug #2 fix: focusRequester on the ITEM ROW, not on inner Surface
                        modifier     = if (idx == 0) Modifier.focusRequester(sidebarFirstFR) else Modifier,
                        onRightPress = onClose,
                        onClick      = { onNavSelect(item.id) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "▶  לחץ ימין לתוכן",
                    color    = NfMidGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 20.dp)
                )
            }
        }
    }
}

// ── SidebarItem ────────────────────────────────────────────────────
// Bug #2 fix: key-handler moved to the Surface (actual focus holder),
// not on the outer Row which never receives focus events.
@Composable
private fun NfSidebarItem(
    item: NavItem,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onRightPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { isActive -> NfRed.copy(alpha = 0.22f); isFocused -> GlassWhite; else -> Color.Transparent },
        tween(150)
    )
    val textColor by animateColorAsState(
        if (isFocused || isActive) NfWhite else NfLightGray, tween(150)
    )
    val barW by animateDpAsState(if (isActive) 3.dp else 0.dp, tween(150))

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { isFocused = it.isFocused },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(barW).height(32.dp).background(NfRed))
        Spacer(Modifier.width(if (isActive) 10.dp else 16.dp))
        // Surface is the actual focus node — key handler belongs here
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
                    when {
                        kev.type != KeyEventType.KeyDown                  -> false
                        kev.key == Key.DirectionRight                     -> { onRightPress(); true }
                        kev.key == Key.Back || kev.key == Key.Escape      -> { onRightPress(); true }
                        else                                               -> false
                    }
                }
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    item.icon, null,
                    tint     = if (isActive) NfRed else textColor,
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

// ── Hero Banner ───────────────────────────────────────────────────
@Composable
fun NfHeroBanner(
    movie: Movie?,
    heroHeight: androidx.compose.ui.unit.Dp,
    playBtnFR: FocusRequester,
    onPlayClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    onDownPress: () -> Unit,
    onUpPress: () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {

        AnimatedContent(
            targetState    = movie?.backdropUrl ?: movie?.posterUrl,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(700)) },
            label          = "hero_bg"
        ) { url ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url).size(1920, 1080).scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(1000).build(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }

        // Gradient overlays
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.3f to Color.Transparent,
            0.68f to NfBlack.copy(0.5f),
            1.0f to NfBlack
        )))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0.0f to NfBlack.copy(0.88f),
            0.48f to NfBlack.copy(0.12f),
            1.0f to Color.Transparent
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0.0f to NfBlack.copy(0.5f),
            0.2f to Color.Transparent
        )))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 72.dp, bottom = 80.dp)
                .fillMaxWidth(0.5f)
        ) {
            AnimatedContent(
                targetState    = movie?.title ?: "",
                transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(400)) },
                label          = "hero_title"
            ) { title ->
                Text(
                    title,
                    color      = NfWhite,
                    fontSize   = 58.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 64.sp,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatchGreen)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("97% Match", color = NfBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val rating = movie?.rating?.let { if (it > 0f) "%.1f ★".format(it) else null } ?: ""
                if (rating.isNotEmpty()) Text(rating, color = NfLightGray, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState    = movie?.overview ?: "",
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(350)) },
                label          = "hero_overview"
            ) { ov ->
                Text(
                    ov,
                    color      = NfLightGray,
                    fontSize   = 15.sp,
                    lineHeight = 22.sp,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NfHeroButton(
                    label          = "▶  Play",
                    isPrimary      = true,
                    focusRequester = playBtnFR,
                    onUpPress      = onUpPress,
                    onDownPress    = onDownPress,
                    onClick        = onPlayClick
                )
                NfHeroButton(
                    label          = "ℹ  More Info",
                    isPrimary      = false,
                    focusRequester = null,
                    onUpPress      = onUpPress,
                    onDownPress    = onDownPress,
                    onClick        = onMoreInfoClick
                )
            }
        }
    }
}

// ── HeroButton ────────────────────────────────────────────────────
// Bug #5 fix: build the full Modifier chain in one expression, never reassign.
@Composable
private fun NfHeroButton(
    label: String,
    isPrimary: Boolean,
    focusRequester: FocusRequester?,
    onUpPress: () -> Unit,
    onDownPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            isPrimary && isFocused -> NfWhite.copy(alpha = 0.85f)
            isPrimary              -> NfWhite
            isFocused              -> GlassWhite.copy(alpha = 0.55f)
            else                   -> GlassWhite
        },
        tween(120)
    )
    val fg by animateColorAsState(if (isPrimary) NfBlack else NfWhite, tween(120))

    // Build modifier immutably in one chain (bug #5)
    val mod = Modifier
        .height(54.dp)
        .widthIn(min = if (isPrimary) 168.dp else 192.dp)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { isFocused = it.isFocused }
        .onPreviewKeyEvent { kev ->
            when {
                kev.type != KeyEventType.KeyDown                       -> false
                kev.key == Key.DirectionUp                             -> { onUpPress();   true }
                kev.key == Key.DirectionDown                           -> { onDownPress(); true }
                else                                                   -> false
            }
        }

    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = mod
    ) {
        Row(
            modifier              = Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── ContentRow ────────────────────────────────────────────────────
@Composable
fun NfContentRow(
    title: String,
    movies: List<Movie>,
    rowModifier: Modifier = Modifier,
    onUpFromRow: (() -> Unit)? = null,
    onLeftEdge: (() -> Unit)? = null,
    onFocus: (Movie) -> Unit,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp).then(rowModifier)) {
        Text(
            title,
            color      = NfWhite,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 64.dp, top = 24.dp, bottom = 12.dp)
        )
        // Bug #4 fix: focusRestorer() alone is sufficient for TV LazyRow —
        // wrapping in an additional focusGroup caused double-intercept.
        LazyRow(
            state                 = rememberLazyListState(),
            contentPadding        = PaddingValues(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.focusRestorer()
        ) {
            items(movies, key = { it.id }) { movie ->
                NfPosterCard(
                    movie      = movie,
                    onUpPress  = onUpFromRow,
                    onLeftEdge = onLeftEdge,
                    onFocus    = { onFocus(movie) },
                    onClick    = { onClick(movie.id) }
                )
            }
        }
    }
}

// ── PosterCard (98dp = 35% smaller than original 150dp) ───────────
@Composable
fun NfPosterCard(
    movie: Movie,
    onUpPress: (() -> Unit)?,
    onLeftEdge: (() -> Unit)?,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cardScale by animateFloatAsState(
        if (isFocused) 1.08f else 1.0f,
        tween(180, easing = FastOutSlowInEasing),
        label = "card_scale"
    )

    Column(modifier = Modifier.width(98.dp).padding(vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
        ) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = NfDarkGray,
                    focusedContainerColor = NfDarkGray
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(5.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                border   = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        BorderStroke(2.dp, NfWhite),
                        shape = RoundedCornerShape(5.dp)
                    )
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { fs ->
                        isFocused = fs.isFocused
                        if (fs.isFocused) onFocus()
                    }
                    .onPreviewKeyEvent { kev ->
                        when {
                            kev.type != KeyEventType.KeyDown -> false
                            kev.key == Key.DirectionUp && onUpPress != null -> {
                                onUpPress(); true
                            }
                            // Bug #1 related: only fire onLeftEdge for first card
                            // (LazyRow handles scrolling internally for others)
                            kev.key == Key.DirectionLeft && onLeftEdge != null -> {
                                onLeftEdge()
                                false  // let LazyRow consume for its own scroll logic
                            }
                            else -> false
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(movie.posterUrl)
                        .size(200, 300)
                        .scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(200).build(),
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            if (isFocused) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(
                            listOf(Color.Transparent, NfBlack.copy(alpha = 0.45f))
                        ))
                )
            }
        }
        AnimatedVisibility(
            visible = isFocused,
            enter   = fadeIn(tween(130)) + slideInVertically { it / 3 },
            exit    = fadeOut(tween(90))
        ) {
            Text(
                movie.title,
                color      = NfWhite,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.padding(top = 4.dp, start = 1.dp, end = 1.dp)
            )
        }
    }
}

// ── LoadingSkeleton ───────────────────────────────────────────────
@Composable
fun NfLoadingSkeleton() {
    val inf   = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        0.2f, 0.6f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer_a"
    )
    val shimmer = NfDarkGray.copy(alpha = alpha)
    Column(Modifier.fillMaxSize().background(NfBlack)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.88f).background(shimmer))
        Spacer(Modifier.height(20.dp))
        repeat(2) {
            Box(
                Modifier
                    .padding(start = 64.dp)
                    .width(180.dp).height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmer)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(8) {
                    Box(
                        Modifier
                            .width(98.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmer)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── ErrorScreen ───────────────────────────────────────────────────
@Composable
fun NfErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(NfBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(message, color = NfLightGray, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick  = onRetry,
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = NfRed,
                    focusedContainerColor = NfDarkRed
                ),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(48.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Try Again", color = NfWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
