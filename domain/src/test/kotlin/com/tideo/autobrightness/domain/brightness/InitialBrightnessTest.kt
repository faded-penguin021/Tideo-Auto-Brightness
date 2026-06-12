package com.tideo.autobrightness.domain.brightness

import com.tideo.autobrightness.domain.reference.TaskerReference
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [InitialBrightness.computeInitialLux].
 *
 * Cross-validates against [TaskerReference.setInitialBrightness] (the Tasker oracle).
 * Tie cases (0.5, 1.5, 2.345, -2.5) confirm Math.round semantics: ties round toward +∞,
 * differing from Kotlin's kotlin.math.round (rounds to even).
 */
class InitialBrightnessTest {

    @Test
    fun computeInitialLux_sweepMatchesOracle() {
        val cases = listOf(
            0.0, 0.5, 1.0, 1.5, 2.0, 2.345, 2.5, 10.0, 100.0, 999.5, 1000.0, 120_000.0,
            // Negative (raw lux is never negative in practice; oracle defined for all doubles)
            -0.5, -2.5,
        )
        for (raw in cases) {
            val (lux0dp, lux2dp) = InitialBrightness.computeInitialLux(raw)
            val ref = TaskerReference.setInitialBrightness(raw)
            assertEquals(ref.smoothedLux0dp, lux0dp, "smoothedLux0dp mismatch at raw=$raw")
            assertEquals(ref.smoothedLux2dp, lux2dp, 1e-9, "smoothedLux2dp mismatch at raw=$raw")
        }
    }

    @Test
    fun computeInitialLux_tieRoundsTowardPlusInfinity() {
        // Java Math.round(0.5) = 1, kotlin.math.round(0.5) = 0 (ties-to-even) — must be 1
        val (lux0dp1, _) = InitialBrightness.computeInitialLux(0.5)
        assertEquals(1L, lux0dp1, "0.5 must round to 1 (Math.round tie-toward-+∞)")

        // Math.round(1.5) = 2
        val (lux0dp2, _) = InitialBrightness.computeInitialLux(1.5)
        assertEquals(2L, lux0dp2, "1.5 must round to 2")

        // Math.round(-2.5) = -2 (toward +∞, i.e. less negative)
        val (neg0dp, _) = InitialBrightness.computeInitialLux(-2.5)
        assertEquals(-2L, neg0dp, "-2.5 must round to -2 (toward +∞)")
    }

    @Test
    fun computeInitialLux_2dpRounding() {
        // 2-dp: Math.round(rawLux * 100.0) / 100.0
        // 2.345 * 100 = 234.5 → Math.round = 235 → 2.35
        val (_, lux2dp) = InitialBrightness.computeInitialLux(2.345)
        assertEquals(2.35, lux2dp, 1e-9, "2.345 * 100 = 234.5 → Math.round = 235 → 2.35")
    }

    @Test
    fun computeInitialLux_zeroInput() {
        val (lux0dp, lux2dp) = InitialBrightness.computeInitialLux(0.0)
        assertEquals(0L, lux0dp)
        assertEquals(0.0, lux2dp, 1e-9)
    }
}
