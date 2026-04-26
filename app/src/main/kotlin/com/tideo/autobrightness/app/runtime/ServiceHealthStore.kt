package com.tideo.autobrightness.app.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServiceHealthTelemetry(
    val lastSensorTimestampMs: Long? = null,
    val lastApplyTimestampMs: Long? = null,
    val degradedMode: Boolean = false,
    val degradedReason: String? = null,
)

class ServiceHealthStore(
    private val dataStore: DataStore<Preferences>,
) {
    val telemetry: Flow<ServiceHealthTelemetry> = dataStore.data.map { prefs ->
        ServiceHealthTelemetry(
            lastSensorTimestampMs = prefs[LAST_SENSOR_TS],
            lastApplyTimestampMs = prefs[LAST_APPLY_TS],
            degradedMode = prefs[DEGRADED_MODE] ?: false,
            degradedReason = prefs[DEGRADED_REASON],
        )
    }

    suspend fun markSensorSampled(timestampMs: Long) {
        dataStore.edit { it[LAST_SENSOR_TS] = timestampMs }
    }

    suspend fun markApplied(timestampMs: Long) {
        dataStore.edit {
            it[LAST_APPLY_TS] = timestampMs
            it[DEGRADED_MODE] = false
            it.remove(DEGRADED_REASON)
        }
    }

    suspend fun markDegraded(reason: String) {
        dataStore.edit {
            it[DEGRADED_MODE] = true
            it[DEGRADED_REASON] = reason
        }
    }

    private companion object {
        val LAST_SENSOR_TS = longPreferencesKey("last_sensor_timestamp_ms")
        val LAST_APPLY_TS = longPreferencesKey("last_apply_timestamp_ms")
        val DEGRADED_MODE = booleanPreferencesKey("degraded_mode")
        val DEGRADED_REASON = stringPreferencesKey("degraded_reason")
    }
}
