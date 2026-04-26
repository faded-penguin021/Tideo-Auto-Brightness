package com.tideo.autobrightness.data

import java.io.File

class SettingsFileAdapter(
    private val file: File,
) {
    fun export(settings: BrightnessSettings) {
        file.writeText("${settings.defaultThrottleMs},${settings.overrideDetectionEnabled}")
    }

    fun import(): BrightnessSettings {
        if (!file.exists()) return BrightnessSettings()
        val raw = file.readText().split(',')
        return BrightnessSettings(
            defaultThrottleMs = raw.getOrNull(0)?.toLongOrNull() ?: 500,
            overrideDetectionEnabled = raw.getOrNull(1)?.toBooleanStrictOrNull() ?: true,
        )
    }
}
