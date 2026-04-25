package com.luminastreams.tv.presentation.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "zoom"
    )
    val accent = if (isFuzer) ACCENT_PINK else RED
    val qBadge: String? = when {
        result.qualityTag.isNotBlank() -> result.qualityTag
        result.title.contains("4K", true) || result.title.contains("2160p", true) -> "4K"
        result.title.contains("1080p", true) -> "FHD"
        result.title.contains("720p", true) -> "HD"
        else -> null
    }
    val isDubbed = isFuzer && result.title.contains("מדובב", true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .zIndex(if (focused) 10f else 0f)
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = CARD_BG, focusedContainerColor = CARD_BG),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(border = BorderStroke(2.5.dp, accent.copy(0.8f)), shape = RoundedCornerShape(12.dp))
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(accent.copy(0.4f), 24.dp)),
            modifier = Modifier.fillMaxSize().onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(result)
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (result.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(result.posterUrl).crossfade(300).build(),
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.65f).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(if (focused) 0.95f else 0.7f))))
                )

                if (focused) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(WHITE.copy(0.15f), Color.Transparent))))
                }

                Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isDubbed) Badge("🎤 DUB", ACCENT_PINK)
                    else if (result.releaseYear.isNotBlank()) Badge(result.releaseYear, Color(0xBB000000))

                    if (isFuzer && !isDubbed) Badge("💎 FUZER", Color(0xFF00B0FF))
                }

                if (qBadge != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Badge(qBadge, when(qBadge) { "4K" -> Color(0xFFFF3D00); "FHD" -> ACCENT_BLUE; else -> ACCENT_GREEN })
                    }
                }

                Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        result.title, color = WHITE, fontSize = 13.sp,
                        fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (result.rating > 0f) {
                            Text("★ %.1f".format(result.rating), color = GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("•", color = DIM3, fontSize = 11.sp)
                        }
                        Text(if (isFuzer) "Fuzer" else if (result.type == MediaType.TV_SHOW) "TV Show" else "Movie", color = DIM.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}