@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.main

// ════════════════════════════════════════════════════════════════════════════
// MainScreen.kt — legacy shell, kept for sidebar component only.
// All home-screen composables (HomeScreen, ArvioCard, NfCard, HomeLoading,
// HomeError, NfLoadingSkeleton, NfErrorScreen, LuminaSidebar, NfSidebar)
// now live exclusively in HomeScreen.kt to avoid "Conflicting overloads".
// ════════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.luminastreams.tv.presentation.home.HomeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── PremiumVideoSidebar ───────────────────────────────────────────────────────
@Composable
fun PremiumVideoSidebar(
    navController: NavHostController,
    viewModel: HomeViewModel,
    contentFocusRequester: FocusRequester,
    isRtl: Boolean
) {
    var isSidebarFocused by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue    = if (isSidebarFocused) 260.dp else 24.dp,
        animationSpec  = tween(300),
        label          = "sidebarWidth"
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val dateFmt = SimpleDateFormat("EEEE • d 'במרץ, 2026'", Locale("he", "IL"))
        while (true) {
            val d = Date(); timeStr = timeFmt.format(d); dateStr = dateFmt.format(d)
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    Box(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(Color(0xFF0A0A0A))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(0.dp))
            .focusGroup()
            .focusProperties {
                if (isRtl) left = contentFocusRequester else right = contentFocusRequester
            }
            .onFocusChanged { isSidebarFocused = it.hasFocus }
    ) {
        if (isSidebarFocused) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
                verticalArrangement   = Arrangement.SpaceBetween,
                horizontalAlignment   = Alignment.Start
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SidebarQuickActionCircle(Icons.AutoMirrored.Filled.ExitToApp) {}
                    SidebarQuickActionCircle(Icons.Default.Settings) { navController.navigate("settings") }
                    SidebarQuickActionCircle(Icons.Default.Search)   { navController.navigate("search") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val selectedTab = viewModel.state.collectAsState().value.selectedTab
                    SidebarTextMenuItem("סרטים",  currentRoute == "home" && selectedTab == "סרטים") {
                        navController.navigate("home"); viewModel.selectTab("סרטים")
                    }
                    SidebarTextMenuItem("סדרות",  currentRoute == "home" && selectedTab == "סדרות") {
                        navController.navigate("home"); viewModel.selectTab("סדרות")
                    }
                    listOf("רשתות", "אנימה", "ספורט חי", "טלוויזיה חיה", "תוספים", "מועדפים")
                        .forEach { SidebarTextMenuItem(it, false) {} }
                }
                Column {
                    Text(timeStr, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text(dateStr, color = Color.Gray,  fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun SidebarQuickActionCircle(icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors  = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A0A0A), focusedContainerColor = Color(0xFF5A1010)),
        shape   = ClickableSurfaceDefaults.shape(CircleShape),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.size(42.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) Color.White else Color(0xFF7A1515), CircleShape)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SidebarTextMenuItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text       = title,
            color      = if (isFocused || isSelected) Color.White else Color.Gray,
            fontSize   = 20.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            letterSpacing = 0.5.sp,
            modifier   = Modifier.padding(vertical = 4.dp).onFocusChanged { isFocused = it.isFocused }
        )
    }
}