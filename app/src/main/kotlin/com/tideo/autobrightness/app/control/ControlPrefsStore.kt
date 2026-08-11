package com.tideo.autobrightness.app.control

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * D-157/D-105: external intent-control opt-in gate (default OFF). Separate store so profile apply/import
 * can never flip it, avoiding schema/clamp churn.
 */
class ControlPrefsStore(private val dataStore: DataStore<Preferences>) {
    val externalControlEnabled: Flow<Boolean> = dataStore.data.map { it[EXTERNAL_CONTROL] ?: false }

    suspend fun setExternalControlEnabled(enabled: Boolean) {
        dataStore.edit { it[EXTERNAL_CONTROL] = enabled }
    }

    /** D-172: force-dark opt-in (debug.hwui.force_dark via Shizuku/root). Default OFF, re-asserted at service start. */
    val forceDarkEnabled: Flow<Boolean> = dataStore.data.map { it[FORCE_DARK] ?: false }

    suspend fun setForceDarkEnabled(enabled: Boolean) {
        dataStore.edit { it[FORCE_DARK] = enabled }
    }

    private companion object {
        val EXTERNAL_CONTROL = booleanPreferencesKey("external_control_enabled")
        val FORCE_DARK = booleanPreferencesKey("force_dark_enabled")
    }
}
