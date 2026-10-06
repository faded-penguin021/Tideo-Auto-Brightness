package com.tideo.autobrightness.app.runtime

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DD-038: pinned → live fix → last-known → geo-IP (if enabled) → null. */
class ContextSolarTimesTest {

    private class FakeLocation(private val last: LocationSnapshot?) : LocationReader {
        override fun lastKnownLocation(): LocationSnapshot? = last
        override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = emptyFlow()
        override suspend fun currentLocation(): LocationResult = LocationResult.Unavailable
    }

    private val amsterdam = 52.37 to 4.90
    private val sydney = -33.87 to 151.21

    // 2026-10-06 15:30 UTC (17:30 in Amsterdam), the owner's report.
    private val reportMs = 1_791_300_600_000L

    private fun withStore(body: suspend (ExperimentPrefsStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("context_solar_test", ".preferences_pb").apply { delete() }
        try {
            runBlocking { body(ExperimentPrefsStore(PreferenceDataStoreFactory.create(scope = scope) { file })) }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun times(store: ExperimentPrefsStore, last: LocationSnapshot? = null) =
        ContextSolarTimes(store, FakeLocation(last))

    @Test
    fun noLocationAnywhere_isNull() = withStore { store ->
        assertNull(times(store).today(reportMs, liveFix = 0.0 to 0.0))
    }

    @Test
    fun pinnedLocation_winsOverLiveFixAndLastKnown() = withStore { store ->
        val sydneyTimes = times(store).today(reportMs, liveFix = sydney)
        val amsterdamTimes = times(store).today(reportMs, liveFix = amsterdam)
        assertNotEquals(sydneyTimes, amsterdamTimes)

        store.set(date = null, latitude = amsterdam.first, longitude = amsterdam.second)
        val last = LocationSnapshot(sydney.first, sydney.second)
        assertEquals(amsterdamTimes, times(store, last).today(reportMs, liveFix = sydney))
    }

    @Test
    fun liveFix_thenLastKnown_thenGeoIpOnlyWhenEnabled() = withStore { store ->
        val expected = times(store).today(reportMs, liveFix = amsterdam)
        val last = LocationSnapshot(amsterdam.first, amsterdam.second)
        assertEquals(expected, times(store, last).today(reportMs, liveFix = 0.0 to 0.0))

        store.writeCachedSunLocation(amsterdam.first, amsterdam.second, day = 20_000L)
        assertNull(times(store).today(reportMs))

        store.setGeoIpEnabled(true)
        assertEquals(expected, times(store).today(reportMs))
        assertNotEquals(expected, times(store).today(reportMs, liveFix = sydney))
    }

    @Test
    fun ownerReport_pinnedAmsterdamSunsetIsNotThePlaceholder() = withStore { store ->
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
        try {
            store.set(date = null, latitude = amsterdam.first, longitude = amsterdam.second)
            val (_, sunset) = assertNotNull(times(store).today(reportMs))
            // Almanac sunset for Amsterdam on 2026-10-06 is about 19:07 CEST, so SUNSET-30 is ~18:37.
            assertTrue(sunset in (18 * 3600 + 50 * 60)..(19 * 3600 + 20 * 60), "sunset $sunset")
            assertTrue(sunset - 30 * 60 > 17 * 3600 + 30 * 60)
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
