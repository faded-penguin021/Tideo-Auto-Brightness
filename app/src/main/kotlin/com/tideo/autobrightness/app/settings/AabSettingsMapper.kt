package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.brightness.AnimationConfig
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.brightness.ThresholdConfig

// Domain config mappings for BrightnessPolicyInput.

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

// D-002/D-004: derive form2A/form3A for C0 continuity.
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
        // D-036: %AAB_ScalingUse picks taper vs. linear; %AAB_Scale is linear multiplier.
        scalingUse = scalingEnabled,
        scale = scale.toDouble(),
    )
}

fun AabSettings.toDynamicScalingConfig(): DynamicScalingConfig = DynamicScalingConfig(
    enabled = scalingEnabled,
    // D-072: scale spread must stay positive; dimSpread alone can go negative (boost-in-daylight).
    spreadPercent = scaleSpread.coerceIn(1, 100).toDouble(),
    dimSpreadPercent = dimSpread.toDouble(),
    steepness = scaleSteepness.toDouble(),
)

fun AabSettings.validate(): AabSettings {
    // D-146: NaN → default; ±Infinity auto-clamped by coerceIn.
    fun Float.nanTo(default: Float): Float = if (isNaN()) default else this
    fun Double.nanTo(default: Double): Double = if (isNaN()) default else this
    val d = AabSettings()

    // G3-F3: floor is 0 (OEM minimum, dimmest not screen-off), not 1.
    val clampedMinBrightness = minBrightness.coerceIn(0, 255)
    val clampedZone1End = zone1End.coerceIn(1, 20_000)
    val clampedMinWait = minWaitMs.coerceIn(1, 5_000)
    return copy(
        minBrightness = clampedMinBrightness,
        maxBrightness = maxBrightness.coerceIn(clampedMinBrightness, 255),
        offset = offset.coerceIn(-255, 255),
        scale = scale.nanTo(d.scale).coerceIn(0.1f, 10.0f),
        zone1End = clampedZone1End,
        zone2End = zone2End.coerceIn(clampedZone1End, 100_000),
        form1A = form1A.nanTo(d.form1A).coerceIn(1.0, 20.0),
        form2B = form2B.nanTo(d.form2B).coerceIn(0.1f, 30f),
        form2C = form2C.coerceIn(1, 50),
        // DB-008 (issue #110): clamp SETPOINT to 65 (runtime always clamped; UI/persistence must agree).
        dimmingStrength = dimmingStrength.coerceIn(0, MAX_DIMMING_STRENGTH_SETPOINT),
        dimmingExponent = dimmingExponent.nanTo(d.dimmingExponent).coerceIn(0.5f, 5f),
        // Dimming threshold is brightness level (0..255), not percentage (old 0..100 was rebuild artifact).
        dimmingThreshold = dimmingThreshold.coerceIn(0, 255),
        // S12.9c #6: dimSpread is signed (−100..100), not 1..300 (old clamp blocked "boost in daylight").
        dimSpread = dimSpread.coerceIn(-100, 100),
        pwmExponent = pwmExponent.nanTo(d.pwmExponent).coerceIn(0.1f, 3f),
        throttleDefaultMs = throttleDefaultMs.coerceIn(100, 60_000),
        minWaitMs = clampedMinWait,
        maxWaitMs = maxWaitMs.coerceIn(clampedMinWait, 5_000),
        animSteps = animSteps.coerceIn(0, 100),
        deltaFactor = deltaFactor.nanTo(d.deltaFactor).coerceIn(0.1f, 10f),
        thresholdBright = thresholdBright.nanTo(d.thresholdBright).coerceIn(0f, 1f),
        thresholdDark = thresholdDark.nanTo(d.thresholdDark).coerceIn(0f, 1f),
        thresholdDim = thresholdDim.nanTo(d.thresholdDim).coerceIn(0f, 1f),
        thresholdSteepness = thresholdSteepness.nanTo(d.thresholdSteepness).coerceIn(0.1f, 10f),
        thresholdMidpoint = thresholdMidpoint.nanTo(d.thresholdMidpoint).coerceIn(0.0, 6.0),
        scaleSpread = scaleSpread.coerceIn(1, 100),
        scaleSteepness = scaleSteepness.coerceIn(1, 20),
        scaleTaperMidpoint = scaleTaperMidpoint.coerceIn(130, 240),
        scaleTaperSteepness = scaleTaperSteepness.nanTo(d.scaleTaperSteepness).coerceIn(0.001f, 1f),
        scaleTransitionFactor = scaleTransitionFactor.nanTo(d.scaleTransitionFactor).coerceIn(0f, 1f),
        debugLevel = debugLevel.coerceIn(0, 9),
        panicSensitivity = panicSensitivity.coerceIn(0, 10),
        // D-151: display-toggle fields; unknown daltonizer resets to OFF (D-146 spirit).
        nightLightTemperature = nightLightTemperature?.coerceIn(1_000, 10_000),
        daltonizerMode = if (daltonizerMode in DALTONIZER_MODES) daltonizerMode else DALTONIZER_OFF,
    )
}
