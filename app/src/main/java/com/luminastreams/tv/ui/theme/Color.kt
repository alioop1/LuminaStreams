package com.luminastreams.tv.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════
//  LUMINA STREAMS — MODERN PREMIUM PALETTE v2
//  Deep Space Blue-Black + Neon Cyan + Electric Purple
// ══════════════════════════════════════════════════════════════

// ── Core Background ───────────────────────────────────────────────
// Deep Space Blue-Black — warmer and more cinematic than pure #000
val LuminaBlackBg   = Color(0xFF070B14)
val LuminaNavy      = Color(0xFF0D1525)
val LuminaNavy2     = Color(0xFF0F1A2E)
val DarkBackground  = LuminaBlackBg    // compat alias for Theme.kt

// ── Primary Accents ───────────────────────────────────────────────
val LuminaCyan      = Color(0xFF00D4FF) // Neon Cyan — primary accent
val LuminaCyanDark  = Color(0xFF00B4D8) // Darker Cyan for focus/hover
val LuminaPurple    = Color(0xFF7B61FF) // Electric Purple — secondary
val LuminaGreen     = Color(0xFF00FF88) // Vivid Green for LIVE badge
val LuminaRed       = Color(0xFFFF3B5C) // Vibrant Crimson for errors

// ── Legacy compat aliases ─────────────────────────────────────────
val NetflixRed      = LuminaRed
val OledBlack       = LuminaBlackBg

// ── Typography ──────────────────────────────────────────────────
val TextPrimary     = Color(0xFFEEF2FF) // Slightly blue-white
val TextSecondary   = Color(0xFFBBC8E8) // Muted blue-white
val TextMuted       = Color(0xFF7A8AB5) // Faded slate

// ── Glass / Overlay ──────────────────────────────────────────────
val GlassBackground   = Color(0x990D1525) // Navy glass
val GlassBorder       = Color(0x2200D4FF) // Cyan-tinted border
val GlassHighlight    = Color(0x1500D4FF) // Subtle cyan glow
val GlassWhiteBtn     = Color(0x22FFFFFF)
val GlassWhiteBorder  = Color(0x33FFFFFF)

// ── Status / Badge ──────────────────────────────────────────────
val MatchGreen      = LuminaGreen
val BadgeBorder     = TextSecondary
