package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.CachedSunLocation
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import com.tideo.autobrightness.platform.context.LocationReader
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.LocationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * G2R-F73 regression: the pipeline must feed the REAL sunrise/sunset ramp windows to the dynamic-scale
 * engine, not `TimeContext`'s fixed 6–8am-UTC defaults. Pure JVM — exercises the app-layer
 * [CircadianWindowProvider.compute] + the fenced domain [DynamicScaleEngine] only (domain untouched).
 *
 * Scenario: Edinburgh (55.95, −3.19), mid-June, device clock UTC+1 (BST). At **07:13 local = 06:13
 * UTC** the sun has been up for ~3h, so `%AAB_ScaleDynamic` should be in full daytime (> 1). The old
 * fixed windows treated 06–08 UTC (= 07–09 local) as "morning", so the ramp had barely started there
 * → scaleDynamic < 1 (the owner's bug).
 */
class CircadianWindowProviderTest {

    // Utrecht, NL — the owner's location (UTC+2 / CEST in June).
    private val lat = 52.0907
    private val lon = 5.1214
    private val tzOffsetHours = 2.0
    private val transitionFactor = 0.1
    private val scaleSpread = 15.0
    private val steepness = 6.0

    // 08:13 local (CEST) == 06:13 UTC, as UTC seconds-of-day (the pipeline's `now` frame). This is the
    // instant the owner saw scaleDynamic = 0.852.
    private val now0613Utc = (6 * 3600 + 13 * 60).toDouble()

    private fun midJuneEpochSec(): Long {
        val cal = Calendar.getInstance(SimpleTimeZone(2 * 3_600_000, "CEST"))
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    @Test
    fun utrecht_sunriseAndDaytimeScale_matchReality() {
        // Sanity-anchor against a real-world almanac value: Utrecht sunrise on 2026-06-15 ≈ 05:18 local.
        val solar = com.tideo.autobrightness.domain.circadian.SolarCalculator
            .compute(lat, lon, midJuneEpochSec(), tzOffsetHours)
        fun local(epochSec: Long): String {
            val s = Math.floorMod(epochSec + (tzOffsetHours * 3600).toLong(), 86_400L)
            return "%02d:%02d".format(s / 3600, (s % 3600) / 60)
        }
        assertTrue(local(solar.riseEpochSec) == "05:18", "Utrecht sunrise was ${local(solar.riseEpochSec)}")

        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        val scale = scaleDynamicAt(
            now0613Utc, w.morningStart, w.morningEnd, w.eveningStart, w.eveningEnd, w.sunlightDurationMinutes,
        )
        // Full daytime at 08:13 local → progress 1 → 1 + 15% = 1.15 (was 0.852 with the fixed windows).
        assertTrue(kotlin.math.abs(scale - 1.15) < 1e-6, "expected 1.15 daytime scale, was $scale")
    }

    private fun scaleDynamicAt(
        now: Double,
        morningStart: Double, morningEnd: Double,
        eveningStart: Double, eveningEnd: Double,
        sunlightMinutes: Double,
    ): Double = DynamicScaleEngine.compute(
        DynamicScaleInput(
            nowSecOfDay = now,
            morningStart = morningStart, morningEnd = morningEnd,
            eveningStart = eveningStart, eveningEnd = eveningEnd,
            sunlightDurationMinutes = sunlightMinutes,
            isPolar = false, steepness = steepness, scaleSpreadPercent = scaleSpread,
        ),
    ).scaleDynamic

    @Test
    fun realWindows_giveDaytimeScale_at0813Local() {
        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        // Morning ramp completes well before 06:13 UTC; evening is far later → full daytime.
        assertTrue(w.morningEnd < now0613Utc, "morningEnd ${w.morningEnd} should be before $now0613Utc")
        assertTrue(w.eveningStart > now0613Utc, "eveningStart ${w.eveningStart} should be after $now0613Utc")

        val scale = scaleDynamicAt(
            now0613Utc, w.morningStart, w.morningEnd, w.eveningStart, w.eveningEnd, w.sunlightDurationMinutes,
        )
        assertTrue(scale > 1.0, "real sunrise → daytime scale > 1, was $scale")
    }

    @Test
    fun defaultWindows_reproduceTheBug_subUnityScale() {
        // The pre-F73 behaviour: TimeContext defaults (6–8am / 18–20pm UTC, 720 min sunlight).
        val scale = scaleDynamicAt(
            now0613Utc, 6 * 3600.0, 8 * 3600.0, 18 * 3600.0, 20 * 3600.0, 720.0,
        )
        assertTrue(scale < 1.0, "fixed 6–8am-UTC morning makes 06:13 UTC sub-unity (the bug), was $scale")
    }

    @Test
    fun realWindows_areOrderedAndNonPolarAtMidLatitude() {
        val w = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), tzOffsetHours, transitionFactor)
        assertTrue(!w.isPolar, "55.95°N in June is not polar")
        assertTrue(w.morningStart < w.morningEnd, "morning window ordered")
        assertTrue(w.eveningStart < w.eveningEnd, "evening window ordered")
        assertTrue(w.morningEnd <= w.eveningStart, "day spans morningEnd..eveningStart")
    }

    // ----- F39: fixed Date / Location override actually changes the scaling -----

    private fun decEpochSec(): Long {
        val cal = Calendar.getInstance(SimpleTimeZone(1 * 3_600_000, "CET")) // NL winter = UTC+1
        cal.set(2025, Calendar.DECEMBER, 21, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    @Test
    fun fixedDate_changesSunlightDuration_G2RF39() {
        // A fixed 21-Dec date must produce a much shorter day than mid-June at the same location.
        val june = CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), 2.0, transitionFactor)
        val dec = CircadianWindowProvider.compute(lat, lon, decEpochSec(), 1.0, transitionFactor)
        assertTrue(
            dec.sunlightDurationMinutes < june.sunlightDurationMinutes - 200,
            "Utrecht winter solstice (${dec.sunlightDurationMinutes}m) << midsummer (${june.sunlightDurationMinutes}m)",
        )
    }

    @Test
    fun fixedLocationOverride_appliesAndSkipsAcquisition_G2RF39_F83() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var geoIpCalled = false
        val loc = FakeLocationReader() // would return null → forces fallback IF consulted
        val provider = CircadianWindowProvider(
            scope = scope,
            overrideFlow = MutableStateFlow(ExperimentDateLocation(date = "2025-12-21", latitude = lat, longitude = lon)),
            location = loc,
            geoIpFallback = { geoIpCalled = true; null },
            clock = { decEpochSec() * 1000L },
            tzOffsetForDate = { 1.0 },
        )
        val w = provider.current(transitionFactor)
        assertNotNull(w, "fixed date+location yields windows directly")
        assertFalse(geoIpCalled, "fixed lat/lon must skip the geo-IP acquisition (task90 skip-when-fixed)")
        assertFalse(loc.lastKnownCalled, "fixed lat/lon must not consult Android location")
        // Equivalent to the pure winter-solstice computation.
        assertEquals(CircadianWindowProvider.compute(lat, lon, decEpochSec(), 1.0, transitionFactor), w)
        scope.cancel()
    }

    @Test
    fun dateOnlyOverride_usesLiveLocation_G2RF39() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val loc = FakeLocationReader(lastKnown = LocationSnapshot(lat, lon))
        val provider = CircadianWindowProvider(
            scope = scope,
            // date pinned, NO coords → live (last-known) location
            overrideFlow = MutableStateFlow(ExperimentDateLocation(date = "2025-12-21")),
            location = loc,
            geoIpFallback = { null },
            clock = { midJuneEpochSec() * 1000L }, // "now" is June, but the fixed Dec date must win
            tzOffsetForDate = { 1.0 },
        )
        val w = provider.current(transitionFactor)
        assertNotNull(w)
        assertTrue(loc.lastKnownCalled, "date-only override still acquires the live location")
        // Windows reflect the FIXED Dec date (short day), not today's June.
        assertEquals(CircadianWindowProvider.compute(lat, lon, decEpochSec(), 1.0, transitionFactor), w)
        scope.cancel()
    }

    // ----- F83: geo-IP fallback when no Android fix is available -----

    @Test
    fun noAndroidFix_fallsBackToGeoIp_G2RF83() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val loc = FakeLocationReader() // no last-known, currentLocation Unavailable
        val provider = CircadianWindowProvider(
            scope = scope,
            overrideFlow = MutableStateFlow(ExperimentDateLocation()),
            location = loc,
            geoIpFallback = { LocationSnapshot(lat, lon) }, // ip-api yields Utrecht
            clock = { midJuneEpochSec() * 1000L },
            tzOffsetForDate = { 2.0 },
        )
        // The acquire launch runs inline (Unconfined) → first call already has the geo-IP fix.
        val w = provider.current(transitionFactor)
        assertNotNull(w, "geo-IP fallback supplies a location when Android has none")
        assertEquals(CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), 2.0, transitionFactor), w)
        scope.cancel()
    }

    @Test
    fun noLocationAtAll_returnsNull_keepsDefaultWindows_G2RF73() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val provider = CircadianWindowProvider(
            scope = scope,
            overrideFlow = MutableStateFlow(ExperimentDateLocation()),
            location = FakeLocationReader(),
            geoIpFallback = { null }, // network also down
            clock = { midJuneEpochSec() * 1000L },
            tzOffsetForDate = { 2.0 },
        )
        assertNull(provider.current(transitionFactor), "no fix anywhere → null → pipeline keeps default windows")
        scope.cancel()
    }

    // ----- D-103: persist/restore the once-a-day location across process restarts -----

    @Test
    fun persistedLocation_isUsedOnColdStart_D103() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val loc = FakeLocationReader() // no live fix available this session
        var geoIpCalled = false
        val today = midJuneEpochSec() / 86_400L
        val provider = CircadianWindowProvider(
            scope = scope,
            overrideFlow = MutableStateFlow(ExperimentDateLocation()),
            location = loc,
            geoIpFallback = { geoIpCalled = true; null },
            // a persisted fix for TODAY (Tasker %AAB_SunLat/Lon + %AAB_SunLastDate)
            loadCachedLocation = { CachedSunLocation(lat, lon, today) },
            clock = { midJuneEpochSec() * 1000L },
            tzOffsetForDate = { 2.0 },
        )
        val w = provider.current(transitionFactor)
        assertNotNull(w, "a persisted location must produce windows on cold start instead of null/defaults (D-103)")
        assertEquals(CircadianWindowProvider.compute(lat, lon, midJuneEpochSec(), 2.0, transitionFactor), w)
        assertFalse(geoIpCalled, "a persisted fix for today must not trigger a network re-acquire")
        assertFalse(loc.lastKnownCalled, "a persisted fix for today must not consult Android location")
        scope.cancel()
    }

    @Test
    fun acquiredLocation_isPersisted_D103() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var saved: Triple<Double, Double, Long>? = null
        val provider = CircadianWindowProvider(
            scope = scope,
            overrideFlow = MutableStateFlow(ExperimentDateLocation()),
            location = FakeLocationReader(), // no Android fix → geo-IP path
            geoIpFallback = { LocationSnapshot(lat, lon) },
            persistLocation = { la, lo, day -> saved = Triple(la, lo, day) },
            clock = { midJuneEpochSec() * 1000L },
            tzOffsetForDate = { 2.0 },
        )
        provider.current(transitionFactor) // triggers the (inline, Unconfined) acquire
        assertEquals(
            Triple(lat, lon, midJuneEpochSec() / 86_400L),
            saved,
            "a freshly acquired location must be persisted for the next cold start (D-103)",
        )
        scope.cancel()
    }

    // ----- F73: tz offset is taken at the TARGET date instant (DST-aware) -----

    @Test
    fun tzOffset_isEvaluatedAtTargetDateInstant_G2RF73() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val seenOffsets = mutableListOf<Double>()
        val provider = CircadianWindowProvider(
            scope = scope,
            // fixed Dec date + location so the path is deterministic and skips acquisition
            overrideFlow = MutableStateFlow(ExperimentDateLocation(date = "2025-12-21", latitude = lat, longitude = lon)),
            location = FakeLocationReader(),
            geoIpFallback = { null },
            clock = { midJuneEpochSec() * 1000L }, // "now" is summer (would be +2 if naively used)
            tzOffsetForDate = { epochSec ->
                // Real DST-aware default would do exactly this; we record what instant it is asked about.
                val off = TimeZone.getTimeZone("Europe/Amsterdam").getOffset(epochSec * 1000L) / 3_600_000.0
                seenOffsets += off
                off
            },
        )
        val w = provider.current(transitionFactor)
        assertNotNull(w)
        // The window math must have used the WINTER (+1) offset of the fixed Dec date, never the
        // summer (+2) offset of "now" — proving the offset is read at the target instant, not now.
        assertTrue(seenOffsets.contains(1.0), "Dec target instant → +1 offset used; saw $seenOffsets")
        assertEquals(CircadianWindowProvider.compute(lat, lon, decEpochSec(), 1.0, transitionFactor), w)
        scope.cancel()
    }

    /** Minimal [LocationReader] fake: configurable last-known; current/updates inert. */
    private class FakeLocationReader(
        private val lastKnown: LocationSnapshot? = null,
    ) : LocationReader {
        var lastKnownCalled = false
            private set

        override fun lastKnownLocation(): LocationSnapshot? {
            lastKnownCalled = true
            return lastKnown
        }

        override fun locationUpdates(minTimeMs: Long, minDistanceM: Float): Flow<LocationSnapshot> = emptyFlow()

        override suspend fun currentLocation(): LocationResult =
            lastKnown?.let { LocationResult.Available(it) } ?: LocationResult.Unavailable
    }
}
