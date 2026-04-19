@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.*
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.R
import com.luminastreams.tv.domain.model.Movie

@Composable
fun RowLabel(title: String, isActive: Boolean, modifier: Modifier = Modifier) {
    val alphaAnim by animateFloatAsState(targetValue = if (isActive) 1f else 0.5f, animationSpec = tween(300), label = "alpha")
    Text(
        text = title, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = modifier.graphicsLayer { alpha = alphaAnim }
    )
}

@Composable
fun StudioBadge(brand: StudioBrand, isActive: Boolean, isLarge: Boolean = false) {
    val imageRes = when (brand) {
        StudioBrand.NETFLIX -> R.drawable.logo_netflix; StudioBrand.APPLE_TV -> R.drawable.logo_appletv
        StudioBrand.DISNEY -> R.drawable.logo_disney; StudioBrand.HBO -> R.drawable.logo_hbo
        StudioBrand.AMAZON -> R.drawable.logo_amazon; StudioBrand.PARAMOUNT -> R.drawable.logo_paramount
        StudioBrand.HULU -> R.drawable.logo_hulu
    }
    val alphaAnim by animateFloatAsState(targetValue = if (isActive) 1f else 0.5f, animationSpec = tween(300), label = "alpha")
    Box(modifier = Modifier.height(if (isLarge) 32.dp else 22.dp).width(if (isLarge) 80.dp else 50.dp).graphicsLayer { alpha = alphaAnim }, contentAlignment = Alignment.Center) {
        Image(painterResource(imageRes), brand.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun LandscapeCard(movie: Movie, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val url = movie.backdropUrl.ifBlank { movie.posterUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .memoryCacheKey("landscape_${movie.id}_$url") // Nuvio Logic למניעת צוואר בקבוק
            .diskCacheKey("landscape_${movie.id}_$url")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true) // פגז חומרה ללא פשרות באיכות
            .crossfade(false)
            .build()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Column(modifier = modifier.width(LAND_W)) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = Color.White),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(1.dp, Color(0x1AFFFFFF)), shape = RoundedCornerShape(16.dp)),
                focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(16.dp))
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None), // כבוי כליל! משאיר את כל הכוח ל-LazyColumn
            modifier = Modifier
                .fillMaxWidth()
                .height(LAND_H)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (url.isNotBlank()) AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().align(Alignment.TopCenter))
                else Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) { Text(movie.title, color = WHITE.copy(0.5f), fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(8.dp)) }

                if (movie.id.startsWith("http")) {
                    val isDubbed = movie.title.contains("מדובב")
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                val textColor = if (isFocused) Color.Black else WHITE
                val subTextColor = if (isFocused) Color.DarkGray else WHITE.copy(alpha = 0.7f)

                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6000000)).padding(16.dp)) {
                    Column {
                        Text(movie.title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = subTextColor, fontSize = 11.sp)
                    }
                }

                if (movie.rating > 0f) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xD9000000)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth((if (prog >= 0.95f) 1f else prog).coerceIn(0f, 1f)).fillMaxHeight().background(RED))
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(movie: Movie, modifier: Modifier = Modifier, cardW: Dp = PORT_W, cardH: Dp = PORT_H, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val url = movie.posterUrl.ifBlank { movie.backdropUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .memoryCacheKey("poster_${movie.id}_$url") // Nuvio Logic
            .diskCacheKey("poster_${movie.id}_$url")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = Color.White),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(1.dp, Color(0x1AFFFFFF)), shape = RoundedCornerShape(16.dp)),
                focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(16.dp))
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier
                .fillMaxWidth()
                .height(cardH)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (url.isNotBlank()) AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().align(Alignment.TopCenter))
                else Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) { Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp, maxLines = 3, modifier = Modifier.padding(8.dp)) }

                if (movie.id.startsWith("http")) {
                    val isDubbed = movie.title.contains("מדובב")
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                if (movie.rating > 0f) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xD9000000)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth((if (prog >= 0.95f) 1f else prog).coerceIn(0f, 1f)).fillMaxHeight().background(RED))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val textColor = if (isFocused) WHITE else WHITE.copy(alpha = 0.8f)
        val subTextColor = if (isFocused) WHITE.copy(alpha = 0.8f) else WHITE.copy(alpha = 0.4f)

        Text(movie.title, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(cardW).padding(horizontal = 4.dp))
        Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = subTextColor, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}