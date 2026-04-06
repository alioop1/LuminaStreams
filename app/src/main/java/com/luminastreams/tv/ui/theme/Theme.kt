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
    background      = LuminaBlackBg,
    surface         = LuminaNavy,
    primary         = LuminaCyan,
    secondary       = LuminaPurple,
    onPrimary       = LuminaBlackBg,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    surfaceVariant  = GlassBackground,
    error           = LuminaRed,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LuminaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuminaColorScheme,
    ) {
        // Transparent Box so 4K HDR video can punch through the hardware layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            content()
        }
    }
}
