package com.luminastreams.tv.presentation.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current subtitle delay (ms; positive = subtitles shown later) for the
 * playing item, and persists it per title so re-opening the same show keeps your sync.
 */
object SubtitleOffsetManager {

    private const val PREFS = "subtitle_sync"
    const val MAX_OFFSET_MS = 15_000L

    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    /** Key of the item currently playing, so [persist] knows where to write. */
    private var activeKey: String? = null

    fun getOffset(): Long = _offsetMs.value

    fun setOffset(ms: Long) {
        _offsetMs.value = ms.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
    }

    fun reset() {
        _offsetMs.value = 0L
    }

    /**
     * Load the saved offset for [key] (per title). Call once when a media item opens.
     * A blank key disables persistence for this session but keeps sync working in-memory.
     */
    fun restoreFor(context: Context, key: String) {
        activeKey = key.trim().takeIf { it.isNotEmpty() }
        _offsetMs.value = activeKey?.let {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(it, 0L)
        } ?: 0L
    }

    /** Persist the current offset for the active key (no-op if there is none). */
    fun persist(context: Context) {
        val k = activeKey ?: return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(k, _offsetMs.value).apply()
    }
}
