package com.luminastreams.tv.core

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LuminaApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // OkHttp משותף עם connection pool
        val okhttp = OkHttpClient.Builder()
            .connectTimeout(8,  TimeUnit.SECONDS)
            .readTimeout(12,    TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            // ARGB_8888 חובה ל-TV — RGB_565 שובר hardware acceleration
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .okHttpClient(okhttp)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 25% מה-heap — מספיק ל-TV 4K עם largeHeap
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // 150MB disk cache — תמונות לא נטענות שוב
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            // כבה crossfade גלובלי — מורכבות compositing מיותרת
            .crossfade(false)
            .build()
    }
}
