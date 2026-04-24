@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class, ExperimentalAnimationApi::class
)
@file:Suppress("UsePropertyAccessSyntax", "DEPRECATION")

package com.luminastreams.tv.presentation.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminastreams.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import java.util.Locale
import kotlinx.coroutines.launch

// 🎨 PALETTE
private val ColorBgDark = Color(0xFF050507)
private val ColorTextMain = Color(0xFFFFFFFF)
private val ColorAccentIsland = Color(0xFFE50914)

@Composable
fun tr(en: String, he: String): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en

@Composable
fun DetailsScreen(
    state: DetailsScreenState,
    onEvent: (DetailsEvent) -> Unit,
    onPlayDirectUrl: (String, String, String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var showSources by remember { mutableStateOf(false) }

    // FOCUS REQUESTERS
    val playFR = remember { FocusRequester() }
    val backFR = remember { FocusRequester() }
    val selectedSeasonFR = remember { FocusRequester() }
    val firstEpisodeFR = remember { FocusRequester() }
    val firstCastFR = remember { FocusRequester() }
    val firstSourceFR = remember { FocusRequester() }

    var currentBackdrop by remember { mutableStateOf(state.mediaInfo.backdropUrl) }
    LaunchedEffect(state.mediaInfo.backdropUrl) { currentBackdrop = state.mediaInfo.backdropUrl.ifBlank { state.mediaInfo.posterUrl } }

    LaunchedEffect(state.readyToPlayUrl) {
        state.readyToPlayUrl?.let { url ->
            showSources = false
            onPlayDirectUrl(url, state.mediaInfo.imdbId, state.mediaInfo.title, state.mediaInfo.backdropUrl, state.mediaInfo.posterUrl, state.mediaInfo.logoUrl ?: "")
            onEvent(DetailsEvent.ClearPlayUrl)
        }
    }

    LaunchedEffect(state.isLoadingData) { if (!state.isLoadingData) { delay(250); runCatching { playFR.requestFocus() } } }

    LaunchedEffect(showSources, state.availableStreams) {
        if (showSources && state.availableStreams.isNotEmpty()) {
            delay(300)
            runCatching { firstSourceFR.requestFocus() }
        }
    }

    BackHandler {
        if (showSources) { showSources = false; onEvent(DetailsEvent.CancelScraping) }
        else onNavigateBack()
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(ColorBgDark), Alignment.Center) { LoadingIndicator() }
        return
    }

    val media = state.mediaInfo

    // ⚡ CUSTOM DPI SCALING FOR 77" OLED (Shrinks UI by 20%)
    val currentDensity = LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density * 1.0f,
        fontScale = currentDensity.fontScale * 1.0f
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        Box(modifier = Modifier.fillMaxSize().background(ColorBgDark)) {

            Crossfade(targetState = currentBackdrop, animationSpec = tween(400), label = "backdrop") { bgUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(bgUrl).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.5f)
                )
            }

            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                colors = listOf(Color.Transparent, ColorBgDark.copy(0.8f), ColorBgDark),
                startY = 300f
            )))

            LazyColumn(
                contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(64.dp),
                modifier = Modifier.fillMaxSize().focusGroup()
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                        Box(Modifier.fillMaxWidth()) {
                            PremiumIconButton(
                                icon = if (isRtl) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                modifier = Modifier.align(Alignment.TopStart).focusRequester(backFR),
                                onClick = onNavigateBack
                            )

                            if (!media.logoUrl.isNullOrEmpty()) {
                                AsyncImage(model = media.logoUrl, contentDescription = media.title, modifier = Modifier.heightIn(max = 140.dp).fillMaxWidth(0.5f).align(Alignment.Center), contentScale = ContentScale.Fit)
                            } else {
                                Text(media.title, color = ColorTextMain, fontSize = 72.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 76.sp, modifier = Modifier.fillMaxWidth(0.7f).align(Alignment.Center))
                            }
                        }

                        Spacer(Modifier.height(48.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.height(110.dp)) {
                            BentoCard(title = tr("RATING", "דירוג"), value = "★ ${String.format(Locale.US, "%.1f", media.tmdbRating)}")
                            BentoCard(title = media.releaseDate.take(4).ifEmpty { "YEAR" }, value = media.ageRating.ifEmpty { "NR" })
                            BentoCard(title = tr("GENRE", "ז'אנר"), value = media.displayGenres.split("•").firstOrNull()?.trim() ?: "Movie", isWide = true)
                            BentoCard(title = tr("QUALITY", "איכות"), value = state.bestSourceHint?.substringBefore("•")?.trim() ?: "1080p")
                        }

                        Spacer(Modifier.height(48.dp))
                        Text(media.overview, color = ColorTextMain.copy(0.8f), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.65f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(48.dp))

                        val isPartiallyWatched = (state.contentProgress ?: 0f) >= 0.02f && !state.contentIsFinished
                        ActionIsland(
                            playText = if(isPartiallyWatched) tr("Continue S${state.lastWatchedSeason?:1}:E${state.lastWatchedEpisode?:1}", "המשך צפייה") else tr("Play Now", "נגן עכשיו"),
                            // ⚡ THE FIX: Hard-wire the D-PAD DOWN directly to the Season tabs!
                            modifier = Modifier
                                .focusRequester(playFR)
                                .focusProperties {
                                    if (media.isSeries && media.totalSeasons > 0) {
                                        down = selectedSeasonFR
                                    }
                                },
                            onPlayClick = { showSources = true; onEvent(DetailsEvent.InitiateScraping(media.imdbId)) },
                            onSourcesClick = { showSources = true; onEvent(DetailsEvent.InitiateScraping(media.imdbId)) },
                            onTrailerClick = { launchNativeTrailer(context, media.trailerUrl, media.title) },
                            isFavorite = media.isFavorite,
                            onFavClick = { onEvent(DetailsEvent.ToggleFavorite) }
                        )
                    }
                }

                // --- SEASONS & EPISODES ---
                if (media.isSeries) {

                    // 1. Season Picker Row
                    if (media.totalSeasons > 0) {
                        item {
                            Column {
                                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 64.dp)) {
                                    Text(tr("Seasons", "עונות"), color = ColorTextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(16.dp))
                                    Text("${media.totalSeasons} ${tr("Available", "זמינות")}", color = ColorTextMain.copy(0.5f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                                }
                                Spacer(Modifier.height(24.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 64.dp),
                                    modifier = Modifier.focusGroup().focusRestorer { selectedSeasonFR }
                                ) {
                                    items(media.totalSeasons) { idx ->
                                        val seasonNum = idx + 1
                                        val isTargetFocus = (state.selectedSeason == seasonNum) || (state.selectedSeason <= 0 && idx == 0)

                                        SeasonPill(
                                            seasonNumber = seasonNum,
                                            isSelected = state.selectedSeason == seasonNum,
                                            modifier = Modifier
                                                .then(if (isTargetFocus) Modifier.focusRequester(selectedSeasonFR) else Modifier)
                                                // ⚡ THE "DOWN" FIX: Hard-wire D-Pad DOWN to Episode 1
                                                .focusProperties { down = firstEpisodeFR },
                                            onClick = {
                                                // 1. Tell the ViewModel to change the season
                                                onEvent(DetailsEvent.SelectSeason(seasonNum))

                                                // ⚡ THE "OK" FIX: Wait for the episodes to load, then jump focus to Episode 1
                                                scope.launch {
                                                    delay(150)
                                                    runCatching { firstEpisodeFR.requestFocus() }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Episodes Row
                    if (state.episodes.isNotEmpty()) {
                        item {
                            Column {
                                Text(tr("Episodes", "פרקים"), color = ColorTextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 64.dp))
                                Spacer(Modifier.height(24.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    contentPadding = PaddingValues(horizontal = 64.dp),
                                    modifier = Modifier.focusGroup().focusRestorer { firstEpisodeFR }
                                ) {
                                    itemsIndexed(state.episodes) { idx, ep ->
                                        EpisodeCardOptimized(
                                            episode = ep,
                                            fallback = media.backdropUrl,
                                            modifier = if (idx == 0) Modifier.focusRequester(firstEpisodeFR) else Modifier,
                                            onFocused = { if(ep.stillUrl.isNotBlank()) currentBackdrop = ep.stillUrl },
                                            onClick = { showSources = true; onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CAST ROW ---
                if (media.cast.isNotEmpty()) {
                    item {
                        Column {
                            Text(tr("Cast & Crew", "שחקנים"), color = ColorTextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 64.dp))
                            Spacer(Modifier.height(24.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                modifier = Modifier.focusGroup().focusRestorer { firstCastFR }
                            ) {
                                itemsIndexed(media.cast) { idx, a ->
                                    CastCardOptimized(
                                        actor = a,
                                        modifier = if (idx == 0) Modifier.focusRequester(firstCastFR) else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }

// --- PREMIUM SOURCES BOTTOM SHEET ---
            AnimatedVisibility(
                visible = showSources,
                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400, easing = FastOutLinearInEasing)),
                modifier = Modifier.zIndex(100f)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(remember { MutableInteractionSource() }, null) { showSources = false; onEvent(DetailsEvent.CancelScraping) }, Alignment.BottomCenter) {

                    // ⚡ FIX: Removed fillMaxHeight so it hugs the bottom elegantly
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0A0C), Color(0xFF0A0A0C), Color(0xFF0A0A0C))))
                            .padding(top = 40.dp)
                    ) {
                        // Header
                        Row(Modifier.padding(horizontal = 64.dp), verticalAlignment = Alignment.Bottom) {
                            Text(tr("Available Sources", "מקורות זמינים"), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                            Spacer(Modifier.width(16.dp))
                            Text(tr("Select a stream to begin playback", "בחר מקור כדי להתחיל"), color = Color.White.copy(0.5f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        Spacer(Modifier.height(40.dp))

                        when (val st = state.scrapingStatus) {
                            is ScrapingStatus.Searching, is ScrapingStatus.ResolvingDebrid -> {
                                Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) { LoadingIndicator() }
                            }
                            is ScrapingStatus.Error -> {
                                Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) { Text(st.message, color = ColorAccentIsland, fontSize = 24.sp) }
                            }
                            else -> {
                                // ⚡ FIX: Switched back to LazyRow for a single, clean horizontal ribbon!
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 64.dp),
                                    modifier = Modifier.fillMaxWidth().focusGroup().focusRestorer { firstSourceFR }
                                ) {
                                    itemsIndexed(state.availableStreams) { idx, stream ->
                                        DetailedSourceCube(
                                            stream = stream,
                                            logoUrl = media.logoUrl.takeIf { !it.isNullOrBlank() } ?: media.backdropUrl.takeIf { !it.isBlank() } ?: media.posterUrl,
                                            modifier = if (idx == 0) Modifier.focusRequester(firstSourceFR) else Modifier,
                                            onClick = { onEvent(DetailsEvent.ResolveAndPlayStream(stream)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
    }
}
