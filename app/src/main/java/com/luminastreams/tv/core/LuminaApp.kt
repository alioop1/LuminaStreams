package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
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

        // חובה לאתחל את הפרופיל של המכשיר קודם כל!
        DeviceProfile.init(this)

        val prefs = getSharedPreferences("lumina_settings", MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        Log.d("DeviceProfile", DeviceProfile.debugInfo())

        setupCoil()

        // התיקון כאן: העברת this ל-MediaRepositoryImpl
        repository = MediaRepositoryImpl(this)

        // GC on non-HIGH devices — free init overhead fast
        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            Runtime.getRuntime().gc()
            registerLowMemoryCallbacks()
        }
    }

    private fun setupCoil() {
        val maxHeap = Runtime.getRuntime().maxMemory()

        val memoryCachePercent = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 0.15
            DeviceProfile.Tier.MID  -> 0.08
            DeviceProfile.Tier.LOW  -> 0.05
        }
        val memoryCacheBytes = (maxHeap * memoryCachePercent).toLong()
            .coerceIn(24 * 1024 * 1024L, 256 * 1024 * 1024L)

        val diskCacheBytes = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 512 * 1024 * 1024L
            DeviceProfile.Tier.MID  -> 256 * 1024 * 1024L
            DeviceProfile.Tier.LOW  -> 96  * 1024 * 1024L
        }

        val connectTimeout = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 15L else 10L
        val readTimeout    = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 20L else 15L

        val okhttp = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().also { d ->
                d.maxRequests        = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 4 else 8
                d.maxRequestsPerHost = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 2 else 4
            })
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
            .crossfade(DeviceProfile.tier == DeviceProfile.Tier.HIGH)
            .respectCacheHeaders(false)
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