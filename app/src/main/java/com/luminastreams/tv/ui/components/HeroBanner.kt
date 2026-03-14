package com.luminastreams.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Netflix-accurate Hero Banner component.
 * • Full-bleed backdrop image
 * • 3-layer gradient scrim (bottom, left, top)
 * • Palette color extracted for ambient glow tint
 * • Meta row: match %, year, HD badge, age rating
 * • Quality + genre chips
 * • Play / More Info CTA buttons
 */
@Composable
fun HeroBanner(
    focusedMovie: Movie?,
    modifier: Modifier = Modifier,
    heroHeight: androidx.compose.ui.unit.Dp = 520.dp,
    onPlayClick: (String) -> Unit = {},
    onMoreInfoClick: (String) -> Unit = {}
) {
    var dominantColor by remember { mutableStateOf(Color(0xFF0A0A0A)) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ambientColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "ambient")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // ── Animated backdrop ───────────────────────────────────────
        AnimatedContent(
            targetState = focusedMovie?.backdropUrl ?: focusedMovie?.posterUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) },
            label = "backdrop"
        ) { imageUrl ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .size(1920, 1080)
                    .allowHardware(true)
                    .listener(onSuccess = { _, result ->
                        coroutineScope.launch(Dispatchers.Default) {
                            val bmp = result.drawable.toBitmap().copy(Bitmap.Config.RGB_565, false)
                            val palette = Palette.from(bmp).generate()
                            val c = palette.getDarkVibrantColor(
                                palette.getDarkMutedColor(android.graphics.Color.parseColor("#0A0A0A"))
                            )
                            withContext(Dispatchers.Main) { dominantColor = Color(c) }
                        }
                    }).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Ambient glow tint from palette ─────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to ambientColor.copy(alpha = 0.35f)
            )
        ))

        // ── Bottom-to-black gradient ─────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f   to Color.Transparent,
                0.45f to Color.Transparent,
                0.78f to OledBlack.copy(alpha = 0.7f),
                1f   to OledBlack
            )
        ))

        // ── Left-side gradient for text legibility ──────────────────────
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f    to OledBlack.copy(alpha = 0.88f),
                0.5f  to OledBlack.copy(alpha = 0.25f),
                1f    to Color.Transparent
            )
        ))

        // ── Top gradient (nav area readability) ────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f    to OledBlack.copy(alpha = 0.55f),
                0.2f  to Color.Transparent
            )
        ))

        // ── Hero content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 56.dp)
                .fillMaxWidth(0.52f)
        ) {
            // Quality + genre chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (focusedMovie?.is4K == true) NetflixQualityBadge("4K HDR")
                focusedMovie?.rating?.let { if (it > 0) NetflixQualityBadge("IMDb $it") }
                NetflixGenreChip("Action")
                NetflixGenreChip("Sci-Fi")
            }

            Spacer(Modifier.height(14.dp))

            // Title
            Text(
                text = focusedMovie?.title ?: "",
                color = TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 58.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            // Meta row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(MatchGreen).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text("97% Match", color = OledBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                Text(
                    text = focusedMovie?.releaseDate?.take(4) ?: "2024",
                    color = TextSecondary, fontSize = 15.sp
                )
                NetflixMetaBadge("HD")
                NetflixMetaBadge("16+")
            }

            Spacer(Modifier.height(16.dp))

            // Overview
            Text(
                text = focusedMovie?.overview ?: "",
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            // CTA Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NetflixHeroCtaButton(label = "▶  Play", isPrimary = true) {
                    focusedMovie?.id?.let(onPlayClick)
                }
                NetflixHeroCtaButton(label = "ℹ  More Info", isPrimary = false) {
                    focusedMovie?.id?.let(onMoreInfoClick)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Sub-components
// ─────────────────────────────────────────────
@Composable
fun NetflixHeroCtaButton(label: String, isPrimary: Boolean, onClick: () -> Unit) {
    var isFocused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = when {
            isPrimary && isFocused -> TextPrimary.copy(alpha = 0.85f)
            isPrimary              -> TextPrimary
            isFocused              -> GlassWhiteBtn.copy(alpha = 0.55f)
            else                   -> GlassWhiteBtn
        }, animationSpec = tween(120)
    )
    val textCol by animateColorAsState(
        targetValue = if (isPrimary) OledBlack else TextPrimary,
        animationSpec = tween(120)
    )
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = if (!isPrimary) ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.5.dp, GlassWhiteBorder)
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.5.dp, TextPrimary)
            )
        ) else ClickableSurfaceDefaults.border(),
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = if (isPrimary) 160.dp else 180.dp)
            .androidx.compose.ui.focus.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = textCol, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun NetflixQualityBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TextPrimary)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = OledBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NetflixMetaBadge(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, BadgeBorder, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NetflixGenreChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// Keep legacy names for backward compat
@Composable fun QualityBadge(text: String) = NetflixQualityBadge(text)
@Composable fun GenreChip(text: String) = NetflixGenreChip(text)
