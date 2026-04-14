package com.luminastreams.tv.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.luminastreams.tv.R

class SoundManager(context: Context) {

    private var soundPool: SoundPool? = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val clickSoundId  = soundPool?.load(context, R.raw.snd_click,  1) ?: 0
    private val navSoundId    = soundPool?.load(context, R.raw.snd_nav,    1) ?: 0
    private val splashSoundId = soundPool?.load(context, R.raw.snd_splash, 1) ?: 0

    // Guard every play() call: SoundPool.play() with soundId == 0 (load failure)
    // increments the JNI ObjectManager's mObjectCount without a matching
    // decrement, so each guarded-off call would permanently leak a native object.
    fun playClick()  { soundPool?.takeIf { clickSoundId  > 0 }?.play(clickSoundId,  1f,   1f,   1, 0, 1f) }
    fun playNav()    { soundPool?.takeIf { navSoundId    > 0 }?.play(navSoundId,    0.4f, 0.4f, 1, 0, 1f) }
    fun playSplash() { soundPool?.takeIf { splashSoundId > 0 }?.play(splashSoundId, 1f,   1f,   1, 0, 1f) }

    /**
     * Release all native SoundPool resources.
     *
     * Call from Activity.onStop() — NOT only onDestroy().
     *
     * When Android kills the process with SIGKILL (e.g. user swipes app away
     * from recents, OOM killer fires, or the system reclaims resources),
     * onDestroy() is NOT guaranteed to run. onStop() IS always dispatched
     * through the normal lifecycle before the process can be killed, so
     * placing release() here ensures the native SoundPool objects are always
     * freed cleanly and the JNI ~ObjectManager sees mObjectCount == 0.
     *
     * autoPause() drains the active-stream queue synchronously so no streams
     * are in flight when release() destroys the pool. unload() frees each
     * sound buffer's native object, decrementing the count before release().
     */
    fun release() {
        soundPool?.let { pool ->
            pool.autoPause()               // stop all active streams synchronously
            pool.unload(clickSoundId)      // free native buffer objects
            pool.unload(navSoundId)
            pool.unload(splashSoundId)
            pool.release()                 // destroy pool — mObjectCount is now 0
        }
        soundPool = null                   // prevent any double-release
    }
}
