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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edgeShadow = remember(isRtl) {
        Brush.horizontalGradient(
            *if (isRtl) arrayOf(0.0f to Color(0x0D000000), 0.15f to Color.Transparent)
            else arrayOf(0.85f to Color.Transparent, 1.0f to Color(0x0D000000))
        )
    }

    val bottomFade = remember {
        Brush.verticalGradient(0.55f to Color.Transparent, 1.0f to BG)
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        Crossfade(
            targetState = heroUrl,
            animationSpec = tween(600, easing = LinearEasing),
            label = "backdropCrossfade",
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(url)
                        .size(coil.size.Size(1280, 720)) // פענוח קל שלא קורס
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // הצללות פשוטות שעובדות תמיד ללא קריסות GPU!
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f).background(edgeShadow))
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f).background(bottomFade))
    }
}

@Composable
fun MetaDot() = Text("  ·  ", color = DIM3, fontSize = 14.sp)

@Composable
fun HeroOverlay(hero: Movie?, panelH: Dp) {
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        hero?.let { m ->
            key(m.id) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 60.dp, end = 400.dp, bottom = panelH + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val tsz = when { m.title.length > 26 -> 28.sp; m.title.length > 16 -> 34.sp; else -> 44.sp }
                    Text(m.title, color = WHITE, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = (tsz.value * 1.15f).sp, letterSpacing = (-0.3).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.year > 0) { Text(m.year.toString(), color = DIM, fontSize = 13.sp); MetaDot() }
                        if (m.genre.isNotBlank()) { Text(m.genre, color = DIM, fontSize = 13.sp); MetaDot() }
                        Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = DIM, fontSize = 13.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            Row(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("IMDb", color = Color(0xFF141414), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (m.overview.isNotBlank()) {
                        Text(m.overview, color = DIM2, fontSize = 13.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 640.dp))
                    }
                }
            }
        }
    }
}