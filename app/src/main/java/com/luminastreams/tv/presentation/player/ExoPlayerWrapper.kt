@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
            if (hwAcceleration) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
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
            .setTunnelingEnabled(true)

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
            .build()
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)

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

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _currentTracks = MutableStateFlow(Tracks.EMPTY)
    val currentTracks: StateFlow<Tracks> = _currentTracks.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Decoder init failed — try a different source."
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> "Decoding error — try a different source."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection timed out."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                        return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _playerError.value = message
                _isPlaying.value = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) _isPlaying.value = false
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        try {
            _playerError.value = null
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(videoUrl)).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare stream: ${e.message}"
        }
    }

    fun applySubtitle(subtitleUrl: String, lang: String = "heb", isVtt: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentUrl = subtitleUrl
                var redirectCount = 0
                var connected = false
                var rawBytes: ByteArray? = null

                if (subtitleUrl.startsWith("file://")) {
                    val sourceFile = File(subtitleUrl.replace("file://", ""))
                    if (sourceFile.exists()) rawBytes = sourceFile.readBytes()
                    connected = true
                } else {
                    while (!connected && redirectCount < 5) {
                        val connection = URL(currentUrl).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        connection.setRequestProperty("Accept-Encoding", "gzip")
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        connection.instanceFollowRedirects = false

                        val status = connection.responseCode
                        if (status in 200..299) {
                            val encoding = connection.contentEncoding
                            rawBytes = if (encoding != null && encoding.contains("gzip")) {
                                java.util.zip.GZIPInputStream(connection.inputStream).readBytes()
                            } else {
                                connection.inputStream.readBytes()
                            }
                            connected = true
                        } else if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 303) {
                            currentUrl = connection.getHeaderField("Location") ?: break
                            redirectCount++
                        } else {
                            break
                        }
                    }
                }

                if (rawBytes == null || rawBytes.isEmpty()) return@launch

                var decodedBytes: ByteArray = rawBytes

                if (decodedBytes.size >= 4 && decodedBytes[0] == 0x50.toByte() && decodedBytes[1] == 0x4B.toByte()) {
                    try {
                        val unzippedBytes = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(decodedBytes)).use { zis ->
                            zis.nextEntry
                            zis.readBytes()
                        }
                        decodedBytes = unzippedBytes
                    } catch (e: Exception) {}
                }
                else if (decodedBytes.size >= 2 && decodedBytes[0] == 0x1F.toByte() && decodedBytes[1] == 0x8B.toByte()) {
                    try {
                        val unzippedBytes = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(decodedBytes)).use { zis ->
                            zis.readBytes()
                        }
                        decodedBytes = unzippedBytes
                    } catch (e: Exception) {}
                }

                var text = ""
                try {
                    text = String(decodedBytes, Charsets.UTF_8)
                    if (!text.contains("-->") && !text.contains("WEBVTT")) {
                        text = String(decodedBytes, java.nio.charset.Charset.forName("windows-1255"))
                    }
                } catch (e: Exception) {
                    text = String(decodedBytes, java.nio.charset.Charset.forName("windows-1255"))
                }

                if (text.startsWith("\uFEFF")) {
                    text = text.substring(1)
                }

                // הגנה קריטית מהדבקה של דפי שגיאה (HTML) אל תוך הנגן
                val lowerText = text.trimStart().lowercase()
                if (lowerText.startsWith("<!doctype") || lowerText.startsWith("<html")) {
                    return@launch
                }

                val isActualVtt = text.trimStart().startsWith("WEBVTT", ignoreCase = true)
                val mime = if (isActualVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                val ext = if (isActualVtt) ".vtt" else ".srt"

                val subFile = File(appContext.cacheDir, "external_sub$ext")
                subFile.writeText(text, Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    val current = player.currentMediaItem ?: return@withContext
                    val injectedLang = "he-IL" // שימוש בקוד שפה שונה לחלוטין מזה של הטורנט המובנה
                    val localUri = Uri.fromFile(subFile)

                    val conf = MediaItem.SubtitleConfiguration.Builder(localUri)
                        .setMimeType(mime)
                        .setLanguage(injectedLang)
                        .setId("external_sub_network")
                        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                        .build()

                    val pos = player.currentPosition

                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        current.buildUpon().setSubtitleConfigurations(listOf(conf)).build()
                    )

                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(injectedLang)
                        .build()

                    player.seekTo(pos)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        try { player.release() } catch (_: Exception) {}
    }
}