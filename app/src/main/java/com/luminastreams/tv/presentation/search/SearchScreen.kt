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

// ── Palette — Apple TV inspired ──────────────────────────────────────────────
private val BG       = Color(0xFF000000)
private val GLASS    = Color(0xFF1C1C1E)   // iOS/tvOS system grouped bg
private val GLASS2   = Color(0xFF2C2C2E)   // elevated surface
private val WHITE    = Color(0xFFFFFFFF)
private val LABEL    = Color(0xFFEBEBF5)   // primary label
private val LABEL2   = Color(0x99EBEBF5)   // secondary label
private val LABEL3   = Color(0x4DEBEBF5)   // tertiary label
private val SEP      = Color(0x40787880)   // separator
private val TINT     = Color(0xFF0A84FF)   // blue tint (tvOS system blue)
private val RED      = Color(0xFFE50914)   // Lumina brand
private val GOLD     = Color(0xFFFF9F0A)   // tvOS yellow
private val FUZER    = Color(0xFF30D158)   // tvOS green — Fuzer accent

private val HINTS = listOf(
    "Movies, series, actors...",
    "Try \"Inception\" or \"The Wire\"...",
    "Search by actor or genre...",
    "What are you in the mood for?"
)

// ── Root ─────────────────────────────────────────────────────────────────────
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

    // Restore focus to the previously focused card
    val gridState = rememberLazyGridState()

    BackHandler {
        when {
            state.showFilters        -> onIntent(SearchIntent.ToggleFilters)
            state.query.isNotBlank() -> onIntent(SearchIntent.UpdateQuery(""))
            else                     -> onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { backFR.requestFocus() }
    }

    LaunchedEffect(state.lastFocusedIndex) {
        if (state.lastFocusedIndex > 0)
            gridState.animateScrollToItem(state.lastFocusedIndex)
    }

    Box(
        Modifier.fillMaxSize().background(BG)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                    when {
                        state.showFilters        -> { onIntent(SearchIntent.ToggleFilters); true }
                        state.query.isNotBlank() -> { onIntent(SearchIntent.UpdateQuery(""));  true }
                        else                     -> false
                    }
                } else false
            }
    ) {
        Column(Modifier.fillMaxSize()) {

            // 1 — Header (back + logo + search field)
            AppleTvSearchHeader(
                state       = state,
                backFR      = backFR,
                inputFR     = inputFR,
                firstSugFR  = firstSugFR,
                firstScopeFR= firstScopeFR,
                onBack      = onNavigateBack,
                onIntent    = onIntent
            )

            // 2 — Suggestion rail (only when typing)
            AnimatedVisibility(
                visible     = state.query.isNotBlank() && state.suggestions.isNotEmpty(),
                enter       = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit        = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                SuggestionRail(
                    suggestions  = state.suggestions,
                    history      = state.searchHistory,
                    firstSugFR   = firstSugFR,
                    firstScopeFR = firstScopeFR,
                    onPick       = {
                        onIntent(SearchIntent.UpdateQuery(it))
                        runCatching { firstResultFR.requestFocus() }
                    }
                )
            }

            // 3 — Scope segmented control
            ScopeSegmentedControl(
                source        = state.source,
                firstScopeFR  = firstScopeFR,
                firstChipFR   = firstChipFR,
                firstResultFR = firstResultFR,
                isFuzerActive = state.isFuzerLoading || state.fuzerResults.isNotEmpty(),
                onSelect      = { onIntent(SearchIntent.SelectSource(it)) }
            )

            // 4 — Smart filter tray
            AnimatedVisibility(
                visible = state.visibleFilterChips.isNotEmpty(),
                enter   = expandVertically(tween(200)) + fadeIn(tween(180)),
                exit    = shrinkVertically(tween(150)) + fadeOut(tween(120))
            ) {
                SmartFilterTray(
                    chips         = state.visibleFilterChips,
                    filtersActive = state.filters.isActive,
                    firstChipFR   = firstChipFR,
                    firstResultFR = firstResultFR,
                    onChipTap     = { onIntent(SearchIntent.ApplyChip(it)) },
                    onClearAll    = { onIntent(SearchIntent.ClearFilters) }
                )
            }

            // 5 — Content area
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> ShimmerGrid()
                    state.source == SearchSource.FUZER && state.fuzerError != null ->
                        FuzerError(state.fuzerError!!)
                    state.activeResults.isEmpty() ->
                        EmptyState(state.query, state.source)
                    else -> PosterGrid(
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

// ── 1  HEADER ─────────────────────────────────────────────────────────────────
@Composable
private fun AppleTvSearchHeader(
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
    var focused  by remember { mutableStateOf(false) }
    var hintIdx  by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3800); hintIdx = (hintIdx + 1) % HINTS.size } }

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0A), BG)))
    ) {
        Row(
            Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Back button — round, glass
            Surface(
                onClick  = onBack,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = GLASS,
                    focusedContainerColor = WHITE,
                    contentColor          = LABEL2,
                    focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                modifier = Modifier.size(42.dp)
                    .focusRequester(backFR)
                    .focusProperties { down = inputFR }
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            }}

            // Lumina logomark — minimal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(RED)
                )
                Text(
                    "LUMINA",
                    color      = WHITE,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Heavy,
                    letterSpacing = 3.sp
                )
            }

            // Search field — full-width, tvOS style
            val fieldShape = RoundedCornerShape(12.dp)
            Box(
                Modifier.weight(1f).height(46.dp)
                    .clip(fieldShape)
                    .background(if (focused) GLASS2 else GLASS)
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) TINT.copy(alpha = 0.8f) else SEP,
                        shape = fieldShape
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val iconTint by animateColorAsState(
                        if (focused) TINT else LABEL3, tween(200), label = "ic"
                    )
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = iconTint)

                    BasicTextField(
                        value           = state.query,
                        onValueChange   = { onIntent(SearchIntent.UpdateQuery(it)) },
                        singleLine      = true,
                        textStyle       = TextStyle(
                            color      = LABEL,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush     = SolidColor(TINT),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                        }),
                        decorationBox   = { inner ->
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    AnimatedContent(
                                        targetState  = hintIdx,
                                        transitionSpec = {
                                            (fadeIn(tween(300)) + slideInVertically(tween(300)) { 10 })
                                                .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -10 })
                                        },
                                        label = "hint"
                                    ) { i ->
                                        Text(HINTS[i], color = LABEL3, fontSize = 16.sp)
                                    }
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                            .focusRequester(inputFR)
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { ev ->
                                when {
                                    ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionCenter -> {
                                        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                        true
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

                    // Clear button
                    AnimatedVisibility(
                        state.query.isNotEmpty(),
                        enter = fadeIn(tween(120)) + scaleIn(tween(120)),
                        exit  = fadeOut(tween(100)) + scaleOut(tween(100))
                    ) {
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery("")) },
                            shape    = ClickableSurfaceDefaults.shape(CircleShape),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = GLASS2,
                                focusedContainerColor = TINT,
                                contentColor          = LABEL2,
                                focusedContentColor   = WHITE
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                            modifier = Modifier.size(24.dp)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(Icons.Default.Close, null, Modifier.size(11.dp))
                        }}
                    }
                }
            }

            // Result count — subdued badge
            AnimatedContent(
                targetState = when {
                    state.isLoading               -> "Loading..."
                    state.activeResults.isEmpty() -> ""
                    else -> "${state.activeResults.size} results"
                },
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "cnt"
            ) { t ->
                if (t.isNotEmpty())
                    Text(t, color = LABEL3, fontSize = 12.sp, maxLines = 1)
            }
        }

        // History chips — quiet horizontal rail, only when query is empty
        AnimatedVisibility(
            visible = state.query.isBlank() && state.searchHistory.isNotEmpty(),
            enter   = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit    = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, null, Modifier.size(12.dp), tint = LABEL3)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.searchHistory) { _, h ->
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery(h)) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = GLASS,
                                focusedContainerColor = GLASS2,
                                contentColor          = LABEL2,
                                focusedContentColor   = LABEL
                            ),
                            border   = ClickableSurfaceDefaults.border(
                                border        = Border(border = androidx.compose.foundation.BorderStroke(1.dp, SEP), shape = RoundedCornerShape(50)),
                                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, TINT.copy(0.5f)), shape = RoundedCornerShape(50))
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                                Alignment.Center
                            ) { Text(h, fontSize = 12.sp) }
                        }
                    }
                    item {
                        Surface(
                            onClick  = { onIntent(SearchIntent.ClearHistory) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = Color.Transparent,
                                focusedContainerColor = Color(0x22FF453A),
                                contentColor          = Color(0x88FF453A),
                                focusedContentColor   = Color(0xFFFF453A)
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                                Alignment.Center
                            ) { Text("Clear", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        // Thin separator
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SEP))
    }
}

// ── 2  SUGGESTION RAIL ────────────────────────────────────────────────────────
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
            .background(GLASS)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstScopeFR.requestFocus() }; true }
                else false
            }
    ) {
        suggestions.forEachIndexed { idx, s ->
            val isHistory = history.contains(s)
            Surface(
                onClick  = { onPick(s) },
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color.Transparent,
                    focusedContainerColor = GLASS2,
                    contentColor          = LABEL2,
                    focusedContentColor   = LABEL
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
                        if (isHistory) Icons.Default.History else Icons.Default.Search,
                        null,
                        Modifier.size(14.dp),
                        tint = if (isHistory) TINT.copy(0.6f) else LABEL3
                    )
                    Text(s, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (idx < suggestions.lastIndex)
                Box(Modifier.fillMaxWidth().height(0.5.dp).padding(start = 80.dp).background(SEP))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SEP))
    }
}

// ── 3  SCOPE SEGMENTED CONTROL ────────────────────────────────────────────────
private data class ScopeDef(val src: SearchSource, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun ScopeSegmentedControl(
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
        Modifier.fillMaxWidth().height(56.dp)
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstChipFR.requestFocus() }; true }
                else if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp)
                    { true }   // up is handled by focusProperties on items
                else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        scopes.forEachIndexed { idx, scope ->
            val isSel   = source == scope.src
            val accent  = when (scope.src) {
                SearchSource.FUZER  -> FUZER
                SearchSource.MOVIES -> GOLD
                SearchSource.SERIES -> TINT
                else                -> LABEL
            }

            // Animated underline indicator (Apple style)
            val indicatorAlpha by animateFloatAsState(
                if (isSel) 1f else 0f, tween(200), label = "ind"
            )

            Column(
                Modifier.let { if (idx == 0) it.focusRequester(firstScopeFR) else it },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick  = { onSelect(scope.src) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = Color.Transparent,
                        focusedContainerColor = GLASS,
                        contentColor          = if (isSel) accent else LABEL3,
                        focusedContentColor   = accent
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(scope.icon, null, Modifier.size(15.dp))
                        Text(
                            scope.label,
                            fontSize   = 14.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            softWrap   = false
                        )
                        // Live dot for Fuzer
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
                // Apple-style selection indicator line
                Box(
                    Modifier.width(32.dp).height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent.copy(indicatorAlpha))
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SEP))
}

// ── 4  SMART FILTER TRAY ─────────────────────────────────────────────────────
@Composable
private fun SmartFilterTray(
    chips:         List<FilterChip>,
    filtersActive: Boolean,
    firstChipFR:   FocusRequester,
    firstResultFR: FocusRequester,
    onChipTap:     (String) -> Unit,
    onClearAll:    () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .background(Color(0xFF050505))
            .padding(horizontal = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstResultFR.requestFocus() }; true }
                else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(chips) { idx, chip ->
                val accent = when {
                    chip.id.startsWith("sort") -> TINT
                    chip.id.startsWith("g_")   -> LABEL
                    chip.id.startsWith("q_")   -> GOLD
                    chip.id == "dubbed"         -> FUZER
                    chip.id.startsWith("r")     -> GOLD
                    else                        -> LABEL
                }
                Surface(
                    onClick  = { onChipTap(chip.id) },
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = if (chip.isActive) accent.copy(0.18f) else GLASS,
                        focusedContainerColor = if (chip.isActive) accent.copy(0.30f) else GLASS2,
                        contentColor          = if (chip.isActive) accent else LABEL2,
                        focusedContentColor   = accent
                    ),
                    border   = ClickableSurfaceDefaults.border(
                        border        = if (chip.isActive)
                            Border(border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.6f)), shape = RoundedCornerShape(50))
                        else Border(border = androidx.compose.foundation.BorderStroke(0.5.dp, SEP), shape = RoundedCornerShape(50)),
                        focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, accent.copy(0.8f)), shape = RoundedCornerShape(50))
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.height(32.dp)
                        .let { if (idx == 0) it.focusRequester(firstChipFR) else it }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (chip.emoji.isNotEmpty())
                            Text(chip.emoji, fontSize = 12.sp)
                        Text(chip.label, fontSize = 12.sp, fontWeight = if (chip.isActive) FontWeight.SemiBold else FontWeight.Normal, softWrap = false)
                        if (chip.isActive)
                            Icon(Icons.Default.Check, null, Modifier.size(11.dp))
                    }
                }
            }
        }

        // Clear all — appears on the right only when filters are active
        AnimatedVisibility(
            visible = filtersActive,
            enter   = fadeIn(tween(150)) + scaleIn(tween(150)),
            exit    = fadeOut(tween(100)) + scaleOut(tween(100))
        ) {
            Surface(
                onClick  = onClearAll,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = Color.Transparent,
                    focusedContainerColor = Color(0x22FF453A),
                    contentColor          = Color(0xAAFF453A),
                    focusedContentColor   = Color(0xFFFF453A)
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(32.dp)
            ) {
                Box(Modifier.padding(horizontal = 14.dp).fillMaxHeight(), Alignment.Center) {
                    Text("Clear", fontSize = 12.sp)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SEP))
}

// ── 5  POSTER GRID ────────────────────────────────────────────────────────────
@Composable
private fun PosterGrid(
    results:       List<SearchResult>,
    isFuzer:       Boolean,
    gridState:     LazyGridState,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult, Int) -> Unit
) {
    LazyVerticalGrid(
        state                 = gridState,
        columns               = GridCells.Adaptive(minSize = 160.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement   = Arrangement.spacedBy(28.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            PosterCard(
                result   = result,
                isFuzer  = isFuzer,
                modifier = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onClick  = { onResultClick(result, idx) }
            )
        }
    }
}

// ── POSTER CARD ───────────────────────────────────────────────────────────────
@Composable
private fun PosterCard(
    result:   SearchResult,
    isFuzer:  Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(
        if (focused) 1.05f else 1f,
        tween(180, easing = FastOutSlowInEasing),
        label = "s"
    )
    val accent = if (isFuzer) FUZER else TINT

    val qBadge: String? = when {
        result.qualityTag.isNotBlank()                          -> result.qualityTag
        result.title.contains("4K",    ignoreCase = true) ||
        result.title.contains("2160p", ignoreCase = true)       -> "4K"
        result.title.contains("1080p", ignoreCase = true)       -> "FHD"
        result.title.contains("720p",  ignoreCase = true)       -> "HD"
        else                                                    -> null
    }

    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .zIndex(if (focused) 10f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = GLASS,
                    focusedContainerColor = GLASS
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp, accent.copy(0.7f)
                        ),
                        shape  = RoundedCornerShape(12.dp)
                    )
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = Glow(accent.copy(0.25f), 16.dp)
                ),
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .onFocusChanged { focused = it.isFocused }
            ) {
                // Poster image
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(result.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = result.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    // No poster — clean placeholder
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(GLASS2, GLASS))),
                        Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (isFuzer) "\uD83C\uDFAC" else "\uD83C\uDFAC",
                                fontSize = 28.sp
                            )
                            Text(
                                result.title,
                                color     = LABEL3,
                                fontSize  = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines  = 3,
                                modifier  = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }

                // Subtle bottom gradient when focused — for metadata legibility
                AnimatedVisibility(
                    visible = focused,
                    enter   = fadeIn(tween(160)),
                    exit    = fadeOut(tween(120)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(0.65f))
                                )
                            )
                    )
                }

                // Year — top left, minimal
                if (result.releaseYear.isNotBlank()) {
                    Text(
                        result.releaseYear,
                        color    = LABEL3,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Quality badge — top right only (4K, FHD, HD)
                if (qBadge != null) {
                    val badgeColor = when (qBadge) {
                        "4K"  -> Color(0xFFFF453A)   // tvOS red
                        "FHD" -> TINT
                        else  -> FUZER
                    }
                    Text(
                        qBadge,
                        color      = WHITE,
                        fontSize   = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                } else if (result.rating >= 7f) {
                    // Rating only if no quality badge and score ≥ 7 (minimal, not noisy)
                    Text(
                        "%.1f".format(result.rating),
                        color      = GOLD,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Metadata below card — two clean lines
        Text(
            result.title,
            color      = if (focused) WHITE else LABEL,
            fontSize   = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        val sub = buildString {
            if (isFuzer) append("Fuzer") else append(if (result.type == MediaType.TV_SHOW) "Series" else "Movie")
            if (result.releaseYear.isNotBlank()) append(" \u00b7 ${result.releaseYear}")
            if (result.genre.isNotBlank()) append(" \u00b7 ${result.genre}")
        }
        if (sub.isNotBlank())
            Text(
                sub,
                color    = LABEL3,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
    }
}

// ── SHIMMER ───────────────────────────────────────────────────────────────────
@Composable
private fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sp"
    )
    val shimmer = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to GLASS,
            0.5f to GLASS2,
            1.0f to GLASS
        ),
        start = androidx.compose.ui.geometry.Offset(p * 2000f - 1000f, 0f),
        end   = androidx.compose.ui.geometry.Offset(p * 2000f, 600f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(160.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement   = Arrangement.spacedBy(28.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(18) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp)).background(shimmer)
                )
                Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
        }
    }
}

// ── EMPTY STATE ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            val emoji = when {
                query.isNotBlank()              -> "\uD83D\uDD0D"
                source == SearchSource.FUZER    -> "\uD83C\uDFAC"
                else                            -> "\u2600\uFE0F"
            }
            Text(emoji, fontSize = 64.sp)
            Text(
                when {
                    query.isNotBlank() -> "No results for \u201c$query\u201d"
                    source == SearchSource.FUZER -> "Search Fuzer"
                    else -> "What would you like to watch?"
                },
                color      = LABEL,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            Text(
                when {
                    query.isNotBlank() -> "Try a different keyword or adjust filters above"
                    source == SearchSource.FUZER -> "Type to search Israeli content"
                    else -> "Start typing to discover movies and series"
                },
                color     = LABEL3,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 400.dp)
            )
        }
    }
}

// ── FUZER ERROR ───────────────────────────────────────────────────────────────
@Composable
private fun FuzerError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(48.dp)
        ) {
            Box(
                Modifier.size(80.dp)
                    .clip(CircleShape)
                    .background(FUZER.copy(0.08f))
                    .border(1.dp, FUZER.copy(0.25f), CircleShape),
                Alignment.Center
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(34.dp), tint = FUZER.copy(0.6f))
            }
            Text("Fuzer Unavailable", color = LABEL, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                message,
                color     = LABEL3,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 480.dp)
            )
        }
    }
}
