package de.curlybracket.grocery.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import de.curlybracket.grocery.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFeedback @Inject constructor(@ApplicationContext context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var successId = 0
    private var failureId = 0
    private var successLoaded = false
    private var failureLoaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                when (sampleId) {
                    successId -> successLoaded = true
                    failureId -> failureLoaded = true
                }
            }
        }
        successId = soundPool.load(context, R.raw.beep_success, 1)
        failureId = soundPool.load(context, R.raw.beep_failure, 1)
    }

    fun playSuccess() { if (successLoaded) soundPool.play(successId, 1f, 1f, 1, 0, 1f) }
    fun playFailure() { if (failureLoaded) soundPool.play(failureId, 0.7f, 0.7f, 1, 0, 0.8f) }
    fun release() = soundPool.release()
}
