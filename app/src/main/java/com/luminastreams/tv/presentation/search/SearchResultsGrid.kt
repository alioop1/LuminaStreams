package com.luminastreams.tv.presentation.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.luminastreams.tv.domain.model.SearchResult

@Composable
fun SearchResultsGrid(
    results: List<SearchResult>,
    isFuzer: Boolean,
    firstResultFR: FocusRequester,
    onFocusCard: (SearchResult) -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isFuzer) 156.dp else 140.dp),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            MediaSearchCard(
                result = result,
                isFuzer = isFuzer,
                modifier = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onFocus = { onFocusCard(result) },
                onClick = { onResultClick(result) }
            )
        }
    }
}

@Composable
fun ShimmerGrid() {
    val inf = rememberInfiniteTransition(label = "sh")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")
    val shimmer = Brush.linearGradient(listOf(Color(0xFF0F0F14), Color(0xFF1A1A24), Color(0xFF0F0F14)), start = Offset(p * 1600f - 800f, 0f), end = Offset(p * 1600f, 400f))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()
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

@Composable
fun EmptyState(query: String, source: SearchSource) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Text(text = when (source) { SearchSource.FUZER -> "💾"; SearchSource.MOVIES -> "🎬"; SearchSource.SERIES -> "📺"; else -> if (query.isNotBlank()) "🔍" else "🌟" }, fontSize = 56.sp)
            Text(when { query.isNotBlank() -> "No results for \u201c$query\u201d"; source == SearchSource.FUZER -> "Fuzer Torrent Search"; else -> "Discover Something Great" }, color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(when { query.isNotBlank() -> "Try a different keyword or adjust filters"; source == SearchSource.FUZER -> "Type to search Hebrew content on Fuzer"; else -> "Search above or browse by genre with filters" }, color = DIM.copy(0.5f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
        }
    }
}

@Composable
fun FuzerError(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(40.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(ACCENT_PINK.copy(0.08f)).border(1.dp, ACCENT_PINK.copy(0.3f), CircleShape), Alignment.Center) { Icon(Icons.Default.CloudOff, null, Modifier.size(32.dp), tint = ACCENT_PINK.copy(0.7f)) }
            Text("Fuzer Unavailable", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = DIM.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
        }
    }
}