package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build

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

    // ✅ Set by SettingsViewModel when Lite UI mode is toggled ON.
    var forceLowTier: Boolean = false
        set(value) {
            field = value
            animConfig = buildConfig(effectiveTier())
        }

    // ✅ Set by SettingsViewModel when Reduce Motion is toggled ON.
    // Zeroes ALL animation durations regardless of tier — no visual change on strong devices
    // beyond skipping transitions (they were already fast there).
    var forceReduceMotion: Boolean = false
        set(value) {
            field = value
            animConfig = buildConfig(effectiveTier())
        }

    private fun effectiveTier(): Tier =
        if (forceLowTier) Tier.LOW else tier

    fun init(context: Context) {
        gpuRenderer = readGpuRenderer()
        totalRamMb  = readTotalRam(context)

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
        animConfig = buildConfig(effectiveTier())
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
        if (g.contains("vivante")) return Tier.LOW
        if (isMeCool || isAmlogic) {
            val model = Build.MODEL.lowercase()
            return when {
                (model.contains("km7") || g.contains("s922")) && ramMb >= 4000 -> Tier.HIGH
                (model.contains("km6") || g.contains("s905x4")) && ramMb >= 2000 -> Tier.MID
                else -> Tier.LOW
            }
        }
        if (isXiaomi) {
            val model = Build.MODEL.lowercase()
            return when {
                model.contains("mi box") && ramMb >= 2000 -> Tier.HIGH
                model.contains("mi stick") || model.contains("tv stick") -> Tier.MID
                else -> Tier.MID
            }
        }
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

    private fun buildConfig(t: Tier): AnimConfig {
        // forceReduceMotion → zero everything, regardless of tier
        if (forceReduceMotion) return AnimConfig(
            rowFadeDuration   = 0,
            backdropDuration  = 0,
            heroFadeDuration  = 0,
            crossfadeDuration = 0,
            enableRowFade     = false,
            enableParallax    = false,
            lazyBeyondBounds  = 0
        )
        return when (t) {
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
    }

    fun debugInfo(): String =
        "Tier=${if (forceLowTier) "LOW (forced)" else tier.name} | " +
        "ReduceMotion=$forceReduceMotion | " +
        "GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
        "Xiaomi=$isXiaomi | MeCool=$isMeCool | Amlogic=$isAmlogic | " +
        "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
