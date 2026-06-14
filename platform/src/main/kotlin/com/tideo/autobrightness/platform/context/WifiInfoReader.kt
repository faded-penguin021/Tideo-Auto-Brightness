package com.tideo.autobrightness.platform.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Tasker: prof768 Context: WiFi (Dis)connected → task43 matches SSID for per-WiFi context rules.
// The connected SSID requires ACCESS_FINE_LOCATION at runtime on API 29+ AND location services ON;
// it is only delivered through a NetworkCallback registered with FLAG_INCLUDE_LOCATION_INFO.
interface WifiInfoReader {
    fun ssidFlow(): Flow<String?>

    /**
     * One-shot read of the currently-connected SSID for the rule editor's "use current SSID"
     * (G2R-F22/F41). Returns a typed [SsidResult] so the UI can give a targeted message instead of a
     * blanket "Not connected".
     *
     * Resolution order follows Tasker's `_GetWifiNoLocation V3` (S12.7d): the no-Location strategies
     * (Shizuku `cmd wifi status`, then `dumpsys wifi`) are tried FIRST, and only when neither resolves
     * does it fall back to the Location-gated `NetworkCallback` path — so most users read the SSID
     * without ever granting ACCESS_FINE_LOCATION.
     */
    suspend fun currentSsid(): SsidResult
}

/** Outcome of a one-shot SSID read, so each failure mode gets its own message (G2R-F22). */
sealed interface SsidResult {
    data class Connected(val ssid: String) : SsidResult
    /** Active network is not Wi-Fi. */
    data object NotOnWifi : SsidResult
    /** ACCESS_FINE_LOCATION not granted — required to read the SSID on API 29+. */
    data object NeedsLocationPermission : SsidResult
    /** Location services are switched off — the OS redacts the SSID. */
    data object LocationServicesOff : SsidResult
    /** On Wi-Fi + permitted, but the SSID could not be resolved in time. */
    data object Unknown : SsidResult
}

class AndroidWifiInfoReader(
    private val context: Context,
    // The no-Location strategies, in priority order (S12.7d/G2R-F41). Injectable so the source-
    // selection order can be unit-tested with fakes without a real Shizuku binder / dumpsys.
    private val noLocationStrategies: List<WifiSsidStrategy> = listOf(
        ShizukuWifiSsidStrategy(context),
        DumpsysWifiSsidStrategy(context),
    ),
) : WifiInfoReader {
    override suspend fun currentSsid(): SsidResult {
        // _GetWifiNoLocation V3 order: try Shizuku → dumpsys first; a hit returns without Location.
        for (strategy in noLocationStrategies) {
            val ssid = runCatching { strategy.trySsid() }.getOrNull()
            if (!ssid.isNullOrEmpty()) return SsidResult.Connected(ssid)
        }
        return locationCallbackSsid()
    }

    // The Location-gated NetworkCallback path — the LAST fallback (was the only path pre-S12.7d).
    private suspend fun locationCallbackSsid(): SsidResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return SsidResult.NotOnWifi

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return SsidResult.NeedsLocationPermission
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm != null && !lm.isLocationEnabled) return SsidResult.LocationServicesOff

        // A NetworkCallback with FLAG_INCLUDE_LOCATION_INFO is the only API-29+ path that yields the
        // real SSID; register it briefly and take the first non-redacted value.
        val ssid = withTimeoutOrNull(SSID_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) {
                        val raw = (c.transportInfo as? WifiInfo)?.ssid
                            ?.removeSurrounding("\"")
                            ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                        if (raw != null && cont.isActive) cont.resume(raw)
                    }
                }
                cm.registerNetworkCallback(request, callback)
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(callback) } }
            }
        }
        return ssid?.let { SsidResult.Connected(it) } ?: SsidResult.Unknown
    }

    override fun ssidFlow(): Flow<String?> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val raw = (caps.transportInfo as? WifiInfo)?.ssid
                // Android wraps SSID in quotes; strip them for plain comparison against rule values.
                val ssid = raw?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
                trySend(ssid)
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }

        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val SSID_TIMEOUT_MS = 2_000L
    }
}
