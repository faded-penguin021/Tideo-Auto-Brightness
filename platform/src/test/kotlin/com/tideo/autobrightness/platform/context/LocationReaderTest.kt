package com.tideo.autobrightness.platform.context

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * H3 glue-seam audit: `LocationReader.activeFix` (D-120/D-122) had no test — the active
 * requestLocationUpdates acquisition, the null-island skip, the last-known BACKUP on timeout,
 * and the call-time permission recheck are all decided here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocationReaderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val lm = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val reader = AndroidLocationReader(application)

    init {
        // Robolectric grants no runtime permissions by default; activeFix rechecks at call time.
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    private fun fix(provider: String, lat: Double, lon: Double, time: Long = 1_000L) =
        Location(provider).apply {
            latitude = lat
            longitude = lon
            this.time = time
        }

    @Test
    fun activeFix_deliversTheFreshProviderFix() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 10_000) }

        shadowOf(lm).simulateLocation(fix(LocationManager.GPS_PROVIDER, 51.5, -0.1))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocationResult.Available(LocationSnapshot(51.5, -0.1)), result.await())
    }

    @Test
    fun activeFix_skipsNullIsland_keepsListeningForARealFix() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 10_000) }

        // A (0,0) "null island" read must be ignored, not returned (D-122 / the `loc 0.0,0.0` bug).
        shadowOf(lm).simulateLocation(fix(LocationManager.GPS_PROVIDER, 0.0, 0.0))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isActive, "a null-island fix must not complete the acquisition")

        shadowOf(lm).simulateLocation(fix(LocationManager.GPS_PROVIDER, 48.85, 2.35))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LocationResult.Available(LocationSnapshot(48.85, 2.35)), result.await())
    }

    @Test
    fun activeFix_timeout_fallsBackToBestLastKnown() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(LocationManager.GPS_PROVIDER, 10.0, 20.0))
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 1_000) }

        advanceUntilIdle() // no fresh fix arrives; the virtual clock passes the timeout

        assertEquals(
            LocationResult.Available(LocationSnapshot(10.0, 20.0)),
            result.await(),
            "the last-known fix is the BACKUP when no fresh fix lands in time (D-122)",
        )
    }

    @Test
    fun activeFix_missingPermission_reportsNeedsPermission() = runTest {
        shadowOf(application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        assertEquals(LocationResult.NeedsPermission, reader.activeFix(timeoutMs = 1_000))
    }

    @Test
    fun activeFix_noEnabledProvidersAndNoCache_isUnavailable() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 1_000) }
        advanceUntilIdle()
        assertEquals(LocationResult.Unavailable, result.await())
    }
}
