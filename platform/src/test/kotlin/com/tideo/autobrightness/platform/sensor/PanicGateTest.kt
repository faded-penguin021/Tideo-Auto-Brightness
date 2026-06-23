package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * S14 (owner: panic too sensitive on grab-to-wake): the [PanicGate] adds a screen-on grace window +
 * cooldown around the pure [PanicGestureDetector]. Pure timing tests with an explicit clock.
 */
class PanicGateTest {

    @Test
    fun wakeEdge_isReportedOnce() {
        val gate = PanicGate()
        // Screen off → on: the rising edge reports true once (caller resets the detector); staying on
        // does not re-report.
        assertFalse(gate.onScreenState(now = 0L, interactive = false))
        assertTrue(gate.onScreenState(now = 100L, interactive = true), "rising edge should report a wake")
        assertFalse(gate.onScreenState(now = 200L, interactive = true), "staying on is not a new wake")
    }

    @Test
    fun graceAfterWake_suppressesFire() {
        val gate = PanicGate(screenOnGraceMs = 3_000L)
        gate.onScreenState(now = 1_000L, interactive = true) // wake at t=1000 → grace until t=4000
        // A gesture detected during the grace window must NOT fire (the grab-to-wake shake).
        assertFalse(gate.shouldFire(now = 1_500L, interactive = true), "within grace → suppressed")
        assertFalse(gate.shouldFire(now = 3_999L, interactive = true), "still within grace → suppressed")
    }

    @Test
    fun firesAfterGraceElapses() {
        val gate = PanicGate(screenOnGraceMs = 3_000L)
        gate.onScreenState(now = 1_000L, interactive = true)
        assertTrue(gate.shouldFire(now = 4_500L, interactive = true), "past the grace window → fires")
    }

    @Test
    fun cooldown_debouncesRepeatFires() {
        val gate = PanicGate(screenOnGraceMs = 0L, cooldownMs = 3_000L)
        gate.onScreenState(now = 0L, interactive = true)
        assertTrue(gate.shouldFire(now = 10_000L, interactive = true), "first detection fires")
        assertFalse(gate.shouldFire(now = 11_000L, interactive = true), "within cooldown → suppressed")
        assertTrue(gate.shouldFire(now = 13_500L, interactive = true), "past cooldown → fires again")
    }

    @Test
    fun notInteractive_neverFires() {
        val gate = PanicGate(screenOnGraceMs = 0L)
        // A face-down phone in a pocket: the display is off, so the gesture must never fire.
        assertFalse(gate.shouldFire(now = 10_000L, interactive = false))
    }
}
