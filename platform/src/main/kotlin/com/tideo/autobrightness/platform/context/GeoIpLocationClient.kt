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
 * Geo-IP location fallback (task90 act28, G2R-F83); D-121: ipwho.is over HTTPS, opt-in (D-105).
 * Fetch is injectable for pure-JVM testing.
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
        private const val TIMEOUT_MS = 30_000 // task90 act28
        internal const val MAX_RESPONSE_BYTES = 16 * 1024
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Parse ipwho.is JSON response. */
        fun parse(json: String): LocationSnapshot? {
            val body = runCatching { JSON.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            if (body["success"]?.jsonPrimitive?.booleanOrNull != true) return null
            val latValue = body["latitude"]?.jsonPrimitive ?: return null
            val lonValue = body["longitude"]?.jsonPrimitive ?: return null
            if (latValue.isString || lonValue.isString) return null
            val lat = latValue.doubleOrNull ?: return null
            val lon = lonValue.doubleOrNull ?: return null
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return null
            // Reject null island (0,0).
            if (lat == 0.0 && lon == 0.0) return null
            return LocationSnapshot(lat, lon)
        }

        /**
         * DB-006: blocking request in child coroutine so cancellation reaches socket.
         * Parent awaits (real suspension), finally disconnects, releasing blocked child.
         */
        private suspend fun fetchGeoIp(): String? = coroutineScope {
            val conn = (URL(URL_GEO_IP).openConnection() as HttpsURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
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
                // Close socket to unpark blocked read.
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
