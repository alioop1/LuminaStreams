@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:Suppress("SpellCheckingInspection", "UNUSED_PARAMETER")

package com.luminastreams.tv.presentation.home

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.zIndex
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun ContentLayer(
    rows: List<RowDef>, contentAlpha: Float, focusState: HomeFocusState, activeTab: String, activeFilter: String?,
    maxPanelH: Dp, rowHeightFor: (Int) -> Dp, firstContentIndex: Int,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie?) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit
) {
    val firstNavFR = remember { FocusRequester() }
    val firstCardFRs = remember { List(30) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        if (focusState.isNavFocused) {
            runCatching { firstNavFR.requestFocus() }
        } else if (rows.isNotEmpty()) {
            runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex.coerceIn(0, rows.size - 1))?.requestFocus() }
        }
    }

    LaunchedEffect(rows.size) {
        if (!initialFocusDone && rows.isNotEmpty()) {
            delay(380)
            initialFocusDone = true
            if (!focusState.isNavFocused) runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex.coerceIn(0, rows.size - 1))?.requestFocus() }
        }
    }

    LaunchedEffect(focusState.focusTrigger) {
        if (focusState.focusTrigger > 0) {
            focusState.currentRowIndex = firstContentIndex
            focusState.isNavFocused = false
            repeat(5) {
                delay(80)
                if (runCatching { firstCardFRs.getOrNull(firstContentIndex)?.requestFocus(); true }.getOrDefault(false)) return@repeat
            }
        }
    }

    Box(Modifier.fillMaxSize().focusGroup().onPreviewKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (ev.key) {
            Key.DirectionUp -> {
                if (!focusState.isNavFocused && focusState.currentRowIndex == 0) {
                    focusState.isNavFocused = true
                    runCatching { firstNavFR.requestFocus() }
                    true
                } else false
            }
            Key.DirectionDown -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    runCatching { firstCardFRs.firstOrNull()?.requestFocus() }
                    true
                } else false
            }
            Key.Back, Key.Escape -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                    true
                } else false
            }
            else -> false
        }
    }) {
        TwoRowNavBar(activeTab, firstNavFR, onSearch, onHomeTab, onMoviesTab, onSeriesTab, onFuzer, onWatchlist, onSettings, onIptv, { focusState.isNavFocused = true; focusState.currentRowIndex = 0 }, Modifier.fillMaxWidth().height(NAV_H).align(Alignment.TopStart).zIndex(10f))

        Box(Modifier.fillMaxWidth().height(maxPanelH).align(Alignment.BottomStart)) {
            RowsPanel(rows, focusState, firstCardFRs, maxPanelH, rowHeightFor, firstContentIndex, activeFilter, onStudioFilterClick, onLoadMore, onHeroUpdate, onMovieClick)
        }
    }
}

@Composable
fun RowsPanel(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>, maxPanelH: Dp,
    rowHeightFor: (Int) -> Dp, firstContentIndex: Int, activeFilter: String?,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onItemFocus: (Movie?) -> Unit, onItemClick: (String) -> Unit
) {
    if (rows.isEmpty()) return

    val verticalScrollState = rememberLazyListState()

    LaunchedEffect(focusState.currentRowIndex, rows.size) {
        val curRow = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
        verticalScrollState.animateScrollToItem(curRow)
    }

    LazyColumn(
        state = verticalScrollState,
        modifier = Modifier.fillMaxWidth().height(maxPanelH),
        userScrollEnabled = false,
        contentPadding = PaddingValues(0.dp)
    ) {
        itemsIndexed(rows, key = { _, item -> item.id }) { index, rowDef ->
            val rh = rowHeightFor(index)
            val isLand = (index == firstContentIndex)

            IsolatedRowWrapper(
                index = index,
                rowDef = rowDef,
                rh = rh,
                maxPanelH = maxPanelH,
                isLand = isLand,
                focusState = focusState,
                cardFR = rowFRs.getOrNull(index),
                activeFilter = activeFilter,
                onFocus = { m ->
                    focusState.currentRowIndex = index
                    focusState.isNavFocused = false
                    onItemFocus(m)
                },
                onItemClick = onItemClick,
                onLoadMore = onLoadMore,
                onStudioFilterClick = onStudioFilterClick
            )
        }
    }
}

@Composable
private fun IsolatedRowWrapper(
    index: Int, rowDef: RowDef, rh: Dp, maxPanelH: Dp, isLand: Boolean,
    focusState: HomeFocusState, cardFR: FocusRequester?, activeFilter: String?,
    onFocus: (Movie?) -> Unit, onItemClick: (String) -> Unit,
    onLoadMore: (String) -> Unit, onStudioFilterClick: (String?) -> Unit
) {
    val isActive by remember(focusState, index) {
        derivedStateOf { !focusState.isNavFocused && focusState.currentRowIndex == index }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(maxPanelH).graphicsLayer {
            alpha = if (isActive) 1f else 0.22f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = Alignment.BottomStart
    ) {
        Box(Modifier.fillMaxWidth().height(rh)) {
            if (rowDef is RowDef.StudioRibbon) {
                StudioRibbonRow(isActive, cardFR, activeFilter, onStudioFilterClick, onFocus = { onFocus(null) })
            } else if (isLand) {
                when (rowDef) {
                    is RowDef.Regular -> LandscapeRow(rowDef.title, rowDef.movies, isActive, cardFR, { onFocus(it) }, onItemClick) { onLoadMore(rowDef.id) }
                    is RowDef.Studio -> LandscapeStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, { onFocus(it) }, onItemClick) { onLoadMore(rowDef.id) }
                }
            } else {
                when (rowDef) {
                    is RowDef.Regular -> PortraitRow(rowDef.title, rowDef.movies, isActive, cardFR, { onFocus(it) }, onItemClick) { onLoadMore(rowDef.id) }
                    is RowDef.Studio -> PortraitStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, { onFocus(it) }, onItemClick) { onLoadMore(rowDef.id) }
                }
            }
        }
    }
}

@Composable
fun LandscapeRow(title: String, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> LandscapeCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun LandscapeStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = Color.White.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> LandscapeCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun PortraitRow(title: String, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> PosterCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, PORT_W, PORT_H, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun PortraitStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = Color.White.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> PosterCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, PORT_W, PORT_H, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun StudioRibbonRow(isActive: Boolean, cardFR: FocusRequester?, activeFilter: String?, onStudioFilterClick: (String?) -> Unit, onFocus: () -> Unit) {
    val brands = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    val rowState = rememberLazyListState()
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(tr("Browse by Studio", "סנן לפי אולפן"), color = Color.White.copy(if (isActive) 1f else 0.4f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, modifier = Modifier.padding(start = 52.dp, bottom = 12.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(brands, key = { _, b -> b.name }) { i, brand ->
                StudioLogoButton(
                    brand,
                    activeFilter == brand.name,
                    if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    onFocused = onFocus
                ) { onStudioFilterClick(brand.name) }
            }
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

@Composable
private fun PagedRowLoadTrigger(rowState: androidx.compose.foundation.lazy.LazyListState, onLoadMore: () -> Unit) {
    val loadMoreState = remember { derivedStateOf { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == rowState.layoutInfo.totalItemsCount - 1 } }
    LaunchedEffect(loadMoreState.value) { if (loadMoreState.value) onLoadMore() }
}