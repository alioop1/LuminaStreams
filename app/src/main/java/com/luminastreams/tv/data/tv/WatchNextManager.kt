package com.luminastreams.tv.data.tv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.luminastreams.tv.domain.model.Movie

class WatchNextManager(private val context: Context) {

    private val TAG = "WatchNextManager"

    fun updateWatchNext(id: String, title: String, overview: String, posterUrl: String?, progressMs: Long, durationMs: Long) {
        try {
            // Android TV only allows writing to Watch Next if the app has the right permissions and is running on TV.
            if (!isTvDevice()) return

            val contentId = id
            val existingProgram = findProgramById(contentId)

            // If progress is very small (less than 1 minute) or very close to the end (95%), remove it.
            val progressPercent = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()) else 0f
            if (progressPercent < 0.05f || progressPercent > 0.95f) {
                if (existingProgram != null) {
                    removeProgram(existingProgram.id)
                }
                return
            }

            // Create or update the Watch Next Program
            val builder = if (existingProgram != null) {
                WatchNextProgram.Builder(existingProgram)
            } else {
                WatchNextProgram.Builder()
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            }

            builder.apply {
                setTitle(title)
                setDescription(overview)
                setPosterArtUri(Uri.parse(posterUrl ?: ""))
                setIntentUri(Uri.parse("luminastreams://details/${id}"))
                setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                setDurationMillis(durationMs.toInt())
                setLastPlaybackPositionMillis(progressMs.toInt())
            }

            val program = builder.build()

            if (existingProgram != null) {
                val updated = context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingProgram.id),
                    program.toContentValues(),
                    null,
                    null
                )
                Log.d(TAG, "Updated existing Watch Next program: $updated")
            } else {
                val uri = context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
                Log.d(TAG, "Inserted new Watch Next program: $uri")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Watch Next", e)
        }
    }

    private fun findProgramById(contentId: String): WatchNextProgram? {
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    val program = WatchNextProgram.fromCursor(it)
                    if (program.intentUri?.toString()?.endsWith(contentId) == true) {
                        return program
                    }
                } while (it.moveToNext())
            }
        }
        return null
    }

    private fun removeProgram(programId: Long) {
        val deleted = context.contentResolver.delete(
            TvContractCompat.buildWatchNextProgramUri(programId),
            null,
            null
        )
        Log.d(TAG, "Deleted Watch Next program: $deleted")
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}
