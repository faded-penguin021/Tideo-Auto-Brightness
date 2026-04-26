package com.tideo.autobrightness.app.settings

object TaskerLegacyProfileSerializer {
    private val trueValues = setOf("true", "on", "1")

    fun deserialize(raw: String): AabSettings {
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
        map["%AAB_Scale"]?.toIntOrNull()?.let { settings = settings.copy(scale = it) }
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
        map["%AAB_DefaultThrottle"]?.toLongOrNull()?.let { settings = settings.copy(throttleDefaultMs = it) }
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

        return settings.validate()
    }

    private fun String.asBoolean(default: Boolean): Boolean {
        return when (trim().lowercase()) {
            in trueValues -> true
            "false", "off", "0" -> false
            else -> default
        }
    }
}
