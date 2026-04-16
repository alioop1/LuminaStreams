@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.luminastreams.tv.presentation.home
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
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
    Text(time, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
fun LuminaLogo() {
    Image(painterResource(R.drawable.logo_lumina_unified), "Lumina Logo", contentScale = ContentScale.Fit, modifier = Modifier.height(64.dp))
}

@Composable
fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = NAV_GLASS, focusedContainerColor = Color(0x44FFFFFF), contentColor = DIM2, focusedContentColor = WHITE),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Color(0x25FFFFFF)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(border = BorderStroke(1.5.dp, Color(0x70FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(34.dp).width(260.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(13.dp))
            Text(tr("Search movies, shows...", "חיפוש סרטים וסדרות..."), fontSize = 12.sp)
        }
    }
}

@Composable
fun NavPill(label: String, icon: ImageVector, isSelected: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit, onTabPositioned: (Float, Dp) -> Unit) {
    val density = LocalDensity.current
    val contentColor = if (isSelected) Color(0xFF0C0C0C) else WHITE
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = NAV_FOCUS, pressedContainerColor = Color(0x20FFFFFF), contentColor = contentColor, focusedContentColor = contentColor),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(border = BorderStroke(1.5.dp, Color(0x66FFFFFF)), shape = RoundedCornerShape(50))
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = Modifier.height(NAV_PILL_H).wrapContentWidth().onGloballyPositioned { coords -> onTabPositioned(coords.positionInParent().x, with(density) { coords.size.width.toDp() }) }.let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        Box(Modifier.fillMaxHeight().wrapContentWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(14.dp))
                Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
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
    val pillSpec = if (isHighTier) tween<Float>(300, easing = FastOutSlowInEasing) else snap()
    val pillDpSpec = if (isHighTier) tween<Dp>(300, easing = FastOutSlowInEasing) else snap()
    val animatedX by animateFloatAsState(targetValue = targetX, animationSpec = pillSpec, label = "pillX")
    val animatedWidth by animateDpAsState(targetValue = targetWidth, animationSpec = pillDpSpec, label = "pillW")

    Column(modifier = modifier.onFocusChanged { if (it.hasFocus) onNavFocus() }) {
        Row(modifier = Modifier.fillMaxWidth().height(NAV_SEARCH_H).padding(horizontal = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            LuminaLogo(); Spacer(Modifier.weight(1f)); ClockText()
        }
        Spacer(Modifier.height(NAV_GAP))
        Box(modifier = Modifier.fillMaxWidth().height(NAV_PILLS_H).padding(horizontal = 52.dp), contentAlignment = AbsoluteAlignment.CenterLeft) {
            if (animatedWidth > 0.dp) {
                androidx.compose.material3.Surface(modifier = Modifier.width(animatedWidth).height(NAV_PILL_H).graphicsLayer { translationX = animatedX }, color = WHITE, shape = RoundedCornerShape(50)) {}
            }
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavPill(tr("Home", "ראשי"), Icons.Default.Home, activeTab == "ראשי", firstNavFR, onHomeTab) { o, w -> tabPositions["ראשי"] = o; tabWidths["ראשי"] = w }
                NavPill(tr("Movies", "סרטים"), Icons.Default.Movie, activeTab == "סרטים", null, onMoviesTab) { o, w -> tabPositions["סרטים"] = o; tabWidths["סרטים"] = w }
                NavPill(tr("TV Shows", "סדרות"), Icons.Default.Tv, activeTab == "סדרות", null, onSeriesTab) { o, w -> tabPositions["סדרות"] = o; tabWidths["סדרות"] = w }
                NavPill("Fuzer", Icons.Default.LocalMovies, activeTab == "Fuzer", null, onFuzer) { o, w -> tabPositions["Fuzer"] = o; tabWidths["Fuzer"] = w }
                NavPill(tr("Live TV", "טלוויזיה חיה"), Icons.Default.Cast, false, null, onIptv) { o, w -> tabPositions["iptv"] = o; tabWidths["iptv"] = w }
                NavPill(tr("Watchlist", "רשימת צפייה"), Icons.Default.Bookmark, false, null, onWatchlist) { o, w -> tabPositions["Watchlist"] = o; tabWidths["Watchlist"] = w }
                NavPill(tr("Settings", "הגדרות"), Icons.Default.Settings, false, null, onSettings) { o, w -> tabPositions["Settings"] = o; tabWidths["Settings"] = w }
                Spacer(Modifier.weight(1f))
                SearchBarButton(onClick = onSearch)
            }
        }
    }
}