package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The Circadian "Experiment" fixed date + location override (experiment_settings.md elements37:
 * `%AAB_Date` / `%AAB_Latitude` / `%AAB_Longitude`, set by `_ExperimentSetDate`, cleared by
 * `_ExperimentClearDate`). When [isUnset] the app uses **live data** — today's date + the current
 * location — so the circadian curve previews "now"; setting a fixed date/location lets the user
 * preview any day/place (S12.7h / G2R-F39).
 */
data class ExperimentDateLocation(
    val date: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** No fixed override stored → use live data (today + current location). */
    val isUnset: Boolean get() = date == null && latitude == null && longitude == null
}

/** Preferences-backed store for the Circadian fixed date/location override (G2R-F39). */
class ExperimentPrefsStore(private val dataStore: DataStore<Preferences>) {
    val dateLocation: Flow<ExperimentDateLocation> = dataStore.data.map { prefs ->
        ExperimentDateLocation(
            date = prefs[DATE],
            latitude = prefs[LAT],
            longitude = prefs[LON],
        )
    }

    /** Store a fixed date (`YYYY-MM-DD`) + latitude/longitude — mirrors `_ExperimentSetDate`. */
    suspend fun set(date: String, latitude: Double, longitude: Double) {
        dataStore.edit { prefs ->
            prefs[DATE] = date
            prefs[LAT] = latitude
            prefs[LON] = longitude
        }
    }

    /** Revert to live data (today + current location) — mirrors `_ExperimentClearDate`. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(DATE)
            prefs.remove(LAT)
            prefs.remove(LON)
        }
    }

    private companion object {
        val DATE = stringPreferencesKey("experiment_date")
        val LAT = doublePreferencesKey("experiment_lat")
        val LON = doublePreferencesKey("experiment_lon")
    }
}
