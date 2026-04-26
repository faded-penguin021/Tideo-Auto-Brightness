package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import com.tideo.autobrightness.app.state.SettingsState
import kotlinx.coroutines.flow.first

/**
 * Typed DataStore-backed setting persistence replacing Tasker variables/scenes.
 */
class SettingsStore(
    private val dataStore: DataStore<AabSettings>? = null,
) {
    suspend fun readSettings(): SettingsState {
        val store = dataStore ?: return SettingsState()
        val settings = store.data.first().validate()
        return AabSettingsMapper.toUiState(settings)
    }

    suspend fun writeSettings(settings: SettingsState) {
        val store = dataStore ?: return
        store.updateData { current ->
            AabSettingsMapper.fromUiState(settings, current).validate()
        }
    }

    suspend fun readRawSettings(): AabSettings {
        val store = dataStore ?: return AabSettings()
        return store.data.first().validate()
    }

    suspend fun writeRawSettings(settings: AabSettings) {
        val store = dataStore ?: return
        store.updateData { settings.validate() }
    }
}
