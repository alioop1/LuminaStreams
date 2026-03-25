@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs      = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    private val audioPassthrough  = prefs.getBoolean("audio_passthrough", false)
    private val hwAcceleration    = prefs.getBoolean("hw_accel", true)
    private val preAllocateBuffer = prefs.getBoolean("pre_buffer", false)
    private val audioLangPref     = prefs.getString("preferred_audio_lang", "original") ?: "original"

    val useYellowSubtitles: Boolean = prefs.getBoolean("yellow_subs", false)
    val subtitleFontScale: Float = when (prefs.getString("subtitle_font_scale", "medium")) {
        "small"  -> 0.75f
        "large"  -> 1.30f
        "xlarge" -> 1.60f
        else     -> 1.00f
    }

    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(
            when {
                !hwAcceleration -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.isXiaomi || DeviceProfile.isMeCool || DeviceProfile.isAmlogic ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.tier == DeviceProfile.Tier.HIGH ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            }
        )
        setEnableDecoderFallback(true)
    }

    val trackSelector = DefaultTrackSelector(appContext).apply {
        val builder = buildUponParameters()
            .setPreferredVideoMimeTypes(
                MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265,
                MimeTypes.VIDEO_H264, MimeTypes.VIDEO_AV1
            )
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AAC
            )
            .setTunnelingEnabled(
                DeviceProfile.tier == DeviceProfile.Tier.HIGH &&
                !DeviceProfile.isXiaomi && !DeviceProfile.isMeCool && !DeviceProfile.isAmlogic
            )
            .setPreferredTextLanguages("iw", "heb", "he")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("heb", "iw", "he")
            "en" -> builder.setPreferredAudioLanguages("eng", "en")
            else -> builder
        }
        setParameters(params)
    }

    private val loadControl = if (preAllocateBuffer) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 5_000, 10_000)
            .setTargetBufferBytes(64 * 1024 * 1024).build()
    } else {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .setTargetBufferBytes(20 * 1024 * 1024).build()
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)
    val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
    val mediaSourceFactory: DefaultMediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)
        .setSubtitleParserFactory(androidx.media3.extractor.text.DefaultSubtitleParserFactory())

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
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

    // ─── State ────────────────────────────────────────────────────────
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

    // שומר את ה-videoUrl לצורך MergingMediaSource
    private var currentVideoUrl: String? = null

    init {
        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(p: Boolean) {
                _isPlaying.value = p
            }

            override fun onCues(cueGroup: CueGroup) {
                _currentCues.value = cueGroup.cues
            }

            override fun onPlayerError(error: PlaybackException) {
                _playerError.value = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED           -> "Decoder init failed."
                    PlaybackException.ERROR_CODE_DECODING_FAILED               -> "Decoding error."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED  -> "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection timed out."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition(); player.prepare(); return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _isPlaying.value = false
            }

            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_ENDED) _isPlaying.value = false
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks

                val textGroup = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .firstOrNull { grp ->
                        (0 until grp.length).any { i ->
                            grp.mediaTrackGroup.getFormat(i).id == "ext_sub"
                        }
                    } ?: return

                val alreadySelected = (0 until textGroup.length).any { i ->
                    textGroup.mediaTrackGroup.getFormat(i).id == "ext_sub" &&
                    textGroup.isTrackSelected(i)
                }
                if (alreadySelected) return

                val idx = (0 until textGroup.length).firstOrNull { i ->
                    textGroup.mediaTrackGroup.getFormat(i).id == "ext_sub"
                } ?: 0

                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(textGroup.mediaTrackGroup, idx))
                    .build()
                _subtitleApplied.value = true
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        currentVideoUrl        = videoUrl
        _playerError.value     = null
        _subtitleApplied.value = false
        _currentCues.value     = emptyList()
        try {
            player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare: ${e.message}"
        }
    }

    fun applySubtitle(
        subtitleUrl: String,
        lang: String    = "heb",
        isVtt: Boolean  = false,
        maxRetries: Int = 2
    ) {
        if (subtitleUrl.startsWith("file://")) {
            val f = File(Uri.parse(subtitleUrl).path!!)
            injectSubtitle(f, subtitleUrl.endsWith(".vtt", ignoreCase = true))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            var lastErr: Exception? = null
            repeat(maxRetries + 1) { attempt ->
                try {
                    val conn = URL(subtitleUrl).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 12_000
                    conn.readTimeout    = 12_000
                    if (conn.responseCode in 200..299) {
                        val bytes = conn.inputStream.readBytes()
                        val ext   = if (isVtt || subtitleUrl.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
                        val file  = File(appContext.cacheDir, "lumina_sub.$ext")
                        file.writeBytes(bytes)
                        withContext(Dispatchers.Main) { injectSubtitle(file, ext == "vtt") }
                        return@launch
                    }
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < maxRetries) delay(1_500L * (attempt + 1))
                }
            }
            lastErr?.printStackTrace()
        }
    }

    // ─── injectSubtitle ─────────────────────────────────────────────────────
    //
    // משתמשת ב-MergingMediaSource — הגישה היחידה שבאמת מפעילה onCues:
    //
    //  videoSource  =  אותו video URL בדיוקה (ExoPlayer מזהה URI שווה -> לא re-fetch)
    //  subSource    =  SingleSampleMediaSource מה-cache file המקומי
    //  setMediaSource(merged, resetPosition=false)
    //    └ ExoPlayer עוצר / מתחיל מחדש את הנגינה במיקום הנכון
    //    └ ה-video URL זהה -> הוא עושה reuse ל-segments שכבר הורדו
    //    └ ה-subtitle file הוא מקומי -> לא צריך אינטרנט
    //    └ onTracksChanged מופעל -> נבחר track -> onCues מתחיל
    //
    //  על error 1004: זה יקרה אם ה-video URL הוא HLS token-based שפג
    //  תוקפו. במקרה כזה ExoPlayer יפעיל שגיאה, אבל הנגינה הקודמת
    //  תמשיך מה-buffer הקיים. חוץ מזה אין פתרון ב-Media3 API שעובד
    //  באמת לכתוביות חיצוניות בלי לאפס את ה-buffer.
    private fun injectSubtitle(subFile: File, isVtt: Boolean) {
        val videoUrl   = currentVideoUrl ?: return
        val mime       = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        val savedPos   = player.currentPosition
        val wasPlaying = player.isPlaying

        _subtitleApplied.value = false
        _currentCues.value     = emptyList()

        try {
            // video source — אותו URI בדיוקה
            val videoSource = mediaSourceFactory.createMediaSource(
                MediaItem.Builder().setUri(videoUrl.toUri()).build()
            )

            // subtitle source — מקומי, לא צריך רשת
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                .setMimeType(mime)
                .setLanguage("iw")
                .setId("ext_sub")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val subSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subConfig, C.TIME_UNSET)

            val merged = MergingMediaSource(videoSource, subSource)

            // ✅ setMediaSource בלי prepare() נוסף — ExoPlayer עושה
            //    prepare פנימי אוטומטית כשהוא רואה שה-source השתנה
            player.playWhenReady = wasPlaying
            player.setMediaSource(merged, savedPos)
            player.prepare()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }
    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }
    fun clearError() { _playerError.value = null }
    fun release() {
        _currentCues.value = emptyList()
        try { player.release() } catch (_: Exception) {}
    }
}
