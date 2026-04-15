@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.delay

@Composable
fun BackdropLayer(hero: Movie?) {
    val ctx = LocalContext.current
    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW

    // המתנה קלה לפני טעינת הרקע כדי שגלילה מהירה לא תתקע את המכשיר
    var debouncedUrl by remember { mutableStateOf<String?>(null) }
    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl

    LaunchedEffect(heroUrl) {
        if (heroUrl == debouncedUrl) return@LaunchedEffect
        if (!isLow) delay(250) // השהיית ביצועים לגלילה מהירה
        debouncedUrl = heroUrl
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(Modifier.fillMaxSize()) {
        // רקע שחור אחיד שיושב מאחורי הכל
        Box(Modifier.fillMaxSize().background(BG))

        // 🚀 מנוע ה-Crossfade המובנה: ממזג (Melt) את התמונות אחת לתוך השנייה ב-60FPS
        Crossfade(
            targetState = debouncedUrl,
            animationSpec = tween(if (isLow) 0 else 800, easing = LinearEasing),
            label = "backdropCrossfade"
        ) { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url) {
                        ImageRequest.Builder(ctx)
                            .data(url)
                            .size(Size(1920, 1080)) // נעול ל-1080p כדי למנוע דילוגי פריימים!
                            .scale(Scale.FILL)
                            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(DeviceProfile.tier != DeviceProfile.Tier.LOW)
                            .crossfade(false) // Compose עושה את המעבר עכשיו, לא Coil
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f) // תופס 80% מהגובה
                        .graphicsLayer {
                            alpha = 0.8f // 80% אטימות (Opacity) כמו שביקשת!
                        }
                        .drawWithCache {
                            // השחרה מלמטה לשילוב טבעי עם הרקע
                            val bottomFade = Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.90f to BG,
                                1.0f to BG,
                                startY = 0f,
                                endY = size.height
                            )

                            // הצללת צד להבלטת טקסט
                            val textProtectionFade = Brush.horizontalGradient(
                                *if (isRtl) arrayOf(0.0f to BG.copy(alpha = 0.85f), 0.5f to Color.Transparent)
                                else arrayOf(0.5f to Color.Transparent, 1.0f to BG.copy(alpha = 0.85f))
                            )

                            onDrawWithContent {
                                drawContent()
                                drawRect(textProtectionFade)
                                drawRect(bottomFade)
                            }
                        }
                )
            }
        }
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