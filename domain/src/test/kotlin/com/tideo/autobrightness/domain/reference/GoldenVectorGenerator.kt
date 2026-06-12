package com.tideo.autobrightness.domain.reference

import java.io.File
import kotlin.math.ln
import kotlin.test.Test

/**
 * Generates the committed golden CSV fixtures from [TaskerReference].
 *
 * The CSVs in `domain/src/test/resources/golden/` are the immutable parity oracle. To regenerate
 * them (only after an evidence-backed reference change, logged in STATE.md):
 *
 *     ./gradlew :domain:test -DregenGolden=1 --tests "*GoldenVectorGenerator*"
 *
 * then commit the changed CSVs. The [regenerateGoldenVectors] test is a no-op unless the
 * `regenGolden` system property is set, so normal `:domain:test` runs never touch the fixtures.
 *
 * Grid: log-spaced lux 0.01 → 120000 plus explicit boundary rows, crossed with ≥4 settings
 * variants (defaults + edge variants). Every CSV has ≥500 rows. Segment: S4.
 */
object GoldenVectorGenerator {

    /** A full curve/threshold settings variant; derived continuity coefficients via task659. */
    data class Variant(
        val name: String,
        val form1a: Double = 5.0,
        val form2b: Double = 8.8,
        val form2c: Double = 18.0,
        val zone1End: Double = 35.0,
        val zone2End: Double = 10_000.0,
        val minBright: Double = 10.0,
        val maxBright: Double = 255.0,
        val offset: Double = 0.0,
        val scale: Double = 1.0,
        val taperMidpoint: Double = 190.0,
        val taperSteepness: Double = 0.075,
        // thresholds
        val threshDark: Double = 0.3,
        val threshDim: Double = 0.25,
        val threshBright: Double = 0.08,
        val threshSteepness: Double = 2.1,
        val threshMidpoint: Double = 4.0,
        val deltaFactor: Double = 1.8,
    ) {
        val form2d: Double get() = zone1End // D-008/D-025: Form2D ≡ Zone1End (derived)
        val continuity get() = TaskerReference.deriveContinuityCoefficients(form1a, form2b, form2c, zone1End, zone2End, maxBright)
        val form2a: Double get() = continuity.form2a
        val form3a: Double get() = continuity.form3a
    }

    /** Canonical Tasker defaults (task570) + edge variants per the S4 brief. */
    val variants: List<Variant> = listOf(
        Variant("default"),
        Variant("minEqMaxBright", minBright = 128.0, maxBright = 128.0),
        Variant("zoneShift", zone1End = 50.0, zone2End = 8_000.0, form2c = 12.0),
        Variant("offsetScale", offset = 7.0, scale = 1.4, threshMidpoint = 3.5, deltaFactor = 2.5),
    )

    /** Log-spaced lux grid (0.01 → 120000) with explicit boundary rows. */
    fun luxGrid(): List<Double> {
        val pts = mutableListOf<Double>()
        val n = 160
        val lo = 0.01
        val hi = 120_000.0
        for (i in 0..n) {
            val frac = i.toDouble() / n
            pts.add(Math.round(Math.exp(ln(lo) + frac * (ln(hi) - ln(lo))) * 1000.0) / 1000.0)
        }
        // Explicit boundaries: 0, zone ends across variants, sensor max.
        pts.addAll(listOf(0.0, 35.0, 50.0, 8_000.0, 10_000.0, 120_000.0, 0.1, 0.19, 0.2, 9.99, 10.0))
        return pts
    }

    // ---- CSV writers ---------------------------------------------------------------------

    private fun fmt(v: Double): String = v.toBigDecimal().toPlainString()

    fun writeMapping(dir: File) {
        val rows = StringBuilder("variant,lux,form1a,form2a,form2b,form2c,form2d,zone1End,zone2End,form3a,minBright,maxBright,mappedRaw\n")
        for (v in variants) for (lux in luxGrid()) {
            val mapped = TaskerReference.mappedBrightness(
                lux, v.form1a, v.form2a, v.form2b, v.form2c, v.form2d, v.zone1End, v.zone2End, v.form3a, v.maxBright,
            )
            rows.append("${v.name},${fmt(lux)},${fmt(v.form1a)},${fmt(v.form2a)},${fmt(v.form2b)},${fmt(v.form2c)},${fmt(v.form2d)},${fmt(v.zone1End)},${fmt(v.zone2End)},${fmt(v.form3a)},${fmt(v.minBright)},${fmt(v.maxBright)},${fmt(mapped)}\n")
        }
        File(dir, "mapping.csv").writeText(rows.toString())
    }

    fun writeFormulae(dir: File) {
        val rows = StringBuilder("variant,form1a,form2b,form2c,zone1End,zone2End,maxBright,form2a,form3a\n")
        // ≥500 rows: sweep parameter combinations.
        val form1aGrid = listOf(3.0, 4.0, 5.0, 6.0, 7.0)
        val form2bGrid = listOf(6.0, 8.8, 11.0)
        val form2cGrid = listOf(10.0, 18.0, 25.0)
        val zone1Grid = listOf(30.0, 35.0, 50.0)
        val zone2Grid = listOf(8_000.0, 10_000.0)
        val maxGrid = listOf(200.0, 255.0)
        for (f1 in form1aGrid) for (f2b in form2bGrid) for (f2c in form2cGrid) for (z1 in zone1Grid) for (z2 in zone2Grid) for (mx in maxGrid) {
            val c = TaskerReference.deriveContinuityCoefficients(f1, f2b, f2c, z1, z2, mx)
            rows.append("sweep,${fmt(f1)},${fmt(f2b)},${fmt(f2c)},${fmt(z1)},${fmt(z2)},${fmt(mx)},${fmt(c.form2a)},${fmt(c.form3a)}\n")
        }
        File(dir, "formulae.csv").writeText(rows.toString())
    }

    fun writeSmoothing(dir: File) {
        val rows = StringBuilder("variant,par1,par2,threshDynamicPercent,deltaFactor,zone1End,smoothedLux,luxAlpha,luxDelta\n")
        val prevGrid = luxGrid().filter { it >= 0.0 }
        // Ratios chosen to exercise small deltas (negative effective-delta) and large spikes.
        val ratios = listOf(1.0, 1.005, 0.995, 1.05, 0.5, 2.0, 0.1, 10.0)
        val threshPercents = listOf(1.0, 5.0, 10.0)
        for (v in variants) for (prev in prevGrid) for (r in ratios) for (tp in threshPercents) {
            val par1 = prev * r
            val res = TaskerReference.luxSmoothing(par1, prev, tp, v.deltaFactor, v.zone1End)
            rows.append("${v.name},${fmt(par1)},${fmt(prev)},${fmt(tp)},${fmt(v.deltaFactor)},${fmt(v.zone1End)},${fmt(res.smoothedLux)},${fmt(res.luxAlpha)},${fmt(res.luxDelta)}\n")
        }
        File(dir, "smoothing.csv").writeText(rows.toString())
    }

    fun writeThreshold(dir: File) {
        val rows = StringBuilder("variant,currentLux,smoothedLux,lastRawLux,threshDark,threshDim,threshBright,threshSteepness,threshMidpoint,zone1End,relativeChange,dynamicThreshold,threshDynamicStr,threshAbsLow,threshAbsHigh\n")
        for (v in variants) for (lux in luxGrid()) {
            val smoothed = lux
            val lc = TaskerReference.evaluateLightChange(
                lux, smoothed, v.threshDim, v.threshBright, v.threshSteepness, v.threshMidpoint, v.threshDark, v.zone1End,
            )
            val abs = TaskerReference.setThresholds(lux, lc.dynamicThreshold, lux)
            rows.append("${v.name},${fmt(lux)},${fmt(smoothed)},${fmt(lux)},${fmt(v.threshDark)},${fmt(v.threshDim)},${fmt(v.threshBright)},${fmt(v.threshSteepness)},${fmt(v.threshMidpoint)},${fmt(v.zone1End)},${fmt(lc.relativeChange)},${fmt(lc.dynamicThreshold)},${abs.threshDynamic},${abs.threshAbsLow},${abs.threshAbsHigh}\n")
        }
        File(dir, "threshold.csv").writeText(rows.toString())
    }

    fun writeTaper(dir: File) {
        val rows = StringBuilder("variant,mapped,scaleDynamic,minBright,maxBright,offset,taperMidpoint,taperSteepness,calculatedBrightness,effectiveScale\n")
        val mappedGrid = (0..40).map { it * 6.5 } // 0 .. 260
        val scaleGrid = listOf(0.5, 0.7, 0.9, 1.0, 1.1, 1.4, 2.0)
        for (v in variants) for (m in mappedGrid) for (s in scaleGrid) {
            val res = TaskerReference.dynamicCompressedScale(m, v.minBright, s, v.maxBright, v.offset, v.taperMidpoint, v.taperSteepness)
            rows.append("${v.name},${fmt(m)},${fmt(s)},${fmt(v.minBright)},${fmt(v.maxBright)},${fmt(v.offset)},${fmt(v.taperMidpoint)},${fmt(v.taperSteepness)},${fmt(res.calculatedBrightness)},${fmt(res.effectiveScale)}\n")
        }
        File(dir, "taper.csv").writeText(rows.toString())
    }

    fun writeAnimation(dir: File) {
        val rows = StringBuilder("variant,alpha,animSteps,minWait,maxWait,cycleTime,loops,wait,throttle\n")
        val alphaGrid = (0..100).map { it / 100.0 } + listOf(-0.3, 1.3)
        val settings = listOf(
            Triple(20.0, 25.0, 65.0),
            Triple(50.0, 5.0, 30.0),
            Triple(10.0, 10.0, 100.0),
        )
        val cycleTimes = listOf<Double?>(null, 500.0, 2500.0)
        for ((steps, mn, mx) in settings) for (a in alphaGrid) for (ct in cycleTimes) {
            val res = TaskerReference.calculateAnimation(a, steps, mn, mx, ct)
            rows.append("anim,${fmt(a)},${fmt(steps)},${fmt(mn)},${fmt(mx)},${ct?.let { fmt(it) } ?: ""},${res.loops},${res.wait},${res.throttle}\n")
        }
        File(dir, "animation.csv").writeText(rows.toString())
    }

    fun writeTransition(dir: File) {
        val rows = StringBuilder("start,target,loops,counter,brightnessInt\n")
        val pairs = listOf(
            10.0 to 200.0, 200.0 to 10.0, 0.0 to 255.0, 255.0 to 0.0,
            128.0 to 130.0, 50.0 to 49.0, 100.0 to 100.0, 5.0 to 250.0,
        )
        val loopsGrid = listOf(5, 10, 20, 50)
        for ((s, t) in pairs) for (loops in loopsGrid) for (counter in 0 until loops) {
            val b = TaskerReference.transitionFrameBrightness(s, t, counter, loops)
            rows.append("${fmt(s)},${fmt(t)},$loops,$counter,$b\n")
        }
        File(dir, "transition.csv").writeText(rows.toString())
    }

    fun writeDimming(dir: File) {
        val rows = StringBuilder("start,target,dimStart,finalDim,loops,counter,dimmingThreshold,hardwareTarget,dimVal,calculatedBright\n")
        val cases = listOf(
            // start, target, dimStart, finalDim, dimmingThreshold
            listOf(10.0, 4.0, 0.0, 60.0, 5.0),
            listOf(4.0, 10.0, 60.0, 0.0, 5.0),
            listOf(2.0, 2.0, 100.0, 100.0, 5.0),
            listOf(50.0, 3.0, 0.0, 90.0, 5.0),
            listOf(3.0, 50.0, 90.0, 0.0, 5.0),
            listOf(20.0, 1.0, 10.0, 80.0, 6.0),
        )
        val loopsGrid = listOf(5, 10, 20, 50)
        for (c in cases) for (loops in loopsGrid) for (counter in 0 until loops) {
            val f = TaskerReference.dcTransitionFrame(c[0], c[1], c[2], c[3], counter, loops, c[4])
            rows.append("${fmt(c[0])},${fmt(c[1])},${fmt(c[2])},${fmt(c[3])},$loops,$counter,${fmt(c[4])},${f.hardwareTarget},${f.dimVal},${fmt(f.calculatedBright)}\n")
        }
        File(dir, "dimming.csv").writeText(rows.toString())
    }

    fun writeSuperdimming(dir: File) {
        val rows = StringBuilder(
            "targetBrightness,isElevated,dimmingThreshold,pwmExp,minBright,dimmingExponent," +
                "dimmingStrength,dimDynamic,scalingUse,finalDim,dimProgress,dimShell\n",
        )
        val brightnessGrid = listOf(0.0, 4.0, 10.0, 30.0, 100.0, 200.0, 255.0)
        val elevatedGrid = listOf(false, true)
        val thresholdGrid = listOf(5.0, 20.0)
        val pwmExpGrid = listOf(1.5, 2.0, 3.0)
        val minBrightGrid = listOf(1.0, 10.0)
        val dimExpGrid = listOf(1.0, 2.0)
        val strengthGrid = listOf(10.0, 65.0)
        val dimDynGrid = listOf(1.0, 2.0)
        val scalingUseGrid = listOf(false, true)
        for (tb in brightnessGrid)
            for (elev in elevatedGrid)
            for (thresh in thresholdGrid)
            for (pwm in pwmExpGrid)
            for (minB in minBrightGrid)
            for (dimExp in dimExpGrid)
            for (str in strengthGrid)
            for (dd in dimDynGrid)
            for (su in scalingUseGrid) {
                // Skip invalid config: DimmingThreshold must exceed MinBright (D-030: span<=0 guard)
                if (thresh <= minB) continue
                val fd = TaskerReference.finalDimLevel(tb, elev, thresh, pwm)
                val ds = TaskerReference.dimProgressAndShell(tb, minB, thresh, dimExp, str, dd, su)
                rows.append(
                    "${fmt(tb)},$elev,${fmt(thresh)},${fmt(pwm)},${fmt(minB)},${fmt(dimExp)}," +
                        "${fmt(str)},${fmt(dd)},$su,${fmt(fd)},${fmt(ds.dimProgress)},${fmt(ds.dimShell)}\n",
                )
            }
        File(dir, "superdimming.csv").writeText(rows.toString())
    }

    fun generateAll(dir: File) {
        dir.mkdirs()
        writeMapping(dir)
        writeFormulae(dir)
        writeSmoothing(dir)
        writeThreshold(dir)
        writeTaper(dir)
        writeAnimation(dir)
        writeTransition(dir)
        writeDimming(dir)
        writeSuperdimming(dir)
    }
}

class GoldenVectorGeneratorTest {
    @Test
    fun regenerateGoldenVectors() {
        if (System.getProperty("regenGolden") == null) return
        // Test working dir is the module dir (domain/); write into the source resources tree.
        val dir = File("src/test/resources/golden")
        GoldenVectorGenerator.generateAll(dir)
        println("Regenerated golden vectors in ${dir.absolutePath}")
    }
}
