@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)
package com.luminastreams.tv.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val inputFR = remember { FocusRequester() }
    val actionRowFR = remember { FocusRequester() }
    val filterFR = remember { FocusRequester() }
    val firstResultFR = remember { FocusRequester() }

    var focusedBackdrop by remember { mutableStateOf<String?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    BackHandler {
        when {
            state.showFilters -> onIntent(SearchIntent.ToggleFilters)
            state.query.isNotBlank() -> onIntent(SearchIntent.UpdateQuery(""))
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(Unit) { delay(100); runCatching { inputFR.requestFocus() } }
    LaunchedEffect(state.showFilters) { if (state.showFilters) { delay(150); runCatching { filterFR.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
        // --- 1. רקע גיבור דינמי ---
        AnimatedContent(targetState = focusedBackdrop, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "bg") { backdrop ->
            if (!backdrop.isNullOrBlank()) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(backdrop).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.30f))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color(0x77000000), 0.5f to Color(0xDD040405), 1.0f to Color(0xFF040405))))
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFE50914).copy(0.12f), Color(0xFF040405)), radius = 1500f)))
            }
        }

        // --- 2. התוכן המרכזי ---
        Column(Modifier.fillMaxSize().focusProperties { canFocus = !state.showFilters }) {
            // שורת החיפוש
            Column(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val searchScale by animateFloatAsState(if (searchFocused) 1.03f else 1f, tween(300))
                val searchGlow by animateColorAsState(if (searchFocused) Color(0xFFE50914).copy(0.4f) else Color.Transparent, tween(300))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(72.dp)
                        .graphicsLayer { scaleX = searchScale; scaleY = searchScale }
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xFF13131A).copy(0.85f))
                        .border(2.dp, searchGlow, RoundedCornerShape(36.dp))
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Search, null, Modifier.size(32.dp), tint = if (searchFocused) Color(0xFFE50914) else Color(0x99FFFFFF))
                        BasicTextField(
                            value = state.query, onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium), cursorBrush = SolidColor(Color(0xFFE50914)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            decorationBox = { inner -> Box(Modifier.weight(1f)) { if (state.query.isEmpty()) Text("Search Movies, Series, or Fuzer...", color = Color(0x55FFFFFF), fontSize = 24.sp); inner() } },
                            modifier = Modifier.weight(1f).focusRequester(inputFR).onFocusChanged { searchFocused = it.isFocused }
                                .onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { runCatching { actionRowFR.requestFocus() }; true } else if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft && state.query.isEmpty()) { onNavigateBack(); true } else false }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action Row (Fuzer & Full Screen Filters)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val isFuzer = state.source == SearchSource.FUZER
                    val isFiltered = state.filters.isActive

                    Surface(
                        onClick = { onIntent(SearchIntent.SelectSource(if (isFuzer) SearchSource.ALL else SearchSource.FUZER)) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (isFuzer) Color(0xFFFF2D78).copy(0.15f) else Color(0xFF1A1A24), focusedContainerColor = if (isFuzer) Color(0xFFFF2D78).copy(0.3f) else Color.White, contentColor = if (isFuzer) Color(0xFFFF2D78) else Color(0xAAFFFFFF), focusedContentColor = if (isFuzer) Color.White else Color.Black),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isFuzer) Border(border = BorderStroke(1.dp, Color(0xFFFF2D78)), shape = RoundedCornerShape(50)) else Border.None,
                            focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFFFF2D78)), shape = RoundedCornerShape(50))
                        ),
                        modifier = Modifier.height(44.dp).focusRequester(actionRowFR).focusProperties { up = inputFR; down = firstResultFR }
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudQueue, null, Modifier.size(18.dp))
                            Text("Fuzer Only", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (isFuzer) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF2D78)))
                        }
                    }

                    Surface(
                        onClick = { onIntent(SearchIntent.ToggleFilters) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (isFiltered) Color(0xFFE50914).copy(0.15f) else Color(0xFF1A1A24), focusedContainerColor = if (isFiltered) Color(0xFFE50914).copy(0.3f) else Color.White, contentColor = if (isFiltered) Color(0xFFE50914) else Color(0xAAFFFFFF), focusedContentColor = if (isFiltered) Color.White else Color.Black),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isFiltered) Border(border = BorderStroke(1.dp, Color(0xFFE50914)), shape = RoundedCornerShape(50)) else Border.None,
                            focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFFE50914)), shape = RoundedCornerShape(50))
                        ),
                        modifier = Modifier.height(44.dp).focusProperties { up = inputFR; down = firstResultFR }
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                            Text(if (isFiltered) "Active Filters" else "Discovery Grid", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (isFiltered) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE50914)))
                        }
                    }
                }
            }

            // --- 3. אזור התוצאות ---
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val isFuzer = state.source == SearchSource.FUZER
                val activeList = state.activeResults

                if (state.filters.isActive || isFuzer) {
                    when {
                        state.isLoading || state.isFuzerLoading -> ShimmerGrid()
                        isFuzer && state.fuzerError != null -> FuzerError(state.fuzerError)
                        activeList.isEmpty() -> EmptyStateMessage("No results match your filters.")
                        else -> SearchResultsGrid(
                            results = activeList, isFuzer = isFuzer, firstResultFR = firstResultFR,
                            onFocusCard = { focusedBackdrop = it.backdropUrl }, onResultClick = onResultClick
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(40.dp), modifier = Modifier.fillMaxSize()) {
                        if (state.query.isNotBlank()) {
                            val movies = activeList.filter { it.type == MediaType.MOVIE }
                            val series = activeList.filter { it.type == MediaType.TV_SHOW }

                            if (movies.isNotEmpty()) item { HorizontalMediaRow("Movies", movies, false, firstResultFR, { focusedBackdrop = it.backdropUrl }, onResultClick) }
                            if (series.isNotEmpty()) item { HorizontalMediaRow("TV Shows", series, false, FocusRequester.Default, { focusedBackdrop = it.backdropUrl }, onResultClick) }

                            if (state.isFuzerLoading) item { LoadingRow("Searching Fuzer Torrents...") }
                            else if (state.fuzerResults.isNotEmpty()) item { HorizontalMediaRow("Fuzer Torrents", state.fuzerResults, true, FocusRequester.Default, { focusedBackdrop = it.backdropUrl }, onResultClick) }

                            if (!state.isLoading && !state.isFuzerLoading && movies.isEmpty() && series.isEmpty() && state.fuzerResults.isEmpty()) {
                                item { EmptyStateMessage("No results found for \"${state.query}\"") }
                            }
                        } else {
                            if (state.searchHistory.isNotEmpty()) item { HistoryRow(state.searchHistory, firstResultFR) { onIntent(SearchIntent.UpdateQuery(it)) } }
                            if (activeList.isNotEmpty()) item { HorizontalMediaRow("Trending Now", activeList, false, if (state.searchHistory.isEmpty()) firstResultFR else FocusRequester.Default, { focusedBackdrop = it.backdropUrl }, onResultClick) }
                        }
                    }
                }
            }
        }

        // --- 4. מסך ה-GRID המלא לסינונים ---
        AnimatedVisibility(
            visible = state.showFilters,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF040405).copy(alpha = 0.98f)) // רקע כהה כמעט אטום לגמרי למסך שלם
                    .clickable(remember { MutableInteractionSource() }, null) { /* Block clicks */ }
                    .focusGroup()
                    .focusProperties { onExit = { FocusRequester.Cancel }; left = FocusRequester.Cancel; right = FocusRequester.Cancel; up = FocusRequester.Cancel; down = FocusRequester.Cancel }
                    .onKeyEvent { ev -> if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) { onIntent(SearchIntent.ToggleFilters); true } else false }
            ) {
                // קריאה לקובץ הקוביות החדש שיכסה את כל המסך
                FilterSidebar(
                    filters = state.filters, isFuzer = state.source == SearchSource.FUZER, firstFilterFR = filterFR,
                    onUpdate = { onIntent(SearchIntent.UpdateFilters(it)) }, onClear = { onIntent(SearchIntent.ClearFilters); onIntent(SearchIntent.ToggleFilters) },
                    onClose = { onIntent(SearchIntent.ToggleFilters) }
                )
            }
        }
    }
}

// === קומפוננטות עזר לשורות האופקיות ===
@Composable
fun HorizontalMediaRow(title: String, items: List<SearchResult>, isFuzer: Boolean, firstItemFR: FocusRequester, onFocusCard: (SearchResult) -> Unit, onClick: (SearchResult) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 64.dp, vertical = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }) {
            itemsIndexed(items, key = { _, r -> r.id }) { idx, result ->
                MediaSearchCard(
                    result = result, isFuzer = isFuzer,
                    modifier = Modifier.width(160.dp).let { if (idx == 0) it.focusRequester(firstItemFR) else it },
                    onFocus = { onFocusCard(result) }, onClick = { onClick(result) }
                )
            }
        }
    }
}

@Composable
fun HistoryRow(history: List<String>, firstItemFR: FocusRequester, onClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Recent Searches", color = Color(0x99FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 64.dp, vertical = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(history) { idx, term ->
                Surface(
                    onClick = { onClick(term) }, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A24), focusedContainerColor = Color.White, contentColor = Color(0xDDFFFFFF), focusedContentColor = Color.Black),
                    modifier = Modifier.let { if (idx == 0) it.focusRequester(firstItemFR) else it }
                ) { Text(term, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
            }
        }
    }
}

@Composable
fun LoadingRow(text: String) { Box(Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 24.dp)) { Text(text, color = Color(0xFF00D4FF), fontSize = 16.sp, fontWeight = FontWeight.Medium) } }

@Composable
fun EmptyStateMessage(text: String) { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text(text, color = Color(0x66FFFFFF), fontSize = 20.sp) } }