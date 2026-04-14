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
        val lazyBeyondBounds  : Int,
        // Max items per row rendered into LazyRow — keeps LOW tier from
        // paying the recomposition cost of 20 identical invisible cards.
        val maxRowItems       : Int
    )

    // ── Manufacturer / chipset flags ──────────────────────────────────────────
    var isXiaomi     : Boolean = false; private set
    var isMeCool     : Boolean = false; private set
    var isAmlogic    : Boolean = false; private set
    var isLg         : Boolean = false; private set
    var isSony       : Boolean = false; private set
    var isPhilips    : Boolean = false; private set
    var isNvidia     : Boolean = false; private set
    var isRockchip   : Boolean = false; private set
    var isWeakAmlogic: Boolean = false; private set

    // YYC / Skyworth / generic no-name Android TV boxes that use the Amlogic
    // SoC but report a non-standard manufacturer string. These boxes have
    // the same broken CCodec stack and need tunneled video for 4K HDR.
    var isGenericAmlogicBox: Boolean = false; private set

    // True for ANY device that needs tunneled video to play 4K HDR:
    // MeCool, generic Amlogic boxes, and any device where the standard
    // MediaCodec path for HEVC Main10 is broken or missing.
    val needsTunneledVideo: Boolean
        get() = isAmlogic || isGenericAmlogicBox || isMeCool

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

        // Generic/no-name Android TV boxes that use Amlogic SoC but report
        // a custom manufacturer (YYC, Skyworth, HiMedia, Tanix, etc.).
        // Detection strategy: manufacturer is not a known brand AND the SoC
        // is Amlogic (detected via board name, SOC_MODEL, or known board strings).
        val socModel = if (Build.VERSION.SDK_INT >= 31)
            Build.SOC_MODEL.lowercase() else ""
        val isKnownBrand = isXiaomi || isMeCool || isLg || isSony ||
                isPhilips || isNvidia || isRockchip ||
                manufacturer.contains("samsung") || manufacturer.contains("tcl") ||
                manufacturer.contains("hisense") || manufacturer.contains("sharp")
        val hasSkyworthy = manufacturer.contains("skyworth") ||
                manufacturer.contains("yyc") ||
                manufacturer.contains("hisilicon") ||
                manufacturer.contains("tanix") ||
                manufacturer.contains("h96") ||
                manufacturer.contains("x96") ||
                model.contains("4k android tv box") ||
                model.contains("android tv box")
        val hasAmlogicSoc = socModel.contains("amlogic") ||
                socModel.contains("s905") || socModel.contains("s922") ||
                socModel.contains("s912") || socModel.contains("t962") ||
                board.contains("meson") || board.contains("p212") ||
                board.contains("p230") || board.contains("p231")
        isGenericAmlogicBox = hasSkyworthy || (!isKnownBrand && (isAmlogic || hasAmlogicSoc))

        // Treat generic boxes as isAmlogic for all downstream logic
        if (isGenericAmlogicBox) isAmlogic = true

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
            isGenericAmlogicBox                                  -> "Mali (Amlogic/Generic)"
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

        if (totalRamMb < 2500) return Tier.LOW

        if (isMeCool || isAmlogic) {
            return when {
                hw.contains("s922") || model.contains("km7")   -> Tier.HIGH
                hw.contains("s905x4") || model.contains("km6") -> Tier.MID
                hw.contains("s905x3")                          -> Tier.LOW
                hw.contains("s905x2")                          -> Tier.LOW
                hw.contains("s905x")                           -> Tier.LOW
                hw.contains("s905d")                           -> Tier.LOW
                hw.contains("s905w")                           -> Tier.LOW
                hw.contains("s905")                            -> Tier.LOW
                // Generic Amlogic box with unknown SoC variant — treat as LOW
                // to be safe; better to under-animate than to stutter.
                isGenericAmlogicBox                            -> Tier.LOW
                else                                           -> Tier.LOW
            }
        }

        if (isXiaomi) {
            return when {
                model.contains("mi box s") -> Tier.HIGH
                else                       -> Tier.LOW
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
        if (forceReduceMotion) return AnimConfig(
            rowFadeDuration = 0, backdropDuration = 0, heroFadeDuration = 0,
            crossfadeDuration = 0, enableRowFade = false, enableParallax = false,
            lazyBeyondBounds = 0, maxRowItems = 10
        )
        return when (t) {
            Tier.HIGH -> AnimConfig(
                rowFadeDuration   = 200,
                backdropDuration  = 500,
                heroFadeDuration  = 350,
                crossfadeDuration = 200,
                enableRowFade     = true,
                enableParallax    = true,
                lazyBeyondBounds  = 2,
                maxRowItems       = Int.MAX_VALUE
            )
            Tier.MID -> AnimConfig(
                rowFadeDuration   = 120,
                backdropDuration  = 250,
                heroFadeDuration  = 150,
                crossfadeDuration = 80,
                enableRowFade     = true,
                enableParallax    = false,
                lazyBeyondBounds  = 1,
                maxRowItems       = Int.MAX_VALUE
            )
            Tier.LOW -> AnimConfig(
                rowFadeDuration   = 0,
                backdropDuration  = 0,
                heroFadeDuration  = 0,
                crossfadeDuration = 0,
                enableRowFade     = false,
                enableParallax    = false,
                lazyBeyondBounds  = 1,
                maxRowItems       = 15
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
        Tier.HIGH -> BufferConfig( 8_000, 20_000, 1_500, 3_000, 10 * 1024 * 1024)
        Tier.MID  -> BufferConfig( 6_000, 15_000, 1_500, 3_000,  6 * 1024 * 1024)
        Tier.LOW  -> BufferConfig(10_000, 25_000, 2_000, 4_000,  5 * 1024 * 1024)
    }

    fun debugInfo(): String =
        "Tier=${if (forceLowTier) "LOW(forced)" else tier.name} | " +
                "ReduceMotion=$forceReduceMotion | GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
                "Nvidia=$isNvidia | Xiaomi=$isXiaomi | MeCool=$isMeCool | " +
                "GenericAmlogic=$isGenericAmlogicBox | NeedsTunnel=$needsTunneledVideo | " +
                "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
