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
    // G2R-F70: %AAB_Form1A is a CONTINUOUS curve coefficient in Tasker (the wizard suggests e.g.
    // 5.833); modelling it as Int silently rounded a loaded value (5.833 → 6). Stored as Double so the
    // decimal survives a legacy load / wizard apply. Old int-encoded values read back transparently.
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
    // Tasker: %AAB_PanicSensitivity — shake intensity/duration required to confirm a Panic (Reset)
    // gesture (task528 _PanicButton A2). 0 = pass-through (no shake required); 10 = long vigorous
    // shake. Default 8 (the Tasker A1 default when the var is unset). Global pref, not a profile
    // parameter — like debugLevel. (D-116)
    val panicSensitivity: Int = 8,
    // Tasker: %AAB_ContextOverride — runtime "manual context lock" latch. When true, ALL context
    // watchers are suppressed (contexts_spec §1.1 gate fires only when ContextOverride != true).
    // The baseline/fresh-install default MUST be false or context switching never works (D-038).
    // A saved override-profile stores true here; the baseline AabSettings does not.
    val contextOverride: Boolean = false,
    // Tasker: %AAB_SetupTitle; onboarding dialog title (D-008)
    val setupTitle: String = "Advanced Auto Brightness Setup",
    // --- Privileged display toggles (rebuild-only, no Tasker source — D-151/D-152). ALL of the
    // Privileged Display toggles are per-profile screen state, applied on profile change by
    // DisplayTogglesCoordinator (ELEVATED-gated, no-op below; the super-dimming precedent:
    // profile fields drive a secure feature). Defaults are the "leave the device alone" values:
    // a profile chain that never edits them never writes.
    val nightLightEnabled: Boolean = false,
    // Night Light intensity in Kelvin; null = this profile has no temperature opinion (the device
    // value — a persistent system preference — is left untouched). Written only when non-null.
    val nightLightTemperature: Int? = null,
    // D-154: the temperature follows the circadian tanh modifier while the service runs (warmest
    // at night — anchored on nightLightTemperature, or the AOSP default when null — relaxing to
    // the AOSP max/weakest filter in daylight). While on, the ticker owns the temperature and
    // manual temperature changes do NOT stick (unlike every other display field).
    val nightLightCircadianEnabled: Boolean = false,
    // Color-correction mode: one of [DALTONIZER_MODES] ("OFF", "GRAYSCALE", or a correction
    // matrix). Stored as a STRING enum name (the D-150 lesson): an unknown value from a newer
    // schema validates back to "OFF" instead of failing the whole settings file.
    val daltonizerMode: String = DALTONIZER_OFF,
    val inversionEnabled: Boolean = false,
    val alwaysOnDisplayEnabled: Boolean = false,
    val stayAwakeChargingEnabled: Boolean = false,
    // Android-14+ force-SDR (experimental). On older devices the field is inert: the coordinator
    // checks the controller's availability gate and never writes it.
    val hdrForceSdrEnabled: Boolean = false,
)

/** [AabSettings.daltonizerMode] value for "color correction off". */
const val DALTONIZER_OFF = "OFF"

/**
 * Valid [AabSettings.daltonizerMode] values — the name-for-name mirror of the platform
 * `DaltonizerMode` enum (kept platform-import-free here; `DisplayTogglesCoordinatorTest` guards
 * the two sets against drift). Anything else validates back to [DALTONIZER_OFF].
 */
val DALTONIZER_MODES: Set<String> =
    setOf(DALTONIZER_OFF, "GRAYSCALE", "PROTANOMALY", "DEUTERANOMALY", "TRITANOMALY")

// Schema v2: added animSteps, thresholdMidpoint, contextOverride, setupTitle; scale Float (was Int v1)
// Schema v3: removed thresholdDynamic (G2R-F85) — it was never an input. The stale key is dropped on
// read via the serializer's ignoreUnknownKeys; migration only bumps the version stamp.
const val CURRENT_SCHEMA_VERSION = 3

enum class AabValueType {
    Boolean,
    Int,
    Long,
    Float,
    Double,
    String,
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
        AabSettingRule("%AAB_DimmingStrength", "dimmingStrength", AabValueType.Int, "25", "range 0..100"),
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
        // %AAB_ThreshDynamic removed (G2R-F85): it is a computed runtime value, not a user setting.
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
        // Rebuild-only display-toggle profile fields (D-151/D-152) — invented %AAB_ names, the
        // D-116 panicSensitivity precedent (no Tasker source; the name exists for diff/export display).
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
