package de.curlybracket.grocery.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AudioFeedbackTest {

    private lateinit var mockPool: SoundPool
    private lateinit var audioFeedback: AudioFeedback
    private var loadCompleteListener: SoundPool.OnLoadCompleteListener? = null

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        mockPool = mockk(relaxed = true)

        mockkConstructor(SoundPool.Builder::class, AudioAttributes.Builder::class)

        every { anyConstructed<AudioAttributes.Builder>().setUsage(any()) } answers { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().setContentType(any()) } answers { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().build() } returns mockk(relaxed = true)

        every { anyConstructed<SoundPool.Builder>().setMaxStreams(any()) } answers { self as SoundPool.Builder }
        every { anyConstructed<SoundPool.Builder>().setAudioAttributes(any()) } answers { self as SoundPool.Builder }
        every { anyConstructed<SoundPool.Builder>().build() } returns mockPool

        val listenerSlot = slot<SoundPool.OnLoadCompleteListener>()
        every { mockPool.setOnLoadCompleteListener(capture(listenerSlot)) } answers {
            loadCompleteListener = listenerSlot.captured
        }

        var nextId = 1
        every { mockPool.load(any<Context>(), any<Int>(), any()) } answers { nextId++ }

        audioFeedback = AudioFeedback(context)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `SoundEffect SUCCESS has correct properties`() {
        assertEquals(1f, SoundEffect.SUCCESS.volume)
        assertEquals(1f, SoundEffect.SUCCESS.rate)
    }

    @Test
    fun `SoundEffect FAILURE has correct properties`() {
        assertEquals(0.7f, SoundEffect.FAILURE.volume)
        assertEquals(0.8f, SoundEffect.FAILURE.rate)
    }

    @Test
    fun `play is no-op when effect is not yet loaded`() {
        audioFeedback.play(SoundEffect.SUCCESS)
        verify(exactly = 0) { mockPool.play(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `play SUCCESS dispatches correct id, volume and rate after load`() {
        // SUCCESS is first enum entry → id = 1
        loadCompleteListener!!.onLoadComplete(mockPool, 1, 0)

        audioFeedback.play(SoundEffect.SUCCESS)

        verify(exactly = 1) {
            mockPool.play(1, SoundEffect.SUCCESS.volume, SoundEffect.SUCCESS.volume, 1, 0, SoundEffect.SUCCESS.rate)
        }
    }

    @Test
    fun `play FAILURE dispatches correct id, volume and rate after load`() {
        // FAILURE is second enum entry → id = 2
        loadCompleteListener!!.onLoadComplete(mockPool, 2, 0)

        audioFeedback.play(SoundEffect.FAILURE)

        verify(exactly = 1) {
            mockPool.play(2, SoundEffect.FAILURE.volume, SoundEffect.FAILURE.volume, 1, 0, SoundEffect.FAILURE.rate)
        }
    }

    @Test
    fun `play is no-op when load completed with non-zero status`() {
        loadCompleteListener!!.onLoadComplete(mockPool, 1, 1) // error status
        audioFeedback.play(SoundEffect.SUCCESS)
        verify(exactly = 0) { mockPool.play(any(), any(), any(), any(), any(), any()) }
    }
}
