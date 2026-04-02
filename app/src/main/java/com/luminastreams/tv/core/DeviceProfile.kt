package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Detects hardware tier at app start and exposes animation/quality configs.
 */
object DeviceProfile {

    enum class Tier { LOW, MID, HIGH }

    data class AnimConfig(
        val rowFadeDuration   : Int,
        val backdropDuration  : Int,
        val heroFadeDuration  : Int,
        val crossfadeDuration : Int,
        val enableRowFade     : Boolean,
        val enableParallax    : Boolean,
        val lazyBeyondBounds  : Int
    )

    // ── Manufacturer / chipset flags ──────────────────────────────────────────
    var isXiaomi    : Boolean = false; private set
    var isMeCool    : Boolean = false; private set
    var isAmlogic   : Boolean = false; private set
    var isLg        : Boolean = false; private set
    var isSony      : Boolean = false; private set
    var isPhilips   : Boolean = false; private set
    var isNvidia    : Boolean = false; private set
    var isRockchip  : Boolean = false; private set
    var isWeakAmlogic: Boolean = false; private set

    // ── Read-only state ────────────────────────────────────────────────────────
    var gpuRenderer: String = "unknown"; private set
    var totalRamMb : Int    = 0;         private set

    lateinit var tier      : Tier;       private set
    lateinit var animConfig: AnimConfig; private set

    // ── User overrides ─────────────────────────────────────────────────────────
    var forceLowTier: Boolean = false
        set(value) { field = value; animConfig = buildConfig(effectiveTier()) }

    var forceReduceMotion: Boolean = false
        set(value) { field = value; animConfig = buildConfig(effectiveTier()) }

    private fun effectiveTier(): Tier = if (forceLowTier) Tier.LOW else tier

    // ── Initialisation ─────────────────────────────────────────────────────────
    fun init(context: Context) {
        totalRamMb = readTotalRam(context)

        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware     = Build.HARDWARE.lowercase()
        val model        = Build.MODEL.lowercase()
        val board        = Build.BOARD.lowercase()

        isXiaomi    = manufacturer.contains("xiaomi")
        isMeCool    = manufacturer.contains("mecool") || model.contains("km") || model.contains("mecool")
        isAmlogic   = hardware.contains("amlogic") || hardware.contains("s905") ||
                hardware.contains("s922")           || hardware.contains("s912") ||
                hardware.contains("s905x")          || board.contains("s905")
        isLg        = manufacturer.contains("lge") || manufacturer.contains("lg") ||
                model.contains("oled")              || model.contains(" lg")
        isSony      = manufacturer.contains("sony")
        isPhilips   = manufacturer.contains("philips") || manufacturer.contains("tp vision")
        isNvidia    = manufacturer.contains("nvidia") || model.contains("shield")
        isRockchip  = hardware.contains("rockchip") || hardware.contains("rk3588") ||
                hardware.contains("rk3399")         || board.contains("rk3588")

        isWeakAmlogic = isAmlogic && (
                hardware.contains("s905x")  ||
                        hardware.contains("s905d")  ||
                        hardware.contains("s905w")  ||
                        hardware.contains("s905l")  ||
                        hardware.contains("s905")   ||
                        board.contains("s905")
                ) && !hardware.contains("s922")

        gpuRenderer = buildGpuLabel(manufacturer, hardware, model, board)
        tier        = detectTier(hardware, model, board)
        animConfig  = buildConfig(effectiveTier())
    }

    // ── GPU label ──────────────────────────────────────────────────────────────
    private fun buildGpuLabel(mfr: String, hw: String, model: String, board: String): String {
        return when {
            mfr.contains("nvidia")                               -> "Tegra (Nvidia)"
            hw.contains("rk3588")  || board.contains("rk3588")  -> "Mali-G610 (RK3588)"
            hw.contains("rk3399")  || board.contains("rk3399")  -> "Mali-T860 (RK3399)"
            hw.contains("s922")                                  -> "Mali-G52 (S922X)"
            hw.contains("s905x4")  || model.contains("km7")     -> "Mali-G31 (S905X4)"
            hw.contains("s905x3")                                -> "Mali-G31 (S905X3)"
            hw.contains("s905x2")                                -> "Mali-450 (S905X2)"
            hw.contains("s905")                                  -> "Mali-450 (S905)"
            mfr.contains("lge")    || model.contains("oled")    -> "Mali-G78 (LG)"
            mfr.contains("sony")                                 -> "Adreno/PowerVR (Sony)"
            mfr.contains("xiaomi") && model.contains("mi box")  -> "Mali-G52 (Mi Box)"
            mfr.contains("xiaomi")                               -> "Mali (Xiaomi)"
            else                                                 -> "${hw}/${Build.SUPPORTED_ABIS[0]}"
        }
    }

    // ── Tier detection ─────────────────────────────────────────────────────────
    private fun detectTier(hw: String, model: String, board: String): Tier {
        if (isNvidia)  return Tier.HIGH
        if (isLg)      return Tier.HIGH
        if (isSony)    return Tier.HIGH
        if (isPhilips) return Tier.HIGH

        if (hw.contains("rk3588") || board.contains("rk3588")) return Tier.HIGH

        // חוק נוקשה: אם הראם קטן מ-2500MB (כמו ברוב הסטרימרים הסיניים) -> מיד LOW
        if (totalRamMb < 2500) return Tier.LOW

        if (isMeCool || isAmlogic) {
            return when {
                hw.contains("s922") || model.contains("km7")  -> Tier.HIGH
                hw.contains("s905x4") || model.contains("km6") -> Tier.LOW
                hw.contains("s905x3")                          -> Tier.LOW
                hw.contains("s905x2")                          -> Tier.LOW
                hw.contains("s905x")                           -> Tier.LOW
                hw.contains("s905d")                           -> Tier.LOW
                hw.contains("s905w")                           -> Tier.LOW
                hw.contains("s905")                            -> Tier.LOW
                else                                           -> Tier.LOW
            }
        }

        if (isXiaomi) {
            return when {
                model.contains("mi box s") -> Tier.HIGH
                else                                              -> Tier.LOW
            }
        }

        if (isRockchip) {
            return if (totalRamMb >= 4000) Tier.MID else Tier.LOW
        }

        return when {
            totalRamMb >= 6000 -> Tier.HIGH
            totalRamMb >= 3000 -> Tier.HIGH
            totalRamMb >= 2000 -> Tier.MID
            else               -> Tier.LOW
        }
    }

    // ── RAM ───────────────────────────────────────────────────────────────────
    private fun readTotalRam(context: Context): Int {
        val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / 1024 / 1024).toInt()
    }

    // ── AnimConfig factory ─────────────────────────────────────────────────────
    private fun buildConfig(t: Tier): AnimConfig {
        if (forceReduceMotion) return AnimConfig(0, 0, 0, 0, false, false, 0)
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
                backdropDuration  = 250,
                heroFadeDuration  = 150,
                crossfadeDuration = 80,
                enableRowFade     = true,
                enableParallax    = false,
                lazyBeyondBounds  = 1
            )
            Tier.LOW -> AnimConfig(
                rowFadeDuration   = 0,
                backdropDuration  = 0,
                heroFadeDuration  = 0,
                crossfadeDuration = 0,
                enableRowFade     = false,
                enableParallax    = false,
                lazyBeyondBounds  = 0
            )
        }
    }

    // ── ExoPlayer buffer sizes per tier ──────────────────────────────────────
    data class BufferConfig(
        val minBufferMs       : Int,
        val maxBufferMs       : Int,
        val bufferForPlayMs   : Int,
        val bufferForReplayMs : Int,
        val targetBufferBytes : Int
    )

    val bufferConfig: BufferConfig get() = when (effectiveTier()) {
        Tier.HIGH -> BufferConfig(15_000, 60_000, 2_500, 5_000, 64 * 1024 * 1024)
        Tier.MID  -> BufferConfig(12_000, 40_000, 2_000, 4_000, 32 * 1024 * 1024)
        // שדרוג הבאפר ל-LOW כדי ש-4K לא ייתקע! (אגרנו יותר שניות ויותר מגה-בייטים)
        Tier.LOW  -> BufferConfig(25_000, 60_000, 2_500, 5_000, 32 * 1024 * 1024)
    }

    fun debugInfo(): String =
        "Tier=${if (forceLowTier) "LOW(forced)" else tier.name} | " +
                "ReduceMotion=$forceReduceMotion | GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
                "Nvidia=$isNvidia | Xiaomi=$isXiaomi | MeCool=$isMeCool | " +
                "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}