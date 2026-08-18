package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** G2R-F83 / D-121: ipwho.is geo-IP fallback (task90 act28, HTTPS). */
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
    fun requiresExplicitSuccessAndValidCoordinateRanges() {
        assertNull(GeoIpLocationClient.parse("""{"latitude":52.0,"longitude":5.0}"""))
        assertNull(GeoIpLocationClient.parse("""{"success":true,"latitude":91,"longitude":5}"""))
        assertNull(GeoIpLocationClient.parse("""{"success":true,"latitude":52,"longitude":181}"""))
        assertNull(GeoIpLocationClient.parse("""{"success":true,"latitude":"52","longitude":5}"""))
    }

    @Test
    fun resolveUsesInjectedFetch() = runTest {
        val ok = GeoIpLocationClient(fetch = { successBody }).resolve()
        assertEquals(LocationSnapshot(52.0907, 5.1214), ok)

        assertNull(GeoIpLocationClient(fetch = { null }).resolve()) // network failure
    }

    @Test(expected = CancellationException::class)
    fun resolveDoesNotConvertCancellationToFailure() = runTest {
        GeoIpLocationClient(fetch = { throw CancellationException("test cancellation") }).resolve()
    }

    @Test
    fun boundedReadRejectsDeclaredAndStreamedOversizeBodies() = runTest {
        val exact = ByteArray(GeoIpLocationClient.MAX_RESPONSE_BYTES) { 'x'.code.toByte() }
        assertEquals(String(exact), GeoIpLocationClient.readBounded(ByteArrayInputStream(exact)))
        assertNull(
            GeoIpLocationClient.readBounded(
                ByteArrayInputStream(byteArrayOf()),
                GeoIpLocationClient.MAX_RESPONSE_BYTES + 1L,
            ),
        )
        assertNull(GeoIpLocationClient.readBounded(ByteArrayInputStream(exact + byteArrayOf(0))))
    }
}
