package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * G2R-F83: the ip-api.com geo-IP fallback (task90 act28). The HTTP fetch is injected so the JSON
 * parse and the failure paths are pure-JVM testable. ip-api's free endpoint returns
 * `{"status":"success",...,"lat":...,"lon":...}` (or `{"status":"fail",...}`).
 */
class GeoIpLocationClientTest {

    private val successBody = """
        {"status":"success","country":"Netherlands","city":"Utrecht","lat":52.0907,"lon":5.1214,"query":"1.2.3.4"}
    """.trimIndent()

    @Test
    fun parsesLatLonFromSuccessBody() {
        val snap = GeoIpLocationClient.parse(successBody)
        assertEquals(LocationSnapshot(52.0907, 5.1214), snap)
    }

    @Test
    fun returnsNullOnFailStatus() {
        assertNull(GeoIpLocationClient.parse("""{"status":"fail","message":"private range","query":"10.0.0.1"}"""))
    }

    @Test
    fun returnsNullOnNullIslandAndGarbage() {
        assertNull(GeoIpLocationClient.parse("""{"status":"success","lat":0,"lon":0}"""))
        assertNull(GeoIpLocationClient.parse("not json at all"))
    }

    @Test
    fun resolveUsesInjectedFetch() = runTest {
        val ok = GeoIpLocationClient(fetch = { successBody }).resolve()
        assertEquals(LocationSnapshot(52.0907, 5.1214), ok)

        assertNull(GeoIpLocationClient(fetch = { null }).resolve()) // network failure
    }
}
