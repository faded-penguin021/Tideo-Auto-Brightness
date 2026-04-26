package com.tideo.autobrightness.data

interface SettingsRepository {
    fun read(): BrightnessSettings
    fun write(settings: BrightnessSettings)
}

data class BrightnessSettings(
    val defaultThrottleMs: Long = 500,
    val overrideDetectionEnabled: Boolean = true,
)
