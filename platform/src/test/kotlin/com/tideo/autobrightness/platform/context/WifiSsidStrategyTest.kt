package com.tideo.autobrightness.platform.context

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun ssidFlow_resolvesViaNoLocationStrategy() = runTest {
        // Regression (D-096): the runtime context-evaluation flow must run the SAME no-Location
        // strategies as currentSsid(), not just the Location-gated callback — otherwise Wi-Fi context
        // rules silently require Location services ON even when Shizuku can resolve the SSID.
        val shizuku = FakeStrategy("HomeNet")
        val reader = AndroidWifiInfoReader(context, listOf(shizuku))
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val emissions = mutableListOf<String?>()
        val collectJob = launch { reader.ssidFlow().collect { emissions.add(it) } }
        runCurrent() // let the flow register its NetworkCallback

        val callbacks = Shadows.shadowOf(cm).networkCallbacks
        assertTrue(callbacks.isNotEmpty())
        // Fire a Wi-Fi capabilities change; the no-Location strategy must resolve before any caps read.
        val network = ShadowNetwork.newInstance(1)
        val caps = ShadowNetworkCapabilities.newInstance()
        callbacks.forEach { it.onCapabilitiesChanged(network, caps) }
        advanceUntilIdle()

        assertEquals("HomeNet", emissions.lastOrNull())
        assertTrue(shizuku.called)
        collectJob.cancel()
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
    fun parseDumpsysWifi_quotedStep1KeepsCommaInName() {
        // Two-step strategy: step 1 (quoted) must win over step 2 (up-to-comma) so a network whose name
        // contains a comma is captured whole, not truncated at the first comma.
        val out = "mWifiInfo SSID: \"Net, Work\", BSSID: aa:bb, Supplicant state: COMPLETED,"
        assertEquals("Net, Work", parseDumpsysWifi(out))
    }

    @Test
    fun parseDumpsysWifi_requiresMWifiInfoLine() {
        // Tasker's `grep mWifiInfo | grep COMPLETED` — a COMPLETED line without mWifiInfo is not the
        // connected-network info line and must not be mined for an SSID.
        val out = "Network 1: SSID: Neighbour, status: COMPLETED, not mine"
        assertNull(parseDumpsysWifi(out))
    }

    @Test
    fun normalizeSsid_rejectsRedactedPlaceholders() {
        assertNull(normalizeSsid("<unknown ssid>"))
        assertNull(normalizeSsid("<redacted>"))
        assertNull(normalizeSsid("\"\""))
        assertEquals("Real", normalizeSsid("\"Real\""))
    }
}
