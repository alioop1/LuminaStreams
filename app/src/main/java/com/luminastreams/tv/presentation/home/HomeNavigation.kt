@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.luminastreams.tv.core.tr
import kotlinx.coroutines.delay

@Composable
fun ClockText() {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = java.util.Calendar.getInstance()
            time = "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Text(
        time,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
fun Ps5TopNav(
    isFocused: Boolean,
    onNavFocus: () -> Unit,
    onSearch: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
    onIptv: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onHome: () -> Unit,
    onFuzer: () -> Unit,
    onDebrid: () -> Unit,
    onProfileClick: () -> Unit,
    selectedTab: String
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val navFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(100)
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp) // Explicit height for navbar
            .padding(top = 24.dp, start = 52.dp, end = 52.dp)
            .onFocusChanged { if (it.hasFocus) onNavFocus() }
    ) {
        // Logo
        Box(modifier = Modifier.align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LUMINA ",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "STREAMS",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Clock
        Box(modifier = Modifier.align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)) {
            ClockText()
        }

        // Nav Items (Centered)
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .focusProperties { canFocus = isFocused },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIconButton(Icons.Default.Search, false, navFocusRequester, onSearch)
            NavIconButton(Icons.Default.Home, selectedTab == "home", null, onHome)
            NavIconButton(Icons.Default.Movie, selectedTab == "movies", null, onMovies)
            NavIconButton(Icons.Default.LiveTv, selectedTab == "series", null, onSeries)
            NavIconButton(Icons.Default.AutoAwesome, selectedTab == "Fuzer", null, onFuzer)
            NavIconButton(Icons.Default.Tv, false, null, onIptv)
            NavIconButton(Icons.Default.Cloud, false, null, onDebrid)
            NavIconButton(Icons.Default.Favorite, false, null, onWatchlist)
            NavIconButton(Icons.Default.Settings, false, null, onSettings)
            NavIconButton(Icons.Default.AccountCircle, false, null, onProfileClick)
        }
    }
}

@Composable
fun NavIconButton(icon: ImageVector, isSelected: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val glowAlpha by animateFloatAsState(if (isFocused) 0.6f else if (isSelected) 0.3f else 0f, tween(300))

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.4f),
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        modifier = Modifier
            .size(48.dp)
            .let { if (fr != null) it.focusRequester(fr) else it }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            // Glow Effect
            if (glowAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = CircleShape,
                            ambientColor = Color.White.copy(alpha = glowAlpha),
                            spotColor = Color.White.copy(alpha = glowAlpha)
                        )
                )
            }

            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}