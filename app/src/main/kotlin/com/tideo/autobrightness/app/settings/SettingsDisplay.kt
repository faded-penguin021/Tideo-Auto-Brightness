package com.tideo.autobrightness.app.settings

/**
 * One row of the "full settings list" the Tasker AAB Profile dashboard shows (profile.md elements0:
 * "compares active settings vs factory defaults, tuned values shown yellow"). [changed] is true when
 * the value differs from the factory default → the UI highlights it in theme gold (S12.7h, G2R-F38).
 */
data class SettingDisplayRow(
    val label: String,
    val taskerVariable: String,
    val value: String,
    val changed: Boolean,
)

/**
 * Every user-facing setting from [AabSettingsContract] with its current value, paired against a
 * [reference] (factory default by default) so the UI can gold-highlight tuned values (G2R-F38). No
 * reflection (owner caution): the per-key extractor is an explicit `when` kept in lock-step with the
 * contract. Runtime/identity keys (serviceEnabled, contextOverride) are excluded — they are not
 * profile parameters the dashboard compares.
 */
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

/**
 * Keys excluded from the changed-vs-default diff (G2R-F84 + modal exclusions). `serviceEnabled` and
 * `contextOverride` are runtime/identity latches (never profile parameters). `debugLevel`,
 * `detectOverrides`, `quickSettingsEnabled` and `notificationsEnabled` are GLOBAL preferences the
 * profile load deliberately preserves (G2-F8/G2R-F9) — listing them in a profile diff is misleading.
 * `thresholdMidpoint` is DERIVED (log10(zone2End), task570 act39), not an independent tuned value.
 */
private val EXCLUDED_KEYS = setOf(
    "serviceEnabled",
    "contextOverride",
    "debugLevel",
    "detectOverrides",
    "quickSettingsEnabled",
    "notificationsEnabled",
    "thresholdMidpoint",
)

/**
 * Friendly, end-user labels for the diff list (G2R-F84): the raw camelCase/`form1A` names are
 * meaningless to a user. Explicit map (no reflection, owner caution); kept in step with the screen
 * labels so the dashboard reads the same as the editors. Anything unmapped falls back to [humanize].
 */
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
)

internal fun friendlyLabel(key: String, taskerVariable: String): String =
    FRIENDLY_LABELS[key] ?: humanize(taskerVariable)

/** Formatted value for a contract key. Explicit `when` (no reflection) — keep aligned with the contract. */
internal fun AabSettings.valueFor(key: String): String = when (key) {
    "serviceEnabled" -> serviceEnabled.toString()
    "detectOverrides" -> detectOverrides.toString()
    "minBrightness" -> minBrightness.toString()
    "maxBrightness" -> maxBrightness.toString()
    "offset" -> offset.toString()
    "scale" -> scale.toString()
    "zone1End" -> zone1End.toString()
    "zone2End" -> zone2End.toString()
    // G2R-F70: form1A is a Double; show whole values without a trailing ".0" (5.0 → "5", 5.833 → "5.833").
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
    "contextOverride" -> contextOverride.toString()
    else -> ""
}

/** "%AAB_MinBright" → "Min Bright": drop the prefix, space camelCase boundaries (readable, faithful). */
private fun humanize(taskerVariable: String): String {
    val bare = taskerVariable.removePrefix("%AAB_")
    val sb = StringBuilder()
    bare.forEachIndexed { i, c ->
        if (i > 0 && c.isUpperCase() && (bare[i - 1].isLowerCase() || bare[i - 1].isDigit())) sb.append(' ')
        sb.append(c)
    }
    return sb.toString()
}
