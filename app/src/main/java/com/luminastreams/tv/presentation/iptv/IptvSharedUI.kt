@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.luminastreams.tv.presentation.iptv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HintPill(key: String, label: String) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(WHITE.copy(0.08f))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(WHITE.copy(0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(key, color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = WHITE.copy(0.5f), fontSize = 10.sp)
    }
}

@Composable
fun LiveBadge() {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(RED).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(5.dp).background(WHITE, CircleShape))
            Text("LIVE", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ResBadge(res: String) {
    val color = when {
        res.contains("4K", true) || res.contains("UHD", true) -> Color(0xFFFF6B35)
        res.contains("FHD", true) || res.contains("1080", true) -> ACCENT
        else -> MUTED
    }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.18f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(res, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgBar(fraction: Float, width: Dp) {
    Box(Modifier.width(width).height(3.dp).clip(CircleShape).background(WHITE.copy(0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
            .background(ProgBarBrush))
    }
}

@Composable
fun ChannelLogoImage(
    channel  : IptvChannel,
    logoUrl  : String,
    size     : Dp,
    isFocused: Boolean = false
) {
    val ctx      = LocalContext.current
    val initials = channel.name.take(2).uppercase()
    if (logoUrl.isNotBlank()) {
        val errorState = remember(logoUrl) { mutableStateOf(false) }
        if (errorState.value) {
            Text(initials, color = if (isFocused) BG else WHITE,
                fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black)
        } else {
            AsyncImage(
                model = remember(logoUrl) {
                    ImageRequest.Builder(ctx).data(logoUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .allowHardware(true)
                        .crossfade(false)
                        .dispatcher(kotlinx.coroutines.Dispatchers.IO)
                        .build()
                },
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(size),
                onError            = { errorState.value = true }
            )
        }
    } else {
        Text(initials, color = if (isFocused) BG else WHITE,
            fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun StreamTracksBadges(currTracks: androidx.media3.common.Tracks) {
    val audioLangs = remember(currTracks) {
        currTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { grp -> (0 until grp.length).mapNotNull { grp.mediaTrackGroup.getFormat(it).language?.uppercase() } }
            .filter { it.length <= 4 }.toSet().toList()
    }
    val subLangs = remember(currTracks) {
        currTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { grp -> (0 until grp.length).mapNotNull { grp.mediaTrackGroup.getFormat(it).language?.uppercase() } }
            .filter { it.length <= 4 }.toSet().toList()
    }
    if (audioLangs.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = ACCENT2, modifier = Modifier.size(13.dp))
            Text(audioLangs.joinToString(", "), color = WHITE.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (subLangs.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Subtitles, null, tint = ACCENT2, modifier = Modifier.size(13.dp))
            Text(subLangs.joinToString(", "), color = WHITE.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FullscreenClock() {
    var t by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            t = fmt.format(Date())
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Text(t, color = WHITE, fontSize = 38.sp, fontWeight = FontWeight.Thin, letterSpacing = (-1).sp)
}