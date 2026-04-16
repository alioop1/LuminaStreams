package com.luminastreams.tv.presentation.iptv

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.util.Locale
import androidx.compose.ui.unit.dp

data class StreamTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
@Composable
fun IptvPlayerScreen(
    streamUrl: String,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    var showSettings by remember { mutableStateOf(false) }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var audioTracks by remember { mutableStateOf<List<StreamTrack>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<StreamTrack>>(emptyList()) }

    // תיקון #1 — מונה ניסיונות retry למניעת לולאה אינסופית
    var retryCount by remember { mutableIntStateOf(0) }
    val maxRetries = 3

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(32000, 64000, 2500, 5000)
            .build()
    }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val newAudio = mutableListOf<StreamTrack>()
                val newSubs = mutableListOf<StreamTrack>()
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val lang = format.language ?: "Unknown"
                        val name = Locale(lang).displayLanguage.ifBlank { "Track ${i + 1}" }
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            newAudio.add(StreamTrack(group, i, name, isSelected))
                        } else if (group.type == C.TRACK_TYPE_TEXT) {
                            newSubs.add(StreamTrack(group, i, name, isSelected))
                        }
                    }
                }
                audioTracks = newAudio
                subTracks = newSubs
            }

            // תיקון #1 — מקסימום 3 ניסיונות retry, אחר כך מפסיק
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < maxRetries) {
                    retryCount++
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                // אחרי maxRetries — מפסיק, הנגן נשאר בstate שגוי וניתן לטפל ב-UI
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // איפוס מונה ה-retry כשהנגן עולה בהצלחה
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            retryCount = 0 // איפוס בכל מעבר ערוץ
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
            else if (event == Lifecycle.Event.ON_RESUME) exoPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    val selectTrack = { track: StreamTrack, type: Int ->
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
            .setTrackTypeDisabled(type, false)
            .build()
    }

    val disableSubtitles = {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        subTracks = subTracks.map { it.copy(isSelected = false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> {
                            showSettings = !showSettings
                            true
                        }
                        // תיקון #3 — DPAD_LEFT סוגר את ה-settings panel
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (showSettings) { showSettings = false; true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PLUS,
                        KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_PAGE_UP -> {
                            if (!showSettings) onChannelUp()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MINUS,
                        KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (!showSettings) onChannelDown()
                            true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            if (showSettings) showSettings = false else onBackPressed()
                            true
                        }
                        else -> false
                    }
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PLUS,
                        KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_PAGE_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MINUS,
                        KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_PAGE_DOWN,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> true
                        else -> false
                    }
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view -> view.resizeMode = currentResizeMode },
            modifier = Modifier.fillMaxSize()
        )

        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xE60A0A12))
                    .padding(24.dp)
            ) {
                LazyColumn(modifier = Modifier.focusRequester(settingsFocusRequester)) {
                    item {
                        Text("יחס תמונה (Aspect Ratio)", color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    item {
                        SettingRow("התאמה למסך (Fit)",
                            currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                    item {
                        SettingRow("מתיחה (Fill)",
                            currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    }
                    item {
                        SettingRow("תקריב (Zoom)",
                            currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                    item {
                        Text("שפת שמע (Audio)", color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (audioTracks.isEmpty()) {
                        item { Text("ערוץ שמע יחיד", color = Color.White, modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(audioTracks) { track ->
                            SettingRow(track.name, track.isSelected) {
                                selectTrack(track, C.TRACK_TYPE_AUDIO)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                    item {
                        Text("כתוביות (Subtitles)", color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    item {
                        SettingRow("ללא כתוביות", subTracks.none { it.isSelected }) {
                            disableSubtitles()
                        }
                    }
                    items(subTracks) { track ->
                        SettingRow(track.name, track.isSelected) {
                            selectTrack(track, C.TRACK_TYPE_TEXT)
                        }
                    }
                }
            }
            // תיקון #2 — requestFocus עם try/catch למניעת crash אם ה-LazyColumn לא מוכן
            LaunchedEffect(Unit) {
                try { settingsFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        } else {
            LaunchedEffect(Unit) {
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }
}

@Composable
private fun SettingRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A35)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Text(text = text, color = if (isSelected) Color.White else Color.LightGray)
        }
    }
}