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
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// G2R-F41: test no-Location strategy precedence (Shizuku → dumpsys → Location callback).
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

        assertTrue(shizuku.called && dump.called)
        assertTrue(result !is SsidResult.Connected)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun ssidFlow_resolvesViaNoLocationStrategy() = runTest {
        // D-096: runtime flow must run same no-Location strategies as currentSsid().
        val shizuku = FakeStrategy("HomeNet")
        val reader = AndroidWifiInfoReader(context, listOf(shizuku))
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val emissions = mutableListOf<String?>()
        val collectJob = launch { reader.ssidFlow().collect { emissions.add(it) } }
        runCurrent()

        val callbacks = Shadows.shadowOf(cm).networkCallbacks
        assertTrue(callbacks.isNotEmpty())
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
        val out = "mWifiInfo SSID: \"Net, Work\", BSSID: aa:bb, Supplicant state: COMPLETED,"
        assertEquals("Net, Work", parseDumpsysWifi(out))
    }

    @Test
    fun parseDumpsysWifi_requiresMWifiInfoLine() {
        // Tasker's `grep mWifiInfo | grep COMPLETED` — a COMPLETED line without mWifiInfo is not the
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

    // DB-074: these pin the hand-rolled loop; only :platform's lint gate catches a readNBytes.
    @Test
    fun readBounded_stopsAtTheLimitWithoutConsumingTheRest() {
        val source = ByteArrayInputStream(ByteArray(100) { it.toByte() })

        val head = source.readBounded(40)

        assertEquals(40, head.size)
        assertEquals(0.toByte(), head[0])
        assertEquals(39.toByte(), head[39])
        assertEquals(40, source.read(), "the limit must not over-read the stream")
    }

    @Test
    fun readBounded_returnsEverythingWhenTheStreamEndsFirst() {
        assertEquals(7, ByteArrayInputStream(ByteArray(7)).readBounded(4096).size)
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readBounded(4096).size)
    }

    // A naive single read() reports one chunk as the whole of a process's output.
    @Test
    fun readBounded_keepsReadingAcrossPartialReads() {
        val dribble = object : InputStream() {
            private val data = "abcdefghij".toByteArray()
            private var at = 0
            override fun read(): Int = if (at < data.size) data[at++].toInt() else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= data.size) return -1
                b[off] = data[at++]
                return 1 // one byte per call, whatever was asked for
            }
        }

        assertEquals("abcdefghij", dribble.readBounded(64).decodeToString())
    }
}
