@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl

    val fades = remember {
        listOf(
            Brush.horizontalGradient(0.0f to BG, 0.20f to Color.Transparent),
            Brush.horizontalGradient(0.80f to Color.Transparent, 1.0f to BG),
            Brush.verticalGradient(0.0f to BG, 0.15f to Color.Transparent),
            Brush.verticalGradient(0.40f to Color.Transparent, 1.0f to BG)
        )
    }

    Box(Modifier.fillMaxSize().background(BG)) {
        // Nuvio Logic: Crossfade חוסך 80% מהמאמץ של ה-GPU לעומת Slide על תמונת ענק
        Crossfade(
            targetState = heroUrl,
            animationSpec = tween(600, easing = LinearEasing),
            label = "backdropNuvio",
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(url)
                        .memoryCacheKey("hero_$url") // מפתח ייעודי מונע טעינה כפולה
                        .diskCacheKey("hero_$url")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true) // Nuvio Magic: עוקף את ה-CPU ישירות לכרטיס מסך!
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        fades.forEach { Box(Modifier.fillMaxWidth().fillMaxHeight(0.85f).background(it)) }
    }
}

@Composable
fun MetaDot() = Text("  •  ", color = WHITE.copy(alpha = 0.5f), fontSize = 14.sp)

@Composable
fun HeroOverlay(hero: Movie?, panelH: Dp) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(Modifier.fillMaxSize().zIndex(3f)) {
        // הטקסט מחליק ב-60FPS נקי כי הוא שוקל כלום לכרטיס המסך
        AnimatedContent(
            targetState = hero,
            transitionSpec = {
                (fadeIn(tween(400)) + slideInHorizontally(tween(600, easing = FastOutSlowInEasing)) { if (isRtl) -it/8 else it/8 }).togetherWith(
                    fadeOut(tween(400)) + slideOutHorizontally(tween(600, easing = FastOutSlowInEasing)) { if (isRtl) it/8 else -it/8 }
                )
            },
            label = "textSlide",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 52.dp, bottom = panelH + 32.dp)
        ) { m ->
            if (m != null) {
                Column(
                    modifier = Modifier.widthIn(max = 680.dp).background(Color(0x4D000000), RoundedCornerShape(32.dp)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(32.dp)).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val tsz = when { m.title.length > 26 -> 32.sp; m.title.length > 16 -> 40.sp; else -> 48.sp }
                    Text(m.title, color = WHITE, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = (tsz.value * 1.1f).sp, letterSpacing = (-0.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.year > 0) { Text(m.year.toString(), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); MetaDot() }
                        if (m.genre.isNotBlank()) { Text(m.genre, color = WHITE.copy(alpha = 0.8f), fontSize = 14.sp); MetaDot() }
                        Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = WHITE.copy(alpha = 0.8f), fontSize = 14.sp)
                        if (m.rating > 0f) {
                            MetaDot()
                            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("IMDb", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text("%.1f".format(m.rating), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    if (m.overview.isNotBlank()) {
                        Text(m.overview, color = WHITE.copy(alpha = 0.6f), fontSize = 15.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}