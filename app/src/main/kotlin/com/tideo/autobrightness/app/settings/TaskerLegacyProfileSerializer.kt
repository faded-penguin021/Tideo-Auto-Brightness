package com.tideo.autobrightness.app.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

object TaskerLegacyProfileSerializer {
    private val trueValues = setOf("true", "on", "1")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Decode a legacy Tasker AAB config into [AabSettings].
     *
     * Two on-disk formats are accepted:
     *  1. The real Tasker AAB **nested JSON** written by task637 `_ProfileManager.performSave`
     *     (XML L29365+) — a `{meta, general, misc, reactivity, circadian, superdimming}` object whose
     *     keys are snake_case (`min_bright`, `z1_end`, …). This is what the on-device app saved to
     *     `Download/AAB/configs/<name>.json`, i.e. the format the Profiles "Load" button must understand
     *     (G2R-F70). task637 `performLoad` (L29490+) is the authority for the key→`%AAB_` mapping
     *     ported below. The previous parser only handled `%AAB_Key=value` lines, so a real JSON config
     *     parsed to all-defaults — the file "loaded" by name but no setting actually changed.
     *  2. A flat `%AAB_Key = value` plaintext dump (older exports / hand-written fixtures).
     *
     * Derived coefficients form2A/form2D/form3A are NOT stored: task637 `performLoad` recomputes them
     * from form1A/zone1End/form2B/form2C/maxBright, exactly as [derivedCoefficients] does at read-time.
     */
    fun deserialize(raw: String): AabSettings {
        val trimmed = raw.trimStart()
        return if (trimmed.startsWith("{")) deserializeJson(trimmed) else deserializeKeyValue(raw)
    }

    /** Parse the nested Tasker AAB JSON config (task637 performSave/performLoad schema). */
    private fun deserializeJson(content: String): AabSettings {
        val root = runCatching { json.parseToJsonElement(content) as? JsonObject }.getOrNull()
            ?: return deserializeKeyValue(content)
        var s = AabSettings()

        (root["general"] as? JsonObject)?.let { g ->
            g.intRound("z1_end")?.let { s = s.copy(zone1End = it) }
            g.intRound("z2_end")?.let { s = s.copy(zone2End = it) }
            g.intRound("form1a")?.let { s = s.copy(form1A = it) }
            g.flt("form2b")?.let { s = s.copy(form2B = it) }
            g.intRound("form2c")?.let { s = s.copy(form2C = it) }
            // form2a/form2d/form3a are DERIVED (task637 performLoad recomputes them) — not stored.
        }
        (root["misc"] as? JsonObject)?.let { m ->
            m.intRound("min_bright")?.let { s = s.copy(minBrightness = it) }
            m.intRound("max_bright")?.let { s = s.copy(maxBrightness = it) }
            m.flt("scale")?.let { s = s.copy(scale = it) }
            m.intRound("offset")?.let { s = s.copy(offset = it) }
            m.intRound("anim_steps")?.let { s = s.copy(animSteps = it) }
            m.intRound("min_wait")?.let { s = s.copy(minWaitMs = it) }
            m.intRound("max_wait")?.let { s = s.copy(maxWaitMs = it) }
            m.flt("delta_factor")?.let { s = s.copy(deltaFactor = it) }
            m.longRound("throttle")?.let { s = s.copy(throttleDefaultMs = it) }
        }
        (root["reactivity"] as? JsonObject)?.let { r ->
            r.bool("detect_overrides")?.let { s = s.copy(detectOverrides = it) }
            r.bool("trust_unreliable")?.let { s = s.copy(trustUnreliableSensor = it) }
            r.flt("thresh_dark")?.let { s = s.copy(thresholdDark = it) }
            r.flt("thresh_dim")?.let { s = s.copy(thresholdDim = it) }
            r.flt("thresh_bright")?.let { s = s.copy(thresholdBright = it) }
            r.flt("thresh_steepness")?.let { s = s.copy(thresholdSteepness = it) }
            r.dbl("thresh_midpoint")?.let { s = s.copy(thresholdMidpoint = it) }
        }
        (root["circadian"] as? JsonObject)?.let { c ->
            c.bool("enabled")?.let { s = s.copy(scalingEnabled = it) }
            c.bool("qs_use")?.let { s = s.copy(quickSettingsEnabled = it) }
            c.intRound("spread")?.let { s = s.copy(scaleSpread = it) }
            c.flt("transition")?.let { s = s.copy(scaleTransitionFactor = it) }
            c.intRound("steepness")?.let { s = s.copy(scaleSteepness = it) }
            c.intRound("taper_mid")?.let { s = s.copy(scaleTaperMidpoint = it) }
            c.flt("taper_steep")?.let { s = s.copy(scaleTaperSteepness = it) }
        }
        (root["superdimming"] as? JsonObject)?.let { d ->
            d.bool("enabled")?.let { s = s.copy(dimmingEnabled = it) }
            d.bool("pwm_sensitive")?.let { s = s.copy(pwmSensitive = it) }
            d.intRound("threshold")?.let { s = s.copy(dimmingThreshold = it) }
            d.intRound("strength")?.let { s = s.copy(dimmingStrength = it) }
            d.flt("exponent")?.let { s = s.copy(dimmingExponent = it) }
            d.intRound("spread")?.let { s = s.copy(dimSpread = it) }
            d.flt("pwm_exp")?.let { s = s.copy(pwmExponent = it) }
        }
        return s.validate()
    }

    /** Parse a flat `%AAB_Key = value` dump (older exports / fixtures). */
    private fun deserializeKeyValue(raw: String): AabSettings {
        val map = raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("%AAB_") && it.contains('=') }
            .associate { line ->
                val index = line.indexOf('=')
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }

        var settings = AabSettings()

        map["%AAB_Service"]?.let { settings = settings.copy(serviceEnabled = it.asBoolean(settings.serviceEnabled)) }
        map["%AAB_DetectOverrides"]?.let { settings = settings.copy(detectOverrides = it.asBoolean(settings.detectOverrides)) }
        map["%AAB_MinBright"]?.toIntOrNull()?.let { settings = settings.copy(minBrightness = it) }
        map["%AAB_MaxBright"]?.toIntOrNull()?.let { settings = settings.copy(maxBrightness = it) }
        map["%AAB_Offset"]?.toIntOrNull()?.let { settings = settings.copy(offset = it) }
        map["%AAB_Scale"]?.toFloatOrNull()?.let { settings = settings.copy(scale = it) }
        map["%AAB_Zone1End"]?.toIntOrNull()?.let { settings = settings.copy(zone1End = it) }
        map["%AAB_Zone2End"]?.toIntOrNull()?.let { settings = settings.copy(zone2End = it) }
        map["%AAB_Form1A"]?.toIntOrNull()?.let { settings = settings.copy(form1A = it) }
        map["%AAB_Form2B"]?.toFloatOrNull()?.let { settings = settings.copy(form2B = it) }
        map["%AAB_Form2C"]?.toIntOrNull()?.let { settings = settings.copy(form2C = it) }
        map["%AAB_DimmingEnabled"]?.let { settings = settings.copy(dimmingEnabled = it.asBoolean(settings.dimmingEnabled)) }
        map["%AAB_DimmingStrength"]?.toIntOrNull()?.let { settings = settings.copy(dimmingStrength = it) }
        map["%AAB_DimmingExponent"]?.toFloatOrNull()?.let { settings = settings.copy(dimmingExponent = it) }
        map["%AAB_DimmingThreshold"]?.toIntOrNull()?.let { settings = settings.copy(dimmingThreshold = it) }
        map["%AAB_DimSpread"]?.toIntOrNull()?.let { settings = settings.copy(dimSpread = it) }
        map["%AAB_PWMSensitive"]?.let { settings = settings.copy(pwmSensitive = it.asBoolean(settings.pwmSensitive)) }
        map["%AAB_PWMExp"]?.toFloatOrNull()?.let { settings = settings.copy(pwmExponent = it) }
        // Tasker uses %AAB_Throttle; legacy exports may also use %AAB_DefaultThrottle (old naming)
        (map["%AAB_Throttle"] ?: map["%AAB_DefaultThrottle"])?.toLongOrNull()?.let { settings = settings.copy(throttleDefaultMs = it) }
        map["%AAB_MinWait"]?.toIntOrNull()?.let { settings = settings.copy(minWaitMs = it) }
        map["%AAB_MaxWait"]?.toIntOrNull()?.let { settings = settings.copy(maxWaitMs = it) }
        map["%AAB_DeltaFactor"]?.toFloatOrNull()?.let { settings = settings.copy(deltaFactor = it) }
        map["%AAB_ThreshBright"]?.toFloatOrNull()?.let { settings = settings.copy(thresholdBright = it) }
        map["%AAB_ThreshDark"]?.toFloatOrNull()?.let { settings = settings.copy(thresholdDark = it) }
        map["%AAB_ThreshDim"]?.toFloatOrNull()?.let { settings = settings.copy(thresholdDim = it) }
        map["%AAB_ThreshDynamic"]?.toIntOrNull()?.let { settings = settings.copy(thresholdDynamic = it) }
        map["%AAB_ThreshSteepness"]?.toFloatOrNull()?.let { settings = settings.copy(thresholdSteepness = it) }
        map["%AAB_ScalingUse"]?.let { settings = settings.copy(scalingEnabled = it.asBoolean(settings.scalingEnabled)) }
        map["%AAB_ScaleSpread"]?.toIntOrNull()?.let { settings = settings.copy(scaleSpread = it) }
        map["%AAB_ScaleSteepness"]?.toIntOrNull()?.let { settings = settings.copy(scaleSteepness = it) }
        map["%AAB_ScaleTaperMidpoint"]?.toIntOrNull()?.let { settings = settings.copy(scaleTaperMidpoint = it) }
        map["%AAB_ScaleTaperSteepness"]?.toFloatOrNull()?.let { settings = settings.copy(scaleTaperSteepness = it) }
        map["%AAB_ScaleTransitionFactor"]?.toFloatOrNull()?.let { settings = settings.copy(scaleTransitionFactor = it) }
        map["%AAB_TrustUnreliable"]?.let { settings = settings.copy(trustUnreliableSensor = it.asBoolean(settings.trustUnreliableSensor)) }
        map["%AAB_QSUse"]?.let { settings = settings.copy(quickSettingsEnabled = it.asBoolean(settings.quickSettingsEnabled)) }
        map["%AAB_NotifyUse"]?.let { settings = settings.copy(notificationsEnabled = it.asBoolean(settings.notificationsEnabled)) }
        map["%AAB_Debug"]?.toIntOrNull()?.let { settings = settings.copy(debugLevel = it) }
        map["%AAB_AnimSteps"]?.toIntOrNull()?.let { settings = settings.copy(animSteps = it) }
        map["%AAB_ThreshMidpoint"]?.toDoubleOrNull()?.let { settings = settings.copy(thresholdMidpoint = it) }
        map["%AAB_ContextOverride"]?.let { settings = settings.copy(contextOverride = it.asBoolean(settings.contextOverride)) }
        map["%AAB_SetupTitle"]?.let { settings = settings.copy(setupTitle = it) }

        return settings.validate()
    }

    private fun String.asBoolean(default: Boolean): Boolean {
        return when (trim().lowercase()) {
            in trueValues -> true
            "false", "off", "0" -> false
            else -> default
        }
    }

    // --- nested-JSON value readers (tolerant: numbers stored as JSON doubles, booleans as JSON bools
    //     by performSave's getG/getI/getB, but accept "On"/"Off"/"1" string variants too) ---

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.dbl(key: String): Double? = prim(key)?.doubleOrNull
    private fun JsonObject.flt(key: String): Float? = prim(key)?.doubleOrNull?.toFloat()
    private fun JsonObject.intRound(key: String): Int? = prim(key)?.doubleOrNull?.let { Math.round(it).toInt() }
    private fun JsonObject.longRound(key: String): Long? = prim(key)?.doubleOrNull?.let { Math.round(it) }
    private fun JsonObject.bool(key: String): Boolean? {
        val p = prim(key) ?: return null
        p.booleanOrNull?.let { return it }
        return when (p.content.trim().lowercase()) {
            in trueValues -> true
            "false", "off", "0" -> false
            else -> null
        }
    }
}
