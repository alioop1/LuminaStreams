@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.luminastreams.tv.presentation.player

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.*

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import android.graphics.Color as AndroidColor
import com.luminastreams.tv.data.local.WatchProgressManager

@Composable
fun tr(en: String, he: String): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) he else en

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val CustomSubtitlesIcon: ImageVector
    get() = ImageVector.Builder("Subtitles", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 6f)
            curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
            moveTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 14f); lineTo(4f, 14f); close()
            moveTo(10f, 12f); lineTo(20f, 12f); lineTo(20f, 14f); lineTo(10f, 14f); close()
            moveTo(14f, 16f); lineTo(20f, 16f); lineTo(20f, 18f); lineTo(14f, 18f); close()
            moveTo(4f, 16f); lineTo(12f, 16f); lineTo(12f, 18f); lineTo(4f, 18f); close()
        }
    }.build()

val CustomAudioIcon: ImageVector
    get() = ImageVector.Builder("Audio", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f); lineTo(12f, 14.5f)
            curveTo(11.6f, 14.2f, 11.1f, 14f, 10.5f, 14f)
            curveTo(9.1f, 14f, 8f, 15.1f, 8f, 16.5f)
            curveTo(8f, 17.9f, 9.1f, 19f, 10.5f, 19f)
            curveTo(11.9f, 19f, 13f, 17.9f, 13f, 16.5f)
            lineTo(13f, 6f); lineTo(17f, 6f); lineTo(17f, 3f); lineTo(12f, 3f); close()
        }
    }.build()

val CustomWebSubsIcon: ImageVector
    get() = ImageVector.Builder("WebSubs", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 4f); lineTo(5f, 4f); curveTo(3.89f, 4f, 3f, 4.9f, 3f, 6f)
            lineTo(3f, 18f); curveTo(3f, 19.1f, 3.89f, 20f, 5f, 20f); lineTo(14f, 20f)
            lineTo(14f, 18f); lineTo(5f, 18f); lineTo(5f, 6f); lineTo(19f, 6f); lineTo(19f, 13f)
            lineTo(21f, 13f); lineTo(21f, 6f); curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f); close()
            moveTo(7f, 14f); lineTo(13f, 14f); lineTo(13f, 16f); lineTo(7f, 16f); close()
            moveTo(7f, 10f); lineTo(17f, 10f); lineTo(17f, 12f); lineTo(7f, 12f); close()
            moveTo(20.49f, 19.08f); lineTo(22.96f, 21.54f); lineTo(21.54f, 22.96f)
            lineTo(19.08f, 20.49f); curveTo(18.41f, 20.81f, 17.68f, 21f, 16.9f, 21f)
            curveTo(14.19f, 21f, 12f, 18.81f, 12f, 16.1f); curveTo(12f, 13.39f, 14.19f, 11.2f, 16.9f, 11.2f)
            curveTo(19.61f, 11.2f, 21.8f, 13.39f, 21.8f, 16.1f); curveTo(21.8f, 16.88f, 21.61f, 17.61f, 21.29f, 18.28f)
            lineTo(20.49f, 19.08f); close()
            moveTo(16.9f, 13.2f); curveTo(15.29f, 13.2f, 14f, 14.49f, 14f, 16.1f)
            curveTo(14f, 17.71f, 15.29f, 19f, 16.9f, 19f); curveTo(18.51f, 19f, 19.8f, 17.71f, 19.8f, 16.1f)
            curveTo(19.8f, 14.49f, 18.51f, 13.2f, 16.9f, 13.2f); close()
        }
    }.build()

enum class AspectRatioMode(
    val label: String,
    val description: String,
    val resizeMode: Int,
    val forcedRatio: Float? = null
) {
    NORMAL("Normal", "Fit to screen", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", "Crop to fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", "Stretch to fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    RATIO_16_9("16:9", "Widescreen", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, 16f / 9f),
    RATIO_4_3("4:3", "Classic TV", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, 4f / 3f),
    RATIO_21_9("21:9", "CinemaScope", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, 21f / 9f)
}

private val RED          = Color(0xFFE50914)
private val WHITE        = Color(0xFFFFFFFF)
private val DIM          = Color(0xAAFFFFFF)
private val DV_BLUE      = Color(0xFF00B4FF)
private val ATMOS_PURPLE = Color(0xFF7B2FBE)
private val RES_GRAY     = Color(0xFF424242)
private val POPUP_BG     = Color(0xE6141414)

enum class ActiveMenu { NONE, AUDIO, EMBEDDED_SUBS, WEB_SUBS, ASPECT_RATIO }

// ⚡ FIX: Removed posterUrl entirely. ONLY uses TMDB Logo and Cinematic Backdrop!
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

private fun applyAfrForContent(activity: Activity, contentFps: Float) {
    val win     = activity.window ?: return
    val display = win.decorView.display ?: return
    val current = display.mode
    val targetFps = when {
        contentFps in 23.9f..24.1f -> 24f
        contentFps in 25f..25.1f   -> 25f
        contentFps in 29.9f..30.1f -> 30f
        contentFps in 47.9f..48.1f -> 48f
        contentFps in 49.9f..50.1f -> 50f
        contentFps in 59.9f..60.1f -> 60f
        else -> contentFps
    }
    val sameRes = display.supportedModes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }
    val best = sameRes.minByOrNull { abs(it.refreshRate - targetFps) }
        ?: display.supportedModes.minByOrNull { abs(it.refreshRate - targetFps) }
        ?: return
    if (best.modeId == current.modeId) return
    win.attributes = win.attributes.also { a -> a.preferredDisplayModeId = best.modeId }
}

private fun restoreDisplayMode(activity: Activity) {
    val win = activity.window ?: return
    win.attributes = win.attributes.also { a -> a.preferredDisplayModeId = 0 }
}

private fun enableHdrWindow(activity: Activity) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            activity.window.attributes = activity.window.attributes.also { lp ->
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val cls = ActivityInfo::class.java
            val colorModeField = runCatching { cls.getField("COLOR_MODE_HDR") }.getOrNull()
            val hdrMode = colorModeField?.getInt(null) ?: 2
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val setColorMode = Activity::class.java.getMethod("setRequestedColorMode", Int::class.java)
            setColorMode.invoke(activity, hdrMode)
        }
    }
}

private fun applySurfaceDolbyVision(surfaceView: SurfaceView) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val method = SurfaceView::class.java.getMethod("setHdrOutputMode", Int::class.java)
            method.invoke(surfaceView, 3)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    exo: ExoPlayerWrapper,
    selectedAspectRatio: AspectRatioMode,
    onSurfaceReady: (Boolean) -> Unit
) {
    val currentCues by exo.currentCues.collectAsState()
    val videoAspectRatio by exo.videoAspectRatio.collectAsState()

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        factory = { ctx ->
            val arLayout = AspectRatioFrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setAspectRatio(16f / 9f)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            val surfaceView = SurfaceView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                keepScreenOn = true
                setZOrderMediaOverlay(true)
                applySurfaceDolbyVision(this)
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        exo.player.setVideoSurfaceView(this@apply)
                        onSurfaceReady(true)
                    }
                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        onSurfaceReady(false)
                        exo.player.clearVideoSurface()
                    }
                })
            }
            val subtitleView = SubtitleView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val textColor = if (exo.useYellowSubtitles) AndroidColor.YELLOW else AndroidColor.WHITE
                setStyle(CaptionStyleCompat(textColor, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, AndroidColor.BLACK, null))
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * exo.subtitleFontScale)
                setBottomPaddingFraction(0.08f)
            }
            arLayout.addView(surfaceView)
            arLayout.addView(subtitleView)
            arLayout
        },
        update = { arLayout ->
            val sub = arLayout.getChildAt(1) as? SubtitleView
            sub?.setCues(currentCues)

            val ratio = selectedAspectRatio.forcedRatio ?: videoAspectRatio.takeIf { it > 0f } ?: (16f / 9f)
            val cacheTag = "${ratio}_${selectedAspectRatio.resizeMode}"

            if (arLayout.tag != cacheTag) {
                arLayout.setAspectRatio(ratio)
                arLayout.resizeMode = selectedAspectRatio.resizeMode
                arLayout.tag = cacheTag
            }
        }
    )
}

@Composable
fun IsolatedUpNextCard(
    showNextEpisodeCard: Boolean,
    nextEpSeason: Int,
    nextEpEpisode: Int,
    isSameSeasonNext: Boolean,
    isRtl: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(10) }

    LaunchedEffect(showNextEpisodeCard) {
        if (!showNextEpisodeCard) return@LaunchedEffect
        countdown = 10
        for (i in 10 downTo 1) {
            countdown = i
            delay(1000)
        }
        onPlayNext()
    }

    AnimatedVisibility(
        visible  = showNextEpisodeCard,
        enter    = slideInHorizontally(initialOffsetX = { if (isRtl) -it else it }, animationSpec = tween(350)) + fadeIn(tween(250)),
        exit     = slideOutHorizontally(targetOffsetX = { if (isRtl) -it else it }, animationSpec = tween(280)) + fadeOut(tween(200)),
        modifier = Modifier
            .padding(start = if (isRtl) 48.dp else 0.dp, end = if (isRtl) 0.dp else 48.dp, bottom = 135.dp)
            .zIndex(150f)
    ) {
        val nextFR = remember { FocusRequester() }
        val dismissFR = remember { FocusRequester() }

        LaunchedEffect(showNextEpisodeCard) {
            if (showNextEpisodeCard) { delay(150); runCatching { nextFR.requestFocus() } }
        }

        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0101018))
                .border(1.dp, WHITE.copy(0.12f), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .focusGroup()
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) {
                        when (ev.key) {
                            Key.DirectionUp, Key.DirectionDown -> true
                            Key.DirectionLeft -> { runCatching { if (isRtl) dismissFR.requestFocus() else nextFR.requestFocus() }; true }
                            Key.DirectionRight -> { runCatching { if (isRtl) nextFR.requestFocus() else dismissFR.requestFocus() }; true }
                            else -> false
                        }
                    } else false
                }
        ) {
            Text(tr("UP NEXT", "הבא בתור"), color = DIM.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isSameSeasonNext) tr("Episode $nextEpEpisode", "פרק $nextEpEpisode") else tr("Season $nextEpSeason • Episode $nextEpEpisode", "עונה $nextEpSeason • פרק $nextEpEpisode"),
                color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(WHITE.copy(0.15f))) {
                Box(Modifier.fillMaxWidth((10f - countdown) / 10f).fillMaxHeight().background(RED))
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onPlayNext,
                    shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                    scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    glow     = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RED.copy(0.5f), 16.dp)),
                    modifier = Modifier.weight(1f).height(46.dp).focusRequester(nextFR)
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Text(tr("Play ($countdown)", "נגן ($countdown)"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Surface(
                    onClick = onDismiss,
                    shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = WHITE.copy(0.08f), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                    border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, WHITE.copy(0.2f))), focusedBorder = Border.None),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.height(46.dp).focusRequester(dismissFR)
                ) {
                    Box(Modifier.padding(horizontal = 18.dp).fillMaxHeight(), Alignment.Center) {
                        Text(tr("Dismiss", "דחה"), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    videoUrl:       String,
    imdbId:         String,
    title:          String = "",
    backdropUrl:    String = "",
    posterUrl:      String = "", // Left in signature to prevent Navigation crash, but unused visually
    logoUrl:        String = "",
    onNavigateBack: () -> Unit,
    viewModel:      PlayerViewModel = viewModel()
) {
    val context          = LocalContext.current
    val isRtl            = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state            by viewModel.state.collectAsState()
    val exo              = remember { ExoPlayerWrapper(context) }
    val isPlaying        by exo.isPlaying.collectAsState()
    val error            by exo.playerError.collectAsState()
    val currentTracks    by exo.currentTracks.collectAsState()
    val isDolbyVision    by exo.isDolbyVision.collectAsState()
    val isDolbyAtmos     by exo.isDolbyAtmos.collectAsState()
    val contentFps       by exo.contentFrameRate.collectAsState()

    val afrEnabled = remember {
        context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            .getBoolean("afr", false)
    }

    var selectedAspectRatio by remember { mutableStateOf(AspectRatioMode.NORMAL) }

    var surfaceReady      by remember { mutableStateOf(false) }
    var prepared          by remember { mutableStateOf(false) }
    var showControls      by remember { mutableStateOf(true) }
    var activeMenu        by remember { mutableStateOf(ActiveMenu.NONE) }
    var selectedWebSubUrl by remember { mutableStateOf<String?>(null) }
    var pendingSubIndex   by remember { mutableStateOf<Int?>(null) }
    var subtitleApplied   by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }

    val playerPrefs = remember { context.getSharedPreferences("player_context", Context.MODE_PRIVATE) }
    val seasonPref  = remember { playerPrefs.getInt("current_season", -1) }
    val episodePref = remember { playerPrefs.getInt("current_episode", -1) }

    val season  = if (seasonPref  != -1 && imdbId.isNotBlank()) seasonPref  else null
    val episode = if (episodePref != -1 && imdbId.isNotBlank()) episodePref else null

    val totalEpisodesInSeason = remember { playerPrefs.getInt("total_episodes_in_season", -1) }
    val totalSeasonsInShow    = remember { playerPrefs.getInt("total_seasons", -1) }

    val (nextEpSeason, nextEpEpisode) = remember(season, episode, totalEpisodesInSeason, totalSeasonsInShow) {
        when {
            season == null || episode == null -> Pair(null, null)
            totalEpisodesInSeason > 0 && episode < totalEpisodesInSeason ->
                Pair(season, episode + 1)
            totalSeasonsInShow > 0 && season < totalSeasonsInShow ->
                Pair(season + 1, 1)
            totalEpisodesInSeason == -1 ->
                Pair(season, episode + 1)
            else -> Pair(null, null)
        }
    }

    var showNextEpisodeCard  by remember { mutableStateOf(false) }
    var nextEpCardDismissed  by remember { mutableStateOf(false) }

    val progressManager = remember { WatchProgressManager(context) }
    val progressKey = remember(imdbId, season, episode) {
        when {
            imdbId.isBlank()                  -> ""
            season != null && episode != null -> progressManager.episodeKey(imdbId, season, episode)
            else                              -> progressManager.movieKey(imdbId)
        }
    }
    val savedPosition = remember(progressKey) {
        if (progressKey.isEmpty()) -1L
        else progressManager.get(progressKey)?.positionMs?.takeIf { it > 30_000L }
            ?: context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)
                .getLong("progress_$imdbId", -1L)
    }
    var showResumeDialog by remember { mutableStateOf(false) }
    var resumeHandled    by remember { mutableStateOf(false) }

    val playPauseFR = remember { FocusRequester() }
    val seekBarFR   = remember { FocusRequester() }
    val sideMenuFR  = remember { FocusRequester() }

    val controlsBg = remember { Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))) }

    // ⚡ FIX: Using a background Coroutine Job instead of activityTick state means
    // button presses will NEVER force the entire screen to recompose and lag!
    val scope = rememberCoroutineScope()
    val hideControlsJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val resetHideControlsTimeout = {
        hideControlsJob.value?.cancel()
        if (showControls && isPlaying && activeMenu == ActiveMenu.NONE && !showResumeDialog) {
            hideControlsJob.value = scope.launch {
                delay(5000)
                showControls = false
            }
        }
    }

    LaunchedEffect(Unit) { context.findActivity()?.let { enableHdrWindow(it) } }
    LaunchedEffect(videoUrl, imdbId) { viewModel.loadMedia(videoUrl, imdbId, season, episode) }

    LaunchedEffect(surfaceReady) {
        if (surfaceReady && !prepared) {
            prepared = true
            exo.prepareStream(videoUrl)

            if (savedPosition <= 30_000L) {
                exo.play()
            } else {
                exo.pause()
            }

            delay(3000)
            if (isPlaying) showControls = false
        }
    }

    LaunchedEffect(contentFps) {
        if (!afrEnabled || contentFps <= 0f) return@LaunchedEffect
        val activity = context.findActivity() ?: return@LaunchedEffect
        applyAfrForContent(activity, contentFps)
    }

    DisposableEffect(Unit) {
        onDispose {
            exo.release()
            if (afrEnabled) context.findActivity()?.let { restoreDisplayMode(it) }
        }
    }

    LaunchedEffect(prepared, isPlaying) {
        if (!prepared || !isPlaying) return@LaunchedEffect
        var tickCount = 0
        while (isActive) {
            delay(1000)
            val pos = exo.player.currentPosition
            val dur = exo.player.duration.coerceAtLeast(1L)

            val remaining = dur - pos
            if (nextEpSeason != null && !nextEpCardDismissed && !showNextEpisodeCard &&
                dur > 60_000 && remaining in 1L..45_000L) {
                showNextEpisodeCard = true
            }

            if (tickCount % 5 == 0) {
                if (pos > 10_000L && progressKey.isNotEmpty()) {
                    if (pos.toFloat() / dur.toFloat() < 0.95f) {
                        progressManager.save(progressKey, pos, dur)
                    } else {
                        progressManager.save(progressKey, dur, dur)
                    }
                }
            }
            tickCount++
        }
    }

    LaunchedEffect(prepared) {
        if (prepared && savedPosition > 30_000L && !resumeHandled) {
            delay(800); showResumeDialog = true
        }
    }

    LaunchedEffect(isPlaying) { if (isPlaying) hasStartedPlaying = true }

    LaunchedEffect(state.availableSubtitles) {
        if (state.availableSubtitles.isEmpty() || subtitleApplied) return@LaunchedEffect
        val defLang = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            .getString("def_subs", "Hebrew") ?: "Hebrew"
        if (defLang == "None") return@LaunchedEffect
        val langCode = if (defLang == "Hebrew") "heb" else "eng"
        val idx = state.availableSubtitles.indexOfFirst { it.lang.contains(langCode, ignoreCase = true) }
        if (idx >= 0) {
            pendingSubIndex   = idx
            selectedWebSubUrl = state.availableSubtitles[idx].url
        }
    }

    LaunchedEffect(prepared, pendingSubIndex) {
        val idx = pendingSubIndex ?: return@LaunchedEffect
        if (!prepared || subtitleApplied) return@LaunchedEffect
        val subs = state.availableSubtitles
        if (idx >= subs.size) return@LaunchedEffect

        repeat(60) {
            if (exo.player.currentMediaItem != null) return@repeat
            delay(300)
        }

        if (exo.player.currentMediaItem == null) return@LaunchedEffect
        val langCode = if (
            context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                .getString("def_subs", "Hebrew") == "Hebrew"
        ) "heb" else "eng"

        val candidates = subs.mapIndexedNotNull { i, sub ->
            if (sub.lang.contains(langCode, ignoreCase = true)) i else null
        }

        for (ci in candidates) {
            val sub = subs[ci]
            exo.applySubtitle(sub.url)

            repeat(12) {
                if (exo.subtitleApplied.value) return@repeat
                delay(200)
            }

            if (exo.subtitleApplied.value) {
                subtitleApplied   = true
                selectedWebSubUrl = sub.url
                break
            }
        }
    }

    LaunchedEffect(activeMenu) {
        if (activeMenu == ActiveMenu.NONE && showControls) {
            delay(50); runCatching { playPauseFR.requestFocus() }
        }
    }

    LaunchedEffect(showControls, isPlaying, activeMenu, showResumeDialog) {
        resetHideControlsTimeout()
    }

    BackHandler {
        when {
            showResumeDialog              -> showResumeDialog = false
            showNextEpisodeCard           -> { showNextEpisodeCard = false; nextEpCardDismissed = true }
            activeMenu != ActiveMenu.NONE -> activeMenu = ActiveMenu.NONE
            showControls                  -> showControls = false
            else                          -> { exo.pause(); onNavigateBack() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .focusTarget()
            // ⚡ FIX: Any key press now resets the timer invisibly WITHOUT lagging the UI
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    resetHideControlsTimeout()
                }
                false
            }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> false
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (activeMenu == ActiveMenu.NONE && !showResumeDialog && !showNextEpisodeCard) {
                            if (showControls) { if (isPlaying) exo.pause() else exo.play() }
                            else showControls = true
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (activeMenu == ActiveMenu.NONE && !showResumeDialog) {
                            showControls = true; true
                        } else false
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) exo.pause() else exo.play()
                        showControls = true; true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls && activeMenu == ActiveMenu.NONE && !showResumeDialog && !showNextEpisodeCard) {
                            showControls = true; true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        VideoPlayerSurface(
            exo = exo,
            selectedAspectRatio = selectedAspectRatio,
            onSurfaceReady = { surfaceReady = it }
        )

        AnimatedVisibility(
            visible = !hasStartedPlaying && error == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(800)),
            modifier = Modifier.fillMaxSize().zIndex(200f)
        ) {
            PremiumLoadingOverlay(
                backdropUrl = backdropUrl,
                logoUrl = logoUrl,  // Only Logo! No Poster!
                title = title,
                statusText = tr("Loading and connecting...", "טוען ומתחבר לזרם..."),
                baseColor = Color.Black
            )
        }

        var activeVideoFormat by remember { mutableStateOf<Format?>(null) }
        LaunchedEffect(currentTracks) {
            val format = currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                .firstOrNull { it.width > 0 && it.height > 0 }
            if (format != null) activeVideoFormat = format
        }

        val resolutionBadge = remember(activeVideoFormat) {
            val h = activeVideoFormat?.height ?: 0
            when { h >= 2160 -> "4K UHD"; h >= 1080 -> "1080p"; h >= 720 -> "720p"; h > 0 -> "${h}p"; else -> null }
        }
        val hzBadge = remember(activeVideoFormat, contentFps) {
            val fps = if (contentFps > 0f) contentFps else activeVideoFormat?.frameRate ?: 0f
            if (fps > 0f) when { fps > 59f -> "60Hz"; fps > 49f -> "50Hz"; fps > 29f -> "30Hz"; fps > 24f -> "25Hz"; else -> "24Hz" }
            else null
        }
        var showInfoBadges by remember { mutableStateOf(false) }
        LaunchedEffect(resolutionBadge, hzBadge, isDolbyVision, isDolbyAtmos) {
            val hasData = resolutionBadge != null || hzBadge != null || isDolbyVision || isDolbyAtmos
            if (hasData) { showInfoBadges = true; delay(3500); showInfoBadges = false }
        }

        Row(modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp).zIndex(50f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = showInfoBadges && resolutionBadge != null, enter = fadeIn(), exit = fadeOut(tween(600))) { InfoBadge(text = resolutionBadge ?: "", color = RES_GRAY) }
            AnimatedVisibility(visible = showInfoBadges && hzBadge != null, enter = fadeIn(), exit = fadeOut(tween(600))) { InfoBadge(text = hzBadge ?: "", color = RES_GRAY) }
            AnimatedVisibility(visible = showInfoBadges && isDolbyVision, enter = fadeIn(), exit = fadeOut(tween(600))) { InfoBadge(text = "DOLBY VISION", color = DV_BLUE) }
            AnimatedVisibility(visible = showInfoBadges && isDolbyAtmos, enter = fadeIn(), exit = fadeOut(tween(600))) { InfoBadge(text = "DOLBY ATMOS", color = ATMOS_PURPLE) }
        }

        if (selectedAspectRatio != AspectRatioMode.NORMAL) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 28.dp).zIndex(50f).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(0.7f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                val modeLabel = when (selectedAspectRatio) {
                    AspectRatioMode.NORMAL -> tr("Normal", "רגיל")
                    AspectRatioMode.ZOOM   -> tr("Zoom", "זום")
                    AspectRatioMode.STRETCH -> tr("Stretch", "מתיחה")
                    else -> selectedAspectRatio.label
                }
                Text("⬛ $modeLabel", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        }

        Box(Modifier.align(if (isRtl) Alignment.BottomStart else Alignment.BottomEnd)) {
            IsolatedUpNextCard(
                showNextEpisodeCard = showNextEpisodeCard && nextEpSeason != null && nextEpEpisode != null,
                nextEpSeason = nextEpSeason ?: 0,
                nextEpEpisode = nextEpEpisode ?: 0,
                isSameSeasonNext = nextEpSeason == season,
                isRtl = isRtl,
                onPlayNext = {
                    if (nextEpSeason != null && nextEpEpisode != null) {
                        playerPrefs.edit { putInt("auto_play_season", nextEpSeason); putInt("auto_play_episode", nextEpEpisode) }
                        exo.pause(); onNavigateBack()
                    }
                },
                onDismiss = { showNextEpisodeCard = false; nextEpCardDismissed = true }
            )
        }

        if (showResumeDialog) {
            val resumeFR    = remember { FocusRequester() }
            val fromStartFR = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(100); runCatching { resumeFR.requestFocus() } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.75f)).zIndex(300f), Alignment.Center) {
                Column(
                    modifier = Modifier.width(480.dp).background(Color(0xFF1A1A24), RoundedCornerShape(24.dp)).padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = RED, modifier = Modifier.size(48.dp))
                    Text(tr("Continue Watching?", "להמשיך צפייה?"), color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text(tr("Stopped at", "נעצר ב-") + " ${formatTime(savedPosition)}", color = DIM, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) {
                                when {
                                    ev.key == Key.DirectionRight -> { runCatching { if (isRtl) resumeFR.requestFocus() else fromStartFR.requestFocus() }; true }
                                    ev.key == Key.DirectionLeft  -> { runCatching { if (isRtl) fromStartFR.requestFocus() else resumeFR.requestFocus() }; true }
                                    else -> false
                                }
                            } else false
                        }
                    ) {
                        Surface(
                            onClick  = { showResumeDialog = false; resumeHandled = true; exo.seekTo(savedPosition); exo.play() },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.weight(1f).height(52.dp).focusRequester(resumeFR)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr("▶ Continue", "▶ המשך"), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center) } }
                        Surface(
                            onClick  = { showResumeDialog = false; resumeHandled = true; progressManager.remove(progressKey); exo.play() },
                            shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors   = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A38), focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                            scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            modifier = Modifier.weight(1f).height(52.dp).focusRequester(fromStartFR)
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr("From Start", "מהתחלה"), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center) } }
                    }
                }
            }
        }

        if (error != null) {
            val errorFR = remember { FocusRequester() }
            LaunchedEffect(error) { delay(100); runCatching { errorFR.requestFocus() } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.88f)).zIndex(200f), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(48.dp)) {
                    Box(Modifier.size(80.dp).background(RED.copy(0.15f), CircleShape), Alignment.Center) {
                        Icon(Icons.Default.Warning, null, tint = RED, modifier = Modifier.size(40.dp))
                    }
                    Text(tr("Playback Error", "שגיאת נגן"), color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text(error!!, color = DIM, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick  = { exo.clearError(); onNavigateBack() },
                        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors   = ClickableSurfaceDefaults.colors(containerColor = RED, focusedContainerColor = WHITE, contentColor = WHITE, focusedContentColor = Color.Black),
                        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.height(52.dp).focusRequester(errorFR)
                    ) {
                        Box(Modifier.padding(horizontal = 32.dp).fillMaxHeight(), Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                                Text(tr("Back to Sources", "חזור למקורות"), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = showControls && error == null && !showResumeDialog,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).background(controlsBg))

                AnimatedVisibility(
                    visible  = !isPlaying,
                    enter    = scaleIn(tween(120)) + fadeIn(tween(120)),
                    exit     = scaleOut(tween(100)) + fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(Modifier.size(80.dp).background(Color.Black.copy(0.6f), CircleShape).border(2.dp, WHITE.copy(0.7f), CircleShape), Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = WHITE, modifier = Modifier.size(44.dp))
                    }
                }

                AnimatedVisibility(
                    visible = activeMenu != ActiveMenu.NONE,
                    enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(tween(200)),
                    exit = slideOutVertically(targetOffsetY = { 30 }) + fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 120.dp, end = 48.dp).zIndex(100f)
                ) {
                    PlayerPopupMenu(
                        activeMenu = activeMenu, exo = exo, state = state,
                        selectedWebSubUrl = selectedWebSubUrl, selectedAspectRatio = selectedAspectRatio, sideMenuFR = sideMenuFR,
                        onApplySubtitle = { url ->
                            exo.applySubtitle(url); selectedWebSubUrl = url
                            subtitleApplied = true; pendingSubIndex = null; activeMenu = ActiveMenu.NONE
                        },
                        onApplyAspectRatio = { mode -> selectedAspectRatio = mode; activeMenu = ActiveMenu.NONE },
                        onClose = { activeMenu = ActiveMenu.NONE }
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    PlayerProgressControls(
                        exoWrapper    = exo, isPlaying = isPlaying, isRtl = isRtl, seekFR = seekBarFR,
                        onUpPressed   = {},
                        onDownPressed = { runCatching { playPauseFR.requestFocus() } }
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                runCatching { seekBarFR.requestFocus() }; true
                            } else false
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PlayerIconButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                focusRequester = playPauseFR,
                                onClick = { if (isPlaying) exo.pause() else exo.play() }
                            )
                            PlayerIconButton(icon = Icons.Default.Close, onClick = { exo.pause(); onNavigateBack() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PlayerIconButton(CustomAudioIcon, isActive = activeMenu == ActiveMenu.AUDIO) {
                                activeMenu = if (activeMenu == ActiveMenu.AUDIO) ActiveMenu.NONE else ActiveMenu.AUDIO
                            }
                            PlayerIconButton(CustomSubtitlesIcon, isActive = activeMenu == ActiveMenu.EMBEDDED_SUBS) {
                                activeMenu = if (activeMenu == ActiveMenu.EMBEDDED_SUBS) ActiveMenu.NONE else ActiveMenu.EMBEDDED_SUBS
                            }
                            PlayerIconButton(CustomWebSubsIcon, isActive = activeMenu == ActiveMenu.WEB_SUBS) {
                                if (!state.isSubtitlesLoading) {
                                    activeMenu = if (activeMenu == ActiveMenu.WEB_SUBS) ActiveMenu.NONE else ActiveMenu.WEB_SUBS
                                }
                            }
                            PlayerIconButton(Icons.Default.AspectRatio, isActive = activeMenu == ActiveMenu.ASPECT_RATIO) {
                                activeMenu = if (activeMenu == ActiveMenu.ASPECT_RATIO) ActiveMenu.NONE else ActiveMenu.ASPECT_RATIO
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBadge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun PlayerIconButton(
    icon: ImageVector, isActive: Boolean = false,
    focusRequester: FocusRequester? = null, onClick: () -> Unit
) {
    val activeColor = Color(0xFFE50914)
    Surface(
        onClick = onClick,
        shape   = ClickableSurfaceDefaults.shape(CircleShape),
        colors  = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) activeColor else Color.Transparent,
            focusedContainerColor = if (isActive) activeColor else Color.White,
            contentColor = Color.White,
            focusedContentColor = if (isActive) Color.White else Color.Black
        ),
        scale   = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        border  = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        modifier = Modifier.size(44.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(icon, null, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
fun PlayerPopupMenu(
    activeMenu: ActiveMenu, exo: ExoPlayerWrapper, state: PlayerUiState,
    selectedWebSubUrl: String?, selectedAspectRatio: AspectRatioMode,
    sideMenuFR: FocusRequester, onApplySubtitle: (String) -> Unit,
    onApplyAspectRatio: (AspectRatioMode) -> Unit, onClose: () -> Unit
) {
    val currentTracks by exo.currentTracks.collectAsState()
    LaunchedEffect(activeMenu) { delay(50); runCatching { sideMenuFR.requestFocus() } }

    Box(
        Modifier.width(320.dp).heightIn(max = 350.dp)
            .background(POPUP_BG, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    if (ev.key == Key.Back || ev.key == Key.Escape || ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight) {
                        onClose(); true
                    } else false
                } else false
            }
    ) {
        when (activeMenu) {
            ActiveMenu.AUDIO -> Column {
                Text(tr("Audio Tracks", "רצועות שמע"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
                TrackListUi(exo = exo, groups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }, trackType = C.TRACK_TYPE_AUDIO, focusReq = sideMenuFR, onClose = onClose)
            }
            ActiveMenu.EMBEDDED_SUBS -> Column {
                Text(tr("Subtitles", "כתוביות"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
                TrackListUi(exo = exo, groups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }, trackType = C.TRACK_TYPE_TEXT, focusReq = sideMenuFR, onClose = onClose)
            }
            ActiveMenu.WEB_SUBS -> Column {
                Text(tr("Web Subtitles", "כתוביות רשת"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
                if (state.availableSubtitles.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                        Text(if (state.isSubtitlesLoading) tr("Searching...", "מחפש...") else tr("No subtitles found", "לא נמצאו כתוביות"), color = DIM, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.focusGroup()) {
                        itemsIndexed(state.availableSubtitles, key = { _, s -> s.url }) { index, sub ->
                            TrackItemCard(
                                title = "${sub.lang.uppercase()} ${getFlagEmoji(sub.lang)}", subtitle = sub.source,
                                isSelected = sub.url == selectedWebSubUrl,
                                modifier = Modifier.then(if (index == 0) Modifier.focusRequester(sideMenuFR) else Modifier),
                                onClick = { onApplySubtitle(sub.url) }
                            )
                        }
                    }
                }
            }
            ActiveMenu.ASPECT_RATIO -> Column {
                Text(tr("Aspect Ratio", "יחס תצוגה"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.focusGroup()) {
                    itemsIndexed(AspectRatioMode.entries) { index, mode ->
                        val modeLabel = when (mode) {
                            AspectRatioMode.NORMAL  -> tr("Normal", "רגיל")
                            AspectRatioMode.ZOOM    -> tr("Zoom", "זום")
                            AspectRatioMode.STRETCH -> tr("Stretch", "מתיחה")
                            else -> mode.label
                        }
                        TrackItemCard(
                            title = modeLabel, subtitle = mode.description, isSelected = mode == selectedAspectRatio,
                            modifier = Modifier.then(if (index == 0) Modifier.focusRequester(sideMenuFR) else Modifier),
                            onClick = { onApplyAspectRatio(mode) }
                        )
                    }
                }
            }
            ActiveMenu.NONE -> {}
        }
    }
}

@Composable
fun TrackListUi(
    exo: ExoPlayerWrapper, groups: List<androidx.media3.common.Tracks.Group>,
    trackType: Int, focusReq: FocusRequester, onClose: () -> Unit
) {
    val trackList = remember(groups) {
        val list = mutableListOf<Triple<androidx.media3.common.Tracks.Group?, Int, String>>()
        if (trackType == C.TRACK_TYPE_TEXT) list.add(Triple(null, -1, "Turn Off"))
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val format   = group.mediaTrackGroup.getFormat(i)
                val lang     = format.language ?: "Unknown"
                val label    = format.label    ?: ""
                val channels = if (format.channelCount > 0) "${format.channelCount}Ch" else ""
                val codec    = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                val dolbyTag = when (format.sampleMimeType) {
                    "audio/eac3-joc"    -> "ATMOS"
                    "video/dolby-vision" -> "DV"
                    else -> ""
                }
                val name = listOf(lang.uppercase(), label, channels, codec, dolbyTag).filter { it.isNotBlank() }.joinToString(" • ")
                list.add(Triple(group, i, name))
            }
        }
        list
    }

    if (trackList.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp).focusRequester(focusReq).focusable(), Alignment.Center) {
            Text(tr("No tracks available", "אין רצועות זמינות"), color = DIM, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.focusGroup()) {
            itemsIndexed(trackList) { index, (group, trackIndex, name) ->
                val isFirst    = index == 0
                val isSelected = group?.isTrackSelected(trackIndex) ?: (trackType == C.TRACK_TYPE_TEXT && name.contains("Turn Off") && groups.none { it.isSelected })
                val displayName = if (name == "Turn Off") tr("Turn Off", "כבה") else name
                TrackItemCard(
                    title = displayName, subtitle = if (group == null) "" else tr("Internal", "פנימי"),
                    isSelected = isSelected,
                    modifier = Modifier.then(if (isFirst) Modifier.focusRequester(focusReq) else Modifier),
                    onClick = {
                        val builder = exo.player.trackSelectionParameters.buildUpon()
                        builder.clearOverridesOfType(trackType)
                        if (group == null) builder.setTrackTypeDisabled(trackType, true)
                        else {
                            builder.setTrackTypeDisabled(trackType, false)
                            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                        }
                        exo.player.trackSelectionParameters = builder.build()
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
fun TrackItemCard(title: String, subtitle: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        colors   = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFFE50914),
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        shape    = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale    = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(text = title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
                if (subtitle.isNotEmpty()) Text(text = subtitle, color = Color.White.copy(0.7f), fontSize = 11.sp, textAlign = TextAlign.Start)
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun getFlagEmoji(lang: String) = when (lang.lowercase().take(3)) {
    "heb", "he" -> "\uD83C\uDDEE\uD83C\uDDF1"
    "eng", "en" -> "\uD83C\uDDFA\uD83C\uDDF8"
    "ara", "ar" -> "\uD83C\uDDF8\uD83C\uDDE6"
    "rus", "ru" -> "\uD83C\uDDF7\uD83C\uDDFA"
    "fre", "fr" -> "\uD83C\uDDEB\uD83C\uDDF7"
    "spa", "es" -> "\uD83C\uDDEA\uD83C\uDDF8"
    "ger", "de" -> "\uD83C\uDDE9\uD83C\uDDEA"
    else        -> "\uD83C\uDF10"
}

// ⚡ FIX: Reverted to standard Box rendering! This guarantees that LTR fills Left-to-Right
// and RTL automatically flips to fill Right-to-Left visually and mechanically.
@Composable
fun PlayerProgressControls(
    exoWrapper: ExoPlayerWrapper, isPlaying: Boolean, isRtl: Boolean,
    seekFR: FocusRequester = remember { FocusRequester() },
    onUpPressed: () -> Unit = {}, onDownPressed: () -> Unit = {}
) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration   by remember { mutableLongStateOf(1L) }
    var seekFocused     by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            currentPosition = exoWrapper.player.currentPosition
            videoDuration   = exoWrapper.player.duration.coerceAtLeast(1L)
            delay(500)
        }
    }
    LaunchedEffect(Unit) { runCatching { seekFR.requestFocus() } }

    val progress  = (currentPosition.toFloat() / videoDuration.toFloat()).coerceIn(0f, 1f)
    val barHeight by animateDpAsState(if (seekFocused) 10.dp else 5.dp, label = "bh")
    val thumbSize by animateDpAsState(if (seekFocused) 20.dp else 0.dp, label = "ts")

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(currentPosition), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp), textAlign = TextAlign.Start)
        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(32.dp)
                .focusRequester(seekFR).focusable()
                .onFocusChanged { seekFocused = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val d = if (isRtl) 10_000L else -10_000L
                            val p = (exoWrapper.player.currentPosition + d).coerceIn(0L, videoDuration)
                            exoWrapper.seekTo(p); currentPosition = p; true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val d = if (isRtl) -10_000L else 10_000L
                            val p = (exoWrapper.player.currentPosition + d).coerceIn(0L, videoDuration)
                            exoWrapper.seekTo(p); currentPosition = p; true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (isPlaying) exoWrapper.pause() else exoWrapper.play(); true }
                        KeyEvent.KEYCODE_DPAD_UP   -> { onUpPressed();   true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { onDownPressed(); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track Background
            Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(Color.White.copy(if (seekFocused) 0.35f else 0.25f)))
            // Fill
            Box(Modifier.fillMaxWidth(progress).height(barHeight).clip(RoundedCornerShape(50)).background(if (seekFocused) Color(0xFFE50914) else Color(0xFFE50914).copy(0.8f)))
            // Thumb
            if (thumbSize > 0.dp) {
                Box(Modifier.fillMaxWidth(progress).wrapContentWidth(Alignment.End)) {
                    Box(Modifier.size(thumbSize).clip(CircleShape).background(Color.White).shadow(4.dp, CircleShape))
                }
            }
        }
        Text(formatTime(videoDuration - currentPosition), color = DIM, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
    }
}

fun formatTime(ms: Long): String {
    val s   = ms / 1000
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}