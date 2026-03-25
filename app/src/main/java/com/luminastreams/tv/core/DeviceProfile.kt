package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20

// ═══════════════════════════════════════════════════════════════════
// DeviceProfile — detects hardware tier at runtime, drives anim config.
// forceLowTier: override for Lite UI Mode set via SettingsViewModel.
// ═══════════════════════════════════════════════════════════════════

object DeviceProfile {

    enum class Tier { LOW, MID, HIGH }

    data class AnimConfig(
        val rowFadeDuration: Int,
        val backdropDuration: Int,
        val heroFadeDuration: Int,
        val crossfadeDuration: Int,
        val enableRowFade: Boolean,
        val enableParallax: Boolean,
        val lazyBeyondBounds: Int
    )

    lateinit var tier: Tier
        private set

    lateinit var animConfig: AnimConfig
        private set

    lateinit var gpuRenderer: String
        private set

    var totalRamMb: Int = 0
        private set

    // ✅ NEW: set to true by SettingsViewModel when Lite UI mode is toggled ON
    // Immediately forces LOW-tier animation config for the current session.
    // Persisted across cold-starts via SharedPreferences read in LuminaApp.
    var forceLowTier: Boolean = false
        set(value) {
            field = value
            animConfig = buildConfig(if (value) Tier.LOW else tier)
        }

    fun init(context: Context) {
        gpuRenderer  = readGpuRenderer()
        totalRamMb   = readTotalRam(context)
        tier         = detectTier(gpuRenderer, totalRamMb)
        animConfig   = buildConfig(tier)
    }

    private fun readGpuRenderer(): String = try {
        GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
    } catch (_: Exception) { "unknown" }

    private fun readTotalRam(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / 1024 / 1024).toInt()
    }

    private fun detectTier(gpu: String, ramMb: Int): Tier {
        val g = gpu.lowercase()
        return when {
            g.contains("tegra")                               -> Tier.HIGH
            g.contains("adreno 6") || g.contains("adreno 7") -> Tier.HIGH
            g.contains("mali-g7") || g.contains("mali-g9")   -> Tier.HIGH
            g.contains("apple")                               -> Tier.HIGH
            ramMb >= 3000 && !g.contains("mali-g3")          -> Tier.HIGH
            g.contains("adreno 5")                            -> Tier.MID
            g.contains("mali-g5") || g.contains("mali-g6")   -> Tier.MID
            g.contains("powervr gm")                          -> Tier.MID
            ramMb in 2000..2999                               -> Tier.MID
            else                                              -> Tier.LOW
        }
    }

    private fun buildConfig(t: Tier): AnimConfig = when (t) {
        Tier.HIGH -> AnimConfig(
            rowFadeDuration   = 200,
            backdropDuration  = 500,
            heroFadeDuration  = 350,
            crossfadeDuration = 200,
            enableRowFade     = true,
            enableParallax    = true,
            lazyBeyondBounds  = 2
        )
        Tier.MID -> AnimConfig(
            rowFadeDuration   = 120,
            backdropDuration  = 300,
            heroFadeDuration  = 200,
            crossfadeDuration = 100,
            enableRowFade     = true,
            enableParallax    = false,
            lazyBeyondBounds  = 1
        )
        Tier.LOW -> AnimConfig(
            rowFadeDuration   = 0,
            backdropDuration  = 150,
            heroFadeDuration  = 0,
            crossfadeDuration = 0,
            enableRowFade     = false,
            enableParallax    = false,
            lazyBeyondBounds  = 0
        )
    }

    fun debugInfo(): String =
        "Tier=${if (forceLowTier) "LOW (forced)" else tier.name} | " +
                "GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
                "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}