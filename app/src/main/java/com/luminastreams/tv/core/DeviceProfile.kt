package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build

// ╔═══════════════════════════════════════════════════════════════════
// DeviceProfile — detects hardware tier at runtime, drives anim config.
// forceLowTier: override for Lite UI Mode set via SettingsViewModel.
// ╚═══════════════════════════════════════════════════════════════════

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

    // ── Manufacturer / SoC flags (set during init, usable anywhere) ───────────────
    var isXiaomi:  Boolean = false; private set
    var isMeCool:  Boolean = false; private set
    var isAmlogic: Boolean = false; private set

    lateinit var tier: Tier
        private set

    lateinit var animConfig: AnimConfig
        private set

    lateinit var gpuRenderer: String
        private set

    var totalRamMb: Int = 0
        private set

    // ✅ Set to true by SettingsViewModel when Lite UI mode is toggled ON.
    var forceLowTier: Boolean = false
        set(value) {
            field = value
            animConfig = buildConfig(if (value) Tier.LOW else tier)
        }

    fun init(context: Context) {
        gpuRenderer = readGpuRenderer()
        totalRamMb  = readTotalRam(context)

        // ── Manufacturer detection ──────────────────────────────────────────
        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware     = Build.HARDWARE.lowercase()
        val model        = Build.MODEL.lowercase()

        isXiaomi  = manufacturer.contains("xiaomi")
        isMeCool  = manufacturer.contains("mecool") ||
                    model.contains("km")             ||
                    model.contains("mecool")
        isAmlogic = hardware.contains("amlogic") ||
                    hardware.contains("s905")     ||
                    hardware.contains("s922")     ||
                    hardware.contains("s912")     ||
                    hardware.contains("s905x")

        tier       = detectTier(gpuRenderer, totalRamMb)
        animConfig = buildConfig(tier)
    }

    private fun readGpuRenderer(): String = try {
        GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
    } catch (_: Exception) { "unknown" }

    private fun readTotalRam(context: Context): Int {
        val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / 1024 / 1024).toInt()
    }

    private fun detectTier(gpu: String, ramMb: Int): Tier {
        val g = gpu.lowercase()

        // ── Amlogic S905 / S912 — Vivante GPU → always LOW ───────────────────────
        if (g.contains("vivante")) return Tier.LOW

        // ── MeCool smart tier (Amlogic SoC) ──────────────────────────────────
        if (isMeCool || isAmlogic) {
            val model = Build.MODEL.lowercase()
            return when {
                // KM7 / S922X (4 GB+) → HIGH
                (model.contains("km7") || g.contains("s922")) && ramMb >= 4000 -> Tier.HIGH
                // KM6 / S905X4 (2 GB+) → MID
                (model.contains("km6") || g.contains("s905x4")) && ramMb >= 2000 -> Tier.MID
                // KM2 / older → LOW
                else -> Tier.LOW
            }
        }

        // ── Xiaomi smart tier ──────────────────────────────────────────────
        if (isXiaomi) {
            val model = Build.MODEL.lowercase()
            return when {
                // Mi Box S / Mi Box 4K → HIGH
                model.contains("mi box") && ramMb >= 2000 -> Tier.HIGH
                // Mi TV Stick 4K → MID
                model.contains("mi stick") || model.contains("tv stick") -> Tier.MID
                else -> Tier.MID
            }
        }

        // ── Generic GPU-based tier ─────────────────────────────────────────
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
        "Xiaomi=$isXiaomi | MeCool=$isMeCool | Amlogic=$isAmlogic | " +
        "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
