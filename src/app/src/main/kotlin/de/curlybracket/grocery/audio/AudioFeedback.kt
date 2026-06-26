package de.curlybracket.grocery.audio

import android.content.Context
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio feedback for app interactions (e.g., barcode scan success/failure).
 * The SoundPool must be explicitly released via [release()].
 */
@Singleton
class AudioFeedback @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val soundPool: SoundPool? = null // Placeholder; will be initialized in Task 13

    fun playSuccess() {
        // Placeholder; will be implemented in Task 13
    }

    fun playFailure() {
        // Placeholder; will be implemented in Task 13
    }

    fun release() {
        soundPool?.release()
    }
}
