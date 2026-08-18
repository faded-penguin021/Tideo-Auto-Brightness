package com.tideo.autobrightness.platform.context

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Tasker: prof768 Context: WiFi (Dis)connected → task43 matches SSID for per-WiFi context rules.
interface WifiInfoReader {
    fun ssidFlow(): Flow<String?>

    /** One-shot SSID read (G2R-F22/F41). _GetWifiNoLocation V3 order (S12.7d): Shizuku→dumpsys first, then Location-gated. */
    suspend fun currentSsid(): SsidResult
}

/** One-shot SSID read outcome (G2R-F22: targeted error messages). */
sealed interface SsidResult {
    data class Connected(val ssid: String) : SsidResult
    data object NotOnWifi : SsidResult
    data object NeedsLocationPermission : SsidResult
    data object LocationServicesOff : SsidResult
    data object Unknown : SsidResult
}

class AndroidWifiInfoReader(
    private val context: Context,
    // The no-Location strategies, in priority order (S12.7d/G2R-F41). Injectable so the source-
    // selection order can be unit-tested with fakes without a real Shizuku binder / dumpsys.
    private val noLocationStrategies: List<WifiSsidStrategy> = listOf(
        ShizukuWifiSsidStrategy(context),
        RootWifiSsidStrategy(context),
        DumpsysWifiSsidStrategy(context),
    ),
) : WifiInfoReader {
    override suspend fun currentSsid(): SsidResult {
        // _GetWifiNoLocation V3 order: try Shizuku → dumpsys first; a hit returns without Location.
        resolveNoLocationSsid()?.let { return SsidResult.Connected(it) }
        return locationCallbackSsid()
    }

    // _GetWifiNoLocation V3 order (S12.7d/G2R-F41): Shizuku→dumpsys first; shared to prevent drift.
    private suspend fun resolveNoLocationSsid(): String? {
        for (strategy in noLocationStrategies) {
            val ssid = runCatching { strategy.trySsid() }.getOrNull()
            if (!ssid.isNullOrEmpty()) return ssid
        }
        return null
    }

    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE (app-declared); SSID Location-gated.
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

    @SuppressLint("MissingPermission") // ConnectivityManager registerNetworkCallback needs ACCESS_NETWORK_STATE (app-declared).
    override fun ssidFlow(): Flow<String?> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        // task43 L154-181: bypass FIRST (Shizuku→dumpsys), then framework read. D-143: track live network.
        val resolvedNetwork = java.util.concurrent.atomic.AtomicReference<Network?>(null)
        val liveNetwork = java.util.concurrent.atomic.AtomicReference<Network?>(null)

        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                liveNetwork.set(network)
                if (network == resolvedNetwork.get()) return
                launch {
                    val ssid = resolveNoLocationSsid() ?: normalizeSsid((caps.transportInfo as? WifiInfo)?.ssid)
                    if (liveNetwork.get() != network) return@launch
                    if (ssid == null && resolvedNetwork.get() == network) return@launch
                    if (ssid != null) resolvedNetwork.set(network)
                    trySend(ssid)
                }
            }

            override fun onLost(network: Network) {
                liveNetwork.compareAndSet(network, null)
                resolvedNetwork.compareAndSet(network, null)
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
