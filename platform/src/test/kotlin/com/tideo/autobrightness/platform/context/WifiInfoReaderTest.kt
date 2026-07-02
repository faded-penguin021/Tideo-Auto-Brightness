package com.tideo.autobrightness.platform.context

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `ssidFlow()` NetworkCallback path (H3 seam; D-143): the SSID resolve runs asynchronously per
 * capabilities callback, so a resolve still in flight when the network state moves on must not
 * publish its stale result over the newer state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WifiInfoReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Strategy whose resolves park until the test releases them, so callbacks can interleave. */
    private class ControllableStrategy : WifiSsidStrategy {
        val calls = mutableListOf<CompletableDeferred<String?>>()
        override suspend fun trySsid(): String? {
            val call = CompletableDeferred<String?>()
            calls += call
            return call.await()
        }
    }

    private fun TestScopeFixture(body: (Fixture) -> Unit) = runTest {
        val strategy = ControllableStrategy()
        val reader = AndroidWifiInfoReader(context, listOf(strategy))
        val emissions = mutableListOf<String?>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            reader.ssidFlow().collect { emissions += it }
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = shadowOf(cm).networkCallbacks.single()
        body(Fixture(strategy, emissions, callback))
        collector.cancel()
    }

    private class Fixture(
        val strategy: ControllableStrategy,
        val emissions: MutableList<String?>,
        val callback: ConnectivityManager.NetworkCallback,
    )

    @Test
    fun inFlightResolve_completingAfterOnLost_isDropped_D143() = TestScopeFixture { f ->
        val net = ShadowNetwork.newInstance(101)
        val caps = ShadowNetworkCapabilities.newInstance()

        // Connect: the resolve starts and parks inside the strategy.
        f.callback.onCapabilitiesChanged(net, caps)
        assertEquals(1, f.strategy.calls.size, "resolve started")

        // Disconnect while the resolve is still in flight: the flow publishes null.
        f.callback.onLost(net)
        assertEquals(listOf<String?>(null), f.emissions, "onLost publishes the disconnect")

        // The stale resolve lands AFTER the disconnect — it must be dropped, not resurrect
        // a "connected to StaleNet" state that then sticks until the next network change.
        f.strategy.calls[0].complete("StaleNet")
        assertEquals(listOf<String?>(null), f.emissions, "stale in-flight SSID must not override the disconnect")
    }

    @Test
    fun slowFailedResolve_completingAfterFastSuccess_isDropped_D143() = TestScopeFixture { f ->
        val net = ShadowNetwork.newInstance(102)
        val caps = ShadowNetworkCapabilities.newInstance()

        // Two capability callbacks race two resolves for the same (unresolved) network.
        f.callback.onCapabilitiesChanged(net, caps)
        f.callback.onCapabilitiesChanged(net, caps)
        assertEquals(2, f.strategy.calls.size, "both resolves in flight")

        // The second resolve succeeds first: SSID published, network marked resolved.
        f.strategy.calls[1].complete("HomeNet")
        assertEquals(listOf<String?>("HomeNet"), f.emissions)

        // The first (slow, FAILED) resolve completes late. Publishing its null would wipe the
        // good SSID — and with the network marked resolved, no later callback re-resolves it,
        // so the wipe would stick until the next connect/disconnect.
        f.strategy.calls[0].complete(null)
        assertEquals(listOf<String?>("HomeNet"), f.emissions, "stale failed resolve must not wipe the resolved SSID")
    }

    @Test
    fun resolvedNetwork_skipsReResolveOnLaterCallbacks() = TestScopeFixture { f ->
        val net = ShadowNetwork.newInstance(103)
        val caps = ShadowNetworkCapabilities.newInstance()

        f.callback.onCapabilitiesChanged(net, caps)
        f.strategy.calls[0].complete("HomeNet")
        assertEquals(listOf<String?>("HomeNet"), f.emissions)

        // RSSI-style capability churn on the resolved network: no new (costly) resolve.
        f.callback.onCapabilitiesChanged(net, caps)
        assertEquals(1, f.strategy.calls.size, "resolved network must not re-run the shell strategies")
        assertEquals(listOf<String?>("HomeNet"), f.emissions)
    }
}
