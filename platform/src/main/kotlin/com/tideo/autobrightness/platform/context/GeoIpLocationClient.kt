package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Geo-IP location fallback — the **final** step of task90's location-acquisition chain (act27–30:
 * `HTTP Request GET …/json` → `%gl_latitude = %http_data[latitude]`). Used only when no Android fix is
 * available and no fixed lat/lon is pinned (G2R-F83). Remains an explicit opt-IN (default off, D-105).
 *
 * Tasker: task90 act28 (code 339) "fallback for exported kid app version", XML L40292.
 *
 * D-121: the endpoint is **ipwho.is over HTTPS** (was the cleartext `http://ip-api.com/json`). This
 * removes the only cleartext request in the app — the `ip-api.com` network-security-config exception is
 * gone and all traffic is HTTPS-only again. ipwho.is returns the coordinates under the **full words**
 * `latitude`/`longitude` (ip-api.com used `lat`/`lon`) and signals failure with `"success":false`.
 *
 * The HTTP [fetch] is injectable so the JSON parse + the no-Android-fix path are pure-JVM testable
 * without a real network.
 */
class GeoIpLocationClient(
    private val fetch: () -> String? = ::fetchGeoIp,
) {
    /** Resolve an approximate location from the device's public IP, or null on failure. */
    suspend fun resolve(): LocationSnapshot? = withContext(Dispatchers.IO) {
        runCatching { fetch() }.getOrNull()?.let { parse(it) }
    }

    companion object {
        private const val URL_GEO_IP = "https://ipwho.is/"
        private const val TIMEOUT_MS = 30_000 // task90 act28 timeout=30
        private val LAT = Regex("\"latitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        private val LON = Regex("\"longitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        private val FAIL = Regex("\"success\"\\s*:\\s*false")

        /** Parse ipwho.is's `{"success":true,...,"latitude":52.09,"longitude":5.12,...}` body. */
        fun parse(json: String): LocationSnapshot? {
            if (FAIL.containsMatchIn(json)) return null
            val lat = LAT.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val lon = LON.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            // Reject "null island" (0,0) — same guard as the Android fix path.
            if (lat == 0.0 && lon == 0.0) return null
            return LocationSnapshot(lat, lon)
        }

        private fun fetchGeoIp(): String? {
            val conn = (URL(URL_GEO_IP).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            return try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) null
                else conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }
    }
}
