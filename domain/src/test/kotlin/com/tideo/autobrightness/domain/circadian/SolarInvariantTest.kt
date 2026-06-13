package com.tideo.autobrightness.domain.circadian

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INDEPENDENT ground-truth checks for [SolarCalculator] (S8.5/D-037).
 *
 * The circadian golden CSV is generated FROM this same production code (TaskerReference delegates
 * to SolarCalculator — D-037), so CircadianParityTest is a regression-lock, not an independent
 * oracle. These assertions instead rely on astronomical invariants that hold no matter how the
 * algorithm is implemented; each would be violated by a specific class of systematic bug:
 *
 *   - day length ≈ 12 h at the equator on an equinox            → gross constant / unit errors
 *   - dawn < sunrise < noon < sunset < dusk                      → twilight-zenith / branch mix-ups
 *   - moving EAST advances sunrise (earlier UTC)                 → longitude sign error
 *   - N hemisphere long day in June, short in December (and the  → LATITUDE sign error
 *     reverse in the S hemisphere)
 *   - high latitude → midnight sun (June) / polar night (Dec)    → polar-branch (cosH) errors
 *
 * Tolerances are deliberately wide (never false-fail on equation-of-time / refraction / the date
 * not being the exact solstice) yet far tighter than any sign flip, which shifts results by hours.
 */
class SolarInvariantTest {

    private fun epochNoonUtc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0).toEpochSecond(ZoneOffset.UTC)

    /** Seconds-of-day in [0,86400) — epochs sit on a UTC-midnight multiple of 86400. */
    private fun secOfDay(epoch: Long): Long = ((epoch % 86400) + 86400) % 86400

    private val equinox = epochNoonUtc(2024, 3, 20)
    private val juneSolstice = epochNoonUtc(2024, 6, 20)
    private val decSolstice = epochNoonUtc(2024, 12, 21)

    @Test
    fun equatorEquinox_dayIsAboutTwelveHours() {
        val s = SolarCalculator.compute(latitudeDeg = 0.0, longitudeDeg = 0.0, dateEpochSec = equinox, tzOffsetHours = 0.0)
        assertEquals("ok", s.sunStatus)
        // ~12 h + a few minutes (refraction/disk via the 90.8333° zenith).
        assertTrue(s.sunlightDurationMinutes in 705..745, "equator equinox day=${s.sunlightDurationMinutes}min")
        assertTrue(secOfDay(s.riseEpochSec) in 19_800..23_400, "sunrise=${secOfDay(s.riseEpochSec)}s (expect ~06:00)")
        assertTrue(secOfDay(s.setEpochSec) in 63_000..66_600, "sunset=${secOfDay(s.setEpochSec)}s (expect ~18:00)")
    }

    @Test
    fun twilightBracketsAndOrders_sunriseSunset() {
        val s = SolarCalculator.compute(51.5, 0.0, equinox, 0.0) // London-ish
        assertEquals("ok", s.sunStatus)
        assertTrue(
            s.dawnEpochSec < s.riseEpochSec &&
                s.riseEpochSec < s.noonEpochSec &&
                s.noonEpochSec < s.setEpochSec &&
                s.setEpochSec < s.duskEpochSec,
            "order dawn<rise<noon<set<dusk violated: dawn=${s.dawnEpochSec} rise=${s.riseEpochSec} " +
                "noon=${s.noonEpochSec} set=${s.setEpochSec} dusk=${s.duskEpochSec}",
        )
    }

    @Test
    fun movingEast_advancesSunriseInUtc() {
        val s0 = SolarCalculator.compute(0.0, 0.0, equinox, 0.0)
        val sEast = SolarCalculator.compute(0.0, 30.0, equinox, 0.0) // +30° E ≈ 2 h earlier in UTC
        val rise0 = secOfDay(s0.riseEpochSec)
        val riseEast = secOfDay(sEast.riseEpochSec)
        assertTrue(riseEast < rise0, "east sunrise should be earlier in UTC: east=$riseEast vs base=$rise0")
        val diffMin = (rise0 - riseEast) / 60
        assertTrue(diffMin in 100..140, "30°E should advance sunrise ~120min, got ${diffMin}min")
    }

    @Test
    fun latitudeSign_northLongDayInJune_shortInDecember() {
        val north = 51.5 // northern mid-latitude
        assertTrue(SolarCalculator.compute(north, 0.0, juneSolstice, 0.0).sunlightDurationMinutes > 840, "N June should be >14h")
        assertTrue(SolarCalculator.compute(north, 0.0, decSolstice, 0.0).sunlightDurationMinutes < 540, "N Dec should be <9h")
    }

    @Test
    fun latitudeSign_southReversesSeasons() {
        val south = -33.87 // southern mid-latitude
        assertTrue(SolarCalculator.compute(south, 0.0, juneSolstice, 0.0).sunlightDurationMinutes < 660, "S June should be <11h")
        assertTrue(SolarCalculator.compute(south, 0.0, decSolstice, 0.0).sunlightDurationMinutes > 780, "S Dec should be >13h")
    }

    @Test
    fun highArctic_midnightSunInJune() {
        val s = SolarCalculator.compute(78.0, 0.0, juneSolstice, 0.0)
        assertEquals("polar", s.sunStatus, "78°N in June is midnight sun")
        assertEquals(1440L, s.sunlightDurationMinutes)
    }

    @Test
    fun highArctic_polarNightInDecember() {
        val s = SolarCalculator.compute(78.0, 0.0, decSolstice, 0.0)
        assertEquals("polar", s.sunStatus, "78°N in December is polar night")
        assertEquals(0L, s.sunlightDurationMinutes)
    }
}
