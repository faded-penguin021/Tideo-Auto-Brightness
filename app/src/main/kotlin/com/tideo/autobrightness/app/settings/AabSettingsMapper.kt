package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.app.state.SettingsState
import com.tideo.autobrightness.domain.brightness.AnimationConfig
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.brightness.ThresholdConfig

object AabSettingsMapper {
    fun toUiState(settings: AabSettings): SettingsState {
        return SettingsState(
            enabled = settings.serviceEnabled,
            minBrightness = (settings.minBrightness / 255f).coerceIn(0f, 1f),
            maxBrightness = (settings.maxBrightness / 255f).coerceIn(0f, 1f),
        )
    }

    fun fromUiState(uiState: SettingsState, current: AabSettings): AabSettings {
        val min = (uiState.minBrightness.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        val max = (uiState.maxBrightness.coerceIn(0f, 1f) * 255).toInt().coerceIn(min, 255)
        return current.copy(
            serviceEnabled = uiState.enabled,
            minBrightness = min,
            maxBrightness = max,
        )
    }
}

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
    spreadPercent = scaleSpread.toDouble(),
    dimSpreadPercent = dimSpread.toDouble(),
    steepness = scaleSteepness.toDouble(),
)

fun AabSettings.validate(): AabSettings {
    val clampedMinBrightness = minBrightness.coerceIn(1, 255)
    val clampedZone1End = zone1End.coerceIn(1, 20_000)
    val clampedMinWait = minWaitMs.coerceIn(1, 5_000)
    return copy(
        minBrightness = clampedMinBrightness,
        maxBrightness = maxBrightness.coerceIn(clampedMinBrightness, 255),
        offset = offset.coerceIn(-255, 255),
        scale = scale.coerceIn(0.1f, 10.0f),
        zone1End = clampedZone1End,
        zone2End = zone2End.coerceIn(clampedZone1End, 100_000),
        form1A = form1A.coerceIn(1, 20),
        form2B = form2B.coerceIn(0.1f, 30f),
        form2C = form2C.coerceIn(1, 50),
        dimmingStrength = dimmingStrength.coerceIn(0, 100),
        dimmingExponent = dimmingExponent.coerceIn(0.5f, 5f),
        dimmingThreshold = dimmingThreshold.coerceIn(0, 100),
        dimSpread = dimSpread.coerceIn(1, 300),
        pwmExponent = pwmExponent.coerceIn(0.1f, 3f),
        throttleDefaultMs = throttleDefaultMs.coerceIn(100, 60_000),
        minWaitMs = clampedMinWait,
        maxWaitMs = maxWaitMs.coerceIn(clampedMinWait, 5_000),
        animSteps = animSteps.coerceIn(0, 100),
        deltaFactor = deltaFactor.coerceIn(0.1f, 10f),
        thresholdBright = thresholdBright.coerceIn(0f, 1f),
        thresholdDark = thresholdDark.coerceIn(0f, 1f),
        thresholdDim = thresholdDim.coerceIn(0f, 1f),
        thresholdDynamic = thresholdDynamic.coerceIn(1, 20),
        thresholdSteepness = thresholdSteepness.coerceIn(0.1f, 10f),
        thresholdMidpoint = thresholdMidpoint.coerceIn(0.0, 6.0),
        scaleSpread = scaleSpread.coerceIn(1, 100),
        scaleSteepness = scaleSteepness.coerceIn(1, 20),
        scaleTaperMidpoint = scaleTaperMidpoint.coerceIn(130, 240),
        scaleTaperSteepness = scaleTaperSteepness.coerceIn(0.001f, 1f),
        scaleTransitionFactor = scaleTransitionFactor.coerceIn(0f, 1f),
        debugLevel = debugLevel.coerceIn(0, 9),
    )
}
