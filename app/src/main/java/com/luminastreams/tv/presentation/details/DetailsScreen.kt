@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.usecase.GetMediaDetailsUseCase
import com.luminastreams.tv.presentation.details.DetailsEvent
import com.luminastreams.tv.presentation.details.DetailsScreen
import com.luminastreams.tv.presentation.details.DetailsViewModel
import com.luminastreams.tv.presentation.home.HomeScreen
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.presentation.player.PlayerScreen
import com.luminastreams.tv.presentation.search.SearchScreen
import com.luminastreams.tv.presentation.search.SearchViewModel
import com.luminastreams.tv.presentation.settings.SettingsScreen
import com.luminastreams.tv.presentation.settings.SettingsViewModel
import com.luminastreams.tv.presentation.watchlist.WatchlistScreen
import com.luminastreams.tv.presentation.watchlist.WatchlistViewModel
import com.luminastreams.tv.ui.theme.LuminaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        setContent { LuminaTheme { LuminaAppShell() } }
    }
}

@Composable
fun LuminaAppShell() {
    val navController = rememberNavController()
    val repository    = remember { MediaRepositoryImpl() }
    val homeViewModel : HomeViewModel = viewModel()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AppNavHostContainer(navController, homeViewModel, repository)
        }
    }
}

@Composable
fun AppNavHostContainer(
    navController : NavHostController,
    homeViewModel : HomeViewModel,
    repository    : MediaRepositoryImpl
) {
    val context      = LocalContext.current
    val application  = context.applicationContext as Application

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home",
            // ── אופטימיזציה: הנפשות חלוקות ונעימות במעבר בין מסכים ──
            enterTransition = { fadeIn(animationSpec = tween(400, easing = LinearOutSlowInEasing)) },
            exitTransition = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) },
            popEnterTransition = { fadeIn(animationSpec = tween(400, easing = LinearOutSlowInEasing)) },
            popExitTransition = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) }
        ) {

            composable("home") {
                HomeScreen(
                    state         = homeViewModel.state.collectAsState().value,
                    viewModel     = homeViewModel,
                    navController = navController,
                    onMovieClick  = { id -> navController.navigate("details/$id") }
                )
            }

            composable(
                route     = "details/{fullId}",
                arguments = listOf(navArgument("fullId") { type = NavType.StringType })
            ) { backStackEntry ->
                val fullId = backStackEntry.arguments?.getString("fullId") ?: return@composable
                val detailsViewModel: DetailsViewModel = viewModel(
                    key     = "details_$fullId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            DetailsViewModel(GetMediaDetailsUseCase(repository), context) as T
                    }
                )
                LaunchedEffect(fullId) { detailsViewModel.onEvent(DetailsEvent.LoadInitialData(fullId)) }
                DetailsScreen(
                    state                 = detailsViewModel.state.collectAsState().value,
                    onEvent               = detailsViewModel::onEvent,
                    onPlayDirectUrl       = { videoUrl, imdbId ->
                        val encodedUrl = android.net.Uri.encode(videoUrl)
                        val safeImdbId = imdbId.ifBlank { "_" }
                        navController.navigate("player/$encodedUrl/$safeImdbId")
                    },
                    onNavigateBack        = { navController.popBackStack() },
                    onRecommendationClick = { id -> navController.navigate("details/$id") }
                )
            }

            composable(
                route     = "player/{videoUrl}/{imdbId}",
                arguments = listOf(
                    navArgument("videoUrl") { type = NavType.StringType },
                    navArgument("imdbId")   { type = NavType.StringType }
                )
            ) { back ->
                val encodedUrl = back.arguments?.getString("videoUrl") ?: return@composable
                val imdbId     = back.arguments?.getString("imdbId") ?: ""
                val videoUrl   = android.net.Uri.decode(encodedUrl)
                if (videoUrl.isNotBlank()) {
                    PlayerScreen(
                        videoUrl       = videoUrl,
                        imdbId         = if (imdbId == "_") "" else imdbId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            composable("search") {
                val vm: SearchViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                SearchScreen(
                    state          = vm.state.collectAsState().value,
                    onIntent       = vm::onIntent,
                    onNavigateBack = { navController.popBackStack() },
                    onResultClick  = { result -> navController.navigate("details/${result.id}") }
                )
            }

            composable("settings") {
                val vm: SettingsViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                    SettingsScreen(
                        state            = vm.state.collectAsState().value,
                        viewModel        = vm,
                        isRtl            = false,
                        onNavigateBack   = { navController.popBackStack() },
                        onToggleLanguage = {}
                    )
                }
            }

            composable("watchlist") {
                val vm: WatchlistViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                    WatchlistScreen(
                        viewModel      = vm,
                        onNavigateBack = { navController.popBackStack() },
                        onMovieClick   = { id -> navController.navigate("details/$id") }
                    )
                }
            }
        }
    }
}