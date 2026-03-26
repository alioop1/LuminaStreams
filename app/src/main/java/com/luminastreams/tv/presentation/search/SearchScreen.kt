@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.search

import androidx.compose.ui.geometry.Offset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.*
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

// ── Apple TV / Premium Theme Palette ──────────────────────────────────────────
private val BackgroundDark = Color(0xFF0A0A0F)
private val GlassDark = Color(0x661A1A24)
private val GlassLight = Color(0x1AFFFFFF)
private val AppleFocusWhite = Color(0xFFF5F5F7)
private val AppleFocusScale = 1.1f
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0x99FFFFFF)
private val AccentFuzer = Color(0xFF34C97A)
private val AccentTmdb = Color(0xFF4F8EF7)

private val DISCOVERY_CATS = listOf(
    DiscoveryCategory("sort_seeds", "Popular", "🔥", listOf(Color(0xFFE03E3E), Color(0xFFB20710))),
    DiscoveryCategory("q_4k", "4K Ultra", "✨", listOf(Color(0xFF6C63FF), Color(0xFF3B32B8))),
    DiscoveryCategory("dubbed", "Dubbed", "🇮🇱", listOf(Color(0xFF4F8EF7), Color(0xFF1955B8))),
    DiscoveryCategory("g_action", "Action", "💥", listOf(Color(0xFFFF6B35), Color(0xFFC73E0E))),
    DiscoveryCategory("g_comedy", "Comedy", "😂", listOf(Color(0xFFFFB830), Color(0xFFCC890A))),
    DiscoveryCategory("g_drama", "Drama", "🎭", listOf(Color(0xFF9B59B6), Color(0xFF6C2B87))),
    DiscoveryCategory("g_scifi", "Sci-Fi", "🚀", listOf(Color(0xFF00BCD4), Color(0xFF008394))),
    DiscoveryCategory("r8", "IMDb 8+", "🏆", listOf(Color(0xFFFF9800), Color(0xFFB86E00)))
)

private data class DiscoveryCategory(
    val id: String, val label: String, val emoji: String, val gradient: List<Color>
)

@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit
) {
    val searchBarFR = remember { FocusRequester() }
    val firstDiscoveryFR = remember { FocusRequester() }
    val firstResultFR = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    BackHandler {
        if (state.query.isNotBlank()) onIntent(SearchIntent.UpdateQuery(""))
        else onNavigateBack()
    }

    LaunchedEffect(Unit) { delay(100); runCatching { searchBarFR.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(BackgroundDark)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top Search Bar Section
            PremiumSearchBar(
                state = state,
                searchBarFR = searchBarFR,
                nextFR = if (state.activeResults.isEmpty() && state.query.isBlank()) firstDiscoveryFR else firstResultFR,
                onBack = onNavigateBack,
                onIntent = onIntent
            )

            // Content Area
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> ShimmerPremiumGrid()
                    state.activeResults.isEmpty() && state.query.isBlank() -> {
                        // Stremio/Apple style Discovery Board
                        DiscoveryBoard(
                            chips = state.visibleFilterChips,
                            firstDiscFR = firstDiscoveryFR,
                            onChipTap = { onIntent(SearchIntent.ApplyChip(it)) }
                        )
                    }
                    state.activeResults.isEmpty() -> EmptyStatePremium(state.query)
                    else -> PremiumResultGrid(
                        results = state.activeResults,
                        isFuzer = state.source == SearchSource.FUZER,
                        gridState = gridState,
                        firstResultFR = firstResultFR,
                        onResultClick = onResultClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumSearchBar(
    state: SearchState,
    searchBarFR: FocusRequester,
    nextFR: FocusRequester,
    onBack: () -> Unit,
    onIntent: (SearchIntent) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (isFocused) 80.dp else 70.dp, label = "height")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 58.dp, vertical = 24.dp)
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Rounded Back Button
        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = GlassDark,
                focusedContainerColor = AppleFocusWhite,
                contentColor = TextPrimary,
                focusedContentColor = Color.Black
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", Modifier.size(24.dp))
            }
        }

        // Apple TV Style Input Field
        Surface(
            onClick = { /* Handle via Focus */ },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(32.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = GlassDark,
                focusedContainerColor = AppleFocusWhite,
                contentColor = TextPrimary,
                focusedContentColor = Color.Black
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(searchBarFR)
                .onFocusChanged { isFocused = it.isFocused }
                .focusProperties { down = nextFR }
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(28.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = { onIntent(SearchIntent.UpdateQuery(it)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = if (isFocused) Color.Black else TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text("חיפוש סרטים, סדרות או שחקנים...", color = if (isFocused) Color.DarkGray else TextSecondary, fontSize = 22.sp)
                        }
                        inner()
                    }
                )
            }
        }

        // Fuzer / All Toggle (Instead of full tab row, sleek toggle)
        val isFuzer = state.source == SearchSource.FUZER
        Surface(
            onClick = { onIntent(SearchIntent.SelectSource(if (isFuzer) SearchSource.ALL else SearchSource.FUZER)) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isFuzer) AccentFuzer.copy(0.2f) else GlassDark,
                focusedContainerColor = if (isFuzer) AccentFuzer else AppleFocusWhite,
                contentColor = if (isFuzer) AccentFuzer else TextPrimary,
                focusedContentColor = if (isFuzer) Color.White else Color.Black
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.height(56.dp).width(140.dp)
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(if (isFuzer) Icons.Default.CloudQueue else Icons.Default.Language, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isFuzer) "Fuzer" else "Global", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DiscoveryBoard(
    chips: List<FilterChip>,
    firstDiscFR: FocusRequester,
    onChipTap: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text("גלה תוכן חדש", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 30.dp, end = 58.dp)
        ) {
            itemsIndexed(DISCOVERY_CATS) { idx, cat ->
                val isActive = chips.any { it.id == cat.id && it.isActive }
                DiscoveryPremiumCard(
                    cat = cat,
                    isActive = isActive,
                    modifier = if (idx == 0) Modifier.focusRequester(firstDiscFR) else Modifier,
                    onClick = { onChipTap(cat.id) }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryPremiumCard(
    cat: DiscoveryCategory,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = AppleFocusScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        modifier = modifier
            .width(220.dp)
            .height(140.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .shadow(if (isFocused) 20.dp else 0.dp, RoundedCornerShape(28.dp), spotColor = cat.gradient.first())
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(cat.gradient))
                .border(
                    width = if (isActive || isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else if (isActive) Color.White.copy(0.7f) else Color.Transparent,
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(cat.emoji, fontSize = 36.sp)
                Text(
                    cat.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PremiumResultGrid(
    results: List<SearchResult>,
    isFuzer: Boolean,
    gridState: LazyGridState,
    firstResultFR: FocusRequester,
    onResultClick: (SearchResult) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 180.dp),
        contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 10.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(results, key = { _, r -> r.id }) { idx, result ->
            MediaPremiumCard(
                result = result,
                isFuzer = isFuzer,
                modifier = if (idx == 0) Modifier.focusRequester(firstResultFR) else Modifier,
                onClick = { onResultClick(result) }
            )
        }
    }
}

@Composable
private fun MediaPremiumCard(
    result: SearchResult,
    isFuzer: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val accent = if (isFuzer) AccentFuzer else AccentTmdb

    Column(modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = AppleFocusScale),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(accent.copy(0.5f), elevation = 20.dp)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(true).build(),
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(GlassDark), Alignment.Center) {
                        Text("🎬", fontSize = 40.sp)
                    }
                }

                // Badges
                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (result.qualityTag.isNotBlank()) {
                        BadgePremium(result.qualityTag, AccentFuzer)
                    }
                    if (result.rating > 0) {
                        BadgePremium("★ ${"%.1f".format(result.rating)}", Color(0xFFFFB830))
                    }
                }
            }
        }

        // Title text outside the card (Apple TV style)
        Text(
            text = result.title,
            color = if (isFocused) Color.White else TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BadgePremium(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.7f))
            .border(1.dp, color.copy(0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyStatePremium(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.SearchOff, null, Modifier.size(80.dp), tint = TextSecondary)
            Text("לא נמצאו תוצאות עבור \"$query\"", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("נסה לחפש מילות מפתח אחרות", color = TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ShimmerPremiumGrid() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "sp")
    val brush = Brush.linearGradient(
        listOf(GlassDark, GlassLight, GlassDark),
        start = androidx.compose.ui.geometry.Offset(p * 2000f - 1000f, 0f),
        end = androidx.compose.ui.geometry.Offset(p * 2000f, 600f)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(58.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(12) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(CircleShape).background(brush))
            }
        }
    }
}