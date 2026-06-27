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
     * G3-F12 / D-105 (privacy): whether the IP-geolocation fallback (`ip-api.com`, cleartext HTTP) may
     * run as the LAST resort when no Android location fix is available and no fixed lat/lon is pinned
     * (task90 act28). Default **off — opt-in** (D-105): a cleartext request to a third party is not
     * made unless the user explicitly enables it. (Tasker called ip-api.com unconditionally; the toggle
     * itself was already a deviation, G3-F12 — D-105 only flips its default from on to off.) When off,
     * the app never contacts ip-api.com — circadian simply waits for an on-device fix.
     */
    val geoIpEnabled: Flow<Boolean> = dataStore.data.map { it[GEO_IP] ?: false }

    /** Opt in to (or back out of) the ip-api.com geo-IP location fallback (G3-F12 / D-105). */
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

    /**
     * D-103: the once-a-day resolved location (Android fix or geo-IP), persisted so a cold start
     * (process death / service restart after screen-on) reuses it immediately instead of falling back
     * to the fixed `TimeContext` defaults until the async re-acquire lands. Mirrors Tasker's persisted
     * `%AAB_SunLat`/`%AAB_SunLon` + `%AAB_SunLastDate`. [day] is epoch-days the fix was acquired for.
     */
    suspend fun readCachedSunLocation(): CachedSunLocation? {
        val prefs = dataStore.data.first()
        val lat = prefs[SUN_LAT] ?: return null
        val lon = prefs[SUN_LON] ?: return null
        val day = prefs[SUN_DAY] ?: return null
        return CachedSunLocation(lat, lon, day)
    }

    /** Persist the daily-resolved location (D-103). */
    suspend fun writeCachedSunLocation(latitude: Double, longitude: Double, day: Long) {
        dataStore.edit { prefs ->
            prefs[SUN_LAT] = latitude
            prefs[SUN_LON] = longitude
            prefs[SUN_DAY] = day
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
        // D-103: persisted once-a-day resolved location.
        val SUN_LAT = doublePreferencesKey("sun_cached_lat")
        val SUN_LON = doublePreferencesKey("sun_cached_lon")
        val SUN_DAY = longPreferencesKey("sun_cached_day")
    }
}

/** D-103: a persisted daily-resolved location (epoch-[day] it was acquired for). */
data class CachedSunLocation(val latitude: Double, val longitude: Double, val day: Long)
