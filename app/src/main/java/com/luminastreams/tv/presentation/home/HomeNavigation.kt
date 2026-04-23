@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.luminastreams.tv.R
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
    Text(time, color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun Ps5TopNav(
    activeTab: String,
    isFocused: Boolean,
    onNavFocus: () -> Unit,
    onSearch: () -> Unit,
    onHomeTab: () -> Unit,
    onMoviesTab: () -> Unit,
    onSeriesTab: () -> Unit,
    onFuzer: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit,
    onIptv: () -> Unit
) {
    val navFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) runCatching { navFocusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 52.dp, end = 52.dp)
            .onFocusChanged { if (it.hasFocus) onNavFocus() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo_lumina_unified),
                contentDescription = "Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(48.dp).padding(end = 24.dp)
            )

            // Minimalist Icon Buttons
            NavIconButton(Icons.Default.Search, false, navFocusRequester, onSearch)
            NavIconButton(Icons.Default.Home, activeTab == "ראשי", null, onHomeTab)
            NavIconButton(Icons.Default.Movie, activeTab == "סרטים", null, onMoviesTab)
            NavIconButton(Icons.Default.Tv, activeTab == "סדרות", null, onSeriesTab)
            NavIconButton(Icons.Default.LocalMovies, activeTab == "Fuzer", null, onFuzer)
            NavIconButton(Icons.Default.Cast, activeTab == "iptv", null, onIptv)
            NavIconButton(Icons.Default.Bookmark, activeTab == "Watchlist", null, onWatchlist)
            NavIconButton(Icons.Default.Settings, activeTab == "Settings", null, onSettings)
        }

        ClockText()
    }
}

@Composable
fun NavIconButton(icon: ImageVector, isSelected: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedScale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, animationSpec = tween(150), label = "navScale")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            focusedContentColor = Color.Black
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
            .let { if (fr != null) it.focusRequester(fr) else it }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}