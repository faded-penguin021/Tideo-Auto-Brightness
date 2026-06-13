package com.tideo.autobrightness.domain.brightness

import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Tasker task548 result: the final calculated brightness (round1) + the effective compressed
// scale (%AAB_ScaleDynamicCompress), which task561/OverrideRules consumes.
data class CompressedScaleResult(val calculatedBrightness: Double, val effectiveScale: Double)

class BrightnessEngine {
    fun evaluate(input: BrightnessPolicyInput): BrightnessPolicyOutput {
        input.overrides.manualBrightness?.let { manual ->
            return BrightnessPolicyOutput(
                targetBrightness = manual.coerceIn(input.curve.minBrightness, input.curve.maxBrightness),
                transitionDurationMs = 0,
                animationSteps = 1,
                animationWaitMs = 0,
                luxAlpha = 1.0,
                dimmingAlpha = 0.0,
                smoothedLux = input.lux,
                dynamicThreshold = 0.0,
                thresholdLow = input.lux,
                thresholdHigh = input.lux,
                scaleDynamic = 1.0,
                scaleDynamicCompress = 1.0,
            )
        }

        val prev = input.previous
        val prevSmoothedLux = prev?.smoothedLux ?: input.lux

        val dynamicThreshold = dynamicThreshold(input.lux, prevSmoothedLux, input.thresholds)
        // Tasker task546: par1 = current lux (gate + scale selector), lastRawLux = previous raw
        val absThresholds = absoluteThresholds(input.lux, prev?.lastRawLux ?: input.lux, dynamicThreshold)

        val shouldUpdate = prev == null || input.lux <= absThresholds.first || input.lux >= absThresholds.second
        val (smoothedLux, luxAlpha) = if (shouldUpdate) {
            smoothLux(
                rawLux = input.lux,
                previousSmoothedLux = prevSmoothedLux,
                thresholdDynamicPercent = dynamicThreshold * 100.0,
                deltaFactor = input.thresholds.deltaFactor,
                zone1End = input.thresholds.zone1End,
            )
        } else {
            prevSmoothedLux to 0.0
        }

        val mappedBrightness = mapLuxToBrightness(smoothedLux, input.curve)
        val scaleDynamic = if (input.dynamicScaling.enabled) {
            computeDynamicScale(input.time, input.dynamicScaling, input.context)
        } else {
            input.overrides.baseScaleOverride ?: 1.0
        }

        // Tasker task661 act10-14: ScalingUse → task548 taper; else linear mapped*Scale+Offset.
        val scaleResult = if (input.curve.scalingUse) {
            compressedDynamicScale(mappedBrightness, scaleDynamic, input.curve)
        } else {
            CompressedScaleResult(
                calculatedBrightness = mappedBrightness * input.curve.scale + input.curve.offset,
                effectiveScale = scaleDynamic,
            )
        }
        // Tasker task661 act16-21: clamp to [MinBright, MaxBright] AFTER scaling; Math.round (ties toward +∞)
        val targetBrightness = Math.round(scaleResult.calculatedBrightness).toInt()
            .coerceIn(input.curve.minBrightness, input.curve.maxBrightness)

        val (steps, wait, throttle) = calculateAnimation(
            alpha = luxAlpha,
            animation = input.animation,
            cycleTimeMs = prev?.cycleTimeMs,
        )

        val dimmingAlpha = dimmingAlpha(targetBrightness, input.curve.minBrightness)

        return BrightnessPolicyOutput(
            targetBrightness = targetBrightness,
            transitionDurationMs = throttle,
            animationSteps = steps,
            animationWaitMs = wait,
            luxAlpha = luxAlpha,
            dimmingAlpha = dimmingAlpha,
            smoothedLux = smoothedLux,
            dynamicThreshold = dynamicThreshold,
            thresholdLow = absThresholds.first,
            thresholdHigh = absThresholds.second,
            scaleDynamic = scaleDynamic,
            scaleDynamicCompress = scaleResult.effectiveScale,
        )
    }

    fun smoothLux(
        rawLux: Double,
        previousSmoothedLux: Double,
        thresholdDynamicPercent: Double,
        deltaFactor: Double,
        zone1End: Double,
    ): Pair<Double, Double> {
        val luxDelta = round3(abs((rawLux - previousSmoothedLux) / (previousSmoothedLux + 1.0)))
        val effectiveDelta = round3(luxDelta - (thresholdDynamicPercent / 100.0))
        // Tasker task535: lux_alpha is NOT clamped to [0,1] (D-010a, gap-01 R2 fix)
        val luxAlpha = round3(1.0 - exp(-deltaFactor * effectiveDelta))
        val smoothed = rawLux * luxAlpha + previousSmoothedLux * (1.0 - luxAlpha)
        // Tasker task535: BigDecimal(raw).setScale(2|0, HALF_UP) — exact-binary BigDecimal (gap-01 R1 fix)
        val rounded = if (smoothed < zone1End) bigScale(smoothed, 2) else bigScale(smoothed, 0)
        return rounded to luxAlpha
    }

    fun dynamicThreshold(rawLux: Double, smoothedLux: Double, cfg: ThresholdConfig): Double {
        val logLux = log10(smoothedLux + 1.0)
        val exponent = -cfg.threshSteepness * (logLux - cfg.threshMidpoint)
        val threshSig = round3(cfg.threshDim + (cfg.threshBright - cfg.threshDim) / (1.0 + exp(exponent)))
        val threshLow = round3(cfg.threshDark - ((cfg.threshDark - cfg.threshDim) / cfg.zone1End) * smoothedLux)
        return if (smoothedLux < cfg.zone1End) threshLow else threshSig
    }

    fun absoluteThresholds(currentLux: Double, lastRawLux: Double, dynamicThreshold: Double): Pair<Double, Double> {
        // Tasker task546: par1 < 0.2 → ("1","0","0.1") — special-case (gap-02 R2 fix)
        if (currentLux < 0.2) return 0.0 to 0.1
        val dynamicPercent = dynamicThreshold * 100.0
        val low = lastRawLux * (1.0 - (dynamicPercent / 100.0))
        val high = lastRawLux * (1.0 + (dynamicPercent / 100.0))
        // Tasker task546: BigDecimal HALF_UP, 2-dp if par1 < 10, else 0-dp (gap-02 R1 fix)
        val scale = if (currentLux < 10) 2 else 0
        return bigScale(low, scale) to bigScale(high, scale)
    }

    fun mapLuxToBrightness(smoothedLux: Double, cfg: BrightnessCurveConfig): Double {
        // Tasker task661: NO coerceAtLeast on ^0.33 bases, NO clamp to [min,max] here (D-010b, gap-03 R2 fix)
        return when {
            smoothedLux < cfg.zone1End -> cfg.form1A * sqrt(smoothedLux)
            smoothedLux < cfg.zone2End -> cfg.form2A + cfg.form2B * (
                (smoothedLux - cfg.form2C).pow(0.33) -
                    (cfg.zone1End - cfg.form2C).pow(0.33)
                )
            else -> cfg.maxBrightness - (cfg.form3A / smoothedLux) * cfg.maxBrightness
        }
    }

    fun compressedDynamicScale(mappedBrightness: Double, scaleDynamic: Double, cfg: BrightnessCurveConfig): CompressedScaleResult {
        if (mappedBrightness == 0.0) return CompressedScaleResult(cfg.minBrightness.toDouble(), scaleDynamic)
        val exponent = round3(-cfg.taperSteepness * (mappedBrightness - cfg.taperMidpoint))
        val compressionFactor = round3(1.0 / (1.0 + exp(exponent)))
        val taperEffect = round3(1.0 - compressionFactor)
        val taperedScale = round3(1.0 + (scaleDynamic - 1.0) * taperEffect)
        val dynamicCap = round3(cfg.maxBrightness / mappedBrightness)
        val dynamicFloor = round3(2.0 - dynamicCap)

        val effectiveScale = round3(
            if (scaleDynamic > 1.0) min(taperedScale, dynamicCap) else max(taperedScale, dynamicFloor),
        )

        return CompressedScaleResult(roundN(mappedBrightness * effectiveScale + cfg.offset, 1), effectiveScale)
    }

    // Tasker task661 act10-21: map → (ScalingUse ? task548 taper : mapped*Scale+Offset) → clamp
    // to [MinBright, MaxBright] as doubles (the int round happens at the platform write). This is
    // the unit the golden vector `calculated.csv` pins; evaluate() reuses the same branch.
    fun calculatedBrightness(lux: Double, cfg: BrightnessCurveConfig, scaleDynamic: Double): Double {
        val mapped = mapLuxToBrightness(lux, cfg)
        val calc = if (cfg.scalingUse) {
            compressedDynamicScale(mapped, scaleDynamic, cfg).calculatedBrightness
        } else {
            mapped * cfg.scale + cfg.offset
        }
        return calc.coerceIn(cfg.minBrightness.toDouble(), cfg.maxBrightness.toDouble())
    }

    fun computeDynamicScale(time: TimeContext, scaling: DynamicScalingConfig, context: BrightnessContext): Double =
        DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = time.secondsOfDay,
                morningStart = time.morningStart,
                morningEnd = time.morningEnd,
                eveningStart = time.eveningStart,
                eveningEnd = time.eveningEnd,
                sunlightDurationMinutes = time.sunlightDurationMinutes,
                isPolar = context.isPolarDayNight,
                steepness = scaling.steepness,
                scaleSpreadPercent = scaling.spreadPercent,
            )
        ).scaleDynamic

    fun calculateAnimation(alpha: Double, animation: AnimationConfig, cycleTimeMs: Double?): Triple<Int, Long, Long> {
        val clamped = alpha.coerceIn(0.0, 1.0)
        // Tasker task543: Math.round (ties toward +∞), not kotlin.math.round (gap-04 R1 fix)
        val loops = Math.round(1.0 + clamped * (animation.maxSteps - 1.0)).toInt().coerceAtLeast(1)
        val rawWait = (1.0 - clamped) * (animation.maxWaitMs - animation.minWaitMs) + animation.minWaitMs
        val wait = Math.round(rawWait)
        var throttle = Math.round(loops.toLong() * wait + 10.0)
        if (cycleTimeMs != null && cycleTimeMs <= 2000.0) {
            throttle += Math.round(cycleTimeMs)
        }
        return Triple(loops, wait, throttle)
    }

    private fun round3(value: Double): Double = roundN(value, 3)

    // Tasker idiom Math.round(v * factor) / factor — ties toward +∞ (not kotlin.math.round which ties-to-even)
    private fun roundN(value: Double, digits: Int): Double {
        val factor = 10.0.pow(digits)
        return Math.round(value * factor).toDouble() / factor
    }

    // Tasker idiom new BigDecimal(v).setScale(scale, ROUND_HALF_UP) — exact-binary double constructor
    private fun bigScale(v: Double, scale: Int): Double =
        BigDecimal(v).setScale(scale, RoundingMode.HALF_UP).toDouble()

    private fun dimmingAlpha(targetBrightness: Int, minBrightness: Int): Double {
        val span = (255 - minBrightness).coerceAtLeast(1)
        return (1.0 - ((targetBrightness - minBrightness).toDouble() / span)).coerceIn(0.0, 1.0)
    }
}
