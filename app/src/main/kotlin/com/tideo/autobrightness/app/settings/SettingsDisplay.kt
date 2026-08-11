package com.tideo.autobrightness.app.settings

/** One row of the full settings list (profiles.md elements0): compares vs factory defaults, gold highlight (S12.7h, G2R-F38). */
data class SettingDisplayRow(
    val label: String,
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
                label = friendlyLabel(rule.key, rule.taskerVariable),
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

/** User-friendly labels for diff list (G2R-F84); unmapped keys fall back to [humanize] (no reflection). */
private val FRIENDLY_LABELS: Map<String, String> = mapOf(
    "minBrightness" to "Min brightness",
    "maxBrightness" to "Max brightness",
    "offset" to "Brightness offset",
    "scale" to "Brightness scale",
    "zone1End" to "Zone 1 end (lux)",
    "zone2End" to "Zone 2 end (lux)",
    "form1A" to "Zone 1 scaling",
    "form2B" to "Zone 2 scaling",
    "form2C" to "Zone 2 offset",
    "dimmingEnabled" to "Super dimming",
    "dimmingStrength" to "Dimming strength",
    "dimmingExponent" to "Dimming curve",
    "dimmingThreshold" to "Dimming threshold",
    "dimSpread" to "Dimming spread",
    "pwmSensitive" to "PWM-sensitive mode",
    "pwmExponent" to "PWM curve",
    "throttleDefaultMs" to "Throttle (ms)",
    "minWaitMs" to "Min step wait (ms)",
    "maxWaitMs" to "Max step wait (ms)",
    "animSteps" to "Animation steps",
    "deltaFactor" to "Smoothing Δ",
    "thresholdBright" to "Bright threshold",
    "thresholdDark" to "Dark threshold",
    "thresholdDim" to "Dim threshold",
    "thresholdSteepness" to "Curve slope",
    "scalingEnabled" to "Circadian scaling",
    "scaleSpread" to "Scale spread",
    "scaleSteepness" to "Scale steepness",
    "scaleTaperMidpoint" to "Taper midpoint",
    "scaleTaperSteepness" to "Taper steepness",
    "scaleTransitionFactor" to "Scale transition",
    "trustUnreliableSensor" to "Trust low-accuracy sensor",
    "nightLightEnabled" to "Night Light",
    "nightLightTemperature" to "Night Light temperature",
    "nightLightCircadianEnabled" to "Night Light circadian tracking",
    "daltonizerMode" to "Color correction",
    "inversionEnabled" to "Color inversion",
    "alwaysOnDisplayEnabled" to "Always-on display",
    "stayAwakeChargingEnabled" to "Stay awake while charging",
    "hdrForceSdrEnabled" to "Force SDR (disable HDR)",
)

internal fun friendlyLabel(key: String, taskerVariable: String): String =
    FRIENDLY_LABELS[key] ?: humanize(taskerVariable)

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
    "daltonizerMode" -> daltonizerMode
    "inversionEnabled" -> inversionEnabled.toString()
    "alwaysOnDisplayEnabled" -> alwaysOnDisplayEnabled.toString()
    "stayAwakeChargingEnabled" -> stayAwakeChargingEnabled.toString()
    "hdrForceSdrEnabled" -> hdrForceSdrEnabled.toString()
    // Fail fast on schema drift (S12.9c #2). SettingsDisplayContractDriftTest guards.
    else -> throw IllegalArgumentException("Unknown AabSettings key: '$key' (not in valueFor's when)")
}

/** "%AAB_MinBright" → "Min Bright": drop prefix, space camelCase (readable, faithful). */
private fun humanize(taskerVariable: String): String {
    val bare = taskerVariable.removePrefix("%AAB_")
    val sb = StringBuilder()
    bare.forEachIndexed { i, c ->
        if (i > 0 && c.isUpperCase() && (bare[i - 1].isLowerCase() || bare[i - 1].isDigit())) sb.append(' ')
        sb.append(c)
    }
    return sb.toString()
}
