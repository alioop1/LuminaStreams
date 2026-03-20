@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.luminastreams.tv.domain.repository.MediaRepository
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

/**
 * MainActivity — single-activity entry point.
 *
 * תיקונים לעומת הגרסה הקודמת:
 * 1. הקובץ מכיל עכשיו את התוכן הנכון (MainActivity + Navigation)
 * 2. DetailsViewModel נוצר עם MediaRepository ישירות (ללא GetMediaDetailsUseCase)
 * 3. הוסרה תלות מיותרת ב-GetMediaDetailsUseCase
 * 4. תיקון קריטי לניווט הנגן - קידוד URLEncoder מאובטח שמונע קריסות של לינקי Real Debrid
 * 5. תיקון colorMode ל-Mali GPU של Mecool — מונע "Unknown dataspace 0"
 *
 * Path: app/src/main/java/com/luminastreams/tv/MainActivity.kt
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        // ✅ תיקון Mali GPU — מונע "Unknown dataspace 0" + "Unable to match swap behavior"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }

        setContent {
            LuminaTheme { LuminaAppShell() }
        }
    }
}

@Composable
fun LuminaAppShell() {
    val navController = rememberNavController()
    val repository: MediaRepository = remember { MediaRepositoryImpl() }
    val homeViewModel: HomeViewModel = viewModel()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AppNavHostContainer(navController, homeViewModel, repository)
        }
    }
}

@Composable
fun AppNavHostContainer(
    navController : NavHostController,
    homeViewModel : HomeViewModel,
    repository    : MediaRepository
) {
    val context     = LocalContext.current
    val application = context.applicationContext as Application

    NavHost(
        navController    = navController,
        startDestination = "home",
        enterTransition  = { fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        exitTransition   = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        popExitTransition  = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) }
    ) {

        // ── Home ──────────────────────────────────────────────────────────────
        composable("home") {
            HomeScreen(
                state         = homeViewModel.state.collectAsState().value,
                viewModel     = homeViewModel,
                navController = navController,
                onMovieClick  = { id ->
                    val safeId = java.net.URLEncoder.encode(id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        // ── Details ───────────────────────────────────────────────────────────
        composable(
            route     = "details?fullId={fullId}",
            arguments = listOf(navArgument("fullId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val fullId = backStackEntry.arguments?.getString("fullId") ?: ""
            if (fullId.isBlank()) return@composable

            val detailsViewModel: DetailsViewModel = viewModel(
                key     = "details_$fullId",
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        DetailsViewModel(repository, context) as T
                }
            )

            LaunchedEffect(fullId) {
                detailsViewModel.onEvent(DetailsEvent.LoadInitialData(fullId))
            }

            DetailsScreen(
                state           = detailsViewModel.state.collectAsState().value,
                onEvent         = detailsViewModel::onEvent,
                onPlayDirectUrl = { videoUrl, imdbId ->
                    val safeUrl  = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val safeImdb = if (imdbId.isBlank()) "_" else imdbId
                    navController.navigate("player?videoUrl=$safeUrl&imdbId=$safeImdb")
                },
                onNavigateBack        = { navController.popBackStack() },
                onRecommendationClick = { id ->
                    val safeId = java.net.URLEncoder.encode(id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        // ── Player ────────────────────────────────────────────────────────────
        composable(
            route     = "player?videoUrl={videoUrl}&imdbId={imdbId}",
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("imdbId")   { type = NavType.StringType; defaultValue = "_" }
            )
        ) { back ->
            val encodedUrl = back.arguments?.getString("videoUrl") ?: ""
            val imdbId     = back.arguments?.getString("imdbId")   ?: "_"
            val videoUrl   = java.net.URLDecoder.decode(encodedUrl, "UTF-8")

            if (videoUrl.isNotBlank()) {
                PlayerScreen(
                    videoUrl       = videoUrl,
                    imdbId         = if (imdbId == "_") "" else imdbId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ── Search ────────────────────────────────────────────────────────────
        composable("search") {
            val vm: SearchViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            SearchScreen(
                state          = vm.state.collectAsState().value,
                onIntent       = vm::onIntent,
                onNavigateBack = { navController.popBackStack() },
                onResultClick  = { result ->
                    val safeId = java.net.URLEncoder.encode(result.id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
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

        // ── Watchlist ─────────────────────────────────────────────────────────
        composable("watchlist") {
            val vm: WatchlistViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                WatchlistScreen(
                    viewModel      = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onMovieClick   = { id ->
                        val safeId = java.net.URLEncoder.encode(id, "UTF-8")
                        navController.navigate("details?fullId=$safeId")
                    }
                )
            }
        }
    }
}
