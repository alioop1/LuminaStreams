@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.tv.material3.*
import coil.compose.AsyncImage


// 🎨 COMPONENT PALETTE
private val ColorGlassBento = Color(0x33FFFFFF)
private val ColorTextMain = Color(0xFFFFFFFF)
private val ColorAccentIsland = Color(0xFFE50914)

// ─── UTILITY ───
fun launchNativeTrailer(context: Context, trailerIdOrUrl: String?, fallbackTitle: String) {
    val appCtx = context.applicationContext
    if (!trailerIdOrUrl.isNullOrBlank()) {
        val ytAppIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:$trailerIdOrUrl".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appCtx.packageManager.resolveActivity(ytAppIntent, 0) != null) {
            try { appCtx.startActivity(ytAppIntent); return } catch (_: Exception) {}
        }
        val watchUrl = if (trailerIdOrUrl.startsWith("http")) trailerIdOrUrl else "https://www.youtube.com/watch?v=$trailerIdOrUrl"
        try { appCtx.startActivity(Intent(Intent.ACTION_VIEW, watchUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
    try {
        appCtx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/results?search_query=${Uri.encode("$fallbackTitle official trailer")}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

// ─── 🎬 DYNAMIC SOURCE POSTER CARD (DELICATE ZOOM & ZERO LAG) ───
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailedSourceCube(stream: AdvancedStreamSource, logoUrl: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val searchString = (stream.filename + " " + stream.releaseGroup).uppercase().replace(".", " ")
    val is4k = stream.quality.name.contains("4K") || searchString.contains("2160P")
    val isHdr = searchString.contains(" HDR") || searchString.contains("HDR10")
    val isDv = searchString.contains(" DV ") || searchString.contains(" DOVI ") || searchString.contains("DOLBY VISION")
    val isAtmos = searchString.contains("ATMOS")
    val isCached = stream.isCachedRd
    Surface(
        onClick = onClick,
        modifier = modifier.width(260.dp).height(150.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF141417),
            focusedContainerColor = Color(0xFF141417)
        ),
        // ⚡ The PS5 Thick Glowing Border
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White)),
            border = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(0.3f), elevation = 24.dp))
    ) {
        Box(Modifier.fillMaxSize()) {

            // 1. Subtle radial glow in the background
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(0.05f), Color.Transparent), radius = 300f)))

            // 2. 🎬 LOGO (Moved to Center so it sits beautifully under the top tags)
            Box(
                Modifier.align(Alignment.Center).padding(bottom = 24.dp).height(48.dp).fillMaxWidth(0.8f),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl.isNotBlank()) {
                    AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }

            // 3. 🏷️ PREMIUM TAGS (Top Right - Drawn AFTER the logo so they float on top)
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (is4k) PremiumTag("4K", Color(0xFF00E5FF)) else PremiumTag("HD", Color.White)
                if (isHdr) PremiumTag("HDR", Color(0xFFFF9500))
                if (isDv) PremiumTag("DV", Color(0xFFE50914))
                if (isAtmos) PremiumTag("ATMOS", Color(0xFF0A84FF))
                if (isCached) PremiumTag("RD+", Color(0xFF32D74B))
            }

            // 4. 📝 BOTTOM METADATA TEXT
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))).padding(16.dp)) {
                Text(
                    text = stream.releaseGroup.ifEmpty { "UNKNOWN GROUP" }.uppercase(),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stream.filename.substringBefore("\n").trim(),
                    color = Color.White.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (stream.sizeGb >= 1.0) "%.1f GB".format(stream.sizeGb) else "%.0f MB".format(stream.sizeBytes / 1048576.0),
                    color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Ultra-sharp, minimal tag design
@Composable
fun PremiumTag(text: String, color: Color) {
    Box(modifier = Modifier.background(color.copy(0.15f), RoundedCornerShape(4.dp)).border(1.dp, color.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

// ─── MAIN SCREEN BENTO COMPONENTS ───

@Composable
fun BentoCard(title: String, value: String, isWide: Boolean = false) {
    Box(Modifier.width(if(isWide) 220.dp else 140.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(ColorGlassBento).padding(16.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = ColorTextMain, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ActionIsland(playText: String, modifier: Modifier = Modifier, onPlayClick: () -> Unit, onSourcesClick: () -> Unit, onTrailerClick: () -> Unit, isFavorite: Boolean, onFavClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(0.1f)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onPlayClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(containerColor = ColorAccentIsland, contentColor = ColorTextMain, focusedContainerColor = ColorTextMain, focusedContentColor = Color.Black),
            scale = ClickableSurfaceDefaults.scale(1.05f),
            modifier = modifier.height(64.dp)
        ) {
            Row(Modifier.padding(horizontal = 32.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(playText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
        PremiumIconButton(Icons.AutoMirrored.Filled.List, onClick = onSourcesClick)
        PremiumIconButton(Icons.Default.PlayCircleOutline, onClick = onTrailerClick)
        PremiumIconButton(if(isFavorite) Icons.Default.Check else Icons.Default.Add, tint = if(isFavorite) Color(0xFF00E676) else ColorTextMain, onClick = onFavClick)
    }
}

@Composable
fun PremiumIconButton(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = ColorTextMain, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = tint, focusedContainerColor = ColorTextMain, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(1.1f), modifier = modifier.size(64.dp)
    ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, Modifier.size(28.dp)) } }
}

@Composable
fun EpisodeCardOptimized(
    episode: Episode,
    fallback: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick ?: {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(1.05f),
        // ⚡ WIRED THE ONFOCUSED TRIGGER BACK IN!
        modifier = modifier.width(340.dp).aspectRatio(16f/9f).onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = episode.stillUrl.ifBlank { fallback },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (episode.hasWatched) Modifier.alpha(0.5f) else Modifier
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)), startY = 100f)))

            // ✅ Watched badge — top-right green checkmark
            if (episode.hasWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                        .background(Color(0xFF32D74B), RoundedCornerShape(50))
                        .border(2.dp, Color.White.copy(0.3f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("E${episode.episodeNumber} • ${episode.title}", color = ColorTextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            // Progress bar — red for in-progress, green for fully watched
            if (episode.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Color.DarkGray)) {
                    Box(Modifier.fillMaxWidth(episode.progress).fillMaxHeight().background(
                        if (episode.hasWatched) Color(0xFF32D74B) else ColorAccentIsland
                    ))
                }
            }
        }
    }
}

@Composable
fun CastCardOptimized(actor: CastMember, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(130.dp)) {
        Surface(
            onClick = {}, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            scale = ClickableSurfaceDefaults.scale(1.08f),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f/3f)
        ) { AsyncImage(model = actor.imageUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Spacer(Modifier.height(16.dp))
        Text(actor.name, color = ColorTextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        Text(actor.character, color = ColorTextMain.copy(0.5f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
@Composable
fun SeasonPill(seasonNumber: Int, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if(isSelected) ColorTextMain else Color.White.copy(0.1f),
            contentColor = if(isSelected) Color.Black else ColorTextMain,
            focusedContainerColor = ColorAccentIsland,
            focusedContentColor = ColorTextMain
        ),
        scale = ClickableSurfaceDefaults.scale(1.05f),
        modifier = modifier
    ) {
        Text(
            text = "Season $seasonNumber",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = Color.White.copy(0.4f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = ColorTextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RecommendationCard(rec: Recommendation, modifier: Modifier = Modifier) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
            border = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(0.2f), elevation = 20.dp)),
        modifier = modifier.width(150.dp).aspectRatio(2f / 3f)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = rec.posterUrl,
                contentDescription = rec.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)), startY = 200f)))
            Text(
                rec.title,
                color = ColorTextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }
    }
}