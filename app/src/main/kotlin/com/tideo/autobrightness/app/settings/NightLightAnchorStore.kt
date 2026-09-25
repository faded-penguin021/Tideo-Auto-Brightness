package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/** DC-056: the device Kelvin the D-154 ramp displaced. Present = the ramp owns the key. */
class NightLightAnchorStore(private val dataStore: DataStore<Preferences>) {

    suspend fun read(): Int? = dataStore.data.first()[ANCHOR_K]

    suspend fun write(kelvin: Int) {
        dataStore.edit { it[ANCHOR_K] = kelvin }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ANCHOR_K) }
    }

    private companion object {
        val ANCHOR_K = intPreferencesKey("night_light_anchor_k")
    }
}
