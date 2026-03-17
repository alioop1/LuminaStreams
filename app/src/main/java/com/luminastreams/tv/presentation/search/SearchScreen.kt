@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ══════════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════════
private val BG        = Color(0xFF070707)
private val PANEL_BG  = Color(0xFF0C0C0C)
private val CARD_BG   = Color(0xFF161616)
private val RED       = Color(0xFFE50914)
private val WHITE     = Color(0xFFFFFFFF)
private val DIM       = Color(0xAAFFFFFF)
private val DIM2      = Color(0x44FFFFFF)
private val DIM3      = Color(0x18FFFFFF)
private val GOLD      = Color(0xFFFFD700)
private val SEL_BG    = Color(0x28E50914)
private val SEL_BOR   = Color(0xCCE50914)
private val IDLE_BG   = Color(0x0FFFFFFF)
private val IDLE_BOR  = Color(0x18FFFFFF)

// ══════════════════════════════════════════════════════════════════
//  DATA MODELS
// ══════════════════════════════════════════════════════════════════
data class FilterState(
    val mediaType : String      = "all",
    val genres    : Set<String> = emptySet(),
    val sortBy    : String      = "popularity.desc",
    val decade    : Int         = 0,
    val minRating : Float       = 0f,
    val language  : String      = "any",
    val platforms : Set<String> = emptySet()
) {
    val activeCount: Int get() = listOf(
        mediaType != "all",
        genres.isNotEmpty(),
        sortBy != "popularity.desc",
        decade != 0,
        minRating > 0f,
        language != "any",
        platforms.isNotEmpty()
    ).count { it }
}

private val GENRES_MOVIE = listOf(
    "28" to "Action",      "12" to "Adventure",  "16" to "Animation",
    "35" to "Comedy",      "80" to "Crime",       "99" to "Documentary",
    "18" to "Drama",   "10751" to "Family",       "14" to "Fantasy",
    "36" to "History",    "27" to "Horror",    "10402" to "Music",
    "9648" to "Mystery",  "10749" to "Romance",    "878" to "Sci-Fi",
    "53" to "Thriller",  "10752" to "War",         "37" to "Western"
)
private val GENRES_TV = listOf(
    "10759" to "Action",     "16" to "Animation",   "35" to "Comedy",
    "80" to "Crime",      "99" to "Documentary", "18" to "Drama",
    "10762" to "Kids",     "9648" to "Mystery",  "10765" to "Sci-Fi",
    "10766" to "Soap",    "10767" to "Talk",     "10768" to "War & Politics"
)
private val SORT_OPTIONS = listOf(
    "popularity.desc"           to "Most Popular",
    "vote_average.desc"         to "Top Rated",
    "primary_release_date.desc" to "Newest First",
    "revenue.desc"              to "Box Office",
    "vote_count.desc"           to "Most Voted"
)
private val DECADES = listOf(
    0 to "Any Era", 2020 to "2020s", 2010 to "2010s",
    2000 to "2000s", 1990 to "90s", 1980 to "80s", 1970 to "70s"
)
private val RATINGS   = listOf(0f to "Any", 5f to "5+", 6f to "6+", 7f to "7+", 8f to "8+", 9f to "9+")
private val LANGUAGES = listOf(
    "any" to "🌍 Any",   "en" to "🇺🇸 English", "he" to "🇮🇱 Hebrew",
    "fr"  to "🇫🇷 French","es" to "🇪🇸 Spanish", "de" to "🇩🇪 German",
    "ja"  to "🇯🇵 Japanese","ko" to "🇰🇷 Korean"
)
private val PLATFORMS = listOf(
    "8"   to "https://images.ctfassets.net/y2ske730sjqp/4aEQ1zAUZF5pLSDtfviWjb/ba04f8d5bd01428f6e3803cc6effaf30/Netflix_N.png", // Netflix
    "337" to "https://image.tmdb.org/t/p/w92/97yvRBw1GzX7fXprcF80er19ot.jpg", // Disney+
    "350" to "https://image.tmdb.org/t/p/w92/6uhKBfmtzFqOcLousHwZuzcrScK.jpg", // Apple TV+
    "384" to "https://image.tmdb.org/t/p/w92/6YZ2Qk212u4eZ4WzEBSYwQJntWz.jpg", // Max
    "387" to "https://image.tmdb.org/t/p/w92/aS2zvJWn9mwiCOeaa8hFhnwNCB5.jpg", // HBO Max
    "386" to "https://image.tmdb.org/t/p/w92/xTHq2oDheY2p9W8e4j2iI0Zt2L8.jpg", // Peacock
    "10"  to "https://image.tmdb.org/t/p/w92/68MNrwlkpF7WnmNPXLah69CR5cb.jpg", // Amazon Prime
    "15"  to "https://image.tmdb.org/t/p/w92/giwM8XX4V2AQb9vsoN7yti82tKK.jpg", // Hulu
    "531" to "https://image.tmdb.org/t/p/w92/fi83B1bZV9GpnwO1XkS1aOOEQH3.jpg", // Paramount+
    "257" to "https://image.tmdb.org/t/p/w92/2wjcjwXoW2R0I5k0j4k0w1B2lVw.jpg", // Fubo
    "190" to "https://image.tmdb.org/t/p/w92/2fF80l9HnI1aP5BvV0R8G8XpY0n.jpg"  // Discovery+
)
private val SEARCH_HINTS = listOf(
    "Search movies, series, actors...",
    "Try \"Inception\" or \"Breaking Bad\"...",
    "Search by actor name...",
    "Discover something new..."
)

// ══════════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ══════════════════════════════════════════════════════════════════
@Composable
fun SearchScreen(
    state          : SearchState,
    onIntent       : (SearchIntent) -> Unit,
    onNavigateBack : () -> Unit,
    onResultClick  : (SearchResult) -> Unit
) {
    // ── State ──────────────────────────────────────────────────
    var query        by remember { mutableStateOf("") }
    var filters      by remember { mutableStateOf(FilterState()) }
    var results      by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading    by remember { mutableStateOf(false) }
    var currentPage  by remember { mutableStateOf(1) }
    var isFetching   by remember { mutableStateOf(false) }
    var endReached   by remember { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var showHistory  by remember { mutableStateOf(false) }
    var lastFocusedResultIdx by remember { mutableStateOf(0) }

    val scope     = rememberCoroutineScope()
    var searchJob : Job? by remember { mutableStateOf(null) }

    // ── Focus requesters ────────────────────────────────────────
    val backFR       = remember { FocusRequester() }
    val inputFR      = remember { FocusRequester() }
    val firstFilterFR= remember { FocusRequester() }
    val firstResultFR= remember { FocusRequester() }

    BackHandler {
        when {
            query.isNotBlank() -> { query = ""; showHistory = true }
            else               -> onNavigateBack()
        }
    }

    // Trigger for Discover Now button
    fun triggerSearch() {
        showHistory = false
        if (query.isNotBlank()) {
            searchHistory = (listOf(query) + searchHistory).distinct().take(8)
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            isLoading = true
            currentPage = 2
            endReached = false

            // שימוש ב-async לביצוע 2 קריאות במקביל
            val deferred1 = async { if (query.isNotBlank()) fetchTextSearch(query, filters, 1) else fetchDiscovery(filters, 1) }
            val deferred2 = async { if (query.isNotBlank()) fetchTextSearch(query, filters, 2) else fetchDiscovery(filters, 2) }

            val p1 = deferred1.await()
            val p2 = deferred2.await()

            results = (p1 + p2).distinctBy { it.id }
            isLoading = false
            if (results.isNotEmpty()) {
                delay(100)
                runCatching { firstResultFR.requestFocus() }
            }
        }
    }

    // Initial load on first open
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { backFR.requestFocus() }
        isLoading = true
        currentPage = 2
        endReached = false

        // שימוש ב-async לביצוע 2 קריאות במקביל
        val deferred1 = async { fetchDiscovery(filters, 1) }
        val deferred2 = async { fetchDiscovery(filters, 2) }

        val p1 = deferred1.await()
        val p2 = deferred2.await()

        results = (p1 + p2).distinctBy { it.id }
        isLoading = false
    }

    // ── Root layout ─────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Back || ev.key == Key.Escape)) {
                    if (query.isNotBlank()) {
                        query = ""; showHistory = true; true
                    } else {
                        onNavigateBack(); true
                    }
                } else false
            }
    ) {
        // ── HEADER ────────────────────────────────────────────
        DiscoverHeader(
            query        = query,
            filters      = filters,
            resultCount  = results.size,
            isLoading    = isLoading,
            backFR       = backFR,
            onBack       = onNavigateBack,
            onReset      = { filters = FilterState(); query = "" }
        )

        // ── BODY: Left panel + Right grid ─────────────────────
        Row(Modifier.weight(1f).fillMaxWidth()) {

            // ── LEFT FILTER PANEL ───────────────
            FilterPanel(
                filters       = filters,
                query         = query,
                searchHistory = searchHistory,
                showHistory   = showHistory,
                inputFR       = inputFR,
                firstFilterFR = firstFilterFR,
                firstResultFR = firstResultFR,
                onQuery       = { q ->
                    query = q
                    showHistory = q.isEmpty()
                },
                onSearch      = { triggerSearch() },
                onHistorySelect = { q ->
                    query = q; showHistory = false
                },
                onHistoryClear  = { searchHistory = emptyList() },
                onFilterChange  = { filters = it }
            )

            // ── RIGHT RESULTS PANEL ───────────────────────────
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BG)
            ) {
                when {
                    isLoading              -> ShimmerGrid()
                    results.isEmpty()      -> EmptyState(query, filters.activeCount)
                    else                   -> ResultsGrid(
                        results          = results,
                        firstResultFR    = firstResultFR,
                        initialFocusIdx  = lastFocusedResultIdx,
                        onFocusIdx       = { lastFocusedResultIdx = it },
                        onResultClick    = onResultClick,
                        onLoadMore       = {
                            if (!isFetching && !endReached) {
                                isFetching = true
                                scope.launch {
                                    currentPage++
                                    val newRes = if (query.isNotBlank()) fetchTextSearch(query, filters, currentPage)
                                    else fetchDiscovery(filters, currentPage)
                                    if (newRes.isEmpty()) endReached = true
                                    else results = (results + newRes).distinctBy { it.id }
                                    isFetching = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  HEADER
// ══════════════════════════════════════════════════════════════════
@Composable
private fun DiscoverHeader(
    query       : String,
    filters     : FilterState,
    resultCount : Int,
    isLoading   : Boolean,
    backFR      : FocusRequester,
    onBack      : () -> Unit,
    onReset     : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(PANEL_BG)
            .border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 24.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            onClick  = onBack,
            shape    = ClickableSurfaceDefaults.shape(CircleShape),
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = DIM3,
                focusedContainerColor = WHITE,
                contentColor          = WHITE,
                focusedContentColor   = BG
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.size(38.dp).focusRequester(backFR)
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
            }
        }

        // Logo
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(RED),
                Alignment.Center
            ) { Text("L", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black) }
            Column {
                Text("LUMINA",  color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp, lineHeight = 12.sp)
                Text("STREAMS", color = RED,   fontSize = 6.5.sp, fontWeight = FontWeight.Bold,  letterSpacing = 2.4.sp, lineHeight = 7.5.sp)
            }
        }

        Box(Modifier.width(1.dp).height(24.dp).background(DIM2))

        Text("Discover", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)

        AnimatedContent(
            targetState = when {
                isLoading        -> "Loading..."
                resultCount == 0 -> "No results"
                else             -> "$resultCount titles"
            },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "cnt"
        ) { t -> Text(t, color = DIM, fontSize = 11.sp) }

        Spacer(Modifier.weight(1f))

        if (filters.activeCount > 0 || query.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SEL_BG)
                    .border(1.dp, SEL_BOR, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                val cnt = filters.activeCount + (if (query.isNotBlank()) 1 else 0)
                Text("$cnt active", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedVisibility(
            visible = filters.activeCount > 0 || query.isNotBlank(),
            enter   = fadeIn() + scaleIn(initialScale = 0.88f),
            exit    = fadeOut() + scaleOut(targetScale = 0.88f)
        ) {
            Surface(
                onClick  = onReset,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color(0x1AE50914),
                    focusedContainerColor = RED,
                    contentColor          = RED,
                    focusedContentColor   = WHITE
                ),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border(androidx.compose.foundation.BorderStroke(1.dp,   RED.copy(0.4f)), shape = RoundedCornerShape(50)),
                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, WHITE.copy(0.6f)), shape = RoundedCornerShape(50))
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(30.dp)
            ) {
                Box(
                    Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                    Alignment.Center
                ) {
                    Text(
                        "Reset",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap   = false
                    )
                }
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(listOf(RED.copy(0.7f), RED.copy(0.15f), Color.Transparent)))
    )
}

// ══════════════════════════════════════════════════════════════════
//  LEFT FILTER PANEL
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FilterPanel(
    filters         : FilterState,
    query           : String,
    searchHistory   : List<String>,
    showHistory     : Boolean,
    inputFR         : FocusRequester,
    firstFilterFR   : FocusRequester,
    firstResultFR   : FocusRequester,
    onQuery         : (String) -> Unit,
    onSearch        : () -> Unit,
    onHistorySelect : (String) -> Unit,
    onHistoryClear  : () -> Unit,
    onFilterChange  : (FilterState) -> Unit
) {
    val genres      = if (filters.mediaType == "tv") GENRES_TV else GENRES_MOVIE
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(PANEL_BG)
            .border(width = 1.dp, color = Color(0x10FFFFFF),
                shape = RoundedCornerShape(0.dp))
            .focusGroup()
    ) {
        PanelSearchBar(
            query         = query,
            onQuery       = onQuery,
            onSearch      = onSearch,
            inputFR       = inputFR,
            firstResultFR = firstResultFR,
            firstFilterFR = firstFilterFR
        )

        AnimatedVisibility(
            visible = showHistory && searchHistory.isNotEmpty(),
            enter   = expandVertically(tween(200)) + fadeIn(tween(150)),
            exit    = shrinkVertically(tween(150)) + fadeOut(tween(100))
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF0A0A0A))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Recent", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        onClick  = onHistoryClear,
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = Color(0x18FFFFFF),
                            contentColor          = DIM, focusedContentColor = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.height(20.dp)
                    ) { Text("Clear", color = DIM, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                searchHistory.forEach { h ->
                    Surface(
                        onClick  = { onHistorySelect(h) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = DIM3,
                            contentColor          = DIM, focusedContentColor = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth().height(30.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = DIM)
                            Text(h, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {

            FilterSectionHeader("Content Type", Icons.Default.MovieFilter)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("all" to "🌐 All", "movie" to "🎬 Movies", "tv" to "📺 Series")
                    .forEachIndexed { idx, (v, l) ->
                        val isSel = filters.mediaType == v
                        Surface(
                            onClick  = { onFilterChange(filters.copy(mediaType = v, genres = emptySet())) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = if (isSel) RED    else IDLE_BG,
                                focusedContainerColor = if (isSel) Color(0xFFFF2A2A) else Color(0x22FFFFFF),
                                contentColor          = WHITE,
                                focusedContentColor   = WHITE
                            ),
                            border   = ClickableSurfaceDefaults.border(
                                border        = Border(androidx.compose.foundation.BorderStroke(1.dp,   if (isSel) RED.copy(0.8f) else IDLE_BOR), shape = RoundedCornerShape(8.dp)),
                                focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, WHITE.copy(0.5f)), shape = RoundedCornerShape(8.dp))
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            modifier = Modifier.weight(1f).height(34.dp)
                                .let { if (idx == 0) it.focusRequester(firstFilterFR) else it }
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(l, fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (isSel) WHITE else DIM,
                                    softWrap = false)
                            }
                        }
                    }
            }

            FilterSectionHeader(
                "Genre${if (filters.genres.isNotEmpty()) " (${filters.genres.size})" else ""}",
                Icons.Default.Category
            )
            GenreGrid(
                genres   = genres,
                selected = filters.genres,
                onToggle = { id ->
                    onFilterChange(filters.copy(
                        genres = if (id in filters.genres) filters.genres - id else filters.genres + id
                    ))
                }
            )

            FilterSectionDivider()
            FilterSectionHeader("Sort By", Icons.Default.Sort)
            SORT_OPTIONS.forEach { (v, l) ->
                RadioRow(
                    label    = l,
                    selected = filters.sortBy == v,
                    onClick  = { onFilterChange(filters.copy(sortBy = v)) }
                )
            }

            FilterSectionDivider()
            FilterSectionHeader("Era", Icons.Default.CalendarToday)
            SimplePillRow(
                items    = DECADES.map { (yr, l) -> yr.toString() to l },
                selected = filters.decade.toString(),
                onSelect = { v -> onFilterChange(filters.copy(decade = v.toIntOrNull() ?: 0)) }
            )

            FilterSectionDivider()
            FilterSectionHeader("Min Rating", Icons.Default.Star)
            SimplePillRow(
                items    = RATINGS.map { (v, l) -> v.toString() to l },
                selected = filters.minRating.toString(),
                onSelect = { v -> onFilterChange(filters.copy(minRating = v.toFloatOrNull() ?: 0f)) }
            )

            FilterSectionDivider()
            FilterSectionHeader("Language", Icons.Default.Language)
            SimplePillRow(
                items    = LANGUAGES,
                selected = filters.language,
                onSelect = { v -> onFilterChange(filters.copy(language = v)) }
            )

            FilterSectionDivider()
            FilterSectionHeader(
                "Platform${if (filters.platforms.isNotEmpty()) " (${filters.platforms.size})" else ""}",
                Icons.Default.Tv
            )
            SimplePillRow(
                items       = PLATFORMS,
                selectedSet = filters.platforms,
                multiSelect = true,
                onToggle    = { v ->
                    onFilterChange(filters.copy(
                        platforms = if (v in filters.platforms) filters.platforms - v else filters.platforms + v
                    ))
                }
            )

            Spacer(Modifier.height(14.dp))
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Surface(
                onClick  = onSearch,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    contentColor          = WHITE,
                    focusedContentColor   = WHITE
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                glow     = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(RED.copy(0.6f), 24.dp)
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFFE50914), Color(0xFFFF3B3B))),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = WHITE)
                        Text(
                            "Discover Now",
                            fontSize      = 14.sp,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color         = WHITE
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PANEL SEARCH BAR (FIXED)
// ══════════════════════════════════════════════════════════════════
@Composable
private fun PanelSearchBar(
    query         : String,
    onQuery       : (String) -> Unit,
    onSearch      : () -> Unit,
    inputFR       : FocusRequester,
    firstResultFR : FocusRequester,
    firstFilterFR : FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }

    // הפתרון המוחלט לקריסה: שימוש במנהל המקלדת המובנה של אנדרואיד במקום זה של Compose
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val imm = remember { context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager }

    var hintIdx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3200); hintIdx = (hintIdx + 1) % SEARCH_HINTS.size }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF181818) else Color(0xFF0F0F0F))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) RED.copy(0.65f) else DIM2,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(15.dp),
                tint = if (isFocused) RED else DIM)

            BasicTextField(
                value           = query,
                onValueChange   = onQuery,
                singleLine      = true,
                textStyle       = TextStyle(color = WHITE, fontSize = 13.sp),
                cursorBrush     = SolidColor(RED),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        // העלמת המקלדת בצורה בטוחה דרך המערכת
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                        onSearch()
                    }
                ),
                decorationBox   = { inner ->
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty() && !isFocused) {
                            AnimatedContent(
                                targetState = hintIdx,
                                transitionSpec = {
                                    fadeIn(tween(350)) + slideInVertically { 8 } togetherWith
                                            fadeOut(tween(250)) + slideOutVertically { -8 }
                                },
                                label = "hint"
                            ) { idx -> Text(SEARCH_HINTS[idx], color = DIM.copy(0.38f), fontSize = 13.sp) }
                        } else if (query.isEmpty()) {
                            Text("Type to search...", color = DIM.copy(0.38f), fontSize = 13.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFR)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { ev ->
                        when {
                            ev.type == KeyEventType.KeyDown &&
                                    ev.key  == Key.DirectionCenter -> {
                                // הקפצת המקלדת בצורה בטוחה בלי Compose
                                imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                true
                            }
                            ev.type == KeyEventType.KeyDown &&
                                    ev.key  == Key.DirectionRight &&
                                    query.isEmpty() -> {
                                runCatching { firstResultFR.requestFocus() }
                                true
                            }
                            ev.type == KeyEventType.KeyDown &&
                                    ev.key  == Key.DirectionDown -> {
                                runCatching { firstFilterFR.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    }
            )

            AnimatedVisibility(
                query.isNotEmpty(),
                enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.85f),
                exit  = fadeOut(tween(80))  + scaleOut(targetScale = 0.85f)
            ) {
                Surface(
                    onClick  = { onQuery("") },
                    shape    = ClickableSurfaceDefaults.shape(CircleShape),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = DIM2,
                        focusedContainerColor = RED,
                        contentColor          = WHITE,
                        focusedContentColor   = WHITE
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(10.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FILTER SECTION HELPERS
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FilterSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, null, Modifier.size(11.dp), tint = RED)
        Text(title, color = DIM, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun FilterSectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 14.dp)
            .background(Color(0x0AFFFFFF))
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun GenreGrid(
    genres  : List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val rows = (genres.size + 2) / 3
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (c in 0..2) {
                    val idx = r * 3 + c
                    if (idx < genres.size) {
                        val (id, name) = genres[idx]
                        val isSel = id in selected
                        Surface(
                            onClick  = { onToggle(id) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(7.dp)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = if (isSel) SEL_BG   else IDLE_BG,
                                focusedContainerColor = if (isSel) Color(0x38E50914) else Color(0x18FFFFFF),
                                contentColor          = if (isSel) WHITE    else DIM,
                                focusedContentColor   = WHITE
                            ),
                            border   = filterBorder(isSel),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.weight(1f).height(30.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    name,
                                    fontSize   = 9.5.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    softWrap   = false,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (selected) SEL_BG else Color.Transparent,
            focusedContainerColor = if (selected) Color(0x38E50914) else Color(0x10FFFFFF),
            contentColor          = if (selected) WHITE else DIM,
            focusedContentColor   = WHITE
        ),
        border   = ClickableSurfaceDefaults.border(Border.None, Border.None),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 10.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (selected) RED else Color.Transparent)
                    .border(1.dp, if (selected) RED else DIM2, CircleShape)
            )
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SimplePillRow(
    items       : List<Pair<String, String>>,
    selected    : String       = "",
    selectedSet : Set<String>  = emptySet(),
    multiSelect : Boolean      = false,
    onSelect    : (String) -> Unit = {},
    onToggle    : (String) -> Unit = {}
) {
    val firstFR = remember { FocusRequester() }
    val context = LocalContext.current

    LazyRow(
        Modifier.fillMaxWidth().focusRestorer { firstFR },
        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { idx, (id, value) ->
            val isSel = if (multiSelect) id in selectedSet else id == selected
            val isImageUrl = value.startsWith("http")

            Surface(
                onClick  = { if (multiSelect) onToggle(id) else onSelect(id) },
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = if (isSel) SEL_BG else IDLE_BG,
                    focusedContainerColor = if (isSel) Color(0x38E50914) else Color(0x28FFFFFF),
                    contentColor          = WHITE,
                    focusedContentColor   = WHITE
                ),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border(androidx.compose.foundation.BorderStroke(1.dp,   if (isSel) SEL_BOR else IDLE_BOR), shape = RoundedCornerShape(8.dp)),
                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, if (isSel) RED     else WHITE.copy(0.5f)), shape = RoundedCornerShape(8.dp))
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier = Modifier
                    .height(36.dp)
                    .let { if (idx == 0) it.focusRequester(firstFR) else it }
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    Alignment.Center
                ) {
                    if (isImageUrl) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(value)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Text(
                            value,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun filterBorder(isSelected: Boolean) = ClickableSurfaceDefaults.border(
    border        = Border(androidx.compose.foundation.BorderStroke(1.dp,   if (isSelected) SEL_BOR else IDLE_BOR), shape = RoundedCornerShape(7.dp)),
    focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) RED     else WHITE.copy(0.35f)), shape = RoundedCornerShape(7.dp))
)

// ══════════════════════════════════════════════════════════════════
//  RESULTS GRID
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ResultsGrid(
    results         : List<SearchResult>,
    firstResultFR   : FocusRequester,
    initialFocusIdx : Int,
    onFocusIdx      : (Int) -> Unit,
    onResultClick   : (SearchResult) -> Unit,
    onLoadMore      : () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(results.size) {
        if (results.isNotEmpty() && results.size <= 40) {
            delay(100)
            runCatching { firstResultFR.requestFocus() }
        }
    }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 138.dp),
        state                 = gridState,
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(14.dp),
        modifier              = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) false else false
            }
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            if (idx >= results.size - 8) {
                LaunchedEffect(idx) { onLoadMore() }
            }

            DiscoveryCard(
                result    = result,
                modifier  = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onFocused = { onFocusIdx(idx) },
                onClick   = { onResultClick(result) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  DISCOVERY CARD
// ══════════════════════════════════════════════════════════════════
@Composable
private fun DiscoveryCard(
    result   : SearchResult,
    modifier : Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick  : () -> Unit
) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label         = "zoom"
    )

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .zIndex(if (focused) 8f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(Border.None, Border.None),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = zoom, scaleY = zoom)
                    .onFocusChanged { fs -> focused = fs.isFocused; if (fs.isFocused) onFocused() }
            ) {
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(false).build(),
                        contentDescription = result.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), CARD_BG))),
                        Alignment.Center
                    ) {
                        Text(result.title, color = WHITE.copy(0.3f), fontSize = 9.sp, modifier = Modifier.padding(6.dp))
                    }
                }

                if (result.releaseYear.isNotBlank()) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Color(0xAA000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) { Text(result.releaseYear, color = DIM, fontSize = 8.sp) }
                }

                if (result.rating > 0f) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Color(0xBB000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("★ %.1f".format(result.rating), color = GOLD, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            result.title,
            color      = if (focused) WHITE else DIM,
            fontSize   = 10.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Text(
            if (result.type == MediaType.TV_SHOW) "TV Show" else "Movie",
            color = WHITE.copy(0.22f), fontSize = 9.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  SHIMMER LOADING
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF1E1E1E), Color(0xFF111111)),
        start = androidx.compose.ui.geometry.Offset(p * 1400f - 700f, 0f),
        end   = androidx.compose.ui.geometry.Offset(p * 1400f, 300f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(138.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(14.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(16) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(9.dp)).background(shimmer))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.72f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════════════
@Composable
private fun EmptyState(query: String, activeFilters: Int) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🎬", fontSize = 52.sp)
            Text(
                if (query.isNotBlank()) "No results for \"$query\""
                else "Nothing found",
                color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(
                if (activeFilters > 0) "Try adjusting your filters ← "
                else "Start typing or select filters ← ",
                color = DIM, fontSize = 12.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NETWORK — TEXT SEARCH
// ══════════════════════════════════════════════════════════════════
private suspend fun fetchTextSearch(query: String, f: FilterState, page: Int = 1): List<SearchResult> =
    withContext(Dispatchers.IO) {
        val key     = "9ab4a284f0c028007b78925852196b79"
        val imgBase = "https://image.tmdb.org/t/p"
        val enc     = URLEncoder.encode(query, "UTF-8")
        val out     = mutableListOf<SearchResult>()
        try {
            val con = (URL("https://api.themoviedb.org/3/search/multi?api_key=$key&language=en-US&query=$enc&page=$page&include_adult=false")
                .openConnection() as HttpURLConnection)
                .also { it.connectTimeout = 6000; it.readTimeout = 9000 }
            if (con.responseCode == 200) {
                val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                    .optJSONArray("results") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val j  = arr.getJSONObject(i)
                    val mt = j.optString("media_type")
                    if (mt != "movie" && mt != "tv") continue
                    if (f.mediaType != "all" && f.mediaType != mt) continue
                    val title  = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                    else j.optString("title").ifBlank { j.optString("original_title") }
                    val poster = j.optString("poster_path").let {
                        if (it.isNotBlank() && it != "null") "$imgBase/w342$it" else ""
                    }
                    val rating = j.optDouble("vote_average", 0.0).toFloat()
                    if (f.minRating > 0f && rating < f.minRating) continue
                    out += SearchResult(
                        id          = "${mt}_${j.optInt("id")}",
                        title       = title,
                        posterUrl   = poster,
                        backdropUrl = j.optString("backdrop_path").let {
                            if (it.isNotBlank() && it != "null") "$imgBase/w780$it" else ""
                        },
                        type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                        rating      = rating,
                        releaseYear = (if (mt == "tv") j.optString("first_air_date")
                        else j.optString("release_date")).take(4)
                    )
                }
            }
        } catch (_: Exception) {}
        out
    }

// ══════════════════════════════════════════════════════════════════
//  NETWORK — DISCOVER
// ══════════════════════════════════════════════════════════════════
private suspend fun fetchDiscovery(f: FilterState, page: Int = 1): List<SearchResult> =
    withContext(Dispatchers.IO) {
        val key     = "9ab4a284f0c028007b78925852196b79"
        val imgBase = "https://image.tmdb.org/t/p"
        val base    = "https://api.themoviedb.org/3"
        val types   = when (f.mediaType) {
            "movie" -> listOf("movie")
            "tv"    -> listOf("tv")
            else    -> listOf("movie", "tv")
        }
        val all = mutableListOf<SearchResult>()

        for (mt in types) {
            try {
                val sb = StringBuilder("$base/discover/$mt?api_key=$key&language=en-US&page=$page&sort_by=${f.sortBy}")
                if (f.genres.isNotEmpty())
                    sb.append("&with_genres=${f.genres.joinToString(",")}")
                if (f.decade > 0) {
                    val field = if (mt == "movie") "primary_release_date" else "first_air_date"
                    val end   = if (f.decade == 2020) 2026 else f.decade + 9
                    sb.append("&${field}.gte=${f.decade}-01-01&${field}.lte=$end-12-31")
                }
                if (f.minRating > 0f)
                    sb.append("&vote_average.gte=${f.minRating}&vote_count.gte=100")
                if (f.platforms.isNotEmpty())
                    sb.append("&with_watch_providers=${f.platforms.joinToString("|")}&watch_region=US")
                if (f.language != "any")
                    sb.append("&with_original_language=${f.language}")

                val con = (URL(sb.toString()).openConnection() as HttpURLConnection)
                    .also { it.connectTimeout = 6000; it.readTimeout = 9000 }
                if (con.responseCode == 200) {
                    val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                        .optJSONArray("results") ?: continue
                    for (i in 0 until arr.length()) {
                        val j     = arr.getJSONObject(i)
                        val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                        else j.optString("title").ifBlank { j.optString("original_title") }
                        val poster = j.optString("poster_path").let {
                            if (it.isNotBlank() && it != "null") "$imgBase/w342$it" else ""
                        }
                        if (poster.isBlank()) continue
                        all += SearchResult(
                            id          = "${mt}_${j.optInt("id")}",
                            title       = title,
                            posterUrl   = poster,
                            backdropUrl = j.optString("backdrop_path").let {
                                if (it.isNotBlank() && it != "null") "$imgBase/w780$it" else ""
                            },
                            type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                            rating      = j.optDouble("vote_average", 0.0).toFloat(),
                            releaseYear = (if (mt == "tv") j.optString("first_air_date")
                            else j.optString("release_date")).take(4)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        all.distinctBy { it.id }
    }