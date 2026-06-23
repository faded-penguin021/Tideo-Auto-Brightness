package com.tideo.autobrightness.domain.power

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S14: the pure task524 `_CalibratePowerDraw` math — geometric steps, current normalization, and the
 * net-of-idle post-processing — ported from the extracted Java. The device sweep is orchestrated by
 * `app/runtime/PowerDrawCalibrator`.
 */
class PowerDrawCalibrationTest {

    @Test
    fun generateSteps_isAscending_endsAt255_andRespectsMinDiff() {
        val steps = PowerDrawCalibration.generateSteps()
        assertTrue(steps.isNotEmpty(), "must produce steps")
        assertEquals(255, steps.last(), "the sweep always ends at full brightness")
        assertTrue(steps.all { it in 0..255 }, "all steps within 0..255")
        // Strictly ascending; every gap (except possibly the forced final 255) ≥ MIN_STEP_DIFF.
        for (i in 1 until steps.size) {
            assertTrue(steps[i] > steps[i - 1], "steps strictly ascending")
            val gap = steps[i] - steps[i - 1]
            assertTrue(
                gap >= PowerDrawCalibration.MIN_STEP_DIFF || steps[i] == 255,
                "gap $gap below MIN_STEP_DIFF at index $i (only the final 255 may be closer)",
            )
        }
    }

    @Test
    fun normalizeCurrentMa_convertsMicroampsAndTakesMagnitude() {
        // |raw| > 50000 ⇒ µA → mA (÷1000); otherwise already mA. Sign (discharge) is dropped.
        assertEquals(120.0, PowerDrawCalibration.normalizeCurrentMa(120_000), 1e-9)
        assertEquals(75.0, PowerDrawCalibration.normalizeCurrentMa(-75_000), 1e-9)
        assertEquals(480.0, PowerDrawCalibration.normalizeCurrentMa(480), 1e-9)
        assertEquals(0.0, PowerDrawCalibration.normalizeCurrentMa(0), 1e-9)
    }

    @Test
    fun postProcess_subtractsIdleFloor_netOfIdle() {
        // baseline 100 mA idle; steps draw 100/160/240. Net = raw − min(=100): 0/60/140.
        val out = PowerDrawCalibration.postProcess(
            brightness = listOf(0, 120, 255),
            rawMa = listOf(100.0, 160.0, 240.0),
            rawW = listOf(0.40, 0.64, 0.96),
        )
        assertEquals(listOf(0, 120, 255), out.map { it.brightness })
        assertEquals(listOf(0.0, 60.0, 140.0), out.map { it.currentMa })
        assertEquals(0.0, out[0].powerW, 1e-9)
        assertTrue(out[2].powerW > out[1].powerW, "net power rises with brightness")
    }

    @Test
    fun postProcess_zeroesBogusBaseline_whenSample0ExceedsSample1() {
        // task524 step 5: if the idle baseline reads HIGHER than the first lit step, it is bogus → zero it.
        val out = PowerDrawCalibration.postProcess(
            brightness = listOf(0, 120, 255),
            rawMa = listOf(300.0, 150.0, 250.0),
            rawW = listOf(1.2, 0.6, 1.0),
        )
        // sample0 (300) > sample1 (150) → zero it, then min = 0 → net = raw.
        assertEquals(0.0, out[0].currentMa, 1e-9)
        assertEquals(150.0, out[1].currentMa, 1e-9)
        assertEquals(250.0, out[2].currentMa, 1e-9)
    }
}
