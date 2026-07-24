package com.tideo.autobrightness.domain.parity

import com.tideo.autobrightness.domain.brightness.AnimationConfig
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import com.tideo.autobrightness.domain.reference.TaskerReference
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Differential sweep (DA-015): the production engine vs [TaskerReference] evaluated LIVE,
 * side-by-side, over a seeded pseudo-random input sweep — generated golden coverage between
 * the hand-picked grid points of the committed CSVs. Extends the live-differential precedent
 * of [CorePipelineParityTest.mapping661VsPlot663_agree] from one pairing to the core pipeline.
 *
 * DETERMINISTIC by construction: fixed [SEED], so every run evaluates the identical case set —
 * a red run is always reproducible (the repo's guards are deterministic; a flaky rung gets
 * disabled, not fixed). Honest scope: [TaskerReference] is a transcription sharing provenance
 * with the port, so agreement here rules out divergence introduced by the MODERNIZATION
 * (rounding, branch edges, zone boundaries — the D-030/D-034 bug classes), not extraction
 * error; extraction stays covered by the XML recipes + parity_gaps flow.
 *
 * A mismatch is a parity finding: triage into docs/rebuild/parity_gaps.md and re-derive from
 * the XML (D-002) — never edit the reference, never silence the case.
 */
class DifferentialSweepParityTest {

    private val engine = BrightnessEngine()
    private val tol = 1e-9

    private companion object {
        const val SEED = 20260722L
        const val CASES = 4000
    }

    /** Log-uniform sample in [lo, hi] — matches the golden grid's log spacing. */
    private fun Random.logUniform(lo: Double, hi: Double): Double =
        exp(ln(lo) + nextDouble() * (ln(hi) - ln(lo)))

    /** A random but VALID settings variant (the GoldenVectorGenerator.Variant ranges, continuous). */
    private data class SweepVariant(
        val form1a: Double, val form2b: Double, val form2c: Double,
        val zone1End: Double, val zone2End: Double,
        val minBright: Double, val maxBright: Double,
        val offset: Double, val scale: Double,
        val taperMidpoint: Double, val taperSteepness: Double,
        val threshDark: Double, val threshDim: Double, val threshBright: Double,
        val threshSteepness: Double, val threshMidpoint: Double, val deltaFactor: Double,
    ) {
        val form2d: Double get() = zone1End // D-008/D-025: Form2D ≡ Zone1End
        val continuity = TaskerReference.deriveContinuityCoefficients(
            form1a, form2b, form2c, zone1End, zone2End, maxBright,
        )
    }

    private fun Random.sweepVariant(): SweepVariant {
        // min/max brightness are INTEGRAL settings (the config holds them as Int — Android
        // brightness); fractional values are outside the modeled input domain and produce
        // spurious engine-vs-reference clamping diffs, not parity findings.
        val maxB = (200 + nextInt(56)).toDouble()
        // ~10% of variants pin min == max (the minEqMaxBright golden edge).
        val minB = if (nextDouble() < 0.1) maxB else (1 + nextInt(maxB.toInt())).toDouble()
        val z1 = 30.0 + nextDouble() * 20.0
        return SweepVariant(
            form1a = 3.0 + nextDouble() * 4.0,
            form2b = 6.0 + nextDouble() * 5.0,
            form2c = 10.0 + nextDouble() * 15.0,
            zone1End = z1,
            zone2End = 8_000.0 + nextDouble() * 2_000.0,
            minBright = minB,
            maxBright = maxB,
            offset = nextDouble() * 7.0,
            scale = 0.5 + nextDouble() * 1.5,
            taperMidpoint = 150.0 + nextDouble() * 70.0,
            taperSteepness = 0.05 + nextDouble() * 0.05,
            threshDark = 0.1 + nextDouble() * 0.4,
            threshDim = 0.05 + nextDouble() * 0.35,
            threshBright = 0.01 + nextDouble() * 0.19,
            threshSteepness = 1.0 + nextDouble() * 3.0,
            threshMidpoint = 3.0 + nextDouble() * 2.0,
            deltaFactor = 1.0 + nextDouble() * 2.0,
        )
    }

    private fun SweepVariant.curveConfig(scalingUse: Boolean = false): BrightnessCurveConfig =
        BrightnessCurveConfig(
            form1A = form1a, form2A = continuity.form2a, form2B = form2b, form2C = form2c,
            zone1End = zone1End, zone2End = zone2End, form3A = continuity.form3a,
            minBrightness = minBright.toInt(), maxBrightness = maxBright.toInt(),
            offset = offset, scale = scale, scalingUse = scalingUse,
            taperMidpoint = taperMidpoint, taperSteepness = taperSteepness,
        )

    private fun reportMismatches(name: String, mismatches: List<String>) {
        if (mismatches.isNotEmpty()) {
            fail(
                "differential sweep '$name' (seed=$SEED): engine and TaskerReference diverge in " +
                    "${mismatches.size}/$CASES cases — a parity finding: triage into parity_gaps.md " +
                    "and re-derive from XML (D-002), never edit the reference. First: ${mismatches.first()}",
            )
        }
    }

    // ---- smoothing (task535) -------------------------------------------------------------
    @Test
    fun sweep_smoothing() {
        val rng = Random(SEED)
        val mismatches = mutableListOf<String>()
        repeat(CASES) {
            val v = rng.sweepVariant()
            val prev = if (rng.nextDouble() < 0.02) 0.0 else rng.logUniform(0.01, 120_000.0)
            val par1 = prev * rng.logUniform(0.1, 10.0)
            val tp = 0.5 + rng.nextDouble() * 14.5
            val ref = TaskerReference.luxSmoothing(par1, prev, tp, v.deltaFactor, v.zone1End)
            val (smoothed, alpha) = engine.smoothLux(par1, prev, tp, v.deltaFactor, v.zone1End)
            if (abs(smoothed - ref.smoothedLux) > tol || abs(alpha - ref.luxAlpha) > tol) {
                mismatches += "par1=$par1 par2=$prev tp=$tp engine=($smoothed,$alpha) ref=(${ref.smoothedLux},${ref.luxAlpha})"
            }
        }
        reportMismatches("smoothing", mismatches)
    }

    // ---- dynamic + absolute thresholds (task544/task546) ---------------------------------
    @Test
    fun sweep_thresholds() {
        val rng = Random(SEED)
        val mismatches = mutableListOf<String>()
        repeat(CASES) {
            val v = rng.sweepVariant()
            val lux = if (rng.nextDouble() < 0.02) 0.0 else rng.logUniform(0.01, 120_000.0)
            val smoothed = lux * rng.logUniform(0.5, 2.0)
            val cfg = ThresholdConfig(
                threshDark = v.threshDark, threshDim = v.threshDim, threshBright = v.threshBright,
                threshSteepness = v.threshSteepness, threshMidpoint = v.threshMidpoint, zone1End = v.zone1End,
            )
            val refLc = TaskerReference.evaluateLightChange(
                lux, smoothed, v.threshDim, v.threshBright, v.threshSteepness,
                v.threshMidpoint, v.threshDark, v.zone1End,
            )
            val dyn = engine.dynamicThreshold(lux, smoothed, cfg)
            if (abs(dyn - refLc.dynamicThreshold) > tol) {
                mismatches += "dynThresh lux=$lux smoothed=$smoothed engine=$dyn ref=${refLc.dynamicThreshold}"
                return@repeat
            }
            // Reference outputs are Tasker-style STRINGS (task546 stores strings); parse like
            // the golden CSV path does.
            val refAbs = TaskerReference.setThresholds(lux, refLc.dynamicThreshold, lux)
            val (low, high) = engine.absoluteThresholds(lux, lux, dyn)
            if (abs(low - refAbs.threshAbsLow.toDouble()) > tol ||
                abs(high - refAbs.threshAbsHigh.toDouble()) > tol
            ) {
                mismatches += "absThresh lux=$lux engine=($low,$high) ref=(${refAbs.threshAbsLow},${refAbs.threshAbsHigh})"
            }
        }
        reportMismatches("thresholds", mismatches)
    }

    // ---- full calculated_brightness path, both branches (task661 act10-21) ----------------
    @Test
    fun sweep_calculatedBrightness() {
        val rng = Random(SEED)
        val mismatches = mutableListOf<String>()
        repeat(CASES) {
            val v = rng.sweepVariant()
            val lux = if (rng.nextDouble() < 0.02) 0.0 else rng.logUniform(0.01, 120_000.0)
            val su = rng.nextBoolean()
            val sd = 0.5 + rng.nextDouble() * 1.5
            val ref = TaskerReference.calculatedBrightness(
                lux, v.form1a, v.continuity.form2a, v.form2b, v.form2c, v.form2d, v.zone1End,
                v.zone2End, v.continuity.form3a, v.minBright, v.maxBright, v.offset, v.scale,
                su, sd, v.taperMidpoint, v.taperSteepness,
            )
            val calc = engine.calculatedBrightness(lux, v.curveConfig(scalingUse = su), sd)
            if (abs(calc - ref) > tol) {
                mismatches += "lux=$lux su=$su sd=$sd engine=$calc ref=$ref"
            }
        }
        reportMismatches("calculatedBrightness", mismatches)
    }

    // ---- animation (task543) -------------------------------------------------------------
    @Test
    fun sweep_animation() {
        val rng = Random(SEED)
        val mismatches = mutableListOf<String>()
        repeat(CASES) {
            val alpha = -0.5 + rng.nextDouble() * 2.0
            val steps = 5 + rng.nextInt(56)
            val minWait = 1L + rng.nextLong(30)
            val maxWait = minWait + rng.nextLong(90)
            val cycle = if (rng.nextBoolean()) null else 300.0 + rng.nextDouble() * 2_700.0
            val ref = TaskerReference.calculateAnimation(
                alpha, steps.toDouble(), minWait.toDouble(), maxWait.toDouble(), cycle,
            )
            val cfg = AnimationConfig(maxSteps = steps, minWaitMs = minWait, maxWaitMs = maxWait)
            val (engSteps, engWait, engThrottle) = engine.calculateAnimation(alpha, cfg, cycle)
            if (engSteps.toLong() != ref.loops || engWait != ref.wait || engThrottle != ref.throttle) {
                mismatches += "alpha=$alpha steps=$steps engine=($engSteps,$engWait,$engThrottle) ref=(${ref.loops},${ref.wait},${ref.throttle})"
            }
        }
        reportMismatches("animation", mismatches)
    }

    // ---- continuity coefficients (task659) -----------------------------------------------
    @Test
    fun sweep_continuityCoefficients() {
        val rng = Random(SEED)
        val mismatches = mutableListOf<String>()
        repeat(CASES) {
            val v = rng.sweepVariant()
            val prod = BrightnessFormulae.deriveContinuityCoefficients(
                v.form1a, v.form2b, v.form2c, v.zone1End, v.zone2End, v.maxBright,
            )
            if (abs(prod.form2A - v.continuity.form2a) > tol || abs(prod.form3A - v.continuity.form3a) > tol) {
                mismatches += "f1=${v.form1a} f2b=${v.form2b} prod=(${prod.form2A},${prod.form3A}) ref=(${v.continuity.form2a},${v.continuity.form3a})"
            }
        }
        reportMismatches("continuity", mismatches)
    }
}
