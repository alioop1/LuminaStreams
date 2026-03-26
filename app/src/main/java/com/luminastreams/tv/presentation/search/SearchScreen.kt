@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.delay

// ── Palette ───────────────────────────────────────────────────────────────────
private val BG           = Color(0xFF0F0F13)
private val CARD         = Color(0xFF1A1A24)
private val CARD2        = Color(0xFF22222E)
private val SURFACE      = Color(0xFF16161F)
private val WHITE        = Color(0xFFFFFFFF)
private val LABEL        = Color(0xFFE8E8F0)
private val LABEL2       = Color(0xAAB0B0C8)
private val LABEL3       = Color(0x55B0B0C8)
private val SEP          = Color(0x20FFFFFF)
private val TINT         = Color(0xFF4F8EF7)
private val RED          = Color(0xFFE03E3E)
private val GOLD         = Color(0xFFFFB830)
private val FUZER        = Color(0xFF34C97A)
private val FOCUS_BORDER = Color(0xFF6AA3FF)

private val HINTS = listOf(
    "Movies, series, actors...",
    "Try \"Inception\" or \"The Wire\"...",
    "Search by genre or actor...",
    "What are you in the mood for?"
)

// ── Root ──────────────────────────────────────────────────────────────────────
@Composable
fun SearchScreen(
    state:          SearchState,
    onIntent:       (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick:  (SearchResult) -> Unit
) {
    val backFR        = remember { FocusRequester() }
    val inputFR       = remember { FocusRequester() }
    val firstSugFR    = remember { FocusRequester() }
    val firstScopeFR  = remember { FocusRequester() }
    val firstChipFR   = remember { FocusRequester() }
    val firstResultFR = remember { FocusRequester() }
    val gridState     = rememberLazyGridState()

    BackHandler {
        when {
            state.showFilters        -> onIntent(SearchIntent.ToggleFilters)
            state.query.isNotBlank() -> onIntent(SearchIntent.UpdateQuery(""))
            else                     -> onNavigateBack()
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { backFR.requestFocus() } }
    LaunchedEffect(state.lastFocusedIndex) {
        if (state.lastFocusedIndex > 0) gridState.animateScrollToItem(state.lastFocusedIndex)
    }

    Box(Modifier.fillMaxSize().background(BG)
        .onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                when {
                    state.showFilters        -> { onIntent(SearchIntent.ToggleFilters); true }
                    state.query.isNotBlank() -> { onIntent(SearchIntent.UpdateQuery(""));  true }
                    else -> false
                }
            } else false
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            SearchHeader(
                state        = state,
                backFR       = backFR,
                inputFR      = inputFR,
                firstSugFR   = firstSugFR,
                firstScopeFR = firstScopeFR,
                onBack       = onNavigateBack,
                onIntent     = onIntent
            )
            AnimatedVisibility(
                visible = state.query.isNotBlank() && state.suggestions.isNotEmpty(),
                enter   = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit    = shrinkVertically(tween(150)) + fadeOut(tween(100))
            ) {
                SuggestionRail(
                    suggestions  = state.suggestions,
                    history      = state.searchHistory,
                    firstSugFR   = firstSugFR,
                    firstScopeFR = firstScopeFR,
                    onPick = {
                        onIntent(SearchIntent.UpdateQuery(it))
                        runCatching { firstResultFR.requestFocus() }
                    }
                )
            }
            ScopeTabRow(
                source        = state.source,
                firstScopeFR  = firstScopeFR,
                firstChipFR   = firstChipFR,
                firstResultFR = firstResultFR,
                isFuzerActive = state.isFuzerLoading || state.fuzerResults.isNotEmpty(),
                onSelect      = { onIntent(SearchIntent.SelectSource(it)) }
            )
            AnimatedVisibility(
                visible = state.visibleFilterChips.isNotEmpty(),
                enter   = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit    = shrinkVertically(tween(150)) + fadeOut(tween(100))
            ) {
                FilterChipRow(
                    chips         = state.visibleFilterChips,
                    filtersActive = state.filters.isActive,
                    firstChipFR   = firstChipFR,
                    firstResultFR = firstResultFR,
                    onChipTap     = { onIntent(SearchIntent.ApplyChip(it)) },
                    onClearAll    = { onIntent(SearchIntent.ClearFilters) }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> ShimmerGrid()
                    state.source == SearchSource.FUZER && state.fuzerError != null ->
                        FuzerErrorView(state.fuzerError!!)
                    state.activeResults.isEmpty() ->
                        EmptyStateView(state.query, state.source)
                    else -> ResultGrid(
                        results       = state.activeResults,
                        isFuzer       = state.source == SearchSource.FUZER,
                        gridState     = gridState,
                        firstResultFR = firstResultFR,
                        onResultClick = { result, idx ->
                            onIntent(SearchIntent.SetLastFocused(idx))
                            onResultClick(result)
                        }
                    )
                }
            }
        }
    }
}

// ── HEADER ────────────────────────────────────────────────────────────────────
@Composable
private fun SearchHeader(
    state:        SearchState,
    backFR:       FocusRequester,
    inputFR:      FocusRequester,
    firstSugFR:   FocusRequester,
    firstScopeFR: FocusRequester,
    onBack:       () -> Unit,
    onIntent:     (SearchIntent) -> Unit
) {
    val ctx  = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val imm  = remember {
        ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
    }
    var inputFocused by remember { mutableStateOf(false) }
    var hintIdx      by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3600); hintIdx = (hintIdx + 1) % HINTS.size } }

    Box(Modifier.fillMaxWidth().background(SURFACE)) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.horizontalGradient(
                    listOf(Color.Transparent, SEP, SEP, Color.Transparent)
                ))
        )
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    onClick  = onBack,
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = CARD,
                        focusedContainerColor = FOCUS_BORDER,
                        contentColor          = LABEL2,
                        focusedContentColor   = WHITE
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border        = Border(border = androidx.compose.foundation.BorderStroke(1.dp, SEP), shape = RoundedCornerShape(50)),
                        focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, FOCUS_BORDER), shape = RoundedCornerShape(50))
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.size(44.dp).focusRequester(backFR).focusProperties { down = inputFR }
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(4.dp, 24.dp).clip(RoundedCornerShape(2.dp)).background(RED))
                    Text("LUMINA", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
                }

                val fieldRadius = RoundedCornerShape(28.dp)
                val fieldBorder by animateColorAsState(
                    if (inputFocused) FOCUS_BORDER.copy(0.8f) else SEP, tween(180), label = "fb"
                )
                Box(
                    Modifier.weight(1f).height(46.dp)
                        .clip(fieldRadius).background(CARD)
                        .border(1.5.dp, fieldBorder, fieldRadius)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val iconAlpha by animateFloatAsState(
                            if (inputFocused) 1f else 0.4f, tween(180), label = "ia"
                        )
                        Icon(Icons.Default.Search, null, Modifier.size(17.dp), tint = TINT.copy(iconAlpha))
                        BasicTextField(
                            value           = state.query,
                            onValueChange   = { onIntent(SearchIntent.UpdateQuery(it)) },
                            singleLine      = true,
                            textStyle       = TextStyle(color = LABEL, fontSize = 15.sp, fontWeight = FontWeight.Normal),
                            cursorBrush     = SolidColor(TINT),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                            }),
                            decorationBox = { inner ->
                                Box(Modifier.weight(1f)) {
                                    if (state.query.isEmpty()) {
                                        AnimatedContent(
                                            targetState = hintIdx,
                                            transitionSpec = {
                                                (fadeIn(tween(280)) + slideInVertically(tween(280)) { 8 })
                                                    .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -8 })
                                            },
                                            label = "hint"
                                        ) { i -> Text(HINTS[i], color = LABEL3, fontSize = 15.sp) }
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.weight(1f)
                                .focusRequester(inputFR)
                                .onFocusChanged { inputFocused = it.isFocused }
                                .onPreviewKeyEvent { ev ->
                                    when {
                                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionCenter -> {
                                            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); true
                                        }
                                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                                            if (state.suggestions.isNotEmpty() && state.query.isNotBlank())
                                                runCatching { firstSugFR.requestFocus() }
                                            else
                                                runCatching { firstScopeFR.requestFocus() }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        )
                        AnimatedVisibility(state.query.isNotEmpty(),
                            enter = fadeIn(tween(120)) + scaleIn(tween(120)),
                            exit  = fadeOut(tween(100)) + scaleOut(tween(100))
                        ) {
                            Surface(
                                onClick  = { onIntent(SearchIntent.UpdateQuery("")) },
                                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                                colors   = ClickableSurfaceDefaults.colors(
                                    containerColor        = CARD2,
                                    focusedContainerColor = RED,
                                    contentColor          = LABEL2,
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

                AnimatedContent(
                    targetState = when {
                        state.isLoading -> "..."
                        state.activeResults.isEmpty() -> ""
                        else -> "${state.activeResults.size}"
                    },
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "cnt"
                ) { t ->
                    if (t.isNotEmpty()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(CARD).border(1.dp, SEP, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(t, color = LABEL2, fontSize = 12.sp) }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.query.isBlank() && state.searchHistory.isNotEmpty(),
                enter   = expandVertically(tween(180)) + fadeIn(tween(160)),
                exit    = shrinkVertically(tween(140)) + fadeOut(tween(100))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = LABEL3)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(state.searchHistory) { _, h ->
                            Chip(label = h, isActive = false, accent = TINT,
                                onClick = { onIntent(SearchIntent.UpdateQuery(h)) })
                        }
                        item {
                            Chip(label = "Clear", isActive = false, accent = RED,
                                onClick = { onIntent(SearchIntent.ClearHistory) })
                        }
                    }
                }
            }
        }
    }
}

// ── SUGGESTION RAIL ───────────────────────────────────────────────────────────
@Composable
private fun SuggestionRail(
    suggestions:  List<String>,
    history:      List<String>,
    firstSugFR:   FocusRequester,
    firstScopeFR: FocusRequester,
    onPick:       (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(SURFACE)
            .border(0.5.dp, SEP, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstScopeFR.requestFocus() }; true }
                else false
            }
    ) {
        suggestions.forEachIndexed { idx, s ->
            val isHist = history.contains(s)
            Surface(
                onClick  = { onPick(s) },
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color.Transparent,
                    focusedContainerColor = CARD,
                    contentColor          = LABEL2,
                    focusedContentColor   = WHITE
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth().height(44.dp)
                    .let { if (idx == 0) it.focusRequester(firstSugFR) else it }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        if (isHist) Icons.Default.History else Icons.Default.Search,
                        null, Modifier.size(14.dp),
                        tint = if (isHist) TINT.copy(0.6f) else LABEL3
                    )
                    Text(s, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (idx < suggestions.lastIndex) Divider(Modifier.padding(start = 80.dp))
        }
    }
}

// ── SCOPE TABS ───────────────────────────────────────────────────────────────
private data class ScopeDef(
    val src:   SearchSource,
    val label: String,
    val icon:  androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun ScopeTabRow(
    source:        SearchSource,
    firstScopeFR:  FocusRequester,
    firstChipFR:   FocusRequester,
    firstResultFR: FocusRequester,
    isFuzerActive: Boolean,
    onSelect:      (SearchSource) -> Unit
) {
    val scopes = remember {
        listOf(
            ScopeDef(SearchSource.ALL,    "All",    Icons.Default.GridView),
            ScopeDef(SearchSource.MOVIES, "Movies", Icons.Default.Movie),
            ScopeDef(SearchSource.SERIES, "Series", Icons.Default.LiveTv),
            ScopeDef(SearchSource.FUZER,  "Fuzer",  Icons.Default.CloudQueue)
        )
    }
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .background(SURFACE)
            .padding(horizontal = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstChipFR.requestFocus() }; true }
                else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        scopes.forEachIndexed { idx, scope ->
            val isSel  = source == scope.src
            val accent = when (scope.src) {
                SearchSource.FUZER  -> FUZER
                SearchSource.MOVIES -> GOLD
                SearchSource.SERIES -> TINT
                else                -> LABEL
            }
            val tabAlpha by animateFloatAsState(if (isSel) 1f else 0f, tween(200), label = "ta")
            Column(
                Modifier.let { if (idx == 0) it.focusRequester(firstScopeFR) else it },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick  = { onSelect(scope.src) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (isSel) accent.copy(0.15f) else Color.Transparent,
                        focusedContainerColor = accent.copy(0.22f),
                        contentColor          = if (isSel) accent else LABEL3,
                        focusedContentColor   = accent
                    ),
                    border   = ClickableSurfaceDefaults.border(
                        border        = if (isSel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.4f)), shape = RoundedCornerShape(10.dp))
                                        else Border.None,
                        focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(0.7f)), shape = RoundedCornerShape(10.dp))
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(scope.icon, null, Modifier.size(14.dp))
                        Text(
                            scope.label, fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            softWrap = false
                        )
                        if (scope.src == SearchSource.FUZER && isFuzerActive) {
                            val inf = rememberInfiniteTransition(label = "fz")
                            val a by inf.animateFloat(
                                0.3f, 1f,
                                infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                                label = "fa"
                            )
                            Box(Modifier.size(5.dp).clip(CircleShape).background(FUZER.copy(a)))
                        }
                    }
                }
                Box(Modifier.width(28.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(accent.copy(tabAlpha)))
            }
        }
    }
    Divider()
}

// ── FILTER CHIP ROW ───────────────────────────────────────────────────────────
@Composable
private fun FilterChipRow(
    chips:         List<FilterChip>,
    filtersActive: Boolean,
    firstChipFR:   FocusRequester,
    firstResultFR: FocusRequester,
    onChipTap:     (String) -> Unit,
    onClearAll:    () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .background(BG)
            .padding(horizontal = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstResultFR.requestFocus() }; true }
                else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(chips) { idx, chip ->
                val accent = chipAccent(chip.id)
                Chip(
                    label     = if (chip.emoji.isNotEmpty()) "${chip.emoji} ${chip.label}" else chip.label,
                    isActive  = chip.isActive,
                    accent    = accent,
                    modifier  = if (idx == 0) Modifier.focusRequester(firstChipFR) else Modifier,
                    onClick   = { onChipTap(chip.id) },
                    showCheck = chip.isActive
                )
            }
        }
        AnimatedVisibility(filtersActive,
            enter = fadeIn(tween(140)) + scaleIn(tween(140)),
            exit  = fadeOut(tween(100)) + scaleOut(tween(100))
        ) {
            Chip(label = "Clear all", isActive = false, accent = RED, onClick = onClearAll)
        }
    }
    Divider()
}

// ── CHIP ──────────────────────────────────────────────────────────────────────
@Composable
private fun Chip(
    label:     String,
    isActive:  Boolean,
    accent:    Color,
    modifier:  Modifier = Modifier,
    onClick:   () -> Unit,
    showCheck: Boolean = false
) {
    val radius = RoundedCornerShape(50)
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(radius),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = if (isActive) accent.copy(0.18f) else CARD,
            focusedContainerColor = if (isActive) accent.copy(0.28f) else CARD2,
            contentColor          = if (isActive) accent else LABEL2,
            focusedContentColor   = accent
        ),
        border   = ClickableSurfaceDefaults.border(
            border        = Border(border = androidx.compose.foundation.BorderStroke(
                if (isActive) 1.5.dp else 1.dp,
                if (isActive) accent.copy(0.6f) else SEP
            ), shape = radius),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(0.8f)), shape = radius)
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier.height(30.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, softWrap = false)
            if (showCheck) Icon(Icons.Default.Check, null, Modifier.size(10.dp))
        }
    }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SEP))
}

private fun chipAccent(id: String): Color = when {
    id.startsWith("sort") -> TINT
    id.startsWith("g_")   -> LABEL
    id.startsWith("q_")   -> GOLD
    id == "dubbed"         -> FUZER
    id.startsWith("r")     -> GOLD
    else                   -> LABEL
}

// ── RESULT GRID ───────────────────────────────────────────────────────────────
@Composable
private fun ResultGrid(
    results:       List<SearchResult>,
    isFuzer:       Boolean,
    gridState:     LazyGridState,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult, Int) -> Unit
) {
    LazyVerticalGrid(
        state                 = gridState,
        columns               = GridCells.Adaptive(minSize = 155.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement   = Arrangement.spacedBy(26.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            MediaCard(
                result   = result,
                isFuzer  = isFuzer,
                modifier = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onClick  = { onResultClick(result, idx) }
            )
        }
    }
}

// ── MEDIA CARD ────────────────────────────────────────────────────────────────
@Composable
private fun MediaCard(
    result:   SearchResult,
    isFuzer:  Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(
        if (focused) 1.04f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "sc"
    )
    val accent = if (isFuzer) FUZER else TINT

    val qBadge: String? = when {
        result.qualityTag.isNotBlank()                    -> result.qualityTag
        result.title.contains("4K",    ignoreCase = true) ||
        result.title.contains("2160p", ignoreCase = true) -> "4K"
        result.title.contains("1080p", ignoreCase = true) -> "FHD"
        result.title.contains("720p",  ignoreCase = true) -> "HD"
        else -> null
    }

    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .zIndex(if (focused) 10f else 0f)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        ) {
            if (focused) {
                Box(
                    Modifier.fillMaxSize()
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp),
                            ambientColor = accent.copy(0.5f), spotColor = accent.copy(0.5f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                )
            }
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = CARD,
                    focusedContainerColor = CARD
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = Glow(accent.copy(0.3f), 18.dp)
                ),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border(border = androidx.compose.foundation.BorderStroke(1.dp, CARD2), shape = RoundedCornerShape(16.dp)),
                    focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.5.dp, accent), shape = RoundedCornerShape(16.dp))
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxSize().onFocusChanged { focused = it.isFocused }
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (result.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(result.posterUrl).crossfade(true).build(),
                            contentDescription = result.title,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Brush.verticalGradient(listOf(CARD2, CARD))),
                            Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("\uD83C\uDFAC", fontSize = 32.sp)
                                Text(result.title, color = LABEL3, fontSize = 10.sp,
                                    textAlign = TextAlign.Center, maxLines = 3,
                                    modifier = Modifier.padding(horizontal = 10.dp))
                            }
                        }
                    }

                    // FIX: colorStops requires vararg Pair — use spread operator *arrayOf(...)
                    Box(
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f   to Color.Transparent,
                                        0.6f to Color.Transparent,
                                        1f   to Color.Black.copy(if (focused) 0.7f else 0.4f)
                                    )
                                )
                            )
                    )

                    if (result.releaseYear.isNotBlank()) {
                        Text(
                            result.releaseYear, color = WHITE.copy(0.85f), fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (qBadge != null) {
                        val badgeColor = when (qBadge) { "4K" -> Color(0xFFFF453A); "FHD" -> TINT; else -> FUZER }
                        Text(
                            qBadge, color = WHITE, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(badgeColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else if (result.rating >= 7f) {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Default.Star, null, Modifier.size(9.dp), tint = GOLD)
                            Text("%.1f".format(result.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isFuzer) {
                        Text(
                            "FZ", color = FUZER, fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(FUZER.copy(0.15f))
                                .border(0.5.dp, FUZER.copy(0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Text(
            result.title,
            color      = if (focused) WHITE else LABEL,
            fontSize   = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        val sub = buildString {
            append(if (isFuzer) "Fuzer" else if (result.type == MediaType.TV_SHOW) "Series" else "Movie")
            if (result.genre.isNotBlank()) append(" \u00b7 ${result.genre}")
        }
        Text(sub, color = LABEL3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── SHIMMER ───────────────────────────────────────────────────────────────────
@Composable
private fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sp")
    val shimmer = Brush.linearGradient(
        colorStops = arrayOf(
            0f    to CARD,
            0.45f to CARD2,
            1f    to CARD
        ),
        start = androidx.compose.ui.geometry.Offset(p * 2000f - 1000f, 0f),
        end   = androidx.compose.ui.geometry.Offset(p * 2000f, 600f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(155.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement   = Arrangement.spacedBy(26.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(18) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.72f).height(12.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
            }
        }
    }
}

// ── EMPTY STATE ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyStateView(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
                    .background(CARD).border(1.dp, SEP, RoundedCornerShape(24.dp)),
                Alignment.Center
            ) {
                Text(when {
                    query.isNotBlank()           -> "\uD83D\uDD0D"
                    source == SearchSource.FUZER -> "\uD83C\uDFAC"
                    else                         -> "\uD83C\uDF1F"
                }, fontSize = 40.sp)
            }
            Text(
                when {
                    query.isNotBlank()           -> "No results for \u201c$query\u201d"
                    source == SearchSource.FUZER -> "Search Fuzer"
                    else                         -> "What would you like to watch?"
                },
                color = LABEL, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Text(
                when {
                    query.isNotBlank()           -> "Try a different keyword or clear filters"
                    source == SearchSource.FUZER -> "Type to search Israeli content"
                    else                         -> "Start typing to discover movies and series"
                },
                color = LABEL3, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp)
            )
        }
    }
}

// ── FUZER ERROR ───────────────────────────────────────────────────────────────
@Composable
private fun FuzerErrorView(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(48.dp)
        ) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(22.dp))
                    .background(FUZER.copy(0.08f))
                    .border(1.dp, FUZER.copy(0.3f), RoundedCornerShape(22.dp)),
                Alignment.Center
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(36.dp), tint = FUZER.copy(0.7f))
            }
            Text("Fuzer Unavailable", color = LABEL, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = LABEL3, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp))
        }
    }
}
