package com.luminastreams.tv.presentation.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult

@Composable
fun MediaSearchCard(
    result: SearchResult,
    isFuzer: Boolean,
    modifier: Modifier = Modifier,
    onFocus: (SearchResult) -> Unit,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val accent = if (isFuzer) ACCENT_PINK else RED
    val qBadge: String? = when {
        result.qualityTag.isNotBlank() -> result.qualityTag
        result.title.contains("4K", true) || result.title.contains("2160p", true) -> "4K"
        result.title.contains("1080p", true) -> "FHD"
        result.title.contains("720p", true) -> "HD"
        else -> null
    }
    val isDubbed = isFuzer && result.title.contains("מדובב", true)
    val imgAlpha by animateFloatAsState(if (focused) 1f else 0.85f, tween(250), label = "ia")

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0A0A0F),
            focusedContainerColor = Color(0xFF0A0A0F)
        ),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(Color.White.copy(0.15f), 10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(result)
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            // Poster image
            if (result.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(300).build(),
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(imgAlpha)
                )
            }

            // Bottom gradient for text
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent,
                        Color.Black.copy(if (focused) 0.92f else 0.75f)
                    )))
            )

            // Top badges
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isDubbed) Badge("🎤 DUB", ACCENT_PINK)
                else if (result.releaseYear.isNotBlank()) Badge(result.releaseYear, Color(0xAA000000))
                if (isFuzer && !isDubbed) Badge("💎 FUZER", Color(0xFF00B0FF))
            }

            if (qBadge != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Badge(qBadge, when (qBadge) {
                        "4K" -> Color(0xFFFF3D00)
                        "FHD" -> ACCENT_BLUE
                        else -> ACCENT_GREEN
                    })
                }
            }

            // Bottom info
            Column(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    result.title,
                    color = WHITE,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (result.rating > 0f) {
                        Text(
                            "★ %.1f".format(result.rating),
                            color = GOLD,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (isFuzer) "Fuzer"
                        else if (result.type == MediaType.TV_SHOW) "TV Show"
                        else "Movie",
                        color = DIM.copy(0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}