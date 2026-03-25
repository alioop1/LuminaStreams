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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
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

// ─────────────────────────────────────────
// APPLE TV PALETTE — Pure Black + White + Subtle Blue
// ─────────────────────────────────────────
private val BG             = Color(0xFF000000)
private val SURFACE_1      = Color(0xFF1C1C1E)  // iOS dark grouped
private val SURFACE_2      = Color(0xFF2C2C2E)
private val SURFACE_3      = Color(0xFF3A3A3C)
private val WHITE          = Color(0xFFFFFFFF)
private val LABEL          = Color(0xFFEBEBF5)           // iOS primary label
private val LABEL_2        = Color(0x99EBEBF5)           // secondary
private val LABEL_3        = Color(0x4DEBEBF5)           // tertiary
private val SEPARATOR      = Color(0x1FEBEBF5)
private val APPLE_BLUE     = Color(0xFF0A84FF)           // iOS blue
private val APPLE_BLUE_DIM = Color(0x220A84FF)
private val GOLD           = Color(0xFFFFD60A)           // iOS yellow
private val RED            = Color(0xFFFF453A)           // iOS red
private val GREEN          = Color(0xFF30D158)           // iOS green
private val PINK           = Color(0xFFFF375F)           // Fuzer accent

private val CARD_SHAPE  = RoundedCornerShape(12.dp)
private val CHIP_SHAPE  = RoundedCornerShape(10.dp)
private val INPUT_SHAPE = RoundedCornerShape(12.dp)

private val HINTS = listOf(
    "Movies, shows, actors\u2026",
    "Try \"Inception\" or \"Succession\"\u2026",
    "Search by genre or director\u2026",
    "What are you in the mood for?"
)
val GENRES = listOf(
    "Action","Adventure","Animation","Comedy","Crime",
    "Documentary","Drama","Family","Fantasy","History",
    "Horror","Music","Mystery","Romance","Sci-Fi",
    "Thriller","War","Western"
)
private val GENRE_ICON = mapOf(
    "Action" to Icons.Default.Bolt,
    "Comedy" to Icons.Default.SentimentVerySatisfied,
    "Drama"  to Icons.Default.TheaterComedy,
    "Horror" to Icons.Default.Bedtime,
    "Sci-Fi" to Icons.Default.RocketLaunch,
    "Thriller" to Icons.Default.Visibility,
    "Romance" to Icons.Default.Favorite,
    "Documentary" to Icons.Default.PlayCircle,
    "Animation" to Icons.Default.Animation
)

// ═════════════════════════════════════════════════════════════════
// ROOT
// ═════════════════════════════════════════════════════════════════
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
    val firstFilterFR = remember { FocusRequester() }

    BackHandler {
        when {
            state.showFilters        -> onIntent(SearchIntent.ToggleFilters)
            state.query.isNotBlank() -> onIntent(SearchIntent.UpdateQuery(""))
            else                     -> onNavigateBack()
        }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { backFR.requestFocus() } }
    LaunchedEffect(state.showFilters) {
        if (state.showFilters) { delay(250); runCatching { firstFilterFR.requestFocus() } }
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

        // ─ Blurred backdrop from first result poster ─
        val backdropUrl = state.activeResults.firstOrNull()?.backdropUrl
            ?: state.activeResults.firstOrNull()?.posterUrl
        AnimatedVisibility(
            visible = backdropUrl != null && state.activeResults.size > 2,
            enter   = fadeIn(tween(800)),
            exit    = fadeOut(tween(400))
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model              = ImageRequest.Builder(LocalContext.current).data(backdropUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                        .blur(80.dp)
                        .drawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.82f))
                        }
                )
            }
        }

        // ─ Layout ─
        Row(Modifier.fillMaxSize()) {

            // Filter panel slides from left
            AnimatedVisibility(
                visible = state.showFilters,
                enter   = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(220)),
                exit    = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(160))
            ) {
                FilterPanel(
                    filters       = state.filters,
                    isFuzer       = state.source == SearchSource.FUZER,
                    firstFilterFR = firstFilterFR,
                    firstResultFR = firstResultFR,
                    onUpdate      = { onIntent(SearchIntent.UpdateFilters(it)) },
                    onClear       = { onIntent(SearchIntent.ClearFilters) },
                    onClose       = { onIntent(SearchIntent.ToggleFilters) }
                )
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                SearchHeader(
                    state      = state,
                    backFR     = backFR,
                    inputFR    = inputFR,
                    firstTabFR = firstTabFR,
                    onBack     = onNavigateBack,
                    onIntent   = onIntent
                )
                SourceRow(
                    state         = state,
                    firstTabFR    = firstTabFR,
                    firstResultFR = firstResultFR,
                    onIntent      = onIntent
                )
                Box(Modifier.weight(1f)) {
                    when {
                        state.isLoading -> AppleShimmer()
                        state.source == SearchSource.FUZER && state.fuzerError != null ->
                            ErrorState(state.fuzerError!!)
                        state.activeResults.isEmpty() ->
                            EmptyState(state.query, state.source)
                        else -> ResultsGrid(
                            results       = state.activeResults,
                            isFuzer       = state.source == SearchSource.FUZER,
                            firstResultFR = firstResultFR,
                            onResultClick = onResultClick
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// SEARCH HEADER
// ═════════════════════════════════════════════════════════════════
@Composable
private fun SearchHeader(
    state:      SearchState,
    backFR:     FocusRequester,
    inputFR:    FocusRequester,
    firstTabFR: FocusRequester,
    onBack:     () -> Unit,
    onIntent:   (SearchIntent) -> Unit
) {
    val ctx  = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val imm  = remember {
        ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
    }
    var focused  by remember { mutableStateOf(false) }
    var showDrop by remember { mutableStateOf(false) }
    var hintIdx  by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3800); hintIdx = (hintIdx + 1) % HINTS.size } }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back
            Surface(
                onClick  = onBack,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = SURFACE_1,
                    focusedContainerColor = WHITE,
                    contentColor          = LABEL_2,
                    focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                glow     = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(WHITE.copy(0.25f), 12.dp)
                ),
                modifier = Modifier.size(40.dp)
                    .focusRequester(backFR)
                    .focusProperties { down = inputFR }
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(17.dp))
                }
            }

            // Search field — Apple-style frosted pill
            val borderColor by animateColorAsState(
                if (focused) APPLE_BLUE.copy(0.9f) else Color.Transparent, tween(220), label = "bc"
            )
            val bgColor by animateColorAsState(
                if (focused) SURFACE_2 else SURFACE_1, tween(220), label = "bg"
            )

            Box(
                Modifier.weight(1f).height(46.dp)
                    .clip(INPUT_SHAPE)
                    .background(bgColor)
                    .border(1.5.dp, borderColor, INPUT_SHAPE)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val iconColor by animateColorAsState(
                        if (focused) APPLE_BLUE else LABEL_3, tween(200), label = "ic"
                    )
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = iconColor)

                    BasicTextField(
                        value           = state.query,
                        onValueChange   = { v ->
                            onIntent(SearchIntent.UpdateQuery(v))
                            showDrop = v.isNotBlank()
                        },
                        singleLine      = true,
                        textStyle       = TextStyle(
                            color      = WHITE,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp
                        ),
                        cursorBrush     = SolidColor(APPLE_BLUE),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                            showDrop = false
                        }),
                        decorationBox = { inner ->
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
                                        Text(HINTS[i], color = LABEL_3, fontSize = 16.sp)
                                    }
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                            .focusRequester(inputFR)
                            .onFocusChanged { f ->
                                focused  = f.isFocused
                                if (!f.isFocused) showDrop = false
                            }
                            .onPreviewKeyEvent { ev ->
                                when {
                                    ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionCenter -> {
                                        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                        true
                                    }
                                    ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                                        showDrop = false
                                        runCatching { firstTabFR.requestFocus() }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )

                    AnimatedVisibility(
                        state.query.isNotEmpty(),
                        enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.7f),
                        exit  = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.7f)
                    ) {
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery(""))
                                         showDrop = false },
                            shape    = ClickableSurfaceDefaults.shape(CircleShape),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = SURFACE_3,
                                focusedContainerColor = WHITE,
                                contentColor          = LABEL_2,
                                focusedContentColor   = BG
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Icon(Icons.Default.Close, null, Modifier.size(11.dp))
                            }
                        }
                    }
                }
            }

            // Result count
            AnimatedContent(
                targetState = when {
                    state.isLoading               -> "\u2026"
                    state.activeResults.isEmpty() -> ""
                    else                          -> "${state.activeResults.size}"
                },
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "cnt"
            ) { t ->
                if (t.isNotEmpty()) {
                    Text(
                        t,
                        color      = LABEL_3,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.widthIn(min = 36.dp),
                        textAlign  = TextAlign.End
                    )
                } else Spacer(Modifier.width(0.dp))
            }
        }

        // Autocomplete suggestions
        AnimatedVisibility(
            showDrop && state.autocompleteSuggestions.isNotEmpty(),
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit  = shrinkVertically(tween(160)) + fadeOut(tween(130))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SURFACE_1)
            ) {
                state.autocompleteSuggestions.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(SEPARATOR))
                    Surface(
                        onClick  = { onIntent(SearchIntent.UpdateQuery(s)); showDrop = false },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = SURFACE_2,
                            contentColor          = LABEL,
                            focusedContentColor   = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = LABEL_3)
                            Text(s, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Recent searches
        AnimatedVisibility(
            state.query.isBlank() && state.searchHistory.isNotEmpty(),
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit  = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Searches",
                        color      = LABEL_2,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        onClick  = { onIntent(SearchIntent.ClearHistory) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = SURFACE_1,
                            contentColor          = APPLE_BLUE,
                            focusedContentColor   = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp).fillMaxHeight(), Alignment.Center) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.searchHistory) { h ->
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery(h)) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = SURFACE_1,
                                focusedContainerColor = SURFACE_2,
                                contentColor          = LABEL,
                                focusedContentColor   = WHITE
                            ),
                            border   = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, APPLE_BLUE.copy(0.6f)),
                                    shape  = RoundedCornerShape(20.dp)
                                )
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.History, null, Modifier.size(11.dp), tint = LABEL_3)
                                Text(h, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Divider
        Box(Modifier.fillMaxWidth().height(1.dp).background(SEPARATOR))
    }
}

// ═════════════════════════════════════════════════════════════════
// SOURCE ROW — Apple-style segmented
// ═════════════════════════════════════════════════════════════════
private data class SrcDef(val src: SearchSource, val label: String)

@Composable
private fun SourceRow(
    state:         SearchState,
    firstTabFR:    FocusRequester,
    firstResultFR: FocusRequester,
    onIntent:      (SearchIntent) -> Unit
) {
    val srcs = remember {
        listOf(
            SrcDef(SearchSource.ALL,    "All"),
            SrcDef(SearchSource.MOVIES, "Movies"),
            SrcDef(SearchSource.SERIES, "TV Shows"),
            SrcDef(SearchSource.FUZER,  "Fuzer")
        )
    }

    Row(
        Modifier.fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 48.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                    { runCatching { firstResultFR.requestFocus() }; true }
                else false
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Segmented pill group
        Box(
            Modifier.clip(RoundedCornerShape(11.dp)).background(SURFACE_1).padding(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                srcs.forEachIndexed { idx, def ->
                    val isSel = state.source == def.src
                    Surface(
                        onClick  = { onIntent(SearchIntent.SelectSource(def.src)) },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSel) SURFACE_2 else Color.Transparent,
                            focusedContainerColor = SURFACE_3,
                            contentColor          = if (isSel) WHITE else LABEL_2,
                            focusedContentColor   = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.height(34.dp)
                            .let { if (idx == 0) it.focusRequester(firstTabFR) else it }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                def.label,
                                fontSize   = 13.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                softWrap   = false
                            )
                            // Fuzer loading dot
                            if (def.src == SearchSource.FUZER) {
                                if (state.isFuzerLoading) {
                                    val inf = rememberInfiniteTransition(label = "fl")
                                    val a by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "fa")
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(PINK.copy(a)))
                                } else if (state.fuzerResults.isNotEmpty()) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(PINK))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Filter button
        val isFiltered = state.filters.isActive
        Surface(
            onClick  = { onIntent(SearchIntent.ToggleFilters) },
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = if (isFiltered) APPLE_BLUE_DIM else SURFACE_1,
                focusedContainerColor = if (isFiltered) APPLE_BLUE.copy(0.22f) else SURFACE_2,
                contentColor          = if (isFiltered) APPLE_BLUE else LABEL_2,
                focusedContentColor   = WHITE
            ),
            border   = ClickableSurfaceDefaults.border(
                border        = if (isFiltered) Border(border = androidx.compose.foundation.BorderStroke(1.dp, APPLE_BLUE.copy(0.5f)), shape = RoundedCornerShape(11.dp))
                               else Border.None,
                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, APPLE_BLUE.copy(0.7f)), shape = RoundedCornerShape(11.dp))
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (isFiltered) Icons.Default.FilterAlt else Icons.Default.Tune, null, Modifier.size(15.dp))
                Text(
                    if (isFiltered) "Filtered" else "Filter",
                    fontSize   = 13.sp,
                    fontWeight = if (isFiltered) FontWeight.SemiBold else FontWeight.Normal,
                    softWrap   = false
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SEPARATOR))
}

// ═════════════════════════════════════════════════════════════════
// FILTER PANEL — slides in from left
// ═════════════════════════════════════════════════════════════════
@Composable
private fun FilterPanel(
    filters:       SearchFilters,
    isFuzer:       Boolean,
    firstFilterFR: FocusRequester,
    firstResultFR: FocusRequester,
    onUpdate:      (SearchFilters) -> Unit,
    onClear:       () -> Unit,
    onClose:       () -> Unit
) {
    Column(
        Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(SURFACE_1)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight)
                    { runCatching { firstResultFR.requestFocus() }; true }
                else false
            }
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 32.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Filters", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (filters.isActive)
                    Text("Active", color = APPLE_BLUE, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Surface(
                onClick  = onClose,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = SURFACE_2,
                    focusedContainerColor = WHITE,
                    contentColor          = LABEL_2,
                    focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(SEPARATOR).padding(horizontal = 20.dp))

        // Scrollable filter content
        Column(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Genre
            AppleFilterSection(title = "GENRE") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GENRES.forEachIndexed { idx, g ->
                        val sel  = filters.genre == g
                        val icon = GENRE_ICON[g]
                        Surface(
                            onClick  = { onUpdate(filters.copy(genre = if (sel) null else g)) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = if (sel) APPLE_BLUE_DIM else Color.Transparent,
                                focusedContainerColor = SURFACE_2,
                                contentColor          = if (sel) APPLE_BLUE else LABEL,
                                focusedContentColor   = WHITE
                            ),
                            border   = ClickableSurfaceDefaults.border(
                                border        = if (sel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, APPLE_BLUE.copy(0.5f)), shape = RoundedCornerShape(10.dp)) else Border.None,
                                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, APPLE_BLUE.copy(0.5f)), shape = RoundedCornerShape(10.dp))
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                                .let { if (idx == 0) it.focusRequester(firstFilterFR) else it }
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (icon != null)
                                    Icon(icon, null, Modifier.size(14.dp),
                                        tint = if (sel) APPLE_BLUE else LABEL_3)
                                else
                                    Spacer(Modifier.width(14.dp))
                                Text(g, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = APPLE_BLUE)
                            }
                        }
                    }
                }
            }

            // Quality
            AppleFilterSection("QUALITY") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(QualityFilter.ANY to "Any", QualityFilter.HD to "HD", QualityFilter.FHD to "1080p", QualityFilter.UHD to "4K")
                        .forEach { (q, lbl) ->
                            val sel  = filters.quality == q
                            val ac   = when (q) { QualityFilter.UHD -> RED; QualityFilter.FHD -> APPLE_BLUE; QualityFilter.HD -> GREEN; else -> LABEL_2 }
                            Surface(
                                onClick  = { onUpdate(filters.copy(quality = q)) },
                                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors   = ClickableSurfaceDefaults.colors(
                                    containerColor        = if (sel) ac.copy(0.18f) else SURFACE_2,
                                    focusedContainerColor = ac.copy(0.28f),
                                    contentColor          = if (sel) ac else LABEL_2,
                                    focusedContentColor   = WHITE
                                ),
                                border   = ClickableSurfaceDefaults.border(
                                    border        = if (sel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, ac.copy(0.6f)), shape = RoundedCornerShape(8.dp)) else Border.None,
                                    focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, ac.copy(0.8f)), shape = RoundedCornerShape(8.dp))
                                ),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Text(lbl, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                }
            }

            // Rating
            AppleFilterSection("MIN RATING") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0f to "Any", 6f to "6+", 7f to "7+", 8f to "8+", 9f to "9+")
                        .forEach { (v, lbl) ->
                            val sel = filters.minRating == v
                            Surface(
                                onClick  = { onUpdate(filters.copy(minRating = v)) },
                                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors   = ClickableSurfaceDefaults.colors(
                                    containerColor        = if (sel) GOLD.copy(0.18f) else SURFACE_2,
                                    focusedContainerColor = GOLD.copy(0.25f),
                                    contentColor          = if (sel) GOLD else LABEL_2,
                                    focusedContentColor   = WHITE
                                ),
                                border   = ClickableSurfaceDefaults.border(
                                    border        = if (sel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, GOLD.copy(0.6f)), shape = RoundedCornerShape(8.dp)) else Border.None,
                                    focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, GOLD.copy(0.7f)), shape = RoundedCornerShape(8.dp))
                                ),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Text(if (v == 0f) lbl else "\u2605$lbl", fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                }
            }

            // Hebrew Dubbed (Fuzer)
            if (isFuzer) {
                AppleFilterSection("LANGUAGE") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to "All", true to "\uD83C\uDDEE\uD83C\uDDF1 Hebrew Dubbed")
                            .forEach { (v, lbl) ->
                                val sel = filters.dubbedOnly == v
                                Surface(
                                    onClick  = { onUpdate(filters.copy(dubbedOnly = v)) },
                                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors   = ClickableSurfaceDefaults.colors(
                                        containerColor        = if (sel) PINK.copy(0.18f) else SURFACE_2,
                                        focusedContainerColor = PINK.copy(0.25f),
                                        contentColor          = if (sel) PINK else LABEL_2,
                                        focusedContentColor   = WHITE
                                    ),
                                    border   = ClickableSurfaceDefaults.border(
                                        border        = if (sel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, PINK.copy(0.6f)), shape = RoundedCornerShape(8.dp)) else Border.None,
                                        focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PINK.copy(0.7f)), shape = RoundedCornerShape(8.dp))
                                    ),
                                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Text(lbl, fontSize = 10.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                    }
                }
            }
        }

        // Clear button
        AnimatedVisibility(filters.isActive, enter = expandVertically(tween(200)) + fadeIn(tween(160)), exit = shrinkVertically(tween(160)) + fadeOut(tween(130))) {
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                Surface(
                    onClick  = onClear,
                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors   = ClickableSurfaceDefaults.colors(
                        containerColor        = SURFACE_2,
                        focusedContainerColor = RED.copy(0.15f),
                        contentColor          = LABEL,
                        focusedContentColor   = RED
                    ),
                    border   = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, RED.copy(0.5f)), shape = RoundedCornerShape(12.dp))
                    ),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FilterAltOff, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Filters", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    Box(Modifier.width(1.dp).fillMaxHeight().background(SEPARATOR))
}

@Composable
private fun AppleFilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            color         = LABEL_3,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        content()
    }
}

// ═════════════════════════════════════════════════════════════════
// RESULTS GRID
// ═════════════════════════════════════════════════════════════════
@Composable
private fun ResultsGrid(
    results:       List<SearchResult>,
    isFuzer:       Boolean,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 160.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement   = Arrangement.spacedBy(24.dp),
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

// ═════════════════════════════════════════════════════════════════
// MEDIA CARD — Apple TV style poster + minimal info below
// ═════════════════════════════════════════════════════════════════
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
        targetValue   = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "sc"
    )
    val elevation by animateDpAsState(
        targetValue   = if (focused) 24.dp else 0.dp,
        animationSpec = tween(200),
        label         = "el"
    )

    val qBadge: String? = when {
        result.qualityTag.isNotBlank()                    -> result.qualityTag
        result.title.contains("4K",    ignoreCase = true) ||
        result.title.contains("2160p", ignoreCase = true) -> "4K"
        result.title.contains("1080p", ignoreCase = true) -> "FHD"
        result.title.contains("720p",  ignoreCase = true) -> "HD"
        else -> null
    }

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .zIndex(if (focused) 10f else 0f)
        ) {
            Surface(
                onClick  = onClick,
                shape    = ClickableSurfaceDefaults.shape(CARD_SHAPE),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = SURFACE_1,
                    focusedContainerColor = SURFACE_1
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border   = ClickableSurfaceDefaults.border(
                    border        = Border.None,
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    if (isFuzer) PINK else WHITE,
                                    if (isFuzer) PINK.copy(0.4f) else APPLE_BLUE
                                )
                            )
                        ),
                        shape = CARD_SHAPE
                    )
                ),
                glow     = ClickableSurfaceDefaults.glow(
                    glow        = Glow.None,
                    focusedGlow = Glow(
                        elevationColor = if (isFuzer) PINK.copy(0.4f) else WHITE.copy(0.15f),
                        elevation      = elevation
                    )
                ),
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .onFocusChanged { focused = it.isFocused }
            ) {
                // Poster image
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(ctx)
                            .data(result.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = result.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                if (isFuzer) listOf(Color(0xFF16082A), Color(0xFF0A0512))
                                else         listOf(SURFACE_2, SURFACE_1)
                            )
                        ),
                        Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                null,
                                Modifier.size(32.dp),
                                tint = LABEL_3
                            )
                            Text(
                                result.title,
                                color     = LABEL_3,
                                fontSize  = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 10.dp),
                                maxLines  = 3,
                                overflow  = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bottom vignette on focus
                AnimatedVisibility(
                    visible = focused,
                    enter   = fadeIn(tween(200)),
                    exit    = fadeOut(tween(150))
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.6f to Color.Transparent,
                                        1f to Color.Black.copy(0.6f)
                                    )
                                )
                            )
                    )
                }

                // Quality badge — top-left pill
                if (qBadge != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (qBadge) {
                                    "4K"  -> RED
                                    "FHD" -> APPLE_BLUE
                                    else  -> GREEN
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            qBadge,
                            color      = WHITE,
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Rating — top-right (only for TMDB)
                if (!isFuzer && result.rating >= 7f) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "\u2605 %.1f".format(result.rating),
                            color      = GOLD,
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Fuzer pill
                if (isFuzer) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PINK.copy(0.85f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("Fuzer", color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Info below card
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                result.title,
                color      = if (focused) WHITE else LABEL,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (result.releaseYear.isNotBlank()) {
                    Text(result.releaseYear, color = LABEL_3, fontSize = 10.sp)
                    if (result.type != MediaType.PERSON)
                        Box(Modifier.size(3.dp).clip(CircleShape).background(LABEL_3))
                }
                Text(
                    when {
                        isFuzer                          -> "Fuzer"
                        result.type == MediaType.TV_SHOW -> "TV Show"
                        result.type == MediaType.MOVIE   -> "Movie"
                        else                             -> ""
                    },
                    color    = if (isFuzer) PINK.copy(0.6f) else LABEL_3,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// SHIMMER — minimal Apple style
// ═════════════════════════════════════════════════════════════════
@Composable
private fun AppleShimmer() {
    val inf = rememberInfiniteTransition(label = "s")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val shimmer = Brush.linearGradient(
        listOf(SURFACE_1, SURFACE_2, SURFACE_1),
        start = Offset(p * 2000f - 1000f, 0f),
        end   = Offset(p * 2000f, 600f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(160.dp),
        contentPadding        = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement   = Arrangement.spacedBy(24.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(18) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(CARD_SHAPE).background(shimmer))
                Box(Modifier.fillMaxWidth(0.7f).height(11.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.45f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═════════════════════════════════════════════════════════════════
@Composable
private fun EmptyState(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(48.dp)
        ) {
            Icon(
                when (source) {
                    SearchSource.FUZER  -> Icons.Default.CloudQueue
                    SearchSource.MOVIES -> Icons.Default.Movie
                    SearchSource.SERIES -> Icons.Default.Tv
                    else                -> if (query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.PlayCircle
                },
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint     = LABEL_3
            )
            Text(
                when {
                    query.isNotBlank() -> "No results for \u201c$query\u201d"
                    source == SearchSource.FUZER -> "Search Fuzer"
                    else -> "Search LuminaStreams"
                },
                color      = LABEL,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center
            )
            Text(
                when {
                    query.isNotBlank() -> "Check your spelling or try different keywords"
                    source == SearchSource.FUZER -> "Find Israeli dubbed and Hebrew content"
                    else -> "Movies, TV shows, actors and more"
                },
                color     = LABEL_3,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 360.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// ERROR STATE
// ═════════════════════════════════════════════════════════════════
@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(48.dp)
        ) {
            Box(
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(PINK.copy(0.1f))
                    .border(1.dp, PINK.copy(0.25f), CircleShape),
                Alignment.Center
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(30.dp), tint = PINK.copy(0.6f))
            }
            Text("Fuzer Unavailable", color = LABEL, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = LABEL_3, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 400.dp))
        }
    }
}
