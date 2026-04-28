@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.core.DeviceProfile

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    // Full-quality backdrop from TMDB — decoded at device screen resolution
    val heroUrl = (hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl)
        ?.replace("/w780/", "/original/")
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Decode at actual screen resolution — sharp on 1080p AND 4K TVs
    val dm = ctx.resources.displayMetrics
    val screenW = dm.widthPixels
    val screenH = dm.heightPixels

    Box(Modifier.fillMaxSize().background(BG)) {
        if (!heroUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(heroUrl)
                    .size(screenW, screenH) // Match device panel — no upscale, no waste
                    .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().fillMaxHeight() // Full Bleed!
            )
        }

        // Deep cinematic vignette — strengthened for premium readability
        Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0.0f to Color(0x99000000), 0.3f to Color.Transparent)) // Top Nav Shadow (60%)
            .background(
                Brush.horizontalGradient(
                    *if (isRtl) arrayOf(0.0f to Color(0xBB000000), 0.55f to Color.Transparent)
                    else arrayOf(0.45f to Color.Transparent, 1.0f to Color(0xBB000000))
                )
            ) // Side Hero Shadow (73%)
            .background(Brush.verticalGradient(0.35f to Color.Transparent, 0.85f to BG, 1.0f to BG)) // Deep Bottom Shadow
        )
    }
}

@Composable
fun MetaDot() = Text("  ·  ", color = DIM3, fontSize = 14.sp)

@Composable
fun HeroOverlay(hero: Movie?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = hero,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        modifier = modifier,
        label = "hero_fade"
    ) { m ->
        if (m != null) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val tsz = when { m.title.length > 26 -> 38.sp; m.title.length > 16 -> 46.sp; else -> 56.sp }
                Text(m.title, color = WHITE, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = (tsz.value * 1.1f).sp, letterSpacing = (-0.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.year > 0) { Text(m.year.toString(), color = DIM, fontSize = 15.sp); MetaDot() }
                    if (m.genre.isNotBlank()) { Text(m.genre, color = DIM, fontSize = 15.sp); MetaDot() }
                    Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = DIM, fontSize = 15.sp)
                    if (m.rating > 0f) {
                        MetaDot()
                        Row(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("IMDb", color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                if (m.overview.isNotBlank()) {
                    Text(m.overview, color = DIM2, fontSize = 16.sp, lineHeight = 26.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 700.dp))
                }
            }
        }
    }
}