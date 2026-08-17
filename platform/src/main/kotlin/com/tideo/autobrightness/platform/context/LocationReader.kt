package com.tideo.autobrightness.platform.context

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Tasker: prof765/766/767 Location context rules compare %LOC to per-rule radius.
// Requires ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION at runtime; SecurityException → null.
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
)

/** Typed result for one-shot "use current location" read (G2R-F42). Separates missing-permission from no-fix. */
sealed interface LocationResult {
    data class Available(val snapshot: LocationSnapshot) : LocationResult
    /** Neither COARSE nor FINE location permission is granted (rechecked at call time). */
    data object NeedsPermission : LocationResult
    /** Permission granted, but no fix could be obtained in time. */
    data object Unavailable : LocationResult
}

interface LocationReader {
    /** Best last-known fix across providers, or null when none / unpermitted (legacy callers). */
    fun lastKnownLocation(): LocationSnapshot?

    /** Continuous location updates for "super smart location listener" (G2R-F45). Hosted in foreground service scope. Seeds with last-known fix; filters null-island. */
    fun locationUpdates(minTimeMs: Long = DEFAULT_MIN_TIME_MS, minDistanceM: Float = DEFAULT_MIN_DISTANCE_M): Flow<LocationSnapshot>

    /** One-shot read with call-time permission recheck + fresh fix (G2R-F42). Prefers current; falls back to last-known. */
    suspend fun currentLocation(): LocationResult

    /** ACTIVE one-shot for user-initiated "Use current location" buttons (D-122). Registers for live provider updates (powers GPS/network). Backup: last-known if no fresh fix within timeout. */
    suspend fun activeFix(timeoutMs: Long = ACTIVE_FIX_TIMEOUT_MS): LocationResult = currentLocation()

    fun locationServicesEnabled(): Boolean = true

    companion object {
        const val DEFAULT_MIN_TIME_MS = 30_000L
        const val DEFAULT_MIN_DISTANCE_M = 50f
        /** DB-055: cold-GPS budget before the last-known fallback; 20 s lost fixes this device lands at ~15 s. */
        const val ACTIVE_FIX_TIMEOUT_MS = 45_000L
    }
}

class AndroidLocationReader(private val context: Context) : LocationReader {

    override fun lastKnownLocation(): LocationSnapshot? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return bestLastKnown(lm)
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = callbackFlow {
        if (!hasLocationPermission()) { close(); return@callbackFlow }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { close(); return@callbackFlow }

        val listener = LocationListener { loc -> loc.toSnapshotOrNull()?.let { trySend(it) } }
        bestLastKnown(lm)?.let { trySend(it) }

        val providers = buildList {
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }.ifEmpty { listOf(LocationManager.PASSIVE_PROVIDER) }

        try {
            providers.forEach {
                lm.requestLocationUpdates(it, minTimeMs, minDistanceM, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            close(); return@callbackFlow
        }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationResult {
        // Recheck grant at call time (G2R-F42): grant may lag from cached checks.
        if (!hasLocationPermission()) return LocationResult.NeedsPermission
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.Unavailable

        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        val fresh = withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<LocationSnapshot?> { cont ->
                try {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc?.toSnapshotOrNull())
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        val snapshot = fresh ?: bestLastKnown(lm)
        return snapshot?.let { LocationResult.Available(it) } ?: LocationResult.Unavailable
    }

    @SuppressLint("MissingPermission")
    override suspend fun activeFix(timeoutMs: Long): LocationResult {
        // Recheck grant at call time (G2R-F42).
        if (!hasLocationPermission()) return LocationResult.NeedsPermission
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.Unavailable

        // DB-057: with the master switch off nothing can deliver, so spending the window is pure wait.
        if (!locationServicesEnabled()) {
            return bestLastKnown(lm)?.let { LocationResult.Available(it) } ?: LocationResult.Unavailable
        }

        // D-122: actively request NEW fix from enabled real providers. requestLocationUpdates powers sensors.
        // DB-053: PASSIVE last resort, as currentLocation() and locationUpdates() already had.
        val providers = buildList {
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }.ifEmpty { listOf(LocationManager.PASSIVE_PROVIDER) }

        val fresh = if (providers.isEmpty()) {
            null
        } else {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<LocationSnapshot?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val snap = location.toSnapshotOrNull() ?: return // skip null-island, keep listening
                            if (cont.isActive) {
                                runCatching { lm.removeUpdates(this) }
                                cont.resume(snap)
                            }
                        }
                    }
                    cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                    try {
                        providers.forEach {
                            lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper())
                        }
                    } catch (_: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }

        // BACKUP: last-known fix if active request produced nothing within timeout (D-122).
        val snapshot = fresh ?: bestLastKnown(lm)
        return snapshot?.let { LocationResult.Available(it) } ?: LocationResult.Unavailable
    }

    override fun locationServicesEnabled(): Boolean = runCatching {
        (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled ?: false
    }.getOrDefault(false)

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Newest valid (non null-island) last-known fix across providers. */
    @SuppressLint("MissingPermission")
    private fun bestLastKnown(lm: LocationManager): LocationSnapshot? = try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .asSequence()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .maxByOrNull { it.time }
            ?.let { LocationSnapshot(it.latitude, it.longitude) }
    } catch (_: SecurityException) {
        null
    }

    private fun Location.toSnapshotOrNull(): LocationSnapshot? =
        if (latitude == 0.0 && longitude == 0.0) null else LocationSnapshot(latitude, longitude)

    private companion object {
        const val CURRENT_FIX_TIMEOUT_MS = 5_000L
    }
}
