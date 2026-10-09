package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/** DD-059: Night Light as Tideo found it before its first Night Light write; [kelvin] null when unreadable. */
data class NightLightPrior(val activated: Boolean, val kelvin: Int?)

/** DC-056: the Kelvin the D-154 ramp displaced (present = it owns the key); DD-059's [NightLightPrior]. */
class NightLightAnchorStore(private val dataStore: DataStore<Preferences>) {

    suspend fun read(): Int? = dataStore.data.first()[ANCHOR_K]

    suspend fun write(kelvin: Int) {
        dataStore.edit { it[ANCHOR_K] = kelvin }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ANCHOR_K) }
    }

    suspend fun readPrior(): NightLightPrior? {
        val prefs = dataStore.data.first()
        val activated = prefs[PRIOR_ON] ?: return null
        return NightLightPrior(activated, prefs[PRIOR_K])
    }

    suspend fun writePrior(prior: NightLightPrior?) {
        dataStore.edit {
            it.remove(PRIOR_K)
            if (prior == null) {
                it.remove(PRIOR_ON)
            } else {
                it[PRIOR_ON] = prior.activated
                prior.kelvin?.let { kelvin -> it[PRIOR_K] = kelvin }
            }
        }
    }

    private companion object {
        val ANCHOR_K = intPreferencesKey("night_light_anchor_k")
        val PRIOR_ON = booleanPreferencesKey("night_light_prior_on")
        val PRIOR_K = intPreferencesKey("night_light_prior_k")
    }
}
