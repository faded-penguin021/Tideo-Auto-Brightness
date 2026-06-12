package com.tideo.autobrightness.domain.parity

import com.tideo.autobrightness.domain.brightness.AnimationConfig
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import com.tideo.autobrightness.domain.reference.GoldenVectorGenerator
import com.tideo.autobrightness.domain.reference.TaskerReference
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parity harness: asserts the CURRENT production [BrightnessEngine] (and helpers) against the
 * committed golden vectors generated from [TaskerReference] (the Tasker oracle).
 *
 * Numeric tolerance is 1e-9; integer/string outputs are exact. All 7 gaps documented in
 * docs/rebuild/parity_gaps.md were closed in S5 — no active Ignore annotations remain. The
 * golden vectors and reference implementation are immutable fixtures (never edit them to
 * make tests pass; fix production code instead).
 */
class CorePipelineParityTest {

    private val engine = BrightnessEngine()
    private val tol = 1e-9

    private fun golden(name: String): List<Map<String, String>> {
        val file = File("src/test/resources/golden/$name")
        assertTrue(file.exists(), "missing golden vector $name — run with -DregenGolden=1")
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(",")
        return lines.drop(1).map { line -> header.zip(line.split(",")).toMap() }
    }

    private fun Map<String, String>.d(k: String): Double = getValue(k).toDouble()

    // ---- smoothing (task535) -------------------------------------------------------------
    @Test
    fun smoothing_matchesEngine() {
        val mismatches = mutableListOf<String>()
        for (r in golden("smoothing.csv")) {
            val (smoothed, alpha) = engine.smoothLux(
                rawLux = r.d("par1"),
                previousSmoothedLux = r.d("par2"),
                thresholdDynamicPercent = r.d("threshDynamicPercent"),
                deltaFactor = r.d("deltaFactor"),
                zone1End = r.d("zone1End"),
            )
            if (abs(smoothed - r.d("smoothedLux")) > tol || abs(alpha - r.d("luxAlpha")) > tol) {
                mismatches += "par1=${r["par1"]} par2=${r["par2"]} engine=($smoothed,$alpha) ref=(${r["smoothedLux"]},${r["luxAlpha"]})"
            }
        }
        if (mismatches.isNotEmpty()) fail("smoothing diverges in ${mismatches.size} rows; e.g. ${mismatches.first()}")
    }

    // ---- dynamic threshold (task544) -----------------------------------------------------
    @Test
    fun dynamicThreshold_matchesEngine() {
        for (r in golden("threshold.csv")) {
            val cfg = ThresholdConfig(
                threshDark = r.d("threshDark"),
                threshDim = r.d("threshDim"),
                threshBright = r.d("threshBright"),
                threshSteepness = r.d("threshSteepness"),
                threshMidpoint = r.d("threshMidpoint"),
                zone1End = r.d("zone1End"),
            )
            val dyn = engine.dynamicThreshold(r.d("currentLux"), r.d("smoothedLux"), cfg)
            assertEquals(r.d("dynamicThreshold"), dyn, tol, "lux=${r["currentLux"]}")
        }
    }

    // ---- absolute thresholds (task546) ---------------------------------------------------
    @Test
    fun absoluteThresholds_matchesEngine() {
        val mismatches = mutableListOf<String>()
        for (r in golden("threshold.csv")) {
            // currentLux = par1 (for < 0.2 special-case and < 10 scale selector); lastRawLux separate
            val (low, high) = engine.absoluteThresholds(r.d("currentLux"), r.d("lastRawLux"), r.d("dynamicThreshold"))
            if (abs(low - r.d("threshAbsLow")) > tol || abs(high - r.d("threshAbsHigh")) > tol) {
                mismatches += "lux=${r["currentLux"]} engine=($low,$high) ref=(${r["threshAbsLow"]},${r["threshAbsHigh"]})"
            }
        }
        if (mismatches.isNotEmpty()) fail("absoluteThresholds diverges in ${mismatches.size} rows; e.g. ${mismatches.first()}")
    }

    // ---- lux→brightness mapping (task661) ------------------------------------------------
    @Test
    fun mapping_matchesEngine() {
        val mismatches = mutableListOf<String>()
        for (r in golden("mapping.csv")) {
            val cfg = BrightnessCurveConfig(
                form1A = r.d("form1a"),
                form2A = r.d("form2a"),
                form2B = r.d("form2b"),
                form2C = r.d("form2c"),
                zone1End = r.d("zone1End"),
                zone2End = r.d("zone2End"),
                form3A = r.d("form3a"),
                minBrightness = r.d("minBright").toInt(),
                maxBrightness = r.d("maxBright").toInt(),
            )
            val mapped = engine.mapLuxToBrightness(r.d("lux"), cfg)
            if (abs(mapped - r.d("mappedRaw")) > tol) {
                mismatches += "variant=${r["variant"]} lux=${r["lux"]} engine=$mapped ref=${r["mappedRaw"]}"
            }
        }
        if (mismatches.isNotEmpty()) fail("mapping diverges in ${mismatches.size} rows; e.g. ${mismatches.first()}")
    }

    // ---- compressed dynamic scale / taper (task548) --------------------------------------
    @Test
    fun taper_matchesEngine() {
        for (r in golden("taper.csv")) {
            val cfg = BrightnessCurveConfig(
                minBrightness = r.d("minBright").toInt(),
                maxBrightness = r.d("maxBright").toInt(),
                offset = r.d("offset"),
                taperMidpoint = r.d("taperMidpoint"),
                taperSteepness = r.d("taperSteepness"),
            )
            val calc = engine.compressedDynamicScale(r.d("mapped"), r.d("scaleDynamic"), cfg)
            assertEquals(r.d("calculatedBrightness"), calc, tol, "mapped=${r["mapped"]} scale=${r["scaleDynamic"]}")
        }
    }

    // ---- animation (task543) -------------------------------------------------------------
    @Test
    fun animation_matchesEngine() {
        for (r in golden("animation.csv")) {
            val cfg = AnimationConfig(
                maxSteps = r.d("animSteps").toInt(),
                minWaitMs = r.d("minWait").toLong(),
                maxWaitMs = r.d("maxWait").toLong(),
            )
            val cycle = r["cycleTime"]?.takeIf { it.isNotBlank() }?.toDouble()
            val (steps, wait, throttle) = engine.calculateAnimation(r.d("alpha"), cfg, cycle)
            assertEquals(r.getValue("loops").toLong(), steps.toLong(), "alpha=${r["alpha"]} loops")
            assertEquals(r.getValue("wait").toLong(), wait, "alpha=${r["alpha"]} wait")
            assertEquals(r.getValue("throttle").toLong(), throttle, "alpha=${r["alpha"]} throttle")
        }
    }

    // ---- continuity coefficients production code (task659) --------------------------------
    @Test
    fun formulae_productionMatchesOracle() {
        for (r in golden("formulae.csv")) {
            val c = BrightnessFormulae.deriveContinuityCoefficients(
                r.d("form1a"), r.d("form2b"), r.d("form2c"), r.d("zone1End"), r.d("zone2End"), r.d("maxBright"),
            )
            assertEquals(r.d("form2a"), c.form2A, tol, "form2A variant=${r["variant"]}")
            assertEquals(r.d("form3a"), c.form3A, tol, "form3A variant=${r["variant"]}")
        }
    }

    // ---- oracle self-consistency (no production counterpart yet — S5/S6) -----------------
    @Test
    fun formulae_oracleIsStable() {
        for (r in golden("formulae.csv")) {
            val c = TaskerReference.deriveContinuityCoefficients(
                r.d("form1a"), r.d("form2b"), r.d("form2c"), r.d("zone1End"), r.d("zone2End"), r.d("maxBright"),
            )
            assertEquals(r.d("form2a"), c.form2a, tol)
            assertEquals(r.d("form3a"), c.form3a, tol)
        }
    }

    /**
     * Cross-validate task661 (runtime) vs task663 (plot-side copy) of the 3-zone mapping over the
     * golden lux grid (D-002/D-027c). They must agree because Form2D ≡ Zone1End; any disagreement
     * would be recorded in parity_gaps.md and re-derived from XML — never guessed.
     */
    @Test
    fun mapping661VsPlot663_agree() {
        for (v in GoldenVectorGenerator.variants) {
            for (lux in GoldenVectorGenerator.luxGrid()) {
                val viaTask661 = TaskerReference.mappedBrightness(
                    lux, v.form1a, v.form2a, v.form2b, v.form2c, v.form2d, v.zone1End, v.zone2End, v.form3a, v.maxBright,
                )
                val viaPlot663 = plot663Mapping(lux, v)
                assertEquals(viaPlot663, viaTask661, tol, "variant=${v.name} lux=$lux")
            }
        }
    }

    /** task663 block#2 zone math (verbatim), un-clamped, to mirror task661 for cross-validation. */
    private fun plot663Mapping(lux: Double, v: GoldenVectorGenerator.Variant): Double = when {
        lux < v.zone1End -> v.form1a * Math.sqrt(lux)
        lux < v.zone2End -> v.form2a + v.form2b * (Math.pow(lux - v.form2c, 0.33) - Math.pow(v.zone1End - v.form2c, 0.33))
        else -> v.maxBright - (v.form3a / lux) * v.maxBright
    }

    // ---- software dimming: finalDimLevel (task700) -----------------------------------------
    @Test
    fun softwareDimming_finalDimLevel_matchesOracle() {
        for (r in golden("superdimming.csv")) {
            val fd = SoftwareDimming.finalDimLevel(
                targetBrightness = r.d("targetBrightness"),
                isElevated = r["isElevated"] == "true",
                dimmingThreshold = r.d("dimmingThreshold"),
                pwmExp = r.d("pwmExp"),
            )
            assertEquals(r.d("finalDim"), fd, tol, "tb=${r["targetBrightness"]} elev=${r["isElevated"]}")
        }
    }

    // ---- super dimming shell (task646/647) -------------------------------------------------
    @Test
    fun softwareDimming_dimShell_matchesOracle() {
        val mismatches = mutableListOf<String>()
        for (r in golden("superdimming.csv")) {
            val scalingUse = r["scalingUse"] == "true"
            val dimDynamic = if (scalingUse) r.d("dimDynamic") else null
            val shell = SoftwareDimming.dimShell(
                brightness = r.d("targetBrightness"),
                minBrightness = r.d("minBright"),
                dimmingThreshold = r.d("dimmingThreshold"),
                dimmingExponent = r.d("dimmingExponent"),
                dimmingStrength = r.d("dimmingStrength"),
                dimDynamic = dimDynamic,
            )
            if (abs(shell - r.d("dimShell")) > tol) {
                mismatches += "tb=${r["targetBrightness"]} su=${r["scalingUse"]} engine=$shell ref=${r["dimShell"]}"
            }
        }
        if (mismatches.isNotEmpty()) fail("dimShell diverges in ${mismatches.size} rows; e.g. ${mismatches.first()}")
    }

    @Test
    fun dimming_oracleIsStable() {
        for (r in golden("dimming.csv")) {
            val f = TaskerReference.dcTransitionFrame(
                r.d("start"), r.d("target"), r.d("dimStart"), r.d("finalDim"),
                r.getValue("counter").toInt(), r.getValue("loops").toInt(), r.d("dimmingThreshold"),
            )
            assertEquals(r.getValue("hardwareTarget").toInt(), f.hardwareTarget)
            assertEquals(r.getValue("dimVal").toInt(), f.dimVal)
            assertEquals(r.d("calculatedBright"), f.calculatedBright, tol)
        }
    }

    @Test
    fun transition_oracleIsStable() {
        for (r in golden("transition.csv")) {
            val b = TaskerReference.transitionFrameBrightness(
                r.d("start"), r.d("target"), r.getValue("counter").toInt(), r.getValue("loops").toInt(),
            )
            assertEquals(r.getValue("brightnessInt").toInt(), b)
        }
    }
}
