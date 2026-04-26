package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.tideo.autobrightness.app.state.SettingsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed setting persistence replacing Tasker variables/scenes.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>? = null,
) {
    suspend fun readSettings(): SettingsState {
        val store = dataStore ?: return SettingsState()
        return store.data
            .map { prefs ->
                SettingsState(
                    enabled = prefs[ENABLED] ?: true,
                    minBrightness = prefs[MIN_BRIGHTNESS] ?: 0.05f,
                    maxBrightness = prefs[MAX_BRIGHTNESS] ?: 1f,
                )
            }
            .first()
    }

    suspend fun writeSettings(settings: SettingsState) {
        val store = dataStore ?: return
        store.edit { prefs ->
            prefs[ENABLED] = settings.enabled
            prefs[MIN_BRIGHTNESS] = settings.minBrightness
            prefs[MAX_BRIGHTNESS] = settings.maxBrightness
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val MIN_BRIGHTNESS = floatPreferencesKey("min_brightness")
        val MAX_BRIGHTNESS = floatPreferencesKey("max_brightness")
    }
}
