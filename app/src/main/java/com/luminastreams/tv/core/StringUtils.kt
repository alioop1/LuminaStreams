package com.luminastreams.tv.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Locale-aware string selector — returns Hebrew text for RTL layouts, English for LTR.
 * Single source of truth for the entire app.
 */
@Composable
fun tr(en: String, he: String): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en
