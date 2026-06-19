package com.tideo.autobrightness.app.runtime

import android.content.Intent
import com.tideo.autobrightness.platform.context.BatteryState
import com.tideo.autobrightness.platform.context.BatteryStateReader
import com.tideo.autobrightness.platform.context.ForegroundAppMonitor
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import com.tideo.autobrightness.platform.context.SsidResult
import com.tideo.autobrightness.platform.context.WifiInfoReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S12.9d backfill — the Android context-signal source's clock/calendar/solar assembly: it passes the
 * engine-fed app/battery/wifi/location through untouched, derives day-of-week + local seconds-of-day
 * from the (injected) clock, and falls back to 06:00/18:00 sunrise/sunset when there is no location fix.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidContextSignalSourceTest {

    private class FakeBattery : BatteryStateReader {
        override fun batteryState(): Flow<BatteryState> = emptyFlow()
    }
    private class FakeWifi : WifiInfoReader {
        override fun ssidFlow(): Flow<String?> = emptyFlow()
        override suspend fun currentSsid(): SsidResult = SsidResult.Unknown
    }
    private class FakeForeground : ForegroundAppMonitor {
        override fun hasUsageAccessPermission(): Boolean = false
        override fun usageAccessSettingsIntent(): Intent = throw NotImplementedError("unused by assemble")
        override fun foregroundPackage(intervalMs: Long): Flow<String?> = emptyFlow()
    }
    private class FakeLocation(private val last: LocationSnapshot?) : LocationReader {
        override fun lastKnownLocation(): LocationSnapshot? = last
        override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = emptyFlow()
        override suspend fun currentLocation(): LocationResult = LocationResult.Unavailable
    }

    private fun source(last: LocationSnapshot?, clockMs: Long) = AndroidContextSignalSource(
        context = RuntimeEnvironment.getApplication(),
        battery = FakeBattery(),
        wifi = FakeWifi(),
        foregroundApp = FakeForeground(),
        location = FakeLocation(last),
        clock = { clockMs },
    )

    // A fixed instant; derived clock fields are compared against a Calendar built the same way so the
    // assertion is timezone-independent.
    private val fixedClock = 1_750_000_000_000L

    @Test
    fun assemble_passesThroughAndDerivesClockFields() = runTest {
        val signals = source(last = null, clockMs = fixedClock)
            .assemble(app = "com.netflix.mediaclient", batteryPercent = 42, plugged = true, wifi = "Home", lat = 0.0, lon = 0.0)

        assertEquals("com.netflix.mediaclient", signals.app)
        assertEquals(42, signals.batteryPercent)
        assertTrue(signals.plugged)
        assertEquals("Home", signals.wifi)

        val cal = Calendar.getInstance().apply { timeInMillis = fixedClock }
        assertEquals(cal.get(Calendar.DAY_OF_WEEK), signals.dayOfWeek)
        val expectedSecs = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
        assertEquals(expectedSecs, signals.nowSecondsOfDay)
    }

    @Test
    fun assemble_noLocationFix_usesDefaultSunriseSunset() = runTest {
        val signals = source(last = null, clockMs = fixedClock)
            .assemble(app = "", batteryPercent = 50, plugged = false, wifi = "", lat = 0.0, lon = 0.0)
        assertEquals(21_600L, signals.sunriseLocalSecs) // 06:00
        assertEquals(64_800L, signals.sunsetLocalSecs)  // 18:00
    }

    @Test
    fun assemble_withFix_computesNonDefaultSolarTimes() = runTest {
        // Amsterdam in June: sunrise well before 06:00, sunset well after 18:00 → not the fallbacks.
        val signals = source(last = null, clockMs = fixedClock)
            .assemble(app = "", batteryPercent = 50, plugged = false, wifi = "", lat = 52.37, lon = 4.90)
        assertTrue(signals.sunriseLocalSecs in 0L until 86_400L)
        assertTrue(signals.sunsetLocalSecs in 0L until 86_400L)
        assertTrue(
            signals.sunriseLocalSecs != 21_600L || signals.sunsetLocalSecs != 64_800L,
            "expected a computed (non-default) solar window for a real fix",
        )
    }
}
