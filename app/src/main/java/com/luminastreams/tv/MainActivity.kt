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
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import com.luminastreams.tv.core.SoundManager
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
import com.luminastreams.tv.presentation.search.SearchScreen
import com.luminastreams.tv.presentation.search.SearchViewModel
import com.luminastreams.tv.presentation.settings.SettingsScreen
import com.luminastreams.tv.presentation.settings.SettingsViewModel
import com.luminastreams.tv.presentation.watchlist.WatchlistScreen
import com.luminastreams.tv.presentation.watchlist.WatchlistViewModel
import com.luminastreams.tv.ui.theme.LuminaTheme
import java.net.URLDecoder
import java.net.URLEncoder

private const val FORCED_DENSITY_DPI = 160

class MainActivity : ComponentActivity() {

    var soundManager: SoundManager? = null

    override fun attachBaseContext(newBase: Context) {
        val dm = newBase.resources.displayMetrics
        if (dm.densityDpi != FORCED_DENSITY_DPI) {
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
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    var appLang by remember { mutableStateOf(prefs.getString("app_lang", "he") ?: "he") }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "app_lang") {
                appLang = sharedPrefs.getString("app_lang", "he") ?: "he"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val layoutDir = if (appLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

    val navController = rememberNavController()

    val repository: MediaRepository = remember { MediaRepositoryImpl(context) }
    val homeViewModel: HomeViewModel = viewModel()

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
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
                state           = detailsViewModel.state.collectAsStateWithLifecycle().value,
                onEvent         = detailsViewModel::onEvent,
                onPlayDirectUrl = { videoUrl, imdbId, title, backdrop, poster, logo ->
                    val safeUrl      = URLEncoder.encode(videoUrl, "UTF-8")
                    val safeImdb     = if (imdbId.isBlank()) "_" else imdbId
                    val safeTitle    = URLEncoder.encode(title, "UTF-8")
                    val safeBackdrop = URLEncoder.encode(backdrop.ifBlank { "none" }, "UTF-8")
                    val safePoster   = URLEncoder.encode(poster.ifBlank { "none" }, "UTF-8")
                    val safeLogo     = URLEncoder.encode(logo.ifBlank { "none" }, "UTF-8")
                    navController.navigate("player?videoUrl=$safeUrl&imdbId=$safeImdb&title=$safeTitle&backdropUrl=$safeBackdrop&posterUrl=$safePoster&logoUrl=$safeLogo")
                },
                onNavigateBack        = { navController.popBackStack() }
            )
        }

        composable(
            route     = "player?videoUrl={videoUrl}&imdbId={imdbId}&title={title}&backdropUrl={backdropUrl}&posterUrl={posterUrl}&logoUrl={logoUrl}",
            arguments = listOf(
                navArgument("videoUrl")    { type = NavType.StringType; defaultValue = "" },
                navArgument("imdbId")      { type = NavType.StringType; defaultValue = "_" },
                navArgument("title")       { type = NavType.StringType; defaultValue = "" },
                navArgument("backdropUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("posterUrl")   { type = NavType.StringType; defaultValue = "" },
                navArgument("logoUrl")     { type = NavType.StringType; defaultValue = "" }
            )
        ) { back ->
            val videoUrl    = URLDecoder.decode(back.arguments?.getString("videoUrl") ?: "", "UTF-8")
            val imdbId      = back.arguments?.getString("imdbId") ?: "_"
            val title       = URLDecoder.decode(back.arguments?.getString("title") ?: "", "UTF-8")
            val backdropUrl = URLDecoder.decode(back.arguments?.getString("backdropUrl") ?: "", "UTF-8").let { if (it == "none") "" else it }
            val logoUrl     = URLDecoder.decode(back.arguments?.getString("logoUrl") ?: "", "UTF-8").let { if (it == "none") "" else it }

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

        composable("iptv") {
            val context = LocalContext.current
            val app = context.applicationContext as com.luminastreams.tv.core.LuminaApp
            val activity = context as ComponentActivity

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
            val activity = context as ComponentActivity

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
    }
}