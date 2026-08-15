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
    // Tasker: %AAB_Scale; task592 profiles use 0.8/1.15 so Float (was Int in v1 — transparent migration)
    val scale: Float = 1.0f,
    val zone1End: Int = 35,
    val zone2End: Int = 10_000,
    // G2R-F70: %AAB_Form1A continuous curve coefficient; stored as Double to preserve decimals
    val form1A: Double = 5.0,
    val form2B: Float = 8.8f,
    val form2C: Int = 18,
    val dimmingEnabled: Boolean = false,
    val dimmingStrength: Int = 25,
    val dimmingExponent: Float = 2.5f,
    val dimmingThreshold: Int = 15,
    val dimSpread: Int = 100,
    val pwmSensitive: Boolean = false,
    val pwmExponent: Float = 0.8f,
    // Tasker: task570 %AAB_Throttle = AnimSteps*MaxWait+10 = 20*65+10 = 1310 (D-004/D-008)
    val throttleDefaultMs: Long = 1_310L,
    val minWaitMs: Int = 25,
    val maxWaitMs: Int = 65,
    // Tasker: task570 %AAB_AnimSteps = 20; slider range 0–100 (D-004/D-008/D-017)
    val animSteps: Int = 20,
    val deltaFactor: Float = 1.8f,
    val thresholdBright: Float = 0.08f,
    val thresholdDark: Float = 0.3f,
    val thresholdDim: Float = 0.25f,
    // NOTE (G2R-F85): %AAB_ThreshDynamic is the COMPUTED dynamic reactivity threshold for the current
    // lux (task544 output), never a user input — task570 act31 only seeds it. It lived here as a bogus
    // editable Int in v1/v2; removed in v3. The live computed value is PipelineState.threshDynamic.
    val thresholdSteepness: Float = 2.1f,
    // Tasker: %AAB_ThreshMidpoint = log10(%AAB_Zone2End) = log10(10000) = 4; DERIVED-but-persisted (D-004/D-008)
    val thresholdMidpoint: Double = 4.0,
    val scalingEnabled: Boolean = false,
    val scaleSpread: Int = 15,
    val scaleSteepness: Int = 6,
    val scaleTaperMidpoint: Int = 190,
    val scaleTaperSteepness: Float = 0.075f,
    val scaleTransitionFactor: Float = 0.1f,
    val trustUnreliableSensor: Boolean = false,
    val quickSettingsEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    // Tasker: %AAB_Debug; 10 named categories 0–9 (D-023)
    val debugLevel: Int = 0,
    // Tasker: %AAB_PanicSensitivity — shake intensity for Panic gesture; 0=off, 10=vigorous (D-116)
    val panicSensitivity: Int = 8,
    // Tasker: %AAB_ContextOverride — manual context lock. Baseline must be false (D-038).
    val contextOverride: Boolean = false,
    // Tasker: %AAB_PanicPlugged — panic gesture must work on battery (#110)
    val panicRequiresPlugged: Boolean = false,
    // Tasker: %AAB_SetupTitle; onboarding dialog title (D-008)
    val setupTitle: String = "Advanced Auto Brightness Setup",
    // D-151/D-152: privileged display toggles (rebuild-only, per-profile, applied by DisplayTogglesCoordinator)
    val nightLightEnabled: Boolean = false,
    val nightLightTemperature: Int? = null,
    // D-154: temperature follows circadian modifier; manual changes don't stick while on
    val nightLightCircadianEnabled: Boolean = false,
    // D-150: color-correction mode (STRING enum for schema forward-compatibility)
    val daltonizerMode: String = DALTONIZER_OFF,
    val inversionEnabled: Boolean = false,
    val alwaysOnDisplayEnabled: Boolean = false,
    val stayAwakeChargingEnabled: Boolean = false,
    // Android-14+ HDR-format disabling (experimental). On older devices the field is inert: the coordinator
    // checks the controller's availability gate and never writes it.
    val hdrForceSdrEnabled: Boolean = false,
)

/** [AabSettings.daltonizerMode] value for "color correction off". */
const val DALTONIZER_OFF = "OFF"

/** Valid [AabSettings.daltonizerMode] values (mirrors platform DaltonizerMode enum). */
val DALTONIZER_MODES: Set<String> =
    setOf(DALTONIZER_OFF, "GRAYSCALE", "PROTANOMALY", "DEUTERANOMALY", "TRITANOMALY")

// v2: added animSteps/thresholdMidpoint/contextOverride/setupTitle; v3: removed thresholdDynamic (G2R-F85)
const val CURRENT_SCHEMA_VERSION = 3

enum class AabValueType {
    Boolean,
    Int,
    Long,
    Float,
    Double,
    String,
}

/** DB-008: highest dimming-strength SETPOINT (matches SoftwareDimming runtime clamp). */
const val MAX_DIMMING_STRENGTH_SETPOINT = 65

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
        AabSettingRule("%AAB_MinBright", "minBrightness", AabValueType.Int, "10", "range 0..255"),
        AabSettingRule("%AAB_MaxBright", "maxBrightness", AabValueType.Int, "255", "range 1..255 and >= minBrightness"),
        AabSettingRule("%AAB_Offset", "offset", AabValueType.Int, "0", "range -255..255"),
        AabSettingRule("%AAB_Scale", "scale", AabValueType.Float, "1.0", "range 0.1..10.0"),
        AabSettingRule("%AAB_Zone1End", "zone1End", AabValueType.Int, "35", "range 1..20000"),
        AabSettingRule("%AAB_Zone2End", "zone2End", AabValueType.Int, "10000", "range 1..100000 and >= zone1End"),
        AabSettingRule("%AAB_Form1A", "form1A", AabValueType.Double, "5.0", "range 1..20"),
        AabSettingRule("%AAB_Form2B", "form2B", AabValueType.Float, "8.8", "range 0.1..30.0"),
        AabSettingRule("%AAB_Form2C", "form2C", AabValueType.Int, "18", "range 1..50"),
        AabSettingRule("%AAB_DimmingEnabled", "dimmingEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_DimmingStrength", "dimmingStrength", AabValueType.Int, "25", "range 0..65 (higher input is clamped on save)"),
        AabSettingRule("%AAB_DimmingExponent", "dimmingExponent", AabValueType.Float, "2.5", "range 0.5..5.0"),
        AabSettingRule("%AAB_DimmingThreshold", "dimmingThreshold", AabValueType.Int, "15", "range 0..255"),
        // S12.9c #6: spread is signed (−100=boost dimming in daylight … 0=off … 100=suppress in
        // daylight); circadian_dimming_graph.md `dim_val = 2 − (1 + (dimspread/100)·modifier)`.
        AabSettingRule("%AAB_DimSpread", "dimSpread", AabValueType.Int, "100", "range -100..100"),
        AabSettingRule("%AAB_PWMSensitive", "pwmSensitive", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_PWMExp", "pwmExponent", AabValueType.Float, "0.8", "range 0.1..3.0"),
        AabSettingRule("%AAB_Throttle", "throttleDefaultMs", AabValueType.Long, "1310", "range 100..60000"),
        AabSettingRule("%AAB_MinWait", "minWaitMs", AabValueType.Int, "25", "range 1..5000"),
        AabSettingRule("%AAB_MaxWait", "maxWaitMs", AabValueType.Int, "65", "range 1..5000 and >= minWaitMs"),
        AabSettingRule("%AAB_AnimSteps", "animSteps", AabValueType.Int, "20", "range 0..100"),
        AabSettingRule("%AAB_DeltaFactor", "deltaFactor", AabValueType.Float, "1.8", "range 0.1..10.0"),
        AabSettingRule("%AAB_ThreshBright", "thresholdBright", AabValueType.Float, "0.08", "range 0.0..1.0"),
        AabSettingRule("%AAB_ThreshDark", "thresholdDark", AabValueType.Float, "0.3", "range 0.0..1.0"),
        AabSettingRule("%AAB_ThreshDim", "thresholdDim", AabValueType.Float, "0.25", "range 0.0..1.0"),
        // G2R-F85: ThreshDynamic removed (computed runtime value)
        AabSettingRule("%AAB_ThreshSteepness", "thresholdSteepness", AabValueType.Float, "2.1", "range 0.1..10.0"),
        AabSettingRule("%AAB_ThreshMidpoint", "thresholdMidpoint", AabValueType.Double, "4.0", "range 0.0..6.0 (derived=log10(zone2End))"),
        AabSettingRule("%AAB_ScalingUse", "scalingEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_ScaleSpread", "scaleSpread", AabValueType.Int, "15", "range 1..100"),
        AabSettingRule("%AAB_ScaleSteepness", "scaleSteepness", AabValueType.Int, "6", "range 1..20"),
        AabSettingRule("%AAB_ScaleTaperMidpoint", "scaleTaperMidpoint", AabValueType.Int, "190", "range 130..240"),
        AabSettingRule("%AAB_ScaleTaperSteepness", "scaleTaperSteepness", AabValueType.Float, "0.075", "range 0.001..1.0"),
        AabSettingRule("%AAB_ScaleTransitionFactor", "scaleTransitionFactor", AabValueType.Float, "0.1", "range 0.0..1.0"),
        AabSettingRule("%AAB_TrustUnreliable", "trustUnreliableSensor", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_QSUse", "quickSettingsEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_NotifyUse", "notificationsEnabled", AabValueType.Boolean, "true", "must be true|false"),
        AabSettingRule("%AAB_Debug", "debugLevel", AabValueType.Int, "0", "range 0..9"),
        AabSettingRule("%AAB_PanicSensitivity", "panicSensitivity", AabValueType.Int, "8", "range 0..10"),
        AabSettingRule("%AAB_ContextOverride", "contextOverride", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_PanicPlugged", "panicRequiresPlugged", AabValueType.Boolean, "false", "must be true|false"),
        // D-151/D-152: rebuild-only display-toggle fields (invented %AAB_ names, D-116 precedent)
        AabSettingRule("%AAB_NightLight", "nightLightEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_NightLightTemp", "nightLightTemperature", AabValueType.Int, "device default", "range 1000..10000, or unset = device default"),
        AabSettingRule("%AAB_NightLightCircadian", "nightLightCircadianEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_Daltonizer", "daltonizerMode", AabValueType.String, DALTONIZER_OFF, "one of OFF|GRAYSCALE|PROTANOMALY|DEUTERANOMALY|TRITANOMALY"),
        AabSettingRule("%AAB_Inversion", "inversionEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_AlwaysOnDisplay", "alwaysOnDisplayEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_StayAwakeCharging", "stayAwakeChargingEnabled", AabValueType.Boolean, "false", "must be true|false"),
        AabSettingRule("%AAB_HdrForceSdr", "hdrForceSdrEnabled", AabValueType.Boolean, "false", "must be true|false (inert below Android 14)"),
    )
}
