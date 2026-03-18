package com.luminastreams.tv.core

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class LuminaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // מקצה רק 10% מהזיכרון במקום 25%, קריטי ל-TV!
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            // אל תשמור תמונות גדולות מדי בזיכרון הפעיל!
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // מוריד את איכות פענוח הצבע ל-RGB_565 (חוסך 50% בזיכרון של כל פוסטר!!)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
    }
}