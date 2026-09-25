package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NightLightVerdictStore(
    private val dataStore: DataStore<Preferences>,
    build: String,
) {
    private val stamp = "$PROBE_VERSION|$build"

    val notHonouredFlow: Flow<Boolean> = dataStore.data.map { it[NOT_HONOURED] == stamp }

    suspend fun isNotHonoured(): Boolean = notHonouredFlow.first()

    suspend fun markNotHonoured() {
        dataStore.edit { it[NOT_HONOURED] = stamp }
    }

    private companion object {
        const val PROBE_VERSION = 1
        val NOT_HONOURED = stringPreferencesKey("night_light_key_not_honoured")
    }
}
