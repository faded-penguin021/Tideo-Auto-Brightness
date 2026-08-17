package com.tideo.autobrightness.app.state

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
import com.tideo.autobrightness.platform.context.GeoIpLocationClient
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CircadianExtrasViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ExperimentPrefsStore(app.experimentPrefsDataStore)

    private class FakeReader(
        private val result: LocationResult,
        private val recent: LocationSnapshot? = null,
    ) : LocationReader {
        var activeFixCalls = 0
            private set
        var requestedMaxAgeMs: Long? = null
            private set

        override fun lastKnownLocation(): LocationSnapshot? = null
        override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = flowOf()
        override suspend fun currentLocation(): LocationResult = result
        override suspend fun activeFix(timeoutMs: Long): LocationResult {
            activeFixCalls++
            return result
        }

        override fun lastKnownWithin(maxAgeMs: Long): LocationSnapshot? {
            requestedMaxAgeMs = maxAgeMs
            return recent
        }
    }

    private fun vm(
        fix: LocationResult = LocationResult.Unavailable,
        geoIp: GeoIpLocationClient = GeoIpLocationClient { null },
        reader: FakeReader = FakeReader(fix),
    ) = CircadianExtrasViewModel(app, reader, geoIp)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun today() = System.currentTimeMillis() / 1000L / 86_400L

    private fun cached() = runBlocking { store.cachedSunLocation.first() }

    private fun <T> awaitValue(read: () -> T, predicate: (T) -> Boolean): T {
        repeat(100) {
            idle()
            val v = read()
            if (predicate(v)) return v
            Thread.sleep(10)
        }
        return read()
    }

    @org.junit.Before
    fun reset() = runBlocking {
        app.experimentPrefsDataStore.edit { it.clear() }
        Unit
    }

    @Test
    fun anAcquiredDeviceFixBecomesTheDaysCachedLocation() {
        val model = vm(fix = LocationResult.Available(LocationSnapshot(52.37021, 4.89707)))

        val pair = runBlocking { model.freshLatLon() }
        idle()

        assertEquals(52.37021 to 4.89707, pair)
        val cache = assertNotNull(cached(), "the acquired fix must survive as the day's location")
        assertEquals(52.37021, cache.latitude)
        assertEquals(today(), cache.day)
    }

    @Test
    fun theCachedFixClearsTheNoLocationBanner_evenWithNoFixedOverride() {
        val model = vm(fix = LocationResult.Available(LocationSnapshot(52.37021, 4.89707)))
        runBlocking { model.freshLatLon() }
        idle()

        val subscription = CoroutineScope(Dispatchers.Main).launch {
            model.circadianLocationStatus.collect {}
        }
        val status = awaitValue({ model.circadianLocationStatus.value }) { it.hasLocation }
        subscription.cancel()

        assertTrue(status.hasLocation, "a date-only pin must still resolve a location")
        assertFalse(status.isStale, "a fix acquired today is not stale")
        assertFalse(status.fixed, "it is the device's location, not a pinned place")
    }

    @Test
    fun typedCoordinatesDoNotBecomeTheDevicesCachedLocation() {
        val model = vm()
        model.set("2026-12-21", 12.0, 34.0)
        awaitValue({ runBlocking { store.dateLocation.first() } }) { it.latitude != null }

        assertNull(cached(), "a place the user asked about is not where the device is")
    }

    @Test
    fun aRecentFixIsUsedAtOnce_withoutSpendingTheAcquisitionWindow() {
        val reader = FakeReader(LocationResult.Unavailable, recent = LocationSnapshot(52.37021, 4.89707))
        val model = vm(reader = reader)

        val pair = runBlocking { model.freshLatLon() }
        idle()

        assertEquals(52.37021 to 4.89707, pair)
        assertEquals(0, reader.activeFixCalls, "a recent fix must short-circuit the 45 s wait entirely")
        assertEquals(60L * 60L * 1000L, reader.requestedMaxAgeMs, "an hour is the accepted staleness")
        assertNotNull(cached(), "and it is still the day's location")
    }

    @Test
    fun withNoRecentFix_theActiveAcquisitionStillRuns() {
        val reader = FakeReader(LocationResult.Available(LocationSnapshot(1.0, 2.0)), recent = null)
        val model = vm(reader = reader)

        assertEquals(1.0 to 2.0, runBlocking { model.freshLatLon() })
        assertEquals(1, reader.activeFixCalls, "nothing recent means the old path is the only path")
    }

    @Test
    fun aFailedAcquisitionCachesNothing() {
        val model = vm(fix = LocationResult.Unavailable)

        assertNull(runBlocking { model.freshLatLon() })
        idle()

        assertNull(cached())
    }
}
