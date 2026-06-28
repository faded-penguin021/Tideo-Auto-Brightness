package com.tideo.autobrightness.platform.sensor

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * D-116 (reworked prof769): [PanicGate] is now the **re-arm latch** for the 10 s shake window. After a
 * window runs (fired OR timed out) the gesture must not start another until the phone is flipped straight
 * and inverted again — exactly like a Tasker STATE re-entry (`tmp/Tmp.md`). Pure state-machine tests.
 */
class PanicGateTest {

    @Test
    fun armsWhenConditionTrue() {
        val gate = PanicGate()
        assertTrue(gate.canArm(armed = true, upsideDown = true), "armed condition → may start a window")
    }

    @Test
    fun doesNotArmWhenNotArmed() {
        val gate = PanicGate()
        // e.g. proximity near, or display off, or not yet sustained-upside-down.
        assertFalse(gate.canArm(armed = false, upsideDown = true), "not-armed → no window")
    }

    @Test
    fun afterConsume_doesNotReArmWhileStillUpsideDown() {
        val gate = PanicGate()
        assertTrue(gate.canArm(armed = true, upsideDown = true))
        gate.consume() // a window ran (timeout or fire)
        // Still held upside down (the timeout case): must NOT re-arm even though the condition holds.
        assertFalse(gate.canArm(armed = true, upsideDown = true), "still inverted → latched, no re-arm")
        assertFalse(gate.canArm(armed = true, upsideDown = true), "stays latched across readings")
    }

    @Test
    fun reArmsOnlyAfterFlippingStraightThenInvertingAgain() {
        val gate = PanicGate()
        gate.canArm(armed = true, upsideDown = true)
        gate.consume()
        // Flip straight: upside-down exits → latch clears (but not armed in this orientation).
        assertFalse(gate.canArm(armed = false, upsideDown = false), "flipped straight: not armed yet")
        // Invert again → the condition re-arms.
        assertTrue(gate.canArm(armed = true, upsideDown = true), "re-entry re-arms the gesture")
    }

    @Test
    fun consumeWithoutFlip_neverReArms_butFlipResets() {
        val gate = PanicGate()
        gate.canArm(armed = true, upsideDown = true)
        gate.consume()
        repeat(5) { assertFalse(gate.canArm(armed = true, upsideDown = true), "no re-arm while inverted") }
        gate.canArm(armed = false, upsideDown = false) // flip straight clears the latch
        assertTrue(gate.canArm(armed = true, upsideDown = true), "after a flip the next inversion arms")
    }
}
