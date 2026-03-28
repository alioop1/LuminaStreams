package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
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

        DeviceProfile.init(this)

        val prefs = getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        Log.d("DeviceProfile", DeviceProfile.debugInfo())

        setupCoil()

        repository = MediaRepositoryImpl()

        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            Runtime.getRuntime().gc()
            registerLowMemoryCallbacks()
        }
    }

    private fun setupCoil() {
        val maxHeap = Runtime.getRuntime().maxMemory()

        val memoryCachePercent = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 0.15
            DeviceProfile.Tier.MID  -> 0.10
            DeviceProfile.Tier.LOW  -> 0.06
        }
        val memoryCacheBytes = (maxHeap * memoryCachePercent).toLong()
            .coerceIn(32 * 1024 * 1024L, 256 * 1024 * 1024L)

        val diskCacheBytes = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 512 * 1024 * 1024L
            DeviceProfile.Tier.MID  -> 256 * 1024 * 1024L
            DeviceProfile.Tier.LOW  -> 128 * 1024 * 1024L
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
            .crossfade(DeviceProfile.tier != DeviceProfile.Tier.LOW)
            .respectCacheHeaders(false)
            .build()

        Coil.setImageLoader(imageLoader)
    }

    @Suppress("DEPRECATION")
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
                        Log.w("LuminaApp", "⚠️ Low memory — GC triggered (tier=${DeviceProfile.tier.name})")
                    }
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
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