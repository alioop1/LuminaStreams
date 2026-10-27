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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luminastreams.tv.core.tr

// ðŸŽ¨ PALETTE
private val ColorBgDark = Color(0xFF050507)

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) activeColor.copy(0.3f) else Color.White.copy(0.06f),
            focusedContainerColor = if (isActive) activeColor else Color.White,
            contentColor = if (isActive) activeColor else Color.White.copy(0.6f),
            focusedContentColor = if (isActive) Color.White else Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = if (isActive) activeColor.copy(0.5f) else Color.White.copy(0.25f), elevation = 16.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 22.dp)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun DetailsScreen(
    state: DetailsScreenState,
    onEvent: (DetailsEvent) -> Unit,
    onPlayDirectUrl: (String, String, String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetails: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showSources by remember { mutableStateOf(false) }
    val filterFocusRequester = remember { FocusRequester() }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var selectedEpisodeIdx by remember { mutableIntStateOf(-1) }
    var focusedPreviewEpisode by remember { mutableStateOf<Episode?>(null) }
    val returnToEpisodeFR = remember { FocusRequester() }
    var showSeasonPopup by remember { mutableStateOf(false) }

    // FOCUS REQUESTERS
    val playFR = remember { FocusRequester() }
    val backFR = remember { FocusRequester() }
    val firstSeasonFR = remember { FocusRequester() }
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
    var isResumed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = (event == Lifecycle.Event.ON_RESUME)
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(DetailsEvent.RefreshProgress)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isResumed) {
        if (isResumed && state.mediaInfo.imdbId.isNotBlank()) {
            val playerPrefs = context.getSharedPreferences("player_context", 0)
            val autoSeason = playerPrefs.getInt("auto_play_season", -1)
            val autoEpisode = playerPrefs.getInt("auto_play_episode", -1)
            if (autoSeason > 0 && autoEpisode > 0) {
                playerPrefs.edit().remove("auto_play_season").remove("auto_play_episode").apply()
                if (autoSeason != state.selectedSeason) {
                    onEvent(DetailsEvent.SelectSeason(autoSeason))
                }
                showSources = true
                onEvent(DetailsEvent.InitiateScraping(state.mediaInfo.imdbId, autoSeason, autoEpisode))
            }
        }
    }

    LaunchedEffect(showSources, state.availableStreams) {
        if (showSources && state.availableStreams.isNotEmpty()) {
            delay(500) // Wait for bottom sheet animation to finish
            runCatching { firstSourceFR.requestFocus() }
            delay(250) // Double check focus
            runCatching { firstSourceFR.requestFocus() }
        }
    }

    // Restore focus to the season pill after episode popup closes
    LaunchedEffect(selectedEpisode) {
        if (selectedEpisode == null && selectedEpisodeIdx >= 0) {
            delay(150)
            runCatching { firstSeasonFR.requestFocus() }
        }
    }

    // After a season change loads new episodes, auto-open the popup
    LaunchedEffect(state.selectedSeason) {
        selectedEpisodeIdx = -1
    }

    BackHandler {
        if (selectedEpisode != null) selectedEpisode = null
        else if (showSeasonPopup) showSeasonPopup = false
        else if (showSources) { showSources = false; onEvent(DetailsEvent.CancelScraping) }
        else onNavigateBack()
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(ColorBgDark), Alignment.Center) { LoadingIndicator() }
        return
    }

    val media = state.mediaInfo
    val currentDensity = LocalDensity.current
    val scaledDensity = androidx.compose.ui.unit.Density(density = currentDensity.density * 0.85f, fontScale = currentDensity.fontScale * 0.85f)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity
    ) {
        Box(modifier = Modifier.fillMaxSize().background(ColorBgDark)) {
            // ── FULL-BLEED CINEMATIC BACKDROP ──
            Crossfade(targetState = currentBackdrop, animationSpec = tween(600), label = "backdrop") { bgUrl ->
                val highResUrl = bgUrl.replace("/w780/", "/original/").replace("/w1280/", "/original/").replace("/w500/", "/original/")
                AsyncImage(
                    model = ImageRequest.Builder(context).data(highResUrl).size(3840, 2160).allowHardware(true).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Horizontal fade: physical Left is Transparent (for art), physical Right is Dark (for RTL text)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Transparent, ColorBgDark.copy(0.85f), ColorBgDark)
            )))
            // Bottom fade for episodes section
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color.Transparent, ColorBgDark.copy(0.9f), ColorBgDark),
                startY = 0f
            )))

            // Trailer overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = !media.trailerUrl.isNullOrBlank(),
                enter = fadeIn(tween(1500, delayMillis = 2000)),
                exit = fadeOut(tween(800)),
                modifier = Modifier.fillMaxSize()
            ) {
                BackgroundTrailerView(videoId = media.trailerUrl.orEmpty(), modifier = Modifier.fillMaxSize())
            }

        // ── GLASS-MORPHIC CINEMATIC HUB ──
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 72.dp, end = 72.dp, bottom = 24.dp).focusGroup()
            ) {
                // ── BACK BUTTON ──
                PremiumIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    modifier = Modifier.focusRequester(backFR),
                    onClick = onNavigateBack
                )
                Spacer(Modifier.height(16.dp))

                // ── TOP SECTION ──
                Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    // ═══ RIGHT (RTL) = INFO COLUMN ═══
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Logo or Title
                        if (!media.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = media.logoUrl, contentDescription = media.title,
                                modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth(0.8f),
                                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart
                            )
                        } else {
                            PrestigeTitle(title = media.title)
                        }

                        // ── 1. METADATA ROW (PREMIUM) ──
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // IMDb Rating
                            if (media.imdbRating > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = ColorGoldMain, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f", media.imdbRating),
                                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            val dot = @Composable { Text("•", color = Color.White.copy(0.4f), fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                            if (media.imdbRating > 0 && media.releaseDate.isNotBlank()) dot()

                            // Year
                            val releaseYear = if (media.releaseDate.isNotBlank()) media.releaseDate.take(4) else ""
                            if (releaseYear.isNotBlank()) {
                                Text(releaseYear, color = Color.White.copy(0.85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (releaseYear.isNotBlank() && media.formattedRuntime.isNotBlank()) dot()

                            // Runtime
                            if (media.formattedRuntime.isNotBlank()) {
                                Text(media.formattedRuntime, color = Color.White.copy(0.85f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (media.formattedRuntime.isNotBlank() && media.ageRating.isNotBlank()) dot()

                            // Age Rating (Apple TV style badge)
                            if (media.ageRating.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(media.ageRating.uppercase(), color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                            }

                            if (media.ageRating.isNotBlank() && media.genres.isNotEmpty()) dot()

                            // Genres
                            if (media.genres.isNotEmpty()) {
                                Text(
                                    text = media.genres.take(2).joinToString(" • "),
                                    color = Color.White.copy(0.7f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // ── 2. ACTION BUTTONS ──
                        val isPartiallyWatched = (state.contentProgress ?: 0f) >= 0.02f && !state.contentIsFinished
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusGroup().focusRestorer { playFR }) {
                            val watchText = if (media.isSeries) {
                                if (isPartiallyWatched && state.lastWatchedSeason != null && state.lastWatchedEpisode != null)
                                    "${tr("WATCH", "צפה")} S${state.lastWatchedSeason} E${state.lastWatchedEpisode}"
                                else tr("WATCH S1 E1", "צפה ע1 פ1")
                            } else {
                                if (isPartiallyWatched) tr("CONTINUE", "המשך צפייה") else tr("WATCH NOW", "צפה עכשיו")
                            }
                            
                            // Primary Play Button - Premium White
                            Surface(
                                onClick = {
                                    showSources = true
                                    if (media.isSeries && isPartiallyWatched && state.lastWatchedSeason != null && state.lastWatchedEpisode != null)
                                        onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.lastWatchedSeason, state.lastWatchedEpisode))
                                    else onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(0.95f), 
                                    contentColor = Color.Black, 
                                    focusedContainerColor = Color.White, 
                                    focusedContentColor = Color.Black
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Color.White.copy(0.3f), elevation = 16.dp)),
                                modifier = Modifier.focusRequester(playFR).height(56.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 24.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(watchText, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 0.5.sp)
                                }
                            }
                            
                            PrestigeButton(text = tr("TRAILER", "טריילר"), icon = Icons.Default.PlayArrow, onClick = { launchNativeTrailer(context, media.trailerUrl, media.title) })
                            PrestigeButton(
                                text = if (media.isFavorite) tr("MY LIST", "הרשימה שלי") else tr("ADD", "הוסף"),
                                icon = if (media.isFavorite) Icons.Default.Check else Icons.Default.Add,
                                onClick = { onEvent(DetailsEvent.ToggleFavorite) }
                            )
                        }

                        // ── 3. SYNOPSIS (Floating) ──
                        val synopsisText = media.overview.trim().let { if (it.startsWith(".")) it.substring(1).trim() else it }
                        
                        Text(
                            text = synopsisText,
                            color = Color.White.copy(0.85f),
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            style = androidx.compose.ui.text.TextStyle(
                                textDirection = androidx.compose.ui.text.style.TextDirection.Content, // Fix RTL punctuation alignment
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(0.8f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                                    blurRadius = 8f
                                )
                            ),
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )

                        // ── 4. SEASON PILLS ──
                        if (media.isSeries && media.totalSeasons > 0) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.focusGroup().focusRestorer { firstSeasonFR }.focusProperties { up = playFR },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                items(media.totalSeasons) { idx ->
                                    val sn = idx + 1
                                    val isSel = sn == state.selectedSeason
                                    Surface(
                                        onClick = {
                                            onEvent(DetailsEvent.SelectSeason(sn))
                                            showSeasonPopup = true
                                        },
                                        scale = ClickableSurfaceDefaults.scale(1.05f),
                                        modifier = if (idx == 0) Modifier.focusRequester(firstSeasonFR) else Modifier,
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isSel) Color.White.copy(0.2f) else Color.White.copy(0.08f),
                                            contentColor = if (isSel) Color.White else Color.White.copy(0.6f),
                                            focusedContainerColor = ColorGoldMain.copy(0.3f),
                                            focusedContentColor = Color.White
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            border = Border(BorderStroke(1.dp, if (isSel) Color.White.copy(0.4f) else Color.White.copy(0.1f))),
                                            focusedBorder = Border(BorderStroke(2.dp, ColorGoldMain))
                                        ),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                                        glow = ClickableSurfaceDefaults.glow(
                                            focusedGlow = Glow(elevationColor = ColorGoldMain.copy(0.3f), elevation = 20.dp)
                                        )
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                tr("Season", "עונה"),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White.copy(0.7f)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "$sn",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ═══ LEFT (RTL) = POSTER ═══
                    Box(modifier = Modifier.weight(0.75f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.fillMaxHeight(0.95f).aspectRatio(2f / 3f)
                                .shadow(32.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp)).background(Color.Black)
                        ) {
                            AsyncImage(model = media.posterUrl.ifBlank { currentBackdrop }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }


                // ── 5. CAST ROW (AT THE BOTTOM OF THE SCREEN) ──
                if (media.cast.isNotEmpty()) {
                    var focusedCastMember by remember { mutableStateOf<CastMember?>(null) }
                    val displayName = focusedCastMember?.name ?: media.cast.take(4).joinToString(", ") { it.name }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = tr("Cast", "שחקנים"),
                        color = Color.White.copy(0.9f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(0.5f), blurRadius = 8f)),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(24.dp), 
                        modifier = Modifier.focusGroup().focusRestorer { firstCastFR }.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(media.cast.take(12)) { idx, actor ->
                            PrestigeCastCircle(
                                actor = actor, 
                                modifier = if (idx == 0) Modifier.focusRequester(firstCastFR) else Modifier,
                                onClick = { onEvent(DetailsEvent.SelectActor(actor.id)) },
                                onFocus = { focusedCastMember = actor }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp).padding(start = 8.dp)) {
                        Text(displayName, color = Color.White.copy(0.7f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // (Episodes are now shown via the Season Popup overlay)
            }
        }

        // --- PREMIUM SOURCES DIALOG ---
        AnimatedVisibility(
            visible = showSources,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(350)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250)),
            modifier = Modifier.zIndex(100f)
        ) {
            Box(Modifier.fillMaxSize()
                .background(Color.Black.copy(0.88f))
                .clickable(remember { MutableInteractionSource() }, null) { showSources = false; onEvent(DetailsEvent.CancelScraping) }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyUp && (ev.key == Key.Back || ev.key == Key.Escape)) {
                        showSources = false
                        onEvent(DetailsEvent.CancelScraping)
                        true
                    } else false
                },
                Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.9f)
                        .background(Color(0xFF08080B).copy(0.95f), RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Header Row ──
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Available Sources", "מקורות זמינים"),
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Spacer(Modifier.width(20.dp))
                        // Source count pill
                        if (state.availableStreams.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(0.1f), RoundedCornerShape(50))
                                    .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(50))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${state.availableStreams.size} ${tr("streams", "מקורות")}",
                                    color = Color.White.copy(0.7f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = tr("Select a stream to begin playback", "בחר מקור כדי להתחיל"),
                            color = Color.White.copy(0.4f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // ── Gradient separator ──
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(1.dp)
                            .background(Brush.horizontalGradient(listOf(
                                Color.Transparent,
                                Color.White.copy(0.12f),
                                Color.White.copy(0.12f),
                                Color.Transparent
                            )))
                    )
                    Spacer(Modifier.height(16.dp))

                    // ── Smart Filter Bar ──
                    if (state.availableStreams.isNotEmpty() || state.trackerFilters.isNotEmpty() || state.languageFilters.isNotEmpty()) {
                        // Derive unique values from ALL streams
                        val allTrackers = remember(state.availableStreams) {
                            state.availableStreams.map { it.trackerName }.distinct().sorted()
                        }
                        val allLanguages = remember(state.availableStreams) {
                            state.availableStreams.flatMap { it.audioLanguages }
                                .filter { it != AudioLanguage.UNKNOWN }
                                .distinct()
                                .sortedBy { it.ordinal }
                        }
                        val hasActiveFilters = state.trackerFilters.isNotEmpty() ||
                                state.languageFilters.isNotEmpty() ||
                                state.showCachedOnly ||
                                state.qualityFilter != null ||
                                state.streamSortMode != StreamSortMode.DEFAULT

                        Column(
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 32.dp)
                                .focusGroup()
                                .focusProperties { enter = { filterFocusRequester } },
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // ── Row 1: Quality ──
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("Quality", "איכות"),
                                    color = Color.White.copy(0.4f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.width(4.dp))
                                // Quality pills
                                val qualities = listOf<Pair<String, StreamQuality?>>("4K" to StreamQuality.UHD_4K, "1080p" to StreamQuality.FHD_1080P)
                                qualities.forEach { (label, q) ->
                                    FilterChip(
                                        label = label,
                                        isActive = state.qualityFilter == q,
                                        activeColor = Color(0xFFFFA000),
                                        modifier = if (label == "4K") Modifier.focusRequester(filterFocusRequester) else Modifier
                                    ) { onEvent(DetailsEvent.SetQualityFilter(q)) }
                                }

                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.12f)))
                                Spacer(Modifier.width(6.dp))

                                // Dolby Vision toggle
                                FilterChip(
                                    label = "Dolby Vision",
                                    isActive = state.showDvOnly,
                                    activeColor = Color(0xFFFF3B30) // Red/orange active color for DV
                                ) { onEvent(DetailsEvent.SetDvOnly(!state.showDvOnly)) }
                            }

                            // ── Row 2: Tracker + Language multi-select ──
                            if (allTrackers.size > 1 || allLanguages.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Tracker chips
                                    if (allTrackers.size > 1) {
                                        Text(
                                            text = tr("Tracker", "טראקר"),
                                            color = Color.White.copy(0.4f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        allTrackers.take(8).forEach { tracker ->
                                            val isOn = tracker in state.trackerFilters
                                            val tColor = Color(TrackerParser.trackerColor(tracker))
                                            FilterChip(tracker, isOn, tColor) {
                                                onEvent(DetailsEvent.ToggleTrackerFilter(tracker))
                                            }
                                        }
                                    }

                                    // Divider
                                    if (allTrackers.size > 1 && allLanguages.size > 1) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.12f)))
                                        Spacer(Modifier.width(6.dp))
                                    }

                                    // Language chips
                                    val filteredLanguages = allLanguages.filter { it == AudioLanguage.RUSSIAN || it == AudioLanguage.ENGLISH || it == AudioLanguage.HEBREW }
                                    if (filteredLanguages.isNotEmpty()) {
                                        Text(
                                            text = tr("Audio", "שפה"),
                                            color = Color.White.copy(0.4f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        filteredLanguages.forEach { lang ->
                                            val isOn = lang in state.languageFilters
                                            val langColor = when (lang) {
                                                AudioLanguage.RUSSIAN    -> Color(0xFF5C7CFA)
                                                AudioLanguage.ENGLISH    -> Color(0xFF69DB7C)
                                                AudioLanguage.HEBREW     -> Color(0xFF748FFC)
                                                else -> Color.White
                                            }
                                            FilterChip("${lang.flag} ${lang.displayName}", isOn, langColor) {
                                                onEvent(DetailsEvent.ToggleLanguageFilter(lang))
                                            }
                                        }
                                    }

                                    // Clear All button
                                    if (hasActiveFilters) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            onClick = { onEvent(DetailsEvent.ClearAllFilters) },
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = Color(0xFFFF3B30).copy(0.2f),
                                                focusedContainerColor = Color(0xFFFF3B30),
                                                contentColor = Color(0xFFFF6B6B),
                                                focusedContentColor = Color.White
                                            ),
                                            border = ClickableSurfaceDefaults.border(
                                                border = Border.None,
                                                focusedBorder = Border.None
                                            ),
                                            glow = ClickableSurfaceDefaults.glow(
                                                focusedGlow = Glow(elevationColor = Color(0xFFFF3B30).copy(0.5f), elevation = 16.dp)
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxHeight().padding(horizontal = 22.dp)
                                            ) {
                                                Text(
                                                    text = "✕",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = tr("Clear", "נקה"),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Content ──
                    when (val st = state.scrapingStatus) {
                        is ScrapingStatus.Searching, is ScrapingStatus.ResolvingDebrid -> {
                            Box(Modifier.fillMaxWidth().height(260.dp), Alignment.Center) { LoadingIndicator() }
                        }
                        is ScrapingStatus.Error -> {
                            Box(Modifier.fillMaxWidth().height(260.dp), Alignment.Center) {
                                Text(st.message, color = ColorAccentIsland, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 48.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .focusGroup()
                                    .focusRestorer { firstSourceFR }
                            ) {
                                itemsIndexed(state.availableStreams) { idx, stream ->
                                    DetailedSourceRow(
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



    // ── Season Episodes Popup ─────────────────────────────────────────
    if (showSeasonPopup && media.isSeries) {
        SeasonEpisodesPopup(
            seasonNumber = state.selectedSeason,
            episodes = state.episodes,
            isLoading = state.isEpisodesLoading,
            backdropUrl = media.backdropUrl,
            onEpisodeClick = { ep ->
                selectedEpisodeIdx = state.episodes.indexOf(ep)
                selectedEpisode = ep
            },
            onPlayEpisode = { ep ->
                showSeasonPopup = false
                showSources = true
                onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber))
            },
            onMarkSeasonWatched = { onEvent(DetailsEvent.MarkSeasonWatched(state.selectedSeason)) },
            onMarkSeasonUnwatched = { onEvent(DetailsEvent.MarkSeasonUnwatched(state.selectedSeason)) },
            onDismiss = { showSeasonPopup = false }
        )
    }

    // ── Episode Detail Modal ──────────────────────────────────────────
    selectedEpisode?.let { ep ->
        EpisodeDetailsModal(
            episode = ep,
            imdbId = media.imdbId,
            onPlay = { season, episode ->
                selectedEpisode = null
                showSeasonPopup = false
                showSources = true
                onEvent(DetailsEvent.InitiateScraping(media.imdbId, season, episode))
            },
            onMarkWatched = { season, episode, isWatched ->
                if (isWatched) onEvent(DetailsEvent.MarkEpisodeWatched(season, episode))
                else onEvent(DetailsEvent.MarkEpisodeUnwatched(season, episode))
                selectedEpisode = null
            },
            onDismiss = { selectedEpisode = null }
        )
    }

    // ── Actor Details Popup ───────────────────────────────────────────
    if (state.showActorPopup) {
        ActorDetailsPopup(
            actorDetails = state.selectedActorDetails,
            isLoading = state.isLoadingActor,
            onDismiss = { onEvent(DetailsEvent.DismissActorPopup) },
            onMediaClick = { fullId ->
                onEvent(DetailsEvent.DismissActorPopup)
                onNavigateToDetails(fullId)
            }
        )
    }
} // end DetailsScreen
}

