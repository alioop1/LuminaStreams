@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.luminastreams.tv.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.ui.components.TopNavBar

// ── Colour tokens ───────────────────────────────────────────
private val NetflixRed       = Color(0xFFE50914)
private val NetflixDarkRed   = Color(0xFFB20710)
private val NfBlack          = Color(0xFF000000)   // true OLED black
private val NfDarkGray       = Color(0xFF141414)
private val NfLightGray      = Color(0xFFB3B3B3)
private val NfWhite          = Color(0xFFFFFFFF)
private val GlassWhite       = Color(0x33FFFFFF)
private val GlassWhiteBorder = Color(0x55FFFFFF)

// ── Root screen ───────────────────────────────────────────
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: HomeViewModel,
    navController: NavController,
    onMovieClick: (String) -> Unit
) {
    val config = LocalConfiguration.current
    val heroHeight = (config.screenHeightDp * 0.82f).dp

    // Focus requesters for D-pad nav between nav bar and content
    val navBarFocusRequester    = remember { FocusRequester() }
    val firstRowFocusRequester  = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().background(NfBlack)) {
        when {
            state.isLoading -> NetflixLoadingSkeleton()
            state.error != null -> NetflixErrorScreen(state.error) { viewModel.selectTab(state.selectedTab) }
            else -> {
                val displayItem = state.focusedItem ?: state.movieTrending.firstOrNull()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer { firstRowFocusRequester },
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item(key = "hero") {
                        NetflixHeroBanner(
                            movie = displayItem,
                            heroHeight = heroHeight,
                            onPlayClick = { displayItem?.id?.let(onMovieClick) },
                            onMoreInfoClick = { displayItem?.id?.let(onMovieClick) }
                        )
                    }

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
                        if (state.discoveryResults.isNotEmpty())
                            add("🎯 ${state.selectedGenreName}" to state.discoveryResults)
                    }

                    allRows.forEachIndexed { index, (rowTitle, movies) ->
                        item(key = rowTitle) {
                            NetflixContentRow(
                                title = rowTitle,
                                movies = movies,
                                // First content row gets the focus requester so D-pad Down
                                // from TopNav lands here correctly
                                rowModifier = if (index == 0)
                                    Modifier.focusRequester(firstRowFocusRequester)
                                else
                                    Modifier,
                                onFocus = { movie -> viewModel.updateFocusedItem(movie, rowTitle, true) },
                                onClick = onMovieClick
                            )
                        }
                    }
                }

                // Floating TopNav — always on top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                        .align(Alignment.TopCenter)
                ) {
                    NetflixTopNav(
                        state = state,
                        navBarFocusRequester = navBarFocusRequester,
                        firstRowFocusRequester = firstRowFocusRequester,
                        onTabSelect = { viewModel.selectTab(it) },
                        onSearchClick = { navController.navigate("search") },
                        onProfileClick = {}
                    )
                }
            }
        }
    }
}

// ── Top Nav ────────────────────────────────────────────
@Composable
fun NetflixTopNav(
    state: HomeState,
    navBarFocusRequester: FocusRequester,
    firstRowFocusRequester: FocusRequester,
    onTabSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val tabs = listOf("סרטים", "סדרות")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer()
            .background(
                Brush.verticalGradient(
                    0f to NfBlack.copy(alpha = 0.9f),
                    0.6f to NfBlack.copy(alpha = 0.5f),
                    1f to Color.Transparent
                )
            )
    ) {
        TopNavBar(
            rdStatus = true,
            hasNotifications = false,
            onVoiceSearchClick = {},
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick
        )
        // Tab row — D-pad Down moves to firstRowFocusRequester
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
                    onDownPress = { firstRowFocusRequester.requestFocus() },
                    onClick = { onTabSelect(tab) }
                )
            }
        }
    }
}

@Composable
fun NetflixNavTab(
    label: String,
    isSelected: Boolean,
    onDownPress: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(
        targetValue = when {
            isSelected || isFocused -> NfWhite
            else -> NfLightGray
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
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                // D-pad Down from tab → jump to first content row
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.keyCode ==
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                        keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown
                    ) {
                        onDownPress()
                        true
                    } else false
                }
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
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

// ── Hero Banner ───────────────────────────────────────────
@Composable
fun NetflixHeroBanner(
    movie: Movie?,
    heroHeight: androidx.compose.ui.unit.Dp,
    onPlayClick: () -> Unit,
    onMoreInfoClick: () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {

        AnimatedContent(
            targetState = movie?.backdropUrl ?: movie?.posterUrl,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
            label = "hero_bg"
        ) { imageUrl ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    // Cap resolution to save VRAM on 2GB devices
                    .size(1280, 720)
                    .scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(600)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom fade — ends at pure OLED black
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.4f to Color.Transparent,
                0.75f to NfBlack.copy(alpha = 0.6f), 1f to NfBlack
            )
        ))
        // Left fade
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to NfBlack.copy(alpha = 0.85f), 0.55f to NfBlack.copy(alpha = 0.2f), 1f to Color.Transparent
            )
        ))
        // Top fade
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to NfBlack.copy(alpha = 0.5f), 0.18f to Color.Transparent)
        ))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 60.dp)
                .fillMaxWidth(0.55f)
        ) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF46D369))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text("97% Match", color = NfBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                val ratingText = movie?.rating?.let { if (it > 0f) "%.1f ★".format(it) else null } ?: ""
                if (ratingText.isNotEmpty()) Text(ratingText, color = NfLightGray, fontSize = 15.sp)
                Box(Modifier.border(1.dp, NfLightGray, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("HD", color = NfLightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.border(1.dp, NfLightGray, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("16+", color = NfLightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = movie?.overview ?: "",
                color = NfLightGray,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                NetflixHeroButton(label = "▶  Play",      isPrimary = true,  onClick = onPlayClick)
                NetflixHeroButton(label = "ℹ  More Info", isPrimary = false, onClick = onMoreInfoClick)
            }
        }
    }
}

@Composable
fun NetflixHeroButton(label: String, isPrimary: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = when {
            isPrimary && isFocused -> NfWhite.copy(alpha = 0.85f)
            isPrimary              -> NfWhite
            isFocused              -> GlassWhite.copy(alpha = 0.5f)
            else                   -> GlassWhite
        }, animationSpec = tween(120)
    )
    val textColor by animateColorAsState(
        targetValue = if (isPrimary) NfBlack else NfWhite,
        animationSpec = tween(120)
    )
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(containerColor = bgColor, focusedContainerColor = bgColor),
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
            Text(text = label, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Content Row ───────────────────────────────────────────
@Composable
fun NetflixContentRow(
    title: String,
    movies: List<Movie>,
    rowModifier: Modifier = Modifier,
    onFocus: (Movie) -> Unit,
    onClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.padding(vertical = 4.dp).then(rowModifier)) {
        Text(
            text = title,
            color = NfWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 64.dp, top = 24.dp, bottom = 12.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // focusRestorer keeps D-pad Left/Right inside the row and
            // restores last focused card when returning to this row
            modifier = Modifier.focusRestorer()
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
    val context = LocalContext.current

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        // Spring that doesn't overshoot too far — safe for TV edge cards
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            // External scale so the TV focus engine isn't confused
            .scale(cardScale)
            // Vertical padding gives room for scale pop without clipping
            .padding(vertical = 20.dp)
    ) {
        Surface(
            onClick = onClick,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NfDarkGray,
                focusedContainerColor = NfDarkGray
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
            // focusedScale = 1.0f because we handle scale ourselves above
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
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
            // Memory-safe image: capped at 320x480 (poster is never shown bigger)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .size(320, 480)
                    .scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(300)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

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

// ── Loading Skeleton ───────────────────────────────────────
@Composable
fun NetflixLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer_alpha"
    )
    val shimmerColor = NfDarkGray.copy(alpha = shimmerAlpha)

    Column(modifier = Modifier.fillMaxSize().background(NfBlack)) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).background(shimmerColor))
        Spacer(Modifier.height(24.dp))
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

// ── Error Screen ──────────────────────────────────────────
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
