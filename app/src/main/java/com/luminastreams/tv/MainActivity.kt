@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.os.Build
import android.app.Application
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Text
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.also {
                it.preferMinimalPostProcessing = true
            }
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
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // זמן הצגת מסך הפתיחה
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF040405)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE50914)), Alignment.Center) {
                Text("L", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Text("LUMINA",  color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, lineHeight = 30.sp)
                Text("STREAMS", color = Color(0xFFE50914),   fontSize = 14.sp,  fontWeight = FontWeight.Bold,  letterSpacing = 5.sp, lineHeight = 14.sp)
            }
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
        navController      = navController,
        startDestination   = "splash",
        enterTransition    = { fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        exitTransition     = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        popExitTransition  = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) }
    ) {

        composable("splash") {
            SplashScreen(onTimeout = {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }

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
                        DetailsViewModel(repository, application) as T
                }
            )

            LaunchedEffect(fullId) {
                detailsViewModel.onEvent(DetailsEvent.LoadInitialData(fullId))
            }

            DetailsScreen(
                state           = detailsViewModel.state.collectAsState().value,
                onEvent         = detailsViewModel::onEvent,
                onPlayDirectUrl = { videoUrl, imdbId, title, backdrop, logo ->
                    val safeUrl  = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                    val safeImdb = if (imdbId.isBlank()) "_" else imdbId
                    val safeTitle = java.net.URLEncoder.encode(title, "UTF-8")
                    val safeBackdrop = java.net.URLEncoder.encode(backdrop, "UTF-8")
                    val safeLogo = java.net.URLEncoder.encode(logo, "UTF-8") // <--- חדש
                    navController.navigate("player?videoUrl=$safeUrl&imdbId=$safeImdb&title=$safeTitle&backdropUrl=$safeBackdrop&logoUrl=$safeLogo") // <--- התעדכן
                },
                onNavigateBack        = { navController.popBackStack() },
                onRecommendationClick = { id ->
                    val safeId = java.net.URLEncoder.encode(id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable(
            route     = "player?videoUrl={videoUrl}&imdbId={imdbId}&title={title}&backdropUrl={backdropUrl}&logoUrl={logoUrl}",
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("imdbId")   { type = NavType.StringType; defaultValue = "_" },
                navArgument("title")    { type = NavType.StringType; defaultValue = "" },
                navArgument("backdropUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("logoUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            val encodedUrl = back.arguments?.getString("videoUrl") ?: ""
            val videoUrl   = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            val imdbId     = back.arguments?.getString("imdbId")   ?: "_"

            val encodedTitle = back.arguments?.getString("title") ?: ""
            val title      = java.net.URLDecoder.decode(encodedTitle, "UTF-8")

            val encodedBackdrop = back.arguments?.getString("backdropUrl") ?: ""
            val backdropUrl = java.net.URLDecoder.decode(encodedBackdrop, "UTF-8")

            val encodedLogo = back.arguments?.getString("logoUrl") ?: ""
            val logoUrl = java.net.URLDecoder.decode(encodedLogo, "UTF-8")

            if (videoUrl.isNotBlank()) {
                PlayerScreen(
                    videoUrl       = videoUrl,
                    imdbId         = if (imdbId == "_") "" else imdbId,
                    title          = title,
                    backdropUrl    = backdropUrl,
                    logoUrl        = logoUrl,
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
                onResultClick  = { result ->
                    val safeId = java.net.URLEncoder.encode(result.id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable("settings") {
            val vm: SettingsViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                SettingsScreen(
                    state          = vm.state.collectAsState().value,
                    viewModel      = vm,
                    isRtl          = false,
                    onNavigateBack = { navController.popBackStack() }
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
                    onMovieClick   = { id ->
                        val safeId = java.net.URLEncoder.encode(id, "UTF-8")
                        navController.navigate("details?fullId=$safeId")
                    }
                )
            }
        }
    }
}