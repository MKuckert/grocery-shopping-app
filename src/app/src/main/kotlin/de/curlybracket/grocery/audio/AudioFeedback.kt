package de.curlybracket.grocery.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFeedback @Inject constructor(@ApplicationContext context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = mutableMapOf<SoundEffect, Int>()
    private val loadedEffects = mutableSetOf<SoundEffect>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundIds.entries.find { it.value == sampleId }?.key?.let { effect ->
                    loadedEffects += effect
                }
            }
        }
        SoundEffect.entries.forEach { effect ->
            soundIds[effect] = soundPool.load(context, effect.resourceId, 1)
        }
    }

    fun play(effect: SoundEffect) {
        if (effect in loadedEffects) {
            soundPool.play(soundIds.getValue(effect), effect.volume, effect.volume, 1, 0, effect.rate)
        }
    }

    fun release() = soundPool.release()
}
