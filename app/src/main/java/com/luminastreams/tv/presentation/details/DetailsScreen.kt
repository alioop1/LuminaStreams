@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@file:Suppress("UsePropertyAccessSyntax")

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.ui.components.LoadingIndicator
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi

private val BK  = Color(0xFF000000)
private val GL  = Color(0x22FFFFFF)
private val GB  = Color(0x40FFFFFF)
private val DM  = Color(0x99FFFFFF)
private val MT  = Color(0x66FFFFFF)
private val WH  = Color(0xFFFFFFFF)
private val BR  = Color(0xFFE50914)
private val GLD = Color(0xFFFFC107)
private val TMR = Color(0xFFF44336)

private fun launchTrailer(context: android.content.Context, trailerIdOrUrl: String?, fallbackTitle: String) {
    val appCtx = context.applicationContext
    if (!trailerIdOrUrl.isNullOrBlank()) {
        val ytAppIntent = Intent(Intent.ACTION_VIEW, "vnd.youtube:$trailerIdOrUrl".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appCtx.packageManager.resolveActivity(ytAppIntent, 0) != null) {
            try { appCtx.startActivity(ytAppIntent); return } catch (_: Exception) {}
        }
        val watchUrl = if (trailerIdOrUrl.startsWith("http")) trailerIdOrUrl else "https://www.youtube.com/watch?v=$trailerIdOrUrl"
        try { appCtx.startActivity(Intent(Intent.ACTION_VIEW, watchUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
    try {
        appCtx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/results?search_query=${Uri.encode("$fallbackTitle official trailer")}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

// ── Stream metadata parser ────────────────────────────────────────────────────

private data class StreamMeta(
    val videoCodecLabel: String,
    val videoCodecColor: Color,
    val hdrBadges: List<Pair<String, Color>>,
    val audioBadges: List<Pair<String, Color>>,
    val langBadges: List<Pair<String, Color>>,
    val subtitleBadges: List<Pair<String, Color>>,
    val releaseType: String,
    val releaseTypeColor: Color,
    val provider: String,
    val seeders: Int,
    val isRemux: Boolean,
    val isCam: Boolean
)

private fun parseStreamMeta(filename: String, name: String): StreamMeta {
    val upper = filename.uppercase()
    val nameUpper = name.uppercase()
    val combined = "$upper $nameUpper"

    // ── Video Codec ──
    val (codecLabel, codecColor) = when {
        combined.contains("AV1")                                       -> "AV1"  to Color(0xFF00C853)
        combined.contains("HEVC") || combined.contains("X265") ||
                combined.contains("H.265") || combined.contains("H265")       -> "HEVC" to Color(0xFF0091EA)
        combined.contains("AVC")  || combined.contains("X264") ||
                combined.contains("H.264") || combined.contains("H264")       -> "AVC"  to Color(0xFF546E7A)
        else                                                           -> "—"    to Color(0xFF424242)
    }

    // ── HDR ──
    val hdrBadges = buildList {
        if (combined.contains("DOLBY VISION") || combined.contains("DV") ||
            combined.contains("DV.") || combined.contains(".DV."))    add("Dolby Vision" to Color(0xFF7B1FA2))
        if (combined.contains("HDR10+"))                               add("HDR10+"       to Color(0xFF4527A0))
        if (combined.contains("HDR10"))                                add("HDR10"        to Color(0xFF283593))
        if (combined.contains("HDR") && isEmpty())                     add("HDR"          to Color(0xFF283593))
        if (combined.contains("HLG"))                                  add("HLG"          to Color(0xFF00695C))
        if (combined.contains("SDR") && none { it.first.contains("HDR") || it.first.contains("Dolby") })
            add("SDR"          to Color(0xFF424242))
    }

    // ── Audio ──
    val audioBadges = buildList {
        if (combined.contains("ATMOS"))                                add("Atmos"      to Color(0xFF00796B))
        if (combined.contains("DTS:X") || combined.contains("DTSX"))  add("DTS:X"      to Color(0xFF00897B))
        if (combined.contains("DTS-HD") || combined.contains("DTSHD"))add("DTS-HD MA" to Color(0xFF00838F))
        if (combined.contains("TRUEHD"))                               add("TrueHD"    to Color(0xFF0277BD))
        if (combined.contains("EAC3") || combined.contains("E-AC3") ||
            combined.contains("DD+") || combined.contains("DDPLUS"))   add("DD+"       to Color(0xFF1565C0))
        if (combined.contains("DD5.1") || combined.contains("AC3") ||
            (combined.contains("DD") && none { it.first == "DD+" }))  add("DD"        to Color(0xFF37474F))
        if (combined.contains("AAC"))                                  add("AAC"       to Color(0xFF4E342E))
        if (combined.contains("FLAC"))                                 add("FLAC"      to Color(0xFF558B2F))
    }

    // ── Language ──
    val langBadges = buildList {
        if (combined.contains("HEBREW") || combined.contains("HEB") ||
            combined.contains("עברית"))                                add("עברית 🇮🇱"  to Color(0xFF1B5E20))
        if (combined.contains("ENGLISH") || combined.contains(" ENG") ||
            combined.contains(".ENG."))                                add("English 🇺🇸" to Color(0xFF1A237E))
        if (combined.contains("ARABIC") || combined.contains("ARA"))  add("עربي 🇸🇦"   to Color(0xFF4E342E))
        if (combined.contains("FRENCH") || combined.contains("FRE") ||
            combined.contains(".FR."))                                 add("French 🇫🇷"  to Color(0xFF311B92))
        if (combined.contains("GERMAN") || combined.contains("GER") ||
            combined.contains(".DE."))                                 add("Deutsch 🇩🇪" to Color(0xFF37474F))
        if (combined.contains("SPANISH") || combined.contains("SPA") ||
            combined.contains(".ES."))                                 add("Español 🇪🇸" to Color(0xFF880E4F))
        if (combined.contains("MULTI") || combined.contains("DUAL"))  add("Multi 🌍"   to Color(0xFF4A148C))
        if (combined.contains("HEBDUB") || combined.contains("HEBREW DUB") ||
            combined.contains("מדובב"))                               add("מדובב 🎤"    to Color(0xFFE65100))
    }

    // ── Subtitles ──
    val subtitleBadges = buildList {
        if (combined.contains("HEBREW SUB") || combined.contains("HEBSUB") ||
            combined.contains("HEBSUBS") || combined.contains("SUB.HEB") ||
            combined.contains("כתוביות"))                             add("כתוביות עב׳" to Color(0xFF2E7D32))
        if (combined.contains("MULTI.SUB") || combined.contains("MULTISUB") ||
            combined.contains("MULTI SUB"))                            add("Multi Subs"  to Color(0xFF1B5E20))
        if (combined.contains("SUBBED"))                               add("Subbed"      to Color(0xFF33691E))
        if (combined.contains("HARDCODED") || combined.contains("HARDSUB"))
            add("Hardcoded"   to Color(0xFFBF360C))
    }

    // ── Release Type ──
    val (releaseType, releaseTypeColor) = when {
        combined.contains("REMUX")                                  -> "REMUX"       to Color(0xFF37474F)
        combined.contains("BLURAY") || combined.contains("BLU-RAY") ||
                combined.contains("BD")                                     -> "BluRay"      to Color(0xFF0D47A1)
        combined.contains("WEB-DL") || combined.contains("WEBDL")  -> "WEB-DL"      to Color(0xFF1565C0)
        combined.contains("WEBRIP") || combined.contains("WEB-RIP")-> "WEBRip"      to Color(0xFF283593)
        combined.contains("HDTV")                                   -> "HDTV"        to Color(0xFF37474F)
        combined.contains("DVDRIP") || combined.contains("DVD")    -> "DVDRip"      to Color(0xFF4E342E)
        combined.contains("CAM") || combined.contains("HDCAM") ||
                combined.contains("TS.") || combined.contains(".TS.")       -> "CAM"         to Color(0xFFB71C1C)
        else                                                        -> "WEB"         to Color(0xFF1E3A5F)
    }

    // ── Provider ──
    val provider = when {
        combined.contains("YTS") || combined.contains("YIFY")      -> "YTS"
        combined.contains("RARBG")                                  -> "RARBG"
        combined.contains("EZTV")                                   -> "EZTV"
        combined.contains("1337X")                                  -> "1337x"
        combined.contains("SPARKS")                                 -> "SPARKS"
        combined.contains("YIFY")                                   -> "YIFY"
        combined.contains("FLUX")                                   -> "FLuX"
        combined.contains("CMRG")                                   -> "CMRG"
        combined.contains("DSNP") || combined.contains("DISNEY")   -> "Disney+"
        combined.contains("NF") || combined.contains("NETFLIX")    -> "Netflix"
        combined.contains("AMZN") || combined.contains("AMAZON")   -> "Amazon"
        combined.contains("HMAX") || combined.contains("HBO")      -> "HBO Max"
        combined.contains("ATVP") || combined.contains("APPLE")    -> "Apple TV+"
        else                                                        -> "Torrentio"
    }

    // ── Seeders (parse from title like "👤 1234" or "S: 1234") ──
    val seeders = Regex("(?:👤|S:|seeders:)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    val isRemux = combined.contains("REMUX")
    val isCam   = combined.contains("CAM") || combined.contains("HDCAM") ||
            (combined.contains(".TS.") || combined.contains(" TS "))

    return StreamMeta(
        videoCodecLabel  = codecLabel,
        videoCodecColor  = codecColor,
        hdrBadges        = hdrBadges,
        audioBadges      = audioBadges,
        langBadges       = langBadges,
        subtitleBadges   = subtitleBadges,
        releaseType      = releaseType,
        releaseTypeColor = releaseTypeColor,
        provider         = provider,
        seeders          = seeders,
        isRemux          = isRemux,
        isCam            = isCam
    )
}

// ── Seed-health color ─────────────────────────────────────────────────────────
private fun seederColor(seeders: Int) = when {
    seeders <= 0  -> Color(0xFF546E7A)
    seeders < 5   -> Color(0xFFB71C1C)
    seeders < 20  -> Color(0xFFE65100)
    seeders < 100 -> Color(0xFF2E7D32)
    else          -> Color(0xFF1B5E20)
}

@Composable
fun DetailsScreen(
    state: DetailsScreenState,
    onEvent: (DetailsEvent) -> Unit,
    onPlayDirectUrl: (videoUrl: String, imdbId: String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onRecommendationClick: (String) -> Unit
) {
    val context  = LocalContext.current
    val isRtl    = LocalLayoutDirection.current == LayoutDirection.Rtl

    var media by remember(state.mediaInfo) { mutableStateOf(state.mediaInfo) }

    LaunchedEffect(state.mediaInfo.id) {
        if (state.mediaInfo.id.startsWith("http")) {
            try {
                val activity = context as? androidx.activity.ComponentActivity
                if (activity != null) {
                    val homeVm = androidx.lifecycle.ViewModelProvider(activity)[com.luminastreams.tv.presentation.home.HomeViewModel::class.java]
                    val matched = homeVm.state.value.fuzerItems.find { it.id == state.mediaInfo.id }
                    if (matched != null) {
                        media = state.mediaInfo.copy(
                            title       = matched.title,
                            overview    = matched.overview.ifBlank { "לוחץ Play להפעלה אוטומטית דרך Real-Debrid." },
                            posterUrl   = matched.posterUrl,
                            backdropUrl = matched.backdropUrl.ifBlank { matched.posterUrl },
                            genres      = listOf("Fuzer VIP"),
                            studios     = listOf("Private Tracker")
                        )
                    }
                }
            } catch (_: Exception) {}
        } else {
            media = state.mediaInfo
        }
    }

    val playFR        = remember { FocusRequester() }
    val backBtnFR     = remember { FocusRequester() }
    val firstSourceFR = remember { FocusRequester() }
    val scrollState   = rememberLazyListState()
    var showSources   by remember { mutableStateOf(false) }
    val focusManager  = LocalFocusManager.current

    LaunchedEffect(state.mediaInfo.id) {
        if (state.mediaInfo.id.startsWith("http")) {
            showSources = false
            onEvent(DetailsEvent.InitiateScraping(state.mediaInfo.imdbId))
        }
    }

    LaunchedEffect(state.readyToPlayUrl) {
        state.readyToPlayUrl?.let { url ->
            showSources = false
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            onPlayDirectUrl(encodedUrl, media.imdbId)
            onEvent(DetailsEvent.ClearPlayUrl)
        }
    }

    LaunchedEffect(state.isLoadingData) {
        if (!state.isLoadingData) { delay(280L); runCatching { playFR.requestFocus() } }
    }

    LaunchedEffect(state.availableStreams, showSources) {
        if (showSources && state.availableStreams.isNotEmpty()) { delay(350); runCatching { firstSourceFR.requestFocus() } }
    }

    BackHandler(enabled = true) {
        if (showSources) {
            showSources = false
            onEvent(DetailsEvent.CancelScraping)
            focusManager.clearFocus()
        } else onNavigateBack()
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(BK), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading...", color = DM, fontSize = 16.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(BK)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = -(scrollState.firstVisibleItemScrollOffset * 0.12f).coerceIn(0f, 70f)
        }) {
            if (media.backdropUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(media.backdropUrl).crossfade(true).memoryCachePolicy(CachePolicy.ENABLED).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else if (media.posterUrl.isNotEmpty()) {
                AsyncImage(model = media.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.5f)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.4f), BK.copy(0.95f)), startY = 400f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(if (isRtl) listOf(BK.copy(0.85f), Color.Transparent) else listOf(Color.Transparent, BK.copy(0.85f)), startX = 0f, endX = 1400f)))

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().focusGroup(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp)) {
                    Surface(
                        onClick  = { onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(CircleShape),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
                        scale    = ClickableSurfaceDefaults.scale(1.1f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp).size(48.dp)
                            .focusRequester(backBtnFR).focusProperties { down = playFR; left = playFR; right = playFR }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Icon(if (isRtl) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(24.dp))
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.65f).padding(horizontal = 64.dp)) {
                        if (media.displayStudios.isNotEmpty()) {
                            Text(media.displayStudios, color = MT, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        if (!media.logoUrl.isNullOrEmpty()) {
                            AsyncImage(model = media.logoUrl, contentDescription = media.title, modifier = Modifier.heightIn(max = 85.dp).fillMaxWidth(0.88f), contentScale = ContentScale.Fit, alignment = if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)
                        } else {
                            val tsz = when { media.title.length > 22 -> 38.sp; media.title.length > 14 -> 48.sp; else -> 56.sp }
                            Text(media.title, color = WH, fontSize = tsz, fontWeight = FontWeight.Black, lineHeight = tsz * 1.15f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (media.releaseDate.isNotEmpty()) { Text(media.releaseDate.take(4), color = WH, fontSize = 13.sp, fontWeight = FontWeight.Bold); MDot() }
                            if (media.ageRating.isNotEmpty())   { Box(Modifier.border(1.dp, GB, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) { Text(media.ageRating, color = WH, fontSize = 10.sp, fontWeight = FontWeight.Black) }; MDot() }
                            if (!media.isSeries && media.runtimeMinutes > 0) Text(media.formattedRuntime, color = DM, fontSize = 13.sp)
                            else if (media.isSeries && media.totalSeasons > 0) Text("${media.totalSeasons} Seasons", color = DM, fontSize = 13.sp)
                            if (media.displayGenres.isNotEmpty()) { MDot(); Text(media.displayGenres, color = DM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Spacer(Modifier.height(9.dp))
                        state.bestSourceHint?.let { h ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (h.contains("4K"))           QPill("4K",        Color(0xFF1565C0))
                                if (h.contains("HDR"))          QPill("HDR",       Color(0xFF6A1B9A))
                                if (h.contains("REMUX"))        QPill("REMUX",     Color(0xFF37474F))
                                if (h.contains("RD+"))          QPill("RD+",       Color(0xFF2E7D32))
                                if (h.contains("Fuzer Direct")) QPill("Fuzer VIP", Color(0xFF00B0FF))
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (media.imdbRating > 0) RatingChip(Icons.Default.Star,     GLD, String.format(Locale.US, "%.1f", media.imdbRating), "IMDb")
                            if (media.tmdbRating > 0) RatingChip(Icons.Default.Favorite, TMR, "${(media.tmdbRating * 10).toInt()}%", "TMDB")
                        }
                        Spacer(Modifier.height(12.dp))
                        if (media.overview.isNotEmpty()) {
                            Text(media.overview, color = DM, fontSize = 14.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(22.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.focusGroup().focusProperties { up = backBtnFR }
                        ) {
                            if (state.isFuzerDirect) {
                                when (val st = state.scrapingStatus) {
                                    is ScrapingStatus.ResolvingDebrid -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            LoadingIndicator()
                                            Text(st.streamId, color = WH, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    is ScrapingStatus.Error -> {
                                        Row(
                                            Modifier.background(BR.copy(0.12f), RoundedCornerShape(10.dp))
                                                .border(1.dp, BR.copy(0.35f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment     = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, null, tint = BR, modifier = Modifier.size(18.dp))
                                            Text(st.message, color = WH, fontSize = 13.sp)
                                        }
                                    }
                                    else -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            LoadingIndicator()
                                            Text("מתחבר ל-Real-Debrid...", color = DM, fontSize = 15.sp)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = {
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
                                    Row(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Play Now", fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                                ActionPill("Trailer", Icons.Default.PlayArrow) { launchTrailer(context, media.trailerUrl, media.title) }
                                ActionPill(
                                    label = if (state.availableStreams.isNotEmpty()) "Sources (${state.availableStreams.size})" else "Sources",
                                    icon  = Icons.AutoMirrored.Filled.List
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
            }

            // ── Seasons + Episodes ─────────────────────────────────────────────
            if (!state.isFuzerDirect && media.isSeries && media.totalSeasons > 0) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SectionHeader("Seasons & Episodes", Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 64.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.focusGroup().padding(bottom = 14.dp)
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
                                    Text("Season $n", fontWeight = if (sel) FontWeight.Black else FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                                }
                            }
                        }
                        if (state.isEpisodesLoading) {
                            Box(Modifier.fillMaxWidth().height(145.dp), Alignment.Center) { LoadingIndicator() }
                        } else if (state.episodes.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 64.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                itemsIndexed(state.episodes, key = { _, ep -> ep.id }) { idx, ep ->
                                    EpisodeCard(ep, idx == state.episodes.size - 1) {
                                        showSources = true
                                        onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Cast ───────────────────────────────────────────────────────────
            if (!state.isFuzerDirect && media.cast.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SectionHeader("Cast & Crew", Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.focusGroup()) {
                            itemsIndexed(media.cast) { idx, a -> CastMemberCard(a, idx == media.cast.size - 1) }
                        }
                    }
                }
            }

            // ── Recommendations ────────────────────────────────────────────────
            if (!state.isFuzerDirect && media.recommendations.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        SectionHeader("More Like This", Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 64.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.focusGroup()
                        ) {
                            itemsIndexed(media.recommendations, key = { _, r -> r.id }) { idx, rec ->
                                val tempMovie = com.luminastreams.tv.domain.model.Movie(
                                    id = rec.id, title = rec.title, posterUrl = rec.posterUrl,
                                    backdropUrl = "", rating = 0f, mediaType = media.id.substringBefore("_"),
                                    overview = "", year = 0, genre = ""
                                )
                                val isLast = idx == media.recommendations.size - 1
                                com.luminastreams.tv.presentation.home.PosterCard(
                                    movie    = tempMovie,
                                    modifier = Modifier.focusProperties {
                                        if (isLast) { if (isRtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                    },
                                    onClick  = { onRecommendationClick(rec.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Sources Side Panel ─────────────────────────────────────────────────
        if (!state.isFuzerDirect) {
            AnimatedVisibility(
                visible  = showSources,
                enter    = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(380, easing = FastOutSlowInEasing)) + fadeIn(tween(250)),
                exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize().zIndex(200f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(BK.copy(alpha = 0.85f))
                        .focusGroup()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
                                if (event.type == KeyEventType.KeyUp) {
                                    showSources = false; onEvent(DetailsEvent.CancelScraping); focusManager.clearFocus()
                                }
                                true
                            } else false
                        }
                        .clickable(remember { MutableInteractionSource() }, null) {
                            showSources = false; onEvent(DetailsEvent.CancelScraping); focusManager.clearFocus()
                        },
                    contentAlignment = if (isRtl) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            // עיצוב ה-Popup כפאנל מרחף ואלגנטי בסגנון Apple TV
                            .padding(vertical = 24.dp)
                            .padding(start = if (isRtl) 24.dp else 0.dp, end = if (isRtl) 0.dp else 24.dp)
                            .width(660.dp)
                            .clip(RoundedCornerShape(28.dp)) // פינות עגולות גדולות
                            .background(Color(0xFF0F0F13).copy(alpha = 0.98f)) // צבע אפל-כמו כהה ועמוק
                            .padding(horizontal = 36.dp, vertical = 44.dp)
                            .clickable(remember { MutableInteractionSource() }, null) {}
                    ) {
                        // ── Panel header ──
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(36.dp).background(BR, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Available Sources", color = WH, fontSize = 26.sp, fontWeight = FontWeight.Black)
                                if (state.availableStreams.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${state.availableStreams.size} sources found", color = DM, fontSize = 13.sp)
                                        val rdCount = state.availableStreams.count { it.isCachedRd }
                                        if (rdCount > 0) {
                                            Box(
                                                Modifier.clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF1B5E20).copy(0.8f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("$rdCount RD+ cached", color = WH, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(BR.copy(0.6f), Color(0x08FFFFFF)))))
                        Spacer(Modifier.height(20.dp))

                        when (val st = state.scrapingStatus) {
                            is ScrapingStatus.Error -> {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Box(Modifier.size(64.dp).background(BR.copy(0.15f), CircleShape), Alignment.Center) {
                                            Icon(Icons.Default.Warning, null, tint = BR, modifier = Modifier.size(32.dp))
                                        }
                                        Text(st.message, color = DM, fontSize = 17.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            is ScrapingStatus.Searching -> {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        LoadingIndicator()
                                        Text("Scanning Torrentio servers...", color = DM, fontSize = 17.sp)
                                    }
                                }
                            }
                            is ScrapingStatus.ResolvingDebrid -> {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        LoadingIndicator()
                                        val msg = if (st.streamId.contains("%") || st.streamId.contains("מוריד") ||
                                            st.streamId.contains("מתחיל") || st.streamId.contains("מוסיף"))
                                            st.streamId else "Resolving via Real-Debrid..."
                                        Text(msg, color = WH, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding      = PaddingValues(bottom = 64.dp),
                                    modifier            = Modifier.focusGroup()
                                ) {
                                    itemsIndexed(state.availableStreams) { index, stream ->
                                        StreamSourceCard(
                                            source   = stream,
                                            rank     = index + 1,
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
    }
}

// ── StreamSourceCard — Fixed smooth transitions (No Flash) ───────────────────
@Composable
private fun StreamSourceCard(
    source: AdvancedStreamSource,
    rank: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val meta = remember(source.filename, source.releaseGroup) {
        parseStreamMeta(source.filename, source.releaseGroup)
    }

    // ניהול המדינה הממוקדת באופן מקומי רק עבור שינויי צבע טקסט עדינים פנימיים
    val focusedState = remember { mutableStateOf(false) }
    val isFocused by focusedState

    // אנימציה חלקה לטקסט משקל הקובץ
    val fileSizeColor by animateColorAsState(
        targetValue = if (isFocused) WH else DM,
        animationSpec = tween(150),
        label = "fileSizeAnim"
    )

    Surface(
        onClick = onClick,
        // אסטרטגיית תיקון הפלאש: ניהול צבעי הרקע ישירות על ידי ה-Surface
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0CFFFFFF), // צבע לא ממוקד (חצי שקוף)
            focusedContainerColor = Color(0xFF282832), // צבע ממוקד (אטום בסגנון Apple)
            contentColor = WH,
            focusedContentColor = WH
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f), // גדילה קלה ויוקרתית
        glow = ClickableSurfaceDefaults.glow(
            // צל עמוק ומרשים המדגיש את האלמנט הממוקד
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.8f), elevation = 25.dp)
        ),
        modifier = modifier
            .fillMaxWidth()
            // עדכון המדינה הממוקדת
            .onFocusChanged { focusedState.value = it.isFocused }
    ) {
        // התוכן הראשי ללא Box או Background ידני נוסף
        Column(Modifier.fillMaxWidth().padding(24.dp)) {

            // ═══════════════════════════════════════
            // שורה 1: דירוג, ספק, סידרים, ומשקל הקובץ
            // ═══════════════════════════════════════
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // דירוג
                Text(
                    text = "#$rank",
                    color = WH.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(12.dp))

                // ספק
                Text(
                    text = meta.provider.uppercase(),
                    color = if (source.isCachedRd) Color(0xFF43A047) else Color(0xFF29B6F6),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.weight(1f))

                // סידרים (עם אייקון FontAwesome)
                if (meta.seeders > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uf0c0", // FontAwesome: fa-users
                            color = seederColor(meta.seeders),
                            fontSize = 12.sp
                            // fontFamily = yourFontAwesomeFamily
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${meta.seeders}",
                            color = seederColor(meta.seeders),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }

                // משקל הקובץ - משתמש באנימציה חלקה לצבע
                Text(
                    text = source.formattedSize,
                    color = fileSizeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // ═══════════════════════════════════════
            // שורה 2: כותרת / שם קבוצת השחרור (מודגש)
            // ═══════════════════════════════════════
            Text(
                text = source.releaseGroup.ifEmpty { "UNKNOWN RELEASE" }.uppercase(),
                color = WH,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))

            // ═══════════════════════════════════════
            // שורה 3: תגים מסודרים ונקיים בסגנון Apple
            // ═══════════════════════════════════════
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // איכות - צבע עמוק ומודרני
                PremiumBadge(source.quality.displayName, Color(0xFF0D47A1))

                // מזהה RD - ירוק עמוק
                if (source.isCachedRd) {
                    PremiumBadge("RD+ CACHED", Color(0xFF1B5E20), icon = "\uf0e7") // FontAwesome: fa-bolt
                }

                // אזהרת CAM
                if (meta.isCam) {
                    PremiumBadge("CAM", Color(0xFFB71C1C), icon = "\uf071") // FontAwesome: fa-exclamation-triangle
                }

                // קודק (אם קיים) - Outline עדין
                if (meta.videoCodecLabel != "—") {
                    PremiumBadge(meta.videoCodecLabel, WH.copy(alpha = 0.8f), isOutline = true)
                }

                // תגית HDR ראשונה - זהב/HDR עמוק
                meta.hdrBadges.firstOrNull()?.let {
                    PremiumBadge(it.first, Color(0xFFFF8F00), isOutline = true)
                }

                // תגית שמע ראשונה
                meta.audioBadges.firstOrNull()?.let {
                    PremiumBadge(it.first, Color(0xFF8E24AA), isOutline = true)
                }

                // זיהוי עברית מדובב/מתורגם
                val hasHebrew = meta.langBadges.any { it.first.contains("עברית") || it.first.contains("מדובב") }
                        || meta.subtitleBadges.any { it.first.contains("כתוביות עב") }
                if (hasHebrew) {
                    PremiumBadge("HEBREW", Color(0xFF00ACC1), isOutline = true)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ═══════════════════════════════════════
            // שורה 4: שם הקובץ המקורי (עדין ומוחלש)
            // ═══════════════════════════════════════
            Text(
                text = source.filename.replace(".", " "),
                color = WH.copy(alpha = if (isFocused) 0.5f else 0.25f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── רכיב תג מינימליסטי ونקי — מעודכן (No Flash) ──────────────────────────────────
@Composable
private fun PremiumBadge(
    text: String,
    color: Color,
    isOutline: Boolean = false,
    icon: String? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            // שימוש בצבע רקע חלש יותר המשתלב עם הרקע החדש של הכרטיסייה
            .background(if (isOutline) Color.Transparent else color.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = if (isOutline) color.copy(alpha = 0.4f) else Color.Transparent, // ללא Border לתגים מלאים
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(
                    text = icon,
                    color = color,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 6.dp)
                    // fontFamily = yourFontAwesomeFamily
                )
            }
            Text(
                text = text.uppercase(),
                color = if (isOutline) color else WH, // טקסט לבן עבור תגים מלאים
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ── Rest of helpers ───────────────────────────────────────────────────────────
@Composable private fun MDot() = Text("•", color = GB, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
@Composable private fun QPill(label: String, color: Color) = Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(0.88f)).padding(horizontal = 6.dp, vertical = 3.dp)) { Text(label, color = WH, fontSize = 10.sp, fontWeight = FontWeight.Black) }
@Composable private fun RatingChip(icon: ImageVector, tintC: Color, value: String, label: String) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tintC, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(value, color = WH, fontWeight = FontWeight.Black, fontSize = 14.sp); Spacer(Modifier.width(3.dp)); Text(label, color = MT, fontSize = 11.sp) }

@Composable
private fun ActionPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0x1AFFFFFF), contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        border   = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, Color(0x33FFFFFF))), focusedBorder = Border.None),
        modifier = Modifier.wrapContentWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = WH, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(50)).background(BR))
    }
}

@Composable
private fun EpisodeCard(episode: Episode, isLast: Boolean, onClick: () -> Unit) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focusedState = remember { mutableStateOf(false) }
    val isFocused by focusedState
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(3.dp, WH))),
        glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(WH.copy(alpha = 0.5f), 16.dp)),
        modifier = Modifier.width(280.dp).aspectRatio(16f / 9f)
            .zIndex(if (isFocused) 10f else 0f)
            .onFocusChanged { focusedState.value = it.isFocused }
            .focusProperties { if (isLast) { if (isRtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel } }
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            AsyncImage(model = episode.stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.98f)), startY = 40f)))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text("${episode.episodeNumber}. ${episode.title}", color = WH, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun CastMemberCard(actor: CastMember, isLast: Boolean) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp).onFocusChanged { focused = it.isFocused }.zIndex(if (focused) 10f else 0f)) {
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