package com.luminastreams.tv.presentation.iptv

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object IptvTheme {
    val BG       = Color(0xFF07070A)
    val SURFACE  = Color(0xFF12121C)
    val SURFACE2 = Color(0xFF1C1C2A)
    val ACCENT   = Color(0xFF3D8BFF)
    val ACCENT2  = Color(0xFF00D4FF)
    val RED      = Color(0xFFFF3B30)
    val WHITE    = Color(0xFFFFFFFF)
    val MUTED    = Color(0x99FFFFFF)
    val MUTED2   = Color(0x33FFFFFF)
    val HUD_BG   = Color(0xF00A0A12)
    val CARD_BG  = Color(0xFF16161F)
    val GREEN    = Color(0xFF30D158)

    val CardGradientNormal  = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.72f)))
    val CardGradientFocused = Brush.verticalGradient(listOf(Color.Transparent, BG.copy(0.72f)))
    val ZappingGradientNorm = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
    val ZappingHudBg        = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f), Color.Black.copy(0.96f)))
    val TopBarGradient      = Brush.verticalGradient(listOf(Color.Black.copy(0.92f), Color.Black.copy(0.55f), Color.Transparent))
    val ProgBarBrush        = Brush.horizontalGradient(listOf(ACCENT, ACCENT2))
    val ZappingDividerBrush = Brush.horizontalGradient(listOf(Color.Transparent, ACCENT.copy(0.35f), ACCENT.copy(0.5f), ACCENT.copy(0.35f), Color.Transparent))
    val ZappingLogoBgBrush  = Brush.linearGradient(listOf(Color.White.copy(0.10f), Color.White.copy(0.04f)))
    val PQSBgBrushLtr       = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xF0080810)))
    val PQSBgBrushRtl       = Brush.horizontalGradient(listOf(Color(0xF0080810), Color.Transparent))
}