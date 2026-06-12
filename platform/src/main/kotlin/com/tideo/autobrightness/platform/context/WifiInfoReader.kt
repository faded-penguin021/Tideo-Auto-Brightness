package com.tideo.autobrightness.platform.context

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Tasker: prof768 Context: WiFi (Dis)connected → task43 matches SSID for per-WiFi context rules.
// Requires ACCESS_FINE_LOCATION at runtime on API 29+ to obtain SSID.
interface WifiInfoReader {
    fun ssidFlow(): Flow<String?>
}

class AndroidWifiInfoReader(private val context: Context) : WifiInfoReader {
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
}
