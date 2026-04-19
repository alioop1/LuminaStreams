package com.luminastreams.tv.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

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
        val maxRowItems       : Int
    )

    var isXiaomi     : Boolean = false; private set
    var isMeCool     : Boolean = false; private set
    var isAmlogic    : Boolean = false; private set
    var isLg         : Boolean = false; private set
    var isSony       : Boolean = false; private set
    var isPhilips    : Boolean = false; private set
    var isNvidia     : Boolean = false; private set
    var isRockchip   : Boolean = false; private set
    var isWeakAmlogic: Boolean = false; private set
    // S905X4 flag — Mali-G31 MP2, 4GB RAM, can do HIGH visuals but no tunneling
    var isS905X4     : Boolean = false; private set

    var gpuRenderer: String = "unknown"; private set
    var totalRamMb : Int    = 0;         private set

    lateinit var tier      : Tier;       private set
    lateinit var animConfig: AnimConfig; private set

    var forceLowTier: Boolean = false
        set(value) { field = value; animConfig = buildConfig(effectiveTier()) }

    var forceReduceMotion: Boolean = false
        set(value) { field = value; animConfig = buildConfig(effectiveTier()) }

    private fun effectiveTier(): Tier = if (forceLowTier) Tier.LOW else tier

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
                ) && !hardware.contains("s922") && !hardware.contains("s905x4")

        // S905X4 = Mali-G31 MP2 — strong enough for HIGH visuals, no tunneling
        isS905X4 = hardware.contains("s905x4") || model.contains("km6")

        gpuRenderer = buildGpuLabel(manufacturer, hardware, model, board)
        tier        = detectTier(hardware, model, board)
        animConfig  = buildConfig(effectiveTier())
    }

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
                // S905X4 / KM6: Mali-G31 MP2 + 4GB RAM → HIGH
                // (tunneling disabled separately in ExoPlayerWrapper)
                hw.contains("s905x4") || model.contains("km6") -> Tier.HIGH
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

    private fun readTotalRam(context: Context): Int {
        val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / 1024 / 1024).toInt()
    }

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
                // Parallax disabled on Mali-G31 (S905X4) — not enough fillrate
                enableParallax    = !isS905X4,
                lazyBeyondBounds  = 2,
                // Cap row items on S905X4 to avoid Mali-G31 overdraw
                maxRowItems       = if (isS905X4) 20 else Int.MAX_VALUE
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

    data class BufferConfig(
        val minBufferMs       : Int,
        val maxBufferMs       : Int,
        val bufferForPlayMs   : Int,
        val bufferForReplayMs : Int,
        val targetBufferBytes : Int
    )

    val bufferConfig: BufferConfig get() = when (effectiveTier()) {
        // Shield + KM7 + KM6 all get the same HIGH buffer profile
        Tier.HIGH -> BufferConfig( 8_000, 20_000, 1_500, 3_000, 10 * 1024 * 1024)
        // Raised targetBufferBytes from 6MB → 10MB to match HIGH
        Tier.MID  -> BufferConfig( 6_000, 15_000, 1_500, 3_000, 10 * 1024 * 1024)
        Tier.LOW  -> BufferConfig(10_000, 25_000, 2_000, 4_000,  5 * 1024 * 1024)
    }

    fun debugInfo(): String =
        "Tier=${if (forceLowTier) "LOW(forced)" else tier.name} | " +
                "ReduceMotion=$forceReduceMotion | GPU=$gpuRenderer | RAM=${totalRamMb}MB | " +
                "Nvidia=$isNvidia | Xiaomi=$isXiaomi | MeCool=$isMeCool | S905X4=$isS905X4 | " +
                "rowFade=${animConfig.rowFadeDuration}ms | parallax=${animConfig.enableParallax}"
}
