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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.delay

@Composable
fun SmartProgressBar(modifier: Modifier = Modifier) {
    val barGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x00E2E2E2), // Transparent Pearl Gray
                Color(0xFFB0B5B9), // Solid Pearl Gray
                Color(0xFFFF3B30), // Fire Red
                Color(0xFFFF8A66)  // Bright Pearl Fire Red edge
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "L O A D I N G",
            color = Color(0x88FFFFFF),
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            letterSpacing = 8.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ⚡ Premium ultra-thin laser track
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(2.dp)
                .background(Color(0x1AFFFFFF), RoundedCornerShape(50))
                .drawWithContent {
                    drawContent() // Draw the dark track
                    val currentWidth = size.width * progressAnim.value
                    
                    if (currentWidth > 0) {
                        // 1. Draw the gradient fill
                        drawRect(
                            brush = barGradient,
                            size = size.copy(width = currentWidth)
                        )
                        
                        // 2. Draw outer glow dot
                        drawCircle(
                            color = Color(0x66FF3B30), // Fire Red glow
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x = currentWidth, y = size.height / 2f)
                        )
                        
                        // 3. Draw solid white core dot at the leading edge
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x = currentWidth, y = size.height / 2f)
                        )
                    }
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