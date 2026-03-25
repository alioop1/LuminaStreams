@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@file:Suppress("UnusedImport")

package com.luminastreams.tv.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════
private val BG       = Color(0xFF070707)
private val PANEL_BG = Color(0xFF0C0C0E)
private val CARD_BG  = Color(0xFF161618)
private val RED      = Color(0xFFE50914)
private val WHITE    = Color(0xFFFFFFFF)
private val DIM      = Color(0xAAFFFFFF)
private val DIM2     = Color(0x33FFFFFF)
private val DIM3     = Color(0x12FFFFFF)
private val GOLD     = Color(0xFFFFD700)
private val FUZER_BLUE  = Color(0xFF00B0FF)
private val FUZER_PINK  = Color(0xFFE91E63)
private val SEL_RED  = Color(0x22E50914)

private val SEARCH_HINTS = listOf(
    "Search movies, series, actors...",
    "Try \"Inception\" or \"Breaking Bad\"...",
    "Search by actor name...",
    "Discover something new..."
)

// ══════════════════════════════════════════════════════════
//  ROOT
// ══════════════════════════════════════════════════════════
@Composable
fun SearchScreen(
    state:          SearchState,
    onIntent:       (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick:  (SearchResult) -> Unit
) {
    val backFR        = remember { FocusRequester() }
    val inputFR       = remember { FocusRequester() }
    val firstTabFR    = remember { FocusRequester() }
    val firstResultFR = remember { FocusRequester() }

    BackHandler {
        if (state.query.isNotBlank()) onIntent(SearchIntent.UpdateQuery(""))
        else onNavigateBack()
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { backFR.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.Back || ev.key == Key.Escape)) {
                    if (state.query.isNotBlank()) { onIntent(SearchIntent.UpdateQuery(""))
                    } else onNavigateBack()
                    true
                } else false
            }
    ) {
        SearchTopBar(
            state      = state,
            backFR     = backFR,
            inputFR    = inputFR,
            firstTabFR = firstTabFR,
            onBack     = onNavigateBack,
            onIntent   = onIntent
        )

        SourceTabRow(
            selected      = state.source,
            firstTabFR    = firstTabFR,
            firstResultFR = firstResultFR,
            onSelect      = { onIntent(SearchIntent.SelectSource(it)) }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading                                                -> ShimmerGrid()
                state.source == SearchSource.FUZER && state.fuzerError != null -> FuzerError(state.fuzerError!!)
                state.activeResults.isEmpty()                                  -> EmptyState(state.query, state.source)
                else                                                           -> ResultsGrid(
                    results       = state.activeResults,
                    isFuzer       = state.source == SearchSource.FUZER,
                    firstResultFR = firstResultFR,
                    onResultClick = onResultClick
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  TOP BAR
// ══════════════════════════════════════════════════════════
@Composable
private fun SearchTopBar(
    state:      SearchState,
    backFR:     FocusRequester,
    inputFR:    FocusRequester,
    firstTabFR: FocusRequester,
    onBack:     () -> Unit,
    onIntent:   (SearchIntent) -> Unit
) {
    val context = LocalContext.current
    val view    = androidx.compose.ui.platform.LocalView.current
    val imm     = remember {
        context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
    }

    var showAutocomplete by remember { mutableStateOf(false) }
    var inputFocused     by remember { mutableStateOf(false) }
    var hintIdx          by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { while (true) { delay(3000); hintIdx = (hintIdx + 1) % SEARCH_HINTS.size } }

    Column(Modifier.fillMaxWidth().background(PANEL_BG)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Back button
            Surface(
                onClick  = onBack,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor = DIM3, focusedContainerColor = WHITE,
                    contentColor   = WHITE, focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                modifier = Modifier.size(36.dp).focusRequester(backFR)
                    .focusProperties { down = inputFR }
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                }
            }

            // Logo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(RED), Alignment.Center) {
                    Text("L", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("LUMINA",  color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 11.sp)
                    Text("STREAMS", color = RED,   fontSize = 6.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp, lineHeight = 8.sp)
                }
            }

            Box(Modifier.width(1.dp).height(20.dp).background(DIM2))

            // Search field
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (inputFocused) Color(0xFF181818) else Color(0xFF0F0F0F))
                    .border(
                        if (inputFocused) 1.5.dp else 1.dp,
                        if (inputFocused) RED.copy(0.65f) else DIM2,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(14.dp),
                        tint = if (inputFocused) RED else DIM)

                    BasicTextField(
                        value           = state.query,
                        onValueChange   = {
                            onIntent(SearchIntent.UpdateQuery(it))
                            showAutocomplete = it.isNotBlank()
                        },
                        singleLine      = true,
                        textStyle       = TextStyle(color = WHITE, fontSize = 13.sp),
                        cursorBrush     = SolidColor(RED),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                            showAutocomplete = false
                        }),
                        decorationBox   = { inner ->
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    AnimatedContent(
                                        targetState   = hintIdx,
                                        transitionSpec = {
                                            fadeIn(tween(300)) + slideInVertically { 6 } togetherWith
                                            fadeOut(tween(200)) + slideOutVertically { -6 }
                                        },
                                        label = "hint"
                                    ) { i -> Text(SEARCH_HINTS[i], color = DIM.copy(0.35f), fontSize = 13.sp) }
                                }
                                inner()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFR)
                            .onFocusChanged {
                                inputFocused = it.isFocused
                                if (!it.isFocused) showAutocomplete = false
                            }
                            .onPreviewKeyEvent { ev ->
                                when {
                                    ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionCenter -> {
                                        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                        true
                                    }
                                    ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                                        showAutocomplete = false
                                        runCatching { firstTabFR.requestFocus() }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )

                    AnimatedVisibility(state.query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery("")); showAutocomplete = false },
                            shape    = ClickableSurfaceDefaults.shape(CircleShape),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor = DIM2, focusedContainerColor = RED,
                                contentColor   = WHITE, focusedContentColor   = WHITE),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Default.Close, null, Modifier.size(9.dp))
                            }
                        }
                    }
                }
            }

            // Count badge
            AnimatedContent(
                targetState = when {
                    state.isLoading               -> "..."
                    state.activeResults.isEmpty() -> "–"
                    else                          -> "${state.activeResults.size}"
                },
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "cnt"
            ) { t ->
                Text(t, color = DIM, fontSize = 12.sp,
                    modifier = Modifier.widthIn(min = 28.dp),
                    textAlign = TextAlign.Center)
            }
        }

        // Autocomplete dropdown
        AnimatedVisibility(
            visible = showAutocomplete && state.autocompleteSuggestions.isNotEmpty(),
            enter   = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit    = shrinkVertically(tween(130)) + fadeOut(tween(100))
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF0A0A0C))
                    .padding(horizontal = 72.dp, vertical = 6.dp)
            ) {
                state.autocompleteSuggestions.forEach { s ->
                    Surface(
                        onClick  = { onIntent(SearchIntent.UpdateQuery(s)); showAutocomplete = false },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent, focusedContainerColor = DIM3,
                            contentColor   = DIM,               focusedContentColor   = WHITE),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.History, null, Modifier.size(11.dp), tint = DIM)
                            Text(s, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // History chips (when query is empty)
        AnimatedVisibility(
            visible = state.query.isBlank() && state.searchHistory.isNotEmpty(),
            enter   = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit    = shrinkVertically(tween(130)) + fadeOut(tween(100))
        ) {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 72.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                items(state.searchHistory) { h ->
                    Surface(
                        onClick  = { onIntent(SearchIntent.UpdateQuery(h)) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor = DIM3, focusedContainerColor = Color(0xFF1E1E28),
                            contentColor   = DIM,  focusedContentColor   = WHITE),
                        border   = ClickableSurfaceDefaults.border(
                            border        = Border(
                                border = androidx.compose.foundation.BorderStroke(1.dp, DIM2),
                                shape  = RoundedCornerShape(50)
                            ),
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(1.dp, RED.copy(0.6f)),
                                shape  = RoundedCornerShape(50)
                            )
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, null, Modifier.size(11.dp))
                            Text(h, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Surface(
                        onClick  = { onIntent(SearchIntent.ClearHistory) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent, focusedContainerColor = SEL_RED,
                            contentColor   = RED.copy(0.7f),    focusedContentColor   = RED),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp).fillMaxHeight(), Alignment.Center) {
                            Text("Clear history", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Bottom accent line
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(RED.copy(0.7f), RED.copy(0.12f), Color.Transparent)))
        )
    }
}

// ══════════════════════════════════════════════════════════
//  SOURCE TAB ROW
// ══════════════════════════════════════════════════════════
private data class TabDef(
    val src:    SearchSource,
    val label:  String,
    val icon:   androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color
)

@Composable
private fun SourceTabRow(
    selected:      SearchSource,
    firstTabFR:    FocusRequester,
    firstResultFR: FocusRequester,
    onSelect:      (SearchSource) -> Unit
) {
    val tabs = remember {
        listOf(
            TabDef(SearchSource.ALL,    "All",      Icons.Default.GridView,   Color(0xFFB0BEC5)),
            TabDef(SearchSource.MOVIES, "Movies",   Icons.Default.Movie,      Color(0xFFFFD700)),
            TabDef(SearchSource.SERIES, "Series",   Icons.Default.Tv,         Color(0xFF80DEEA)),
            TabDef(SearchSource.FUZER,  "💎 Fuzer", Icons.Default.CloudQueue, Color(0xFF00B0FF))
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(PANEL_BG)
            .padding(horizontal = 20.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                    runCatching { firstResultFR.requestFocus() }; true
                } else false
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { idx, tab ->
            val isSel  = selected == tab.src
            val accent = if (tab.src == SearchSource.FUZER) FUZER_BLUE else tab.accent

            Surface(
                onClick  = { onSelect(tab.src) },
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = if (isSel) accent.copy(0.15f) else Color.Transparent,
                    focusedContainerColor = if (isSel) accent.copy(0.25f) else Color(0x14FFFFFF),
                    contentColor          = if (isSel) WHITE else DIM,
                    focusedContentColor   = WHITE
                ),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border(
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) accent.copy(0.6f) else Color.Transparent),
                        shape  = RoundedCornerShape(8.dp)
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(0.8f)),
                        shape  = RoundedCornerShape(8.dp)
                    )
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier
                    .height(36.dp)
                    .let { if (idx == 0) it.focusRequester(firstTabFR) else it }
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(tab.icon, null, Modifier.size(14.dp), tint = if (isSel) accent else DIM)
                    Text(
                        tab.label,
                        fontSize   = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        softWrap   = false
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (selected == SearchSource.FUZER) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(FUZER_BLUE))
                Text("fuzer.xyz", color = FUZER_BLUE.copy(0.6f), fontSize = 10.sp)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DIM3))
}

// ══════════════════════════════════════════════════════════
//  RESULTS GRID
// ══════════════════════════════════════════════════════════
@Composable
private fun ResultsGrid(
    results:       List<SearchResult>,
    isFuzer:       Boolean,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(if (isFuzer) 160.dp else 138.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(14.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            MediaCard(
                result   = result,
                isFuzer  = isFuzer,
                modifier = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onClick  = { onResultClick(result) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  MEDIA CARD
// ══════════════════════════════════════════════════════════
@Composable
private fun MediaCard(
    result:   SearchResult,
    isFuzer:  Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val zoom    by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "zoom"
    )

    val qualityBadge: String? = if (isFuzer) when {
        result.title.contains("4K",    ignoreCase = true) ||
        result.title.contains("2160p", ignoreCase = true) -> "4K"
        result.title.contains("1080p", ignoreCase = true) -> "FHD"
        result.title.contains("720p",  ignoreCase = true) -> "HD"
        else -> null
    } else null

    val isDubbed = isFuzer && result.title.contains("מדובב", ignoreCase = true)

    Column(modifier, horizontalAlignment = Alignment.Start) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .zIndex(if (focused) 8f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor = CARD_BG, focusedContainerColor = CARD_BG),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = if (isFuzer)
                        Border(
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, FUZER_BLUE.copy(0.5f)),
                            shape  = RoundedCornerShape(9.dp)
                        )
                    else Border.None
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = if (isFuzer) Glow(FUZER_BLUE.copy(0.3f), 16.dp)
                                  else         Glow(Color.Black.copy(0.8f), 20.dp)
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = zoom, scaleY = zoom)
                    .onFocusChanged { fs -> focused = fs.isFocused }
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
                            .background(Brush.verticalGradient(
                                if (isFuzer) listOf(Color(0xFF0A1A2A), Color(0xFF060E14))
                                else         listOf(Color(0xFF1E1E1E), CARD_BG)
                            )),
                        Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isFuzer) Text("💎", fontSize = 28.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                result.title,
                                color     = if (isFuzer) FUZER_BLUE.copy(0.7f) else WHITE.copy(0.3f),
                                fontSize  = 9.sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                // Year badge (top-start)
                if (result.releaseYear.isNotBlank()) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xBB000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) { Text(result.releaseYear, color = DIM, fontSize = 8.sp) }
                }

                // Quality / Rating badge (top-end)
                if (qualityBadge != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(when (qualityBadge) {
                                "4K"  -> Color(0xFFCC2200)
                                "FHD" -> FUZER_BLUE.copy(0.85f)
                                else  -> Color(0xFF388E3C)
                            })
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) { Text(qualityBadge, color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Black) }
                } else if (!isFuzer && result.rating > 0f) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xBB000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) { Text("★ %.1f".format(result.rating), color = GOLD, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                }

                // Dubbed badge (bottom-start)
                if (isDubbed) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(FUZER_PINK)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) { Text("🎤 מדובב", color = WHITE, fontSize = 7.5.sp, fontWeight = FontWeight.Bold) }
                }

                // Focused tint overlay (Fuzer only)
                if (isFuzer && focused) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(
                                listOf(Color.Transparent, FUZER_BLUE.copy(0.12f))
                            ))
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            result.title,
            color      = if (focused) (if (isFuzer) FUZER_BLUE else WHITE) else DIM,
            fontSize   = 10.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        Text(
            if (isFuzer) "Fuzer" else if (result.type == MediaType.TV_SHOW) "TV Show" else "Movie",
            color    = if (isFuzer) FUZER_BLUE.copy(0.4f) else WHITE.copy(0.22f),
            fontSize = 9.sp
        )
    }
}

// ══════════════════════════════════════════════════════════
//  SHIMMER
// ══════════════════════════════════════════════════════════
@Composable
private fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p   by inf.animateFloat(
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
                Box(Modifier.fillMaxWidth(0.7f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(shimmer))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════
@Composable
private fun EmptyState(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (source == SearchSource.FUZER) "💎" else "🎬", fontSize = 52.sp)
            Text(
                if (query.isNotBlank()) "No results for \"$query\""
                else if (source == SearchSource.FUZER) "Browse Fuzer library"
                else "Start searching",
                color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(
                if (source == SearchSource.FUZER) "Type to search Israeli content on Fuzer"
                else "Use the search bar above ↑",
                color    = if (source == SearchSource.FUZER) FUZER_BLUE.copy(0.6f) else DIM,
                fontSize = 12.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  FUZER ERROR
// ══════════════════════════════════════════════════════════
@Composable
private fun FuzerError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(FUZER_BLUE.copy(0.1f)),
                Alignment.Center
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(30.dp), tint = FUZER_BLUE.copy(0.6f))
            }
            Text("Fuzer Unavailable", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                message,
                color    = DIM,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp)
            )
        }
    }
}
