package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie

private val CardShape = RoundedCornerShape(12.dp)

/**
 * A portrait poster card used in horizontal carousels throughout the app.
 * Shown in the Collection and Starring sections of DetailsScreen, and in WatchlistScreen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val elevation by animateFloatAsState(
        targetValue = if (isFocused) 20f else 0f,
        animationSpec = tween(200),
        label = "posterElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = tween(200),
        label = "posterScale"
    )

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = Color(0xFF1A1A1A),
            focusedContainerColor = Color(0xFF1A1A1A),
            contentColor          = Color.White,
            focusedContentColor   = Color.White
        ),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        border = ClickableSurfaceDefaults.border(
            border        = Border.None,
            focusedBorder = Border(
                androidx.compose.foundation.BorderStroke(2.5.dp, Color.White)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.Black.copy(alpha = 0.7f),
                elevation      = 24.dp
            )
        ),
        modifier = modifier
            .width(130.dp)
            .aspectRatio(2f / 3f)
            .zIndex(if (isFocused) 10f else 0f)
            .shadow(elevation.dp, CardShape)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
        ) {
            // Poster image
            if (movie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.posterUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = movie.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                // Fallback gradient when no poster is available
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2C2C2C), Color(0xFF111111))
                            )
                        )
                )
            }

            // Bottom gradient + title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )

            // Title text (only visible when no poster, or on focus for accessibility)
            if (movie.posterUrl.isEmpty() || isFocused) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    androidx.compose.material3.Text(
                        text       = movie.title,
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                    if (movie.year > 0) {
                        androidx.compose.material3.Text(
                            text     = movie.year.toString(),
                            color    = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Rating badge (top-right)
            if (movie.rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    androidx.compose.material3.Text(
                        text       = "★ ${"%.1f".format(movie.rating)}",
                        color      = Color(0xFFFFC107),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
