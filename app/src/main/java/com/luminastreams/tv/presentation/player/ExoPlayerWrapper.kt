@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("UNUSED", "UNUSED_VARIABLE")
// ── Streaming Stability Overhaul v2 ──
// Fixes: stall watchdog, aggressive retry, connection pool flush, buffer tuning

package com.luminastreams.tv.presentation.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.luminastreams.tv.core.TrustedHttpClient
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val settingsSnap = (appContext as com.luminastreams.tv.core.LuminaApp).settingsRepository.current()

    private val audioPassthrough  = settingsSnap.audioPassthrough || DeviceProfile.isLg || DeviceProfile.isSony || DeviceProfile.isPhilips
    private val hwAcceleration    = settingsSnap.hwAcceleration
    private val audioLangPref     = settingsSnap.preferredAudioLang

    val useYellowSubtitles: Boolean = settingsSnap.yellowSubtitles
    val subtitleFontScale: Float = when (settingsSnap.subtitleFontScale) {
        "small"  -> 0.75f
        "large"  -> 1.30f
        "xlarge" -> 1.60f
        else     -> 1.00f
    }

    private val _contentFrameRate = MutableStateFlow(0f)
    val contentFrameRate: StateFlow<Float> = _contentFrameRate.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(0f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    private val _isDolbyVision = MutableStateFlow(false)
    val isDolbyVision: StateFlow<Boolean> = _isDolbyVision.asStateFlow()

    private val _isDolbyAtmos = MutableStateFlow(false)
    val isDolbyAtmos: StateFlow<Boolean> = _isDolbyAtmos.asStateFlow()

    private val preAllocateBuffer = settingsSnap.preAllocateBuffer

    private val loadControl: DefaultLoadControl = DefaultLoadControl.Builder()
        .setTargetBufferBytes(
            if (preAllocateBuffer) {
                when {
                    DeviceProfile.totalRamMb > 2500 -> 200 * 1024 * 1024 // 200MB for High-End
                    DeviceProfile.totalRamMb > 1500 -> 120 * 1024 * 1024 // 120MB for Mid-End
                    else -> 80 * 1024 * 1024 // 80MB for Low-End TVs
                }
            } else {
                80 * 1024 * 1024 // 80MB Default
            }
        )
        .setBufferDurationsMs(
            30_000,   // minBuffer: 30s
            60_000,   // maxBuffer: 60s
            2_000,    // bufferForPlayback: 2s
            4_000     // bufferAfterRebuffer: 4s
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val renderersFactory = CustomRenderersFactory(appContext).apply {
        // EXTENSION_RENDERER_MODE_ON enables software decoder extensions (FFmpeg for AC3/EAC3/DTS/TrueHD)
        // when hardware/MediaCodec decoders are unavailable (e.g., Windows Subsystem for Android).
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    val trackSelector = DefaultTrackSelector(appContext).apply {
        val builder = buildUponParameters()
            .setPreferredVideoMimeTypes(
                MimeTypes.VIDEO_H265, MimeTypes.VIDEO_DOLBY_VISION,
                MimeTypes.VIDEO_H264, MimeTypes.VIDEO_AV1
            )
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_DTS,
                MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_TRUEHD,
                MimeTypes.AUDIO_AAC
            )
            // TUNNELING is always disabled to prevent severe Android TV UI lag
            .setTunnelingEnabled(false)
            .setPreferredTextLanguages("iw", "heb", "he")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setAllowAudioMixedMimeTypeAdaptiveness(true)
            .setAllowAudioMixedSampleRateAdaptiveness(true)

        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("he", "heb", "iw", "he-IL")
            "en" -> builder.setPreferredAudioLanguages("en", "eng", "en-US", "en-GB", "en-CA")
            else -> builder
        }
        setParameters(params.build())
    }

    // ── OkHttp client for streaming — tuned for RD CDN reliability ──
    // readTimeout = 0 (infinite) is CRITICAL: when ExoPlayer's buffer is full it stops
    // calling read() for up to a minute. A finite readTimeout would kill a healthy stream.
    // Instead, we rely on the stall watchdog to detect truly dead connections.
    private val streamingClient = TrustedHttpClient.builder()
        .connectTimeout(
            (if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 20L else 15L),
            java.util.concurrent.TimeUnit.SECONDS
        )
        .readTimeout(0L, java.util.concurrent.TimeUnit.SECONDS)   // Infinite — ExoPlayer controls read pacing
        .writeTimeout(0L, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
        .retryOnConnectionFailure(true) // OkHttp-level retry on TCP reset / connection drop
        .addInterceptor { chain ->
            val req = chain.request()
            android.util.Log.d("LuminaNet", ">>> ${req.method} ${req.url} [Range: ${req.header("Range")}]")
            try {
                val res = chain.proceed(req)
                android.util.Log.d("LuminaNet", "<<< ${res.code} [Content-Length: ${res.header("Content-Length")}, Content-Range: ${res.header("Content-Range")}]")
                res
            } catch (e: Exception) {
                android.util.Log.e("LuminaNet", "❌ HTTP Error for ${req.url}: ${e.message}", e)
                throw e
            }
        }
        .build()

    private val httpDataSourceFactory = OkHttpDataSource.Factory(streamingClient)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

    private val dataSourceFactory  = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)
        .setSubtitleParserFactory(androidx.media3.extractor.text.DefaultSubtitleParserFactory())

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            if (audioPassthrough) runCatching {
                val cls  = Class.forName("androidx.media3.exoplayer.audio.AudioOffloadPreferences")
                val bldr = cls.getClasses().firstOrNull { it.simpleName == "Builder" } ?: return@runCatching
                val ob   = bldr.getDeclaredConstructor().newInstance()
                bldr.getMethod("setAudioOffloadMode", Int::class.java).invoke(ob, 1)
                val op   = bldr.getMethod("build").invoke(ob)
                exo.javaClass.getMethod("setAudioOffloadPreferences", cls).invoke(exo, op)
            }
        }

    // ── Auto-retry state for connection drops ──
    private var _autoRetryCount = 0
    private var _lastStreamUrl: String? = null
    private var _lastSubtitleUrl: String? = null
    private companion object {
        const val MAX_AUTO_RETRIES = 10 // RD link is valid — retry aggressively, user should NEVER see an error
    }

    // ── Stall detection watchdog ──
    // Catches edge case: TCP connection alive but server stopped sending bytes.
    // Only counts stall time when player.isPlaying — initial buffering is NOT a stall.
    private var _stallWatchdogHandler: android.os.Handler? = null
    private var _lastBufferedPosition = 0L
    private var _stallCheckCount = 0
    private val STALL_CHECK_INTERVAL_MS = 5_000L  // Check every 5s
    private val STALL_MAX_CHECKS = 4              // After 4 checks (20s) with no progress → force retry

    private val _isPlaying       = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError     = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _currentTracks   = MutableStateFlow(Tracks.EMPTY)
    val currentTracks: StateFlow<Tracks> = _currentTracks.asStateFlow()

    private val _subtitleApplied = MutableStateFlow(false)
    val subtitleApplied: StateFlow<Boolean> = _subtitleApplied.asStateFlow()

    private val _currentCues     = MutableStateFlow<List<Cue>>(emptyList())
    val currentCues: StateFlow<List<Cue>> = _currentCues.asStateFlow()

    private val _playbackState   = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _hasRenderedFirstFrame = MutableStateFlow(false)
    val hasRenderedFirstFrame: StateFlow<Boolean> = _hasRenderedFirstFrame.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { _isPlaying.value = p }

            override fun onCues(cueGroup: CueGroup) {
                _currentCues.value = cueGroup.cues
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks

                val fps = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .firstOrNull { it.frameRate > 0f }?.frameRate
                if (fps != null && fps > 0f) _contentFrameRate.value = fps

                val hasDv = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any { it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION }
                _isDolbyVision.value = hasDv

                val hasAtmos = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any {
                        it.sampleMimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                                it.codecs?.contains("joc", ignoreCase = true) == true ||
                                it.label?.contains("atmos", ignoreCase = true) == true ||
                                it.id?.contains("atmos", ignoreCase = true) == true
                    }
                _isDolbyAtmos.value = hasAtmos
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val pixelAspect = if (videoSize.pixelWidthHeightRatio > 0f)
                        videoSize.pixelWidthHeightRatio else 1f
                    _videoAspectRatio.value =
                        (videoSize.width.toFloat() * pixelAspect) / videoSize.height.toFloat()
                }
                if (_contentFrameRate.value <= 0f) {
                    val fps = player.currentTracks.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                        .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                        .firstOrNull { it.frameRate > 0f }?.frameRate
                    if (fps != null && fps > 0f) _contentFrameRate.value = fps
                }
            }

            override fun onRenderedFirstFrame() {
                android.util.Log.d("LuminaPlayer", "🎬 onRenderedFirstFrame: First frame rendered successfully!")
                _hasRenderedFirstFrame.value = true
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("LuminaPlayer", "🚨 onPlayerError [${error.errorCodeName} / ${error.errorCode}]: ${error.message}", error)
                error.cause?.let {
                    android.util.Log.e("LuminaPlayer", "🚨 Caused by [${it.javaClass.name}]: ${it.message}", it)
                }

                val isRecoverable = error.errorCode in listOf(
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                )

                if (isRecoverable && _autoRetryCount < MAX_AUTO_RETRIES) {
                    // Silent reconnect — user should never see this happen
                    _autoRetryCount++
                    val resumePos = player.currentPosition.coerceAtLeast(0L)
                    val wasPlayWhenReady = player.playWhenReady
                    val savedSubUrl = _lastSubtitleUrl
                    val streamUrl = _lastStreamUrl ?: return
                    android.util.Log.d("LuminaPlayer", "🔄 Auto-reconnecting attempt $_autoRetryCount/$MAX_AUTO_RETRIES at pos=$resumePos")
                    player.stop()
                    player.clearMediaItems()
                    _playerError.value = null
                    // Kill stale sockets so OkHttp opens a fresh connection with correct Range header
                    flushStaleConnections()
                    // Fast retry: 1.5s base, max 4s — RD CDN is reliable, just reconnect quickly
                    val retryDelayMs = 1_500L + ((_autoRetryCount - 1).coerceAtMost(3) * 800L)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        retryStream(streamUrl, resumePos, wasPlayWhenReady, savedSubUrl)
                    }, retryDelayMs)
                    return
                }

                _playerError.value = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED           -> "Decoder init failed."
                    PlaybackException.ERROR_CODE_DECODING_FAILED               -> "Decoding error."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED  -> "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_TIMEOUT                       -> "Connection timed out."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Stream corrupted or invalid. Try another link."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition(); player.prepare(); return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _isPlaying.value = false
            }

            override fun onPlaybackStateChanged(s: Int) {
                _playbackState.value = s
                android.util.Log.d("LuminaPlayer", "State changed -> $s (1=IDLE, 2=BUFFERING, 3=READY, 4=ENDED), playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}")
                if (s == Player.STATE_ENDED) _isPlaying.value = false
                // Reset auto-retry counter once playback is healthy again
                if (s == Player.STATE_READY && _autoRetryCount > 0) _autoRetryCount = 0

                // ── Stall watchdog: detect STATE_BUFFERING hangs ──
                if (s == Player.STATE_BUFFERING) {
                    startStallWatchdog()
                } else {
                    stopStallWatchdog()
                }
            }
        })
    }

    fun prepareStream(videoUrl: String, startPositionMs: Long = C.TIME_UNSET) {
        _playerError.value           = null
        _subtitleApplied.value       = false
        _hasRenderedFirstFrame.value = false
        _currentCues.value           = emptyList()
        _contentFrameRate.value = 0f
        _videoAspectRatio.value = 0f
        _isDolbyVision.value    = false
        _isDolbyAtmos.value     = false
        _lastStreamUrl          = videoUrl
        _autoRetryCount         = 0 // Reset retry counter for fresh streams
        stopStallWatchdog()
        try {
            if (startPositionMs > 0 && startPositionMs != C.TIME_UNSET) {
                player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build(), startPositionMs)
            } else {
                player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            }
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare: ${e.message}"
        }
    }

    /**
     * Internal retry after a recoverable CDN error. Unlike prepareStream(), this does NOT
     * reset subtitle/DV/Atmos state, preserves the play intent, and re-applies subtitles
     * if they were active before the error.
     */
    private fun retryStream(videoUrl: String, resumePos: Long, wasPlayWhenReady: Boolean, savedSubUrl: String?) {
        _lastStreamUrl = videoUrl
        stopStallWatchdog() // Stop any active stall watchdog before retry
        try {
            val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl.toUri())

            // Re-attach subtitle if one was active before the error
            if (!savedSubUrl.isNullOrBlank()) {
                val mimeType = if (savedSubUrl.contains(".vtt", ignoreCase = true))
                    MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(savedSubUrl.toUri())
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }

            player.setMediaItem(mediaItemBuilder.build(), resumePos)
            player.prepare()
            player.playWhenReady = wasPlayWhenReady
        } catch (e: Exception) {
            _playerError.value = "Failed to retry: ${e.message}"
        }
    }

    fun switchAudioTrack(group: Tracks.Group, trackIndex: Int) {
        val isLive = player.isCurrentMediaItemLive
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
            .build()
        if (isLive) player.seekToDefaultPosition()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun switchSubtitleTrack(group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
            .build()
    }

    // Replaced manual ticker with Native ExoPlayer Subtitle sideloading
    fun applySubtitle(subtitleUrl: String, isVtt: Boolean = false, maxRetries: Int = 2) {
        val currentMediaItem = player.currentMediaItem ?: return
        val currentPosition = player.currentPosition

        // ⚡ FIX: Capture 'playWhenReady' instead of 'isPlaying'!
        // This remembers that we INTEND to play the video even if it is currently buffering.
        val wasPlayWhenReady = player.playWhenReady

        val mimeType = if (isVtt || subtitleUrl.contains(".vtt", ignoreCase = true))
            MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
            .setMimeType(mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .build()

        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .build()

        player.setMediaItem(newMediaItem, currentPosition)
        player.prepare()

        // ⚡ FIX: Restore the correct intent to play
        player.playWhenReady = wasPlayWhenReady

        // Track the subtitle URL so auto-retry can restore it after CDN reconnection
        _lastSubtitleUrl = subtitleUrl
        _subtitleApplied.value = true
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }
    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }
    fun clearError() { _playerError.value = null }

    /** Stop playback and release the HTTP connection to the source URL */
    fun stopPlayback() {
        stopStallWatchdog()
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {}
        _isPlaying.value = false
    }

    private var isReleased = false

    fun release() {
        if (isReleased) return
        isReleased = true
        stopStallWatchdog()
        try {
            player.stop()
            player.clearMediaItems()
            player.release()
        } catch (_: Exception) {}
        // Flush the streaming connection pool to free resources
        try { streamingClient.connectionPool.evictAll() } catch (_: Exception) {}
    }

    // ── Connection Pool Management ──

    /** Force-close all idle HTTP connections to prevent reusing dead RD CDN sockets after a drop */
    private fun flushStaleConnections() {
        try { streamingClient.connectionPool.evictAll() } catch (_: Exception) {}
    }

    // ── Stall Watchdog ──
    // Detects when ExoPlayer is stuck in STATE_BUFFERING during active playback with no new data arriving.
    // Catches the silent-hang case where the CDN drops the connection mid-stream.
    // Note: Never kills the initial startup buffering phase.
    private fun startStallWatchdog() {
        stopStallWatchdog()
        _lastBufferedPosition = player.bufferedPosition
        _stallCheckCount = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        _stallWatchdogHandler = handler
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (_stallWatchdogHandler == null) return
                val currentBuffered = player.bufferedPosition
                if (player.playbackState != Player.STATE_BUFFERING) {
                    stopStallWatchdog()
                    return
                }
                // Only count stall time if the player was already playing —
                // never kill the initial buffering phase on slow starts
                val wasAlreadyPlaying = player.currentPosition > 5_000L
                if (currentBuffered <= _lastBufferedPosition && wasAlreadyPlaying) {
                    _stallCheckCount++
                    if (_stallCheckCount >= STALL_MAX_CHECKS && _autoRetryCount < MAX_AUTO_RETRIES) {
                        android.util.Log.w("LuminaPlayer", "⚠️ Playback stall detected during active playback. Triggering reconnect...")
                        triggerStallRetry()
                        return
                    }
                } else if (currentBuffered > _lastBufferedPosition) {
                    _stallCheckCount = 0
                    _lastBufferedPosition = currentBuffered
                }
                handler.postDelayed(this, STALL_CHECK_INTERVAL_MS)
            }
        }, STALL_CHECK_INTERVAL_MS)
    }

    private fun triggerStallRetry() {
        _autoRetryCount++
        val resumePos = player.currentPosition.coerceAtLeast(0L)
        val wasPlayWhenReady = player.playWhenReady
        val savedSubUrl = _lastSubtitleUrl
        val streamUrl = _lastStreamUrl ?: return
        player.stop()
        player.clearMediaItems()
        _playerError.value = null
        flushStaleConnections()
        stopStallWatchdog()
        val retryDelayMs = 1_500L + ((_autoRetryCount - 1).coerceAtMost(3) * 800L)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            retryStream(streamUrl, resumePos, wasPlayWhenReady, savedSubUrl)
        }, retryDelayMs)
    }

    private fun stopStallWatchdog() {
        _stallWatchdogHandler?.removeCallbacksAndMessages(null)
        _stallWatchdogHandler = null
        _stallCheckCount = 0
    }
}