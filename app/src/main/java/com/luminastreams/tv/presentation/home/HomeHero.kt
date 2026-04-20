@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.home

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.core.DeviceProfile

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(Modifier.fillMaxSize().background(BG)) {
        if (!heroUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(heroUrl)
                    .size(coil.size.Size(1280, 720))
                    .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            )
        }

        Box(Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .background(Brush.verticalGradient(0.0f to BG, 0.35f to Color.Transparent))
            .background(
                Brush.horizontalGradient(
                    *if (isRtl) arrayOf(0.0f to Color(0x1A000000), 0.20f to Color.Transparent)
                    else arrayOf(0.80f to Color.Transparent, 1.0f to Color(0x1A000000))
                )
            )
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1.0f to BG))
        )
    }
}

@Composable
fun MetaDot() = Text("  ·  ", color = DIM3, fontSize = 14.sp)

// FIX: Accepts bottomPadding directly to slide the text up and down without layout trashing
@Composable
fun HeroOverlay(hero: Movie?, bottomPadding: Dp) {
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        hero?.let { m ->
            key(m.id) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, end = 440.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val tsz = when { m.title.length > 26 -> 28.sp; m.title.length > 16 -> 36.sp; else -> 48.sp }
                    Text(m.title, color = WHITE, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = (tsz.value * 1.15f).sp, letterSpacing = (-0.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.year > 0) { Text(m.year.toString(), color = DIM, fontSize = 14.sp); MetaDot() }
                        if (m.genre.isNotBlank()) { Text(m.genre, color = DIM, fontSize = 14.sp); MetaDot() }
                        Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = DIM, fontSize = 14.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            Row(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (m.overview.isNotBlank()) {
                        Text(m.overview, color = DIM2, fontSize = 14.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 680.dp))
                    }
                }
            }
        }
    }
}