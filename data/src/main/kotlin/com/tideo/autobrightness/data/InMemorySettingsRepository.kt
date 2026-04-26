package com.tideo.autobrightness.data

class InMemorySettingsRepository : SettingsRepository {
    private var state = BrightnessSettings()

    override fun read(): BrightnessSettings = state

    override fun write(settings: BrightnessSettings) {
        state = settings
    }
}
