package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first

/**
 * Typed DataStore-backed setting persistence replacing Tasker variables/scenes. The validated
 * [AabSettings] model is the single source of truth (S11 retired the toy `SettingsState` mirror).
 */
class SettingsStore(
    private val dataStore: DataStore<AabSettings>? = null,
) {
    suspend fun readRawSettings(): AabSettings {
        val store = dataStore ?: return AabSettings()
        return store.data.first().validate()
    }

    suspend fun writeRawSettings(settings: AabSettings) {
        val store = dataStore ?: return
        store.updateData { settings.validate() }
    }
}
