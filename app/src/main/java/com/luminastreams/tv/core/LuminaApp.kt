package com.luminastreams.tv.core

import android.app.Application
import android.content.Context
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
    }
}