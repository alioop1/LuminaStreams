package com.luminastreams.tv.presentation.home

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie

// ─────────────────────────────────────────────────────────────────────────────
// StudioSection — top-level entry point
//
// Usage in HomeScreen:
//
//   val ribbonFocus = remember { FocusRequester() }
//   StudioSection(
//       currentStudio        = state.currentStudioId,
//       catalog              = state.currentStudioCatalog,
//       isLoading            = state.studioCatalogLoading,
//       onStudioSelected     = viewModel::selectStudio,
//       onMovieClick         = { movie -> /* navigate */ },
//       ribbonFocusRequester = ribbonFocus
//   )
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudioSection(
    currentStudio: StudioBrand,
    catalog: StudioCatalog?,
    isLoading: Boolean,
    onStudioSelected: (StudioBrand) -> Unit,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    ribbonFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CONSTRAINT: ribbon is a permanent node — never removed from composition
        StudioRibbon(
            currentStudio        = currentStudio,
            onStudioSelected     = onStudioSelected,
            ribbonFocusRequester = ribbonFocusRequester
        )
        StudioContentArea(
            catalog              = catalog,
            isLoading            = isLoading,
            onMovieClick         = onMovieClick,
            ribbonFocusRequester = ribbonFocusRequester
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Persistent Studio Ribbon
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StudioRibbon(
    currentStudio: StudioBrand,
    onStudioSelected: (StudioBrand) -> Unit,
    modifier: Modifier = Modifier,
    ribbonFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val layoutDir = LocalLayoutDirection.current

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(ribbonFocusRequester),
        // Arrangement.Start + reverseLayout handles RTL flip automatically
        // All padding uses start/end — never hardcoded left/right
        horizontalArrangement = Arrangement.spacedBy(12.dp, Arrangement.Start),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
        reverseLayout  = layoutDir == LayoutDirection.Rtl
    ) {
        items(StudioBrand.entries, key = { it.name }) { brand ->
            StudioChip(
                brand      = brand,
                isSelected = brand == currentStudio,
                onClick    = { onStudioSelected(brand) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StudioChip(
    brand: StudioBrand,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (isFocused || isSelected) 1.08f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label         = "chipScale"
    )

    Surface(
        onClick = onClick,
        shape   = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp)),
        colors  = ClickableSurfaceDefaults.colors(
            containerColor        = if (isSelected) Color(0xFFE50914) else Color(0xFF1F1F1F),
            focusedContainerColor = if (isSelected) Color(0xFFE50914) else Color(0xFF2A2A2A),
            contentColor          = Color.White,
            focusedContentColor   = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Text(
            text       = brand.displayName,
            fontSize   = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color      = Color.White,
            modifier   = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Studio Content Area — animated transition on catalog change
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StudioContentArea(
    catalog: StudioCatalog?,
    isLoading: Boolean,
    onMovieClick: (Movie) -> Unit,
    ribbonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val layoutDir = LocalLayoutDirection.current

    AnimatedContent(
        targetState    = catalog,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label          = "studioContentTransition",
        modifier       = modifier.fillMaxWidth()
    ) { activeCatalog ->
        if (isLoading || activeCatalog == null) {
            StudioContentSkeleton()
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding      = PaddingValues(bottom = 40.dp)
            ) {
                // ── Hero: New Releases — landscape 16:9 ─────────────────────
                if (activeCatalog.newReleases.isNotEmpty()) {
                    item(key = "hero_header") {
                        SectionHeader("New Releases", layoutDir)
                    }
                    item(key = "hero_row") {
                        LandscapeCardRow(
                            movies        = activeCatalog.newReleases,
                            onMovieClick  = onMovieClick,
                            layoutDir     = layoutDir,
                            onExitUpFocus = { ribbonFocusRequester.requestFocus() }
                        )
                    }
                }

                // ── Genre category rows — portrait 2:3 ──────────────────────
                activeCatalog.categoryRows.forEachIndexed { index, row ->
                    if (row.movies.isNotEmpty()) {
                        item(key = "cat_header_$index") {
                            SectionHeader(row.genreLabel, layoutDir)
                        }
                        item(key = "cat_row_$index") {
                            PortraitCardRow(
                                movies        = row.movies,
                                onMovieClick  = onMovieClick,
                                layoutDir     = layoutDir,
                                // Only the first category row sends focus back up to ribbon
                                onExitUpFocus = if (index == 0) {
                                    { ribbonFocusRequester.requestFocus() }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header — padding(start/end) only, never hardcoded left/right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, layoutDir: LayoutDirection) {
    Text(
        text       = title,
        color      = Color.White,
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Landscape (16:9) card row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeCardRow(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    layoutDir: LayoutDirection,
    onExitUpFocus: (() -> Unit)?
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                if (onExitUpFocus != null &&
                    e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    e.nativeKeyEvent.action  == KeyEvent.ACTION_DOWN
                ) { onExitUpFocus(); true } else false
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp, Arrangement.Start),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
        reverseLayout  = layoutDir == LayoutDirection.Rtl
    ) {
        items(movies, key = { "land_${it.id}" }) { movie ->
            LandscapePosterCard(movie = movie, onClick = { onMovieClick(movie) })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Portrait (2:3) card row — reuses existing PosterCard composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortraitCardRow(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    layoutDir: LayoutDirection,
    onExitUpFocus: (() -> Unit)?
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                if (onExitUpFocus != null &&
                    e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    e.nativeKeyEvent.action  == KeyEvent.ACTION_DOWN
                ) { onExitUpFocus(); true } else false
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Arrangement.Start),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
        reverseLayout  = layoutDir == LayoutDirection.Rtl
    ) {
        items(movies, key = { "port_${it.id}" }) { movie ->
            PosterCard(movie = movie, onClick = { onMovieClick(movie) })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Landscape (16:9) poster card
// ─────────────────────────────────────────────────────────────────────────────

private val LandscapeShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LandscapePosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val overlayAlpha by animateFloatAsState(
        targetValue   = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label         = "landscapeOverlay"
    )

    Surface(
        onClick = onClick,
        shape   = ClickableSurfaceDefaults.shape(LandscapeShape),
        colors  = ClickableSurfaceDefaults.colors(
            containerColor        = Color(0xFF1A1A1A),
            focusedContainerColor = Color(0xFF1A1A1A),
            contentColor          = Color.White,
            focusedContentColor   = Color.White
        ),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 20.dp)
        ),
        modifier = modifier
            .width(280.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(LandscapeShape)) {
            // Prefer backdropUrl for landscape cards; fallback to posterUrl
            val imageUrl = movie.backdropUrl.ifEmpty { movie.posterUrl }
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(false)
                        .allowHardware(true)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)))
            }

            // Title overlay — GPU-only via graphicsLayer, no recomposition
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overlayAlpha }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // start/end padding only — RTL safe
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    Text(
                        text       = movie.title,
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (movie.year > 0) {
                        Text(
                            text     = "${movie.year}  •  ${movie.genre}",
                            color    = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Rating badge
            if (movie.rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text       = "★ ${{"%.1f".format(movie.rating)}}",
                        color      = Color(0xFFFFC107),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton loader — shimmer while catalog loads
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudioContentSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "shimmerAlpha"
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Landscape row skeleton
        LazyRow(
            contentPadding        = PaddingValues(start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(5) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
        // Portrait rows skeleton
        repeat(2) {
            LazyRow(
                contentPadding        = PaddingValues(start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(6) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
