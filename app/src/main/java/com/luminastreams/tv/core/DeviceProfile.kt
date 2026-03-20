package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.opengl.EGL14
import android.opengl.GLES20
import javax.microedition.khronos.egl.EGL10

// ═══════════════════════════════════════════════════════════════════
// DeviceProfile — מזהה את המכשיר בזמן ריצה ומכוונן ביצועים
// ═══════════════════════════════════════════════════════════════════

object DeviceProfile {

    enum class Tier { LOW, MID, HIGH }

    data class AnimConfig(
        val rowFadeDuration: Int,       // ms לfade של row
        val backdropDuration: Int,      // ms למעבר backdrop
        val heroFadeDuration: Int,      // ms לfade של hero metadata
        val crossfadeDuration: Int,     // ms לcoil crossfade
        val enableRowFade: Boolean,     // האם בכלל להפעיל fade לrows
        val enableParallax: Boolean,    // האם להפעיל parallax בhero
        val lazyBeyondBounds: Int       // כמה rows מעבר למסך לrender מראש
    )

    lateinit var tier: Tier
        private set

    lateinit var animConfig: AnimConfig
        private set

    lateinit var gpuRenderer: String
        private set

    var totalRamMb: Int = 0
        private set

    fun init(context: Context) {
        gpuRenderer  = readGpuRenderer()
        totalRamMb   = readTotalRam(context)
        tier         = detectTier(gpuRenderer, totalRamMb)
        animConfig   = buildConfig(tier)
    }

    // ── GPU renderer מה-OpenGL ──────────────────────────────────────
    private fun readGpuRenderer(): String = try {
        // EGL context כבר קיים כי Compose כבר מאותחל
        GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
    } catch (_: Exception) { "unknown" }

    // ── RAM כוללת ──────────────────────────────────────────────────
    private fun readTotalRam(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / 1024 / 1024).toInt()
    }

    // ── זיהוי tier לפי GPU + RAM ───────────────────────────────────
    private fun detectTier(gpu: String, ramMb: Int): Tier {
        val g = gpu.lowercase()
        return when {
            // ✅ HIGH — GPUs חזקים
            g.contains("tegra")                          -> Tier.HIGH  // NVIDIA Shield
            g.contains("adreno 6") || g.contains("adreno 7") -> Tier.HIGH  // Snapdragon 8xx
            g.contains("mali-g7") || g.contains("mali-g9")   -> Tier.HIGH  // Exynos חזק
            g.contains("apple")                          -> Tier.HIGH
            ramMb >= 3000 && !g.contains("mali-g3")     -> Tier.HIGH

            // ⚠️ MID — GPUs בינוניים
            g.contains("adreno 5")                       -> Tier.MID
            g.contains("mali-g5") || g.contains("mali-g6") -> Tier.MID
            g.contains("powervr gm")                     -> Tier.MID
            ramMb in 2000..2999                          -> Tier.MID

            // 🔴 LOW — Mali-G31, Mali-450, Amlogic ישן
            else                                         -> Tier.LOW
        }
    }

    // ── הגדרות אנימציה לפי tier ────────────────────────────────────
    private fun buildConfig(tier: Tier): AnimConfig = when (tier) {
        Tier.HIGH -> AnimConfig(
            rowFadeDuration    = 200,
            backdropDuration   = 500,
            heroFadeDuration   = 350,
            crossfadeDuration  = 200,
            enableRowFade      = true,
            enableParallax     = true,
            lazyBeyondBounds   = 2
        )
        Tier.MID -> AnimConfig(
            rowFadeDuration    = 120,
            backdropDuration   = 300,
            heroFadeDuration   = 200,
            crossfadeDuration  = 100,
            enableRowFade      = true,
            enableParallax     = false,
            lazyBeyondBounds   = 1
        )
        Tier.LOW -> AnimConfig(
            rowFadeDuration    = 0,     // ✅ ללא אנימציה כלל
            backdropDuration   = 150,
            heroFadeDuration   = 0,
            crossfadeDuration  = 0,
            enableRowFade      = false, // ✅ alpha סטטי — אפס עומס GPU
            enableParallax     = false,
            lazyBeyondBounds   = 0      // ✅ render רק מה שנראה בדיוק
        )
    }

    // ── debug info ─────────────────────────────────────────────────
    fun debugInfo(): String =
        "Tier=$tier | GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
                "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
