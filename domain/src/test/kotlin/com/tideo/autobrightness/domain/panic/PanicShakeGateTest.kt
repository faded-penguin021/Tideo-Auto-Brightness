package com.tideo.autobrightness.domain.panic

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for [PanicShakeGate] against a faithful transcription of the task528 `_PanicButton`
 * A2 "Java Code" leaky bucket (`tmp/Panic_profile_task.md` A2). The reference returns Tasker's
 * `should_stop`; the gate succeeds iff `should_stop == "false"` within the readings of the 10 s window.
 *
 * The platform source feeds ~50 Hz samples (SENSOR_DELAY_GAME), so a 10 s window is ≈500 readings —
 * the sequences below use that order of magnitude. Segment: panic-overhaul port (D-116).
 */
class PanicShakeGateTest {

    /**
     * Faithful port of the A2 Java accumulation (clamp 0..10; sens 0 ⇒ proceed immediately; per
     * reading `mag > threshold` adds `mag − threshold` under a 0.98 leak else drains 0.90; success at
     * `score ≥ targetScore`; no success within the window ⇒ timeout ⇒ stop). Returns true to STOP
     * (veto), matching the Java's `should_stop`.
     */
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
        // Reference agrees regardless of the (irrelevant) samples.
        assertMatchesReference(0, List(500) { 0.0 }, "sens 0 still / no shake")
        assertMatchesReference(0, List(500) { 30.0 }, "sens 0 vigorous shake")
    }

    @Test
    fun clampsSensitivityLikeJava() {
        // 11 clamps to 10, -3 clamps to 0 (pass-through), both must match the reference.
        val vigorous = List(500) { 40.0 }
        assertMatchesReference(11, vigorous, "sens 11 → clamp 10")
        assertMatchesReference(-3, vigorous, "sens -3 → clamp 0")
        assertTrue(PanicShakeGate(-3).isPassThrough, "negative sensitivity is pass-through")
        assertFalse(PanicShakeGate(11).isPassThrough, "sens 11 clamps to 10, not pass-through")
    }

    @Test
    fun sustainedVigorousShake_completes() {
        // Well above threshold even at sens 10 (threshold 20): 40 m/s² beats the 0.98 decay.
        for (sens in 1..10) {
            assertMatchesReference(sens, List(500) { 40.0 }, "sustained 40 m/s² at sens $sens")
            assertTrue(gateCompletes(sens, List(500) { 40.0 }), "sens $sens should pass a 40 m/s² shake")
        }
    }

    @Test
    fun belowThreshold_neverCompletes_timesOut() {
        // Magnitudes at/under threshold earn no points and the bucket drains → guaranteed timeout.
        for (sens in 1..10) {
            val belowThreshold = List(500) { (sens * 2.0) - 0.5 } // strictly < threshold
            assertMatchesReference(sens, belowThreshold, "below-threshold at sens $sens")
            assertFalse(gateCompletes(sens, belowThreshold), "sens $sens must time out below threshold")
        }
    }

    @Test
    fun marginalShake_cannotOutpaceDecay_atHighSensitivity() {
        // At sens 10 the threshold is 20 and target 400; a shake only slightly over threshold can't
        // accumulate fast enough against the 0.98 leak — must veto (the Java's design intent).
        val marginal = List(500) { 22.0 } // 2 over threshold each reading
        assertMatchesReference(10, marginal, "marginal 22 m/s² at sens 10")
        assertFalse(gateCompletes(10, marginal), "marginal shake should not pass at sens 10")
    }

    @Test
    fun matchesReferenceAcrossRandomisedTraces() {
        // Deterministic fuzz: many sensitivities × varied shake traces (bursts, ramps, noise) — the
        // gate must agree with the A2 transcription on every trace, success or timeout.
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
