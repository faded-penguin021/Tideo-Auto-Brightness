package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.brightness.AnimationConfig
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.brightness.ThresholdConfig

// Domain config mappings — used by S9a pipeline controller to build BrightnessPolicyInput.

fun AabSettings.toThresholdConfig(): ThresholdConfig = ThresholdConfig(
    threshDark = thresholdDark.toDouble(),
    threshDim = thresholdDim.toDouble(),
    threshBright = thresholdBright.toDouble(),
    threshSteepness = thresholdSteepness.toDouble(),
    threshMidpoint = thresholdMidpoint,
    zone1End = zone1End.toDouble(),
    deltaFactor = deltaFactor.toDouble(),
)

fun AabSettings.toAnimationConfig(): AnimationConfig = AnimationConfig(
    maxSteps = animSteps,
    minWaitMs = minWaitMs.toLong(),
    maxWaitMs = maxWaitMs.toLong(),
)

// Derives form2A / form3A via BrightnessFormulae for C0 continuity (D-002/D-004 chain).
fun AabSettings.toBrightnessCurveConfig(): BrightnessCurveConfig {
    val coeffs = BrightnessFormulae.deriveContinuityCoefficients(
        form1A = form1A.toDouble(),
        form2B = form2B.toDouble(),
        form2C = form2C.toDouble(),
        zone1End = zone1End.toDouble(),
        zone2End = zone2End.toDouble(),
        maxBrightness = maxBrightness.toDouble(),
    )
    return BrightnessCurveConfig(
        form1A = form1A.toDouble(),
        form2A = coeffs.form2A,
        form2B = form2B.toDouble(),
        form2C = form2C.toDouble(),
        zone1End = zone1End.toDouble(),
        zone2End = zone2End.toDouble(),
        form3A = coeffs.form3A,
        minBrightness = minBrightness,
        maxBrightness = maxBrightness,
        offset = offset.toDouble(),
        taperMidpoint = scaleTaperMidpoint.toDouble(),
        taperSteepness = scaleTaperSteepness.toDouble(),
        // Tasker task661 act10/14 (D-036): %AAB_ScalingUse picks taper vs. linear; %AAB_Scale is
        // the static multiplier used by the linear branch (mapped*Scale+Offset).
        scalingUse = scalingEnabled,
        scale = scale.toDouble(),
    )
}

fun AabSettings.toDynamicScalingConfig(): DynamicScalingConfig = DynamicScalingConfig(
    enabled = scalingEnabled,
    // SAFETY: the circadian SCALE spread must stay positive (1..100). A negative value flips the
    // day/night curve and can drive ScaleDynamic to ≤0 → a black screen. Only the super-dimming
    // circadian spread (dimSpreadPercent) is allowed to go negative (boost-in-daylight, D-072).
    spreadPercent = scaleSpread.coerceIn(1, 100).toDouble(),
    dimSpreadPercent = dimSpread.toDouble(),
    steepness = scaleSteepness.toDouble(),
)

fun AabSettings.validate(): AabSettings {
    // G3-F3 (Gate 3): floor is 0, not 1. The Misc slider exposes 0..75 (Tasker %AAB_MinBright range);
    // clamping 0→1 on Apply meant committed(1) ≠ draft(0) so the screen stayed perpetually dirty and the
    // value never stuck. Domain 0 maps to the OEM minimum (ScreenBrightnessController.toDevice coerces
    // 0..255) — dimmest, not screen-off — so 0 is a valid brightness.
    val clampedMinBrightness = minBrightness.coerceIn(0, 255)
    val clampedZone1End = zone1End.coerceIn(1, 20_000)
    val clampedMinWait = minWaitMs.coerceIn(1, 5_000)
    return copy(
        minBrightness = clampedMinBrightness,
        maxBrightness = maxBrightness.coerceIn(clampedMinBrightness, 255),
        offset = offset.coerceIn(-255, 255),
        scale = scale.coerceIn(0.1f, 10.0f),
        zone1End = clampedZone1End,
        zone2End = zone2End.coerceIn(clampedZone1End, 100_000),
        form1A = form1A.coerceIn(1.0, 20.0),
        form2B = form2B.coerceIn(0.1f, 30f),
        form2C = form2C.coerceIn(1, 50),
        dimmingStrength = dimmingStrength.coerceIn(0, 100),
        dimmingExponent = dimmingExponent.coerceIn(0.5f, 5f),
        // The dimming threshold is a BRIGHTNESS level (super-dimming engages when target < threshold),
        // so it spans the full brightness domain 0..255 like min/maxBrightness — not 0..100. The Tasker
        // field (superdimming_settings.md elements36) is an uncapped numeric; the old 0..100 clamp was a
        // rebuild artifact that prevented engaging dimming above brightness 100 (owner finding).
        dimmingThreshold = dimmingThreshold.coerceIn(0, 255),
        // S12.9c #6: dimSpread is signed (−100..100). The old 1..300 clamp silently turned the S12.9b
        // negative-spread "boost in daylight" path into 1 on every save, making it unreachable.
        dimSpread = dimSpread.coerceIn(-100, 100),
        pwmExponent = pwmExponent.coerceIn(0.1f, 3f),
        throttleDefaultMs = throttleDefaultMs.coerceIn(100, 60_000),
        minWaitMs = clampedMinWait,
        maxWaitMs = maxWaitMs.coerceIn(clampedMinWait, 5_000),
        animSteps = animSteps.coerceIn(0, 100),
        deltaFactor = deltaFactor.coerceIn(0.1f, 10f),
        thresholdBright = thresholdBright.coerceIn(0f, 1f),
        thresholdDark = thresholdDark.coerceIn(0f, 1f),
        thresholdDim = thresholdDim.coerceIn(0f, 1f),
        thresholdSteepness = thresholdSteepness.coerceIn(0.1f, 10f),
        thresholdMidpoint = thresholdMidpoint.coerceIn(0.0, 6.0),
        scaleSpread = scaleSpread.coerceIn(1, 100),
        scaleSteepness = scaleSteepness.coerceIn(1, 20),
        scaleTaperMidpoint = scaleTaperMidpoint.coerceIn(130, 240),
        scaleTaperSteepness = scaleTaperSteepness.coerceIn(0.001f, 1f),
        scaleTransitionFactor = scaleTransitionFactor.coerceIn(0f, 1f),
        debugLevel = debugLevel.coerceIn(0, 9),
        panicSensitivity = panicSensitivity.coerceIn(0, 10),
    )
}
