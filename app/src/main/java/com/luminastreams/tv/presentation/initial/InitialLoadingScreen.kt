package com.luminastreams.tv.presentation.initial

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminastreams.tv.R
import com.luminastreams.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay

@Composable
fun InitialLoadingScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteTransitionSpec(),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(3500)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = com.luminastreams.tv.R.drawable.logo_lumina_glow),
                contentDescription = "Lumina Logo",
                modifier = Modifier.size(240.dp).alpha(alpha).padding(32.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("LUMINA STREAMS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(32.dp))
            LoadingIndicator()
        }
    }
}

@Composable
private fun infiniteTransitionSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(1200, easing = LinearEasing),
    repeatMode = RepeatMode.Reverse
)