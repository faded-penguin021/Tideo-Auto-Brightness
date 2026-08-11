package com.tideo.autobrightness.domain.panic

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for [PanicShakeGate] vs task528 `_PanicButton` A2 Java leaky bucket.
 * Reference returns `should_stop`; gate succeeds iff false within 10s window (~500 readings at 50 Hz).
 * Segment: panic-overhaul port (D-116).
 */
class PanicShakeGateTest {

    /** A2 Java accumulation: clamp 0..10, sens 0 = immediate, 0.98 leak else 0.90 drain. Returns true to STOP. */
    private fun referenceShouldStop(sensitivityRaw: Int, mags: List<Double>): Boolean {
        var sensitivity = sensitivityRaw
        if (sensitivity < 0) sensitivity = 0
        if (sensitivity > 10) sensitivity = 10
        if (sensitivity == 0) return false // pass through immediately, do not stop

        val targetScore = sensitivity * 40.0
        val threshold = sensitivity * 2.0
        var score = 0.0
        for (mag in mags) {
            score = if (mag > threshold) score * 0.98 + (mag - threshold) else score * 0.90
            if (score >= targetScore) return false // reached target → proceed
        }
        return true // 10 s elapsed without reaching target → veto
    }

    /** Run the gate over [mags] and return whether it completed (success) within the window. */
    private fun gateCompletes(sensitivity: Int, mags: List<Double>): Boolean {
        val gate = PanicShakeGate(sensitivity)
        if (gate.isPassThrough) return true
        for (mag in mags) if (gate.onSample(mag)) return true
        return false
    }

    private fun assertMatchesReference(sensitivity: Int, mags: List<Double>, tag: String) {
        val refProceed = !referenceShouldStop(sensitivity, mags)
        assertEquals(refProceed, gateCompletes(sensitivity, mags), "gate vs A2 reference: $tag")
    }

    @Test
    fun passThrough_sensitivityZero_firesImmediately() {
        val gate = PanicShakeGate(0)
        assertTrue(gate.isPassThrough)
        assertTrue(gate.onSample(0.0), "sens 0 must complete on the first sample")
        assertMatchesReference(0, List(500) { 0.0 }, "sens 0 still / no shake")
        assertMatchesReference(0, List(500) { 30.0 }, "sens 0 vigorous shake")
    }

    @Test
    fun clampsSensitivityLikeJava() {
        val vigorous = List(500) { 40.0 }
        assertMatchesReference(11, vigorous, "sens 11 → clamp 10")
        assertMatchesReference(-3, vigorous, "sens -3 → clamp 0")
        assertTrue(PanicShakeGate(-3).isPassThrough, "negative sensitivity is pass-through")
        assertFalse(PanicShakeGate(11).isPassThrough, "sens 11 clamps to 10, not pass-through")
    }

    @Test
    fun sustainedVigorousShake_completes() {
        // 40 m/s² > threshold (20 at sens 10), beats 0.98 decay.
        for (sens in 1..10) {
            assertMatchesReference(sens, List(500) { 40.0 }, "sustained 40 m/s² at sens $sens")
            assertTrue(gateCompletes(sens, List(500) { 40.0 }), "sens $sens should pass a 40 m/s² shake")
        }
    }

    @Test
    fun belowThreshold_neverCompletes_timesOut() {
        // Below threshold: bucket drains, timeout guaranteed.
        for (sens in 1..10) {
            val belowThreshold = List(500) { (sens * 2.0) - 0.5 } // strictly < threshold
            assertMatchesReference(sens, belowThreshold, "below-threshold at sens $sens")
            assertFalse(gateCompletes(sens, belowThreshold), "sens $sens must time out below threshold")
        }
    }

    @Test
    fun marginalShake_cannotOutpaceDecay_atHighSensitivity() {
        // Sens 10: threshold 20, target 400; marginal 22 can't accumulate against 0.98 leak.
        val marginal = List(500) { 22.0 }
        assertMatchesReference(10, marginal, "marginal 22 m/s² at sens 10")
        assertFalse(gateCompletes(10, marginal), "marginal shake should not pass at sens 10")
    }

    @Test
    fun matchesReferenceAcrossRandomisedTraces() {
        // Deterministic fuzz: many sensitivities × varied traces; gate agrees with A2 on all outcomes.
        val rng = Random(seed = 0xABL)
        repeat(400) { i ->
            val sens = rng.nextInt(0, 12) // include out-of-range to exercise clamping
            val n = rng.nextInt(50, 520)
            val base = rng.nextDouble(0.0, 30.0)
            val mags = List(n) {
                val noise = rng.nextDouble(-base, base + 15.0)
                (base + noise).coerceAtLeast(0.0)
            }
            assertMatchesReference(sens, mags, "random trace #$i sens=$sens n=$n base=${"%.1f".format(base)}")
        }
    }
}
