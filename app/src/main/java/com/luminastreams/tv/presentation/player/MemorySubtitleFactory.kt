package com.luminastreams.tv.presentation.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource

object MemorySubtitleFactory {

    fun createMediaItemWithMemorySubtitle(
        videoUrl: String,
        subtitleBytes: ByteArray,
        languageCode: String = "he"
    ): MediaItem {
        // יצירת URI פיקטיבי שייורט על ידי ה-DataSource שלנו
        val memoryUri = Uri.parse("memory://subtitle.srt")

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(memoryUri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(languageCode)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        return MediaItem.Builder()
            .setUri(videoUrl)
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()
    }

    // מפעל נתונים שיודע להתמודד עם ה-URI הפיקטיבי ולהחזיר את מערך הבתים מה-RAM
    fun createDataSourceFactory(subtitleBytes: ByteArray): DataSource.Factory {
        return DataSource.Factory {
            ByteArrayDataSource(subtitleBytes)
        }
    }
}
