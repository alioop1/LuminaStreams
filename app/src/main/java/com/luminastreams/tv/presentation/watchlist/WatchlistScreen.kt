@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.luminastreams.tv.presentation.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.R
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.presentation.home.PosterCard
import kotlinx.coroutines.delay

private val BG = Color(0xFF070707)

@Composable
fun tr(en: String, he: String): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en

@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
    onNavigateBack: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }

    val backFR = remember { FocusRequester() }
    val gridFR = remember { FocusRequester() }

    // Auto-select the first movie for the backdrop when the list loads
    LaunchedEffect(movies.size) {
        if (movies.isNotEmpty() && focusedMovie == null) {
            focusedMovie = movies.first()
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { backFR.requestFocus() }
    }

    BackHandler { onNavigateBack() }

    Box(Modifier.fillMaxSize().background(BG)) {

        // ── 1. DYNAMIC CINEMATIC BACKDROP ──
        if (movies.isNotEmpty()) {
            WatchlistBackdrop(focusedMovie)
        }

        Column(Modifier.fillMaxSize()) {

            // ── 2. HEADER & TOP NAV ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 40.dp, end = 48.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp).focusRequester(backFR),
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp))
                }

                Spacer(Modifier.width(32.dp))
                Image(
                    painter = painterResource(id = R.drawable.logo_lumina_unified),
                    contentDescription = "Lumina Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(34.dp)
                )
                Spacer(Modifier.width(32.dp))
                Box(Modifier.width(2.dp).height(28.dp).background(Color(0x33FFFFFF)))
                Spacer(Modifier.width(32.dp))
                Text(
                    text = tr("My Watchlist", "הרשימה שלי"),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // ── 3. DYNAMIC HERO INFO ──
            Box(
                modifier = Modifier
                    .weight(0.7f) // Takes up top area
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                if (movies.isNotEmpty()) {
                    WatchlistHeroInfo(focusedMovie)
                }
            }

            // ── 4. POSTER GRID ──
            Box(
                modifier = Modifier
                    .weight(1f) // Takes up bottom area
                    .fillMaxWidth()
            ) {
                if (movies.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                            Text("🎬", fontSize = 64.sp)
                            Spacer(Modifier.height(16.dp))
                            Text(tr("Your Watchlist is Empty", "הרשימה שלך ריקה"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(tr("Save shows and movies to watch later.", "סרטים וסדרות שתשמור יופיעו כאן."), color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 138.dp),
                        state = rememberLazyGridState(),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(gridFR)
                            .focusGroup()
                            .focusRestorer()
                            .onPreviewKeyEvent { ev ->
                                // STRICT FOCUS ROUTING: If pressing UP on the top row of the grid, force focus to the Back Button!
                                if (ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown) {
                                    runCatching { backFR.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                                false
                            }
                    ) {
                        itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
                            PosterCard(
                                movie = movie,
                                modifier = Modifier
                                    .focusProperties {
                                        // Prevents native focus from trapping you inside the grid
                                        up = backFR
                                    }
                                    .onFocusChanged { if (it.isFocused) focusedMovie = movie },
                                onClick = { onMovieClick(movie.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  COMPONENTS
// ══════════════════════════════════════════════════════════════════

@Composable
private fun WatchlistBackdrop(hero: Movie?) {
    val ctx = LocalContext.current
    val heroUrl = hero?.backdropUrl?.takeIf { it.isNotBlank() } ?: hero?.posterUrl
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    AnimatedContent(
        targetState = heroUrl,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "backdrop_fade"
    ) { url ->
        Box(Modifier.fillMaxSize().background(BG)) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(url)
                        .size(coil.size.Size(1280, 720))
                        .crossfade(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 0 else 500)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
                )
            }

            // Cinematic Shadows (Fades out the bottom so the grid is perfectly readable)
            Box(Modifier.fillMaxSize()
                .background(Brush.verticalGradient(0.0f to Color(0x99000000), 0.2f to Color.Transparent))
                .background(
                    Brush.horizontalGradient(
                        *if (isRtl) arrayOf(0.0f to Color(0xDD000000), 0.60f to Color.Transparent)
                        else arrayOf(0.40f to Color.Transparent, 1.0f to Color(0xDD000000))
                    )
                )
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 0.8f to BG, 1.0f to BG))
            )
        }
    }
}

@Composable
private fun MetaDot() = Text("  ·  ", color = Color(0x55FFFFFF), fontSize = 14.sp)

@Composable
private fun WatchlistHeroInfo(hero: Movie?) {
    AnimatedContent(
        targetState = hero,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "hero_info_fade"
    ) { m ->
        if (m != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                val tsz = when { m.title.length > 26 -> 32.sp; m.title.length > 16 -> 40.sp; else -> 48.sp }
                Text(
                    text = m.title,
                    color = Color.White,
                    fontSize = tsz,
                    fontWeight = FontWeight.Black,
                    lineHeight = (tsz.value * 1.1f).sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.year > 0) { Text(m.year.toString(), color = Color(0xCCFFFFFF), fontSize = 15.sp); MetaDot() }
                    if (m.genre.isNotBlank()) { Text(m.genre, color = Color(0xCCFFFFFF), fontSize = 15.sp); MetaDot() }
                    Text(if (m.mediaType == "tv") tr("TV Series", "סדרה") else tr("Movie", "סרט"), color = Color(0xCCFFFFFF), fontSize = 15.sp)

                    if (m.rating > 0f) {
                        MetaDot()
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("IMDb", color = Color(0xFF141414), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text("%.1f".format(m.rating), color = Color(0xFF141414), fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                if (m.overview.isNotBlank()) {
                    Text(
                        text = m.overview,
                        color = Color(0x99FFFFFF),
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 700.dp)
                    )
                }
            }
        }
    }
}