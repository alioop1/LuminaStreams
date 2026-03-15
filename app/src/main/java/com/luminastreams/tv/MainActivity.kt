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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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

private val RED   = Color(0xFFE50914)
private val WHITE = Color(0xFFFFFFFF)
private val NAV_HOVER = Color(0x18FFFFFF)

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
    val homeViewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
        }
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AppNavHostContainer(navController, homeViewModel, repository)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// AppNavHostContainer
@Composable
fun AppNavHostContainer(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    repository: MediaRepositoryImpl
) {
    val context     = LocalContext.current
    val application = context.applicationContext as Application
    val backStack   by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    // מסכים שצריכים header עליון ישיר
    val showSharedHeader = currentRoute in listOf("search", "settings", "watchlist")

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {

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
                    onPlayDirectUrl       = { url ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (_: Exception) {}
                    },
                    onNavigateBack        = { navController.popBackStack() },
                    onRecommendationClick = { id -> navController.navigate("details/$id") }
                )
            }

            // Search — content בלבד, ה-header מצויר מעל
            composable("search") {
                val vm: SearchViewModel = viewModel(factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {
                    // הוסף padding למעלה כדי לא להתכסות מתחת ה-header
                    Box(Modifier.fillMaxSize().padding(top = 68.dp)) {
                        SearchScreen(
                            state          = vm.state.collectAsState().value,
                            onIntent       = vm::onIntent,
                            onNavigateBack = { navController.popBackStack() },
                            onResultClick  = { result -> navController.navigate("details/${result.id}") }
                        )
                    }
                }
            }

            composable("settings") {
                val vm: SettingsViewModel = viewModel(factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                Box(Modifier.fillMaxSize().background(Color(0xFF050505))) {
                    Box(Modifier.fillMaxSize().padding(top = 68.dp)) {
                        SettingsScreen(
                            state            = vm.state.collectAsState().value,
                            viewModel        = vm,
                            isRtl            = false,
                            onToggleLanguage = {}
                        )
                    }
                }
            }

            composable("watchlist") {
                Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
                    Box(Modifier.fillMaxSize().padding(top = 68.dp)) {
                        WatchlistScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }

        // ══ HEADER משותף — מוצר מעלה לכל מסך חוץ home/details ══
        if (showSharedHeader) {
            LuminaSharedHeader(
                currentRoute  = currentRoute,
                modifier      = Modifier.align(Alignment.TopStart).fillMaxWidth().zIndex(20f),
                onSearch      = { navController.navigate("search")    { launchSingleTop = true } },
                onMovies      = { navController.navigate("home")       { launchSingleTop = true } },
                onTV          = { navController.navigate("home")       { launchSingleTop = true } },
                onWatchlist   = { navController.navigate("watchlist")  { launchSingleTop = true } },
                onSettings    = { navController.navigate("settings")   { launchSingleTop = true } }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// LuminaSharedHeader — נצמד ל-TOP, אין רווח
@Composable
fun LuminaSharedHeader(
    currentRoute: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onMovies: () -> Unit,
    onTV: () -> Unit,
    onWatchlist: () -> Unit,
    onSettings: () -> Unit
) {
    val time = remember {
        val c = java.util.Calendar.getInstance()
        "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF0080808), Color(0xC0080808), Color.Transparent)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Logo
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)) { append("LUMINA") }
                        withStyle(SpanStyle(color = RED,   fontSize = 11.sp, fontWeight = FontWeight.Bold,  letterSpacing = 1.5.sp)) { append("STREAMS") }
                    }
                )
                Box(Modifier.width(48.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                    .background(Brush.horizontalGradient(listOf(RED, RED.copy(alpha = 0f)))))
            }

            // Nav pills
            SharedNavPill("Search",    Icons.Default.Search,   currentRoute == "search",    onSearch)
            SharedNavPill("Movies",    Icons.Default.Movie,    false,                        onMovies)
            SharedNavPill("TV",        Icons.Default.LiveTv,   false,                        onTV)
            SharedNavPill("Watchlist", Icons.Default.Bookmark, currentRoute == "watchlist",  onWatchlist)
            SharedNavPill("Settings",  Icons.Default.Settings, currentRoute == "settings",   onSettings)

            Spacer(Modifier.weight(1f))
            Text(time, color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SharedNavPill
@Composable
fun SharedNavPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors   = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = NAV_HOVER,
            pressedContainerColor = Color(0xFF1E1E1E)
        ),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .height(44.dp)
            .onSizeChanged { surfaceSize = it }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (isSelected && surfaceSize != IntSize.Zero) {
                        val barH = with(density) { 3.dp.toPx() }
                        val padH = with(density) { 10.dp.toPx() }
                        val w    = surfaceSize.width.toFloat()
                        val h    = surfaceSize.height.toFloat()
                        val barW = (w - padH * 2f).coerceAtLeast(0f)
                        val top  = h - barH
                        val glowH = with(density) { 12.dp.toPx() }
                        drawRect(
                            brush   = Brush.verticalGradient(
                                listOf(Color.Transparent, RED.copy(alpha = 0.45f)),
                                startY = top - glowH, endY = top
                            ),
                            topLeft = Offset(padH, top - glowH),
                            size    = Size(barW, glowH)
                        )
                        drawRoundRect(
                            color        = RED,
                            topLeft      = Offset(padH, top),
                            size         = Size(barW, barH),
                            cornerRadius = CornerRadius(barH / 2)
                        )
                    }
                }
            }
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
                tint               = if (isSelected) RED else WHITE.copy(alpha = 0.75f)
            )
            Text(
                text       = label,
                color      = if (isSelected) WHITE else WHITE.copy(alpha = 0.75f),
                fontSize   = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.2.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// WatchlistScreen
@Composable
fun WatchlistScreen(onNavigateBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onNavigateBack() }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Watchlist — Coming Soon", color = WHITE, fontSize = 28.sp)
    }
}

private val Int.sp   get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
