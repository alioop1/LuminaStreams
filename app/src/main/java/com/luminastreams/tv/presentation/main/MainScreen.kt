@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.usecase.GetMediaDetailsUseCase
import com.luminastreams.tv.presentation.details.DetailsEvent
import com.luminastreams.tv.presentation.details.DetailsScreen
import com.luminastreams.tv.presentation.details.DetailsViewModel
import com.luminastreams.tv.presentation.discovery.DiscoveryScreen
import com.luminastreams.tv.presentation.home.HomeScreen
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.presentation.player.PlayerScreen
import com.luminastreams.tv.presentation.search.SearchScreen
import com.luminastreams.tv.presentation.search.SearchViewModel
import com.luminastreams.tv.presentation.settings.SettingsScreen
import com.luminastreams.tv.presentation.settings.SettingsViewModel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// ============================================================================
// CORE APPLICATION SHELL
// ============================================================================

/**
 * The Master Shell of the Lumina Streams application.
 * * This component acts as the strict spatial root for the entire TV interface.
 * It utilizes a Row-based composite layout to guarantee that the Left Sidebar
 * is a permanent structural anchor. The main content (NavHost) fills the remaining
 * space perfectly, creating the exact layout seen in high-end dedicated media centers.
 *
 * @param viewModel The shared HomeViewModel containing the master state of the app.
 */
@Composable
fun MainApp(viewModel: HomeViewModel) {
    // Instantiate the master navigation controller for the application routing
    val navController = rememberNavController()

    // Check if the system is currently using a Right-To-Left language (like Hebrew).
    // This allows us to program the D-Pad to behave intuitively regardless of locale.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Create a FocusRequester to manually force the D-Pad focus back into the
    // main content area when the user closes or exits the sidebar.
    val contentFocusRequester = remember { FocusRequester() }

    // Use a Row as the absolute base to establish structural isolation
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // --- 1. THE STRUCTURAL ANCHOR SIDEBAR ---
        // This component handles its own expansion animations and traps focus
        // so the user cannot accidentally scroll off the screen.
        PremiumVideoSidebar(
            navController = navController,
            viewModel = viewModel,
            contentFocusRequester = contentFocusRequester,
            isRtl = isRtl
        )

        // --- 2. THE DYNAMIC CONTENT AREA ---
        // This box dynamically consumes exactly 100% of the remaining screen width
        // after the sidebar claims its space.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(contentFocusRequester)
                .focusGroup() // Isolate D-Pad events strictly to this box when active
                .focusRestorer() // Return focus to the last known poster automatically
        ) {
            AppNavHostContainer(
                navController = navController,
                homeViewModel = viewModel
            )
        }
    }
}

// ============================================================================
// PREMIUM SIDEBAR ARCHITECTURE (1:1 VIDEO CLONE)
// ============================================================================

/**
 * A highly stylized, structural sidebar designed to match premium TV skins.
 * It pins navigation, quick actions, and time data to the far left edge.
 * It idles as a tiny 24dp sliver to maximize movie art real estate, and expands
 * to a full 260dp menu only when focused.
 *
 * @param navController Used to route clicks to settings, search, etc.
 * @param viewModel Used to sync active tab states (Movies vs TV Shows).
 * @param contentFocusRequester Used to push focus back to the movie grid safely.
 * @param isRtl Indicates if the layout direction is right-to-left.
 */
@Composable
fun PremiumVideoSidebar(
    navController: NavHostController,
    viewModel: HomeViewModel,
    contentFocusRequester: FocusRequester,
    isRtl: Boolean
) {
    // Tracks if the user's D-Pad is currently resting inside the sidebar bounds
    var isSidebarFocused by remember { mutableStateOf(false) }

    // Hardware-accelerated width animation for buttery smooth opening/closing
    // Pushes the main content area smoothly to the right when expanding
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarFocused) 260.dp else 24.dp,
        animationSpec = tween(durationMillis = 300),
        label = "sidebarWidth"
    )

    // Watch the navigation stack to know which tab should be highlighted globally
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    // ========================================================================
    // TICKING CLOCK SYSTEM
    // ========================================================================
    var timeStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        // Exact Hebrew string formatting from the reference image
        val dateFmt = SimpleDateFormat("EEEE • d 'במרץ, 2026'", Locale("he", "IL"))
        while (true) {
            val d = Date()
            timeStr = timeFmt.format(d)
            dateStr = dateFmt.format(d)
            delay(60000) // 1 minute updates to save TV memory
        }
    }

    // ========================================================================
    // SIDEBAR RENDERING
    // ========================================================================
    Box(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(Color(0xFF0A0A0A)) // Solid deep charcoal black (No transparency)
            .border(width = 0.5.dp, color = Color(0x33FFFFFF), shape = RoundedCornerShape(0.dp))
            .focusGroup()
            .focusProperties {
                // Trap focus: if they press Right (or Left in RTL), force it to content
                if (isRtl) left = contentFocusRequester else right = contentFocusRequester
            }
            .onFocusChanged { isSidebarFocused = it.hasFocus }
    ) {
        // Only render the internal components if the sidebar is expanded
        // This prevents text from bleeding out when it is collapsed to 24dp
        if (isSidebarFocused) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {

                // --- TOP: QUICK ACTION ICONS ---
                // Distinct circular dark-red/brown buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SidebarQuickActionCircle(icon = Icons.AutoMirrored.Filled.ExitToApp) { /* App Exit */ }
                    SidebarQuickActionCircle(icon = Icons.Default.Settings) { navController.navigate("settings") }
                    SidebarQuickActionCircle(icon = Icons.Default.Search) { navController.navigate("search") }
                }

                // --- MIDDLE: TEXT MENU LIST ---
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val selectedTab = viewModel.state.collectAsState().value.selectedTab

                    SidebarTextMenuItem(
                        title = "סרטים",
                        isSelected = currentRoute == "home" && selectedTab == "סרטים"
                    ) {
                        navController.navigate("home")
                        viewModel.selectTab("סרטים")
                    }

                    SidebarTextMenuItem(
                        title = "סדרות",
                        isSelected = currentRoute == "home" && selectedTab == "סדרות"
                    ) {
                        navController.navigate("home")
                        viewModel.selectTab("סדרות")
                    }

                    // Display-only items to match the premium video aesthetic exactly
                    listOf("רשתות", "אנימה", "ספורט חי", "טלוויזיה חיה", "תוספים", "מועדפים")
                        .forEach { title ->
                            SidebarTextMenuItem(title = title, isSelected = false) {}
                        }
                }

                // --- BOTTOM: CLOCK & DATE ---
                Column {
                    Text(
                        text = timeStr,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = dateStr,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// SIDEBAR SUB-COMPONENTS
// ============================================================================

/**
 * Custom dark-red/brown circular quick action button.
 * Uses a double-border effect: a dark base border that turns into a thick
 * bright white glow when the D-pad lands on it.
 */
@Composable
fun SidebarQuickActionCircle(icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A0A0A), // Resting dark red/brown
            focusedContainerColor = Color(0xFF5A1010) // Hover bright red
        ),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier
            .size(42.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF7A1515),
                shape = CircleShape
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Pure text menu item representing the primary navigation tabs.
 * Focus or Selection sets the color to pure white and changes the font
 * weight to ExtraBold. Unfocused remains a dim grey.
 */
@Composable
fun SidebarTextMenuItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = if (isFocused || isSelected) Color.White else Color.Gray,
            fontSize = 20.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}

// ============================================================================
// NAVIGATION ROUTING SYSTEM
// ============================================================================

/**
 * The central routing engine mapping string paths to Composable screens.
 * This function injects the required controllers and states into each child screen.
 *
 * @param navController The master controller for traversing the application.
 * @param homeViewModel The pre-instantiated root state holder.
 */
@Composable
fun AppNavHostContainer(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    NavHost(navController = navController, startDestination = "home") {

        // --- 1. THE MAIN HOME SCREEN ---
        composable("home") {
            val state by homeViewModel.state.collectAsState()

            HomeScreen(
                state = state,
                viewModel = homeViewModel,
                navController = navController,
                onMovieClick = { movieId ->
                    navController.navigate("details/$movieId")
                }
            )
        }

        // --- 2. THE MOVIE DISCOVERY SCREEN ---
        composable("movies") {
            val state by homeViewModel.state.collectAsState()
            DiscoveryScreen(
                state = state,
                viewModel = homeViewModel,
                mediaType = "movie",
                onMovieClick = { movieId ->
                    navController.navigate("details/$movieId")
                }
            )
        }

        // --- 3. THE TV SHOWS DISCOVERY SCREEN ---
        composable("series") {
            val state by homeViewModel.state.collectAsState()
            DiscoveryScreen(
                state = state,
                viewModel = homeViewModel,
                mediaType = "tv",
                onMovieClick = { movieId ->
                    navController.navigate("details/$movieId")
                }
            )
        }

        // --- 4. THE SEARCH ROUTE ---
        composable("search") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search Functionality Architecture Coming Soon...", color = Color.White, fontSize = 24.sp)
            }
        }

        // --- 5. THE SETTINGS ROUTE ---
        composable("settings") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Settings Sub-System Architecture Coming Soon...", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}