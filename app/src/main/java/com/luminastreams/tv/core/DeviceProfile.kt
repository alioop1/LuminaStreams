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

    // ✅ Manufacturer flags — used by ExoPlayerWrapper for device-specific workarounds
    val isXiaomi: Boolean
        get() = android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    val isMeCool: Boolean
        get() = android.os.Build.MANUFACTURER.equals("MeCool", ignoreCase = true) ||
                android.os.Build.MODEL.startsWith("KM", ignoreCase = true)

    val isAmlogic: Boolean
        get() = android.os.Build.HARDWARE.contains("amlogic", ignoreCase = true) ||
                android.os.Build.HARDWARE.startsWith("meson", ignoreCase = true)

    // ✅ set to true by SettingsViewModel when Lite UI mode is toggled ON
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
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()

        return when {
            // ── HIGH-end GPUs ─────────────────────────────────────────────
            g.contains("tegra")                               -> Tier.HIGH
            g.contains("adreno 6") || g.contains("adreno 7") -> Tier.HIGH
            g.contains("mali-g7") || g.contains("mali-g9")   -> Tier.HIGH
            g.contains("apple")                               -> Tier.HIGH
            ramMb >= 3000 && !g.contains("mali-g3")          -> Tier.HIGH

            // ── MID-tier GPUs ─────────────────────────────────────────────
            g.contains("adreno 5")                            -> Tier.MID
            g.contains("mali-g5") || g.contains("mali-g6")   -> Tier.MID
            g.contains("powervr gm")                          -> Tier.MID

            // ── MeCool / Amlogic — S905X3/S905X4/S922X etc. ──────────────
            // Amlogic GPUs report as "Mali-G31" or "Vivante GC" variants.
            // KM6/KM7 (S905X4/S922X) have enough power for MID animations.
            manufacturer == "mecool" || hardware.contains("amlogic") ||
            hardware.startsWith("meson") -> when {
                ramMb >= 4000 -> Tier.HIGH  // KM7 / S922X
                ramMb >= 2000 -> Tier.MID   // KM6 / S905X4
                else          -> Tier.LOW   // KM2 / S905X2
            }

            // ── Xiaomi TV Stick / Mi Box ──────────────────────────────────
            // Mi Box S (S905X), Mi TV Stick 4K (S905Y4), Xiaomi TV Box S 2nd Gen
            manufacturer == "xiaomi" -> when {
                ramMb >= 3000 -> Tier.HIGH
                ramMb >= 2000 -> Tier.MID
                else          -> Tier.LOW
            }

            // ── Vivante GPU (older Amlogic S905/S912) ─────────────────────
            g.contains("vivante") || g.contains("gc1000") || g.contains("gc7000") -> Tier.LOW

            // ── Generic 2GB fallback ──────────────────────────────────────
            ramMb in 2000..2999 && !g.contains("mali-g3") && !g.contains("adreno 3") -> Tier.MID
            ramMb in 2000..2999 -> Tier.LOW

            else -> Tier.LOW
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
        "Xiaomi=$isXiaomi | MeCool=$isMeCool | Amlogic=$isAmlogic | " +
        "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
