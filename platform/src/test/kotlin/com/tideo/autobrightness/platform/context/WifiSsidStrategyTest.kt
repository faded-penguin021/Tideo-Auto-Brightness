package com.tideo.autobrightness.platform.context

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S12.7d (G2R-F41): the `_GetWifiNoLocation V3` source order — the no-Location strategies (Shizuku
 * `cmd wifi status` → `dumpsys wifi`) are tried first, the Location-gated callback is the last
 * fallback. Strategies are injected as fakes so the precedence is testable without a real binder.
 */
@RunWith(RobolectricTestRunner::class)
class WifiSsidStrategyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Records whether it ran, so we can assert the second strategy is skipped after a hit. */
    private class FakeStrategy(private val result: String?) : WifiSsidStrategy {
        var called = false
            private set

        override suspend fun trySsid(): String? {
            called = true
            return result
        }
    }

    @Test
    fun firstStrategyHit_winsAndShortCircuits() = runTest {
        val shizuku = FakeStrategy("HomeNet")
        val dump = FakeStrategy("OtherNet")
        val reader = AndroidWifiInfoReader(context, listOf(shizuku, dump))

        val result = reader.currentSsid()

        assertEquals(SsidResult.Connected("HomeNet"), result)
        assertTrue(shizuku.called)
        // dumpsys must NOT be consulted once Shizuku resolved the SSID.
        assertTrue(!dump.called)
    }

    @Test
    fun firstStrategyMisses_fallsToSecond() = runTest {
        val shizuku = FakeStrategy(null)
        val dump = FakeStrategy("OfficeNet")
        val reader = AndroidWifiInfoReader(context, listOf(shizuku, dump))

        val result = reader.currentSsid()

        assertEquals(SsidResult.Connected("OfficeNet"), result)
        assertTrue(shizuku.called && dump.called)
    }

    @Test
    fun allNoLocationStrategiesMiss_fallsToLocationPath() = runTest {
        val shizuku = FakeStrategy(null)
        val dump = FakeStrategy(null)
        val reader = AndroidWifiInfoReader(context, listOf(shizuku, dump))

        val result = reader.currentSsid()

        // Both no-Location strategies were tried; with no active Wi-Fi network the Location-callback
        // fallback reports NotOnWifi (it is NOT a Connected result).
        assertTrue(shizuku.called && dump.called)
        assertTrue(result !is SsidResult.Connected)
    }

    @Test
    fun parseCmdWifiStatus_extractsQuotedSsid() {
        val out = "Wifi is enabled\nWifi is connected to \"My Home Net\"\nIP address: 192.168.0.5"
        assertEquals("My Home Net", parseCmdWifiStatus(out))
    }

    @Test
    fun parseCmdWifiStatus_handlesUnquoted() {
        assertEquals("Cafe5G", parseCmdWifiStatus("Wifi is connected to Cafe5G"))
    }

    @Test
    fun parseCmdWifiStatus_returnsNullWhenDisconnected() {
        assertNull(parseCmdWifiStatus("Wifi is enabled\nWifi is disconnected"))
    }

    @Test
    fun parseDumpsysWifi_extractsFromCompletedLine() {
        val out = """
            Wifi is enabled
            mWifiInfo SSID: MyNet, BSSID: aa:bb:cc:dd:ee:ff, MAC: ..., Supplicant state: COMPLETED, RSSI: -50
            some other line
        """.trimIndent()
        assertEquals("MyNet", parseDumpsysWifi(out))
    }

    @Test
    fun parseDumpsysWifi_handlesQuotedSsid() {
        val out = "mWifiInfo SSID: \"Quoted Net\", BSSID: .., state: COMPLETED,"
        assertEquals("Quoted Net", parseDumpsysWifi(out))
    }

    @Test
    fun normalizeSsid_rejectsRedactedPlaceholders() {
        assertNull(normalizeSsid("<unknown ssid>"))
        assertNull(normalizeSsid("<redacted>"))
        assertNull(normalizeSsid("\"\""))
        assertEquals("Real", normalizeSsid("\"Real\""))
    }
}
