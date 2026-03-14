@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.components.TopNavBar

// ─────────────────────────────────────────────
//  Netflix Colour Tokens
// ─────────────────────────────────────────────
private val NetflixRed       = Color(0xFFE50914)
private val NetflixDarkRed   = Color(0xFFB20710)
private val NfBlack          = Color(0xFF000000)
private val NfDarkGray       = Color(0xFF141414)
private val NfGray           = Color(0xFF808080)
private val NfLightGray      = Color(0xFFB3B3B3)
private val NfWhite          = Color(0xFFFFFFFF)
private val GlassDark        = Color(0x66000000)
private val GlassWhite       = Color(0x33FFFFFF)
private val GlassWhiteBorder = Color(0x55FFFFFF)

// ─────────────────────────────────────────────
//  Root Home Screen
// ─────────────────────────────────────────────
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val config = LocalConfiguration.current
    val heroHeight = (config.screenHeightDp * 0.82f).dp

    Box(modifier = Modifier.fillMaxSize().background(NfBlack)) {

        when {
            state.isLoading -> NetflixLoadingSkeleton()
            state.error != null -> NetflixErrorScreen(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                val displayItem = state.focusedItem ?: state.movieTrending.firstOrNull()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // ── Hero ──────────────────────────────────────────
                    item {
                        NetflixHeroBanner(
                            movie = displayItem,
                            heroHeight = heroHeight,
                            onPlayClick = { displayItem?.id?.let(onMovieClick) },
                            onMoreInfoClick = { displayItem?.id?.let(onMovieClick) }
                        )
                    }

                    // ── Content Rows ──────────────────────────────────
                    val allRows = buildList {
                        if (state.selectedTab == "סרטים") {
                            if (state.movieTrending.isNotEmpty())   add("🔥 Trending Now"      to state.movieTrending)
                            if (state.moviePremieres.isNotEmpty())  add("🎬 New Releases"       to state.moviePremieres)
                            if (state.movieAction.isNotEmpty())     add("💥 Action & Adventure" to state.movieAction)
                            if (state.movieTopRated.isNotEmpty())   add("⭐ Top Rated"           to state.movieTopRated)
                            if (state.movieComedy.isNotEmpty())     add("😂 Comedy"             to state.movieComedy)
                            if (state.movieDrama.isNotEmpty())      add("🎭 Drama"              to state.movieDrama)
                            if (state.movieScifi.isNotEmpty())      add("🚀 Sci-Fi"             to state.movieScifi)
                            if (state.movieHorror.isNotEmpty())     add("👻 Horror"             to state.movieHorror)
                            if (state.movieAnimation.isNotEmpty())  add("🎨 Animation"          to state.movieAnimation)
                        } else {
                            if (state.tvTrending.isNotEmpty())      add("🔥 Trending Series"    to state.tvTrending)
                            if (state.tvPremieres.isNotEmpty())     add("🆕 New Episodes"        to state.tvPremieres)
                            if (state.tvDrama.isNotEmpty())         add("🎭 Drama Series"        to state.tvDrama)
                            if (state.tvComedy.isNotEmpty())        add("😂 Comedy"             to state.tvComedy)
                            if (state.tvCrime.isNotEmpty())         add("🔪 Crime & Thriller"   to state.tvCrime)
                            if (state.tvScifi.isNotEmpty())         add("🚀 Sci-Fi & Fantasy"   to state.tvScifi)
                            if (state.tvDocumentary.isNotEmpty())   add("📽 Documentary"         to state.tvDocumentary)
                            if (state.tvTopRated.isNotEmpty())      add("⭐ Top Rated"           to state.tvTopRated)
                        }
                        if (state.discoveryResults.isNotEmpty()) add("🎯 ${state.selectedGenreName}" to state.discoveryResults)
                    }

                    allRows.forEach { (rowTitle, movies) ->
                        item(key = rowTitle) {
                            NetflixContentRow(
                                title = rowTitle,
                                movies = movies,
                                onFocus = { movie -> viewModel.updateFocusedItem(movie, rowTitle, true) },
                                onClick = onMovieClick
                            )
                        }
                    }
                }

                // ── Floating Top Nav (over hero) ──────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                        .align(Alignment.TopCenter)
                ) {
                    NetflixTopNav(
                        state = state,
                        onTabSelect = { viewModel.selectTab(it) },
                        onSearchClick = { navController.navigate("search") },
                        onProfileClick = {}
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Netflix Top Navigation Bar
// ─────────────────────────────────────────────
@Composable
fun NetflixTopNav(
    state: HomeState,
    onTabSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val tabs = listOf("סרטים", "סדרות")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to NfBlack.copy(alpha = 0.9f),
                    0.6f to NfBlack.copy(alpha = 0.5f),
                    1f to Color.Transparent
                )
            )
            .padding(top = 0.dp)
    ) {
        // Top bar row
        TopNavBar(
            rdStatus = true,
            hasNotifications = false,
            onVoiceSearchClick = {},
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick
        )

        // Netflix-style tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 64.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                NetflixNavTab(
                    label = tab,
                    isSelected = state.selectedTab == tab,
                    onClick = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
fun NetflixNavTab(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(
        targetValue = when {
            isSelected -> NfWhite
            isFocused  -> NfWhite
            else       -> NfLightGray
        },
        animationSpec = tween(150)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        // Active underline
        AnimatedVisibility(visible = isSelected) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NfWhite)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Netflix Hero Banner
// ─────────────────────────────────────────────
@Composable
fun NetflixHeroBanner(
    movie: Movie?,
    heroHeight: androidx.compose.ui.unit.Dp,
    onPlayClick: () -> Unit,
    onMoreInfoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // ── Full-bleed background image ────────────────────────────
        AnimatedContent(
            targetState = movie?.backdropUrl ?: movie?.posterUrl,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) }
        ) { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Gradient overlays exactly like Netflix ─────────────────
        // Bottom fade to black
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f   to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.75f to NfBlack.copy(alpha = 0.6f),
                    1f   to NfBlack
                )
            )
        )
        // Left-side fade for text legibility
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f   to NfBlack.copy(alpha = 0.85f),
                    0.55f to NfBlack.copy(alpha = 0.2f),
                    1f   to Color.Transparent
                )
            )
        )
        // Top fade for nav bar
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f   to NfBlack.copy(alpha = 0.5f),
                    0.18f to Color.Transparent
                )
            )
        )

        // ── Hero Content ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 60.dp)
                .fillMaxWidth(0.55f)
        ) {
            // Title
            Text(
                text = movie?.title ?: "",
                color = NfWhite,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 58.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            // Meta row: Year · Rating · Maturity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Match score pill (like Netflix's green %)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF46D369))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("97% Match", color = NfBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = movie?.releaseDate?.take(4) ?: "2024",
                    color = NfLightGray,
                    fontSize = 15.sp
                )
                // HD badge
                Box(
                    modifier = Modifier
                        .border(1.dp, NfLightGray, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("HD", color = NfLightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                // Maturity
                Box(
                    modifier = Modifier
                        .border(1.dp, NfLightGray, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("16+", color = NfLightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Overview
            Text(
                text = movie?.overview ?: "",
                color = NfLightGray,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(32.dp))

            // ── CTA Buttons ────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetflixHeroButton(
                    label = "▶  Play",
                    isPrimary = true,
                    onClick = onPlayClick
                )
                NetflixHeroButton(
                    label = "ℹ  More Info",
                    isPrimary = false,
                    onClick = onMoreInfoClick
                )
            }
        }
    }
}

@Composable
fun NetflixHeroButton(label: String, isPrimary: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isPrimary && isFocused  -> NfWhite.copy(alpha = 0.85f)
            isPrimary               -> NfWhite
            isFocused               -> GlassWhite.copy(alpha = 0.5f)
            else                    -> GlassWhite
        },
        animationSpec = tween(120)
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isPrimary               -> NfBlack
            isFocused               -> NfWhite
            else                    -> NfWhite
        },
        animationSpec = tween(120)
    )

    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor       = bgColor,
            focusedContainerColor = bgColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = if (!isPrimary) ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.5.dp, GlassWhiteBorder)),
            focusedBorder = Border(BorderStroke(1.5.dp, NfWhite))
        ) else ClickableSurfaceDefaults.border(),
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = if (isPrimary) 160.dp else 180.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Netflix Content Row (Poster Cards)
// ─────────────────────────────────────────────
@Composable
fun NetflixContentRow(
    title: String,
    movies: List<Movie>,
    onFocus: (Movie) -> Unit,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {

        // Row title
        Text(
            text = title,
            color = NfWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 64.dp, top = 24.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                NetflixPosterCard(
                    movie = movie,
                    onFocus = { onFocus(movie) },
                    onClick = { onClick(movie.id) }
                )
            }
        }
    }
}

@Composable
fun NetflixPosterCard(movie: Movie, onFocus: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 24.dp else 0.dp,
        animationSpec = tween(200)
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .scale(cardScale)
            .padding(vertical = 20.dp) // room for scale pop
    ) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NfDarkGray,
                focusedContainerColor = NfDarkGray
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f), // manual scale above
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.5.dp, NfWhite), shape = RoundedCornerShape(6.dp))
            ),
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onFocus()
                }
        ) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Title under card (visible only when focused)
        AnimatedVisibility(
            visible = isFocused,
            enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(100))
        ) {
            Text(
                text = movie.title,
                color = NfWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Loading Skeleton
// ─────────────────────────────────────────────
@Composable
fun NetflixLoadingSkeleton() {
    val shimmerAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse)
    )
    val shimmerColor = NfDarkGray.copy(alpha = shimmerAlpha)

    Column(modifier = Modifier.fillMaxSize().background(NfBlack)) {
        // Hero skeleton
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).background(shimmerColor))

        Spacer(Modifier.height(24.dp))

        // Row skeletons
        repeat(2) {
            Box(modifier = Modifier.padding(start = 64.dp).width(180.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) {
                    Box(modifier = Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(shimmerColor))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────
//  Error Screen
// ─────────────────────────────────────────────
@Composable
fun NetflixErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(NfBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(text = message, color = NfLightGray, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onRetry,
                colors = ClickableSurfaceDefaults.colors(containerColor = NetflixRed, focusedContainerColor = NetflixDarkRed),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                modifier = Modifier.height(48.dp).width(160.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Try Again", color = NfWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
