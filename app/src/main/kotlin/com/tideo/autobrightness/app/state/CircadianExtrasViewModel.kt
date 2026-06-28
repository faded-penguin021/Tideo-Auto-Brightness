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
import com.tideo.autobrightness.platform.context.LocationResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs the Circadian screen's fixed date/location element (G2R-F39). Reads/writes the
 * [ExperimentPrefsStore] override and supplies the "live data" defaults (today + current location)
 * used when nothing is set. Separate from [DraftSettingsViewModel] because the override is scene-local
 * preview state, not a profile parameter — it never enters `AabSettings`/profiles/export.
 */
class CircadianExtrasViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ExperimentPrefsStore(application.experimentPrefsDataStore)
    private val location = AndroidLocationReader(application)

    val dateLocation: StateFlow<ExperimentDateLocation> = store.dateLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExperimentDateLocation())

    /** G3-F12 / D-105: whether the ip-api.com geo-IP fallback may run (privacy opt-IN, default off). */
    val geoIpEnabled: StateFlow<Boolean> = store.geoIpEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * D-110: freshness of the location backing the live circadian modifier, for the staleness hint. A
     * pinned fixed location is never stale; a cached fix from a previous day is stale (with its age in
     * days); no cached fix at all → the modifier is on the default windows (≈0.85) until a fix lands.
     * Mirrors [com.tideo.autobrightness.app.runtime.CircadianWindowProvider.current]'s fallback chain so
     * the UI hint matches what the pipeline actually uses.
     */
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

    /** Today as `YYYY-MM-DD` — the live-data date default shown when no fixed date is set. */
    fun today(): String = DATE_FORMAT.format(Date())

    /** Best-effort last-known location as the lat/lon default; null if unavailable/no permission. */
    fun defaultLatLon(): Pair<Double, Double>? =
        location.lastKnownLocation()?.let { it.latitude to it.longitude }

    /** A fresh fix for the "Use current location" button (recheck grant at call time, cf. F42). */
    suspend fun freshLatLon(): Pair<Double, Double>? =
        (location.currentLocation() as? LocationResult.Available)
            ?.snapshot?.let { it.latitude to it.longitude }

    /** Pin a fixed date and/or location (G2R-F39); null fields revert to live for that field. The fixed
     *  date/location drive the LIVE circadian scaling (CircadianWindowProvider), not just the preview, so
     *  re-apply the pipeline immediately — otherwise the new %AAB_ScaleDynamic only lands on the next light
     *  change (prof760 drops steady-light cycles). */
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

    /** Force the runtime to recompute brightness with the new circadian windows (gated on serviceEnabled). */
    private suspend fun reapplyIfRunning() {
        val enabled = getApplication<Application>().settingsDataStore.data.first().serviceEnabled
        if (enabled) AutoBrightnessRuntime.reapply(getApplication())
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
