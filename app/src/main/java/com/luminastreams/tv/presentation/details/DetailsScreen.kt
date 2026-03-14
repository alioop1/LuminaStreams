@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_FUTURE_ERROR")
@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class, ExperimentalAnimationApi::class)

package com.luminastreams.tv.presentation.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import java.util.Locale

private val BK  = Color(0xFF000000)
private val GL  = Color(0x22FFFFFF)
private val GB  = Color(0x40FFFFFF)
private val DM  = Color(0x99FFFFFF)
private val MT  = Color(0x66FFFFFF)
private val WH  = Color(0xFFFFFFFF)
private val BR  = Color(0xFFE50914)
private val GLD = Color(0xFFFFC107)
private val TMR = Color(0xFFF44336)

/**
 * Safe YouTube launcher.
 * Priority: vnd.youtube deep-link → youtube.com browser → toast message.
 * Prevents the "YouTube not supported" dead-end QR screen.
 */
private fun launchTrailer(context: android.content.Context, trailerIdOrUrl: String?, fallbackTitle: String) {
    val appCtx = context.applicationContext
    if (!trailerIdOrUrl.isNullOrBlank()) {
        // 1) Try native YouTube app via deep-link
        val ytAppIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$trailerIdOrUrl"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = appCtx.packageManager.resolveActivity(ytAppIntent, 0)
        if (resolved != null) {
            try { appCtx.startActivity(ytAppIntent); return } catch (_: Exception) {}
        }
        // 2) Fall back to watch URL in any browser
        val watchUrl = if (trailerIdOrUrl.startsWith("http")) trailerIdOrUrl
                       else "https://www.youtube.com/watch?v=$trailerIdOrUrl"
        try {
            appCtx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {}
    }
    // 3) Search fallback
    try {
        appCtx.startActivity(
            Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode("$fallbackTitle official trailer")}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}

@Composable
fun DetailsScreen(
    state: DetailsScreenState,
    onEvent: (DetailsEvent) -> Unit,
    onPlayDirectUrl: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onRecommendationClick: (String) -> Unit
) {
    val context    = LocalContext.current
    val isRtl      = LocalLayoutDirection.current == LayoutDirection.Rtl
    val media      = state.mediaInfo

    val playFR        = remember { FocusRequester() }
    val backBtnFR     = remember { FocusRequester() }
    val firstSourceFR = remember { FocusRequester() }
    val scrollState   = rememberLazyListState()

    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(state.readyToPlayUrl) {
        state.readyToPlayUrl?.let { url ->
            showSources = false
            onPlayDirectUrl(url)
            onEvent(DetailsEvent.ClearPlayUrl)
        }
    }

    LaunchedEffect(state.isLoadingData) {
        if (!state.isLoadingData) {
            delay(280L)
            runCatching { playFR.requestFocus() }
        }
    }

    LaunchedEffect(state.availableStreams, showSources) {
        if (showSources && state.availableStreams.isNotEmpty()) {
            delay(350)
            runCatching { firstSourceFR.requestFocus() }
        }
    }

    BackHandler(enabled = true) {
        if (showSources) {
            showSources = false
            onEvent(DetailsEvent.CancelScraping)
            runCatching { playFR.requestFocus() }
        } else {
            onNavigateBack()
        }
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(BK), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text("טוען נתונים...", color = DM, fontSize = 16.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(BK)) {

        // PERF FIX: wrap parallax offset in derivedStateOf so graphicsLayer
        // only reads the state it needs — no full recomposition on every scroll tick.
        val parallaxOffset by remember {
            derivedStateOf { (scrollState.firstVisibleItemScrollOffset * 0.12f).coerceIn(0f, 70f) }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -parallaxOffset }) {
            if (!media.backdropUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(media.backdropUrl)
                        .crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else if (!media.posterUrl.isNullOrEmpty()) {
                AsyncImage(model = media.posterUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.5f)
            }
        }

        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.4f), BK.copy(0.95f)), startY = 400f)
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                if (isRtl) listOf(BK.copy(0.85f), Color.Transparent)
                else        listOf(Color.Transparent, BK.copy(0.85f)),
                startX = 0f, endX = 1400f
            )
        ))

        LazyColumn(
            state           = scrollState,
            modifier        = Modifier.fillMaxSize().focusRestorer(),
            contentPadding  = PaddingValues(top = 48.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp)) {
                    Surface(
                        onClick  = { onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(CircleShape),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
                        scale    = ClickableSurfaceDefaults.scale(1.1f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(start = 48.dp, end = 48.dp).size(48.dp)
                            .focusRequester(backBtnFR).focusProperties { down = playFR; left = playFR; right = playFR }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(if (isRtl) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, "חזור", Modifier.size(24.dp))
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.65f).padding(horizontal = 64.dp)) {
                        if (media.displayStudios.isNotEmpty()) {
                            Text(media.displayStudios, color = MT, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (!media.logoUrl.isNullOrEmpty()) {
                            AsyncImage(model = media.logoUrl, contentDescription = media.title,
                                modifier = Modifier.heightIn(max = 85.dp).fillMaxWidth(0.88f),
                                contentScale = ContentScale.Fit,
                                alignment = if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)
                        } else {
                            val tsz = when { media.title.length > 22 -> 38.sp; media.title.length > 14 -> 48.sp; else -> 56.sp }
                            Text(media.title, color = WH, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = tsz * 1.15f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (media.releaseDate.isNotEmpty()) { Text(media.releaseDate.take(4), color = WH, fontSize = 13.sp, fontWeight = FontWeight.Bold); MDot() }
                            if (media.ageRating.isNotEmpty()) { Box(Modifier.border(1.dp, GB, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) { Text(media.ageRating, color = WH, fontSize = 10.sp, fontWeight = FontWeight.Black) }; MDot() }
                            if (!media.isSeries && media.runtimeMinutes > 0) Text(media.formattedRuntime, color = DM, fontSize = 13.sp)
                            else if (media.isSeries && media.totalSeasons > 0) Text("${media.totalSeasons} עונות", color = DM, fontSize = 13.sp)
                            if (media.displayGenres.isNotEmpty()) { MDot(); Text(media.displayGenres, color = DM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Spacer(Modifier.height(9.dp))
                        state.bestSourceHint?.let { h ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (h.contains("4K"))    QPill("4K",    Color(0xFF1565C0))
                                if (h.contains("HDR"))   QPill("HDR",   Color(0xFF6A1B9A))
                                if (h.contains("REMUX")) QPill("REMUX", Color(0xFF37474F))
                                if (h.contains("RD+"))   QPill("RD+",   Color(0xFF388E3C))
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (media.imdbRating > 0) RChip(Icons.Default.Star,     GLD, String.format(Locale.US, "%.1f", media.imdbRating), "IMDb")
                            if (media.tmdbRating > 0) RChip(Icons.Default.Favorite, TMR, "${(media.tmdbRating * 10).toInt()}%", "TMDB")
                        }
                        Spacer(Modifier.height(12.dp))
                        if (media.overview.isNotEmpty()) {
                            Text(media.overview, color = DM, fontSize = 14.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(22.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.focusRestorer().focusProperties { up = backBtnFR }
                        ) {
                            // Play button
                            Surface(
                                onClick  = {
                                    showSources = true
                                    if (media.isSeries) onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.selectedSeason, 1))
                                    else onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                },
                                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                colors   = ClickableSurfaceDefaults.colors(containerColor = WH, contentColor = BK, focusedContainerColor = BR, focusedContentColor = WH),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
                                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(BR.copy(0.55f), 22.dp)),
                                modifier = Modifier.wrapContentWidth().focusRequester(playFR)
                            ) {
                                Row(Modifier.padding(horizontal = 26.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(19.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("נגן עכשיו", fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, softWrap = false)
                                }
                            }
                            // Trailer button — uses safe launcher (no more YouTube dead-end)
                            Pill("טריילר", Icons.Default.PlayArrow) {
                                launchTrailer(context, media.trailerUrl, media.title)
                            }
                            Pill(
                                label   = if (state.availableStreams.isNotEmpty()) "מקורות (${state.availableStreams.size})" else "מקורות",
                                icon    = Icons.AutoMirrored.Filled.List
                            ) {
                                showSources = true
                                if (media.isSeries) onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.selectedSeason, 1))
                                else onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                            }
                            Surface(
                                onClick  = { onEvent(DetailsEvent.ToggleFavorite) },
                                shape    = ClickableSurfaceDefaults.shape(CircleShape),
                                colors   = ClickableSurfaceDefaults.colors(containerColor = GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                border   = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, GB)), focusedBorder = Border.None),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Icon(if (media.isFavorite) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (media.isSeries && media.totalSeasons > 0) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SecHdr("עונות ופרקים", Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 64.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.focusRestorer().padding(bottom = 14.dp)
                        ) {
                            items(media.totalSeasons) { idx ->
                                val n = idx + 1; val sel = state.selectedSeason == n; val isLast = idx == media.totalSeasons - 1
                                Surface(
                                    onClick  = { onEvent(DetailsEvent.SelectSeason(n)) },
                                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors   = ClickableSurfaceDefaults.colors(containerColor = if (sel) BR else GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
                                    scale    = ClickableSurfaceDefaults.scale(1.05f),
                                    border   = ClickableSurfaceDefaults.border(border = if (sel) Border.None else Border(BorderStroke(1.dp, GB)), focusedBorder = Border.None),
                                    modifier = Modifier.focusProperties { if (isLast) { if (isRtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel } }
                                ) {
                                    Text("עונה $n", fontWeight = if (sel) FontWeight.Black else FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp))
                                }
                            }
                        }
                        if (state.isEpisodesLoading) {
                            Box(Modifier.fillMaxWidth().height(145.dp), Alignment.Center) { LoadingIndicator() }
                        } else if (state.episodes.isNotEmpty()) {
                            LazyRow(
                                contentPadding        = PaddingValues(horizontal = 64.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier              = Modifier.focusRestorer()
                            ) {
                                itemsIndexed(state.episodes, key = { _, ep -> ep.id }) { idx, ep ->
                                    EpCard(ep, idx == state.episodes.size - 1) {
                                        showSources = true
                                        onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (media.cast.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SecHdr("שחקנים וצוות", Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 64.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier              = Modifier.focusRestorer()
                        ) {
                            itemsIndexed(media.cast) { idx, a -> CastCard(a, idx == media.cast.size - 1) }
                        }
                    }
                }
            }

            // Trailers row — uses safe launchTrailer()
            item {
                Column(Modifier.fillMaxWidth()) {
                    SecHdr("טריילרים ותוספות", Modifier.padding(horizontal = 64.dp))
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier              = Modifier.focusRestorer()
                    ) {
                        item {
                            var isFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick  = { launchTrailer(context, media.trailerUrl, media.title) },
                                colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(3.dp, Color.White))),
                                glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(0.4f), 16.dp)),
                                modifier = Modifier.width(320.dp).aspectRatio(16f / 9f)
                                    .zIndex(if (isFocused) 10f else 0f)
                                    .onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                                    if (!media.backdropUrl.isNullOrEmpty()) {
                                        AsyncImage(model = media.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(Modifier.fillMaxSize().background(Color(0xFF141414)))
                                    }
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(48.dp))
                                    Text("טריילר רשמי", color = WH, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
                                }
                            }
                        }
                        item {
                            var isFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick  = {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode("${media.title} behind the scenes")}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {}
                                },
                                colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                                shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                modifier = Modifier.width(200.dp).aspectRatio(16f / 9f)
                                    .zIndex(if (isFocused) 10f else 0f)
                                    .shadow(if (isFocused) 24.dp else 0.dp, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Search, "Search", Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("חפש ב-YouTube", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sources panel
        AnimatedVisibility(
            visible  = showSources,
            enter    = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
            exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BK.copy(alpha = 0.75f))
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
                            if (event.type == KeyEventType.KeyUp) {
                                showSources = false
                                onEvent(DetailsEvent.CancelScraping)
                                runCatching { playFR.requestFocus() }
                            }
                            true
                        } else false
                    }
                    .clickable(remember { MutableInteractionSource() }, null) {
                        showSources = false
                        onEvent(DetailsEvent.CancelScraping)
                        runCatching { playFR.requestFocus() }
                    },
                contentAlignment = if (isRtl) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(550.dp)
                        .background(Color(0xE60A0A0A))
                        .border(1.dp, Color(0x1AFFFFFF))
                        .padding(horizontal = 40.dp, vertical = 48.dp)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                ) {
                    Text("מקורות זמינים", color = WH, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    if (state.availableStreams.isNotEmpty()) {
                        Text("נמצאו ${state.availableStreams.size} מקורות פרימיום", color = DM, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(32.dp))
                    when (val st = state.scrapingStatus) {
                        is ScrapingStatus.Error           -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Warning, null, tint = BR, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(16.dp)); Text(st.message, color = DM, fontSize = 18.sp, textAlign = TextAlign.Center) } }
                        is ScrapingStatus.Searching       -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { LoadingIndicator(); Spacer(Modifier.height(16.dp)); Text("סורק בשרתי Torrentio...", color = DM, fontSize = 18.sp) } }
                        is ScrapingStatus.ResolvingDebrid -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { LoadingIndicator(); Spacer(Modifier.height(16.dp)); Text("מפענח דרך Real-Debrid...", color = WH, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding      = PaddingValues(bottom = 64.dp),
                            modifier            = Modifier.focusRestorer()
                        ) {
                            itemsIndexed(state.availableStreams) { index, stream ->
                                GlassSrcCard(
                                    source   = stream,
                                    modifier = if (index == 0) Modifier.focusRequester(firstSourceFR) else Modifier,
                                    onClick  = { onEvent(DetailsEvent.ResolveAndPlayStream(stream)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MDot() = Text("•", color = GB, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
@Composable private fun QPill(label: String, color: Color) = Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.88f)).padding(horizontal = 6.dp, vertical = 3.dp)) { Text(label, color = WH, fontSize = 10.sp, fontWeight = FontWeight.Black) }
@Composable private fun RChip(icon: ImageVector, tintC: Color, value: String, label: String) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tintC, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(value, color = WH, fontWeight = FontWeight.Black, fontSize = 14.sp); Spacer(Modifier.width(3.dp)); Text(label, color = MT, fontSize = 11.sp) }
@Composable private fun Pill(label: String, icon: ImageVector, onClick: () -> Unit) = Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)), colors = ClickableSurfaceDefaults.colors(containerColor = GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f), border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, GB)), focusedBorder = Border.None), modifier = Modifier.wrapContentWidth()) { Row(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false) } }
@Composable private fun SecHdr(title: String, modifier: Modifier = Modifier) { Column(modifier, horizontalAlignment = Alignment.Start) { Text(title, color = WH, fontSize = 19.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(4.dp)); Box(Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(50)).background(BR)) } }

@Composable
private fun EpCard(episode: Episode, isLast: Boolean, onClick: () -> Unit) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(3.dp, Color.White))),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.5f), 16.dp)),
        modifier = Modifier
            .width(280.dp).aspectRatio(16f / 9f)
            .zIndex(if (isFocused) 10f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
            .focusProperties { if (isLast) { if (isRtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel } }
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            AsyncImage(model = episode.stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.98f)), startY = 40f)))
            Column(Modifier.align(Alignment.BottomStart).padding(bottom = if (episode.progress > 0f) 16.dp else 12.dp, start = 12.dp, end = 12.dp)) {
                Text("‎${episode.episodeNumber}. ${episode.title}", color = WH, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Left)
                Text("45m", color = DM, fontSize = 12.sp)
            }
            if (episode.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(BK.copy(0.6f))) {
                    Box(Modifier.fillMaxWidth(episode.progress).fillMaxHeight().background(BR).align(Alignment.CenterStart))
                }
            }
        }
    }
}

@Composable
private fun CastCard(actor: CastMember, isLast: Boolean) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp).onFocusChanged { focused = it.isFocused }.zIndex(if (focused) 10f else 0f)
    ) {
        Surface(
            onClick  = {},
            shape    = ClickableSurfaceDefaults.shape(CircleShape),
            colors   = ClickableSurfaceDefaults.colors(containerColor = GL, focusedContainerColor = WH),
            scale    = ClickableSurfaceDefaults.scale(1.1f),
            modifier = Modifier.size(90.dp).shadow(if (focused) 16.dp else 0.dp, CircleShape)
                .focusProperties { if (isLast) { if (isRtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel } }
        ) {
            AsyncImage(model = actor.imageUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(actor.name, color = if (focused) WH else DM, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(actor.character, color = MT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GlassSrcCard(source: AdvancedStreamSource, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val qualityColor = when (source.quality.priority) { 9 -> Color(0xFF1565C0); 7 -> Color(0xFF6A1B9A); 6 -> Color(0xFF2E7D32); else -> Color(0xFF424242) }
    val nameUpper = source.filename.uppercase()
    val audioBadges = mutableListOf<Pair<String, Color>>()
    if (nameUpper.contains("HEB") || nameUpper.contains("HEBREW")) audioBadges.add("HEB" to Color(0xFFE65100))
    if (nameUpper.contains("ENG") || nameUpper.contains("ENGLISH")) audioBadges.add("ENG" to Color(0xFF1976D2))
    if (nameUpper.contains("RUS") || nameUpper.contains("DUBBED")) audioBadges.add("RUS" to Color(0xFF388E3C))
    if (nameUpper.contains("MULTI") || nameUpper.contains("DUAL")) audioBadges.add("MULTI" to Color(0xFFF57C00))
    val techBadges = mutableListOf<Pair<String, Color>>()
    if (nameUpper.contains("HDR") || nameUpper.contains("HDR10")) techBadges.add("HDR" to Color(0xFF4A148C))
    if (nameUpper.contains("DV") || nameUpper.contains("DOLBY VISION")) techBadges.add("DV" to Color(0xFF311B92))
    if (nameUpper.contains("ATMOS")) techBadges.add("ATMOS" to Color(0xFF004D40))
    if (nameUpper.contains("7.1")) techBadges.add("7.1" to Color(0xFF01579B))
    val provider = when {
        nameUpper.contains("TGX") || nameUpper.contains("TORRENTGALAXY") -> "TorrentGalaxy"
        nameUpper.contains("RUTRACKER")  -> "Rutracker"
        nameUpper.contains("YTS") || nameUpper.contains("YIFY") -> "YTS"
        nameUpper.contains("RARBG") || nameUpper.contains("RBG") -> "RARBG"
        nameUpper.contains("EZTV")       -> "EZTV"
        nameUpper.contains("1337X")      -> "1337x"
        nameUpper.contains("TORRENTNINJA") -> "TorrentNinja"
        else -> "Torrentio"
    }
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), focusedContainerColor = Color(0xFF1A1A1A), contentColor = Color.White, focusedContentColor = Color.White),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.shadow(if (isFocused) 16.dp else 0.dp, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(qualityColor).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(source.quality.displayName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.height(8.dp))
                Text(source.formattedSize, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isFocused) Color.White else Color(0x99FFFFFF), maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(1.dp).height(50.dp).background(Color(0x33FFFFFF)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (source.isCachedRd) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF2E7D32)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("RD+", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(provider, color = Color(0xFFB0BEC5), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Text(source.releaseGroup.uppercase(), fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                val allBadges = audioBadges + techBadges
                if (allBadges.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allBadges.forEach { (text, color) ->
                            Box(Modifier.border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(source.filename.replace(".", " "), color = if (isFocused) Color.LightGray else Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
