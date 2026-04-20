@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:Suppress("SpellCheckingInspection")

package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay

@Composable
fun ContentLayer(
    rows: List<RowDef>, contentAlpha: Float, focusState: HomeFocusState, activeTab: String, activeFilter: String?,
    panelH: Dp, rowHeightFor: (Int) -> Dp, firstContentIndex: Int,
    onMovieClick: (String) -> Unit, onHeroUpdate: (Movie) -> Unit,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit
) {
    val firstNavFR = remember { FocusRequester() }
    val firstCardFRs = remember { List(30) { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }
    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH
    val animatedContentAlpha by animateFloatAsState(targetValue = contentAlpha, animationSpec = if (isHighTier) tween(250, easing = LinearEasing) else snap(), label = "contentAlpha")

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
                if (focusState.isNavFocused) {
                    true
                } else if (focusState.currentRowIndex <= 0) {
                    focusState.isNavFocused = true
                    runCatching { firstNavFR.requestFocus() }
                    true
                } else {
                    focusState.currentRowIndex--
                    runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                    true
                }
            }
            Key.DirectionDown -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    focusState.currentRowIndex = 0
                    runCatching { firstCardFRs.firstOrNull()?.requestFocus() }
                    true
                } else if (rows.isNotEmpty() && focusState.currentRowIndex < rows.size - 1) {
                    focusState.currentRowIndex++
                    runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                    true
                } else {
                    true
                }
            }
            Key.Back, Key.Escape -> {
                if (focusState.isNavFocused) {
                    focusState.isNavFocused = false
                    runCatching { firstCardFRs.getOrNull(focusState.currentRowIndex)?.requestFocus() }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }) {
        TwoRowNavBar(activeTab, firstNavFR, onSearch, onHomeTab, onMoviesTab, onSeriesTab, onFuzer, onWatchlist, onSettings, onIptv, { focusState.isNavFocused = true; focusState.currentRowIndex = 0 }, Modifier.fillMaxWidth().height(NAV_H).align(Alignment.TopStart).zIndex(10f))
        Box(Modifier.fillMaxWidth().height(panelH).align(Alignment.BottomStart).graphicsLayer { alpha = animatedContentAlpha }) {
            RowsPanel(rows, focusState, firstCardFRs, panelH, rowHeightFor, firstContentIndex, activeFilter, onStudioFilterClick, onLoadMore, onHeroUpdate, onMovieClick)
        }
    }
}

@Composable
fun RowsPanel(
    rows: List<RowDef>, focusState: HomeFocusState, rowFRs: List<FocusRequester>, panelH: Dp,
    rowHeightFor: (Int) -> Dp, firstContentIndex: Int, activeFilter: String?,
    onStudioFilterClick: (String?) -> Unit, onLoadMore: (String) -> Unit,
    onItemFocus: (Movie) -> Unit, onItemClick: (String) -> Unit
) {
    if (rows.isEmpty()) return

    // FIX: Using LazyColumn here instead of a Box to virtualize the rows!
    val verticalScrollState = rememberLazyListState()

    LaunchedEffect(focusState.currentRowIndex, rows.size) {
        val curRow = focusState.currentRowIndex.coerceIn(0, rows.size - 1)
        verticalScrollState.animateScrollToItem(curRow)
    }

    LazyColumn(
        state = verticalScrollState,
        modifier = Modifier.fillMaxWidth().height(panelH),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        itemsIndexed(rows, key = { _, item -> item.id }) { index, rowDef ->
            val rh = rowHeightFor(index)
            val isLand = (index == firstContentIndex)

            IsolatedRowWrapper(
                index = index,
                rowDef = rowDef,
                rh = rh,
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
    index: Int, rowDef: RowDef, rh: Dp, isLand: Boolean,
    focusState: HomeFocusState, cardFR: FocusRequester?, activeFilter: String?,
    onFocus: (Movie) -> Unit, onItemClick: (String) -> Unit,
    onLoadMore: (String) -> Unit, onStudioFilterClick: (String?) -> Unit
) {
    val curRow = focusState.currentRowIndex
    val isActive = !focusState.isNavFocused && curRow == index

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.22f,
        animationSpec = tween(300),
        label = "rowAlpha"
    )

    Box(Modifier.fillMaxWidth().height(rh).graphicsLayer { alpha = animatedAlpha }) {
        if (rowDef is RowDef.StudioRibbon) {
            StudioRibbonRow(isActive, cardFR, activeFilter, onStudioFilterClick)
        } else if (isLand) {
            when (rowDef) {
                is RowDef.Regular -> LandscapeRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick) { onLoadMore(rowDef.id) }
                is RowDef.Studio -> LandscapeStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick) { onLoadMore(rowDef.id) }
                else -> {}
            }
        } else {
            when (rowDef) {
                is RowDef.Regular -> PortraitRow(rowDef.title, rowDef.movies, isActive, cardFR, onFocus, onItemClick) { onLoadMore(rowDef.id) }
                is RowDef.Studio -> PortraitStudioRow(rowDef.brand, rowDef.movies, isActive, cardFR, onFocus, onItemClick) { onLoadMore(rowDef.id) }
                else -> {}
            }
        }
    }
}

@Composable
fun LandscapeRow(title: String, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    RememberPagedRowLoad(rowState, onLoadMore)
    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> LandscapeCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun LandscapeStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    RememberPagedRowLoad(rowState, onLoadMore)
    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> LandscapeCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun PortraitRow(title: String, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    RememberPagedRowLoad(rowState, onLoadMore)
    Column {
        RowLabel(title, isActive, Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> PosterCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, PORT_W, PORT_H, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun PortraitStudioRow(brand: StudioBrand, movies: List<Movie>, isActive: Boolean, cardFR: FocusRequester?, onFocus: (Movie) -> Unit, onClick: (String) -> Unit, onLoadMore: () -> Unit) {
    if (movies.isEmpty()) return
    val rowState = rememberLazyListState()
    LaunchedEffect(isActive) { if (!isActive && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0) }
    RememberPagedRowLoad(rowState, onLoadMore)
    Column {
        Row(Modifier.padding(start = 52.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioBadge(brand, isActive)
            Text(studioLabel(brand), color = WHITE.copy(if (isActive) 0.9f else 0.35f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(movies, key = { _, m -> m.id }) { i, movie -> PosterCard(movie, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier, PORT_W, PORT_H, { onFocus(movie) }) { onClick(movie.id) } }
        }
    }
}

@Composable
fun StudioRibbonRow(isActive: Boolean, cardFR: FocusRequester?, activeFilter: String?, onStudioFilterClick: (String?) -> Unit) {
    val brands = listOf(StudioBrand.HBO, StudioBrand.NETFLIX, StudioBrand.AMAZON, StudioBrand.DISNEY, StudioBrand.APPLE_TV, StudioBrand.PARAMOUNT, StudioBrand.HULU)
    val rowState = rememberLazyListState()
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(tr("Browse by Studio", "סנן לפי אולפן"), color = WHITE.copy(if (isActive) 1f else 0.4f), fontSize = 14.sp, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, modifier = Modifier.padding(start = 52.dp, bottom = 12.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 52.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(brands, key = { _, b -> b.name }) { i, brand -> StudioLogoButton(brand, activeFilter == brand.name, if (i == 0 && cardFR != null) Modifier.focusRequester(cardFR) else Modifier) { onStudioFilterClick(brand.name) } }
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
fun StudioLogoButton(brand: StudioBrand, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (DeviceProfile.tier == DeviceProfile.Tier.HIGH) 1.08f else 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) WHITE.copy(0.15f) else CARD_BG,
            focusedContainerColor = WHITE
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.5.dp, if (isSelected) WHITE else Color.Transparent), shape = RoundedCornerShape(12.dp)),
            focusedBorder = Border(border = BorderStroke(2.5.dp, WHITE), shape = RoundedCornerShape(12.dp))
        ),
        modifier = modifier.width(130.dp).height(65.dp)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { StudioBadge(brand = brand, isActive = true, isLarge = true) }
    }
}