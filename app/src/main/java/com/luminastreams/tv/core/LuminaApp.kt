package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.room.Room
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder
import com.luminastreams.tv.data.local.LuminaDatabase
import com.luminastreams.tv.data.repository.IptvRepository
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.data.repository.WatchlistRepository
import com.luminastreams.tv.domain.repository.MediaRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LuminaApp : Application() {
    lateinit var repository: MediaRepository
        private set

    lateinit var database: LuminaDatabase
        private set

    lateinit var iptvRepository: IptvRepository
        private set

    lateinit var watchlistRepository: WatchlistRepository
        private set

    override fun onCreate() {
        super.onCreate()

        DeviceProfile.init(this)

        val prefs = getSharedPreferences("lumina_settings", MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        Log.d("DeviceProfile", DeviceProfile.debugInfo())

        setupCoil()

        repository = MediaRepositoryImpl(this)

        database = Room.databaseBuilder(
            this,
            LuminaDatabase::class.java,
            "lumina_iptv.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        iptvRepository = IptvRepository(database.iptvDao())
        watchlistRepository = WatchlistRepository(database.watchlistDao())

        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            registerLowMemoryCallbacks()
        }
    }

    private fun setupCoil() {
        val maxHeap = Runtime.getRuntime().maxMemory()

        val memoryCachePercent = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 0.08
            DeviceProfile.Tier.MID  -> 0.06
            DeviceProfile.Tier.LOW  -> 0.04
        }
        val memoryCacheBytes = (maxHeap * memoryCachePercent).toLong()
            .coerceIn(32 * 1024 * 1024L, 512 * 1024 * 1024L)

        val diskCacheBytes = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> 1024 * 1024 * 1024L
            DeviceProfile.Tier.MID  ->  512 * 1024 * 1024L
            DeviceProfile.Tier.LOW  ->  256 * 1024 * 1024L
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
            .connectionPool(
                okhttp3.ConnectionPool(
                    if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 5 else 10,
                    5, TimeUnit.MINUTES
                )
            )
            .build()

        // FIX: Hardware bitmaps ENABLED for all tiers to keep UI thread clean
        val bitmapConfig = Bitmap.Config.HARDWARE

        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
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
                        // Do nothing, let Android OS natively manage memory.
                    }
                    TRIM_MEMORY_RUNNING_LOW,
                    TRIM_MEMORY_RUNNING_CRITICAL -> {
                        Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                        Log.w("LuminaApp", "⚠️ Low memory — Coil cache cleared (tier=${DeviceProfile.tier.name})")
                    }
                    TRIM_MEMORY_COMPLETE -> {
                        Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
                        Log.e("LuminaApp", "🔴 Critical memory — caches cleared (tier=${DeviceProfile.tier.name})")
                    }
                    else -> {}
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() {
                Coil.imageLoader(this@LuminaApp).memoryCache?.clear()
            }
        })
    }
}