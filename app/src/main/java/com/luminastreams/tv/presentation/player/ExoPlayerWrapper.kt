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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val prefs = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    private val audioPassthrough  = prefs.getBoolean("audio_passthrough", false)
    private val hwAcceleration    = prefs.getBoolean("hw_accel", true)
    private val preAllocateBuffer = prefs.getBoolean("pre_buffer", false)
    private val audioLangPref     = prefs.getString("preferred_audio_lang", "original") ?: "original"

    val useYellowSubtitles: Boolean = prefs.getBoolean("yellow_subs", false)

    val subtitleFontScale: Float = when (prefs.getString("subtitle_font_scale", "medium")) {
        "small"   -> 0.75f
        "large"   -> 1.30f
        "xlarge"  -> 1.60f
        else      -> 1.00f
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
                MimeTypes.VIDEO_DOLBY_VISION,
                MimeTypes.VIDEO_H265,
                MimeTypes.VIDEO_H264,
                MimeTypes.VIDEO_AV1
            )
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3_JOC,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_AAC
            )
            .setTunnelingEnabled(
                DeviceProfile.tier == DeviceProfile.Tier.HIGH &&
                !DeviceProfile.isXiaomi &&
                !DeviceProfile.isMeCool &&
                !DeviceProfile.isAmlogic
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

    private val loadControl: DefaultLoadControl = if (preAllocateBuffer) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 5_000, 10_000)
            .setTargetBufferBytes(64 * 1024 * 1024)
            .build()
    } else {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .setTargetBufferBytes(20 * 1024 * 1024)
            .build()
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

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
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            if (audioPassthrough) {
                try {
                    val offloadPrefsClass = Class.forName("androidx.media3.exoplayer.audio.AudioOffloadPreferences")
                    val builderClass = offloadPrefsClass.getClasses().firstOrNull { it.simpleName == "Builder" }
                    if (builderClass != null) {
                        val offloadBuilder = builderClass.getDeclaredConstructor().newInstance()
                        val setMode = builderClass.getMethod("setAudioOffloadMode", Int::class.java)
                        setMode.invoke(offloadBuilder, 1)
                        val buildMethod = builderClass.getMethod("build")
                        val offloadPrefs = buildMethod.invoke(offloadBuilder)
                        val setPrefs = exo.javaClass.getMethod("setAudioOffloadPreferences", offloadPrefsClass)
                        setPrefs.invoke(exo, offloadPrefs)
                    }
                } catch (_: Exception) {}
            }
        }

    private val _isPlaying     = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError   = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _currentTracks = MutableStateFlow(Tracks.EMPTY)
    val currentTracks: StateFlow<Tracks> = _currentTracks.asStateFlow()

    // ✅ true כשהכתובית הוחלה בהצלחה (PlayerScreen מאזין לזה)
    private val _subtitleApplied = MutableStateFlow(false)
    val subtitleApplied: StateFlow<Boolean> = _subtitleApplied.asStateFlow()

    // ── pending subtitle track selection after replaceMediaItem ──────────────
    // שומר את ה-URI של הכתובית שממתינה לבחירה ב-onTracksChanged הבא
    private var pendingSubtitleUri: String? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED           -> "Decoder init failed — try a different source."
                    PlaybackException.ERROR_CODE_DECODING_FAILED               -> "Decoding error — try a different source."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED  -> "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection timed out."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                        return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _playerError.value = message
                _isPlaying.value   = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) _isPlaying.value = false
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks

                // ✅ הבאג הקריטי תוקן כאן:
                // אחרי replaceMediaItem, ExoPlayer מודיע onTracksChanged כשהטראקים
                // כבר נטענו. רק עכשיו אפשר לעשות override מדויק לטראק הכתובית.
                val uri = pendingSubtitleUri ?: return
                pendingSubtitleUri = null

                val textGroup = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .firstOrNull { group ->
                        // מחפשים את הטראק עם ה-URI שלנו
                        (0 until group.length).any { i ->
                            group.mediaTrackGroup.getFormat(i).id == "external_sub"
                        }
                    } ?: return

                // מוצאים את ה-index המדויק
                val trackIndex = (0 until textGroup.length).firstOrNull { i ->
                    textGroup.mediaTrackGroup.getFormat(i).id == "external_sub"
                } ?: 0

                // ✅ Override מפורש — זה בלבד מבטיח שהכתובית תוצג
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(textGroup.mediaTrackGroup, trackIndex)
                    )
                    .build()

                _subtitleApplied.value = true
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        try {
            _playerError.value     = null
            _subtitleApplied.value = false
            pendingSubtitleUri     = null
            player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare stream: ${e.message}"
        }
    }

    /**
     * מוריד כתובית מ-URL ומחיל אותה.
     * ✅ retry עד [maxRetries] פעמים כשהרשת נכשלת.
     * ✅ תומך .srt ו-.vtt.
     */
    fun applySubtitle(
        subtitleUrl: String,
        lang: String = "heb",
        isVtt: Boolean = false,
        maxRetries: Int = 2
    ) {
        if (subtitleUrl.startsWith("file://")) {
            applyLocalSubtitle(subtitleUrl, lang, isVtt)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            var lastError: Exception? = null
            repeat(maxRetries + 1) { attempt ->
                try {
                    val connection = URL(subtitleUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = 12_000
                    connection.readTimeout    = 12_000
                    if (connection.responseCode in 200..299) {
                        val bytes   = connection.inputStream.readBytes()
                        val ext     = if (isVtt || subtitleUrl.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
                        val subFile = File(appContext.cacheDir, "lumina_sub.$ext")
                        subFile.writeBytes(bytes)
                        withContext(Dispatchers.Main) {
                            applyLocalSubtitle(Uri.fromFile(subFile).toString(), lang, ext == "vtt")
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxRetries) kotlinx.coroutines.delay(1_500L * (attempt + 1))
                }
            }
            lastError?.printStackTrace()
        }
    }

    private fun applyLocalSubtitle(localUriStr: String, lang: String, isVtt: Boolean) {
        val current = player.currentMediaItem ?: return
        val mime    = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP

        val savedPos   = player.currentPosition
        val wasPlaying = player.isPlaying

        val conf = MediaItem.SubtitleConfiguration.Builder(Uri.parse(localUriStr))
            .setMimeType(mime)
            .setLanguage("iw")
            // ✅ id ייחודי — משמש ב-onTracksChanged לזיהוי הטראק המדויק
            .setId("external_sub")
            // ✅ DEFAULT + FORCED — מבטיח שהטראק יהיה נגיש לבחירה
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()

        // ✅ שומרים את ה-URI לפני replaceMediaItem — onTracksChanged יקרא לו
        pendingSubtitleUri = localUriStr

        // replaceMediaItem יגרום ל-onTracksChanged שם נעשה את ה-override
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            current.buildUpon().setSubtitleConfigurations(listOf(conf)).build()
        )

        // ✅ seekTo + play אחרי replaceMediaItem — מבטיח שה-position נשמר
        player.seekTo(savedPos)
        if (wasPlaying) player.play()
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }

    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }

    fun clearError() { _playerError.value = null }

    fun release() {
        try { player.release() } catch (_: Exception) {}
    }
}
