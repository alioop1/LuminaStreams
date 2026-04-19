@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
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
    Text(time, color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun LuminaLogo() {
    val logoPainter = painterResource(R.drawable.logo_lumina_unified)
    Image(
        painter = logoPainter,
        contentDescription = "Lumina Logo",
        contentScale = ContentScale.Fit,
        // הגובה הוגדל ל-90 כדי שייראה גדול ומרשים, השארנו את הריווח מלמעלה
        modifier = Modifier.height(90.dp).padding(top = 4.dp)
    )
}

@Composable
fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF), focusedContainerColor = Color.White, contentColor = WHITE, focusedContentColor = Color.Black),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color.Transparent), shape = RoundedCornerShape(50)),
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50))
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(38.dp).width(160.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Search, null, Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(tr("Search", "חיפוש"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NavPill(label: String, icon: ImageVector, isSelected: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit, onTabPositioned: (Float, Dp) -> Unit) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "pressScale"
    )

    val contentColor = if (isSelected) RED else WHITE.copy(alpha = 0.7f)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color(0x20FFFFFF),
            contentColor = contentColor,
            focusedContentColor = RED
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .height(38.dp)
            .wrapContentWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .onGloballyPositioned { coords ->
                onTabPositioned(coords.positionInParent().x, with(density) { coords.size.width.toDp() })
            }
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        Box(Modifier.fillMaxHeight().wrapContentWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(16.dp))
                Text(label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun TwoRowNavBar(activeTab: String, firstNavFR: FocusRequester, onSearch: () -> Unit, onHomeTab: () -> Unit, onMoviesTab: () -> Unit, onSeriesTab: () -> Unit, onFuzer: () -> Unit, onWatchlist: () -> Unit, onSettings: () -> Unit, onIptv: () -> Unit, onNavFocus: () -> Unit, modifier: Modifier = Modifier) {
    val tabPositions = remember { mutableStateMapOf<String, Float>() }
    val tabWidths = remember { mutableStateMapOf<String, Dp>() }
    val targetX = tabPositions[activeTab] ?: 0f
    val targetWidth = tabWidths[activeTab] ?: 0.dp
    val isHighTier = DeviceProfile.tier == DeviceProfile.Tier.HIGH

    val pillSpec = if (isHighTier) spring<Float>(dampingRatio = 0.65f, stiffness = 150f) else snap()
    val pillDpSpec = if (isHighTier) spring<Dp>(dampingRatio = 0.65f, stiffness = 150f) else snap()
    val animatedX by animateFloatAsState(targetValue = targetX, animationSpec = pillSpec, label = "pillX")
    val animatedWidth by animateDpAsState(targetValue = targetWidth, animationSpec = pillDpSpec, label = "pillW")

    Box(modifier = modifier.fillMaxWidth().padding(top = 32.dp).onFocusChanged { if (it.hasFocus) onNavFocus() }) {
        Box(Modifier.align(Alignment.TopStart).padding(start = 52.dp)) { LuminaLogo() }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 52.dp, top = 16.dp)) { ClockText() }

        Box(
            Modifier.align(Alignment.TopCenter)
                .background(Color(0x66000000), RoundedCornerShape(50))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(8.dp)
        ) {
            Box {
                if (animatedWidth > 0.dp) {
                    androidx.compose.material3.Surface(modifier = Modifier.width(animatedWidth).height(38.dp).graphicsLayer { translationX = animatedX }, color = WHITE, shape = RoundedCornerShape(50)) {}
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NavPill(tr("Home", "ראשי"), Icons.Default.Home, activeTab == "ראשי", firstNavFR, onHomeTab) { o, w -> tabPositions["ראשי"] = o; tabWidths["ראשי"] = w }
                    NavPill(tr("Movies", "סרטים"), Icons.Default.Movie, activeTab == "סרטים", null, onMoviesTab) { o, w -> tabPositions["סרטים"] = o; tabWidths["סרטים"] = w }
                    NavPill(tr("TV Shows", "סדרות"), Icons.Default.Tv, activeTab == "סדרות", null, onSeriesTab) { o, w -> tabPositions["סדרות"] = o; tabWidths["סדרות"] = w }
                    NavPill("Fuzer", Icons.Default.LocalMovies, activeTab == "Fuzer", null, onFuzer) { o, w -> tabPositions["Fuzer"] = o; tabWidths["Fuzer"] = w }
                    NavPill(tr("Live TV", "טלוויזיה חיה"), Icons.Default.Cast, activeTab == "iptv", null, onIptv) { o, w -> tabPositions["iptv"] = o; tabWidths["iptv"] = w }
                    NavPill(tr("Watchlist", "רשימה"), Icons.Default.Bookmark, activeTab == "Watchlist", null, onWatchlist) { o, w -> tabPositions["Watchlist"] = o; tabWidths["Watchlist"] = w }
                    NavPill(tr("Settings", "הגדרות"), Icons.Default.Settings, activeTab == "Settings", null, onSettings) { o, w -> tabPositions["Settings"] = o; tabWidths["Settings"] = w }
                    Spacer(Modifier.width(16.dp))
                    SearchBarButton(onClick = onSearch)
                }
            }
        }
    }
}