package com.tideo.autobrightness.app.settings

import kotlinx.serialization.Serializable

/**
 * Nested decomposition of the 41 flat [AabSettings] fields into seven cohesive records (S12.9c #1).
 *
 * **Wire compatibility is binding.** These records are NOT the on-disk schema — [AabSettings] stays a
 * flat `@Serializable` data class so the existing flat v3 JSON (`aab_settings.json`) loads and saves
 * byte-for-byte unchanged (`CURRENT_SCHEMA_VERSION` stays 3, no migration). Each record is exposed as a
 * computed view over the flat fields (see the `AabSettings.bounds/curve/…` accessors below), so a flat
 * JSON file "loads into the nested fields" via those projections (asserted by `NestedSchemaRoundTripTest`).
 * They make the settings model *decomposable* for callers that want a cohesive sub-config, without the
 * ~395-call-site churn a nested-source-of-truth refactor would incur (deviation recorded in D-073).
 *
 * The records are `@Serializable` so they can be embedded in future structured payloads if wanted, but
 * the canonical store remains flat.
 */
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

/** App-layer animation group. Distinct from the domain `AnimationConfig` (the engine's runtime shape). */
@Serializable
data class AnimationConfig(
    val animSteps: Int,
    val minWaitMs: Int,
    val maxWaitMs: Int,
    val throttleDefaultMs: Long,
)

/** App-layer reactivity/threshold group. Distinct from the domain `ThresholdConfig`. */
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

/**
 * App-wide preferences and runtime/identity latches.
 *
 * NOTE on `mergeProfile`: the five fields [serviceEnabled], [detectOverrides], [debugLevel],
 * [contextOverride] and [setupTitle] are preserved across a context profile swap (G2-F8) — they are
 * NOT part of Tasker task626's per-profile snapshot. [quickSettingsEnabled] and [notificationsEnabled]
 * ARE in task626's snapshot (profile-swapped), so they live in this group for cohesion but are taken
 * from the loaded profile, not the baseline. See [mergeProfile].
 */
@Serializable
data class GlobalPrefs(
    val serviceEnabled: Boolean,
    val detectOverrides: Boolean,
    val debugLevel: Int,
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
    get() = GlobalPrefs(serviceEnabled, detectOverrides, debugLevel, contextOverride, setupTitle, quickSettingsEnabled, notificationsEnabled)
