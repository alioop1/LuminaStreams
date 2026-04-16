package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.room.Room
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.luminastreams.tv.data.local.LuminaDatabase
import com.luminastreams.tv.data.repository.IptvRepository
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.repository.MediaRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LuminaApp : Application() {
    lateinit var repository: MediaRepository
        private set

    // הוספת מסד הנתונים
    lateinit var database: LuminaDatabase
        private set

    // הוספת מנהל הנתונים של ה-IPTV
    lateinit var iptvRepository: IptvRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // MUST initialise device profile first
        DeviceProfile.init(this)

        val prefs = getSharedPreferences("lumina_settings", MODE_PRIVATE)
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui",       false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)

        Log.d("DeviceProfile", DeviceProfile.debugInfo())

        setupCoil()

        // אתחול המאגר הישן של הסרטים
        repository = MediaRepositoryImpl(this)

        // אתחול מסד הנתונים והמאגר החדש של ה-IPTV
        database = Room.databaseBuilder(
            this,
            LuminaDatabase::class.java,
            "lumina_iptv.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        iptvRepository = IptvRepository(database.iptvDao())

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
            DeviceProfile.Tier.HIGH -> 0.08   // ירד מ-0.20 ל-0.08
            DeviceProfile.Tier.MID  -> 0.06
            DeviceProfile.Tier.LOW  -> 0.04
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