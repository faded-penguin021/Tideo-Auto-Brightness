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
                label = humanize(rule.taskerVariable),
                taskerVariable = rule.taskerVariable,
                value = mine,
                changed = mine != theirs,
            )
        }

/** The number of settings that differ from [reference] (factory default) — the dashboard summary. */
fun AabSettings.changedCount(reference: AabSettings = AabSettings()): Int =
    displayRows(reference).count { it.changed }

private val EXCLUDED_KEYS = setOf("serviceEnabled", "contextOverride")

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
    "form1A" -> form1A.toString()
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
    "thresholdDynamic" -> thresholdDynamic.toString()
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
