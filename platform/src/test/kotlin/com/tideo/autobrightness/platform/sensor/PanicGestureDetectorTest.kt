package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * D-116 (reworked prof769): [PanicGestureDetector] now reports only the **orientation** half of the
 * panic STATE — sustained upside-down — while the shake is validated separately by PanicShakeGate. Pure
 * detector tests; the Android source adds the display-on (State 123/1) + proximity-not-near gates and
 * the 10 s shake window.
 */
class PanicGestureDetectorTest {

    /** Push steady readings so the low-pass gravity estimate converges to [y] on the y axis. */
    private fun PanicGestureDetector.settle(y: Float, frames: Int = 20) {
        repeat(frames) { onAccelerometer(0f, y, 0f) }
    }

    @Test
    fun heldUpsideDown_reportsSustainedAfterStreak() {
        val d = PanicGestureDetector(sustainedFrames = 5)
        d.onAccelerometer(0f, -9.8f, 0f) // seed (does not count toward the streak)
        repeat(4) { assertFalse(d.onAccelerometer(0f, -9.8f, 0f), "not sustained yet") }
        assertTrue(d.onAccelerometer(0f, -9.8f, 0f), "sustained upside-down after the streak")
        assertTrue(d.isUpsideDown, "instantaneous orientation is upside-down")
    }

    @Test
    fun heldUpright_neverUpsideDown() {
        val d = PanicGestureDetector()
        d.settle(9.8f) // Android reads +9.8 on +y when upright → not inverted
        assertFalse(d.onAccelerometer(0f, 9.8f, 0f), "upright must never report upside-down")
        assertFalse(d.isUpsideDown)
    }

    @Test
    fun flatFaceUp_neverUpsideDown() {
        val d = PanicGestureDetector()
        repeat(20) { d.onAccelerometer(0f, 0f, 9.8f) } // gravity dominated by +z, y ≈ 0
        assertFalse(d.onAccelerometer(0f, 0f, 9.8f), "lying flat is not upside-down (y not dominant)")
        assertFalse(d.isUpsideDown)
    }

    @Test
    fun firstReading_isSeedOnly() {
        val d = PanicGestureDetector()
        // The very first reading seeds the gravity filter and never reports sustained (even if inverted).
        assertFalse(d.onAccelerometer(0f, -30f, 0f), "first reading only seeds gravity")
    }

    @Test
    fun exposesGravityStrippedShakeMagnitude_forFallback() {
        val d = PanicGestureDetector()
        d.settle(-9.8f) // gravity converged to (0, −9.8, 0)
        // A reading at rest (== gravity) leaves ~0 residual; a spike leaves a large residual.
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
        // After reset the next reading is a seed again → not sustained.
        assertFalse(d.onAccelerometer(0f, -9.8f, 0f), "reset re-seeds; streak starts over")
        assertEquals(0.0, d.linearMagnitude, 1e-9)
    }
}
