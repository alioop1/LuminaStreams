package com.luminastreams.tv.core

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.repository.MediaRepository

class LuminaApp : Application() {
    lateinit var repository: MediaRepository

    override fun onCreate() {
        super.onCreate()
        repository = MediaRepositoryImpl()

        // ✅ DeviceProfile must be init first (reads GPU + RAM)
        DeviceProfile.init(this)

        // ✅ REAL: apply Lite UI Mode from saved settings immediately on cold start
        // so DeviceProfile.forceLowTier is set before any screen composes.
        val prefs = getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        DeviceProfile.forceLowTier = prefs.getBoolean("lite_ui", false)

        android.util.Log.d("DeviceProfile", DeviceProfile.debugInfo())

        // ✅ On LOW/MID devices: trim heap immediately after init so largeHeap
        // doesn't sit as wasted reserved memory before any stream starts.
        // HIGH devices benefit from largeHeap and are left untouched.
        if (DeviceProfile.tier != DeviceProfile.Tier.HIGH) {
            Runtime.getRuntime().gc()
            registerLowMemoryCallbacks()
        }
    }

    // ✅ ComponentCallbacks2 — מקבלים callbacks מה-OS כשהזיכרון מתחיל להיגמר.
    // על מכשיר 2GB זה קריטי: מנקים caches לפני שה-OS הורג את התהליך.
    private fun registerLowMemoryCallbacks() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when (level) {
                    // מגיע כשה-app עובר לרקע — נקה מיד
                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                        Runtime.getRuntime().gc()
                    }
                    // מגיע כשהזיכרון במכשיר מתחיל להיגמר
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                        Runtime.getRuntime().gc()
                        android.util.Log.w("LuminaApp", "⚠️ Low memory — GC triggered (tier=${DeviceProfile.tier.name})")
                    }
                    // מגיע כשה-OS עומד להרוג תהליכים
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        Runtime.getRuntime().gc()
                        android.util.Log.e("LuminaApp", "🔴 Critical memory — forced GC (tier=${DeviceProfile.tier.name})")
                    }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() {
                Runtime.getRuntime().gc()
            }
        })
    }
}
