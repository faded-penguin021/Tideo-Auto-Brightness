package com.tideo.autobrightness.platform.context

import android.content.Context
import android.location.LocationManager

// Tasker: prof765/766/767 Location context rules compare %LOC to per-rule radius.
// Returns last known passive-provider location; manual lat/lon fallback lives in AabSettings.
// Requires ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION at runtime; SecurityException → null.
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
)

interface LocationReader {
    fun lastKnownLocation(): LocationSnapshot?
}

class AndroidLocationReader(private val context: Context) : LocationReader {
    override fun lastKnownLocation(): LocationSnapshot? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                ?.let { LocationSnapshot(it.latitude, it.longitude) }
        } catch (_: SecurityException) {
            null
        }
    }
}
