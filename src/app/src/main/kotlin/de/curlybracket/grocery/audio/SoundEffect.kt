package de.curlybracket.grocery.audio

import de.curlybracket.grocery.R

enum class SoundEffect(val resourceId: Int, val volume: Float, val rate: Float) {
    SUCCESS(R.raw.beep_success, 1f, 1f),
    FAILURE(R.raw.beep_failure, 0.7f, 0.8f),
}
