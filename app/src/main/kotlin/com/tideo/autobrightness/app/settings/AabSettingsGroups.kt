package com.tideo.autobrightness.app.settings

import kotlinx.serialization.Serializable

// D-073: nested decomposition (computed views, not wire schema). Flat JSON unchanged; records are @Serializable
// for future structured payloads but canonical store stays flat.
@Serializable
data class BrightnessBounds(
    val minBrightness: Int,
    val maxBrightness: Int,
    val offset: Int,
    val scale: Float,
)

@Serializable
data class CurveParams(
    val zone1End: Int,
    val zone2End: Int,
    val form1A: Double,
    val form2B: Float,
    val form2C: Int,
)

@Serializable
data class DimmingConfig(
    val dimmingEnabled: Boolean,
    val dimmingStrength: Int,
    val dimmingExponent: Float,
    val dimmingThreshold: Int,
    val dimSpread: Int,
    val pwmSensitive: Boolean,
    val pwmExponent: Float,
)

@Serializable
data class AnimationConfig(
    val animSteps: Int,
    val minWaitMs: Int,
    val maxWaitMs: Int,
    val throttleDefaultMs: Long,
)

@Serializable
data class ThresholdConfig(
    val deltaFactor: Float,
    val thresholdBright: Float,
    val thresholdDark: Float,
    val thresholdDim: Float,
    val thresholdSteepness: Float,
    val thresholdMidpoint: Double,
    val trustUnreliableSensor: Boolean,
)

@Serializable
data class ScalingConfig(
    val scalingEnabled: Boolean,
    val scaleSpread: Int,
    val scaleSteepness: Int,
    val scaleTaperMidpoint: Int,
    val scaleTaperSteepness: Float,
    val scaleTransitionFactor: Float,
)

// Global prefs + runtime latches. mergeProfile preserves some; others are profile-swapped (G2-F8).
@Serializable
data class GlobalPrefs(
    val serviceEnabled: Boolean,
    val detectOverrides: Boolean,
    val debugLevel: Int,
    val panicSensitivity: Int,
    val contextOverride: Boolean,
    val setupTitle: String,
    val quickSettingsEnabled: Boolean,
    val notificationsEnabled: Boolean,
)

// --- Computed group views over the flat AabSettings (decomposition without a wire/schema change) ---

val AabSettings.bounds: BrightnessBounds
    get() = BrightnessBounds(minBrightness, maxBrightness, offset, scale)

val AabSettings.curve: CurveParams
    get() = CurveParams(zone1End, zone2End, form1A, form2B, form2C)

val AabSettings.dimming: DimmingConfig
    get() = DimmingConfig(dimmingEnabled, dimmingStrength, dimmingExponent, dimmingThreshold, dimSpread, pwmSensitive, pwmExponent)

val AabSettings.animation: AnimationConfig
    get() = AnimationConfig(animSteps, minWaitMs, maxWaitMs, throttleDefaultMs)

val AabSettings.thresholds: ThresholdConfig
    get() = ThresholdConfig(deltaFactor, thresholdBright, thresholdDark, thresholdDim, thresholdSteepness, thresholdMidpoint, trustUnreliableSensor)

val AabSettings.scaling: ScalingConfig
    get() = ScalingConfig(scalingEnabled, scaleSpread, scaleSteepness, scaleTaperMidpoint, scaleTaperSteepness, scaleTransitionFactor)

val AabSettings.global: GlobalPrefs
    get() = GlobalPrefs(serviceEnabled, detectOverrides, debugLevel, panicSensitivity, contextOverride, setupTitle, quickSettingsEnabled, notificationsEnabled)
