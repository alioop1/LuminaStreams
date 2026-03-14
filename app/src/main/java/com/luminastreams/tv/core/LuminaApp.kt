package com.luminastreams.tv.core

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import android.graphics.Bitmap

class LuminaApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // שימוש ב-RGB_565 חוסך 50% מזיכרון ה-RAM לעומת ARGB_8888
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    // הגבלה נוקשה של 10% מהזיכרון הפנוי בלבד כדי למנוע קריסות (OOM) ב-2GB RAM
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // הגבלת נפח אחסון למכשירי TV
                    .build()
            }
            .crossfade(true)
            .build()
    }
}