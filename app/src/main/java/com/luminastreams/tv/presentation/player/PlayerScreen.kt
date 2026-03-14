@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.tv.material3.*
import kotlinx.coroutines.delay

val CustomPauseIcon: ImageVector
    get() = ImageVector.Builder("CustomPause", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f); lineTo(10f, 19f); lineTo(10f, 5f); lineTo(6f, 5f); close()
            moveTo(14f, 19f); lineTo(18f, 19f); lineTo(18f, 5f); lineTo(14f, 5f); close()
        }
    }.build()

val CustomSubtitlesIcon: ImageVector
    get() = ImageVector.Builder("CustomSubtitles", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
            moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 14f); lineTo(4f, 14f); close()
            moveTo(10f, 12f); lineTo(20f, 12f); lineTo(20f, 14f); lineTo(10f, 14f); close()
            moveTo(14f, 16f); lineTo(20f, 16f); lineTo(20f, 18f); lineTo(14f, 18f); close()
            moveTo(4f, 16f); lineTo(12f, 16f); lineTo(12f, 18f); lineTo(4f, 18f); close()
        }
    }.build()

val CustomAudioIcon: ImageVector
    get() = ImageVector.Builder("CustomAudio", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f); lineTo(12f, 14.5f)
            curveTo(11.6f, 14.2f, 11.1f, 14f, 10.5f, 14f)
            curveTo(9.1f, 14f, 8f, 15.1f, 8f, 16.5f)
            curveTo(8f, 17.9f, 9.1f, 19f, 10.5f, 19f)
            curveTo(11.9f, 19f, 13f, 17.9f, 13f, 16.5f)
            lineTo(13f, 6f); lineTo(17f, 6f); lineTo(17f, 3f); lineTo(12f, 3f); close()
        }
    }.build()

@Composable
fun PlayerScreen(
    videoUrl: String,
    imdbId: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val state by viewModel.state.collectAsState()
    val exoWrapper = remember { ExoPlayerWrapper(context) }
    val isPlaying by exoWrapper.isPlaying.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var showWebSubMenu by remember { mutableStateOf(false) }
    var userActivityTick by remember { mutableStateOf(0) }

    LaunchedEffect(videoUrl, imdbId) {
        viewModel.loadMedia(videoUrl, imdbId)
        exoWrapper.prepareStream(videoUrl)
    }

    LaunchedEffect(state.availableSubtitles) {
        val hebrewSub = state.availableSubtitles.firstOrNull { it.lang.contains("heb", true) }
        if (hebrewSub != null) {
            exoWrapper.applySubtitle(hebrewSub.url, hebrewSub.lang)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoWrapper.release() }
    }

    LaunchedEffect(showControls, isPlaying, userActivityTick) {
        if (showControls && isPlaying && !showWebSubMenu) {
            delay(5000)
            showControls = false
        }
    }

    BackHandler {
        if (showWebSubMenu) {
            showWebSubMenu = false
        } else if (showControls) {
            showControls = false
        } else {
            exoWrapper.pause()
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && !showWebSubMenu) {
                    userActivityTick++
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (!showControls) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (isPlaying) exoWrapper.pause() else exoWrapper.play()
                                showControls = true
                                true
                            }
                            else -> false
                        }
                    } else false
                } else false
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Transparent),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoWrapper.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                    // FIX 3: Keep the PlayerView transparent so the hardware surface below it shows through
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    keepScreenOn = true
                }
            },
            // Keep this! This prevents the GPU buffer dump when Compose draws subtitles.
            update = { playerView ->
                if (playerView.player != exoWrapper.player) {
                    playerView.player = exoWrapper.player
                }
            }
        )

        AnimatedVisibility(
            visible = showControls, enter = fadeIn(tween(200)), exit = fadeOut(tween(300)), modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                Icon(
                    imageVector = if (isPlaying) CustomPauseIcon else Icons.Default.PlayArrow,
                    contentDescription = null, tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(96.dp).padding(16.dp)
                )

                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 48.dp)) {
                    PlayerProgressControls(exoWrapper = exoWrapper, isPlaying = isPlaying, isRtl = isRtl)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PlayerPillButton(icon = CustomAudioIcon, text = if(isRtl) "שמע" else "Audio") {
                                val dialogContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
                                TrackSelectionDialogBuilder(dialogContext, if(isRtl) "בחר שפת שמע" else "Select Audio Track", exoWrapper.player, C.TRACK_TYPE_AUDIO).build().show()
                                userActivityTick++
                            }
                            PlayerPillButton(icon = CustomSubtitlesIcon, text = if(isRtl) "כתוביות פנימיות" else "Local Subs") {
                                val dialogContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
                                TrackSelectionDialogBuilder(dialogContext, if(isRtl) "בחר כתוביות" else "Select Subtitles", exoWrapper.player, C.TRACK_TYPE_TEXT).build().show()
                                userActivityTick++
                            }
                            PlayerPillButton(icon = Icons.Default.Search, text = if(state.isSubtitlesLoading) (if(isRtl) "מחפש..." else "Searching...") else (if(isRtl) "כתוביות רשת" else "Web Subs")) {
                                if (!state.isSubtitlesLoading && state.availableSubtitles.isNotEmpty()) {
                                    showWebSubMenu = true
                                } else if (!state.isSubtitlesLoading) {
                                    Toast.makeText(context, if(isRtl) "לא נמצאו כתוביות ברשת" else "No web subtitles found", Toast.LENGTH_SHORT).show()
                                }
                                userActivityTick++
                            }
                        }
                        PlayerPillButton(icon = Icons.Default.Close, text = if(isRtl) "יציאה" else "Exit") {
                            exoWrapper.pause()
                            onNavigateBack()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showWebSubMenu, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { showWebSubMenu = false }, contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.width(400.dp).background(Color(0xFF141414), RoundedCornerShape(16.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if(isRtl) "בחר כתובית" else "Select Subtitle", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.availableSubtitles) { sub ->
                            Button(
                                onClick = {
                                    exoWrapper.applySubtitle(sub.url, sub.lang)
                                    showWebSubMenu = false
                                },
                                colors = ButtonDefaults.colors(containerColor = Color.DarkGray, focusedContainerColor = Color(0xFFE50914), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text("${sub.lang.uppercase()} • ${sub.source}", fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerProgressControls(exoWrapper: ExoPlayerWrapper, isPlaying: Boolean, isRtl: Boolean) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(1L) }

    val seekBarFocusRequester = remember { FocusRequester() }
    var isSeekBarFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoWrapper.player.currentPosition
            videoDuration = exoWrapper.player.duration.coerceAtLeast(1L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        try { seekBarFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(currentPosition), color = Color.White, fontWeight = FontWeight.Bold)
        Text(formatTime(videoDuration), color = Color.White, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(8.dp))

    val seekHeight by animateDpAsState(if (isSeekBarFocused) 16.dp else 6.dp, label = "seek_height")
    val progress = (currentPosition.toFloat() / videoDuration.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(seekBarFocusRequester)
            .onFocusChanged { isSeekBarFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        val seekDir = if (isRtl) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) -10000L else 10000L
                        } else {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 10000L else -10000L
                        }
                        val newPosition = (exoWrapper.player.currentPosition + seekDir).coerceIn(0L, videoDuration)
                        exoWrapper.seekTo(newPosition)
                        currentPosition = newPosition
                        return@onKeyEvent true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || KeyEvent.KEYCODE_ENTER == keyCode) {
                        if (isPlaying) exoWrapper.pause() else exoWrapper.play()
                        return@onKeyEvent true
                    }
                }
                false
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(seekHeight).clip(RoundedCornerShape(50)).background(if (isSeekBarFocused) Color.Gray else Color.DarkGray.copy(alpha = 0.5f)))
        Box(modifier = Modifier.fillMaxWidth(progress).height(seekHeight).clip(RoundedCornerShape(50)).background(if (isSeekBarFocused) Color.White else Color(0xFFE50914)))
    }
}

@Composable
fun PlayerPillButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Color(0x66000000), focusedContainerColor = Color(0xFFE50914),
            contentColor = Color.White, focusedContentColor = Color.White
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.05f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

