package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.app.state.SettingsState

object AabSettingsMapper {
    fun toUiState(settings: AabSettings): SettingsState {
        return SettingsState(
            enabled = settings.serviceEnabled,
            minBrightness = (settings.minBrightness / 255f).coerceIn(0f, 1f),
            maxBrightness = (settings.maxBrightness / 255f).coerceIn(0f, 1f),
        )
    }

    fun fromUiState(uiState: SettingsState, current: AabSettings): AabSettings {
        val min = (uiState.minBrightness.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        val max = (uiState.maxBrightness.coerceIn(0f, 1f) * 255).toInt().coerceIn(min, 255)
        return current.copy(
            serviceEnabled = uiState.enabled,
            minBrightness = min,
            maxBrightness = max,
        )
    }
}

fun AabSettings.validate(): AabSettings {
    val clampedMinBrightness = minBrightness.coerceIn(1, 255)
    val clampedZone1End = zone1End.coerceIn(1, 20_000)
    val clampedMinWait = minWaitMs.coerceIn(1, 5_000)
    return copy(
        minBrightness = clampedMinBrightness,
        maxBrightness = maxBrightness.coerceIn(clampedMinBrightness, 255),
        offset = offset.coerceIn(-255, 255),
        scale = scale.coerceIn(1, 10),
        zone1End = clampedZone1End,
        zone2End = zone2End.coerceIn(clampedZone1End, 100_000),
        form1A = form1A.coerceIn(1, 20),
        form2B = form2B.coerceIn(0.1f, 30f),
        form2C = form2C.coerceIn(1, 50),
        dimmingStrength = dimmingStrength.coerceIn(0, 100),
        dimmingExponent = dimmingExponent.coerceIn(0.5f, 5f),
        dimmingThreshold = dimmingThreshold.coerceIn(0, 100),
        dimSpread = dimSpread.coerceIn(1, 300),
        pwmExponent = pwmExponent.coerceIn(0.1f, 3f),
        throttleDefaultMs = throttleDefaultMs.coerceIn(100, 60_000),
        minWaitMs = clampedMinWait,
        maxWaitMs = maxWaitMs.coerceIn(clampedMinWait, 5_000),
        deltaFactor = deltaFactor.coerceIn(0.1f, 10f),
        thresholdBright = thresholdBright.coerceIn(0f, 1f),
        thresholdDark = thresholdDark.coerceIn(0f, 1f),
        thresholdDim = thresholdDim.coerceIn(0f, 1f),
        thresholdDynamic = thresholdDynamic.coerceIn(1, 20),
        thresholdSteepness = thresholdSteepness.coerceIn(0.1f, 10f),
        scaleSpread = scaleSpread.coerceIn(1, 100),
        scaleSteepness = scaleSteepness.coerceIn(1, 20),
        scaleTaperMidpoint = scaleTaperMidpoint.coerceIn(1, 255),
        scaleTaperSteepness = scaleTaperSteepness.coerceIn(0.001f, 1f),
        scaleTransitionFactor = scaleTransitionFactor.coerceIn(0f, 1f),
        debugLevel = debugLevel.coerceIn(0, 5),
    )
}
