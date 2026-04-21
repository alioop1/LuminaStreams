@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:Suppress("SpellCheckingInspection")

package com.luminastreams.tv.presentation.home
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.core.*
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
@Composable
fun ContentLayer(
    rows: List<RowDef>, @Suppress("UNUSED_PARAMETER") contentAlpha: Float, focusState: HomeFocusState, activeTab: String, activeFilter: String?,
    maxPanelH: Dp, rowHeightFor: (Int) -> Dp, firstContentIndex: Int,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie?) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit
) {
    val firstNavFR = remember { FocusRequester() }
    val rowFRs = remember { List(30) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }
    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH
    val animatedContentAlpha by animateFloatAsState(targetValue = contentAlpha, animationSpec = if (isHighTier) tween(250, easing = LinearEasing) else snap(), label = "contentAlpha")

    LaunchedEffect(Unit) {
        delay(150)
        if (focusState.isNavFocused) {
            runCatching { firstNavFR.requestFocus() }
        } else if (rows.isNotEmpty()) {
            runCatching { rowFRs.getOrNull(focusState.currentRowIndex.coerceIn(0, rows.size - 1))?.requestFocus() }
        }
    }

    LaunchedEffect(rows.size) {
        if (!initialFocusDone && rows.isNotEmpty()) {
            delay(380)
            initialFocusDone = true
            if (!focusState.isNavFocused) runCatching { rowFRs.getOrNull(focusState.currentRowIndex.coerceIn(0, rows.size - 1))?.requestFocus() }
        }
    }

    LaunchedEffect(focusState.focusTrigger) {
        if (focusState.focusTrigger > 0) {
            focusState.isNavFocused = false
            repeat(5) {
                delay(80)
                // FIX: Respect the actual row index, do NOT force jump to the first landscape row!
                if (runCatching { rowFRs.getOrNull(focusState.currentRowIndex)?.requestFocus(); true }.getOrDefault(false)) return@repeat
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
                } else if (!focusState.isNavFocused && focusState.currentRowIndex > 0) {
                    focusState.currentRowIndex--
                    // FIX: Trigger the async retry loop so it waits for LazyColumn to scroll!
                    focusState.focusTrigger++
                    true
                } else false
            }
            Key.DirectionDown -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    focusState.focusTrigger++
                    true
                } else if (rows.isNotEmpty() && focusState.currentRowIndex < rows.size - 1) {
                    focusState.currentRowIndex++
                    focusState.focusTrigger++
                    true
                } else false
            }
            Key.Back, Key.Escape -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    focusState.focusTrigger++
                    true
                } else false
            }
            else -> false
        }
    }) {
        TwoRowNavBar(activeTab, firstNavFR, onSearch, onHomeTab, onMoviesTab, onSeriesTab, onFuzer, onWatchlist, onSettings, onIptv, { focusState.isNavFocused = true; focusState.currentRowIndex = 0 }, Modifier.fillMaxWidth().height(NAV_H).align(Alignment.TopStart).zIndex(10f))

        Box(Modifier.fillMaxWidth().height(maxPanelH).align(Alignment.BottomStart).graphicsLayer {
            alpha = animatedContentAlpha
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }) {
            RowsPanel(rows, focusState, rowFRs, maxPanelH, rowHeightFor, firstContentIndex, activeFilter, onStudioFilterClick, onLoadMore, onHeroUpdate, onMovieClick)
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

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.22f,
        animationSpec = tween(300),
        label = "rowAlpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(maxPanelH).graphicsLayer {
            alpha = animatedAlpha
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

    // FIX: Memory variable that tracks your exact position on this specific row
    var lastFocusedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0 && lastFocusedIndex == 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                LandscapeCard(
                    movie,
                    // FIX: Dynamically applies the FocusRequester only to your last focused item!
                    if (i == lastFocusedIndex && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    {
                        lastFocusedIndex = i
                        onFocus(movie)
                    }
                ) { onClick(movie.id) }
            }
        }
    }
}

@Composable
fun LandscapeStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0 && lastFocusedIndex == 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                LandscapeCard(
                    movie,
                    if (i == lastFocusedIndex && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    {
                        lastFocusedIndex = i
                        onFocus(movie)
                    }
                ) { onClick(movie.id) }
            }
        }
    }
}

@Composable
fun PortraitRow(title: String, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0 && lastFocusedIndex == 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                PosterCard(
                    movie,
                    if (i == lastFocusedIndex && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    PORT_W, PORT_H,
                    {
                        lastFocusedIndex = i
                        onFocus(movie)
                    }
                ) { onClick(movie.id) }
            }
        }
    }
}

@Composable
fun PortraitStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0 && lastFocusedIndex == 0) rowState.scrollToItem(0) }
    PagedRowLoadTrigger(rowState, onLoadMore)

    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
            modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer()
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie ->
                PosterCard(
                    movie,
                    if (i == lastFocusedIndex && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    PORT_W, PORT_H,
                    {
                        lastFocusedIndex = i
                        onFocus(movie)
                    }
                ) { onClick(movie.id) }
            }
        }
    }
}

@Composable
fun StudioRibbonRow(isActive: Boolean, cardFR: FocusRequester?, activeFilter: String?, onStudioFilterClick: (String?) -> Unit, onFocus: () -> Unit) {
    val brands = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // FIX: Grab the layout direction to know if we are in Hebrew (RTL) or English (LTR)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = tr("Browse by Studio", "סנן לפי אולפן"),
            color = WHITE.copy(if (isActive) 1f else 0.4f),
            fontSize = 14.sp,
            fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = Modifier.padding(start = 52.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 52.dp)
                // Ensures the Row properly protects Up/Down focus restoration
                .focusGroup()
                .focusRestorer()
                // FIX: Smart boundary locking that respects RTL languages!
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        if (isRtl) {
                            if (ev.key == Key.DirectionRight && lastFocusedIndex == 0) return@onPreviewKeyEvent true
                            if (ev.key == Key.DirectionLeft && lastFocusedIndex == brands.lastIndex) return@onPreviewKeyEvent true
                        } else {
                            if (ev.key == Key.DirectionLeft && lastFocusedIndex == 0) return@onPreviewKeyEvent true
                            if (ev.key == Key.DirectionRight && lastFocusedIndex == brands.lastIndex) return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            brands.forEachIndexed { i, brand ->
                val isActiveFilter = activeFilter == brand.name

                StudioLogoButton(
                    brand = brand,
                    isSelected = isActiveFilter,
                    modifier = if (i == lastFocusedIndex && cardFR != null) Modifier.focusRequester(cardFR) else Modifier,
                    onFocused = {
                        lastFocusedIndex = i
                        onFocus()
                    },
                    onClick = { onStudioFilterClick(brand.name) }
                )
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