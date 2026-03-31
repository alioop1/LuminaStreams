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
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luminastreams.tv.core.SoundManager
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
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    var soundManager: SoundManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.also {
                it.preferMinimalPostProcessing = true
            }
        }

        soundManager = SoundManager(this)

        setContent {
            LuminaTheme { LuminaAppShell() }
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    soundManager?.playClick()
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    soundManager?.playNav()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        soundManager?.release()
        super.onDestroy()
    }
}

@Composable
fun LuminaAppShell() {
    val navController = rememberNavController()
    // Single repository instance — shared across all ViewModels
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

// ── Lumina Light Waves loading indicator ──
@Composable
fun LuminaLoadingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "light_waves")
    val waveCount = 5

    val waveAnimations = (0 until waveCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue  = 1.0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = index * 120)
            ),
            label = "wave_$index"
        )
    }

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        waveAnimations.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(anim.value)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFFB400FF)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        (context as? MainActivity)?.soundManager?.playSplash()
        delay(3500)
        onTimeout()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter            = painterResource(id = R.drawable.logo_lumina_glow),
            contentDescription = "Lumina Logo Background",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize().alpha(alpha)
        )
        LuminaLoadingIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
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
                    val safeId = URLEncoder.encode(id, "UTF-8")
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
                    val safeUrl     = URLEncoder.encode(videoUrl, "UTF-8")
                    val safeImdb    = if (imdbId.isBlank()) "_" else imdbId
                    val safeTitle   = URLEncoder.encode(title, "UTF-8")
                    val safeBackdrop = URLEncoder.encode(backdrop.ifBlank { "none" }, "UTF-8")
                    val safeLogo    = URLEncoder.encode(logo.ifBlank { "none" }, "UTF-8")
                    navController.navigate("player?videoUrl=$safeUrl&imdbId=$safeImdb&title=$safeTitle&backdropUrl=$safeBackdrop&logoUrl=$safeLogo")
                },
                onNavigateBack        = { navController.popBackStack() },
                onRecommendationClick = { id ->
                    val safeId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable(
            route     = "player?videoUrl={videoUrl}&imdbId={imdbId}&title={title}&backdropUrl={backdropUrl}&logoUrl={logoUrl}",
            arguments = listOf(
                navArgument("videoUrl")    { type = NavType.StringType; defaultValue = "" },
                navArgument("imdbId")      { type = NavType.StringType; defaultValue = "_" },
                navArgument("title")       { type = NavType.StringType; defaultValue = "" },
                navArgument("backdropUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("logoUrl")     { type = NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            val videoUrl    = URLDecoder.decode(back.arguments?.getString("videoUrl") ?: "", "UTF-8")
            val imdbId      = back.arguments?.getString("imdbId") ?: "_"
            val title       = URLDecoder.decode(back.arguments?.getString("title") ?: "", "UTF-8")
            val backdropUrl = URLDecoder.decode(back.arguments?.getString("backdropUrl") ?: "", "UTF-8")
                .let { if (it == "none") "" else it }
            val logoUrl     = URLDecoder.decode(back.arguments?.getString("logoUrl") ?: "", "UTF-8")
                .let { if (it == "none") "" else it }

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
            val vm: SearchViewModel = viewModel()
            SearchScreen(
                state          = vm.state.collectAsState().value,
                onIntent       = vm::onIntent,
                onNavigateBack = { navController.popBackStack() },
                onResultClick  = { result ->
                    val safeId = URLEncoder.encode(result.id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable("settings") {
            val vm: SettingsViewModel = viewModel()
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
            val vm: WatchlistViewModel = viewModel()
            Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                WatchlistScreen(
                    viewModel      = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onMovieClick   = { id ->
                        val safeId = URLEncoder.encode(id, "UTF-8")
                        navController.navigate("details?fullId=$safeId")
                    }
                )
            }
        }
    }
}