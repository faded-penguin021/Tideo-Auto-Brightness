package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Geo-IP location fallback — the **final** step of task90's location-acquisition chain (act27–30:
 * `HTTP Request GET http://ip-api.com/json` → `%gl_latitude = %http_data[lat]`). Used only when no
 * Android fix is available and no fixed lat/lon is pinned (G2R-F83).
 *
 * Tasker: task90 act28 (code 339) "fallback for exported kid app version", XML L40292.
 *
 * The HTTP [fetch] is injectable so the JSON parse + the no-Android-fix path are pure-JVM testable
 * without a real network. ip-api.com's free endpoint is HTTP-only → an `ip-api.com`-scoped cleartext
 * exception is declared in `res/xml/network_security_config.xml`.
 */
class GeoIpLocationClient(
    private val fetch: () -> String? = ::fetchIpApi,
) {
    /** Resolve an approximate location from the device's public IP, or null on failure. */
    suspend fun resolve(): LocationSnapshot? = withContext(Dispatchers.IO) {
        runCatching { fetch() }.getOrNull()?.let { parse(it) }
    }

    companion object {
        private const val URL_IP_API = "http://ip-api.com/json"
        private const val TIMEOUT_MS = 30_000 // task90 act28 timeout=30
        private val LAT = Regex("\"lat\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        private val LON = Regex("\"lon\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        private val FAIL = Regex("\"status\"\\s*:\\s*\"fail\"")

        /** Parse ip-api.com's `{"status":"success",...,"lat":52.09,"lon":5.12,...}` body. */
        fun parse(json: String): LocationSnapshot? {
            if (FAIL.containsMatchIn(json)) return null
            val lat = LAT.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val lon = LON.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            // Reject "null island" (0,0) — same guard as the Android fix path.
            if (lat == 0.0 && lon == 0.0) return null
            return LocationSnapshot(lat, lon)
        }

        private fun fetchIpApi(): String? {
            val conn = (URL(URL_IP_API).openConnection() as HttpURLConnection).apply {
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
