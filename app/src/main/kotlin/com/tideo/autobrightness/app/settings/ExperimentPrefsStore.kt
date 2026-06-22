package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    /**
     * G3-F12 (Gate 3 — privacy): whether the IP-geolocation fallback (`ip-api.com`, cleartext HTTP) may
     * run as the LAST resort when no Android location fix is available and no fixed lat/lon is pinned
     * (task90 act28). Default **on** (Tasker parity), but a privacy-conscious user can turn it off so
     * the app never contacts ip-api.com — circadian then simply waits for an on-device fix.
     */
    val geoIpEnabled: Flow<Boolean> = dataStore.data.map { it[GEO_IP] ?: true }

    /** Opt out of (or back into) the ip-api.com geo-IP location fallback (G3-F12). */
    suspend fun setGeoIpEnabled(enabled: Boolean) {
        dataStore.edit { it[GEO_IP] = enabled }
    }

    /**
     * Store a fixed override — mirrors `_ExperimentSetDate`. Date and location are **independent**
     * (G2R-F39): a null field is removed (reverts to live for that field), so callers can pin a date
     * only (live location), a location only (today's date), or both.
     */
    suspend fun set(date: String?, latitude: Double?, longitude: Double?) {
        dataStore.edit { prefs ->
            if (date != null) prefs[DATE] = date else prefs.remove(DATE)
            if (latitude != null) prefs[LAT] = latitude else prefs.remove(LAT)
            if (longitude != null) prefs[LON] = longitude else prefs.remove(LON)
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
        val GEO_IP = booleanPreferencesKey("geo_ip_fallback_enabled")
    }
}
