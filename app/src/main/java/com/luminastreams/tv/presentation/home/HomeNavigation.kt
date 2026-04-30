@file:OptIn(ExperimentalTvMaterial3Api::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
        if (isFocused) {
            // FIXED: Added a small delay to ensure the screen has fully transitioned
            // back from the DetailsScreen before hijacking the focus engine!
            delay(100)
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    // In RTL: navbar on LEFT (opposite content side), clock on RIGHT
    // In LTR: navbar on RIGHT (opposite content side), clock on LEFT
    // We achieve this by putting clock FIRST and icons SECOND with SpaceBetween,
    // since Compose Row auto-reverses in RTL — this puts clock on right and icons on left in RTL.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 52.dp, end = 52.dp)
            .onFocusChanged { if (it.hasFocus) onNavFocus() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClockText()

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIconButton(Icons.Default.Search, false, navFocusRequester, onSearch)
            NavIconButton(Icons.Default.WorkspacePremium, false, null, {}) // Premium/Crown
            NavIconButton(Icons.Default.Tv, false, null, onIptv)
            NavIconButton(Icons.Default.Cast, false, null, {})
            NavIconButton(Icons.Default.Bookmark, false, null, onWatchlist)
            NavIconButton(Icons.Default.Settings, false, null, onSettings)
            
            // Profile Circle
            Surface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                modifier = Modifier.size(36.dp).padding(start = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_lumina_glow), // Placeholder for profile pic
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun NavTextTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            focusedContentColor = Color.White
        ),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (isSelected) {
                Box(Modifier.width(20.dp).height(2.dp).background(Color.Red, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
fun NavIconButton(icon: ImageVector, isSelected: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            focusedContentColor = Color.White
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        modifier = Modifier
            .size(38.dp)
            .let { if (fr != null) it.focusRequester(fr) else it }
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}