@file:OptIn(
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.repository.MediaRepository
import com.luminastreams.tv.presentation.details.DetailsEvent
import com.luminastreams.tv.presentation.details.DetailsScreen
import com.luminastreams.tv.presentation.details.DetailsViewModel
import com.luminastreams.tv.presentation.home.HomeScreen
import com.luminastreams.tv.presentation.home.HomeViewModel
import com.luminastreams.tv.presentation.iptv.IptvScreen
import com.luminastreams.tv.presentation.iptv.IptvViewModel
import com.luminastreams.tv.presentation.player.IptvPlayerScreen
import com.luminastreams.tv.presentation.player.PlayerScreen
import com.luminastreams.tv.presentation.player.PlayerViewModel
import com.luminastreams.tv.presentation.search.SearchScreen
import com.luminastreams.tv.presentation.search.SearchViewModel
import com.luminastreams.tv.presentation.settings.SettingsScreen
import com.luminastreams.tv.presentation.settings.SettingsViewModel
import com.luminastreams.tv.presentation.watchlist.WatchlistScreen
import com.luminastreams.tv.presentation.watchlist.WatchlistViewModel
import com.luminastreams.tv.presentation.debrid.DebridCloudScreen
import com.luminastreams.tv.presentation.debrid.DebridCloudViewModel
import com.luminastreams.tv.presentation.auth.*
import com.luminastreams.tv.ui.theme.LuminaTheme
import java.net.URLDecoder
import java.net.URLEncoder
private const val FORCED_DENSITY_DPI = 180

class MainActivity : ComponentActivity() {

    var onUserInteractionListener: (() -> Boolean)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (onUserInteractionListener?.invoke() == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun attachBaseContext(newBase: Context) {
        // WSA (Windows Subsystem for Android) uses a different display pipeline.
        // Forcing 180 DPI collapses the Column weights to ~0px on WSA, causing
        // the entire content area (hero + rows) to be invisible.
        // Confirmed from logcat: Build.HARDWARE = "windows_x86_64" on WSA.
        val hw = Build.HARDWARE.lowercase()
        val isWsa = hw.contains("windows")
                || Build.MANUFACTURER.contains("Microsoft", ignoreCase = true)
                || Build.MODEL.contains("Subsystem", ignoreCase = true)
                || Build.BRAND.contains("Windows", ignoreCase = true)

        val dm = newBase.resources.displayMetrics
        if (!isWsa && dm.densityDpi != FORCED_DENSITY_DPI) {
            val config = Configuration(newBase.resources.configuration)
            config.densityDpi = FORCED_DENSITY_DPI
            val scaled = newBase.createConfigurationContext(config)
            super.attachBaseContext(scaled)
        } else {
            super.attachBaseContext(newBase)
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
    }
}

@Composable
fun LuminaAppShell() {
    val context = LocalContext.current
    val app = context.applicationContext as com.luminastreams.tv.core.LuminaApp
    val settingsSnap by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle()
    val appLang = settingsSnap.appLanguage

    val layoutDir = if (appLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

    val navController = rememberNavController()

    // Reuse the singleton repository built once in LuminaApp.onCreate() instead of
    // constructing a fresh MediaRepositoryImpl here — which would spin up a second
    // OkHttp connection pool and risk holding onto the Activity context.
    val repository: MediaRepository = app.repository
    val homeViewModel: HomeViewModel = viewModel()

    var showScreensaver by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val activity = context as? MainActivity
    DisposableEffect(activity, showScreensaver) {
        if (activity != null) {
            activity.onUserInteractionListener = {
                lastInteractionTime = System.currentTimeMillis()
                if (showScreensaver) {
                    showScreensaver = false
                    true
                } else {
                    false
                }
            }
        }
        onDispose {
            if (activity != null) {
                activity.onUserInteractionListener = null
            }
        }
    }

    LaunchedEffect(lastInteractionTime, currentRoute) {
        if (currentRoute?.startsWith("player") == true || currentRoute?.startsWith("iptv_player") == true) {
            showScreensaver = false
        } else {
            kotlinx.coroutines.delay(300_000L)
            showScreensaver = true
        }
    }

    val screensaverViewModel: com.luminastreams.tv.presentation.screensaver.ScreensaverViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                com.luminastreams.tv.presentation.screensaver.ScreensaverViewModel(repository) as T
        }
    )

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent {
                    lastInteractionTime = System.currentTimeMillis()
                    if (showScreensaver) {
                        showScreensaver = false
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            AppNavHostContainer(navController, homeViewModel, repository)

            androidx.compose.animation.AnimatedVisibility(
                visible = showScreensaver,
                enter = fadeIn(animationSpec = tween(1000)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                com.luminastreams.tv.presentation.screensaver.ScreensaverScreen(screensaverViewModel)
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

    val isLow = DeviceProfile.tier == DeviceProfile.Tier.LOW
    NavHost(
        navController    = navController,
        startDestination = "splash",
        enterTransition  = { if (isLow) EnterTransition.None  else fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        exitTransition   = { if (isLow) androidx.compose.animation.ExitTransition.None   else fadeOut(animationSpec = tween(400, easing = LinearOutSlowInEasing)) },
        popEnterTransition = { fadeIn(animationSpec  = tween(400, easing = LinearOutSlowInEasing)) },
        popExitTransition  = { fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) }
    ) {

        composable("splash") {
            com.luminastreams.tv.presentation.initial.SplashScreen(onTimeout = {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }

        composable("home") {
            HomeScreen(
                state         = homeViewModel.state.collectAsStateWithLifecycle().value,
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
            val fullId = URLDecoder.decode(backStackEntry.arguments?.getString("fullId") ?: "", "UTF-8")
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
                state           = detailsViewModel.state.collectAsStateWithLifecycle().value,
                onEvent         = detailsViewModel::onEvent,
                onPlayDirectUrl = { videoUrl, imdbId, title, backdrop, poster, logo ->
                    val safeUrl      = URLEncoder.encode(videoUrl, "UTF-8")
                    val safeImdb     = if (imdbId.isBlank()) "_" else imdbId
                    val safeTitle    = URLEncoder.encode(title, "UTF-8")
                    val safeBackdrop = URLEncoder.encode(backdrop.ifBlank { "none" }, "UTF-8")
                    val safePoster   = URLEncoder.encode(poster.ifBlank { "none" }, "UTF-8")
                    val safeLogo     = URLEncoder.encode(logo.ifBlank { "none" }, "UTF-8")
                    // Pass fullId (TMDB display ID) so the player can save the IMDB→TMDB bridge
                    val safeFullId   = URLEncoder.encode(fullId, "UTF-8")
                    navController.navigate("player?videoUrl=$safeUrl&imdbId=$safeImdb&title=$safeTitle&backdropUrl=$safeBackdrop&posterUrl=$safePoster&logoUrl=$safeLogo&fullId=$safeFullId")
                },
                onNavigateBack        = { navController.popBackStack() },
                onNavigateToDetails   = { id ->
                    val safeId = URLEncoder.encode(id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable(
            route     = "player?videoUrl={videoUrl}&imdbId={imdbId}&title={title}&backdropUrl={backdropUrl}&posterUrl={posterUrl}&logoUrl={logoUrl}&fullId={fullId}",
            arguments = listOf(
                navArgument("videoUrl")    { type = NavType.StringType; defaultValue = "" },
                navArgument("imdbId")      { type = NavType.StringType; defaultValue = "_" },
                navArgument("title")       { type = NavType.StringType; defaultValue = "" },
                navArgument("backdropUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("posterUrl")   { type = NavType.StringType; defaultValue = "" },
                navArgument("logoUrl")     { type = NavType.StringType; defaultValue = "" },
                navArgument("fullId")      { type = NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            val videoUrl    = URLDecoder.decode(back.arguments?.getString("videoUrl") ?: "", "UTF-8")
            val imdbId      = back.arguments?.getString("imdbId") ?: "_"
            val title       = URLDecoder.decode(back.arguments?.getString("title") ?: "", "UTF-8")
            val backdropUrl = URLDecoder.decode(back.arguments?.getString("backdropUrl") ?: "", "UTF-8").let { if (it == "none") "" else it }
            val posterUrl   = URLDecoder.decode(back.arguments?.getString("posterUrl") ?: "", "UTF-8").let { if (it == "none") "" else it }
            val logoUrl     = URLDecoder.decode(back.arguments?.getString("logoUrl") ?: "", "UTF-8").let { if (it == "none") "" else it }
            val playerFullId = URLDecoder.decode(back.arguments?.getString("fullId") ?: "", "UTF-8")

            if (videoUrl.isNotBlank()) {
                val vm: PlayerViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                )
                PlayerScreen(
                    videoUrl       = videoUrl,
                    imdbId         = if (imdbId == "_") "" else imdbId,
                    title          = title,
                    backdropUrl    = backdropUrl,
                    posterUrl      = posterUrl,
                    logoUrl        = logoUrl,
                    fullId         = playerFullId,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel      = vm
                )
            }
        }

        composable("search") {
            val vm: SearchViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            SearchScreen(
                state          = vm.state.collectAsStateWithLifecycle().value,
                onIntent       = vm::onIntent,
                onNavigateBack = { navController.popBackStack() },
                onResultClick  = { result ->
                    val safeId = URLEncoder.encode(result.id, "UTF-8")
                    navController.navigate("details?fullId=$safeId")
                }
            )
        }

        composable("settings") {
            val vm: SettingsViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val state = vm.state.collectAsStateWithLifecycle().value
            Box(Modifier.fillMaxSize().background(Color(0xFF040405))) {
                SettingsScreen(
                    state          = state,
                    viewModel      = vm,
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
                        val safeId = URLEncoder.encode(id, "UTF-8")
                        navController.navigate("details?fullId=$safeId")
                    }
                )
            }
        }

        composable("debrid") {
            val vm: DebridCloudViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        DebridCloudViewModel(repository, application) as T
                }
            )
            val state by vm.state.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(Color(0xFF060608))) {
                DebridCloudScreen(
                    state = state,
                    onEvent = vm::onEvent,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate("auth") },
                    onPlayDirectUrl = { videoUrl, title, backdrop, poster ->
                        val safeUrl      = URLEncoder.encode(videoUrl, "UTF-8")
                        val safeTitle    = URLEncoder.encode(title, "UTF-8")
                        val safeBackdrop = URLEncoder.encode(backdrop.ifBlank { "none" }, "UTF-8")
                        val safePoster   = URLEncoder.encode(poster.ifBlank { "none" }, "UTF-8")
                        navController.navigate("player?videoUrl=$safeUrl&imdbId=_&title=$safeTitle&backdropUrl=$safeBackdrop&posterUrl=$safePoster&logoUrl=none&fullId=_")
                    }
                )
            }
        }

        composable("iptv") {
            val context = LocalContext.current
            val app = context.applicationContext as com.luminastreams.tv.core.LuminaApp
            val activity = context.findActivity()!!

            val vm: IptvViewModel = viewModel(
                viewModelStoreOwner = activity,
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return IptvViewModel(app.iptvRepository) as T
                    }
                }
            )

            val channels by vm.channels.collectAsStateWithLifecycle()

            Box(Modifier.fillMaxSize().background(Color(0xFF000000))) {
                IptvScreen(
                    viewModel = vm,
                    onPlayChannel = { channelId ->
                        val selectedChannel = channels.find { it.id == channelId }
                        val streamUrl = selectedChannel?.streamUrl ?: ""
                        if (streamUrl.isNotBlank()) {
                            val safeUrl = URLEncoder.encode(streamUrl, "UTF-8")
                            navController.navigate("iptv_player/$safeUrl")
                        }
                    }
                )
            }
        }

        composable("iptv_player/{streamUrl}") { backStackEntry ->
            val streamUrl = URLDecoder.decode(backStackEntry.arguments?.getString("streamUrl") ?: "", "UTF-8")

            val context = LocalContext.current
            val app = context.applicationContext as com.luminastreams.tv.core.LuminaApp
            val activity = context.findActivity()!!

            val vm: IptvViewModel = viewModel(
                viewModelStoreOwner = activity,
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return IptvViewModel(app.iptvRepository) as T
                    }
                }
            )

            IptvPlayerScreen(
                initialChannelUrl = streamUrl,
                viewModel = vm,
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable("auth") {
            val app = LocalContext.current.applicationContext as com.luminastreams.tv.core.LuminaApp
            val vm: AuthViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AuthViewModel(app.authRepository, app.syncManager) as T
                    }
                }
            )
            val state = vm.state.collectAsStateWithLifecycle().value
            AuthScreen(
                state = state,
                onEvent = vm::onEvent,
                onSuccess = { navController.popBackStack() }
            )
        }
    }
}

fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}