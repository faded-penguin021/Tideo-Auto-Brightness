package com.tideo.autobrightness.domain.brightness

import com.tideo.autobrightness.domain.reference.TaskerReference
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SettlingPlacementTest {

    private val engine = BrightnessEngine()
    private val cfg = ThresholdConfig()
    private val curve = BrightnessCurveConfig(minBrightness = 0)
    private val tol = 1e-9

    private fun input(lux: Double, previous: PreviousState?, step: Int = 0, near: Boolean = false) = BrightnessPolicyInput(
        lux = lux,
        time = TimeContext(secondsOfDay = 12 * 3600.0),
        thresholds = cfg,
        curve = curve,
        previous = previous,
        proximityNear = near,
        settlingStep = step,
    )

    private fun carried(out: BrightnessPolicyOutput) = PreviousState(out.smoothedLux, out.threshDynamicPercent)

    private fun BrightnessPolicyOutput.inBand() = smoothedLux in thresholdLow..thresholdHigh

    private fun settle(from: Double, to: Double, near: Boolean = false): List<BrightnessPolicyOutput> {
        var out = engine.evaluate(input(from, null, near = near))
        out = engine.evaluate(input(to, carried(out), near = near))
        val steps = mutableListOf(out)
        var step = 0
        while (!out.inBand()) {
            step++
            if (step > BrightnessEngine.MAX_SETTLING_STEPS) fail("still outside the band after $step steps: $steps")
            val before = out
            out = engine.evaluate(input(to, carried(before), step, near))
            if (out.outcome != EvaluationOutcome.SETTLED) assertMatchesOracle(to, before, out)
            steps += out
        }
        return steps
    }

    private fun assertMatchesOracle(lux: Double, before: BrightnessPolicyOutput, out: BrightnessPolicyOutput) {
        val ref = TaskerReference.lightCycle(
            par1 = lux,
            state = TaskerReference.LightCycleState(before.smoothedLux, before.threshDynamicPercent),
            threshDim = cfg.threshDim,
            threshBright = cfg.threshBright,
            threshSteepness = cfg.threshSteepness,
            threshMidpoint = cfg.threshMidpoint,
            threshDark = cfg.threshDark,
            zone1End = cfg.zone1End,
            deltaFactor = cfg.deltaFactor,
            proximityNear = false,
        )
        assertEquals(ref.smoothedLux, out.smoothedLux, tol, "a progressing settling step is Tasker's step")
        assertEquals(ref.thresholds.threshAbsLow.toDouble(), out.thresholdLow, tol)
        assertEquals(ref.thresholds.threshAbsHigh.toDouble(), out.thresholdHigh, tol)
    }

    @Test
    fun dropToZero_stallsAboveTheBandThenLandsOnIt() {
        val steps = settle(160.0, 0.0)
        val last = steps.last()
        assertEquals(EvaluationOutcome.SETTLED, last.outcome)
        assertEquals(0.43, steps[steps.size - 2].smoothedLux, tol, "α reaches 0 at S* = p/(1 − p), above the 0–0 band")
        assertEquals(0.0 to 0.0, last.thresholdLow to last.thresholdHigh)
        assertEquals(0.0, last.smoothedLux, tol)
        assertEquals(0, last.targetBrightness)
        assertEquals(1.0, last.luxAlpha, tol)
    }

    @Test
    fun dropToNonZero_landsOnTheNearestEdgeNotTheReading() {
        val steps = settle(1_000.0, 100.0)
        val last = steps.last()
        assertEquals(EvaluationOutcome.SETTLED, last.outcome)
        assertEquals(75.0 to 125.0, last.thresholdLow to last.thresholdHigh)
        assertEquals(125.0, last.smoothedLux, tol)
        assertTrue(last.luxAlpha > 0.0 && last.luxAlpha <= 1.0, "α ${last.luxAlpha}")
        val lux = steps.map { it.smoothedLux }
        assertTrue(lux.zipWithNext().all { (a, b) -> b < a }, "smoothed lux must only fall: $lux")
        val targets = steps.map { it.targetBrightness }
        assertTrue(targets.zipWithNext().all { (a, b) -> b <= a }, "brightness must never move back: $targets")
    }

    @Test
    fun anAct19StopOutsideTheBand_places() {
        val previous = PreviousState(smoothedLux = 130.0, threshDynamicPercent = 25.0)
        assertEquals(EvaluationOutcome.DEAD_BAND_STOP, engine.evaluate(input(100.0, previous)).outcome, "30/131 is under d")
        val out = engine.evaluate(input(100.0, previous, step = 1))
        assertEquals(EvaluationOutcome.SETTLED, out.outcome)
        assertEquals(125.0, out.smoothedLux, tol)
        assertTrue(out.inBand())
    }

    @Test
    fun theLastAllowedStep_placesEvenWhileProgressing() {
        val previous = PreviousState(smoothedLux = 5_000.0, threshDynamicPercent = 20.0)
        val progressing = engine.evaluate(input(100.0, previous, step = BrightnessEngine.MAX_SETTLING_STEPS - 1))
        assertEquals(EvaluationOutcome.SMOOTHED, progressing.outcome)
        val last = engine.evaluate(input(100.0, previous, step = BrightnessEngine.MAX_SETTLING_STEPS))
        assertEquals(EvaluationOutcome.SETTLED, last.outcome)
        assertTrue(last.inBand())
        assertEquals(last.thresholdHigh, last.smoothedLux, tol)
    }

    @Test
    fun aReadingTheSpecialCaseBandExcludes_isPartOfTheSettledRange() {
        val out = engine.evaluate(input(0.15, PreviousState(0.5, 1.0), step = 1))
        assertEquals(EvaluationOutcome.SETTLED, out.outcome)
        assertEquals(0.0 to 0.1, out.thresholdLow to out.thresholdHigh)
        assertEquals(0.15, out.smoothedLux, tol)
        assertEquals(1.0, out.luxAlpha, tol)
        val atTheReading = engine.evaluate(input(0.15, PreviousState(0.15, 1.0), step = 1))
        assertEquals(EvaluationOutcome.DEAD_BAND_STOP, atTheReading.outcome, "already settled: nothing to place")
    }

    @Test
    fun aZeroThresholdAboveTenLux_isPartOfTheSettledRange() {
        val zero = ThresholdConfig(threshDark = 0.0, threshDim = 0.0, threshBright = 0.0)
        val settled = BrightnessPolicyInput(
            lux = 10.6, time = TimeContext(12 * 3600.0), thresholds = zero, curve = curve,
            previous = PreviousState(10.6, 0.0), settlingStep = 1,
        )
        val out = engine.evaluate(settled)
        assertEquals(11.0 to 11.0, out.thresholdLow to out.thresholdHigh)
        assertEquals(10.6, out.smoothedLux, tol, "no placement moves past or away from the reading")
    }

    @Test
    fun aPlacementJustOutsideAWideBand_keepsAPositiveAlpha() {
        val wide = ThresholdConfig(threshDark = 1.0, threshDim = 1.0, threshBright = 1.0)
        val out = engine.evaluate(
            BrightnessPolicyInput(
                lux = 100.0, time = TimeContext(12 * 3600.0), thresholds = wide, curve = curve,
                previous = PreviousState(200.01, 100.0), settlingStep = 1,
            ),
        )
        assertEquals(EvaluationOutcome.SETTLED, out.outcome)
        assertEquals(200.0, out.smoothedLux, tol)
        assertTrue(out.luxAlpha > 0.0 && out.luxAlpha <= 1.0, "α ${out.luxAlpha}")
    }

    @Test
    fun anAct19StopAtZero_landsOnTheSpecialCaseEdge() {
        val out = engine.evaluate(input(0.0, PreviousState(0.4, 30.0), step = 1))
        assertEquals(EvaluationOutcome.SETTLED, out.outcome, "0.4/1.4 < 0.299: act19 stops")
        assertEquals(0.0 to 0.1, out.thresholdLow to out.thresholdHigh, "act20's par1 is the 0 lx reading")
        assertEquals(0.1, out.smoothedLux, tol)
    }

    @Test
    fun aStepAlreadyInsideTheBand_isNotPlaced() {
        val previous = PreviousState(smoothedLux = 110.0, threshDynamicPercent = 25.0)
        val out = engine.evaluate(input(100.0, previous, step = 1))
        assertEquals(EvaluationOutcome.DEAD_BAND_STOP, out.outcome)
        assertEquals(110.0, out.smoothedLux, tol)
    }

    @Test
    fun withoutASettlingStep_theEngineIsUnchanged() {
        val previous = PreviousState(smoothedLux = 0.43, threshDynamicPercent = 30.0)
        val out = engine.evaluate(input(0.0, previous))
        assertEquals(EvaluationOutcome.SMOOTHED, out.outcome)
        assertTrue(abs(out.smoothedLux - 0.43) < 0.01, "smoothed ${out.smoothedLux}")
    }

    @Test
    fun proximityNear_settlesTheSameAndDampsOnlyTheReportedAlpha() {
        val far = settle(160.0, 0.0)
        val near = settle(160.0, 0.0, near = true)
        assertEquals(far.map { it.smoothedLux }, near.map { it.smoothedLux })
        assertEquals(far.map { it.targetBrightness }, near.map { it.targetBrightness })
        assertEquals(far.last().luxAlpha * BrightnessEngine.PROXIMITY_ALPHA_DAMP, near.last().luxAlpha, tol)
        assertEquals(far.last().animationSteps, near.last().animationSteps)
    }
}
