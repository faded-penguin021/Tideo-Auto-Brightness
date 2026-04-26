package com.tideo.autobrightness.app.settings

import kotlinx.serialization.Serializable

@Serializable
data class AabSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val serviceEnabled: Boolean = true,
    val detectOverrides: Boolean = false,
    val minBrightness: Int = 10,
    val maxBrightness: Int = 255,
    val offset: Int = 0,
    val scale: Int = 1,
    val zone1End: Int = 35,
    val zone2End: Int = 10_000,
    val form1A: Int = 5,
    val form2B: Float = 8.8f,
    val form2C: Int = 18,
    val dimmingEnabled: Boolean = false,
    val dimmingStrength: Int = 25,
    val dimmingExponent: Float = 2.5f,
    val dimmingThreshold: Int = 15,
    val dimSpread: Int = 100,
    val pwmSensitive: Boolean = false,
    val pwmExponent: Float = 0.8f,
    val throttleDefaultMs: Long = 1_000,
    val minWaitMs: Int = 25,
    val maxWaitMs: Int = 65,
    val deltaFactor: Float = 1.8f,
    val thresholdBright: Float = 0.08f,
    val thresholdDark: Float = 0.3f,
    val thresholdDim: Float = 0.25f,
    val thresholdDynamic: Int = 5,
    val thresholdSteepness: Float = 2.1f,
    val scalingEnabled: Boolean = false,
    val scaleSpread: Int = 15,
    val scaleSteepness: Int = 6,
    val scaleTaperMidpoint: Int = 190,
    val scaleTaperSteepness: Float = 0.075f,
    val scaleTransitionFactor: Float = 0.1f,
    val trustUnreliableSensor: Boolean = false,
    val quickSettingsEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val debugLevel: Int = 0,
)

const val CURRENT_SCHEMA_VERSION = 1

enum class AabValueType {
    Boolean,
    Int,
    Long,
    Float,
}

data class AabSettingRule(
    val taskerVariable: String,
    val key: String,
    val type: AabValueType,
    val defaultValue: String,
    val validation: String,
)

object AabSettingsContract {
    val rules: List<AabSettingRule> = listOf(
        AabSettingRule("%AAB_Service", "serviceEnabled", AabValueType.Boolean, "true", "must be true|false"),
        AabSettingRule("%AAB_DetectOverrides", "detectOverrides", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_MinBright", "minBrightness", AabValueType.Int, "10", "range 1..255"),
        AabSettingRule("%AAB_MaxBright", "maxBrightness", AabValueType.Int, "255", "range 1..255 and >= minBrightness"),
        AabSettingRule("%AAB_Offset", "offset", AabValueType.Int, "0", "range -255..255"),
        AabSettingRule("%AAB_Scale", "scale", AabValueType.Int, "1", "range 1..10"),
        AabSettingRule("%AAB_Zone1End", "zone1End", AabValueType.Int, "35", "range 1..20000"),
        AabSettingRule("%AAB_Zone2End", "zone2End", AabValueType.Int, "10000", "range 1..100000 and >= zone1End"),
        AabSettingRule("%AAB_Form1A", "form1A", AabValueType.Int, "5", "range 1..20"),
        AabSettingRule("%AAB_Form2B", "form2B", AabValueType.Float, "8.8", "range 0.1..30.0"),
        AabSettingRule("%AAB_Form2C", "form2C", AabValueType.Int, "18", "range 1..50"),
        AabSettingRule("%AAB_DimmingEnabled", "dimmingEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_DimmingStrength", "dimmingStrength", AabValueType.Int, "25", "range 0..100"),
        AabSettingRule("%AAB_DimmingExponent", "dimmingExponent", AabValueType.Float, "2.5", "range 0.5..5.0"),
        AabSettingRule("%AAB_DimmingThreshold", "dimmingThreshold", AabValueType.Int, "15", "range 0..100"),
        AabSettingRule("%AAB_DimSpread", "dimSpread", AabValueType.Int, "100", "range 1..300"),
        AabSettingRule("%AAB_PWMSensitive", "pwmSensitive", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_PWMExp", "pwmExponent", AabValueType.Float, "0.8", "range 0.1..3.0"),
        AabSettingRule("%AAB_DefaultThrottle", "throttleDefaultMs", AabValueType.Long, "1000", "range 100..60000"),
        AabSettingRule("%AAB_MinWait", "minWaitMs", AabValueType.Int, "25", "range 1..5000"),
        AabSettingRule("%AAB_MaxWait", "maxWaitMs", AabValueType.Int, "65", "range 1..5000 and >= minWaitMs"),
        AabSettingRule("%AAB_DeltaFactor", "deltaFactor", AabValueType.Float, "1.8", "range 0.1..10.0"),
        AabSettingRule("%AAB_ThreshBright", "thresholdBright", AabValueType.Float, "0.08", "range 0.0..1.0"),
        AabSettingRule("%AAB_ThreshDark", "thresholdDark", AabValueType.Float, "0.3", "range 0.0..1.0"),
        AabSettingRule("%AAB_ThreshDim", "thresholdDim", AabValueType.Float, "0.25", "range 0.0..1.0"),
        AabSettingRule("%AAB_ThreshDynamic", "thresholdDynamic", AabValueType.Int, "5", "range 1..20"),
        AabSettingRule("%AAB_ThreshSteepness", "thresholdSteepness", AabValueType.Float, "2.1", "range 0.1..10.0"),
        AabSettingRule("%AAB_ScalingUse", "scalingEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_ScaleSpread", "scaleSpread", AabValueType.Int, "15", "range 1..100"),
        AabSettingRule("%AAB_ScaleSteepness", "scaleSteepness", AabValueType.Int, "6", "range 1..20"),
        AabSettingRule("%AAB_ScaleTaperMidpoint", "scaleTaperMidpoint", AabValueType.Int, "190", "range 1..255"),
        AabSettingRule("%AAB_ScaleTaperSteepness", "scaleTaperSteepness", AabValueType.Float, "0.075", "range 0.001..1.0"),
        AabSettingRule("%AAB_ScaleTransitionFactor", "scaleTransitionFactor", AabValueType.Float, "0.1", "range 0.0..1.0"),
        AabSettingRule("%AAB_TrustUnreliable", "trustUnreliableSensor", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_QSUse", "quickSettingsEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_NotifyUse", "notificationsEnabled", AabValueType.Boolean, "true", "must be true|false"),
        AabSettingRule("%AAB_Debug", "debugLevel", AabValueType.Int, "0", "range 0..5"),
    )
}
