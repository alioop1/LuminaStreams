@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@file:Suppress("UsePropertyAccessSyntax", "DEPRECATION")

package com.luminastreams.tv.presentation.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.ExperimentalTvMaterial3Api

import androidx.compose.ui.draw.alpha
import android.content.Context
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.luminastreams.tv.domain.model.Movie
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

@Composable
fun tr(en: String, he: String): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en

// ⚡ ZERO-LAG CINEMATIC OVERLAY
// Isolates Backdrop & Logo so they never redraw when the text changes!
@Composable
fun PremiumLoadingOverlay(
    backdropUrl: String,
    logoUrl: String?,
    title: String,
    statusText: String,
    baseColor: Color = Color.Black
) {
    val fadeGradient = remember(baseColor) { Brush.verticalGradient(listOf(Color.Transparent, baseColor.copy(alpha = 0.95f))) }

    Box(Modifier.fillMaxSize().background(baseColor)) {
        // Safe check for backdrop
        if (backdropUrl.isNotBlank() && backdropUrl != "null" && backdropUrl != "none") {
            coil.compose.AsyncImage(
                model = backdropUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.35f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        Box(Modifier.fillMaxSize().background(fadeGradient))

        Column(modifier = Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {

            // ⚡ FIX: Filter out literal "null" and "none" strings
            val validLogo = logoUrl?.takeIf { it.isNotBlank() && it.trim() != "null" && it.trim() != "none" }
            val validTitle = title.takeIf { it.isNotBlank() && it.trim() != "null" && it.trim() != "none" }

            if (validLogo != null) {
                // ⚡ FIX: SubcomposeAsyncImage forces the Title Text to show if the Logo fails or takes too long!
                coil.compose.SubcomposeAsyncImage(
                    model = validLogo,
                    contentDescription = validTitle,
                    modifier = Modifier.widthIn(max = 340.dp).heightIn(max = 140.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    loading = {
                        if (validTitle != null) {
                            Text(
                                text = validTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    error = {
                        if (validTitle != null) {
                            Text(
                                text = validTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            } else if (validTitle != null) {
                Text(
                    text = validTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(32.dp))
            com.luminastreams.tv.ui.components.LoadingIndicator()
            Spacer(Modifier.height(24.dp))
            Text(text = statusText, color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

private fun launchTrailer(context: Context, trailerIdOrUrl: String?, fallbackTitle: String) {
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

private data class StreamMeta(
    val videoCodecLabel: String, val videoCodecColor: Color, val hdrBadges: List<Pair<String, Color>>,
    val audioBadges: List<Pair<String, Color>>, val langBadges: List<Pair<String, Color>>,
    val subtitleBadges: List<Pair<String, Color>>, val releaseType: String, val releaseTypeColor: Color,
    val provider: String, val seeders: Int, val isRemux: Boolean, val isCam: Boolean
)

private fun parseStreamMeta(filename: String, name: String): StreamMeta {
    val upper = filename.uppercase()
    val nameUpper = name.uppercase()
    val combined = "$upper $nameUpper"

    val (codecLabel, codecColor) = when {
        combined.contains("AV1")                                       -> "AV1"  to Color(0xFF00C853)
        combined.contains("HEVC") || combined.contains("X265") ||
                combined.contains("H.265") || combined.contains("H265")       -> "HEVC" to Color(0xFF0091EA)
        combined.contains("AVC")  || combined.contains("X264") ||
                combined.contains("H.264") || combined.contains("H264")       -> "AVC"  to Color(0xFF546E7A)
        else                                                           -> "—"    to Color(0xFF424242)
    }

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

    val langBadges = buildList {
        if (combined.contains(" HEB") || combined.contains(".HEB") || combined.contains("HEBREW") || combined.contains("מדובב") || combined.contains(" IL "))
            add("HEB \uD83C\uDDEE\uD83C\uDDF1"  to Color(0xFF00ACC1))
        if (combined.contains(" ENG") || combined.contains(".ENG") || combined.contains("ENGLISH") || combined.contains(" EN "))
            add("ENG \uD83C\uDDFA\uD83C\uDDF8" to Color(0xFF3949AB))
        if (combined.contains(" RUS") || combined.contains(".RUS") || combined.contains("RUSSIAN") || combined.contains(" RU "))
            add("RUS \uD83C\uDDF7\uD83C\uDDFA"   to Color(0xFFE53935))
        if (combined.contains(" FRE") || combined.contains(".FRE") || combined.contains("FRENCH") || combined.contains(" FR "))
            add("FRE \uD83C\uDDEB\uD83C\uDDF7"  to Color(0xFF1E88E5))
        if (combined.contains(" SPA") || combined.contains(".SPA") || combined.contains("SPANISH") || combined.contains(" ES "))
            add("SPA \uD83C\uDDEA\uD83C\uDDF8" to Color(0xFF8E24AA))
        if (combined.contains(" ITA") || combined.contains(".ITA") || combined.contains("ITALIAN") || combined.contains(" IT "))
            add("ITA \uD83C\uDDEE\uD83C\uDDF9" to Color(0xFF43A047))
        if (combined.contains("MULTI") || combined.contains("DUAL"))
            add("MULTI \uD83C\uDF0D"   to Color(0xFF00897B))
    }

    val subtitleBadges = buildList {
        if (combined.contains("HEBREW SUB") || combined.contains("HEBSUB") ||
            combined.contains("HEBSUBS") || combined.contains("SUB.HEB") ||
            combined.contains("כתוביות"))                              add("כתוביות עב׳" to Color(0xFF2E7D32))
        if (combined.contains("MULTI.SUB") || combined.contains("MULTISUB") ||
            combined.contains("MULTI SUB"))                            add("Multi Subs"  to Color(0xFF1B5E20))
        if (combined.contains("SUBBED"))                               add("Subbed"      to Color(0xFF33691E))
        if (combined.contains("HARDCODED") || combined.contains("HARDSUB"))
            add("Hardcoded"   to Color(0xFFBF360C))
    }

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

    val provider = when {
        combined.contains("YTS") || combined.contains("YIFY")      -> "YTS"
        combined.contains("RARBG")                                  -> "RARBG"
        combined.contains("EZTV")                                   -> "EZTV"
        combined.contains("1337X")                                  -> "1337x"
        combined.contains("SPARKS")                                 -> "SPARKS"
        combined.contains("FLUX")                                   -> "FLuX"
        combined.contains("CMRG")                                   -> "CMRG"
        combined.contains("DSNP") || combined.contains("DISNEY")   -> "Disney+"
        combined.contains("NF") || combined.contains("NETFLIX")    -> "Netflix"
        combined.contains("AMZN") || combined.contains("AMAZON")   -> "Amazon"
        combined.contains("HMAX") || combined.contains("HBO")      -> "HBO Max"
        combined.contains("ATVP") || combined.contains("APPLE")    -> "Apple TV+"
        else                                                        -> "Torrentio"
    }

    val seeders = Regex("(?:👤|S:|seeders:)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    val isRemux = combined.contains("REMUX")
    val isCam   = combined.contains("CAM") || combined.contains("HDCAM") ||
            (combined.contains(".TS.") || combined.contains(" TS "))

    return StreamMeta(videoCodecLabel = codecLabel, videoCodecColor = codecColor, hdrBadges = hdrBadges, audioBadges = audioBadges, langBadges = langBadges, subtitleBadges = subtitleBadges, releaseType = releaseType, releaseTypeColor = releaseTypeColor, provider = provider, seeders = seeders, isRemux = isRemux, isCam = isCam)
}

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
    onPlayDirectUrl: (videoUrl: String, imdbId: String, title: String, backdropUrl: String, posterUrl: String, logoUrl: String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onRecommendationClick: (String) -> Unit
){
    val context  = LocalContext.current
    val isRtl    = LocalLayoutDirection.current == LayoutDirection.Rtl

    var currentScrapeSeason by remember { mutableStateOf<Int?>(null) }
    var currentScrapeEpisode by remember { mutableStateOf<Int?>(null) }

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

    val playFR         = remember { FocusRequester() }
    val backBtnFR      = remember { FocusRequester() }
    val firstSourceFR  = remember { FocusRequester() }

    val seasonsFR      = remember { FocusRequester() }
    val episodesFR     = remember { FocusRequester() }
    val castFR         = remember { FocusRequester() }
    val collectionFR   = remember { FocusRequester() }
    val starringFR     = remember { FocusRequester() }

    val scrollState   = rememberLazyListState()
    var showSources   by remember { mutableStateOf(false) }
    var pendingAutoPlaySeason  by remember { mutableIntStateOf(-1) }
    var pendingAutoPlayEpisode by remember { mutableIntStateOf(-1) }
    var isAutoPlayPending by remember { mutableStateOf(false) }
    val focusManager  = LocalFocusManager.current

    LaunchedEffect(state.mediaInfo.id) {
        if (state.mediaInfo.id.startsWith("http")) {
            showSources = false
            currentScrapeSeason = null
            currentScrapeEpisode = null
            onEvent(DetailsEvent.InitiateScraping(state.mediaInfo.imdbId))
        }
    }

    LaunchedEffect(state.readyToPlayUrl) {
        state.readyToPlayUrl?.let { url ->
            showSources = false
            context.getSharedPreferences("player_context", Context.MODE_PRIVATE).edit {
                putInt("current_season", currentScrapeSeason ?: -1)
                putInt("current_episode", currentScrapeEpisode ?: -1)
            }
            onPlayDirectUrl(url, media.imdbId, media.title, media.backdropUrl, media.posterUrl, media.logoUrl ?: "")
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(DetailsEvent.RefreshProgress)
                val prefs = context.getSharedPreferences("player_context", Context.MODE_PRIVATE)
                val nextS = prefs.getInt("auto_play_season", -1)
                val nextE = prefs.getInt("auto_play_episode", -1)
                if (nextS != -1 && nextE != -1) {
                    prefs.edit {
                        remove("auto_play_season")
                        remove("auto_play_episode")
                    }
                    pendingAutoPlaySeason  = nextS
                    pendingAutoPlayEpisode = nextE
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingAutoPlaySeason, pendingAutoPlayEpisode, state.isLoadingData) {
        if (pendingAutoPlaySeason == -1 || pendingAutoPlayEpisode == -1) return@LaunchedEffect
        if (state.isLoadingData || media.imdbId.isBlank()) return@LaunchedEffect
        val s = pendingAutoPlaySeason
        val e = pendingAutoPlayEpisode
        pendingAutoPlaySeason  = -1
        pendingAutoPlayEpisode = -1
        if (s != state.selectedSeason) onEvent(DetailsEvent.SelectSeason(s))
        showSources          = false
        currentScrapeSeason  = s
        currentScrapeEpisode = e
        isAutoPlayPending    = true
        onEvent(DetailsEvent.InitiateScraping(media.imdbId, s, e))
    }

    LaunchedEffect(state.scrapingStatus, isAutoPlayPending) {
        if (!isAutoPlayPending) return@LaunchedEffect
        if (state.scrapingStatus == ScrapingStatus.Success && state.availableStreams.isNotEmpty()) {
            isAutoPlayPending = false
            onEvent(DetailsEvent.ResolveAndPlayStream(state.availableStreams.first()))
        } else if (state.scrapingStatus is ScrapingStatus.Error) {
            isAutoPlayPending = false
            showSources = true
        }
    }

    if (state.isLoadingData) {
        Box(Modifier.fillMaxSize().background(BK), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text(tr("Loading...", "טוען..."), color = DM, fontSize = 16.sp)
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
                    model = ImageRequest.Builder(context)
                        .data(media.backdropUrl)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true)
                        .build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else if (media.posterUrl.isNotEmpty()) {
                AsyncImage(model = media.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.5f))
            }
        }

        val sideGradient = remember(isRtl) {
            val colors = if (isRtl) listOf(Color.Transparent, BK.copy(0.85f), BK.copy(1f)) else listOf(BK.copy(1f), BK.copy(0.85f), Color.Transparent)
            Brush.horizontalGradient(colors)
        }
        val bottomGradient = remember {
            Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.6f), BK.copy(1f)), startY = 300f)
        }
        Box(Modifier.fillMaxSize().background(sideGradient))
        Box(Modifier.fillMaxSize().background(bottomGradient))

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
                            .focusRequester(backBtnFR).focusProperties {
                                if (!state.isFuzerDirect) { down = playFR; left = playFR; right = playFR }
                            }
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
                            else if (media.isSeries && media.totalSeasons > 0) Text(tr("${media.totalSeasons} Seasons", "${media.totalSeasons} עונות"), color = DM, fontSize = 13.sp)
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
                        if (media.overview.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = media.overview,
                                color = DM,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        if (!media.isSeries && (state.contentProgress ?: 0f) >= 0.02f) {
                            Spacer(Modifier.height(14.dp))
                            val prog = state.contentProgress ?: 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(WH.copy(0.15f))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(prog)
                                            .fillMaxHeight()
                                            .background(if (state.contentIsFinished) GLD else BR)
                                    )
                                }
                                val label = when {
                                    state.contentIsFinished -> tr("Watched ✓", "נצפה ✓")
                                    media.runtimeMinutes > 0 -> {
                                        val leftMin = ((1f - prog) * media.runtimeMinutes).toInt()
                                            .coerceAtLeast(1)
                                        tr("${leftMin}m left", "${leftMin} דקות נותרו")
                                    }
                                    else -> "%.0f%%".format(prog * 100)
                                }
                                Text(label, color = if (state.contentIsFinished) GLD else DM, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.focusGroup().focusProperties {
                                up = backBtnFR
                                enter = { dir ->
                                    if (dir == FocusDirection.Up || dir == FocusDirection.Down) playFR
                                    else FocusRequester.Default
                                }
                            }
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
                                            Text(tr("Connecting to Real-Debrid...", "מתחבר ל-Real-Debrid..."), color = DM, fontSize = 15.sp)
                                        }
                                    }
                                }
                            } else {
                                val isPartiallyWatched = (state.contentProgress ?: 0f) >= 0.02f && !state.contentIsFinished
                                val continueLabel: String = when {
                                    state.contentIsFinished -> tr("Watch Again", "צפה שוב")
                                    isPartiallyWatched && media.isSeries &&
                                            state.lastWatchedSeason != null && state.lastWatchedEpisode != null ->
                                        tr(
                                            "Continue S${state.lastWatchedSeason}:E${state.lastWatchedEpisode}",
                                            "המשך ע${state.lastWatchedSeason}:פ${state.lastWatchedEpisode}"
                                        )
                                    isPartiallyWatched -> tr("Continue", "המשך")
                                    else               -> tr("Play Now", "נגן עכשיו")
                                }
                                val resumeSeason  = state.lastWatchedSeason  ?: state.selectedSeason
                                val resumeEpisode = state.lastWatchedEpisode ?: 1

                                Surface(
                                    onClick = {
                                        showSources = true
                                        if (media.isSeries) {
                                            currentScrapeSeason  = resumeSeason
                                            currentScrapeEpisode = resumeEpisode
                                            onEvent(DetailsEvent.InitiateScraping(media.imdbId, resumeSeason, resumeEpisode))
                                        } else {
                                            currentScrapeSeason  = null
                                            currentScrapeEpisode = null
                                            onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                        }
                                    },
                                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                    colors   = ClickableSurfaceDefaults.colors(
                                        containerColor        = if (state.contentIsFinished) MT else WH,
                                        contentColor          = BK,
                                        focusedContainerColor = BR,
                                        focusedContentColor   = WH
                                    ),
                                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
                                    glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
                                    modifier = Modifier.wrapContentWidth().focusRequester(playFR)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (state.contentIsFinished) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(continueLabel, fontWeight = FontWeight.Black, fontSize = 15.sp,
                                            maxLines = 1, softWrap = false)
                                    }
                                }
                                ActionPill(tr("Trailer", "טריילר"), Icons.Default.PlayArrow) { launchTrailer(context, media.trailerUrl, media.title) }
                                ActionPill(
                                    label = if (state.availableStreams.isNotEmpty()) tr("Sources (${state.availableStreams.size})", "מקורות (${state.availableStreams.size})") else tr("Sources", "מקורות"),
                                    icon  = Icons.AutoMirrored.Filled.List
                                ) {
                                    showSources = true
                                    if (media.isSeries) {
                                        currentScrapeSeason = state.selectedSeason
                                        currentScrapeEpisode = 1
                                        onEvent(DetailsEvent.InitiateScraping(media.imdbId, state.selectedSeason, 1))
                                    } else {
                                        currentScrapeSeason = null
                                        currentScrapeEpisode = null
                                        onEvent(DetailsEvent.InitiateScraping(media.imdbId))
                                    }
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

            if (!state.isFuzerDirect && media.isSeries && media.totalSeasons > 0) {
                item {
                    Column(Modifier.fillMaxWidth().focusGroup()) {
                        SectionHeader(tr("Seasons & Episodes", "עונות ופרקים"), Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(12.dp))

                        val seasonsState = rememberLazyListState()
                        LazyRow(
                            state = seasonsState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = seasonsState),
                            modifier = Modifier
                                .padding(bottom = 14.dp)
                                .focusGroup()
                                .focusProperties {
                                    enter = { dir ->
                                        if (dir == FocusDirection.Up || dir == FocusDirection.Down) seasonsFR
                                        else FocusRequester.Default
                                    }
                                }
                                .lockFocusEdges()
                        ) {
                            item { Spacer(modifier = Modifier.width(56.dp)) }

                            items(media.totalSeasons, key = { idx -> "season_${media.id}_$idx" }, contentType = { _ -> "SeasonTab" }) { idx ->
                                val n = idx + 1
                                val sel = state.selectedSeason == n
                                Surface(
                                    onClick  = { onEvent(DetailsEvent.SelectSeason(n)) },
                                    shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors   = ClickableSurfaceDefaults.colors(containerColor = if (sel) BR else GL, contentColor = WH, focusedContainerColor = WH, focusedContentColor = BK),
                                    scale    = ClickableSurfaceDefaults.scale(1.05f),
                                    border   = ClickableSurfaceDefaults.border(border = if (sel) Border.None else Border(BorderStroke(1.dp, GB)), focusedBorder = Border.None),
                                    modifier = if (idx == 0) Modifier.focusRequester(seasonsFR) else Modifier
                                ) {
                                    Text(tr("Season $n", "עונה $n"), fontWeight = if (sel) FontWeight.Black else FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                                }
                            }

                            item { Spacer(modifier = Modifier.width(56.dp)) }
                        }

                        if (state.isEpisodesLoading) {
                            Box(Modifier.fillMaxWidth().height(145.dp), Alignment.Center) { LoadingIndicator() }
                        } else if (state.episodes.isNotEmpty()) {
                            val episodesState = rememberLazyListState()
                            LazyRow(
                                state = episodesState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                flingBehavior = rememberSnapFlingBehavior(lazyListState = episodesState),
                                modifier = Modifier
                                    .focusGroup()
                                    .focusProperties {
                                        enter = { dir ->
                                            if (dir == FocusDirection.Up || dir == FocusDirection.Down) episodesFR
                                            else FocusRequester.Default
                                        }
                                    }
                                    .lockFocusEdges()
                            ) {
                                item { Spacer(modifier = Modifier.width(50.dp)) }

                                itemsIndexed(state.episodes, key = { _, ep -> "${media.id}_${ep.id}" }, contentType = { _, _ -> "EpisodeCard" }) { idx, ep ->
                                    val fallbackImage = media.backdropUrl.ifBlank { media.posterUrl }
                                    EpisodeCard(
                                        episode = ep,
                                        fallbackImageUrl = fallbackImage,
                                        modifier = if (idx == 0) Modifier.focusRequester(episodesFR) else Modifier,
                                        onClick = {
                                            currentScrapeSeason = ep.seasonNumber
                                            currentScrapeEpisode = ep.episodeNumber
                                            showSources = true
                                            onEvent(DetailsEvent.InitiateScraping(media.imdbId, ep.seasonNumber, ep.episodeNumber))
                                        }
                                    )
                                }

                                item { Spacer(modifier = Modifier.width(50.dp)) }
                            }
                        }
                    }
                }
            }

            if (!state.isFuzerDirect && media.cast.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().focusGroup()) {
                        SectionHeader(tr("Cast & Crew", "שחקנים וצוות"), Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))

                        val castState = rememberLazyListState()
                        LazyRow(
                            state = castState,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = castState),
                            modifier = Modifier
                                .focusGroup()
                                .focusProperties {
                                    enter = { dir ->
                                        if (dir == FocusDirection.Up || dir == FocusDirection.Down) castFR
                                        else FocusRequester.Default
                                    }
                                }
                                .lockFocusEdges()
                        ) {
                            item { Spacer(modifier = Modifier.width(46.dp)) }

                            itemsIndexed(media.cast, contentType = { _, _ -> "CastCard" }) { idx, a ->
                                CastMemberCard(
                                    actor = a,
                                    modifier = if (idx == 0) Modifier.focusRequester(castFR) else Modifier
                                )
                            }

                            item { Spacer(modifier = Modifier.width(46.dp)) }
                        }
                    }
                }
            }

            if (!state.isFuzerDirect && media.collectionItems.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().focusGroup()) {
                        val collName = media.collectionName ?: "Collection"
                        SectionHeader(tr("More in $collName", "עוד באוסף $collName"), Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))

                        val collectionState = rememberLazyListState()
                        LazyRow(
                            state = collectionState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = collectionState),
                            modifier = Modifier
                                .focusGroup()
                                .focusProperties {
                                    enter = { dir ->
                                        if (dir == FocusDirection.Up || dir == FocusDirection.Down) collectionFR
                                        else FocusRequester.Default
                                    }
                                }
                                .lockFocusEdges()
                        ) {
                            item { Spacer(modifier = Modifier.width(50.dp)) }

                            itemsIndexed(media.collectionItems, key = { _, r -> r.id }, contentType = { _, _ -> "CollectionCard" }) { idx, rec ->
                                val tempMovie = Movie(id = rec.id, title = rec.title, posterUrl = rec.posterUrl, backdropUrl = "", rating = 0f, mediaType = "movie", overview = "", year = 0, genre = "")
                                com.luminastreams.tv.presentation.home.PosterCard(
                                    movie = tempMovie,
                                    modifier = if (idx == 0) Modifier.focusRequester(collectionFR) else Modifier,
                                    onClick = { onRecommendationClick(rec.id) }
                                )
                            }

                            item { Spacer(modifier = Modifier.width(50.dp)) }
                        }
                    }
                }
            }

            if (!state.isFuzerDirect && media.starringItems.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().focusGroup()) {
                        val actorName = media.starringActorName ?: ""
                        SectionHeader(tr("More Starring $actorName", "עוד בכיכובו של $actorName"), Modifier.padding(horizontal = 64.dp))
                        Spacer(Modifier.height(16.dp))

                        val starringState = rememberLazyListState()
                        LazyRow(
                            state = starringState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = starringState),
                            modifier = Modifier
                                .focusGroup()
                                .focusProperties {
                                    enter = { dir ->
                                        if (dir == FocusDirection.Up || dir == FocusDirection.Down) starringFR
                                        else FocusRequester.Default
                                    }
                                }
                                .lockFocusEdges()
                        ) {
                            item { Spacer(modifier = Modifier.width(50.dp)) }

                            itemsIndexed(media.starringItems, key = { _, r -> r.id }, contentType = { _, _ -> "StarringCard" }) { idx, rec ->
                                val tempMovie = Movie(id = rec.id, title = rec.title, posterUrl = rec.posterUrl, backdropUrl = "", rating = 0f, mediaType = if (rec.id.startsWith("tv")) "tv" else "movie", overview = "", year = 0, genre = "")
                                com.luminastreams.tv.presentation.home.PosterCard(
                                    movie = tempMovie,
                                    modifier = if (idx == 0) Modifier.focusRequester(starringFR) else Modifier,
                                    onClick = { onRecommendationClick(rec.id) }
                                )
                            }

                            item { Spacer(modifier = Modifier.width(50.dp)) }
                        }
                    }
                }
            }
        }

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
                            .padding(vertical = 24.dp)
                            .padding(start = if (isRtl) 24.dp else 0.dp, end = if (isRtl) 0.dp else 24.dp)
                            .width(660.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF0F0F13).copy(alpha = 0.98f))
                            .clickable(remember { MutableInteractionSource() }, null) {}
                    ) {

                        // ⚡ FIX: Render Premium Loading Overlay inside the side panel
                        when (val st = state.scrapingStatus) {
                            is ScrapingStatus.Searching -> {
                                PremiumLoadingOverlay(
                                    backdropUrl = media.backdropUrl, logoUrl = media.logoUrl,
                                    title = media.title, statusText = tr("Scanning Torrentio servers...", "סורק שרתי Torrentio...")
                                )
                            }
                            is ScrapingStatus.ResolvingDebrid -> {
                                val msg = if (st.streamId.contains("%") || st.streamId.contains("מוריד") ||
                                    st.streamId.contains("מתחיל") || st.streamId.contains("מוסיף"))
                                    st.streamId else tr("Resolving via Real-Debrid...", "מפענח דרך Real-Debrid...")
                                PremiumLoadingOverlay(
                                    backdropUrl = media.backdropUrl, logoUrl = media.logoUrl,
                                    title = media.title, statusText = msg
                                )
                            }
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
                            else -> {
                                Column(Modifier.padding(horizontal = 36.dp, vertical = 44.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(4.dp).height(36.dp).background(BR, RoundedCornerShape(2.dp)))
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text(tr("Available Sources", "מקורות זמינים"), color = WH, fontSize = 26.sp, fontWeight = FontWeight.Black)
                                            if (state.availableStreams.isNotEmpty()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(tr("${state.availableStreams.size} sources found", "נמצאו ${state.availableStreams.size} מקורות"), color = DM, fontSize = 13.sp)
                                                    val rdCount = state.availableStreams.count { it.isCachedRd }
                                                    if (rdCount > 0) {
                                                        Box(
                                                            Modifier.clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF1B5E20).copy(0.8f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(tr("$rdCount RD+ cached", "$rdCount מקורות ב-RD+"), color = WH, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(BR.copy(0.6f), Color(0x08FFFFFF)))))
                                    Spacer(Modifier.height(20.dp))

                                    val sourcesState = rememberLazyListState()
                                    LazyColumn(
                                        state = sourcesState,
                                        flingBehavior = rememberSnapFlingBehavior(lazyListState = sourcesState),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding      = PaddingValues(bottom = 64.dp),
                                        modifier            = Modifier.focusGroup().lockFocusEdges(lockRight = false, lockDown = true)
                                    ) {
                                        itemsIndexed(state.availableStreams, contentType = { _, _ -> "StreamItem" }) { index, stream ->
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
}

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

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val fileSizeColor by animateColorAsState(
        targetValue = if (isFocused) WH else DM,
        animationSpec = tween(150),
        label = "fileSizeAnim"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0CFFFFFF),
            focusedContainerColor = Color(0xFF282832),
            contentColor = WH,
            focusedContentColor = WH
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "#$rank",
                    color = WH.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(10.dp))

                Text(
                    text = meta.provider.uppercase(),
                    color = if (source.isCachedRd) Color(0xFF43A047) else Color(0xFF29B6F6),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.weight(1f))

                if (meta.seeders > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\uf0c0",
                            color = seederColor(meta.seeders),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${meta.seeders}",
                            color = seederColor(meta.seeders),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }

                val formattedSize = run {
                    val gb = source.sizeBytes / 1_073_741_824.0
                    if (gb >= 1.0) "%.2f GB".format(gb)
                    else "%.0f MB".format(source.sizeBytes / 1_048_576.0)
                }

                Text(
                    text = formattedSize,
                    color = fileSizeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = source.releaseGroup.ifEmpty { "UNKNOWN RELEASE" }.uppercase(),
                color = WH,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PremiumBadge(source.quality.name.replace("UHD_4K", "4K UHD").replace("FHD_1080P", "1080p").replace("HD_720P", "720p"), Color(0xFF0D47A1))

                if (source.isCachedRd) {
                    PremiumBadge("RD+", Color(0xFF1B5E20), icon = "\uf0e7")
                }

                if (meta.isCam) {
                    PremiumBadge("CAM", Color(0xFFB71C1C), icon = "\uf071")
                }

                if (meta.videoCodecLabel != "—") {
                    PremiumBadge(meta.videoCodecLabel, WH.copy(alpha = 0.8f), isOutline = true)
                }

                meta.hdrBadges.firstOrNull()?.let {
                    PremiumBadge(it.first, Color(0xFFFF8F00), isOutline = true)
                }

                meta.audioBadges.firstOrNull()?.let {
                    PremiumBadge(it.first, Color(0xFF8E24AA), isOutline = true)
                }

                meta.langBadges.forEach { (langLabel, color) ->
                    PremiumBadge(langLabel, color, isOutline = true)
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = source.filename.replace(".", " "),
                color = WH.copy(alpha = if (isFocused) 0.5f else 0.25f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
            .background(if (isOutline) Color.Transparent else color.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = if (isOutline) color.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Text(
                    text = icon,
                    color = color,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text = text.uppercase(),
                color = if (isOutline) color else WH,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

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
private fun EpisodeCard(
    episode: Episode,
    fallbackImageUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    Surface(
        onClick  = onClick,
        interactionSource = interactionSource,
        colors   = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border   = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border(BorderStroke(3.dp, WH))),
        glow     = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
        modifier = modifier.width(280.dp).aspectRatio(16f / 9f)
            .zIndex(if (isFocused) 10f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            val imageUrl = if (episode.stillUrl.isBlank() || episode.stillUrl.endsWith("null")) fallbackImageUrl else episode.stillUrl

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BK.copy(0.98f)), startY = 40f)))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(tr("Ep ${episode.episodeNumber}. ${episode.title}", "פרק ${episode.episodeNumber}. ${episode.title}"), color = WH, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("45m", color = DM, fontSize = 12.sp)
            }
            if (episode.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(BK.copy(0.6f))) {
                    Box(Modifier.fillMaxWidth(episode.progress).fillMaxHeight().background(BR).align(Alignment.CenterStart))
                }
            }

            if (episode.hasWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint     = WH,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CastMemberCard(actor: CastMember, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(150),
        label = "scale"
    )

    val shadowPx = with(LocalDensity.current) { 16.dp.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(100.dp).zIndex(if (isFocused) 10f else 0f)
    ) {
        Surface(
            onClick  = {},
            interactionSource = interactionSource,
            shape    = ClickableSurfaceDefaults.shape(CircleShape),
            colors   = ClickableSurfaceDefaults.colors(containerColor = GL, focusedContainerColor = WH),
            scale    = ClickableSurfaceDefaults.scale(1.0f),
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    shadowElevation = if (isFocused) shadowPx else 0f
                    shape = CircleShape
                    clip = true
                }
        ) {
            AsyncImage(model = actor.imageUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(actor.name, color = if (isFocused) WH else DM, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(actor.character, color = MT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

fun Modifier.lockFocusEdges(
    lockRight: Boolean = true,
    lockLeft: Boolean = false,
    lockDown: Boolean = false,
    lockUp: Boolean = false
): Modifier = this.focusProperties {
    exit = { direction ->
        when {
            direction == FocusDirection.Right && lockRight -> FocusRequester.Cancel
            direction == FocusDirection.Left && lockLeft -> FocusRequester.Cancel
            direction == FocusDirection.Down && lockDown -> FocusRequester.Cancel
            direction == FocusDirection.Up && lockUp -> FocusRequester.Cancel
            else -> FocusRequester.Default
        }
    }
}