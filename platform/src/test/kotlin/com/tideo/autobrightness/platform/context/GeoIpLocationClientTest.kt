package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * G2R-F83 / D-121: the ipwho.is geo-IP fallback (task90 act28, now HTTPS). The HTTP fetch is injected so
 * the JSON parse and the failure paths are pure-JVM testable. ipwho.is returns
 * `{"success":true,...,"latitude":...,"longitude":...}` (or `{"success":false,"message":...}`), using the
 * full words `latitude`/`longitude` rather than ip-api.com's old `lat`/`lon`.
 */
class GeoIpLocationClientTest {

    private val successBody = """
        {"ip":"1.2.3.4","success":true,"country":"Netherlands","city":"Utrecht","latitude":52.0907,"longitude":5.1214}
    """.trimIndent()

    @Test
    fun parsesLatLonFromSuccessBody() {
        val snap = GeoIpLocationClient.parse(successBody)
        assertEquals(LocationSnapshot(52.0907, 5.1214), snap)
    }

    @Test
    fun returnsNullOnFailStatus() {
        assertNull(GeoIpLocationClient.parse("""{"ip":"10.0.0.1","success":false,"message":"Invalid IP address"}"""))
    }

    @Test
    fun returnsNullOnNullIslandAndGarbage() {
        assertNull(GeoIpLocationClient.parse("""{"success":true,"latitude":0,"longitude":0}"""))
        assertNull(GeoIpLocationClient.parse("not json at all"))
    }

    @Test
    fun resolveUsesInjectedFetch() = runTest {
        val ok = GeoIpLocationClient(fetch = { successBody }).resolve()
        assertEquals(LocationSnapshot(52.0907, 5.1214), ok)

        assertNull(GeoIpLocationClient(fetch = { null }).resolve()) // network failure
    }
}
