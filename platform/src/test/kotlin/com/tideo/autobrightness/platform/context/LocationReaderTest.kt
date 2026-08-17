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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H3 audit: LocationReader.activeFix (D-120/D-122) active acquisition, null-island skip, backup. */
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

    @Test
    fun activeFix_registersBothEnabledProviders_andLeavesNoListenerBehind() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 10_000) }

        assertEquals(1, shadowOf(lm).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        assertEquals(1, shadowOf(lm).getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).size)

        shadowOf(lm).simulateLocation(fix(LocationManager.NETWORK_PROVIDER, 51.5, -0.1))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocationResult.Available(LocationSnapshot(51.5, -0.1)), result.await())
        assertTrue(
            shadowOf(lm).locationUpdateListeners.isEmpty(),
            "a satisfied fix must release every provider it powered, or GPS stays on",
        )
    }

    @Test
    fun activeFix_timeout_alsoReleasesEveryProvider() = runTest {
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 1_000) }

        advanceUntilIdle()

        assertEquals(LocationResult.Unavailable, result.await())
        assertTrue(
            shadowOf(lm).locationUpdateListeners.isEmpty(),
            "giving up must release the providers too",
        )
    }

    @Test
    fun lastKnownWithin_takesTheNewestFreshFix_andRefusesAStaleOrNullIslandOne() {
        val now = System.currentTimeMillis()
        val hour = 60L * 60L * 1000L
        shadowOf(lm).setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            fix(LocationManager.NETWORK_PROVIDER, 51.5, -0.1, time = now - 10 * 60 * 1000L),
        )
        shadowOf(lm).setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            fix(LocationManager.GPS_PROVIDER, 48.85, 2.35, time = now - 2 * 60 * 1000L),
        )

        assertEquals(LocationSnapshot(48.85, 2.35), reader.lastKnownWithin(hour), "newest wins")

        shadowOf(lm).setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            fix(LocationManager.GPS_PROVIDER, 48.85, 2.35, time = now - 5 * hour),
        )
        assertEquals(
            LocationSnapshot(51.5, -0.1),
            reader.lastKnownWithin(hour),
            "a fix older than the bound is not 'recent enough', however new it is relative to others",
        )

        shadowOf(lm).setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            fix(LocationManager.NETWORK_PROVIDER, 0.0, 0.0, time = now),
        )
        assertNull(reader.lastKnownWithin(hour), "null island is not a location, however fresh")
    }

    @Test
    fun activeFix_withLocationServicesOff_givesUpAtOnceInsteadOfSpendingTheWindow() {
        runTest {
            shadowOf(lm).setLocationEnabled(false)
            shadowOf(lm).setLastKnownLocation(
                LocationManager.GPS_PROVIDER,
                fix(LocationManager.GPS_PROVIDER, 10.0, 20.0),
            )
            val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 45_000) }

            assertTrue(
                shadowOf(lm).locationUpdateListeners.isEmpty(),
                "nothing can deliver with the master switch off, so nothing should be registered",
            )
            assertEquals(LocationResult.Available(LocationSnapshot(10.0, 20.0)), result.await())
            assertFalse(reader.locationServicesEnabled())
        }
    }

    @Test
    fun activeFix_withNoRealProviderEnabled_stillListensPassively() {
        runTest {
            shadowOf(lm).setLocationEnabled(true)
            shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            val result = async(UnconfinedTestDispatcher(testScheduler)) { reader.activeFix(timeoutMs = 10_000) }

            assertEquals(
                1,
                shadowOf(lm).getLocationUpdateListeners(LocationManager.PASSIVE_PROVIDER).size,
                "giving up before registering anything is what made this fail instantly",
            )
            shadowOf(lm).simulateLocation(fix(LocationManager.PASSIVE_PROVIDER, 35.68, 139.69))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(LocationResult.Available(LocationSnapshot(35.68, 139.69)), result.await())
        }
    }
}
