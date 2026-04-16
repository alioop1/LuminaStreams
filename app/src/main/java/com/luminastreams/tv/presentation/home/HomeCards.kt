@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie

@Composable
fun RowLabel(title: String, isActive: Boolean, modifier: Modifier = Modifier) {
    Text(title, color = WHITE.copy(alpha = if (isActive) 1f else 0.38f), fontSize = 14.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, letterSpacing = 0.3.sp, modifier = modifier)
}

@Composable
fun StudioBadge(brand: StudioBrand, isActive: Boolean, isLarge: Boolean = false) {
    val imageRes = when (brand) {
        StudioBrand.NETFLIX -> R.drawable.logo_netflix; StudioBrand.APPLE_TV -> R.drawable.logo_appletv
        StudioBrand.DISNEY -> R.drawable.logo_disney; StudioBrand.HBO -> R.drawable.logo_hbo
        StudioBrand.AMAZON -> R.drawable.logo_amazon; StudioBrand.PARAMOUNT -> R.drawable.logo_paramount
        StudioBrand.HULU -> R.drawable.logo_hulu
    }
    Box(modifier = Modifier.height(if (isLarge) 32.dp else 22.dp).width(if (isLarge) 80.dp else 50.dp).alpha(if (isActive) 1f else 0.4f), contentAlignment = Alignment.Center) {
        Image(painterResource(imageRes), brand.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun LandscapeCard(movie: Movie, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val url = movie.backdropUrl.ifBlank { movie.posterUrl }

    val imageRequest = remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .diskCacheKey(url)
            .size(600)
            .memoryCachePolicy(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    Column(modifier = modifier.width(LAND_W)) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(Border.None, Border.None),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier.fillMaxWidth().height(LAND_H).onFocusChanged { if (it.isFocused) onFocused() }
        ) {
            if (url.isNotBlank()) AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) { Text(movie.title, color = WHITE.copy(0.5f), fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(8.dp)) }

            if (movie.id.startsWith("http")) {
                val isDubbed = movie.title.contains("מדובב")
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(movie.title, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = DIM2, fontSize = 11.sp)
            }
            if (movie.rating > 0f) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth((if (prog >= 0.95f) 1f else prog).coerceIn(0f, 1f)).fillMaxHeight().background(RED))
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
            .diskCacheKey(url)
            .size(400)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(DeviceProfile.tier != DeviceProfile.Tier.LOW)
            .crossfade(false)
            .build()
    }

    Column(modifier = modifier.width(cardW), horizontalAlignment = Alignment.Start) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(Border.None, Border.None),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
            modifier = Modifier.fillMaxWidth().height(cardH).onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocused() }
        ) {
            if (url.isNotBlank()) AsyncImage(model = imageRequest, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(placeholderBrush), Alignment.Center) { Text(movie.title, color = WHITE.copy(0.55f), fontSize = 10.sp, maxLines = 3, modifier = Modifier.padding(8.dp)) }

            if (movie.id.startsWith("http")) {
                val isDubbed = movie.title.contains("מדובב")
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(if (isDubbed) Color(0xFFE91E63) else Color(0xFF00B0FF)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(if (isDubbed) tr("🎤 Dubbed", "🎤 מדובב") else "💎 FUZER", color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            if (movie.rating > 0f) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xBB000000)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("★ %.1f".format(movie.rating), color = GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            movie.progress?.takeIf { it >= 0.02f }?.let { prog ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth((if (prog >= 0.95f) 1f else prog).coerceIn(0f, 1f)).fillMaxHeight().background(RED))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(movie.title, color = if (isFocused) WHITE else DIM2, fontSize = 11.sp, fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(cardW))
        Text(if (movie.mediaType == "tv") tr("TV Show", "סדרה") else tr("Movie", "סרט"), color = DIM3, fontSize = 10.sp)
    }
}
