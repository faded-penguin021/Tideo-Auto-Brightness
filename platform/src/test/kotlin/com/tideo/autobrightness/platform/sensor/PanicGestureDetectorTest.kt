package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** D-116 (prof769): reports orientation half (sustained upside-down); shake validated separately. Pure detector tests. */
class PanicGestureDetectorTest {

    private fun PanicGestureDetector.settle(y: Float, frames: Int = 20) {
        repeat(frames) { onAccelerometer(0f, y, 0f) }
    }

    @Test
    fun heldUpsideDown_reportsSustainedAfterStreak() {
        val d = PanicGestureDetector(sustainedFrames = 5)
        d.onAccelerometer(0f, -9.8f, 0f) // seed (does not count toward streak)
        repeat(4) { assertFalse(d.onAccelerometer(0f, -9.8f, 0f), "not sustained yet") }
        assertTrue(d.onAccelerometer(0f, -9.8f, 0f), "sustained upside-down after the streak")
        assertTrue(d.isUpsideDown, "instantaneous orientation is upside-down")
    }

    @Test
    fun heldUpright_neverUpsideDown() {
        val d = PanicGestureDetector()
        d.settle(9.8f) // +9.8 on +y when upright
        assertFalse(d.onAccelerometer(0f, 9.8f, 0f), "upright must never report upside-down")
        assertFalse(d.isUpsideDown)
    }

    @Test
    fun flatFaceUp_neverUpsideDown() {
        val d = PanicGestureDetector()
        repeat(20) { d.onAccelerometer(0f, 0f, 9.8f) } // gravity on +z; y ≈ 0
        assertFalse(d.onAccelerometer(0f, 0f, 9.8f), "lying flat is not upside-down (y not dominant)")
        assertFalse(d.isUpsideDown)
    }

    @Test
    fun firstReading_isSeedOnly() {
        val d = PanicGestureDetector()
        // First reading seeds gravity filter, never sustained
        assertFalse(d.onAccelerometer(0f, -30f, 0f), "first reading only seeds gravity")
    }

    @Test
    fun exposesGravityStrippedShakeMagnitude_forFallback() {
        val d = PanicGestureDetector()
        d.settle(-9.8f) // gravity converged to (0, −9.8, 0)
        // At rest (~0 residual); spike (large residual)
        d.onAccelerometer(0f, -9.8f, 0f)
        assertTrue(d.linearMagnitude < 1.0, "at rest the gravity-stripped magnitude is ~0")
        d.onAccelerometer(0f, -40f, 0f)
        assertTrue(d.linearMagnitude > 20.0, "a hard shake leaves a large gravity-stripped magnitude")
    }

    @Test
    fun reset_clearsStreakAndOrientation() {
        val d = PanicGestureDetector(sustainedFrames = 3)
        d.settle(-9.8f)
        assertTrue(d.onAccelerometer(0f, -9.8f, 0f))
        d.reset()
        // After reset, next reading is seed again
        assertFalse(d.onAccelerometer(0f, -9.8f, 0f), "reset re-seeds; streak starts over")
        assertEquals(0.0, d.linearMagnitude, 1e-9)
    }
}
