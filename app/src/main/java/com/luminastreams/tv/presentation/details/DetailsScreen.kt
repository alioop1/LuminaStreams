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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var selectedEpisodeIdx by remember { mutableIntStateOf(-1) }
    val returnToEpisodeFR = remember { FocusRequester() }

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

    // Refresh watch progress every time this screen is resumed (e.g. after returning from player)
    // Also check for auto-play next episode request
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(DetailsEvent.RefreshProgress)

                // Check if the player requested auto-play next episode
                val playerPrefs = context.getSharedPreferences("player_context", 0)
                val autoSeason = playerPrefs.getInt("auto_play_season", -1)
                val autoEpisode = playerPrefs.getInt("auto_play_episode", -1)
                if (autoSeason > 0 && autoEpisode > 0) {
                    // Clear immediately so it doesn't re-trigger
                    playerPrefs.edit().remove("auto_play_season").remove("auto_play_episode").apply()

                    // Switch season if needed and trigger scraping
                    if (autoSeason != state.selectedSeason) {
                        onEvent(DetailsEvent.SelectSeason(autoSeason))
                    }
                    showSources = true
                    onEvent(DetailsEvent.InitiateScraping(state.mediaInfo.imdbId, autoSeason, autoEpisode))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showSources, state.availableStreams) {
        if (showSources && state.availableStreams.isNotEmpty()) {
            delay(300)
            runCatching { firstSourceFR.requestFocus() }
        }
    }

    // Restore focus to the episode card after popup closes
    LaunchedEffect(selectedEpisode) {
        if (selectedEpisode == null && selectedEpisodeIdx >= 0) {
            delay(150)
            runCatching { returnToEpisodeFR.requestFocus() }
        }
    }

    BackHandler {
        if (selectedEpisode != null) selectedEpisode = null
        else if (showSources) { showSources = false; onEvent(DetailsEvent.CancelScraping) }
        else onNavigateBack()
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(ColorBgDark), Alignment.Center) { LoadingIndicator() }
        return
    }

    val media = state.mediaInfo

    // ⚡ CUSTOM DPI SCALING FOR 4K TVs (50"-77") — shrinks UI ~26% for crisp, premium look
    val currentDensity = LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density * 0.75f,
        fontScale = currentDensity.fontScale * 0.85f
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        Box(modifier = Modifier.fillMaxSize().background(ColorBgDark)) {

            Crossfade(targetState = currentBackdrop, animationSpec = tween(400), label = "backdrop") { bgUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bgUrl)
                        .size(1920, 1080)
                        .allowHardware(true)
                        .crossfade(true)
                        .build(),
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

// ── Movie Progress Bar ──
                        if (!media.isSeries) {
                            if ((state.contentProgress ?: 0f) >= 0.02f) {
                                Spacer(Modifier.height(24.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.35f)
                                            .height(6.dp)
                                            .background(Color.White.copy(0.15f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(state.contentProgress ?: 0f)
                                                .fillMaxHeight()
                                                .background(
                                                    if (state.contentIsFinished) Color(0xFF32D74B) else ColorAccentIsland,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                                                )
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (state.contentIsFinished) tr("Watched", "נצפה")
                                        else "${((state.contentProgress ?: 0f) * 100).toInt()}% ${tr("watched", "נצפה")}",
                                        color = if (state.contentIsFinished) Color(0xFF32D74B) else ColorTextMain.copy(0.5f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(48.dp))

                        val isPartiallyWatched = (state.contentProgress ?: 0f) >= 0.02f && !state.contentIsFinished
                        ActionIsland(
                            playText = if(isPartiallyWatched) tr("Continue S${state.lastWatchedSeason?:1}:E${state.lastWatchedEpisode?:1}", "המשך צפייה") else tr("Play Now", "נגן עכשיו"),
                            modifier = Modifier
                                .focusRequester(playFR)
                                .focusProperties {
                                    if (media.isSeries && media.totalSeasons > 0) {
                                        down = selectedSeasonFR
                                    }
                                },
                            onPlayClick = {
                                showSources = true
                                // ⚡ FIX: When resuming a series, pass the last watched season+episode
                                // so the scraping engine finds the correct episode, not a generic result.
                                if (media.isSeries && isPartiallyWatched && state.lastWatchedSeason != null && state.lastWatchedEpisode != null) {
                                    onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.lastWatchedSeason, state.lastWatchedEpisode))
                                } else {
                                    onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                }
                            },
                            onSourcesClick = {
                                showSources = true
                                if (media.isSeries && isPartiallyWatched && state.lastWatchedSeason != null && state.lastWatchedEpisode != null) {
                                    onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.lastWatchedSeason, state.lastWatchedEpisode))
                                } else {
                                    onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                }
                            },
                            onTrailerClick = { launchNativeTrailer(context, media.trailerUrl, media.title) },
                            isFavorite = media.isFavorite,
                            onFavClick = { onEvent(DetailsEvent.ToggleFavorite) },
                            isWatched = if (!media.isSeries) state.contentIsFinished else false,
                            onWatchedClick = if (!media.isSeries) {{ 
                                if (state.contentIsFinished) onEvent(DetailsEvent.MarkMovieUnwatched)
                                else onEvent(DetailsEvent.MarkMovieWatched)
                            }} else null
                        )

                        // ── Info Chips Bar ──
                        Spacer(Modifier.height(32.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val chips = buildList {
                                if (media.director.isNotBlank()) add(tr("Director", "במאי") to media.director)
                                if (media.studios.isNotEmpty()) add(tr("Studio", "אולפן") to media.studios.first())
                                if (media.runtimeMinutes > 0) add(tr("Runtime", "משך") to "${media.runtimeMinutes / 60}h ${media.runtimeMinutes % 60}m")
                                if (media.releaseDate.isNotBlank()) add(tr("Release", "שנת יציאה") to media.releaseDate.take(4))
                                if (media.genres.isNotEmpty()) add(tr("Genres", "ז'אנרים") to media.genres.take(2).joinToString(" · "))
                            }
                            chips.forEachIndexed { idx, (label, value) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .background(Color.White.copy(0.06f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .padding(horizontal = 32.dp, vertical = 16.dp)
                                ) {
                                    Text(label.uppercase(), color = ColorTextMain.copy(0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(value, color = ColorTextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (idx < chips.lastIndex) Spacer(Modifier.width(16.dp))
                            }
                        }
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
                                            modifier = when {
                                                idx == selectedEpisodeIdx -> Modifier.focusRequester(returnToEpisodeFR).then(if (idx == 0) Modifier.focusRequester(firstEpisodeFR) else Modifier)
                                                idx == 0 -> Modifier.focusRequester(firstEpisodeFR)
                                                else -> Modifier
                                            },
                                            onFocused = { if(ep.stillUrl.isNotBlank()) currentBackdrop = ep.stillUrl },
                                            onClick = { selectedEpisodeIdx = idx; selectedEpisode = ep }
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



                // --- MORE FROM THIS COLLECTION ---
                if (!media.collectionName.isNullOrBlank() && media.collectionItems.isNotEmpty()) {
                    item {
                        Column {
                            Text(media.collectionName!!, color = ColorTextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 64.dp))
                            Spacer(Modifier.height(24.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                itemsIndexed(media.collectionItems) { _, rec ->
                                    RecommendationCard(rec)
                                }
                            }
                        }
                    }
                }

                // --- MORE WITH {ACTOR} ---
                if (!media.starringActorName.isNullOrBlank() && media.starringItems.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                "${tr("More with", "עוד עם")} ${media.starringActorName}",
                                color = ColorTextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 64.dp)
                            )
                            Spacer(Modifier.height(24.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                itemsIndexed(media.starringItems) { _, rec ->
                                    RecommendationCard(rec)
                                }
                            }
                        }
                    }
                }
            }

// --- PREMIUM SOURCES BOTTOM SHEET (restored to original density) ---
            CompositionLocalProvider(LocalDensity provides currentDensity) {
            AnimatedVisibility(
                visible = showSources,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.zIndex(100f)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)).clickable(remember { MutableInteractionSource() }, null) { showSources = false; onEvent(DetailsEvent.CancelScraping) }, Alignment.BottomCenter) {

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
            } // end source density restore
    }

    // ── Episode Options Popup ───────────────────────────────────────
    selectedEpisode?.let { ep ->
        val epPlayFR = remember { FocusRequester() }

        LaunchedEffect(ep) { delay(200); runCatching { epPlayFR.requestFocus() } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "E${ep.episodeNumber} • ${ep.title}",
                    color = ColorTextMain, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, modifier = Modifier.padding(bottom = 8.dp)
                )

                // ▶ Play — auto-focused
                Surface(
                    onClick = {
                        selectedEpisode = null
                        showSources = true
                        onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber))
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = ColorAccentIsland,
                        contentColor = ColorTextMain,
                        focusedContainerColor = ColorTextMain,
                        focusedContentColor = Color.Black
                    ),
                    scale = ClickableSurfaceDefaults.scale(1.05f),
                    modifier = Modifier.fillMaxWidth().height(64.dp).focusRequester(epPlayFR)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Play Episode", "הפעל פרק"), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }

                // ✓ Toggle Watched
                Surface(
                    onClick = {
                        if (ep.hasWatched) onEvent(DetailsEvent.MarkEpisodeUnwatched(ep.seasonNumber, ep.episodeNumber))
                        else onEvent(DetailsEvent.MarkEpisodeWatched(ep.seasonNumber, ep.episodeNumber))
                        selectedEpisode = null
                        // Focus restore happens via LaunchedEffect below
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(0.1f),
                        contentColor = ColorTextMain,
                        focusedContainerColor = if (ep.hasWatched) Color(0xFF32D74B) else Color.White.copy(0.2f),
                        focusedContentColor = if (ep.hasWatched) Color.White else ColorTextMain
                    ),
                    scale = ClickableSurfaceDefaults.scale(1.05f),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(if (ep.hasWatched) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (ep.hasWatched) tr("Mark as Unwatched", "סמן כלא נצפה")
                            else tr("Mark as Watched", "סמן כנצפה"),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                }

                // ✕ Cancel
                Surface(
                    onClick = { selectedEpisode = null },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(0.5f),
                        focusedContainerColor = Color.White.copy(0.1f),
                        focusedContentColor = ColorTextMain
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(tr("Cancel", "ביטול"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    }
}
