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
// The framework SSID (via NetworkCallback FLAG_INCLUDE_LOCATION_INFO) requires ACCESS_FINE_LOCATION
// AND location services ON on API 29+. Both the editor read (currentSsid) and the runtime flow
// (ssidFlow) first try the no-Location bypass strategies (Shizuku/dumpsys, _GetWifiNoLocation V3)
// so Wi-Fi context rules work with Location off — the framework path is the last fallback.
interface WifiInfoReader {
    fun ssidFlow(): Flow<String?>

    /**
     * One-shot read of the currently-connected SSID for the rule editor's "use current SSID"
     * (G2R-F22/F41). Returns a typed [SsidResult] so the UI can give a targeted message instead of a
     * blanket "Not connected".
     *
     * Resolution order follows Tasker's `_GetWifiNoLocation V3` (S12.7d): the no-Location strategies
     * (Shizuku `cmd wifi status`, then root `su -c 'cmd wifi status'`, then a DUMP-granted `dumpsys
     * wifi`) are tried FIRST, and only when none resolves does it fall back to the Location-gated
     * `NetworkCallback` path — so most users read the SSID without ever granting ACCESS_FINE_LOCATION.
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
        RootWifiSsidStrategy(context),
        DumpsysWifiSsidStrategy(context),
    ),
) : WifiInfoReader {
    override suspend fun currentSsid(): SsidResult {
        // _GetWifiNoLocation V3 order: try Shizuku → dumpsys first; a hit returns without Location.
        resolveNoLocationSsid()?.let { return SsidResult.Connected(it) }
        return locationCallbackSsid()
    }

    // _GetWifiNoLocation V3 order (S12.7d/G2R-F41): try the no-Location strategies (Shizuku
    // `cmd wifi status` → root `su -c` → DUMP-granted `dumpsys wifi`) in priority order; first hit
    // wins. Returns null when none resolve, so callers fall back to the Location-gated path. Shared by
    // both currentSsid() (rule
    // editor) and ssidFlow() (runtime context evaluation) so the two paths can't drift again — the
    // earlier drift (flow skipped these strategies) made Wi-Fi rules require Location at eval time.
    private suspend fun resolveNoLocationSsid(): String? {
        for (strategy in noLocationStrategies) {
            val ssid = runCatching { strategy.trySsid() }.getOrNull()
            if (!ssid.isNullOrEmpty()) return ssid
        }
        return null
    }

    // The Location-gated NetworkCallback path — the LAST fallback (was the only path pre-S12.7d).
    // MissingPermission: ConnectivityManager needs ACCESS_NETWORK_STATE (a normal perm the app declares);
    // the SSID read is additionally Location-gated by the checkSelfPermission below.
    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission") // ConnectivityManager registerNetworkCallback needs ACCESS_NETWORK_STATE (app-declared).
    override fun ssidFlow(): Flow<String?> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        // task43 L154-181: context evaluation reads the SSID via the no-Location bypass FIRST
        // (`bypass_ssid`), only then the standard framework read. So the runtime flow must run the same
        // Shizuku→dumpsys strategies as currentSsid()/the rule editor — a Shizuku-resolved SSID drives
        // Wi-Fi rules with Location services OFF. The NetworkCallback is just the change trigger here.
        // Skip the (costly) re-resolve once we hold a confirmed SSID for the current network; reset it
        // on a new network / loss so a real SSID is still picked up if the first read came back redacted.
        val resolvedNetwork = java.util.concurrent.atomic.AtomicReference<Network?>(null)
        // D-143: the resolve is async, so its result can land AFTER the network state moved on. Track
        // the live network so a resolve that outlived its network is dropped (onLost's null stands),
        // and a late FAILED resolve can't wipe an SSID a faster parallel resolve already confirmed
        // (which would stick: the resolved-network skip above stops any re-resolve until reconnect).
        val liveNetwork = java.util.concurrent.atomic.AtomicReference<Network?>(null)

        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                liveNetwork.set(network)
                if (network == resolvedNetwork.get()) return
                launch {
                    // Android wraps the framework SSID in quotes and redacts it without Location;
                    // normalizeSsid strips quotes and rejects the <unknown ssid>/<redacted> placeholders.
                    val ssid = resolveNoLocationSsid() ?: normalizeSsid((caps.transportInfo as? WifiInfo)?.ssid)
                    if (liveNetwork.get() != network) return@launch // network gone/changed mid-resolve (D-143)
                    if (ssid == null && resolvedNetwork.get() == network) return@launch // lost to a faster resolve (D-143)
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
