package com.luminastreams.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val LuminaColorScheme = darkColorScheme(
    background = OledBlack,
    surface = DarkBackground,
    primary = NetflixRed,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = GlassBackground
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LuminaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuminaColorScheme,
    ) {
        // We use a transparent Box instead of Surface so the 4K HDR video
        // can punch through from the hardware layer underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            content()
        }
    }
}