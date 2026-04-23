@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:Suppress("SpellCheckingInspection")

package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ConsoleRowCycler(
    rows: List<RowDef>, focusState: HomeFocusState, activeFilter: String?,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie?) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return

    // Master focus requesters to guarantee exact D-Pad routing
    val firstCardFR = remember { FocusRequester() }
    val sidebarFR = remember { FocusRequester() }

    val safeIndex = focusState.currentRowIndex.coerceIn(0, maxOf(0, rows.size - 1))

    // Layout direction aware navigation logic (Hebrew LTR vs English LTR)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val forwardKey = if (isRtl) Key.DirectionLeft else Key.DirectionRight

    // Trigger focus to posters only when navigating from Sidebar/TopBar
    LaunchedEffect(focusState.focusTrigger) {
        if (focusState.focusTrigger > 0 && !focusState.isNavFocused) {
            delay(150) // Wait for crossfade to clear the layout tree
            runCatching { firstCardFR.requestFocus() }
        }
    }

    // Initial Focus
    LaunchedEffect(Unit) {
        if (!focusState.isNavFocused) {
            delay(300)
            runCatching { firstCardFR.requestFocus() }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ----------------------------------------------------
            // SIDEBAR: Focusable, native list of Categories
            // ----------------------------------------------------
            val listState = rememberLazyListState()

            // Auto-scroll the sidebar so active category is always centered
            LaunchedEffect(safeIndex) {
                listState.animateScrollToItem(maxOf(0, safeIndex - 1))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .focusRequester(sidebarFR) // Allows the 'BACK' key to snap here!
                    .focusProperties {
                        // FIXED: When returning from posters, force spatial focus to land EXACTLY on the active category to prevent geometric jumping bugs
                        enter = { sidebarFR }
                    }
                    .focusGroup()
                    .focusRestorer()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            // Safely move from Sidebar -> Posters
                            if (ev.key == forwardKey) {
                                runCatching { firstCardFR.requestFocus() }
                                return@onPreviewKeyEvent true
                            }
                            // Explicitly force focus to the NavBar if pressing UP on the top category
                            if (ev.key == Key.DirectionUp && safeIndex == 0) {
                                focusState.isNavFocused = true
                                return@onPreviewKeyEvent true // Consume the event so native focus doesn't guess randomly!
                            }
                        }
                        false
                    },
                verticalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(vertical = 40.dp)
            ) {
                itemsIndexed(rows) { i, rowDef ->
                    val isActive = i == safeIndex
                    val title = when (rowDef) {
                        is RowDef.Regular -> rowDef.title
                        is RowDef.Studio -> studioLabel(rowDef.brand)
                        is RowDef.StudioRibbon -> tr("Studios", "אולפנים")
                    }

                    val surfaceMod = if (isActive) Modifier.focusRequester(sidebarFR) else Modifier

                    Surface(
                        onClick = {
                            // If they explicitly CLICK a category, snap focus right into the posters
                            focusState.focusTrigger++
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = WHITE.copy(alpha = 0.15f),
                            contentColor = if (isActive) WHITE else WHITE.copy(alpha = 0.4f),
                            focusedContentColor = WHITE
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = surfaceMod
                            .padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                // Quietly update the background rows without stealing focus
                                if (state.isFocused) {
                                    focusState.currentRowIndex = i
                                }
                            }
                    ) {
                        Text(
                            text = title,
                            fontSize = if (isActive) 17.sp else 15.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Minimalist Divider Line
            Box(modifier = Modifier.width(2.dp).fillMaxHeight(0.5f).background(Color(0x22FFFFFF)))

            // ----------------------------------------------------
            // CONTENT: Stacked Preloading Architecture
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        when (ev.key) {
                            Key.DirectionUp -> {
                                if (safeIndex > 0) {
                                    focusState.currentRowIndex--
                                    focusState.focusTrigger++ // triggers focus lock to new category
                                    true
                                } else {
                                    // Explicitly force focus to the NavBar if pressing UP on the top row's posters
                                    focusState.isNavFocused = true
                                    true // Consume the event so native focus doesn't guess randomly!
                                }
                            }
                            Key.DirectionDown -> {
                                if (safeIndex < rows.size - 1) {
                                    focusState.currentRowIndex++
                                    focusState.focusTrigger++ // triggers focus lock to new category
                                    true
                                } else false
                            }
                            Key.Back, Key.Escape -> {
                                // PS5 MAGIC: Pressing Back instantly drops you out of the posters and into the Sidebar
                                runCatching { sidebarFR.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // Render multiple rows simultaneously to force image preloading
                rows.forEachIndexed { index, rowDef ->
                    val isActive = index == safeIndex
                    val isNear = abs(index - safeIndex) <= 2

                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0f,
                        animationSpec = tween(300),
                        label = "rowAlpha"
                    )

                    // Keep it in composition if near or fading
                    if (isNear || animatedAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = animatedAlpha
                                    val scale = 0.98f + (0.02f * animatedAlpha)
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .focusProperties { canFocus = isActive },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val isLand = rows.indexOf(rowDef) == rows.indexOfFirst { it !is RowDef.StudioRibbon }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isLand) ROW_LANDSCAPE_H else ROW_PORTRAIT_H),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                when (rowDef) {
                                    is RowDef.Regular -> {
                                        if (isLand) LandscapeRowData(rowDef.movies, isActive, firstCardFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                        else PortraitRowData(rowDef.movies, isActive, firstCardFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                    }
                                    is RowDef.Studio -> {
                                        if (isLand) LandscapeRowData(rowDef.movies, isActive, firstCardFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                        else PortraitRowData(rowDef.movies, isActive, firstCardFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                    }
                                    is RowDef.StudioRibbon -> {
                                        StudioRibbonRowData(isActive, firstCardFR, activeFilter, onStudioFilterClick) { onHeroUpdate(null) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------
// Internal Data Renderers
// ----------------------------------------------------------------------------------

@Composable
private fun LandscapeRowData(movies: List<Movie>, isActive: Boolean, firstCardFR: FocusRequester, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    RememberPagedRowLoad(rowState, onLoadMore)

    LaunchedEffect(isActive) {
        if (isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0)
    }

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        // FIXED: Only attach FocusGroup and Restorer to the active row to prevent spatial focus from leaking into invisible rows
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .let { if (isActive) it.focusGroup().focusRestorer() else it }
    ) {
        itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
            val mod = if (isActive && index == 0) Modifier.focusRequester(firstCardFR) else Modifier
            LandscapeCard(movie, mod, { onFocus(movie) }) { onClick(movie.id) }
        }
    }
}

@Composable
private fun PortraitRowData(movies: List<Movie>, isActive: Boolean, firstCardFR: FocusRequester, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    RememberPagedRowLoad(rowState, onLoadMore)

    LaunchedEffect(isActive) {
        if (isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0)
    }

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .let { if (isActive) it.focusGroup().focusRestorer() else it }
    ) {
        itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
            val mod = if (isActive && index == 0) Modifier.focusRequester(firstCardFR) else Modifier
            PosterCard(movie, mod, PORT_W, PORT_H, { onFocus(movie) }) { onClick(movie.id) }
        }
    }
}

@Composable
private fun StudioRibbonRowData(isActive: Boolean, firstCardFR: FocusRequester, activeFilter: String?, onStudioFilterClick: (String?) -> Unit, onFocus: () -> Unit) {
    val brands = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    val targetIndex = maxOf(0, brands.indexOfFirst { it.name == activeFilter })
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .let { if (isActive) it.focusGroup().focusRestorer() else it }
    ) {
        itemsIndexed(brands) { index, brand ->
            val mod = if (isActive && index == targetIndex) Modifier.focusRequester(firstCardFR) else Modifier
            StudioLogoButton(brand = brand, isSelected = activeFilter == brand.name, modifier = mod, onFocused = { onFocus() }, onClick = { onStudioFilterClick(brand.name) })
        }
    }
}

@Composable
fun studioLabel(b: StudioBrand): String {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun translateText(en: String, he: String) = if (isRtl) he else en
    return when (b) {
        StudioBrand.NETFLIX -> translateText("Netflix Originals", "מקור של נטפליקס")
        StudioBrand.APPLE_TV -> translateText("Apple TV+ Originals", "מקור של אפל TV")
        StudioBrand.DISNEY -> translateText("Disney+ Exclusives", "בלעדי לדיסני+")
        StudioBrand.HBO -> translateText("HBO Max Exclusives", "בלעדי ל-HBO")
        StudioBrand.AMAZON -> translateText("Amazon Originals", "מקור של אמאזון")
        StudioBrand.PARAMOUNT -> translateText("Paramount+ Exclusives", "בלעדי לפרמאונט")
        StudioBrand.HULU -> translateText("Hulu Originals", "מקור של הולו")
    }
}