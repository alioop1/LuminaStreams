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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.luminastreams.tv.core.tr
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

    val activeCategoryFR = remember { FocusRequester() }
    val safeIndex = focusState.currentRowIndex.coerceIn(0, maxOf(0, rows.size - 1))
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val forwardKey = if (isRtl) Key.DirectionLeft else Key.DirectionRight

    // One FocusRequester per row — recreated when row count changes
    val rowFRs = remember(rows.size) {
        List(rows.size) { FocusRequester() }
    }

    // Safe focus request with aggressive retry
    suspend fun safeFocus(fr: FocusRequester?) {
        if (fr == null) return
        repeat(12) { attempt ->
            delay(if (attempt == 0) 80L else 100L)
            try {
                fr.requestFocus()
                return // Success
            } catch (_: IllegalStateException) {
                // FocusRequester not yet attached — retry
            }
        }
    }

    // Focus trigger: request focus on the active row's first item
    LaunchedEffect(focusState.focusTrigger, safeIndex) {
        if (focusState.focusTrigger > 0 && !focusState.isNavFocused) {
            safeFocus(rowFRs.getOrNull(safeIndex))
        }
    }

    // Initial focus on first load
    LaunchedEffect(Unit) {
        if (!focusState.isNavFocused) {
            delay(400)
            safeFocus(rowFRs.getOrNull(safeIndex))
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomStart) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {

            // ── SIDEBAR ──────────────────────────────────────────────
            val listState = rememberLazyListState()

            LaunchedEffect(safeIndex) {
                listState.animateScrollToItem(maxOf(0, safeIndex - 1))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .focusProperties { enter = { activeCategoryFR } }
                    .focusGroup()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            if (ev.key == forwardKey) {
                                // Move focus from sidebar → posters
                                focusState.focusTrigger++
                                return@onPreviewKeyEvent true
                            }
                            if (ev.key == Key.DirectionUp && safeIndex == 0) {
                                focusState.isNavFocused = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    },
                verticalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(vertical = 40.dp)
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, r -> 
                        when (r) { 
                            is RowDef.Regular -> r.id 
                            is RowDef.Studio -> r.id 
                            is RowDef.StudioRibbon -> "studios" 
                        } 
                    },
                    contentType = { _, r -> r::class.simpleName }
                ) { i, rowDef ->
                    val isActive = i == safeIndex
                    val title = when (rowDef) {
                        is RowDef.Regular -> rowDef.title
                        is RowDef.Studio -> studioLabel(rowDef.brand)
                        is RowDef.StudioRibbon -> tr("Studios", "אולפנים")
                    }

                    Surface(
                        onClick = { focusState.focusTrigger++ },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = WHITE.copy(alpha = 0.15f),
                            contentColor = if (isActive) WHITE else WHITE.copy(alpha = 0.4f),
                            focusedContentColor = WHITE
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = (if (isActive) Modifier.focusRequester(activeCategoryFR) else Modifier)
                            .padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                            .fillMaxWidth()
                            .onFocusChanged { s -> if (s.isFocused) focusState.currentRowIndex = i }
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

            // Divider
            Box(Modifier.width(2.dp).fillMaxHeight(0.5f).background(Color(0x22FFFFFF)))

            // ── CONTENT AREA ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionUp -> {
                                if (safeIndex > 0) {
                                    focusState.currentRowIndex--
                                    focusState.focusTrigger++
                                    true
                                } else {
                                    focusState.isNavFocused = true
                                    true
                                }
                            }
                            Key.DirectionDown -> {
                                if (safeIndex < rows.size - 1) {
                                    focusState.currentRowIndex++
                                    focusState.focusTrigger++
                                    true
                                } else true // consume at bottom
                            }
                            Key.Back, Key.Escape -> {
                                runCatching { activeCategoryFR.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                val firstIsLand = rows.firstOrNull() !is RowDef.StudioRibbon

                rows.forEachIndexed { index, rowDef ->
                    val isActive = index == safeIndex
                    val isNear = abs(index - safeIndex) <= 1
                    val rowFR = rowFRs.getOrNull(index) ?: return@forEachIndexed

                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0f,
                        animationSpec = tween(300),
                        label = "rowAlpha"
                    )

                    if (isNear) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = animatedAlpha
                                    val scale = 0.98f + (0.02f * animatedAlpha)
                                    scaleX = scale; scaleY = scale
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val isLand = index == 0 && firstIsLand
                            val h = when {
                                rowDef is RowDef.StudioRibbon -> 110.dp
                                isLand -> ROW_LANDSCAPE_H
                                else -> ROW_PORTRAIT_H
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().height(h),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                when (rowDef) {
                                    is RowDef.Regular -> {
                                        if (isLand) LandscapeRowData(rowDef.movies, isActive, rowFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                        else PortraitRowData(rowDef.movies, isActive, rowFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                    }
                                    is RowDef.Studio -> {
                                        if (isLand) LandscapeRowData(rowDef.movies, isActive, rowFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                        else PortraitRowData(rowDef.movies, isActive, rowFR, { onHeroUpdate(it) }, onMovieClick) { onLoadMore(rowDef.id) }
                                    }
                                    is RowDef.StudioRibbon -> {
                                        StudioRibbonRowData(isActive, rowFR, activeFilter, onStudioFilterClick) { onHeroUpdate(null) }
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
private fun LandscapeRowData(movies: List<Movie>, isActive: Boolean, firstItemFR: FocusRequester, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    RememberPagedRowLoad(rowState, onLoadMore)

    // Reset to start when row becomes active (e.g. returning from navigation)
    LaunchedEffect(isActive) {
        if (isActive) {
            rowState.scrollToItem(0)
        }
    }

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .focusGroup()
    ) {
        itemsIndexed(movies, key = { index, m -> "${m.id}_$index" }, contentType = { _, _ -> "movie" }) { index, movie ->
            val mod = if (index == 0) Modifier.focusRequester(firstItemFR) else Modifier
            LandscapeCard(movie, mod, { 
                onFocus(movie) 
            }) { onClick(movie.id) }
        }
    }
}

@Composable
private fun PortraitRowData(movies: List<Movie>, isActive: Boolean, firstItemFR: FocusRequester, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    RememberPagedRowLoad(rowState, onLoadMore)

    // Reset to start when row becomes active
    LaunchedEffect(isActive) {
        if (isActive) {
            rowState.scrollToItem(0)
        }
    }

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .focusGroup()
    ) {
        itemsIndexed(movies, key = { index, m -> "${m.id}_$index" }, contentType = { _, _ -> "movie" }) { index, movie ->
            val mod = if (index == 0) Modifier.focusRequester(firstItemFR) else Modifier
            PosterCard(movie, mod, PORT_W, PORT_H, { 
                onFocus(movie) 
            }) { onClick(movie.id) }
        }
    }
}

@Composable
private fun StudioRibbonRowData(isActive: Boolean, firstItemFR: FocusRequester, activeFilter: String?, onStudioFilterClick: (String?) -> Unit, onFocus: () -> Unit) {
    val brands = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU, StudioBrand.SONY)
    val targetIndex = maxOf(0, brands.indexOfFirst { it.name == activeFilter })
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
        modifier = Modifier.fillMaxWidth()
            .focusProperties { canFocus = isActive }
            .focusGroup()
    ) {
        itemsIndexed(brands, key = { _, b -> b.name }, contentType = { _, _ -> "brand" }) { index, brand ->
            val mod = if (index == targetIndex) Modifier.focusRequester(firstItemFR) else Modifier
            StudioLogoButton(
                brand = brand, 
                isSelected = activeFilter == brand.name, 
                modifier = mod, 
                onFocused = { onFocus() }, 
                onClick = { onStudioFilterClick(brand.name) }
            )
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
        StudioBrand.SONY -> translateText("Sony Pictures", "תוכן של סוני")
    }
}