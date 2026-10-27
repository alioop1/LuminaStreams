package com.luminastreams.tv.presentation.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.*

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerSettingsOverlay(player: ExoPlayer, currentResizeMode: Int, modifier: Modifier = Modifier, onResizeModeChange: (Int) -> Unit, onClose: () -> Unit) {
    var audioTracks by remember { mutableStateOf<List<StreamTrackInfo>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<StreamTrackInfo>>(emptyList()) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val closeKey = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val tempAudio = mutableListOf<StreamTrackInfo>(); val tempSubs = mutableListOf<StreamTrackInfo>()
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i); val isSelected = group.isTrackSelected(i)
                        if (group.type == C.TRACK_TYPE_AUDIO) tempAudio.add(StreamTrackInfo(group, i, format, isSelected))
                        else if (group.type == C.TRACK_TYPE_TEXT) tempSubs.add(StreamTrackInfo(group, i, format, isSelected))
                    }
                }
                audioTracks = tempAudio; subTracks = tempSubs
            }
        }
        player.addListener(listener); listener.onTracksChanged(player.currentTracks); onDispose { player.removeListener(listener) }
    }

    Box(modifier = modifier.fillMaxHeight().width(400.dp).background(Color(0xD9000000), RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp)).border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp)).padding(40.dp)
        .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE || event.nativeKeyEvent.keyCode == closeKey)) { onClose(); true } else false }
    ) {
        Column {
            Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "אפשרויות" else "Options", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(modifier = Modifier.height(40.dp))
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("ASPECT RATIO", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp))
                    listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT to "Original", AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch", AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop").forEach { (mode, label) ->
                        SettingsButton(text = label, isSelected = currentResizeMode == mode, onClick = { onResizeModeChange(mode) })
                    }
                }
                if (audioTracks.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(24.dp)); Text("AUDIO", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp)) }
                    itemsIndexed(audioTracks) { index, trackInfo ->
                        val label = trackInfo.format.label ?: trackInfo.format.language?.uppercase()?.replace("UND", "UNKNOWN") ?: "UNKNOWN"
                        SettingsButton(text = "Track ${index + 1}: $label", isSelected = trackInfo.isSelected, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).addOverride(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex)).build() })
                    }
                }
                if (subTracks.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(24.dp)); Text("SUBTITLES", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(modifier = Modifier.height(8.dp))
                        SettingsButton(text = "Off", isSelected = !subTracks.any { it.isSelected }, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() })
                    }
                    itemsIndexed(subTracks) { index, trackInfo ->
                        val label = trackInfo.format.label ?: trackInfo.format.language?.uppercase()?.replace("UND", "UNKNOWN") ?: "UNKNOWN"
                        SettingsButton(text = "Subtitle ${index + 1}: $label", isSelected = trackInfo.isSelected, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).addOverride(TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackIndex)).build() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) { Text("✓", color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(12.dp)) }
            Text(text = text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp)
        }
    }
}
