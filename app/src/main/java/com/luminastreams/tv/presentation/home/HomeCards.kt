@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.R
import com.luminastreams.tv.domain.model.Movie
import kotlin.math.roundToInt

private fun quantizeProgress(progress: Float): Float {
    if (progress >= 0.95f) return 1f
    return (progress * 100f).roundToInt().coerceIn(1, 99) / 100f
}

@Composable
fun StudioBadge(brand: StudioBrand, isActive: Boolean, isLarge: Boolean = false) {
    val imageRes = when (brand) {
        StudioBrand.NETFLIX -> R.drawable.logo_netflix; StudioBrand.APPLE_TV -> R.drawable.logo_appletv
        StudioBrand.DISNEY -> R.drawable.logo_disney; StudioBrand.HBO -> R.drawable.logo_hbo
        StudioBrand.AMAZON -> R.drawable.logo_amazon; StudioBrand.PARAMOUNT -> R.drawable.logo_paramount
        StudioBrand.HULU -> R.drawable.logo_hulu
    }
    Box(
        modifier = Modifier
            .height(if (isLarge) 32.dp else 22.dp)
            .width(if (isLarge) 80.dp else 50.dp)
            .alpha(if (isActive) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = brand.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LandscapeCard(movie: Movie, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val url = movie.backdropUrl.ifBlank { movie.posterUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .diskCacheKey(url)
            .size(600)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    Column(modifier = modifier.width(LAND_W)) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(0.dp, Color.Transparent), shape = RoundedCornerShape(10.dp)),
                focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier
                .fillMaxWidth()
                .height(LAND_H)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .onFocusChanged { if (it.isFocused) onFocused() }
        ) {
            if (url.isNotBlank()) {
                AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) {
                    Text(movie.title, color = WHITE.copy(0.5f), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(8.dp))
                }
            }

            if (movie.id.startsWith("http")) {
                val isDubbed = movie.title.contains("מדובב")
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = movie.title,
                    color = WHITE,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = DIM2, fontSize = 11.sp)
            }
            if (movie.rating > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth(quantizeProgress(prog)).fillMaxHeight().background(RED))
                }
            }
        }
    }
}

@Composable
fun PosterCard(movie: Movie, modifier: Modifier = Modifier, cardW: Dp = PORT_W, cardH: Dp = PORT_H, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val url = movie.posterUrl.ifBlank { movie.backdropUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .diskCacheKey(url)
            .size(400)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    Column(
        modifier = modifier.width(cardW),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(0.dp, Color.Transparent), shape = RoundedCornerShape(10.dp)),
                focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier
                .fillMaxWidth()
                .height(cardH)
                .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
                .onFocusChanged { if (it.isFocused) onFocused() }
        ) {
            if (url.isNotBlank()) {
                AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) {
                    Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp, maxLines = 3, modifier = Modifier.padding(8.dp))
                }
            }

            if (movie.id.startsWith("http")) {
                val isDubbed = movie.title.contains("מדובב")
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            if (movie.rating > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth(quantizeProgress(prog)).fillMaxHeight().background(RED))
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        Text(
            text = movie.title,
            color = WHITE,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardW).graphicsLayer { alpha = if (isFocused) 1f else 0.7f }
        )
        Text(
            text = if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"),
            color = WHITE,
            fontSize = 10.sp,
            modifier = Modifier.graphicsLayer { alpha = if (isFocused) 0.6f else 0.4f }
        )
    }
}

@Composable
fun StudioLogoButton(brand: StudioBrand, isSelected: Boolean, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(200),
        label = "scale"
    )

    // Brand-specific gradient backgrounds — dark tones that CONTRAST with logo colors
    val gradientBrush = when (brand) {
        StudioBrand.NETFLIX    -> Brush.linearGradient(listOf(Color(0xFF141414), Color(0xFF1C1C1C)))  // Dark charcoal — red logo pops
        StudioBrand.DISNEY     -> Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF142240)))  // Deep navy — white/blue Disney arc pops
        StudioBrand.APPLE_TV   -> Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF2A2A40)))  // Dark slate — black Apple logo visible
        StudioBrand.HBO        -> Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF2D1B4E)))  // Deep purple — HBO brand identity
        StudioBrand.AMAZON     -> Brush.linearGradient(listOf(Color(0xFF0A1E2C), Color(0xFF0F2B3D)))  // Dark navy-teal — cyan logo pops
        StudioBrand.PARAMOUNT  -> Brush.linearGradient(listOf(Color(0xFF0A0F1E), Color(0xFF141E35)))  // Deep dark blue — blue logo pops
        StudioBrand.HULU       -> Brush.linearGradient(listOf(Color(0xFF0A1A14), Color(0xFF0F2A1E)))  // Very dark green — green logo pops
    }

    // TMDB Network logo paths (transparent PNGs — pulled from /3/network/{id} API)
    // Using 'original' quality for maximum sharpness on TV screens
    val logoUrl = "https://image.tmdb.org/t/p/original" + when (brand) {
        StudioBrand.NETFLIX    -> "/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"
        StudioBrand.DISNEY     -> "/1edZOYAfoyZyZ3rklNSiUpXX30Q.png"
        StudioBrand.APPLE_TV   -> "/bngHRFi794mnMq34gfVcm9nDxN1.png"
        StudioBrand.HBO        -> "/tuomPhY2UtuPTqqFnKMVHvSb724.png"
        StudioBrand.AMAZON     -> "/w7HfLNm9CWwRmAMU58udl2L7We7.png"
        StudioBrand.PARAMOUNT  -> "/fi83B1oztoS47xxcemFdPMhIzK.png"
        StudioBrand.HULU       -> "/pqUTCleNUiTLAVlelGxUgWn1ELh.png"
    }

    val logoRequest = remember(logoUrl) {
        ImageRequest.Builder(ctx)
            .data(logoUrl)
            .diskCacheKey(logoUrl)
            .size(400)  // Downsample to 400px — sharp enough for 180dp card, saves memory
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(false) // Disabled for PNG transparency on all GPU tiers
            .crossfade(false)
            .build()
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) WHITE.copy(0.6f) else Color.Transparent),
                shape = RoundedCornerShape(16.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(3.dp, WHITE),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(Color.White.copy(0.15f), 12.dp)),
        modifier = modifier
            .width(180.dp)
            .height(90.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradientBrush, RoundedCornerShape(16.dp)),
            Alignment.Center
        ) {
            AsyncImage(
                model = logoRequest,
                contentDescription = brand.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.55f)
            )
        }
    }
}