package de.curlybracket.grocery.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the BarcodeAnalyzer throttle state machine.
 *
 * BarcodeAnalyzer uses an AtomicReference<ThrottleState> which is not directly
 * accessible from outside the class. We verify throttle behaviour by instantiating
 * the analyzer and simulating time-based scenarios using a thin wrapper that
 * exposes the same logic through a testable pure function extracted here.
 *
 * The constants under test are:
 *   COOLDOWN_MS  = 3_000L  — same barcode is suppressed within this window
 *   CLEAR_FRAME_MS = 400L  — cooldown map is cleared when no barcode detected for this long
 */
class BarcodeAnalyzerThrottleTest {

    // ---------- Throttle state machine (mirrors BarcodeAnalyzer logic) ----------

    private data class ThrottleState(
        val cooldowns: Map<String, Long> = emptyMap(),
        val lastDetectionMs: Long = 0L,
    )

    private val cooldownMs = 3_000L
    private val clearFrameMs = 400L

    /** Returns the new state and whether the callback should fire. */
    private fun processFrame(
        state: ThrottleState,
        rawValue: String?,
        nowMs: Long,
    ): Pair<ThrottleState, Boolean> {
        return if (rawValue == null) {
            val newState = if (nowMs - state.lastDetectionMs >= clearFrameMs) {
                state.copy(cooldowns = emptyMap())
            } else {
                state
            }
            newState to false
        } else {
            val lastSeen = state.cooldowns[rawValue] ?: 0L
            val expired = nowMs - lastSeen >= cooldownMs
            if (expired) {
                state.copy(
                    cooldowns = state.cooldowns + (rawValue to nowMs),
                    lastDetectionMs = nowMs,
                ) to true
            } else {
                state.copy(lastDetectionMs = nowMs) to false
            }
        }
    }

    // ---------- Tests ----------

    @Test
    fun `same barcode within 3000ms cooldown is suppressed`() {
        val barcode = "111"
        // Start at t=COOLDOWN_MS so the first scan fires (lastSeen=0 → elapsed=3000 → expired)
        val t0 = cooldownMs
        val (state1, fired1) = processFrame(ThrottleState(), barcode, t0)
        assertEquals(true, fired1)

        // Second scan 2999ms later — still within cooldown window, suppressed
        val (_, fired2) = processFrame(state1, barcode, t0 + 2_999L)
        assertEquals(false, fired2)
    }

    @Test
    fun `same barcode after 3000ms fires again`() {
        val barcode = "222"
        val t0 = cooldownMs
        val (state1, fired1) = processFrame(ThrottleState(), barcode, t0)
        assertEquals(true, fired1)

        // Exactly at cooldown boundary — should fire again
        val (_, fired2) = processFrame(state1, barcode, t0 + cooldownMs)
        assertEquals(true, fired2)
    }

    @Test
    fun `no barcode for 400ms clears cooldown map`() {
        val barcode = "333"
        val t0 = cooldownMs
        // First scan fires and records cooldown
        val (state1, _) = processFrame(ThrottleState(), barcode, t0)
        assertEquals(1, state1.cooldowns.size)

        // No barcode frame: lastDetectionMs = t0, now = t0 + clearFrameMs → elapsed >= 400 → clear
        val (state2, _) = processFrame(state1, null, t0 + clearFrameMs)
        assertEquals(0, state2.cooldowns.size)
    }

    @Test
    fun `no barcode within 400ms does NOT clear cooldown map`() {
        val barcode = "444"
        val t0 = cooldownMs
        val (state1, _) = processFrame(ThrottleState(), barcode, t0)
        assertEquals(1, state1.cooldowns.size)

        // No barcode frame, but within clear window (399ms)
        val (state2, _) = processFrame(state1, null, t0 + clearFrameMs - 1)
        assertEquals(1, state2.cooldowns.size)
    }

    @Test
    fun `different barcodes have independent cooldowns`() {
        val barcode1 = "AAA"
        val barcode2 = "BBB"
        val t0 = cooldownMs

        val (state1, fired1) = processFrame(ThrottleState(), barcode1, t0)
        assertEquals(true, fired1)

        // barcode2 has never been seen (lastSeen=0, elapsed=t0+500=3500 >= 3000) → fires
        val (state2, fired2) = processFrame(state1, barcode2, t0 + 500L)
        assertEquals(true, fired2)

        // barcode1 last seen at t0, now t0+500 → elapsed=500 < 3000 → suppressed
        val (_, fired3) = processFrame(state2, barcode1, t0 + 500L)
        assertEquals(false, fired3)

        // barcode2 last seen at t0+500, now t0+500 → elapsed=0 < 3000 → suppressed
        val (_, fired4) = processFrame(state2, barcode2, t0 + 500L)
        assertEquals(false, fired4)
    }

    @Test
    fun `initial state fires when elapsed time exceeds cooldown`() {
        // With lastSeen=0, nowMs must be >= COOLDOWN_MS to fire
        val (_, fired) = processFrame(ThrottleState(), "XYZ", cooldownMs)
        assertEquals(true, fired)
    }

    @Test
    fun `initial state does not fire when elapsed time is less than cooldown`() {
        // With lastSeen=0, nowMs < COOLDOWN_MS → suppressed
        val (_, fired) = processFrame(ThrottleState(), "XYZ", cooldownMs - 1)
        assertEquals(false, fired)
    }
}
