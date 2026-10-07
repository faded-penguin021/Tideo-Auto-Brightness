package com.tideo.autobrightness.app.settings

import androidx.annotation.StringRes
import com.tideo.autobrightness.R

/** One row of the full settings list (profiles.md elements0): compares vs factory defaults, gold highlight (S12.7h, G2R-F38). */
data class SettingDisplayRow(
    @StringRes val labelRes: Int,
    val taskerVariable: String,
    val value: String,
    val changed: Boolean,
)

/** All user-facing settings paired against reference (factory default) for diff display (G2R-F38).
 * Explicit `when` extractor (no reflection, owner caution). Excludes runtime/identity keys. */
fun AabSettings.displayRows(reference: AabSettings = AabSettings()): List<SettingDisplayRow> =
    AabSettingsContract.rules
        .filter { it.key !in EXCLUDED_KEYS }
        .map { rule ->
            val mine = valueFor(rule.key)
            val theirs = reference.valueFor(rule.key)
            SettingDisplayRow(
                labelRes = SETTING_LABELS.getValue(rule.key),
                taskerVariable = rule.taskerVariable,
                value = mine,
                changed = mine != theirs,
            )
        }

/** The number of settings that differ from [reference] (factory default) — the dashboard summary. */
fun AabSettings.changedCount(reference: AabSettings = AabSettings()): Int =
    displayRows(reference).count { it.changed }

/** Excluded keys from diff (G2R-F84): runtime/identity latches, GLOBAL prefs preserved on load (G2-F8/G2R-F9/D-116), derived fields. */
private val EXCLUDED_KEYS = setOf(
    "serviceEnabled",
    "contextOverride",
    "debugLevel",
    "panicSensitivity",
    "panicRequiresPlugged",
    "detectOverrides",
    "quickSettingsEnabled",
    "notificationsEnabled",
    "thresholdMidpoint",
)

/** Label resources for the diff list (G2R-F84). */
private val SETTING_LABELS: Map<String, Int> = mapOf(
    "minBrightness" to R.string.misc_min_brightness,
    "maxBrightness" to R.string.misc_max_brightness,
    "offset" to R.string.settings_offset,
    "scale" to R.string.settings_scale,
    "zone1End" to R.string.curve_zone1_end,
    "zone2End" to R.string.curve_zone2_end,
    "form1A" to R.string.settings_form1a,
    "form2B" to R.string.settings_form2b,
    "form2C" to R.string.settings_form2c,
    "dimmingEnabled" to R.string.sd_header_super,
    "dimmingStrength" to R.string.settings_dimming_strength,
    "dimmingExponent" to R.string.settings_dimming_exponent,
    "dimmingThreshold" to R.string.settings_dimming_threshold,
    "dimSpread" to R.string.settings_dim_spread,
    "pwmSensitive" to R.string.settings_pwm_sensitive,
    "pwmExponent" to R.string.settings_pwm_exponent,
    "throttleDefaultMs" to R.string.settings_throttle,
    "minWaitMs" to R.string.settings_min_wait,
    "maxWaitMs" to R.string.settings_max_wait,
    "animSteps" to R.string.misc_anim_steps,
    "deltaFactor" to R.string.react_smoothing_delta,
    "thresholdBright" to R.string.react_bright,
    "thresholdDark" to R.string.react_dark,
    "thresholdDim" to R.string.react_dim,
    "thresholdSteepness" to R.string.react_curve_slope,
    "scalingEnabled" to R.string.circadian_scaling_header,
    "scaleSpread" to R.string.circadian_scale_spread,
    "scaleSteepness" to R.string.circadian_scale_steepness,
    "scaleTaperMidpoint" to R.string.circadian_taper_midpoint,
    "scaleTaperSteepness" to R.string.circadian_taper_steepness,
    "scaleTransitionFactor" to R.string.settings_scale_transition,
    "trustUnreliableSensor" to R.string.react_trust_sensor,
    "nightLightEnabled" to R.string.pd_night_light_switch,
    "nightLightTemperature" to R.string.settings_night_light_temperature,
    "nightLightCircadianEnabled" to R.string.settings_night_light_circadian,
    "extendedNightLightEnabled" to R.string.settings_night_light_extended,
    "daltonizerMode" to R.string.pd_daltonizer_label,
    "inversionEnabled" to R.string.pd_inversion,
    "alwaysOnDisplayEnabled" to R.string.pd_always_on,
    "stayAwakeChargingEnabled" to R.string.pd_stay_awake,
    "hdrForceSdrEnabled" to R.string.pd_hdr_force_sdr,
)

/** Formatted value for contract key. Explicit `when` (no reflection, keep aligned). */
internal fun AabSettings.valueFor(key: String): String = when (key) {
    "serviceEnabled" -> serviceEnabled.toString()
    "detectOverrides" -> detectOverrides.toString()
    "minBrightness" -> minBrightness.toString()
    "maxBrightness" -> maxBrightness.toString()
    "offset" -> offset.toString()
    "scale" -> scale.toString()
    "zone1End" -> zone1End.toString()
    "zone2End" -> zone2End.toString()
    // G2R-F70: drop ".0" from Doubles (5.0 → "5", 5.833 → "5.833").
    "form1A" -> if (form1A % 1.0 == 0.0) form1A.toInt().toString() else form1A.toString()
    "form2B" -> form2B.toString()
    "form2C" -> form2C.toString()
    "dimmingEnabled" -> dimmingEnabled.toString()
    "dimmingStrength" -> dimmingStrength.toString()
    "dimmingExponent" -> dimmingExponent.toString()
    "dimmingThreshold" -> dimmingThreshold.toString()
    "dimSpread" -> dimSpread.toString()
    "pwmSensitive" -> pwmSensitive.toString()
    "pwmExponent" -> pwmExponent.toString()
    "throttleDefaultMs" -> throttleDefaultMs.toString()
    "minWaitMs" -> minWaitMs.toString()
    "maxWaitMs" -> maxWaitMs.toString()
    "animSteps" -> animSteps.toString()
    "deltaFactor" -> deltaFactor.toString()
    "thresholdBright" -> thresholdBright.toString()
    "thresholdDark" -> thresholdDark.toString()
    "thresholdDim" -> thresholdDim.toString()
    "thresholdSteepness" -> thresholdSteepness.toString()
    "thresholdMidpoint" -> thresholdMidpoint.toString()
    "scalingEnabled" -> scalingEnabled.toString()
    "scaleSpread" -> scaleSpread.toString()
    "scaleSteepness" -> scaleSteepness.toString()
    "scaleTaperMidpoint" -> scaleTaperMidpoint.toString()
    "scaleTaperSteepness" -> scaleTaperSteepness.toString()
    "scaleTransitionFactor" -> scaleTransitionFactor.toString()
    "trustUnreliableSensor" -> trustUnreliableSensor.toString()
    "quickSettingsEnabled" -> quickSettingsEnabled.toString()
    "notificationsEnabled" -> notificationsEnabled.toString()
    "debugLevel" -> debugLevel.toString()
    "panicSensitivity" -> panicSensitivity.toString()
    "panicRequiresPlugged" -> panicRequiresPlugged.toString()
    "contextOverride" -> contextOverride.toString()
    // D-151/D-152: null temperature = "device default" (never written).
    "nightLightEnabled" -> nightLightEnabled.toString()
    "nightLightTemperature" -> nightLightTemperature?.toString() ?: "device default"
    "nightLightCircadianEnabled" -> nightLightCircadianEnabled.toString()
    "extendedNightLightEnabled" -> extendedNightLightEnabled.toString()
    "daltonizerMode" -> daltonizerMode
    "inversionEnabled" -> inversionEnabled.toString()
    "alwaysOnDisplayEnabled" -> alwaysOnDisplayEnabled.toString()
    "stayAwakeChargingEnabled" -> stayAwakeChargingEnabled.toString()
    "hdrForceSdrEnabled" -> hdrForceSdrEnabled.toString()
    // Fail fast on schema drift (S12.9c #2). SettingsDisplayContractDriftTest guards.
    else -> throw IllegalArgumentException("Unknown AabSettings key: '$key' (not in valueFor's when)")
}
