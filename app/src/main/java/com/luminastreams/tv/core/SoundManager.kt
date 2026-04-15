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

        soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(audioAttributes).build()

        clickSoundId = soundPool.load(context, R.raw.snd_click, 1)
        navSoundId = soundPool.load(context, R.raw.snd_nav, 1)
        splashSoundId = soundPool.load(context, R.raw.snd_splash, 1)
    }

    fun playClick() = soundPool.play(clickSoundId, 1f, 1f, 1, 0, 1f)
    fun playNav() = soundPool.play(navSoundId, 0.4f, 0.4f, 1, 0, 1f) // ווליום קצת יותר חלש לניווט
    fun playSplash() = soundPool.play(splashSoundId, 1f, 1f, 1, 0, 1f)

    fun release() = soundPool.release()
}