@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.luminastreams.tv.presentation.home.HomeScreen
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.ui.theme.LuminaTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        setContent {
            LuminaTheme {
                LuminaAppShell()
            }
        }
    }
}

@Composable
fun LuminaAppShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val repository = remember { MediaRepositoryImpl() }
    val homeViewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
        }
    )

    var isSidebarOpen by remember { mutableStateOf(false) }
    val contentFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // --- תוכן (100% רוחב) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocusRequester)
                    .focusGroup()
            ) {
                // באמפר שקוף לפתיחת סיידבר בצד שמאל
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .focusable()
                        .onFocusChanged {
                            if (it.isFocused) {
                                isSidebarOpen = true
                                sidebarFocusRequester.requestFocus()
                            }
                        }
                )

                AppNavHostContainer(navController = navController, homeViewModel = homeViewModel)
            }

            // --- סיידבר צף (Overlay) ---
            ReactSidebarOverlay(
                isOpen = isSidebarOpen,
                navController = navController,
                viewModel = homeViewModel,
                sidebarFocusRequester = sidebarFocusRequester,
                onClose = {
                    isSidebarOpen = false
                    contentFocusRequester.requestFocus()
                }
            )
        }
    }
}

@Composable
fun ReactSidebarOverlay(
    isOpen: Boolean,
    navController: NavHostController,
    viewModel: HomeViewModel,
    sidebarFocusRequester: FocusRequester,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val sidebarWidth by animateDpAsState(if (isOpen) 360.dp else 0.dp, label = "width")
    val sidebarAlpha by animateFloatAsState(if (isOpen) 1f else 0f, label = "alpha")

    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            delay(30000)
        }
    }

    Box(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .alpha(sidebarAlpha)
            .background(Brush.verticalGradient(listOf(Color(0xED000000), Color(0xF50F0F0F))))
            .clipToBounds()
            .onFocusChanged { if (!it.hasFocus && isOpen) onClose() }
            .focusGroup()
    ) {
        if (isOpen) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SidebarActionIcon(Icons.Default.Search, modifier = Modifier.focusRequester(sidebarFocusRequester)) { navController.navigate("search") }
                    SidebarActionIcon(Icons.Default.Settings) { navController.navigate("settings") }
                    SidebarActionIcon(Icons.AutoMirrored.Filled.ExitToApp) { /* Exit */ }
                }

                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    val tabs = listOf("Movies", "TV Shows", "Anime", "Live Sports", "Favourites")
                    tabs.forEach { title ->
                        SidebarMenuLabel(title, state.selectedTab == title) { viewModel.selectTab(title) }
                    }
                }

                Text(text = timeStr, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SidebarActionIcon(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFFE50914)),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        modifier = modifier.size(56.dp).onFocusChanged { isFocused = it.isFocused }
            .border(2.dp, if (isFocused) Color.White else Color.Transparent, CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
fun SidebarMenuLabel(title: String, active: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().height(60.dp).onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            Box(modifier = Modifier.height(30.dp).width(4.dp).background(if (active || isFocused) Color(0xFFE50914) else Color.Transparent).clip(RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(20.dp))
            Text(text = title, color = if (isFocused || active) Color.White else Color.Gray, fontSize = 26.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun AppNavHostContainer(navController: NavHostController, homeViewModel: HomeViewModel) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(state = homeViewModel.state.collectAsState().value, viewModel = homeViewModel, navController = navController, onMovieClick = { id -> navController.navigate("details/$id") })
        }
        composable("search") { Box(Modifier.fillMaxSize().background(Color.Black)) }
        composable("settings") { Box(Modifier.fillMaxSize().background(Color.Black)) }
    }
}