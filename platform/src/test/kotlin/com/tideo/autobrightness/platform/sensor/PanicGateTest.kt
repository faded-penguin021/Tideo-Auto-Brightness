package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** D-116 (prof769): PanicGate re-arm latch (10s shake window state machine). */
class PanicGateTest {

    @Test
    fun armsWhenConditionTrue() {
        val gate = PanicGate()
        assertTrue(gate.canArm(armed = true, upsideDown = true), "armed condition → may start a window")
    }

    @Test
    fun doesNotArmWhenNotArmed() {
        val gate = PanicGate()
        assertFalse(gate.canArm(armed = false, upsideDown = true), "not-armed → no window")
    }

    @Test
    fun afterConsume_doesNotReArmWhileStillUpsideDown() {
        val gate = PanicGate()
        assertTrue(gate.canArm(armed = true, upsideDown = true))
        gate.consume()
        assertFalse(gate.canArm(armed = true, upsideDown = true), "still inverted → latched, no re-arm")
        assertFalse(gate.canArm(armed = true, upsideDown = true), "stays latched across readings")
    }

    @Test
    fun reArmsOnlyAfterFlippingStraightThenInvertingAgain() {
        val gate = PanicGate()
        gate.canArm(armed = true, upsideDown = true)
        gate.consume()
        // D-165: sustained straight spell (≥ rearmFrames readings) clears latch.
        repeat(PanicGate.REARM_FRAMES) {
            assertFalse(gate.canArm(armed = false, upsideDown = false), "flipped straight: not armed yet")
        }
        // Invert again → the condition re-arms.
        assertTrue(gate.canArm(armed = true, upsideDown = true), "re-entry re-arms the gesture")
    }

    @Test
    fun consumeWithoutFlip_neverReArms_butFlipResets() {
        val gate = PanicGate()
        gate.canArm(armed = true, upsideDown = true)
        gate.consume()
        repeat(5) { assertFalse(gate.canArm(armed = true, upsideDown = true), "no re-arm while inverted") }
        // A sustained flip-straight spell clears the latch (D-165).
        repeat(PanicGate.REARM_FRAMES) { gate.canArm(armed = false, upsideDown = false) }
        assertTrue(gate.canArm(armed = true, upsideDown = true), "after a flip the next inversion arms")
    }

    @Test
    fun shakeFlickerWhileInverted_doesNotClearTheLatch_D165() {
        // D-165: a vigorous same-axis shake transiently flips the instantaneous isUpsideDown false —
        // the exact flicker the window logic is already immune to (AndroidPanicSensorSource) — and
        // the α=0.9 gravity low-pass keeps it false for ~5-6 readings after a single strong spike.
        // Those bursts must NOT clear the re-arm latch: after a timed-out window, shaking the still-
        // inverted phone must not open a fresh window (the Tasker STATE re-fires only on re-entry).
        val gate = PanicGate()
        gate.canArm(armed = true, upsideDown = true)
        gate.consume() // window timed out; the user still holds the phone inverted
        repeat(20) {
            // Shake artifact: a burst of non-inverted readings shorter than the re-arm spell…
            repeat(PanicGate.REARM_FRAMES - 5) {
                assertFalse(gate.canArm(armed = false, upsideDown = false), "flicker burst must not re-arm")
            }
            // …then the filter recovers to stably-inverted readings (the straight streak resets).
            repeat(5) {
                assertFalse(gate.canArm(armed = true, upsideDown = true), "still latched while inverted")
            }
        }
        // A genuine sustained flip straight still clears, and re-entry re-arms.
        repeat(PanicGate.REARM_FRAMES) { gate.canArm(armed = false, upsideDown = false) }
        assertTrue(gate.canArm(armed = true, upsideDown = true), "sustained straight + re-entry re-arms")
    }
}
