package com.tideo.autobrightness.app.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.CircadianLocationStatus
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.context.AndroidLocationReader
import com.tideo.autobrightness.platform.context.GeoIpLocationClient
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** G2R-F39: fixed date/location element. Scene-local preview state, not persisted as profile parameter. */
class CircadianExtrasViewModel @JvmOverloads constructor(
    application: Application,
    private val location: LocationReader = AndroidLocationReader(application),
    // D-120/D-121: ipwho.is IP lookup backs "Use current location" as active last resort
    private val geoIp: GeoIpLocationClient = GeoIpLocationClient(),
) : AndroidViewModel(application) {
    private val store = ExperimentPrefsStore(application.experimentPrefsDataStore)

    val dateLocation: StateFlow<ExperimentDateLocation> = store.dateLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExperimentDateLocation())

    /** D-105: geo-IP fallback opt-IN privacy gate (default off). */
    val geoIpEnabled: StateFlow<Boolean> = store.geoIpEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** D-110: location staleness hint; mirrors CircadianWindowProvider's fallback chain. */
    val circadianLocationStatus: StateFlow<CircadianLocationStatus> =
        store.dateLocation.combine(store.cachedSunLocation) { ov, cache ->
            val today = System.currentTimeMillis() / 1000L / 86_400L
            when {
                ov.latitude != null && ov.longitude != null ->
                    CircadianLocationStatus(ov.latitude, ov.longitude, resolvedForDay = today, today = today, fixed = true)
                cache != null ->
                    CircadianLocationStatus(cache.latitude, cache.longitude, resolvedForDay = cache.day, today = today, fixed = false)
                else -> CircadianLocationStatus(today = today)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CircadianLocationStatus())

    fun setGeoIpEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setGeoIpEnabled(enabled) }
    }

    fun today(): String = DATE_FORMAT.format(Date())

    fun defaultLatLon(): Pair<Double, Double>? =
        location.lastKnownLocation()?.let { it.latitude to it.longitude }

    /** G2R-F42/D-120/D-122: actively acquire fresh location (not passive last-known); fallback to ipwho.is if opted-in. */
    suspend fun freshLatLon(): Pair<Double, Double>? {
        (location.activeFix() as? LocationResult.Available)?.snapshot?.let { return it.cacheAndPair() }
        if (store.geoIpEnabled.first()) {
            geoIp.resolve()?.let { return it.cacheAndPair() }
        }
        return null
    }

    // DB-054: an acquired location is the day's resolved location; typed coordinates are not.
    private suspend fun LocationSnapshot.cacheAndPair(): Pair<Double, Double> {
        store.writeCachedSunLocation(latitude, longitude, System.currentTimeMillis() / 1000L / 86_400L)
        return latitude to longitude
    }

    /** G2R-F39: pin fixed date/location (null = revert to live). Re-apply pipeline to avoid steady-light drop. */
    fun set(date: String?, latitude: Double?, longitude: Double?) {
        viewModelScope.launch {
            store.set(date, latitude, longitude)
            reapplyIfRunning()
        }
    }

    fun useLiveData() {
        viewModelScope.launch {
            store.clear()
            reapplyIfRunning()
        }
    }

    private suspend fun reapplyIfRunning() {
        val enabled = getApplication<Application>().settingsDataStore.data.first().serviceEnabled
        if (enabled) AutoBrightnessRuntime.reapply(getApplication())
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
