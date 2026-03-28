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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ═══ PALETTE ═════════════════════════════════════════════════════
private val BG           = Color(0xFF060608)
private val SURFACE      = Color(0xFF0D0D10)
private val CARD_BG      = Color(0xFF13131A)
private val RED          = Color(0xFFE50914)
private val RED_DIM      = Color(0x33E50914)
private val WHITE        = Color(0xFFFFFFFF)
private val DIM          = Color(0x99FFFFFF)
private val DIM2         = Color(0x28FFFFFF)
private val DIM3         = Color(0x10FFFFFF)
private val GOLD         = Color(0xFFFFCC00)
private val ACCENT_BLUE  = Color(0xFF00D4FF)
private val ACCENT_PINK  = Color(0xFFFF2D78)
private val ACCENT_GREEN = Color(0xFF00E676)

private val HINTS = listOf(
    "Search movies & series...",
    "Try \"Inception\" or \"The Wire\"...",
    "Search by actor or director...",
    "Discover what\u2019s trending..."
)
val GENRES = listOf(
    "Action","Adventure","Animation","Comedy","Crime",
    "Documentary","Drama","Family","Fantasy","History",
    "Horror","Music","Mystery","Romance","Sci-Fi",
    "Thriller","War","Western"
)

// ═══ ROOT ═════════════════════════════════════════════════════════
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
    LaunchedEffect(Unit) { delay(120); runCatching { backFR.requestFocus() } }

    LaunchedEffect(state.showFilters) {
        if (state.showFilters) {
            delay(150)
            runCatching { firstFilterFR.requestFocus() }
        }
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
        if (state.activeResults.isNotEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(240.dp).align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (state.source == SearchSource.FUZER) ACCENT_BLUE.copy(0.04f)
                                else RED.copy(0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── Main Content ──
        Column(
            Modifier
                .fillMaxSize()
                // נעילת הרקע כשהפופאפ פתוח
                .focusProperties { canFocus = !state.showFilters }
        ) {
            TopBar(
                state      = state,
                backFR     = backFR,
                inputFR    = inputFR,
                firstTabFR = firstTabFR,
                onBack     = onNavigateBack,
                onIntent   = onIntent
            )
            TabRow(
                state         = state,
                firstTabFR    = firstTabFR,
                backFR        = backFR,
                firstResultFR = firstResultFR,
                onIntent      = onIntent
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> ShimmerGrid()
                    state.source == SearchSource.FUZER && state.fuzerError != null ->
                        FuzerError(state.fuzerError!!)
                    state.activeResults.isEmpty() -> EmptyState(state.query, state.source)
                    else -> ResultsGrid(
                        results       = state.activeResults,
                        isFuzer       = state.source == SearchSource.FUZER,
                        firstResultFR = firstResultFR,
                        onResultClick = onResultClick
                    )
                }
            }
        }

        // ── Filter Popup Overlay ──
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedVisibility(
            visible = state.showFilters,
            enter   = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(380, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
            exit    = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.7f))
                    .clickable(remember { MutableInteractionSource() }, null) { onIntent(SearchIntent.ToggleFilters) },
                contentAlignment = if (isRtl) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = if (isRtl) 24.dp else 0.dp, top = 24.dp, end = if (isRtl) 0.dp else 24.dp, bottom = 24.dp)
                        .width(460.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF0F0F13).copy(alpha = 0.98f))
                        .padding(vertical = 40.dp)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .focusGroup()
                        // ── נעילה הרמטית של הפוקוס! ──
                        .focusProperties {
                            exit  = { FocusRequester.Cancel }
                            left  = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up    = FocusRequester.Cancel
                            down  = FocusRequester.Cancel
                        }
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.Escape)) {
                                onIntent(SearchIntent.ToggleFilters)
                                true
                            } else false
                        }
                ) {
                    FilterSidebar(
                        filters       = state.filters,
                        isFuzer       = state.source == SearchSource.FUZER,
                        firstFilterFR = firstFilterFR,
                        onUpdate      = { onIntent(SearchIntent.UpdateFilters(it)) },
                        onClear       = { onIntent(SearchIntent.ClearFilters); onIntent(SearchIntent.ToggleFilters) }
                    )
                }
            }
        }
    }
}

// ═══ TOP BAR ═══════════════════════════════════════════════════════
@Composable
private fun TopBar(
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

    val firstSuggestFR = remember { FocusRequester() }
    val firstHistoryFR = remember { FocusRequester() }

    var inputFocused by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    var hintIdx      by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3500); hintIdx = (hintIdx + 1) % HINTS.size } }

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SURFACE, BG.copy(0f))))
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick  = onBack,
                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = DIM3,
                    focusedContainerColor = WHITE,
                    contentColor          = DIM,
                    focusedContentColor   = BG
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                modifier = Modifier.size(38.dp)
                    .focusRequester(backFR)
                    .focusProperties { down = firstTabFR; right = inputFR }
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp)) } }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF8B0000)))),
                    Alignment.Center
                ) { Text("L", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black) }
                Column(verticalArrangement = Arrangement.Center) {
                    Text("LUMINA",  color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp, lineHeight = 12.sp)
                    Text("STREAMS", color = RED,   fontSize = 7.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 2.sp,  lineHeight = 9.sp)
                }
            }

            Box(Modifier.width(1.dp).height(24.dp).background(DIM2))

            val inputShape = RoundedCornerShape(50)
            Box(
                Modifier.weight(1f).height(44.dp)
                    .clip(inputShape)
                    .background(if (inputFocused) Color(0xFF181820) else Color(0xFF0F0F14))
                    .border(
                        width = if (inputFocused) 1.5.dp else 1.dp,
                        color = if (inputFocused) RED.copy(0.7f) else DIM2,
                        shape = inputShape
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val iconColor by animateColorAsState(if (inputFocused) RED else DIM.copy(0.5f), tween(200), label = "ic")
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = iconColor)

                    BasicTextField(
                        value           = state.query,
                        onValueChange   = { v -> onIntent(SearchIntent.UpdateQuery(v)); showDropdown = v.isNotBlank() },
                        singleLine      = true,
                        textStyle       = TextStyle(color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Normal),
                        cursorBrush     = SolidColor(RED),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            imm.hideSoftInputFromWindow(view.windowToken, 0)
                            showDropdown = false
                        }),
                        decorationBox = { inner ->
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    AnimatedContent(
                                        targetState  = hintIdx,
                                        transitionSpec = {
                                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { 8 })
                                                .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -8 })
                                        },
                                        label = "hint"
                                    ) { i ->
                                        Text(HINTS[i], color = DIM.copy(0.3f), fontSize = 14.sp)
                                    }
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                            .focusRequester(inputFR)
                            .focusProperties { up = backFR } // מילוט מעלה לכפתור אחורה
                            .onFocusChanged { f -> inputFocused = f.isFocused }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    when (ev.key) {
                                        Key.DirectionCenter -> {
                                            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            when {
                                                showDropdown && state.autocompleteSuggestions.isNotEmpty() ->
                                                    runCatching { firstSuggestFR.requestFocus() }
                                                state.query.isBlank() && state.searchHistory.isNotEmpty() ->
                                                    runCatching { firstHistoryFR.requestFocus() }
                                                else ->
                                                    runCatching { firstTabFR.requestFocus() }
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            // קפיצה אקטיבית החוצה לכפתור אחורה!
                                            runCatching { backFR.requestFocus() }
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            // אם אין טקסט - חץ שמאלה מקפיץ אותך החוצה
                                            if (state.query.isEmpty()) {
                                                runCatching { backFR.requestFocus() }
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )

                    AnimatedVisibility(state.query.isNotEmpty(), enter = fadeIn(tween(120)) + scaleIn(tween(120)), exit = fadeOut(tween(100)) + scaleOut(tween(100))) {
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery("")); showDropdown = false },
                            shape    = ClickableSurfaceDefaults.shape(CircleShape),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = DIM2,
                                focusedContainerColor = RED,
                                contentColor          = DIM,
                                focusedContentColor   = WHITE
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                            modifier = Modifier.size(22.dp).focusProperties { down = firstTabFR }
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(10.dp)) } }
                    }
                }
            }

            AnimatedContent(
                targetState = when {
                    state.isLoading               -> "..."
                    state.activeResults.isEmpty() -> "\u2013"
                    else                          -> "${state.activeResults.size}"
                },
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "cnt"
            ) { t ->
                Box(
                    Modifier.widthIn(min = 44.dp).height(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (state.activeResults.isNotEmpty() && !state.isLoading) RED_DIM else DIM3)
                        .padding(horizontal = 10.dp),
                    Alignment.Center
                ) {
                    Text(
                        text = t,
                        color = if (state.activeResults.isNotEmpty() && !state.isLoading) RED else DIM,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showDropdown && state.autocompleteSuggestions.isNotEmpty(),
            enter   = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit    = shrinkVertically(tween(130)) + fadeOut(tween(110))
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0E0E16))
                    .border(1.dp, DIM2, RoundedCornerShape(16.dp))
            ) {
                state.autocompleteSuggestions.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(DIM3))
                    Surface(
                        onClick  = { onIntent(SearchIntent.UpdateQuery(s)); showDropdown = false },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color.Transparent,
                            focusedContainerColor = DIM3,
                            contentColor          = DIM,
                            focusedContentColor   = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                            .let { if (i == 0) it.focusRequester(firstSuggestFR) else it }
                            .focusProperties {
                                up = if (i == 0) inputFR else FocusRequester.Default
                                down = if (i == state.autocompleteSuggestions.lastIndex) firstTabFR else FocusRequester.Default
                            }
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, null, Modifier.size(13.dp), tint = RED.copy(0.6f))
                            Text(s, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.query.isBlank() && state.searchHistory.isNotEmpty(),
            enter   = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit    = shrinkVertically(tween(130)) + fadeOut(tween(110))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Recent", color = DIM.copy(0.45f), fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(state.searchHistory) { idx, h ->
                        Surface(
                            onClick  = { onIntent(SearchIntent.UpdateQuery(h)) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = DIM3,
                                focusedContainerColor = SURFACE,
                                contentColor          = DIM,
                                focusedContentColor   = WHITE
                            ),
                            border   = ClickableSurfaceDefaults.border(
                                border        = Border(border = androidx.compose.foundation.BorderStroke(1.dp, DIM2),            shape = RoundedCornerShape(50)),
                                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.dp, RED.copy(0.55f)), shape = RoundedCornerShape(50))
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            modifier = Modifier.height(28.dp)
                                .let { if (idx == 0) it.focusRequester(firstHistoryFR) else it }
                                .focusProperties { up = inputFR; down = firstTabFR }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.History, null, Modifier.size(10.dp))
                                Text(h, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick  = { onIntent(SearchIntent.ClearHistory) },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(
                                containerColor        = Color.Transparent,
                                focusedContainerColor = RED_DIM,
                                contentColor          = RED.copy(0.6f),
                                focusedContentColor   = RED
                            ),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            modifier = Modifier.height(28.dp)
                                .let { if (state.searchHistory.isEmpty()) it.focusRequester(firstHistoryFR) else it }
                                .focusProperties { up = inputFR; down = firstTabFR }
                        ) { Box(Modifier.padding(horizontal = 10.dp).fillMaxHeight(), Alignment.Center) { Text("Clear", fontSize = 11.sp) } }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(RED.copy(0.8f), RED.copy(0.15f), Color.Transparent, Color.Transparent))))
    }
}

// ═══ TAB ROW ═══════════════════════════════════════════════════════
private data class TabDef(val src: SearchSource, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color)

@Composable
private fun TabRow(
    state:         SearchState,
    firstTabFR:    FocusRequester,
    backFR:        FocusRequester,
    firstResultFR: FocusRequester,
    onIntent:      (SearchIntent) -> Unit
) {
    val tabs = remember {
        listOf(
            TabDef(SearchSource.ALL,    "All",      Icons.Default.Apps,       Color(0xFFB0BEC5)),
            TabDef(SearchSource.MOVIES, "Movies",   Icons.Default.Movie,      GOLD),
            TabDef(SearchSource.SERIES, "Series",   Icons.Default.LiveTv,     ACCENT_BLUE),
            TabDef(SearchSource.FUZER,  "Fuzer",    Icons.Default.CloudQueue, ACCENT_PINK)
        )
    }
    val selected = state.source
    val filtersActive = state.filters.isActive

    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .background(SURFACE)
            .padding(horizontal = 24.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown)
                { runCatching { firstResultFR.requestFocus() }; true }
                else false
            },
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { idx, tab ->
            val isSel = selected == tab.src
            val animBg by animateColorAsState(if (isSel) tab.accent.copy(0.14f) else Color.Transparent, tween(200), label = "bg")
            Surface(
                onClick  = { onIntent(SearchIntent.SelectSource(tab.src)) },
                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors   = ClickableSurfaceDefaults.colors(
                    containerColor        = animBg,
                    focusedContainerColor = tab.accent.copy(0.22f),
                    contentColor          = if (isSel) WHITE else DIM,
                    focusedContentColor   = WHITE
                ),
                border   = ClickableSurfaceDefaults.border(
                    border        = if (isSel) Border(border = androidx.compose.foundation.BorderStroke(1.dp, tab.accent.copy(0.55f)), shape = RoundedCornerShape(10.dp)) else Border.None,
                    focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, tab.accent.copy(0.85f)), shape = RoundedCornerShape(10.dp))
                ),
                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                modifier = Modifier.height(36.dp)
                    .let { if (idx == 0) it.focusRequester(firstTabFR) else it }
                    // מילוט שמאלה לכפתור ה-Back עבור הטאב הראשון!
                    .focusProperties {
                        if (idx == 0) left = backFR
                    }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(tab.icon, null, Modifier.size(14.dp), tint = if (isSel) tab.accent else DIM.copy(0.6f))
                    Text(tab.label, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, softWrap = false)
                    if (tab.src == SearchSource.FUZER && state.isFuzerLoading) {
                        val inf = rememberInfiniteTransition(label = "fz")
                        val a by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "fa")
                        Box(Modifier.size(5.dp).clip(CircleShape).background(ACCENT_PINK.copy(a)))
                    } else if (tab.src == SearchSource.FUZER && state.fuzerResults.isNotEmpty()) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(ACCENT_PINK))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Surface(
            onClick  = { onIntent(SearchIntent.ToggleFilters) },
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = if (filtersActive) RED.copy(0.16f) else DIM3,
                focusedContainerColor = RED.copy(0.28f),
                contentColor          = if (filtersActive) RED else DIM,
                focusedContentColor   = WHITE
            ),
            border   = ClickableSurfaceDefaults.border(
                border        = if (filtersActive) Border(border = androidx.compose.foundation.BorderStroke(1.dp, RED.copy(0.6f)), shape = RoundedCornerShape(10.dp)) else Border.None,
                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, RED.copy(0.8f)), shape = RoundedCornerShape(10.dp))
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Tune, null, Modifier.size(14.dp))
                Text(
                    if (filtersActive) "Filtered" else "Filter",
                    fontSize = 12.sp,
                    fontWeight = if (filtersActive) FontWeight.Bold else FontWeight.Normal,
                    softWrap = false
                )
                if (filtersActive) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(RED))
                }
            }
        }
    }
}

// ═══ FILTER SIDEBAR (עיצוב פופאפ PlayerScreen מדוייק!) ════════════════════════
@Composable
private fun FilterSidebar(
    filters:       SearchFilters,
    isFuzer:       Boolean,
    firstFilterFR: FocusRequester,
    onUpdate:      (SearchFilters) -> Unit,
    onClear:       () -> Unit
) {
    Box(Modifier.padding(horizontal = 36.dp)) {
        SidePanelHeader(
            title    = "Filters",
            subtitle = "Refine search results",
            accentColor = RED
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            FilterSection("Sort By") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)) {
                    val sortOptions = listOf(SortBy.POPULARITY to "Popular", SortBy.RATING to "Top Rated", SortBy.NEWEST to "Newest")
                    items(sortOptions.size) { idx ->
                        val (v, lbl) = sortOptions[idx]
                        FilterChipCard(
                            label      = lbl,
                            isSelected = filters.sortBy == v,
                            accentColor= RED,
                            modifier   = if (idx == 0) Modifier.focusRequester(firstFilterFR) else Modifier,
                            onClick    = { onUpdate(filters.copy(sortBy = v)) }
                        )
                    }
                }
            }
        }

        item {
            FilterSection("Genre") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)) {
                    items(GENRES.size) { idx ->
                        val g = GENRES[idx]
                        FilterChipCard(
                            label      = g,
                            isSelected = filters.genre == g,
                            accentColor= RED,
                            onClick    = { onUpdate(filters.copy(genre = if (filters.genre == g) null else g)) }
                        )
                    }
                }
            }
        }

        item {
            FilterSection("Quality") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)) {
                    val qOptions = listOf(QualityFilter.ANY to "Any", QualityFilter.HD to "HD", QualityFilter.FHD to "1080p", QualityFilter.UHD to "4K")
                    items(qOptions.size) { idx ->
                        val (q, lbl) = qOptions[idx]
                        val acnt = when (q) { QualityFilter.UHD -> Color(0xFFFF3D00); QualityFilter.FHD -> ACCENT_BLUE; QualityFilter.HD -> ACCENT_GREEN; else -> RED }
                        FilterChipCard(
                            label      = lbl,
                            isSelected = filters.quality == q,
                            accentColor= acnt,
                            onClick    = { onUpdate(filters.copy(quality = q)) }
                        )
                    }
                }
            }
        }

        item {
            FilterSection("Min Rating") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)) {
                    val rOptions = listOf(0f to "Any", 6f to "6+", 7f to "7+", 8f to "8+", 9f to "9+")
                    items(rOptions.size) { idx ->
                        val (v, lbl) = rOptions[idx]
                        FilterChipCard(
                            label      = if (v == 0f) lbl else "★ $lbl",
                            isSelected = filters.minRating == v,
                            accentColor= GOLD,
                            onClick    = { onUpdate(filters.copy(minRating = v)) }
                        )
                    }
                }
            }
        }

        if (isFuzer) {
            item {
                FilterSection("Language") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)) {
                        val lOptions = listOf(false to "All", true to "Hebrew Dubbed")
                        items(lOptions.size) { idx ->
                            val (v, lbl) = lOptions[idx]
                            FilterChipCard(
                                label      = lbl,
                                isSelected = filters.dubbedOnly == v,
                                accentColor= ACCENT_PINK,
                                onClick    = { onUpdate(filters.copy(dubbedOnly = v)) }
                            )
                        }
                    }
                }
            }
        }

        item {
            if (filters.isActive) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 36.dp)) {
                    Surface(
                        onClick  = onClear,
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        colors   = ClickableSurfaceDefaults.colors(
                            containerColor        = Color(0x1AE50914),
                            focusedContainerColor = RED,
                            contentColor          = RED,
                            focusedContentColor   = WHITE
                        ),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 20.dp)),
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.FilterAltOff, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Clear All Filters", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Filter Helper Components ───────────────────────────────────────────
@Composable
private fun SidePanelHeader(title: String, subtitle: String, accentColor: Color = RED) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).background(accentColor, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = WHITE, fontSize = 26.sp, fontWeight = FontWeight.Black)
                if (subtitle.isNotEmpty()) Text(subtitle, color = DIM, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(
            Brush.horizontalGradient(listOf(accentColor.copy(0.6f), Color(0x08FFFFFF)))
        ))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = DIM,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 36.dp)
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun FilterChipCard(
    label:       String,
    isSelected:  Boolean,
    accentColor: Color,
    modifier:    Modifier = Modifier,
    onClick:     () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val containerBg by animateColorAsState(
        targetValue   = if (focused) Color(0xFF282832) else Color(0x0CFFFFFF),
        animationSpec = tween(200), label = "bgAnim"
    )
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = WHITE,
            focusedContentColor = WHITE
        ),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(0.7f), 20.dp)),
        modifier = modifier.height(64.dp).onFocusChanged { focused = it.isFocused }
    ) {
        Box(
            Modifier.fillMaxHeight().background(containerBg, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp),
            Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = WHITE, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold)
                if (isSelected) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ═══ RESULTS GRID ════════════════════════════════════════════════════
@Composable
private fun ResultsGrid(
    results:       List<SearchResult>,
    isFuzer:       Boolean,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = if (isFuzer) 156.dp else 140.dp),
        contentPadding        = PaddingValues(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement   = Arrangement.spacedBy(20.dp),
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

// ═══ MEDIA CARD ══════════════════════════════════════════════════════
@Composable
private fun MediaCard(
    result:   SearchResult,
    isFuzer:  Boolean,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    val ctx     = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val zoom by animateFloatAsState(
        targetValue   = if (focused) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "szoom"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (focused) 0.15f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label         = "oalpha"
    )
    val accent = if (isFuzer) ACCENT_PINK else RED

    val qBadge: String? = when {
        result.qualityTag.isNotBlank()                    -> result.qualityTag
        result.title.contains("4K",    ignoreCase = true) ||
                result.title.contains("2160p", ignoreCase = true) -> "4K"
        result.title.contains("1080p", ignoreCase = true) -> "FHD"
        result.title.contains("720p",  ignoreCase = true) -> "HD"
        else -> null
    }
    val isDubbed = isFuzer && result.title.contains("מדובב", ignoreCase = true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .zIndex(if (focused) 10f else 0f)
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        Surface(
            onClick  = onClick,
            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors   = ClickableSurfaceDefaults.colors(
                containerColor        = CARD_BG,
                focusedContainerColor = CARD_BG
            ),
            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border   = ClickableSurfaceDefaults.border(
                border        = Border.None,
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(2.5.dp, accent.copy(0.8f)),
                    shape  = RoundedCornerShape(12.dp)
                )
            ),
            glow     = ClickableSurfaceDefaults.glow(
                glow        = Glow.None,
                focusedGlow = Glow(accent.copy(0.4f), 24.dp)
            ),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focused = it.isFocused }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(300).build(),
                        contentDescription = result.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), CARD_BG))),
                        Alignment.Center
                    ) {
                        androidx.tv.material3.Text(result.title, color = WHITE.copy(0.55f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                }

                val gradientOpacity = if (focused) 0.95f else 0.7f
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.65f).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = gradientOpacity))))
                )

                if (focused) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(WHITE.copy(overlayAlpha * 2f), Color.Transparent, Color.Transparent)))
                    )
                }

                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDubbed) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp)).background(ACCENT_PINK).padding(horizontal = 6.dp, vertical = 3.dp)
                        ) { androidx.tv.material3.Text("🎤 DUB", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    } else if (result.releaseYear.isNotBlank()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 6.dp, vertical = 3.dp)
                        ) { androidx.tv.material3.Text(result.releaseYear, color = DIM, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }

                    if (isFuzer && !isDubbed) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF00B0FF)).padding(horizontal = 6.dp, vertical = 3.dp)
                        ) { androidx.tv.material3.Text("💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                    }
                }

                if (qBadge != null) {
                    val qColor = when (qBadge) { "4K" -> Color(0xFFFF3D00); "FHD" -> ACCENT_BLUE; else -> ACCENT_GREEN }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(qColor).padding(horizontal = 6.dp, vertical = 3.dp)
                    ) { androidx.tv.material3.Text(qBadge, color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                }

                Column(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    androidx.tv.material3.Text(
                        result.title,
                        color      = WHITE,
                        fontSize   = 13.sp,
                        fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (result.rating > 0f) {
                            androidx.tv.material3.Text("★ %.1f".format(result.rating), color = GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            androidx.tv.material3.Text("•", color = DIM3, fontSize = 11.sp)
                        }
                        val typeStr = when {
                            isFuzer -> "Fuzer"
                            result.type == MediaType.TV_SHOW -> "TV Show"
                            else -> "Movie"
                        }
                        androidx.tv.material3.Text(typeStr, color = DIM.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ═══ SHIMMER ═══════════════════════════════════════════════════════
@Composable
private fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "sp"
    )
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFF0F0F14), Color(0xFF1A1A24), Color(0xFF0F0F14)),
        start = Offset(p * 1600f - 800f, 0f),
        end   = Offset(p * 1600f, 400f)
    )
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(148.dp),
        contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(18) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.65f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.4f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
        }
    }
}

// ═══ EMPTY STATE ═════════════════════════════════════════════════════
@Composable
private fun EmptyState(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            val emoji = when (source) {
                SearchSource.FUZER  -> "\uD83D\uDCBE"
                SearchSource.MOVIES -> "\uD83C\uDFAC"
                SearchSource.SERIES -> "\uD83D\uDCFA"
                else                -> if (query.isNotBlank()) "\uD83D\uDD0D" else "\uD83C\uDF1F"
            }
            Text(emoji, fontSize = 56.sp)
            Text(
                when {
                    query.isNotBlank() -> "No results for \u201c$query\u201d"
                    source == SearchSource.FUZER -> "Fuzer Torrent Search"
                    else -> "Discover Something Great"
                },
                color      = WHITE,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            Text(
                when {
                    query.isNotBlank() -> "Try a different keyword or adjust filters"
                    source == SearchSource.FUZER -> "Type to search Hebrew content on Fuzer"
                    else -> "Search above or browse by genre with filters"
                },
                color     = DIM.copy(0.5f),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.widthIn(max = 380.dp)
            )
        }
    }
}

// ═══ FUZER ERROR ═════════════════════════════════════════════════════
@Composable
private fun FuzerError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(ACCENT_PINK.copy(0.08f))
                    .border(1.dp, ACCENT_PINK.copy(0.3f), CircleShape),
                Alignment.Center
            ) { Icon(Icons.Default.CloudOff, null, Modifier.size(32.dp), tint = ACCENT_PINK.copy(0.7f)) }
            Text("Fuzer Unavailable", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = DIM.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
        }
    }
}