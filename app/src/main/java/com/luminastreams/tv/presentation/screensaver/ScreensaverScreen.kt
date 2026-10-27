package com.luminastreams.tv.presentation.screensaver

import android.widget.TextClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ScreensaverScreen(viewModel: ScreensaverViewModel) {
    val backdrops by viewModel.backdrops.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    
    val infiniteTransition = rememberInfiniteTransition(label = "drift")
    val offsetX = infiniteTransition.animateFloat(
        initialValue = -50f, targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "driftX"
    )
    val offsetY = infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 45000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "driftY"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Guard against IndexOutOfBoundsException: currentIndex can lag behind
        // when the backdrop list shrinks (e.g. new shuffle produced fewer items).
        val currentItem = backdrops.getOrNull(currentIndex.coerceIn(0, (backdrops.size - 1).coerceAtLeast(0)))
        if (currentItem != null) {
            
            AnimatedContent(
                targetState = currentItem.id, // Only transition when the background changes
                transitionSpec = {
                    // Dip to black: old screen fades out fast, new screen waits then fades in
                    fadeIn(animationSpec = tween(2000, delayMillis = 1000)) togetherWith fadeOut(animationSpec = tween(1500))
                },
                label = "ScreensaverCrossfade"
            ) { id ->
                // Read the state INSIDE the lambda so it recomposes when logoUrl updates
                val allBackdrops by viewModel.backdrops.collectAsStateWithLifecycle()
                val item = allBackdrops.find { it.id == id }
                
                if (item != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Background Image
                        AsyncImage(
                            model = item.backdropUrl,
                            contentDescription = "Screensaver Background",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // 2. OLED True Black Vignette overlay (Vertical for top/bottom text)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.7f), // Dark at top for clock
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.9f)  // Dark at bottom for logo
                                        )
                                    )
                                )
                        )

                        // 3. Time widget (Top Center) - pure Compose for perfect fade
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var timeText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            while(true) {
                                val is24 = android.text.format.DateFormat.is24HourFormat(context)
                                val format = if (is24) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) 
                                             else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                timeText = format.format(java.util.Date())
                                kotlinx.coroutines.delay(1000)
                            }
                        }
                        Text(
                            text = timeText,
                            color = Color(0xFFE0E0E0),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Light,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black, 
                                    offset = androidx.compose.ui.geometry.Offset(0f, 12f), 
                                    blurRadius = 24f
                                )
                            ),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    translationX = offsetX.value
                                    translationY = offsetY.value
                                }
                                .padding(top = 48.dp)
                        )

                        // 4. Logo (Bottom Center)
                        if (item.logoUrl != null) {
                            AsyncImage(
                                model = item.logoUrl,
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .graphicsLayer {
                                        translationX = offsetX.value
                                        translationY = offsetY.value
                                    }
                                    .padding(bottom = 48.dp)
                                    .widthIn(max = 800.dp) // Doubled size
                                    .heightIn(max = 400.dp), // Doubled size
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
