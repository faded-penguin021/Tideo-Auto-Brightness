package com.tideo.autobrightness.domain.parity

import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.BrightnessPolicyInput
import com.tideo.autobrightness.domain.brightness.BrightnessPolicyOutput
import com.tideo.autobrightness.domain.brightness.EvaluationOutcome
import com.tideo.autobrightness.domain.brightness.PreviousState
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import com.tideo.autobrightness.domain.brightness.TimeContext
import com.tideo.autobrightness.domain.reference.TaskerReference
import com.tideo.autobrightness.domain.reference.TaskerReference.LightCycleOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** task554 act1 → task544 act10–act35 against the engine, one reading at a time, behind prof760's band. */
class LightCycleParityTest {

    private val engine = BrightnessEngine()
    private val cfg = ThresholdConfig()
    private val tol = 1e-9

    private fun input(lux: Double, previous: PreviousState?, near: Boolean = false) = BrightnessPolicyInput(
        lux = lux,
        time = TimeContext(secondsOfDay = 12 * 3600.0),
        thresholds = cfg,
        previous = previous,
        proximityNear = near,
    )

    private fun oracle(lux: Double, state: TaskerReference.LightCycleState, near: Boolean) = TaskerReference.lightCycle(
        par1 = lux,
        state = state,
        threshDim = cfg.threshDim,
        threshBright = cfg.threshBright,
        threshSteepness = cfg.threshSteepness,
        threshMidpoint = cfg.threshMidpoint,
        threshDark = cfg.threshDark,
        zone1End = cfg.zone1End,
        deltaFactor = cfg.deltaFactor,
        proximityNear = near,
    )

    private fun EvaluationOutcome.toOracle() = when (this) {
        EvaluationOutcome.FIRST_RUN -> LightCycleOutcome.FIRST_RUN
        EvaluationOutcome.DEAD_BAND_STOP -> LightCycleOutcome.DEAD_BAND_STOP
        EvaluationOutcome.SMOOTHED -> LightCycleOutcome.SMOOTHED
    }

    private fun carried(out: BrightnessPolicyOutput) = PreviousState(
        smoothedLux = out.smoothedLux,
        threshDynamicPercent = out.threshDynamicPercent,
    )

    private fun replay(readings: List<Double>, near: Boolean = false): List<Pair<Double, BrightnessPolicyOutput>> {
        val evaluated = mutableListOf<Pair<Double, BrightnessPolicyOutput>>()
        var previous: PreviousState? = null
        var band: Pair<Double, Double>? = null
        var state = TaskerReference.LightCycleState(null, null)
        val mismatches = mutableListOf<String>()
        for (lux in readings) {
            // prof760: strict < / > against the stored band; unseeded on the first reading.
            val stored = band
            if (stored != null && !(lux < stored.first || lux > stored.second)) continue
            val out = engine.evaluate(input(lux, previous, near))
            val ref = oracle(lux, state, near)
            val where = "reading $lux after ${evaluated.map { it.first }}"
            if (out.outcome.toOracle() != ref.outcome) mismatches += "$where: outcome ${out.outcome} vs ${ref.outcome}"
            if (kotlin.math.abs(out.smoothedLux - ref.smoothedLux) > tol) {
                mismatches += "$where: smoothed ${out.smoothedLux} vs ${ref.smoothedLux}"
            }
            ref.dynamicThreshold?.let { dt ->
                if (kotlin.math.abs(out.dynamicThreshold - dt) > tol) {
                    mismatches += "$where: dynamic threshold ${out.dynamicThreshold} vs $dt"
                }
            }
            if (ref.outcome == LightCycleOutcome.SMOOTHED && kotlin.math.abs(out.luxAlpha - ref.luxAlpha!!) > tol) {
                mismatches += "$where: alpha ${out.luxAlpha} vs ${ref.luxAlpha}"
            }
            val t = ref.thresholds
            if (kotlin.math.abs(out.thresholdLow - t.threshAbsLow.toDouble()) > tol ||
                kotlin.math.abs(out.thresholdHigh - t.threshAbsHigh.toDouble()) > tol ||
                kotlin.math.abs(out.threshDynamicPercent - t.threshDynamic.toDouble()) > tol
            ) {
                mismatches += "$where: band ${out.thresholdLow}–${out.thresholdHigh} " +
                    "(${out.threshDynamicPercent}%) vs ${t.threshAbsLow}–${t.threshAbsHigh} (${t.threshDynamic}%)"
            }
            if (kotlin.math.abs(out.lastRawLux - ref.lastRawLux.toDouble()) > tol) {
                mismatches += "$where: lastRawLux ${out.lastRawLux} vs ${ref.lastRawLux}"
            }
            state = ref.state
            previous = carried(out)
            band = out.thresholdLow to out.thresholdHigh
            evaluated += lux to out
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} divergence(s):\n" + mismatches.joinToString("\n"))
        return evaluated
    }

    @Test
    fun returnToThePreviousLevel_passesTheStoredBand() {
        val evaluated = replay(listOf(100.0, 30.0, 100.0)).map { it.first }
        assertEquals(listOf(100.0, 30.0, 100.0), evaluated)
    }

    @Test
    fun storedBand_isCentredOnTheRoundedProcessedReading() {
        val (_, out) = replay(listOf(100.0, 31.8)).last()
        assertTrue(31.8 in out.thresholdLow..out.thresholdHigh, "band ${out.thresholdLow}–${out.thresholdHigh}")
    }

    @Test
    fun belowTheDynamicThreshold_stopsWithoutSmoothing() {
        // 100 → 30 leaves smoothed ≈ 50, band ≈ 23–37; 45 clears the band, but 5/51 < ≈ 0.25 stops it.
        val evaluated = replay(listOf(100.0, 30.0, 45.0))
        val smoothedBefore = evaluated[1].second.smoothedLux
        val (lux, out) = evaluated.last()
        assertEquals(45.0, lux)
        assertEquals(EvaluationOutcome.DEAD_BAND_STOP, out.outcome)
        assertEquals(smoothedBefore, out.smoothedLux, tol)
    }

    @Test
    fun firstReading_initialisesAtZeroAndAboveZero() {
        for (lux in listOf(0.0, 42.0)) {
            val out = engine.evaluate(input(lux, previous = null))
            assertEquals(EvaluationOutcome.FIRST_RUN, out.outcome)
            assertEquals(lux, out.smoothedLux, tol)
            assertEquals(1.0, out.luxAlpha, tol)
            assertEquals(0.0, out.threshDynamicPercent, tol)
            assertEquals(lux, out.thresholdLow, tol)
            assertEquals(lux, out.thresholdHigh, tol)
        }
    }

    @Test
    fun sweeps_matchTheOracle() {
        val sequences = listOf(
            listOf(100.0, 30.0, 100.0, 30.0, 100.0),
            listOf(160.0, 0.0, 0.0, 0.0, 2.0),
            listOf(0.0, 0.1, 0.3, 5.0, 12.0, 11.5, 31.8, 0.0),
            listOf(20.0, 800.0, 790.0, 1_200.0, 40_000.0, 38_000.0, 3.0),
            listOf(34.0, 36.0, 34.9, 35.1, 70.0, 9.99, 10.0),
            (0..60).map { 200.0 + 40.0 * kotlin.math.sin(it / 3.0) },
        )
        for (seq in sequences) assertNotNull(replay(seq))
    }

    @Test
    fun proximityNear_smoothsAsFarAndDampsOnlyTheReportedAlpha() {
        // act27 stores %SmoothedLux from the undamped α; act29's ×0.1 reaches only the %LuxAlpha global (gap-08).
        val readings = listOf(100.0, 30.0, 100.0, 20.0, 800.0, 3.0)
        val far = replay(readings)
        val near = replay(readings, near = true)
        assertEquals(far.map { it.first }, near.map { it.first })
        for ((f, n) in far.map { it.second }.zip(near.map { it.second })) {
            assertEquals(f.smoothedLux, n.smoothedLux, tol)
            assertEquals(f.targetBrightness, n.targetBrightness)
            assertEquals(f.animationSteps, n.animationSteps)
            assertEquals(f.animationWaitMs, n.animationWaitMs)
            assertEquals(f.transitionDurationMs, n.transitionDurationMs)
        }
    }
}
