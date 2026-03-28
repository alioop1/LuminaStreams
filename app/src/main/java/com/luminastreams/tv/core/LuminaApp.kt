package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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

        // 1. DeviceProfile FIRST — every other component depends on tier
        DeviceProfile.init(this)

        // 2. Apply saved user overrides immediately so first composition is correct
        val prefs = getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        android.util.Log.d("DeviceProfile", DeviceProfile.debugInfo())

        // 3. Configure Coil image loader — tier-aware cache sizes
        setupCoil()

        // 4. Repository
        repository = MediaRepositoryImpl()

        // 5. GC + memory callbacks for LOW/MID devices
        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            Runtime.getRuntime().gc()
            registerLowMemoryCallbacks()
        }
    }

    // ── Coil configuration ────────────────────────────────────────────────────
    private fun setupCoil() {
        val maxHeap = Runtime.getRuntime().maxMemory()

        // Memory cache: 15 % for HIGH, 10 % for MID, 6 % for LOW
        val memoryCachePercent = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 0.15
            DeviceProfile.Tier.MID  -> 0.10
            DeviceProfile.Tier.LOW  -> 0.06
        }
        val memoryCacheBytes = (maxHeap * memoryCachePercent).toLong()
            .coerceIn(32 * 1024 * 1024L, 256 * 1024 * 1024L)   // 32 MB – 256 MB

        // Disk cache: larger on HIGH to avoid re-downloading UHD artwork
        val diskCacheBytes = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 512 * 1024 * 1024L   // 512 MB
            DeviceProfile.Tier.MID  -> 256 * 1024 * 1024L   // 256 MB
            DeviceProfile.Tier.LOW  -> 128 * 1024 * 1024L   // 128 MB
        }

        // Parallelism — LOW devices struggle with many concurrent HTTP+decode jobs
        val fetcherPoolSize = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 6
            DeviceProfile.Tier.MID  -> 4
            DeviceProfile.Tier.LOW  -> 2
        }

        val okhttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

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
            // Disable crossfade on LOW to avoid janky alpha animations
            .crossfade(DeviceProfile.tier != DeviceProfile.Tier.LOW)
            .respectCacheHeaders(false)          // TMDB images are immutable by path
            .build()

        Coil.setImageLoader(imageLoader)
    }

    // ── Low-memory callbacks ──────────────────────────────────────────────────
    private fun registerLowMemoryCallbacks() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                        Runtime.getRuntime().gc()
                    }
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                        Runtime.getRuntime().gc()
                        android.util.Log.w("LuminaApp", "⚠️ Low memory — GC triggered (tier=${DeviceProfile.tier.name})")
                    }
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        // Clear Coil memory cache on critical memory pressure
                        Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                        Runtime.getRuntime().gc()
                        android.util.Log.e("LuminaApp", "🔴 Critical memory — caches cleared (tier=${DeviceProfile.tier.name})")
                    }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() {
                Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                Runtime.getRuntime().gc()
            }
        })
    }
}