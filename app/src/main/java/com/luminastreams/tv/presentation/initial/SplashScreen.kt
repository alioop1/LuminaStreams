package com.luminastreams.tv.presentation.initial

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.MainActivity
import kotlinx.coroutines.delay

@Composable
fun SmartProgressBar(modifier: Modifier = Modifier) {
    val barGradient = remember {

        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF000000),
                Color(0xFF666666),
                Color(0xFFFF3300)
            )
        )
    }

    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3200, easing = FastOutSlowInEasing)
        )
    }

    // ⚡ FIX 1: Derived State
    val percent by remember { derivedStateOf { (progressAnim.value * 100).toInt() } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LOADING $percent%",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ⚡ FIX 2: Draw-Phase Rendering
        Spacer(
            modifier = Modifier
                .width(280.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A1A1A))
                .drawWithContent {
                    drawContent() // Draw the background track
                    drawRect(
                        brush = barGradient,
                        size = size.copy(width = size.width * progressAnim.value) // Draw the fill
                    )
                }
        )
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // ⚡ FIX 3: Deferred State Hoisting
    // Instead of reading the '.value' directly in the UI tree, we keep it as a State object.
    val alphaState = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
        remember { mutableStateOf(1.0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue  = 0.6f,
            targetValue   = 1.0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.soundManager?.playSplash()
        delay(3500)
        onTimeout()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter            = painterResource(id = R.drawable.logo_lumina_glow),
            contentDescription = "Lumina Logo Background",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                // The actual value is read strictly inside the graphics layer block
                .graphicsLayer { this.alpha = alphaState.value }
        )
        SmartProgressBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}