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

/**
 * Typed result for the one-shot "use current location" read (G2R-F42). The previous boolean/null
 * path conflated "permission missing" with "granted but no cached fix yet", so the editor wrongly
 * reported the permission as not granted (even right after the user granted it). This separates the
 * two so the UI can recheck/guide correctly.
 */
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

    /**
     * Continuous location updates for the context engine's "super smart location listener" (G2R-F45).
     * Hosted in the foreground service scope so it survives the app being backgrounded (the previous
     * on-demand `lastKnownLocation()` read died with the Activity → reverted to no-rule). Seeds with
     * the best last-known fix, then emits real provider fixes; (0.0, 0.0) "null island" reads are
     * filtered out (the bug behind the on-device `loc 0.0,0.0`). Closes (no emissions) when location
     * permission is missing.
     */
    fun locationUpdates(minTimeMs: Long = DEFAULT_MIN_TIME_MS, minDistanceM: Float = DEFAULT_MIN_DISTANCE_M): Flow<LocationSnapshot>

    /**
     * One-shot read with a call-time permission recheck + a fresh fix (G2R-F42). Prefers a current
     * fix; falls back to the best last-known. Distinguishes missing-permission from no-fix.
     */
    suspend fun currentLocation(): LocationResult

    companion object {
        const val DEFAULT_MIN_TIME_MS = 30_000L
        const val DEFAULT_MIN_DISTANCE_M = 50f
    }
}

class AndroidLocationReader(private val context: Context) : LocationReader {

    override fun lastKnownLocation(): LocationSnapshot? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return bestLastKnown(lm)
    }

    // MissingPermission: this is a library adapter; the consuming app declares ACCESS_FINE/COARSE_LOCATION
    // and this guards with hasLocationPermission() + a SecurityException catch before any request.
    @SuppressLint("MissingPermission")
    override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = callbackFlow {
        if (!hasLocationPermission()) { close(); return@callbackFlow }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { close(); return@callbackFlow }

        val listener = LocationListener { loc -> loc.toSnapshotOrNull()?.let { trySend(it) } }
        // Seed immediately so a configured rule resolves without waiting for the first fresh fix.
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

    @SuppressLint("MissingPermission") // guarded by the hasLocationPermission() recheck below + SecurityException catch.
    override suspend fun currentLocation(): LocationResult {
        // Recheck the grant at call time (G2R-F42): the grant may have just been awarded, and the
        // OS permission propagation can lag a stale cached check.
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

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Newest valid (non null-island) last-known fix across GPS/network/passive providers. */
    @SuppressLint("MissingPermission") // wrapped in runCatching + SecurityException catch; app declares the perms.
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
