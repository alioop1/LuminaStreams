package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.repository.MediaRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LuminaApp : Application() {
    lateinit var repository: MediaRepository

    override fun onCreate() {
        super.onCreate()

        // MUST initialise device profile first
        DeviceProfile.init(this)

        val prefs = getSharedPreferences("lumina_settings", MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        Log.d("DeviceProfile", DeviceProfile.debugInfo())

        setupCoil()

        repository = MediaRepositoryImpl(this)

        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            Runtime.getRuntime().gc()
            registerLowMemoryCallbacks()
        }
    }

    private fun setupCoil() {
        val maxHeap = Runtime.getRuntime().maxMemory()

        // ── Memory cache ────────────────────────────────────────────────────────
        // HIGH gets a larger slice so 4K assets stay warm between screen transitions.
        val memoryCachePercent = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 0.20   // ← was 0.15; more room for original-res assets
            DeviceProfile.Tier.MID  -> 0.10   // ← was 0.08
            DeviceProfile.Tier.LOW  -> 0.06   // ← was 0.05
        }
        val memoryCacheBytes = (maxHeap * memoryCachePercent).toLong()
            .coerceIn(32 * 1024 * 1024L, 512 * 1024 * 1024L)   // 32 MB–512 MB

        // ── Disk cache ──────────────────────────────────────────────────────────
        val diskCacheBytes = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 1024 * 1024 * 1024L   // 1 GB  (was 512 MB)
            DeviceProfile.Tier.MID  ->  512 * 1024 * 1024L   // 512 MB (was 256 MB)
            DeviceProfile.Tier.LOW  ->  256 * 1024 * 1024L   // 256 MB (was 96 MB — far too small for TV)
        }

        val connectTimeout = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 20L else 12L
        val readTimeout    = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 30L else 20L

        val okhttp = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().also { d ->
                d.maxRequests        = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 6 else 12
                d.maxRequestsPerHost = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 3 else 6
            })
            // Aggressive image CDN keep-alive
            .connectionPool(
                okhttp3.ConnectionPool(
                    if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 5 else 10,
                    5, TimeUnit.MINUTES
                )
            )
            .build()

        // ── Bitmap config ───────────────────────────────────────────────────────
        //
        // CRITICAL FIX: Previously LOW tier used allowRgb565(true) which forces
        // 16-bit color (RGB_565). This causes severe color banding on TV displays —
        // especially visible on gradients and backdrop images.
        //
        // Fix: ALWAYS use ARGB_8888 (32-bit). Use hardware bitmaps on HIGH/MID to
        // keep GPU memory usage efficient while retaining full color fidelity.
        //
        // LOW tier gets software ARGB_8888 (no hardware bitmap) to avoid driver
        // bugs on cheap Amlogic SoCs, but still full 32-bit color.
        val bitmapConfig = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> Bitmap.Config.HARDWARE   // GPU texture, zero copy
            DeviceProfile.Tier.MID  -> Bitmap.Config.HARDWARE   // GPU texture
            DeviceProfile.Tier.LOW  -> Bitmap.Config.ARGB_8888  // CPU, full color, no driver issues
        }

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(memoryCacheBytes.toInt())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheBytes)
                    .build()
            }
            .okHttpClient(okhttp)
            // ── Quality flags ──────────────────────────────────────────────────
            // crossfade: purely visual, enable on MID+ for polish
            .crossfade(DeviceProfile.tier != DeviceProfile.Tier.LOW)
            // respectCacheHeaders: false = always use our disk cache, ignore server
            // Cache-Control headers that might force redownloads
            .respectCacheHeaders(false)
            // bitmapConfig: ensures all decoded bitmaps use chosen config globally
            .bitmapConfig(bitmapConfig)
            .build()

        Coil.setImageLoader(imageLoader)
    }

    @Suppress("DEPRECATION")
    private fun registerLowMemoryCallbacks() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when (level) {
                    TRIM_MEMORY_UI_HIDDEN -> {
                        Runtime.getRuntime().gc()
                    }
                    TRIM_MEMORY_RUNNING_LOW,
                    TRIM_MEMORY_RUNNING_CRITICAL -> {
                        Runtime.getRuntime().gc()
                        Log.w("LuminaApp", "⚠️ Low memory — GC triggered (tier=${DeviceProfile.tier.name})")
                    }
                    TRIM_MEMORY_COMPLETE -> {
                        Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                        Runtime.getRuntime().gc()
                        Log.e("LuminaApp", "🔴 Critical memory — caches cleared (tier=${DeviceProfile.tier.name})")
                    }
                    else -> {}
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() {
                Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                Runtime.getRuntime().gc()
            }
        })
    }
}