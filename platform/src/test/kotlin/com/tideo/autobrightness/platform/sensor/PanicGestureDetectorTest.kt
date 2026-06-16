package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * S12.8a (G2R-F77): prof769 panic gesture = upside-down (gravity.y high) + shake (linear-accel spike).
 * Pure detector tests; the Android source adds the display-on (State 123/1) gate + cooldown.
 */
class PanicGestureDetectorTest {

    /** Push steady readings so the low-pass gravity estimate converges to [y] on the y axis. */
    private fun PanicGestureDetector.settle(y: Float, frames: Int = 20) {
        repeat(frames) { onAccelerometer(0f, y, 0f) }
    }

    @Test
    fun upsideDownPlusShake_fires() {
        val d = PanicGestureDetector()
        d.settle(9.8f) // held upside down: gravity points toward the top of the screen (+y)
        // A hard shake while inverted: a large y deviation past the shake threshold.
        assertTrue(d.onAccelerometer(0f, 30f, 0f), "upside-down + shake must trigger the panic gesture")
    }

    @Test
    fun upsideDownButSteady_doesNotFire() {
        val d = PanicGestureDetector()
        d.settle(9.8f)
        // Inverted but no shake (no linear-accel spike) → no panic.
        assertFalse(d.onAccelerometer(0f, 9.8f, 0f), "no shake → no panic")
    }

    @Test
    fun shakeWhileUpright_doesNotFire() {
        val d = PanicGestureDetector()
        // Upright portrait: gravity points toward the bottom of the screen (−y), so not upside down.
        d.settle(-9.8f)
        assertFalse(d.onAccelerometer(0f, -30f, 0f), "a shake the right way up must not trigger panic")
    }

    @Test
    fun firstReading_isSeedOnly() {
        val d = PanicGestureDetector()
        // The very first reading seeds the gravity filter and never fires (even if extreme).
        assertFalse(d.onAccelerometer(0f, 30f, 0f), "first reading only seeds gravity")
    }
}
