package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

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
    private val fetch: suspend () -> String? = ::fetchGeoIp,
) {
    /** Resolve an approximate location from the device's public IP, or null on failure. */
    suspend fun resolve(): LocationSnapshot? = withContext(Dispatchers.IO) {
        try {
            fetch()?.let { parse(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val URL_GEO_IP = "https://ipwho.is/"
        private const val TIMEOUT_MS = 30_000 // task90 act28 timeout=30
        internal const val MAX_RESPONSE_BYTES = 16 * 1024
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Parse ipwho.is's `{"success":true,...,"latitude":52.09,"longitude":5.12,...}` body. */
        fun parse(json: String): LocationSnapshot? {
            val body = runCatching { JSON.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            if (body["success"]?.jsonPrimitive?.booleanOrNull != true) return null
            val latValue = body["latitude"]?.jsonPrimitive ?: return null
            val lonValue = body["longitude"]?.jsonPrimitive ?: return null
            if (latValue.isString || lonValue.isString) return null
            val lat = latValue.doubleOrNull ?: return null
            val lon = lonValue.doubleOrNull ?: return null
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return null
            // Reject "null island" (0,0) — same guard as the Android fix path.
            if (lat == 0.0 && lon == 0.0) return null
            return LocationSnapshot(lat, lon)
        }

        /**
         * DB-006: the blocking request runs in a CHILD coroutine so that cancellation can actually
         * reach the socket.
         *
         * The previous shape registered `coroutineContext.job.invokeOnCompletion { conn.disconnect() }`
         * and then did the blocking call **on that same job**. A job's completion handlers run when
         * the job *completes*, and a job parked in an uninterruptible `read()` does not complete on
         * `cancel()` — it completes when the read returns. So the disconnect could only fire after
         * the very wait it existed to cut short: dead code for its own purpose, and the reason the
         * security review was right to call the "cancellation disconnects the request" claim
         * unsupported. A regression test pins both halves (BlockingReadCancellationTest).
         *
         * Now: the parent suspends in `await()` — a real suspension point — so cancellation unwinds
         * it immediately, `finally` closes the socket, and the blocked child is released by that
         * close. Worst case for an UNCANCELLED request is unchanged and remains 30 s connect + 30 s
         * read.
         */
        private suspend fun fetchGeoIp(): String? = coroutineScope {
            val conn = (URL(URL_GEO_IP).openConnection() as HttpsURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false // Never move the public-IP disclosure to another host.
            }
            val request = async(Dispatchers.IO) {
                if (conn.responseCode != HttpsURLConnection.HTTP_OK) null
                else conn.inputStream.use { readBounded(it, conn.contentLengthLong) }
            }
            try {
                request.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                // Runs while the child may still be parked in the blocking read: closing the socket
                // is what unparks it, so this must happen BEFORE we wait for the child to finish.
                conn.disconnect()
            }
        }

        internal suspend fun readBounded(input: InputStream, declaredBytes: Long = -1L): String? {
            if (declaredBytes > MAX_RESPONSE_BYTES) return null
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RESPONSE_BYTES) return null
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
