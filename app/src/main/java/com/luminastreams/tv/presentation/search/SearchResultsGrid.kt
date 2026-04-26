@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.luminastreams.tv.presentation.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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

// ── Year range definition for grouped display ────────────────────────────
private data class YearBucket(val label: String, val emoji: String, val range: IntRange, val isSpecial: Boolean = false)

private val YEAR_BUCKETS = listOf(
    YearBucket("הטובים ביותר בכל הזמנים", "⭐", 1900..2030, isSpecial = true),
    YearBucket("2027 – 2030", "🔮", 2027..2030),
    YearBucket("2020 – 2026", "🔥", 2020..2026),
    YearBucket("2010 – 2019", "🎬", 2010..2019),
    YearBucket("2001 – 2009", "📀", 2001..2009),
    YearBucket("1991 – 2000", "📼", 1991..2000),
    YearBucket("1900 – 1990", "🎞️", 1900..1990),
)

@Composable
fun YearGroupedResults(
    results: List<SearchResult>,
    firstResultFR: FocusRequester,
    navBarFR: FocusRequester,
    onFocusCard: (SearchResult) -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val grouped = remember(results) {
        YEAR_BUCKETS.mapNotNull { bucket ->
            val items = if (bucket.isSpecial) {
                results.sortedByDescending { it.rating }.take(30)
            } else {
                results
                    .filter { r ->
                        val y = r.releaseYear.toIntOrNull() ?: return@filter false
                        y in bucket.range
                    }
                    .sortedByDescending { it.rating }
            }
            if (items.isNotEmpty()) bucket to items else null
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 48.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        grouped.forEachIndexed { groupIdx, (bucket, items) ->
            // ── BIG Section Header ──────────────────────────────
            item(key = "header_$groupIdx") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (groupIdx == 0) 8.dp else 24.dp)
                        .padding(horizontal = 48.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Accent bar
                            Box(
                                Modifier
                                    .width(5.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (bucket.isSpecial) GOLD else RED)
                            )
                            // Emoji
                            Text(bucket.emoji, fontSize = 24.sp)
                            // Title
                            Text(
                                bucket.label,
                                color = if (bucket.isSpecial) GOLD else Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            // Count badge
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (bucket.isSpecial) GOLD.copy(0.15f)
                                        else Color.White.copy(0.08f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${items.size}",
                                    color = if (bucket.isSpecial) GOLD else Color.White.copy(0.5f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Gradient line
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            if (bucket.isSpecial) GOLD.copy(0.5f) else RED.copy(0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // ── Horizontal poster row ──────────────────────────
            item(key = "row_$groupIdx") {
                val rowFirstFR = remember { FocusRequester() }

                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                        .focusRestorer { rowFirstFR }
                ) {
                    itemsIndexed(items, key = { _, r -> "${groupIdx}_${r.id}" }) { idx, result ->
                        MediaSearchCard(
                            result = result,
                            isFuzer = false,
                            modifier = Modifier.width(150.dp)
                                .then(
                                    if (idx == 0) Modifier.focusRequester(
                                        if (groupIdx == 0) firstResultFR else rowFirstFR
                                    ) else Modifier
                                )
                                .then(
                                    if (groupIdx == 0) Modifier.focusProperties { up = navBarFR }
                                    else Modifier
                                ),
                            onFocus = { onFocusCard(result) },
                            onClick = { onResultClick(result) }
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun SearchResultsGrid(
    results: List<SearchResult>,
    isFuzer: Boolean,
    firstResultFR: FocusRequester,
    onFocusCard: (SearchResult) -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (isFuzer) 156.dp else 150.dp),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(18) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(shimmer))
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