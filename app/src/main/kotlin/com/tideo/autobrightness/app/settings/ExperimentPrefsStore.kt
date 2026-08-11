package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Circadian "Experiment" fixed date/location override (G2R-F39); null fields use live data.
data class ExperimentDateLocation(
    val date: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val isUnset: Boolean get() = date == null && latitude == null && longitude == null
}

class ExperimentPrefsStore(private val dataStore: DataStore<Preferences>) {
    val dateLocation: Flow<ExperimentDateLocation> = dataStore.data.map { prefs ->
        ExperimentDateLocation(
            date = prefs[DATE],
            latitude = prefs[LAT],
            longitude = prefs[LON],
        )
    }

    // D-105: IP-geolocation fallback (ipwho.is, D-121); default off (opt-in).
    val geoIpEnabled: Flow<Boolean> = dataStore.data.map { it[GEO_IP] ?: false }
    suspend fun setGeoIpEnabled(enabled: Boolean) {
        dataStore.edit { it[GEO_IP] = enabled }
    }

    // Store fixed override; date/location independent. Null field reverts to live (G2R-F39).
    suspend fun set(date: String?, latitude: Double?, longitude: Double?) {
        dataStore.edit { prefs ->
            if (date != null) prefs[DATE] = date else prefs.remove(DATE)
            if (latitude != null) prefs[LAT] = latitude else prefs.remove(LAT)
            if (longitude != null) prefs[LON] = longitude else prefs.remove(LON)
        }
    }

    // D-103: persisted daily-resolved location; reused on cold start.
    suspend fun readCachedSunLocation(): CachedSunLocation? = cachedSunLocation.first()

    // D-110: cached location as reactive flow for UI staleness hint.
    val cachedSunLocation: Flow<CachedSunLocation?> = dataStore.data.map { prefs ->
        val lat = prefs[SUN_LAT]
        val lon = prefs[SUN_LON]
        val day = prefs[SUN_DAY]
        if (lat != null && lon != null && day != null && validCoordinates(lat, lon)) {
            CachedSunLocation(lat, lon, day)
        } else null
    }

    suspend fun writeCachedSunLocation(latitude: Double, longitude: Double, day: Long) {
        if (!validCoordinates(latitude, longitude)) return
        dataStore.edit { prefs ->
            prefs[SUN_LAT] = latitude
            prefs[SUN_LON] = longitude
            prefs[SUN_DAY] = day
        }
    }

    /** DA-037: persisted disclosure bound, so process death cannot repeat an automatic lookup today. */
    suspend fun readGeoIpAttemptDay(): Long? = dataStore.data.first()[GEO_IP_ATTEMPT_DAY]

    suspend fun writeGeoIpAttemptDay(day: Long) {
        dataStore.edit { it[GEO_IP_ATTEMPT_DAY] = day }
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
        // D-103: persisted once-a-day resolved location.
        val SUN_LAT = doublePreferencesKey("sun_cached_lat")
        val SUN_LON = doublePreferencesKey("sun_cached_lon")
        val SUN_DAY = longPreferencesKey("sun_cached_day")
        val GEO_IP_ATTEMPT_DAY = longPreferencesKey("geo_ip_attempt_day")

        fun validCoordinates(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() && latitude in -90.0..90.0 &&
                longitude.isFinite() && longitude in -180.0..180.0 &&
                (latitude != 0.0 || longitude != 0.0)
    }
}

/** D-103: a persisted daily-resolved location (epoch-[day] it was acquired for). */
data class CachedSunLocation(val latitude: Double, val longitude: Double, val day: Long)
