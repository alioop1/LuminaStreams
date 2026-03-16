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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
private val BG       = Color(0xFF070707)
private val SURF     = Color(0xFF111111)
private val CARD_BG  = Color(0xFF161616)
private val RED      = Color(0xFFE50914)
private val WHITE    = Color(0xFFFFFFFF)
private val DIM      = Color(0xAAFFFFFF)
private val DIM2     = Color(0x44FFFFFF)
private val GOLD     = Color(0xFFFFD700)
private val SEL_BG   = Color(0x28E50914)
private val SEL_BOR  = Color(0xCCE50914)
private val IDLE_BG  = Color(0x0FFFFFFF)
private val IDLE_BOR = Color(0x18FFFFFF)

// ══════════════════════════════════════════════════════════════════
//  DATA
// ══════════════════════════════════════════════════════════════════
data class FilterState(
    val mediaType : String      = "all",
    val genres    : Set<String> = emptySet(),
    val sortBy    : String      = "popularity.desc",
    val decade    : Int         = 0,
    val minRating : Float       = 0f,
    val platforms : Set<String> = emptySet(),
    val language  : String      = "any"
)

private val GENRES_MOVIE = listOf(
    "28"    to "Action",     "12"    to "Adventure", "16"    to "Animation",
    "35"    to "Comedy",     "80"    to "Crime",      "99"    to "Documentary",
    "18"    to "Drama",      "10751" to "Family",     "14"    to "Fantasy",
    "36"    to "History",    "27"    to "Horror",     "10402" to "Music",
    "9648"  to "Mystery",    "10749" to "Romance",    "878"   to "Sci-Fi",
    "53"    to "Thriller",   "10752" to "War",        "37"    to "Western"
)
private val GENRES_TV = listOf(
    "10759" to "Action",     "16"    to "Animation",  "35"    to "Comedy",
    "80"    to "Crime",      "99"    to "Documentary", "18"    to "Drama",
    "10762" to "Kids",       "9648"  to "Mystery",    "10765" to "Sci-Fi",
    "10766" to "Soap",       "10767" to "Talk",       "10768" to "War & Politics"
)
private val SORT_OPTIONS = listOf(
    "popularity.desc"           to "Popular",
    "vote_average.desc"         to "Top Rated",
    "primary_release_date.desc" to "Newest",
    "revenue.desc"              to "Box Office",
    "vote_count.desc"           to "Most Voted"
)
private val DECADES = listOf(
    0    to "Any Era",
    2020 to "2020s",
    2010 to "2010s",
    2000 to "2000s",
    1990 to "1990s",
    1980 to "1980s",
    1970 to "1970s"
)
private val RATINGS   = listOf(0f to "Any", 5f to "5+", 6f to "6+", 7f to "7+", 8f to "8+", 9f to "9+")
private val PLATFORMS = listOf(
    "8"   to "Netflix",    "350" to "Apple TV+",
    "337" to "Disney+",    "384" to "MAX",
    "386" to "Peacock",    "387" to "HBO"
)
private val LANGUAGES = listOf(
    "any" to "Any",   "en" to "English", "he" to "Hebrew",
    "fr"  to "French","es" to "Spanish", "de" to "German",
    "ja"  to "Japanese", "ko" to "Korean"
)

// ══════════════════════════════════════════════════════════════════
//  SCREEN
//  Layout (top→bottom):
//   Header (back + title + count + reset)
//   Search bar (text input, BasicTextField)
//   Filter rows (4 horizontal chip rows, D-pad left/right)
//   Thin divider
//   Results grid (LazyVerticalGrid, fills remaining space)
// ══════════════════════════════════════════════════════════════════
@Composable
fun SearchScreen(
    state         : SearchState,
    onIntent      : (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick : (SearchResult) -> Unit
) {
    var query       by remember { mutableStateOf("") }
    var filters     by remember { mutableStateOf(FilterState()) }
    var results     by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var resultCount by remember { mutableStateOf(0) }

    val scope      = rememberCoroutineScope()
    var searchJob: Job? by remember { mutableStateOf(null) }

    val backFR  = remember { FocusRequester() }

    BackHandler { onNavigateBack() }

    LaunchedEffect(query, filters) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(if (query.isNotBlank()) 380L else 60L)
            isLoading = true
            results = if (query.isNotBlank()) fetchTextSearch(query, filters)
            else fetchDiscovery(filters)
            resultCount = results.size
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { backFR.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                    onNavigateBack(); true
                } else false
            }
    ) {

        // ── HEADER ────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick  = onNavigateBack,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = IDLE_BG,
                    focusedContainerColor = WHITE,
                    contentColor          = WHITE,
                    focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier = Modifier.size(42.dp).focusRequester(backFR)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                }
            }

            Column {
                Text("Discover", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp)
                AnimatedContent(
                    targetState = when {
                        isLoading         -> "Loading..."
                        results.isEmpty() -> "No results"
                        else              -> "$resultCount titles"
                    },
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "cnt"
                ) { txt -> Text(txt, color = DIM, fontSize = 11.sp) }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = filters != FilterState() || query.isNotBlank(),
                enter   = fadeIn() + scaleIn(initialScale = 0.85f),
                exit    = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Surface(
                    onClick  = { filters = FilterState(); query = "" },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = Color(0x1AE50914),
                        focusedContainerColor = RED,
                        contentColor          = RED,
                        focusedContentColor   = WHITE
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(11.dp))
                        Text("Reset all", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── SEARCH BAR ────────────────────────────────────────────
        SearchInputBar(
            query    = query,
            onQuery  = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(bottom = 16.dp)
        )

        // ── FILTER ROWS ───────────────────────────────────────────
        val genres = if (filters.mediaType == "tv") GENRES_TV else GENRES_MOVIE

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Type
            FilterRow("Type") {
                Chip("🌐 All",     filters.mediaType == "all")   { filters = filters.copy(mediaType = "all") }
                Chip("🎬 Movies", filters.mediaType == "movie") { filters = filters.copy(mediaType = "movie") }
                Chip("📺 Series", filters.mediaType == "tv")    { filters = filters.copy(mediaType = "tv") }
            }

            // Row 2: Genres
            FilterRow("Genre") {
                genres.forEach { (id, name) ->
                    Chip(name, id in filters.genres) {
                        filters = filters.copy(genres = if (id in filters.genres) filters.genres - id else filters.genres + id)
                    }
                }
            }

            // Row 3: Sort | Era
            FilterRow("Sort") {
                SORT_OPTIONS.forEach { (v, l) ->
                    Chip(l, filters.sortBy == v) { filters = filters.copy(sortBy = v) }
                }
                Divider()
                DECADES.forEach { (yr, l) ->
                    Chip(l, filters.decade == yr) { filters = filters.copy(decade = yr) }
                }
            }

            // Row 4: Rating | Language
            FilterRow("Rating") {
                RATINGS.forEach { (v, l) ->
                    Chip(l, filters.minRating == v) { filters = filters.copy(minRating = v) }
                }
                Divider()
                LANGUAGES.forEach { (id, l) ->
                    Chip(l, filters.language == id) { filters = filters.copy(language = id) }
                }
            }
        }

        // ── DIVIDER ───────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 40.dp).background(DIM2))
        Spacer(Modifier.height(2.dp))

        // ── RESULTS ───────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading         -> LoadingGrid()
                results.isEmpty() -> EmptyState(query)
                else              -> ResultsGrid(results, onResultClick)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  SEARCH INPUT BAR
// ══════════════════════════════════════════════════════════════════
@Composable
private fun SearchInputBar(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF181818) else SURF)
            .border(1.5.dp, if (focused) RED.copy(0.65f) else DIM2, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = if (focused) RED else DIM)
            BasicTextField(
                value           = query,
                onValueChange   = onQuery,
                singleLine      = true,
                textStyle       = TextStyle(color = WHITE, fontSize = 15.sp),
                cursorBrush     = SolidColor(RED),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                decorationBox   = { inner ->
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search movies, series, actors...", color = DIM.copy(0.45f), fontSize = 15.sp)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }
            )
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Surface(
                    onClick  = { onQuery("") },
                    shape    = ClickableSurfaceDefaults.shape(CircleShape),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor = DIM2, focusedContainerColor = RED,
                        contentColor   = WHITE, focusedContentColor  = WHITE
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  FILTER ROW
// ══════════════════════════════════════════════════════════════════
@Composable
private fun FilterRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DIM, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(54.dp))
        LazyRow(
            modifier              = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) { content() }
            }
        }
    }
}

@Composable
private fun RowScope.Divider() {
    Spacer(Modifier.width(14.dp))
    Box(Modifier.width(1.dp).height(20.dp).background(DIM2).align(Alignment.CenterVertically))
    Spacer(Modifier.width(14.dp))
}

// ══════════════════════════════════════════════════════════════════
//  CHIP
// ══════════════════════════════════════════════════════════════════
@Composable
private fun RowScope.Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (isSelected) SEL_BG else IDLE_BG,
            focusedContainerColor = if (isSelected) SEL_BG else Color(0x18FFFFFF),
            contentColor          = if (isSelected) WHITE  else DIM,
            focusedContentColor   = WHITE
        ),
        border   = ClickableSurfaceDefaults.border(
            border        = Border(androidx.compose.foundation.BorderStroke(1.dp,   if (isSelected) SEL_BOR else IDLE_BOR), shape = RoundedCornerShape(50)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) RED     else WHITE.copy(0.4f)), shape = RoundedCornerShape(50))
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = Modifier.height(28.dp)
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 11.dp), Alignment.Center) {
            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, softWrap = false, maxLines = 1)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  RESULTS GRID
// ══════════════════════════════════════════════════════════════════
@Composable
private fun ResultsGrid(results: List<SearchResult>, onResultClick: (SearchResult) -> Unit) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(148.dp),
        contentPadding        = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement   = Arrangement.spacedBy(18.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { _, result ->
            DiscoveryCard(result, onClick = { onResultClick(result) })
        }
    }
}

@Composable
private fun DiscoveryCard(result: SearchResult, onClick: () -> Unit) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.07f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "z")

    Column(horizontalAlignment = Alignment.Start) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer(scaleX = zoom, scaleY = zoom)
                .zIndex(if (focused) 8f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors   = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    Border.None,
                    Border(androidx.compose.foundation.BorderStroke(2.dp, WHITE.copy(0.85f)), shape = RoundedCornerShape(10.dp))
                ),
                glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow(WHITE.copy(0.1f), 12.dp)),
                modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.isFocused }
            ) {
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(false).build(),
                        contentDescription = result.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), CARD_BG))), Alignment.Center) {
                        Text(result.title, color = WHITE.copy(0.3f), fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                if (result.rating > 0f) {
                    Box(Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("★ %.1f".format(result.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (result.releaseYear.isNotBlank()) {
                    Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(result.releaseYear, color = DIM, fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(result.title, color = if (focused) WHITE else DIM, fontSize = 11.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (result.type == MediaType.TV_SHOW) "TV Show" else "Movie", color = WHITE.copy(0.25f), fontSize = 10.sp)
    }
}

// ══════════════════════════════════════════════════════════════════
//  LOADING
// ══════════════════════════════════════════════════════════════════
@Composable
private fun LoadingGrid() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF212121), Color(0xFF111111)),
        start = androidx.compose.ui.geometry.Offset(p * 1600f - 800f, 0f),
        end   = androidx.compose.ui.geometry.Offset(p * 1600f, 400f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(148.dp),
        contentPadding        = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement   = Arrangement.spacedBy(18.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(18) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(shimmer))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.75f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════════════
@Composable
private fun EmptyState(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎬", fontSize = 48.sp)
            Text(if (query.isNotBlank()) "No results for \"$query\"" else "No results found", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Try different filters or search terms", color = DIM, fontSize = 13.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  NETWORK — TEXT SEARCH  (TMDB /search/multi)
// ══════════════════════════════════════════════════════════════════
private suspend fun fetchTextSearch(query: String, filters: FilterState): List<SearchResult> =
    withContext(Dispatchers.IO) {
        val apiKey  = "9ab4a284f0c028007b78925852196b79"
        val imgBase = "https://image.tmdb.org/t/p"
        val enc     = URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResult>()
        try {
            val con = (URL("https://api.themoviedb.org/3/search/multi?api_key=$apiKey&language=en-US&query=$enc&page=1&include_adult=false").openConnection() as HttpURLConnection)
                .also { it.connectTimeout = 5000; it.readTimeout = 8000 }
            if (con.responseCode == 200) {
                val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val j  = arr.getJSONObject(i)
                    val mt = j.optString("media_type")
                    if (mt != "movie" && mt != "tv") continue
                    if (filters.mediaType != "all" && filters.mediaType != mt) continue
                    val title  = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                    else            j.optString("title").ifBlank { j.optString("original_title") }
                    val poster = j.optString("poster_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w342$it" else "" }
                    val year   = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4)
                    val rating = j.optDouble("vote_average", 0.0).toFloat()
                    if (filters.minRating > 0f && rating < filters.minRating) continue
                    results += SearchResult(
                        id          = "${mt}_${j.optInt("id")}",
                        title       = title,
                        posterUrl   = poster,
                        backdropUrl = j.optString("backdrop_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w780$it" else "" },
                        type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                        rating      = rating,
                        releaseYear = year
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

// ══════════════════════════════════════════════════════════════════
//  NETWORK — DISCOVER  (TMDB /discover)
// ══════════════════════════════════════════════════════════════════
private suspend fun fetchDiscovery(filters: FilterState): List<SearchResult> =
    withContext(Dispatchers.IO) {
        val apiKey  = "9ab4a284f0c028007b78925852196b79"
        val imgBase = "https://image.tmdb.org/t/p"
        val base    = "https://api.themoviedb.org/3"
        val types   = when (filters.mediaType) { "movie" -> listOf("movie"); "tv" -> listOf("tv"); else -> listOf("movie", "tv") }
        val all     = mutableListOf<SearchResult>()

        for (mt in types) {
            try {
                val sb = StringBuilder("$base/discover/$mt?api_key=$apiKey&language=en-US&page=1&sort_by=${filters.sortBy}")
                if (filters.genres.isNotEmpty())  sb.append("&with_genres=${filters.genres.joinToString(",")}")
                if (filters.decade > 0) {
                    val f = if (mt == "movie") "primary_release_date" else "first_air_date"
                    val e = if (filters.decade == 2020) 2026 else filters.decade + 9
                    sb.append("&${f}.gte=${filters.decade}-01-01&${f}.lte=$e-12-31")
                }
                if (filters.minRating > 0f) sb.append("&vote_average.gte=${filters.minRating}&vote_count.gte=100")
                if (filters.platforms.isNotEmpty()) sb.append("&with_watch_providers=${filters.platforms.joinToString("|")}&watch_region=US")
                if (filters.language != "any") sb.append("&with_original_language=${filters.language}")

                val con = (URL(sb.toString()).openConnection() as HttpURLConnection).also { it.connectTimeout = 5000; it.readTimeout = 8000 }
                if (con.responseCode == 200) {
                    val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: continue
                    for (i in 0 until minOf(arr.length(), 40)) {
                        val j      = arr.getJSONObject(i)
                        val title  = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                        else            j.optString("title").ifBlank { j.optString("original_title") }
                        val poster = j.optString("poster_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w342$it" else "" }
                        if (poster.isBlank()) continue
                        all += SearchResult(
                            id          = "${mt}_${j.optInt("id")}",
                            title       = title,
                            posterUrl   = poster,
                            backdropUrl = j.optString("backdrop_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w780$it" else "" },
                            type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                            rating      = j.optDouble("vote_average", 0.0).toFloat(),
                            releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        all.distinctBy { it.id }
    }