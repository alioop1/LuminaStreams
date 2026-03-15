@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.luminastreams.tv.presentation.search.SearchScreen
import com.luminastreams.tv.presentation.search.SearchViewModel
import com.luminastreams.tv.presentation.settings.SettingsScreen
import com.luminastreams.tv.presentation.settings.SettingsViewModel
import com.luminastreams.tv.ui.theme.LuminaTheme

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
    val repository = remember { MediaRepositoryImpl() }
    val homeViewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
        }
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AppNavHostContainer(
                navController = navController,
                homeViewModel = homeViewModel,
                repository    = repository
            )
        }
    }
}

@Composable
fun AppNavHostContainer(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    repository: MediaRepositoryImpl
) {
    val context = LocalContext.current
    // Application נדרש ל-AndroidViewModel
    val application = context.applicationContext as Application

    NavHost(navController = navController, startDestination = "home") {

        // ── Home
        composable("home") {
            HomeScreen(
                state         = homeViewModel.state.collectAsState().value,
                viewModel     = homeViewModel,
                navController = navController,
                onMovieClick  = { id -> navController.navigate("details/$id") }
            )
        }

        // ── Details
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
                        DetailsViewModel(
                            getMediaDetailsUseCase = GetMediaDetailsUseCase(repository),
                            context                = context
                        ) as T
                }
            )
            LaunchedEffect(fullId) { detailsViewModel.onEvent(DetailsEvent.LoadInitialData(fullId)) }
            DetailsScreen(
                state                 = detailsViewModel.state.collectAsState().value,
                onEvent               = detailsViewModel::onEvent,
                onPlayDirectUrl       = { url ->
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                },
                onNavigateBack        = { navController.popBackStack() },
                onRecommendationClick = { id -> navController.navigate("details/$id") }
            )
        }

        // ── Search — AndroidViewModel צריך Application
        composable("search") {
            val searchViewModel: SearchViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            SearchScreen(
                state          = searchViewModel.state.collectAsState().value,
                onIntent       = searchViewModel::onIntent,
                onNavigateBack = { navController.popBackStack() },
                onResultClick  = { result -> navController.navigate("details/${result.id}") }
            )
        }

        // ── Settings — AndroidViewModel צריך Application
        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            SettingsScreen(
                state            = settingsViewModel.state.collectAsState().value,
                viewModel        = settingsViewModel,
                isRtl            = false,
                onToggleLanguage = {}
            )
        }

        // ── Watchlist
        composable("watchlist") {
            WatchlistScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun WatchlistScreen(onNavigateBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onNavigateBack() }
    Box(
        modifier         = Modifier.fillMaxSize().background(Color(0xFF080808)),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Text(
            text  = "Watchlist — Coming Soon",
            color = Color.White,
            fontSize = 28.sp
        )
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
