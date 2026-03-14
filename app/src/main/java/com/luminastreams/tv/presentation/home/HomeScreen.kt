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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
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
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.components.TopNavBar

// ── Colours ───────────────────────────────────────────────────────
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

// ── Custom sidebar icons (no extended-icons dep needed) ───────────
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
            curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f); close()
            moveTo(21f, 17f); lineTo(3f, 17f); lineTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 17f); close()
        }
    }.build()

// ── NavItem — private to file, NfSidebarItem also private ────────
private data class NavItem(val id: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home",      "בית",      Icons.Default.Home),
    NavItem("movies",   "סרטים",   IconFilm),
    NavItem("series",   "סדרות",    IconTv),
    NavItem("search",   "חיפוש",    Icons.Default.Search),
    NavItem("favorites","מועדפים", Icons.Default.Favorite),
    NavItem("settings", "הגדרות",  Icons.Default.Settings)
)

// ── Root screen ───────────────────────────────────────────────────
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val config     = LocalConfiguration.current
    val heroHeight = (config.screenHeightDp * 0.82f).dp

    val searchFR       = remember { FocusRequester() }
    val firstRowFR     = remember { FocusRequester() }
    val sidebarFirstFR = remember { FocusRequester() }

    var sidebarExpanded by remember { mutableStateOf(false) }
    var activeNavId     by remember { mutableStateOf("home") }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            try { searchFR.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NfBlack)) {
        when {
            state.isLoading     -> NfLoadingSkeleton()
            state.error != null -> NfErrorScreen(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                val displayItem = state.focusedItem ?: state.movieTrending.firstOrNull()

                TvLazyColumn(
                    modifier       = Modifier.fillMaxSize().focusRestorer { firstRowFR },
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        NfHeroBanner(
                            movie           = displayItem,
                            heroHeight      = heroHeight,
                            onPlayClick     = { displayItem?.id?.let(onMovieClick) },
                            onMoreInfoClick = { displayItem?.id?.let(onMovieClick) }
                        )
                    }

                    val allRows = buildList {
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

                    allRows.forEachIndexed { idx, (rowTitle, movies) ->
                        item(key = rowTitle) {
                            NfContentRow(
                                title             = rowTitle,
                                movies            = movies,
                                rowModifier       = if (idx == 0) Modifier.focusRequester(firstRowFR) else Modifier,
                                onUpFromFirstCard = if (idx == 0) ({ searchFR.requestFocus() }) else null,
                                onLeftEdge        = { sidebarExpanded = true; sidebarFirstFR.requestFocus() },
                                onFocus           = { movie -> viewModel.updateFocusedItem(movie, rowTitle, true) },
                                onClick           = onMovieClick
                            )
                        }
                    }
                }

                // Floating TopNav
                Box(
                    modifier = Modifier.fillMaxWidth().zIndex(10f).align(Alignment.TopCenter)
                ) {
                    NfTopNav(
                        state          = state,
                        searchFR       = searchFR,
                        firstRowFR     = firstRowFR,
                        sidebarFirstFR = sidebarFirstFR,
                        onSidebarOpen  = { sidebarExpanded = true },
                        onTabSelect    = { viewModel.selectTab(it) },
                        onSearchClick  = { navController.navigate("search") },
                        onProfileClick = {}
                    )
                }

                // Sidebar
                NfSidebar(
                    expanded       = sidebarExpanded,
                    activeId       = activeNavId,
                    sidebarFirstFR = sidebarFirstFR,
                    firstRowFR     = firstRowFR,
                    onClose        = { sidebarExpanded = false },
                    onNavSelect    = { id ->
                        activeNavId     = id
                        sidebarExpanded = false
                        when (id) {
                            "movies"  -> viewModel.selectTab("סרטים")
                            "series"  -> viewModel.selectTab("סדרות")
                            "search"  -> navController.navigate("search")
                            "home"    -> viewModel.selectTab("סרטים")
                        }
                        firstRowFR.requestFocus()
                    }
                )
            }
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────
@Composable
fun NfSidebar(
    expanded: Boolean,
    activeId: String,
    sidebarFirstFR: FocusRequester,
    firstRowFR: FocusRequester,
    onClose: () -> Unit,
    onNavSelect: (String) -> Unit
) {
    AnimatedVisibility(visible = expanded, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().zIndex(19f).background(NfBlack.copy(alpha = 0.6f)))
    }
    AnimatedVisibility(
        visible = expanded,
        enter   = slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it },
        exit    = slideOutHorizontally(tween(180, easing = FastOutLinearInEasing)) { -it }
    ) {
        Box(
            modifier = Modifier
                .zIndex(20f).fillMaxHeight().width(280.dp)
                .background(Brush.horizontalGradient(listOf(NfSidebarBg, NfSidebarBg.copy(alpha = 0.97f))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 48.dp)
                    .focusGroup()
            ) {
                Text(
                    "LUMINA", color = NfRed, fontSize = 22.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 32.dp)
                )
                navItems.forEachIndexed { idx, item ->
                    NfSidebarItem(
                        item         = item,
                        isActive     = item.id == activeId,
                        modifier     = if (idx == 0) Modifier.focusRequester(sidebarFirstFR) else Modifier,
                        onRightPress = { onClose(); firstRowFR.requestFocus() },
                        onClick      = { onNavSelect(item.id) }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "▶  לחץ ימין לתוכן",
                    color = NfMidGray, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 28.dp, bottom = 16.dp)
                )
            }
        }
    }
}

// NfSidebarItem is private — fixes "public function exposes private type" error
@Composable
private fun NfSidebarItem(
    item: NavItem,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onRightPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor   by animateColorAsState(
        targetValue = when { isActive -> NfRed.copy(alpha = 0.18f); isFocused -> GlassWhite; else -> Color.Transparent },
        animationSpec = tween(150)
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused || isActive) NfWhite else NfLightGray, animationSpec = tween(150)
    )
    val leftBar   by animateDpAsState(if (isActive) 3.dp else 0.dp, tween(150))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { kev ->
                if (kev.key == Key.DirectionRight && kev.type == KeyEventType.KeyDown) { onRightPress(); true }
                else false
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(leftBar).height(28.dp).background(NfRed))
        Spacer(Modifier.width(16.dp))
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(item.icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                Text(item.label, color = textColor, fontSize = 17.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ── TopNav ────────────────────────────────────────────────────────
@Composable
fun NfTopNav(
    state: HomeState,
    searchFR: FocusRequester,
    firstRowFR: FocusRequester,
    sidebarFirstFR: FocusRequester,
    onSidebarOpen: () -> Unit,
    onTabSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val tabs = listOf("סרטים", "סדרות")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .background(Brush.verticalGradient(
                0f to NfBlack.copy(alpha = 0.92f),
                0.7f to NfBlack.copy(alpha = 0.5f),
                1f to Color.Transparent
            ))
    ) {
        TopNavBar(
            rdStatus           = true,
            hasNotifications   = false,
            searchFR           = searchFR,
            onVoiceSearchClick = {},
            onSearchClick      = onSearchClick,
            onProfileClick     = onProfileClick,
            onDownPress        = { firstRowFR.requestFocus() },
            onLeftEdge         = { onSidebarOpen(); sidebarFirstFR.requestFocus() }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 64.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                NfNavTab(
                    label       = tab,
                    isSelected  = state.selectedTab == tab,
                    onDownPress = { firstRowFR.requestFocus() },
                    onClick     = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
fun NfNavTab(label: String, isSelected: Boolean, onDownPress: () -> Unit, onClick: () -> Unit) {
    var isFocused  by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(
        if (isSelected || isFocused) NfWhite else NfLightGray, tween(150)
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick  = onClick,
            colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { kev ->
                    if (kev.key == Key.DirectionDown && kev.type == KeyEventType.KeyDown) { onDownPress(); true }
                    else false
                }
        ) {
            Text(label, color = labelColor, fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        // Simple if — avoids ColumnScope.AnimatedVisibility context error
        if (isSelected) {
            Box(Modifier.width(20.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(NfWhite))
        }
    }
}

// ── Hero Banner ───────────────────────────────────────────────────
@Composable
fun NfHeroBanner(
    movie: Movie?,
    heroHeight: androidx.compose.ui.unit.Dp,
    onPlayClick: () -> Unit,
    onMoreInfoClick: () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        AnimatedContent(
            targetState    = movie?.backdropUrl ?: movie?.posterUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(400)) },
            label          = "hero_bg"
        ) { url ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url).size(1280, 720).scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(700).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.38f to Color.Transparent, 0.76f to NfBlack.copy(0.6f), 1f to NfBlack)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to NfBlack.copy(0.86f), 0.54f to NfBlack.copy(0.2f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to NfBlack.copy(0.5f), 0.18f to Color.Transparent)))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 60.dp).fillMaxWidth(0.55f)
        ) {
            Text(movie?.title ?: "", color = NfWhite, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 58.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MatchGreen).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("97% Match", color = NfBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val rating = movie?.rating?.let { if (it > 0f) "%.1f ★".format(it) else null } ?: ""
                if (rating.isNotEmpty()) Text(rating, color = NfLightGray, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(movie?.overview ?: "", color = NfLightGray, fontSize = 16.sp, lineHeight = 24.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NfHeroButton("▶  Play",      isPrimary = true,  onClick = onPlayClick)
                NfHeroButton("ℹ  More Info", isPrimary = false, onClick = onMoreInfoClick)
            }
        }
    }
}

@Composable
fun NfHeroButton(label: String, isPrimary: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { isPrimary && isFocused -> NfWhite.copy(alpha = 0.85f); isPrimary -> NfWhite; isFocused -> GlassWhite.copy(alpha = 0.5f); else -> GlassWhite },
        tween(120)
    )
    val fg by animateColorAsState(if (isPrimary) NfBlack else NfWhite, tween(120))
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border   = ClickableSurfaceDefaults.border(),
        modifier = Modifier.height(52.dp).widthIn(min = if (isPrimary) 160.dp else 180.dp).onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), Arrangement.Center, Alignment.CenterVertically) {
            Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Content Row ───────────────────────────────────────────────────
@Composable
fun NfContentRow(
    title: String,
    movies: List<Movie>,
    rowModifier: Modifier = Modifier,
    onUpFromFirstCard: (() -> Unit)? = null,
    onLeftEdge: () -> Unit,
    onFocus: (Movie) -> Unit,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp).then(rowModifier)) {
        Text(title, color = NfWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 64.dp, top = 24.dp, bottom = 12.dp))
        TvLazyRow(
            state                 = rememberTvLazyListState(),
            contentPadding        = PaddingValues(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            pivotOffsets          = PivotOffsets(parentFraction = 0.07f),
            modifier              = Modifier.focusRestorer()
        ) {
            items(movies, key = { it.id }) { movie ->
                NfPosterCard(
                    movie           = movie,
                    onUpPress       = onUpFromFirstCard,
                    onLeftEdgePress = onLeftEdge,
                    onFocus         = { onFocus(movie) },
                    onClick         = { onClick(movie.id) }
                )
            }
        }
    }
}

// ── Poster Card ───────────────────────────────────────────────────
@Composable
fun NfPosterCard(
    movie: Movie,
    onUpPress: (() -> Unit)?,
    onLeftEdgePress: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val context   = LocalContext.current
    val cardScale by animateFloatAsState(
        targetValue   = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label         = "card_scale"
    )

    Column(modifier = Modifier.width(140.dp).padding(vertical = 12.dp)) {
        // Scale via graphicsLayer (lambda form) — outside Surface so TV focus engine is unaffected
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
        ) {
            Surface(
                onClick  = onClick,
                colors   = ClickableSurfaceDefaults.colors(containerColor = NfDarkGray, focusedContainerColor = NfDarkGray),
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                border   = ClickableSurfaceDefaults.border(),   // no border
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { fs -> isFocused = fs.isFocused; if (fs.isFocused) onFocus() }
                    .onPreviewKeyEvent { kev ->
                        when {
                            onUpPress != null && kev.key == Key.DirectionUp   && kev.type == KeyEventType.KeyDown -> { onUpPress(); true }
                            kev.key == Key.DirectionLeft && kev.type == KeyEventType.KeyDown -> { onLeftEdgePress(); false }
                            else -> false
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(movie.posterUrl).size(300, 450).scale(Scale.FILL)
                        .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(250).build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Focus overlay drawn OUTSIDE Surface but INSIDE Box — valid BoxScope, no ColumnScope issue
            if (isFocused) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, NfBlack.copy(alpha = 0.55f)))
                    )
                )
            }
        }

        // Title fades in below card — valid ColumnScope here
        AnimatedVisibility(
            visible = isFocused,
            enter   = fadeIn(tween(140)) + slideInVertically { it / 3 },
            exit    = fadeOut(tween(100))
        ) {
            Text(
                movie.title, color = NfWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

// ── Loading Skeleton ──────────────────────────────────────────────
@Composable
fun NfLoadingSkeleton() {
    val inf   = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(0.25f, 0.65f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer_alpha")
    val shimmer = NfDarkGray.copy(alpha = alpha)
    Column(Modifier.fillMaxSize().background(NfBlack)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.82f).background(shimmer))
        Spacer(Modifier.height(24.dp))
        repeat(2) {
            Box(Modifier.padding(start = 64.dp).width(180.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(6) { Box(Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(shimmer)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Error Screen ──────────────────────────────────────────────────
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
                colors   = ClickableSurfaceDefaults.colors(containerColor = NfRed, focusedContainerColor = NfDarkRed),
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
