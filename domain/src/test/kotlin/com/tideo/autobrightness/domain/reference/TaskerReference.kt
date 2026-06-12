package com.tideo.autobrightness.domain.reference

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * TASKER REFERENCE IMPLEMENTATION — the behavioral oracle for the rebuild.
 *
 * Each function is a faithful, line-by-line transcription of a single embedded Java block
 * (action code 474) or code-547 maths expression extracted in S1. Java semantics are preserved
 * EXACTLY — this is test-only code and MUST conform to Tasker, not to taste:
 *
 *  - `java.lang.Math.round(x)` (ties toward +infinity) where Tasker used `Math.round` — this
 *    differs from `kotlin.math.round` (which the production engine uses) on tie cases.
 *  - `BigDecimal(double).setScale(n, HALF_UP)` where Tasker used BigDecimal — note the
 *    `BigDecimal(double)` constructor uses the EXACT binary value of the double (parity-critical),
 *    not `BigDecimal.valueOf`.
 *  - String-formatted numbers where Tasker concatenated/printed.
 *  - NO clamping / coercion that Tasker did not perform (see D-010).
 *
 * Vectors generated from these functions are immutable fixtures: production code conforms to
 * THEM (S5+), never the other way round. Changing a reference function requires evidence the
 * extraction was wrong + a STATE.md entry.
 *
 * Segment: S4. Sources: docs/rebuild/extraction/java and extraction/tasks/task659/task661.
 */
object TaskerReference {

    // ---- Java arithmetic primitives (preserve Java rounding semantics) --------------------

    /** `Math.round(double)` — ties toward +infinity, returns long. java.lang.Math, exact Java. */
    fun jround(x: Double): Long = Math.round(x)

    /** Tasker idiom `Math.round(v * 1000.0) / 1000.0` (3-dp round, ties toward +inf). */
    fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0

    /** Tasker idiom `Math.round(v * 10.0) / 10.0` (1-dp round). */
    fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    /** `new BigDecimal(v).setScale(scale, ROUND_HALF_UP).toString()` — exact-binary BigDecimal. */
    fun bigScale(v: Double, scale: Int): String =
        BigDecimal(v).setScale(scale, RoundingMode.HALF_UP).toString()

    // ---- task554 "Process Sensor Event (Java)" -------------------------------------------
    // Java L18133-L18173. Strips brackets, rounds raw lux to 3-dp HALF_UP → %AAB_LastRawLux.
    /** Returns the BigDecimal(3,HALF_UP) string form of the raw sensor lux, as Tasker stores it. */
    fun processSensorEventLastRawLux(rawLux: Double): String = bigScale(rawLux, 3)

    // ---- task535 "Lux Smoothing (Java)" --------------------------------------------------
    // Java L15205-L15248. %vars: %par1(raw lux), %par2(prev smoothed), %AAB_ThreshDynamic(percent),
    // %AAB_DeltaFactor, %AAB_Zone1End. NOTE: lux_alpha is NOT clamped to [0,1] (D-010a).
    data class SmoothingResult(
        val smoothedLux: Double,
        val luxAlpha: Double,
        val luxDelta: Double,
        val smoothedLuxString: String,
    )

    fun luxSmoothing(
        par1: Double,
        par2: Double,
        threshDynamicPercent: Double,
        deltaFactor: Double,
        zone1End: Double,
    ): SmoothingResult {
        // A1: lux_delta = round3(|(par1 - par2) / (par2 + 1)|)
        val luxDeltaRaw = abs((par1 - par2) / (par2 + 1.0))
        val luxDelta = round3(luxDeltaRaw)
        // A2: effective_delta = round3(lux_delta - ThreshDynamic/100)
        val effectiveDelta = round3(luxDelta - (threshDynamicPercent / 100.0))
        // A3: lux_alpha = round3(1 - exp(-DeltaFactor * effective_delta))  [UNCLAMPED]
        val luxAlpha = round3(1.0 - exp(-deltaFactor * effectiveDelta))
        // A4: new_smoothed_lux (unrounded)
        val newSmoothedRaw = (par1 * luxAlpha) + (par2 * (1.0 - luxAlpha))
        // BigDecimal HALF_UP: 2-dp below Zone1End, else 0-dp (whole number, no ".0")
        val str = if (newSmoothedRaw < zone1End) bigScale(newSmoothedRaw, 2) else bigScale(newSmoothedRaw, 0)
        return SmoothingResult(str.toDouble(), luxAlpha, luxDelta, str)
    }

    // ---- task544 "Evaluate Light Change (Java) V2" ---------------------------------------
    // Java L16063-L16100. Produces %relative_change and %dynamic_threshold.
    data class LightChangeResult(val relativeChange: Double, val dynamicThreshold: Double)

    fun evaluateLightChange(
        par1: Double,
        smoothedLux: Double,
        threshDim: Double,
        threshBright: Double,
        threshSteepness: Double,
        threshMidpoint: Double,
        threshDark: Double,
        zone1End: Double,
    ): LightChangeResult {
        val luxDifference = abs(par1 - smoothedLux)
        val relativeChange = round3(luxDifference / (smoothedLux + 1.0))
        val logLux = log10(smoothedLux + 1.0)
        val exponent = -threshSteepness * (logLux - threshMidpoint)
        val threshSig = round3(threshDim + (threshBright - threshDim) / (1.0 + exp(exponent)))
        val threshLow = round3(threshDark - ((threshDark - threshDim) / zone1End) * smoothedLux)
        val dynamicThreshold = if (smoothedLux < zone1End) threshLow else threshSig
        return LightChangeResult(relativeChange, dynamicThreshold)
    }

    // ---- task546 "Set Thresholds (Java)" -------------------------------------------------
    // Java L16482-L16534. %par1(current lux), %par2(dynamic_threshold fraction), %AAB_LastRawLux.
    // String outputs (Tasker stores strings). Special-case par1<0.2 → "1","0","0.1".
    data class AbsoluteThresholds(
        val threshDynamic: String,
        val threshAbsLow: String,
        val threshAbsHigh: String,
    )

    fun setThresholds(par1: Double, par2: Double, lastRawLux: Double): AbsoluteThresholds {
        if (par1 < 0.2) {
            return AbsoluteThresholds("1", "0", "0.1")
        }
        val dynamicThreshRaw = par2 * 100.0
        val threshAbsLowRaw = lastRawLux * (1.0 - (dynamicThreshRaw / 100.0))
        val threshAbsHighRaw = lastRawLux * (1.0 + (dynamicThreshRaw / 100.0))
        val scale = if (par1 < 10) 2 else 0
        return AbsoluteThresholds(
            bigScale(dynamicThreshRaw, scale),
            bigScale(threshAbsLowRaw, scale),
            bigScale(threshAbsHighRaw, scale),
        )
    }

    // ---- task659 "_UpdateBrightnessFormulae" (code-547 maths; D-002/D-027) ----------------
    // XML L33337/L33347 (DoMaths). HIGHEST-RISK transcription in the program.
    //
    //   form2a = form1a * sqrt(zone1end)
    //   form3a = ( zone2end * ( MaxBright - ( form2a
    //                + form2b * ( (zone2end-form2c)^0.33 - (zone1end-form2c)^0.33 ) ) ) / MaxBright )
    //
    // Parse tree for form3a (verbatim XML:
    //   (%aab_zone2end*(%AAB_MaxBright-(%aab_form2a+%aab_form2b*((%aab_zone2end-%aab_form2c)^0.33
    //    -(%aab_zone1end-%aab_form2c)^0.33)))/%AAB_MaxBright) ):
    //   `*` and `/` are left-associative, equal precedence ⇒
    //     ( ( zone2end * INNER ) / MaxBright )   where
    //     INNER = MaxBright - ( form2a + form2b * ( A - B ) )
    //     A = (zone2end-form2c)^0.33 ,  B = (zone1end-form2c)^0.33
    //   `^` (pow) binds tighter than `*`; `-` inside parens is explicit. NO rounding (DoMaths).
    // Cross-validation: task663 block#2 (plot copy) uses the SAME zone-2 anchor expression with
    // `aab_zone1end` in place of `%AAB_Form2D`; defaults_audit confirms Form2D ≡ Zone1End, so 661
    // and 663 agree by construction (recorded in parity_gaps.md §cross-validation).
    data class ContinuityCoefficients(val form2a: Double, val form3a: Double)

    fun deriveContinuityCoefficients(
        form1a: Double,
        form2b: Double,
        form2c: Double,
        zone1End: Double,
        zone2End: Double,
        maxBright: Double,
    ): ContinuityCoefficients {
        val form2a = form1a * sqrt(zone1End)
        val a = (zone2End - form2c).pow(0.33)
        val b = (zone1End - form2c).pow(0.33)
        val inner = maxBright - (form2a + form2b * (a - b))
        val form3a = zone2End * inner / maxBright
        return ContinuityCoefficients(form2a, form3a)
    }

    // ---- task661 "Map Lux to Brightness (Java) V2" — mapping maths (code-547) -------------
    // act4/6/8 (L33732/L33865/L33878), DoMaths. Condition uses %smoothed_lux; formula uses %par1;
    // in the pipeline smoothed_lux == par1, so a single `lux` argument is faithful.
    // NO coerceAtLeast on the ^0.33 bases, NO clamp to [min,max] here (D-010b). Form2D ≡ Zone1End.
    //
    //   lux < zone1End : form1a * sqrt(lux)
    //   lux < zone2End : form2a + form2b * ( (lux-form2c)^0.33 - (form2d-form2c)^0.33 )
    //   else           : maxBright - (form3a / lux) * maxBright
    fun mappedBrightness(
        lux: Double,
        form1a: Double,
        form2a: Double,
        form2b: Double,
        form2c: Double,
        form2d: Double,
        zone1End: Double,
        zone2End: Double,
        form3a: Double,
        maxBright: Double,
    ): Double = when {
        lux < zone1End -> form1a * sqrt(lux)
        lux < zone2End -> form2a + form2b * ((lux - form2c).pow(0.33) - (form2d - form2c).pow(0.33))
        else -> maxBright - (form3a / lux) * maxBright
    }

    // ---- task548 "Dynamic Range Compressed Scale (Java) V2" -------------------------------
    // Java L16631-L16698. Returns the final calculated brightness (round1) + effective scale.
    data class CompressedScaleResult(val calculatedBrightness: Double, val effectiveScale: Double)

    fun dynamicCompressedScale(
        mappedBrightness: Double,
        minBright: Double,
        scaleDynamic: Double,
        maxBright: Double,
        offset: Double,
        taperMidpoint: Double,
        taperSteepness: Double,
    ): CompressedScaleResult {
        if (mappedBrightness == 0.0) {
            return CompressedScaleResult(minBright, scaleDynamic)
        }
        val exponent = round3(-taperSteepness * (mappedBrightness - taperMidpoint))
        val compressionFactor = round3(1.0 / (1.0 + exp(exponent)))
        val taperEffect = round3(1.0 - compressionFactor)
        val taperedScale = round3(1.0 + (scaleDynamic - 1.0) * taperEffect)
        val dynamicCap = round3(maxBright / mappedBrightness)
        val dynamicFloor = round3(2.0 - dynamicCap)
        val effectiveScale = round3(
            if (scaleDynamic > 1.0) min(taperedScale, dynamicCap) else max(taperedScale, dynamicFloor),
        )
        val calculated = round1(mappedBrightness * effectiveScale + offset)
        return CompressedScaleResult(calculated, effectiveScale)
    }

    // ---- task661 full calculated_brightness path (act10-21) ------------------------------
    // If ScalingUse → task548; else calculated = mapped*Scale + Offset (DoMaths, unrounded).
    // Then If calculated<MinBright → MinBright; If calculated>MaxBright → MaxBright.
    fun calculatedBrightness(
        lux: Double,
        form1a: Double,
        form2a: Double,
        form2b: Double,
        form2c: Double,
        form2d: Double,
        zone1End: Double,
        zone2End: Double,
        form3a: Double,
        minBright: Double,
        maxBright: Double,
        offset: Double,
        scale: Double,
        scalingUse: Boolean,
        scaleDynamic: Double,
        taperMidpoint: Double,
        taperSteepness: Double,
    ): Double {
        val mapped = mappedBrightness(lux, form1a, form2a, form2b, form2c, form2d, zone1End, zone2End, form3a, maxBright)
        var calc = if (scalingUse) {
            dynamicCompressedScale(mapped, minBright, scaleDynamic, maxBright, offset, taperMidpoint, taperSteepness)
                .calculatedBrightness
        } else {
            mapped * scale + offset
        }
        if (calc < minBright) calc = minBright
        if (calc > maxBright) calc = maxBright
        return calc
    }

    // ---- task543 "Calculate Animation (Java) V2" -----------------------------------------
    // Java L15879-L15916. %par1(alpha), %AAB_AnimSteps, %AAB_MinWait, %AAB_MaxWait, %AAB_CycleTime.
    data class AnimationResult(val loops: Long, val wait: Long, val throttle: Long)

    fun calculateAnimation(
        par1: Double,
        animSteps: Double,
        minWait: Double,
        maxWait: Double,
        cycleTime: Double?,
    ): AnimationResult {
        var p1 = par1
        if (p1 < 0.0) p1 = 0.0
        if (p1 > 1.0) p1 = 1.0
        val loops = jround(1.0 + p1 * (animSteps - 1.0))
        val rawWait = (1.0 - p1) * (maxWait - minWait) + minWait
        val wait = jround(rawWait)
        var throttle = jround(loops * wait + 10.0)
        if (cycleTime != null && cycleTime <= 2000.0) {
            throttle += jround(cycleTime)
        }
        return AnimationResult(loops, wait, throttle)
    }

    // ---- task618 "Set Initial Brightness (Java) V3" block#1 ------------------------------
    // Java L25827-L25845. raw lux → smoothed_lux (0-dp) + SmoothedLux (2-dp), via Math.round.
    data class InitialBrightnessResult(val smoothedLux0dp: Long, val smoothedLux2dp: Double)

    fun setInitialBrightness(rawLux: Double): InitialBrightnessResult =
        InitialBrightnessResult(jround(rawLux), Math.round(rawLux * 100.0) / 100.0)

    // ---- task696 "Smooth Brightness Transition V5 (Java)" — per-frame pure math ----------
    // Java L35734-L35886. progress = counter/loops; interpolate start→target; round+clamp [0,255].
    fun transitionFrameBrightness(startBrightness: Double, targetBrightness: Double, counter: Int, loops: Int): Int {
        val progress = counter.toDouble() / loops
        val calculated = startBrightness + progress * (targetBrightness - startBrightness)
        var brightnessInt = jround(calculated).toInt()
        if (brightnessInt < 0) brightnessInt = 0
        if (brightnessInt > 255) brightnessInt = 255
        return brightnessInt
    }

    // ---- task698 "Smooth DC-Like Brightness Transition V5 (Java)" — per-frame pure math ---
    // Java L36044-L36298. Hardware target (PWM floor at dimmingThreshold) + dim curve interpolation.
    data class DcTransitionFrame(val hardwareTarget: Int, val dimVal: Int, val calculatedBright: Double)

    fun dcTransitionFrame(
        startBrightness: Double,
        targetBrightness: Double,
        dimStart: Double,
        finalDim: Double,
        counter: Int,
        loops: Int,
        dimmingThreshold: Double,
    ): DcTransitionFrame {
        val progress = counter.toDouble() / loops
        val calculatedBright = startBrightness + progress * (targetBrightness - startBrightness)
        val dimShellDouble = dimStart + progress * (finalDim - dimStart)
        val dimVal = jround(dimShellDouble).toInt()
        val hardwareTarget = if (calculatedBright < dimmingThreshold) {
            jround(dimmingThreshold).toInt()
        } else {
            jround(calculatedBright).toInt()
        }
        return DcTransitionFrame(hardwareTarget, dimVal, calculatedBright)
    }
}
