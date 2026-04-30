@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

val BG        = Color(0xFF070707)
val RED       = Color(0xFFE50914)
val RED2      = Color(0xFFB20710)
val WHITE     = Color(0xFFFFFFFF)
val DIM       = Color(0xCCFFFFFF)
val DIM2      = Color(0x99FFFFFF)
val DIM3      = Color(0x33FFFFFF)
val GOLD      = Color(0xFFFFD700)
val CARD_BG   = Color(0xFF1C1C1C)

val LAND_W = 280.dp
val LAND_H = 158.dp
val PORT_W = 148.dp
val PORT_H = 222.dp
val ROW_LANDSCAPE_H = 194.dp
val ROW_PORTRAIT_H  = 260.dp

val placeholderBrush = Brush.verticalGradient(listOf(Color(0xFF2A2A2A), CARD_BG))


@Composable
fun RememberPagedRowLoad(rowState: androidx.compose.foundation.lazy.LazyListState, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(rowState) {
        snapshotFlow {
            val li = rowState.layoutInfo
            val total = li.totalItemsCount
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (total > 0 && last >= total - 6) total else -1
        }
            .distinctUntilChanged()
            .collectLatest { triggerTotal ->
                if (triggerTotal != -1) {
                    currentOnLoadMore()
                }
            }
    }
}

@Stable
class HomeFocusState(initialRow: Int = 0) {
    var isNavFocused    by mutableStateOf(false)
    var currentRowIndex by mutableIntStateOf(initialRow)
    var heroMovie       by mutableStateOf<Movie?>(null)
    var focusTrigger    by mutableIntStateOf(0)
    companion object {
        val Saver: Saver<HomeFocusState, Int> = Saver(save = { it.currentRowIndex }, restore = { HomeFocusState(it) })
    }
}