package com.luminastreams.tv.core  // ✅ תוקן

import android.app.Application
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.repository.MediaRepository

class LuminaApp : Application() {
    lateinit var repository: MediaRepository

    override fun onCreate() {
        super.onCreate()
        repository = MediaRepositoryImpl()
        DeviceProfile.init(this)
        android.util.Log.d("DeviceProfile", DeviceProfile.debugInfo())
    }
}
