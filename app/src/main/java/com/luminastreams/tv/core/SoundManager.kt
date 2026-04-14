package com.luminastreams.tv.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.luminastreams.tv.R

class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private val clickSoundId: Int
    private val navSoundId: Int
    private val splashSoundId: Int

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        clickSoundId  = soundPool.load(context, R.raw.snd_click,  1)
        navSoundId    = soundPool.load(context, R.raw.snd_nav,    1)
        splashSoundId = soundPool.load(context, R.raw.snd_splash, 1)
    }

    // Guard every play() call: SoundPool.play() with soundId=0 (load failure)
    // silently passes an invalid ID to the JNI layer and increments
    // mObjectCount without ever decrementing it, leaking the native object.
    fun playClick()  { if (clickSoundId  > 0) soundPool.play(clickSoundId,  1f,  1f,  1, 0, 1f) }
    fun playNav()    { if (navSoundId    > 0) soundPool.play(navSoundId,    0.4f, 0.4f, 1, 0, 1f) }
    fun playSplash() { if (splashSoundId > 0) soundPool.play(splashSoundId, 1f,  1f,  1, 0, 1f) }

    fun release() {
        // FIX: stopAll() drains the native stream queue synchronously.
        // Without this, release() is called while streams are still active
        // and the JNI ObjectManager destructor fires with mObjectCount > 0,
        // logging "~ObjectManager: mObjectCount: 1 should be zero".
        soundPool.autoPause()  // pause any currently-playing streams
        soundPool.unload(clickSoundId)
        soundPool.unload(navSoundId)
        soundPool.unload(splashSoundId)
        soundPool.release()
    }
}
