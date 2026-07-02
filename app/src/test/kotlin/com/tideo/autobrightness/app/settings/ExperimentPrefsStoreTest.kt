package com.tideo.autobrightness.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H3 glue-seam audit: the Preferences-DataStore round-trips (G2R-F39, D-103, D-105) had no test. */
class ExperimentPrefsStoreTest {

    /** Fresh store on a unique temp file per test; the scope is cancelled after the block. */
    private fun withStore(body: suspend (ExperimentPrefsStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("experiment_prefs_test", ".preferences_pb").apply { delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            runBlocking { body(ExperimentPrefsStore(dataStore)) }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun dateAndLocation_roundTrip_independently() = withStore { store ->
        // G2R-F39: date and location are independent — a date-only pin leaves location live (null).
        store.set(date = "2025-12-21", latitude = null, longitude = null)
        var v = store.dateLocation.first()
        assertEquals("2025-12-21", v.date)
        assertNull(v.latitude)
        assertNull(v.longitude)
        assertFalse(v.isUnset)

        // Location-only pin replaces the previous date-only pin (null removes the date).
        store.set(date = null, latitude = 51.5, longitude = -0.1)
        v = store.dateLocation.first()
        assertNull(v.date)
        assertEquals(51.5, v.latitude)
        assertEquals(-0.1, v.longitude)
    }

    @Test
    fun clear_revertsToLive() = withStore { store ->
        store.set(date = "2025-06-01", latitude = 10.0, longitude = 20.0)
        store.clear()
        assertTrue(store.dateLocation.first().isUnset, "clear() must revert every override field")
    }

    @Test
    fun cachedSunLocation_roundTrips_andIsNullBeforeFirstWrite() = withStore { store ->
        assertNull(store.readCachedSunLocation(), "no fix ever cached → null (D-103)")
        store.writeCachedSunLocation(latitude = 52.09, longitude = 5.12, day = 20_270L)
        assertEquals(CachedSunLocation(52.09, 5.12, 20_270L), store.readCachedSunLocation())
    }

    @Test
    fun geoIp_defaultsOff_optInPersists() = withStore { store ->
        assertFalse(store.geoIpEnabled.first(), "geo-IP fallback must default OFF (D-105 privacy)")
        store.setGeoIpEnabled(true)
        assertTrue(store.geoIpEnabled.first())
    }
}
