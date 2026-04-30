@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
package com.luminastreams.tv.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.SearchResult
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay



// ── Category definitions for discovery grid ─────────────────────────────
private data class CategoryDef(val en: String, val he: String, val genre: String? = null)

private val CATEGORIES = listOf(
    CategoryDef("Top Box Office", "הנצפים ביותר", "_popular_"),
    CategoryDef("Action", "אקשן", "Action"),
    CategoryDef("Sci-Fi", "מדע בדיוני", "Sci-Fi"),
    CategoryDef("Comedy", "קומדיה", "Comedy"),
    CategoryDef("Drama", "דרמה", "Drama"),
    CategoryDef("Horror", "אימה", "Horror"),
    CategoryDef("Fantasy", "פנטזיה", "Fantasy"),
    CategoryDef("Thriller", "מותחן", "Thriller"),
    CategoryDef("Crime", "פשע", "Crime"),
    CategoryDef("Romance", "רומנטיקה", "Romance"),
    CategoryDef("Animation", "אנימציה", "Animation"),
    CategoryDef("War", "מלחמה", "War"),
    CategoryDef("Documentary", "דוקומנטרי", "Documentary"),
    CategoryDef("Family", "משפחה", "Family"),
    CategoryDef("Mystery", "מסתורין", "Mystery")
)

@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val inputFR = remember { FocusRequester() }
    val firstTabFR = remember { FocusRequester() }
    val firstChipFR = remember { FocusRequester() }
    val firstResultFR = remember { FocusRequester() }
    val filterFR = remember { FocusRequester() }

    var focusedBackdrop by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val searchBtnFR = remember { FocusRequester() }

    BackHandler {
        when {
            state.showFilters -> onIntent(SearchIntent.ToggleFilters)
            state.filters.isActive -> onIntent(SearchIntent.ClearFilters)
            state.query.isNotBlank() -> onIntent(SearchIntent.UpdateQuery(""))
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(Unit) { delay(100); runCatching { firstResultFR.requestFocus() } }
    LaunchedEffect(state.showFilters) { if (state.showFilters) { delay(150); runCatching { filterFR.requestFocus() } } }
    // When search opens, focus the input
    LaunchedEffect(searchOpen) { if (searchOpen) { delay(100); runCatching { inputFR.requestFocus() } } }
    // When genre changes (enter/exit category), focus the first result
    LaunchedEffect(state.filters.genre) { delay(200); runCatching { firstResultFR.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF040408))) {
        // Dynamic backdrop
        AnimatedContent(focusedBackdrop, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) }, label = "bg") { bd ->
            if (!bd.isNullOrBlank()) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(bd).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.3f))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66040408), Color(0xCC040408), Color(0xFF040408)))))
                }
            }
        }

        Column(Modifier.fillMaxSize().focusProperties { canFocus = !state.showFilters }) {

            // ── 1. NAV BAR: Tabs + Search + Filter ────────────────────
            AnimatedContent(
                targetState = searchOpen,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "navbar",
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 16.dp)
            ) { isOpen ->
                if (!isOpen) {
                    // ── Collapsed: Tabs + Search + Filter + Reset ──
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back/Reset button — visible when filters active or search query present
                        if (state.filters.isActive || state.query.isNotBlank()) {
                            Surface(
                                onClick = {
                                    if (state.query.isNotBlank()) onIntent(SearchIntent.UpdateQuery(""))
                                    if (state.filters.isActive) onIntent(SearchIntent.ClearFilters)
                                    searchOpen = false
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = RED.copy(0.25f),
                                    focusedContainerColor = RED
                                ),
                                border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                                modifier = Modifier.height(38.dp)
                                    .focusProperties { down = firstResultFR }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp), tint = Color.White)
                                    Text(tr("Categories", "קטגוריות"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                        }

                        val tabs = listOf(
                            SearchSource.ALL to tr("All", "הכל"),
                            SearchSource.MOVIES to tr("Movies", "סרטים"),
                            SearchSource.SERIES to tr("Shows", "סדרות"),
                            SearchSource.FUZER to "Fuzer"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                    runCatching { firstResultFR.requestFocus() }; true
                                } else false
                            }
                        ) {
                            tabs.forEachIndexed { idx, (src, label) ->
                                val isSel = state.source == src
                                Surface(
                                    onClick = { onIntent(SearchIntent.SelectSource(src)) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (isSel) Color.White.copy(0.12f) else Color.Transparent,
                                        focusedContainerColor = Color.White,
                                        contentColor = if (isSel) Color.White else Color.White.copy(0.5f),
                                        focusedContentColor = Color.Black
                                    ),
                                    border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                                    modifier = Modifier.height(38.dp).let { if (idx == 0) it.focusRequester(firstTabFR) else it }
                                        .focusProperties { down = firstResultFR }
                                ) {
                                    Row(Modifier.padding(horizontal = 18.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (src == SearchSource.FUZER) Text("💎", fontSize = 12.sp)
                                        Text(label, fontSize = 14.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Search button
                        Surface(
                            onClick = { searchOpen = true },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.06f), focusedContainerColor = Color.White),
                            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                            modifier = Modifier.size(38.dp).focusRequester(searchBtnFR)
                                .focusProperties { down = firstResultFR }
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = Color.White.copy(0.6f))
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Filter button
                        Surface(
                            onClick = { onIntent(SearchIntent.ToggleFilters) },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (state.filters.isActive) RED.copy(0.2f) else Color.White.copy(0.06f),
                                focusedContainerColor = Color.White
                            ),
                            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                            modifier = Modifier.size(38.dp).focusProperties { down = firstResultFR }
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Default.Tune, null, Modifier.size(18.dp), tint = if (state.filters.isActive) RED else Color.White.copy(0.6f))
                            }
                        }
                    }
                } else {
                    // ── Expanded: Full search input ──
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.weight(1f).height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(0.08f))
                                .padding(horizontal = 16.dp),
                            Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = RED)
                                BasicTextField(
                                    value = state.query, onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                                    cursorBrush = SolidColor(RED),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                                    decorationBox = { inner -> Box(Modifier.weight(1f)) { if (state.query.isEmpty()) Text(tr("Search movies, series...", "חיפוש סרטים, סדרות..."), color = Color.White.copy(0.25f), fontSize = 17.sp); inner() } },
                                    modifier = Modifier.weight(1f).focusRequester(inputFR)
                                        .focusProperties { down = firstResultFR }
                                        .onPreviewKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                                                runCatching { firstResultFR.requestFocus() }; true
                                            } else if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Escape || ev.key == Key.Back)) {
                                                searchOpen = false; onIntent(SearchIntent.UpdateQuery("")); runCatching { firstResultFR.requestFocus() }; true
                                            } else false
                                        }
                                )
                            }
                        }
                        // Close button
                        Surface(
                            onClick = { searchOpen = false; onIntent(SearchIntent.UpdateQuery("")) },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.06f), focusedContainerColor = Color.White),
                            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color.White.copy(0.6f))
                            }
                        }
                    }
                }
            }

            // ── 3. HISTORY / TRENDING CHIPS ────────────────────────────
            if (state.searchHistory.isNotEmpty() && state.query.isBlank()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(state.searchHistory) { idx, term ->
                        Surface(
                            onClick = { onIntent(SearchIntent.UpdateQuery(term)) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(0.08f),
                                focusedContainerColor = Color.White,
                                contentColor = Color.White.copy(0.7f),
                                focusedContentColor = Color.Black
                            ),
                            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
                            modifier = Modifier.height(36.dp)
                                .let { if (idx == 0) it.focusRequester(firstChipFR) else it }
                                .focusProperties { up = firstTabFR; down = firstResultFR }
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.History, null, Modifier.size(14.dp))
                                Text(term, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── 4. CONTENT AREA ────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val isFuzer = state.source == SearchSource.FUZER
                val results = state.activeResults

                if (state.query.isNotBlank() || state.filters.isActive || isFuzer) {
                    // Search results mode
                    when {
                        state.isLoading || state.isFuzerLoading -> ShimmerGrid()
                        isFuzer && state.fuzerError != null -> FuzerError(state.fuzerError)
                        results.isEmpty() -> EmptyStateMessage(tr("No results found", "לא נמצאו תוצאות"))
                        state.filters.isActive && state.query.isBlank() && !isFuzer -> {
                            // Category browsing: year-grouped rows
                            YearGroupedResults(results, firstResultFR, firstTabFR, { focusedBackdrop = it.backdropUrl }, onResultClick)
                        }
                        else -> SearchResultsGrid(results, isFuzer, firstResultFR, { focusedBackdrop = it.backdropUrl }, onResultClick)
                    }
                } else {
                    // Discovery mode — category grid with real backdrops
                    CategoryGrid(firstResultFR, firstTabFR, firstChipFR, state.searchHistory.isNotEmpty(), state.discoveryResults) { cat ->
                        // Apply genre + type filter based on active source tab
                        val typeFilter = when (state.source) {
                            SearchSource.MOVIES -> MediaTypeFilter.MOVIE
                            SearchSource.SERIES -> MediaTypeFilter.TV_SHOW
                            else -> MediaTypeFilter.ANY
                        }
                        val genre = if (cat.genre == "_popular_") null else cat.genre
                        val sort = if (cat.genre == "_popular_") SortBy.POPULARITY else SortBy.POPULARITY
                        val filters = SearchFilters(genre = genre, typeFilter = typeFilter, sortBy = sort,
                            ratingTier = if (cat.genre == "_popular_") RatingTier.GOOD else RatingTier.ANY)
                        onIntent(SearchIntent.UpdateFilters(filters))
                    }
                }
            }
        }

        // ── 5. FILTER OVERLAY ──────────────────────────────────────
        AnimatedVisibility(
            visible = state.showFilters,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF040408).copy(0.98f))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .focusGroup()
                    .focusProperties { onExit = { FocusRequester.Cancel } }
                    .onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) { onIntent(SearchIntent.ToggleFilters); true } else false }
            ) {
                FilterSidebar(
                    filters = state.filters, isFuzer = state.source == SearchSource.FUZER, firstFilterFR = filterFR,
                    onUpdate = { onIntent(SearchIntent.UpdateFilters(it)) },
                    onClear = { onIntent(SearchIntent.ClearFilters); onIntent(SearchIntent.ToggleFilters) },
                    onClose = { onIntent(SearchIntent.ToggleFilters) }
                )
            }
        }
    }
}

// ── Premium Category Grid ──────────────────────────────────────────────
@Composable
private fun CategoryGrid(
    firstResultFR: FocusRequester,
    navBarFR: FocusRequester,
    chipFR: FocusRequester,
    hasChips: Boolean,
    discoveryResults: List<SearchResult> = emptyList(),
    onClick: (CategoryDef) -> Unit
) {
    // Dynamically map each category to a UNIQUE backdrop from live discovery results
    val genreBackdrops = remember(discoveryResults) {
        val map = mutableMapOf<String?, String>()
        val usedUrls = mutableSetOf<String>()  // Track used URLs to prevent duplicates
        val withBackdrop = discoveryResults.filter { !it.backdropUrl.isNullOrBlank() }

        // "Top Box Office" gets the first result
        if (withBackdrop.isNotEmpty()) {
            map[null] = withBackdrop.first().backdropUrl ?: ""
            usedUrls.add(withBackdrop.first().backdropUrl ?: "")
        }

        // Match each genre to a UNIQUE backdrop — skip already-used images
        for (cat in CATEGORIES) {
            if (cat.genre != null && !map.containsKey(cat.genre)) {
                val match = withBackdrop.firstOrNull {
                    it.genre.contains(cat.genre, true) && it.backdropUrl !in usedUrls
                } ?: withBackdrop.firstOrNull {
                    it.genre.contains(cat.genre.take(4), true) && it.backdropUrl !in usedUrls
                }
                if (match != null) {
                    map[cat.genre] = match.backdropUrl ?: ""
                    usedUrls.add(match.backdropUrl ?: "")
                }
            }
        }

        // Fill remaining with any unused backdrop
        for (cat in CATEGORIES) {
            if (!map.containsKey(cat.genre)) {
                val unused = withBackdrop.firstOrNull { it.backdropUrl !in usedUrls }
                if (unused != null) {
                    map[cat.genre] = unused.backdropUrl ?: ""
                    usedUrls.add(unused.backdropUrl ?: "")
                } else if (withBackdrop.isNotEmpty()) {
                    // Absolute last resort: use poster URL instead of backdrop for variety
                    val posterFallback = discoveryResults.firstOrNull {
                        it.posterUrl.isNotBlank() && it.posterUrl !in usedUrls
                    }
                    if (posterFallback != null) {
                        map[cat.genre] = posterFallback.posterUrl
                        usedUrls.add(posterFallback.posterUrl)
                    }
                }
            }
        }
        map
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(CATEGORIES) { idx, cat ->
            PremiumCategoryCard(
                cat = cat,
                backdropUrl = genreBackdrops[cat.genre],
                modifier = Modifier
                    .let { if (idx == 0) it.focusRequester(firstResultFR) else it }
                    .focusProperties { up = if (hasChips) chipFR else navBarFR },
                onClick = { onClick(cat) }
            )
        }
    }
}

@Composable
private fun PremiumCategoryCard(
    cat: CategoryDef,
    modifier: Modifier = Modifier,
    backdropUrl: String? = null,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val imgAlpha by animateFloatAsState(if (focused) 1f else 0.7f, tween(300), label = "ia")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF0A0A0F), focusedContainerColor = Color(0xFF0A0A0F)),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(Color.White.copy(0.20f), 12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(Modifier.fillMaxSize()) {
            // Full image background
            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(backdropUrl).crossfade(400).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(imgAlpha)
                )
            }

            // Bottom gradient for text readability
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.75f to Color.Black.copy(0.5f),
                    1f to Color.Black.copy(0.9f)
                )
            ))

            // Title
            Text(
                text = if (isRtl) cat.he else cat.en,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
            )
        }
    }
}

@Composable
fun EmptyStateMessage(text: String) { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0x66FFFFFF), fontSize = 20.sp) } }