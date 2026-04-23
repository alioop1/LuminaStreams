@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.luminastreams.tv.presentation.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
fun DetailedSourceCard(stream: AdvancedStreamSource, posterUrl: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val searchString = (stream.filename + " " + stream.releaseGroup).uppercase()
    val is4k = stream.quality.name.contains("4K") || searchString.contains("2160P")

    // TAG PARSING
    val videoTags = mutableListOf<String>()
    if (searchString.contains("DV") || searchString.contains("DOLBY VISION") || searchString.contains("DOVI")) videoTags.add("Dolby Vision")
    if (searchString.contains("HDR10+") || searchString.contains("HDR10PLUS")) videoTags.add("HDR10+")
    else if (searchString.contains("HDR")) videoTags.add("HDR")
    if (searchString.contains("REMUX")) videoTags.add("REMUX")
    if (searchString.contains("HEVC") || searchString.contains("X265") || searchString.contains("H265")) videoTags.add("HEVC")

    val audioTags = mutableListOf<String>()
    if (searchString.contains("ATMOS") || searchString.contains("DOLBY ATMOS")) audioTags.add("Dolby Atmos")
    if (searchString.contains("TRUEHD") || searchString.contains("TRUE-HD")) audioTags.add("TrueHD")
    if (searchString.contains("DTS-HD") || searchString.contains("DTSHD")) audioTags.add("DTS-HD")
    if (searchString.contains("DTS-X") || searchString.contains("DTSX")) audioTags.add("DTS:X")
    if (searchString.contains("FLAC")) audioTags.add("FLAC")
    if (searchString.contains("EAC3") || searchString.contains("E-AC3")) audioTags.add("E-AC3")
    if (searchString.contains("DDP") || searchString.contains("DDP5.1")) audioTags.add("DDP 5.1")
    else if (searchString.contains("DD5.1") || searchString.contains("AC3")) audioTags.add("DD 5.1")
    if (searchString.contains("7.1")) audioTags.add("7.1")

    val langTags = mutableListOf<String>()
    if (searchString.contains("HEB") || searchString.contains("HEBREW") || searchString.contains("מדובב") || searchString.contains("IL")) langTags.add("HEB")
    if (searchString.contains("MULTI") || searchString.contains("DUAL")) langTags.add("MULTI")
    if (searchString.contains("ENG") || searchString.contains("EN ")) langTags.add("ENG")

    val seederMatch = Regex("(?:👤|S:|seeders:)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(stream.releaseGroup)
    val seeders = seederMatch?.groupValues?.getOrNull(1) ?: "-"

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp), focusedShape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.dp, Color.Transparent)), focusedBorder = Border(BorderStroke(0.dp, Color.Transparent)), pressedBorder = Border(BorderStroke(0.dp, Color.Transparent))),
        modifier = modifier.fillMaxWidth().aspectRatio(2f/3f)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f), Color.Black.copy(0.95f)), startY = 100f)))

            Column(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (is4k) CardTag("4K", Color(0xFF00E5FF)) else CardTag("1080p", Color.White)
                    if (stream.isCachedRd) CardTag("RD+", Color(0xFF00E676))
                }
                Spacer(Modifier.height(6.dp))
                Text(stream.releaseGroup.ifEmpty { "UNKNOWN" }.substringBefore("\n").trim(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))

                SourceFlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    videoTags.forEach { tag -> CardPill(tag, Icons.Default.Tv, Color(0xFFB388FF)) }
                    audioTags.forEach { tag -> CardPill(tag, Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF29B6F6)) }
                    langTags.forEach { tag -> CardPill(tag, Icons.Default.Language, Color(0xFFFFD54F)) }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(if (stream.sizeGb >= 1.0) "%.1f GB".format(stream.sizeGb) else "%.0f MB".format(stream.sizeBytes / 1048576.0), color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (seeders != "-") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(seeders, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Default.Person, null, tint = Color(0xFF00E676), modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── COMPONENT HELPERS ───

@Composable
fun CardTag(text: String, color: Color) {
    Box(Modifier.background(color.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CardPill(text: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(color.copy(0.1f), RoundedCornerShape(50)).border(1.dp, color.copy(0.3f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SourceFlowRow(modifier: Modifier = Modifier, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, content: @Composable () -> Unit) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = horizontalArrangement, verticalAlignment = Alignment.CenterVertically) {
        content()
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
fun EpisodeCardOptimized(episode: Episode, fallback: String, modifier: Modifier = Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(1.05f),
        // ⚡ WIRED THE ONFOCUSED TRIGGER BACK IN!
        modifier = modifier.width(340.dp).aspectRatio(16f/9f).onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = episode.stillUrl.ifBlank { fallback }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)), startY = 100f)))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("E${episode.episodeNumber} • ${episode.title}", color = ColorTextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (episode.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Color.DarkGray)) {
                    Box(Modifier.fillMaxWidth(episode.progress).fillMaxHeight().background(ColorAccentIsland))
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