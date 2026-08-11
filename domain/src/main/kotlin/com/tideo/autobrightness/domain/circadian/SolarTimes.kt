package com.tideo.autobrightness.domain.circadian

import java.util.Calendar
import java.util.SimpleTimeZone
import kotlin.math.*

/** NOAA solar-times calculator (task90 Java Block #1). Polar: cosH > 1 → -1.0; cosH < -1 → -2.0. */
data class SolarTimesResult(
    /** "ok" or "polar" */
    val sunStatus: String,
    val riseEpochSec: Long,
    val setEpochSec: Long,
    val dawnEpochSec: Long,
    val duskEpochSec: Long,
    val noonEpochSec: Long,
    /** Rounded minutes (Math.round). 1440 = midnight sun, 0 = polar night. */
    val sunlightDurationMinutes: Long,
)

/** Schedule windows from solar times + transition factor (task90 act59–act76). Seconds-of-day, monotonic. */
data class SolarWindows(
    val dawnSecOfDay: Double,
    val sunriseSecOfDay: Double,
    val noonSecOfDay: Double,
    val sunsetSecOfDay: Double,
    val duskSecOfDay: Double,
    val morningStart: Double,
    val morningEnd: Double,
    val morningDuration: Double,
    val eveningStart: Double,
    val eveningEnd: Double,
    val eveningDuration: Double,
    val daylengthSec: Double,
    val nightlengthSec: Double,
)

object SolarCalculator {

    // Indices: 0=Rise, 1=Set, 2=Dawn, 3=Dusk — Tasker: task90 Java Block #1 L139
    private val IS_SUNRISE = booleanArrayOf(true, false, true, false)
    private val ZENITH_DEG = doubleArrayOf(90.8333, 90.8333, 96.0, 96.0)

    /** Compute solar times for location and date (task90 Java Block #1). */
    fun compute(
        latitudeDeg: Double,
        longitudeDeg: Double,
        dateEpochSec: Long,
        tzOffsetHours: Double,
    ): SolarTimesResult {
        // Explicit timezone for consistent dayOfYear / startOfDay.
        val tzMs = Math.round(tzOffsetHours * 3_600_000.0).toInt()
        val tz = SimpleTimeZone(tzMs, "AAB")
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = dateEpochSec * 1000L
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val zoneOffset = tzOffsetHours

        // Solar loop: indices 0=Rise, 1=Set, 2=Dawn, 3=Dusk
        val results = DoubleArray(4)
        for (i in 0..3) {
            val isSunrise = IS_SUNRISE[i]
            val zenith = ZENITH_DEG[i]
            val lngHour = longitudeDeg / 15.0

            val tApprox = if (isSunrise) {
                dayOfYear + (6.0 - lngHour) / 24.0
            } else {
                dayOfYear + (18.0 - lngHour) / 24.0
            }

            // Mean anomaly, true longitude
            val m = (0.9856 * tApprox) - 3.289
            var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2.0 * m))) + 282.634
            l = (l + 360.0) % 360.0

            // Right ascension (quadrant-corrected).
            var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
            ra = (ra + 360.0) % 360.0
            val lQuadrant = floor(l / 90.0) * 90.0
            val raQuadrant = floor(ra / 90.0) * 90.0
            ra = ra + (lQuadrant - raQuadrant)
            ra /= 15.0

            // Declination, local hour angle.
            val sinDec = 0.39782 * sin(Math.toRadians(l))
            val cosDec = cos(asin(sinDec))

            val cosH = (cos(Math.toRadians(zenith)) - sinDec * sin(Math.toRadians(latitudeDeg))) /
                (cosDec * cos(Math.toRadians(latitudeDeg)))

            results[i] = when {
                cosH > 1.0 -> -1.0    // sun never rises
                cosH < -1.0 -> -2.0   // sun never sets
                else -> {
                    val h = if (isSunrise) {
                        360.0 - Math.toDegrees(acos(cosH))
                    } else {
                        Math.toDegrees(acos(cosH))
                    }
                    val hHours = h / 15.0
                    val t = hHours + ra - (0.06571 * tApprox) - 6.622
                    val ut = t - lngHour
                    val localT = ut + zoneOffset
                    (localT + 24.0) % 24.0
                }
            }
        }

        val riseHour = results[0]
        val setHour = results[1]
        val dawnHour = results[2]
        val duskHour = results[3]

        // Solar noon.
        val noonHour = if (setHour < riseHour) {
            ((riseHour + setHour + 24.0) / 2.0) % 24.0
        } else {
            (riseHour + setHour) / 2.0
        }

        // Midnight in local timezone.
        val calBase = Calendar.getInstance(tz)
        calBase.timeInMillis = dateEpochSec * 1000L
        calBase.set(Calendar.HOUR_OF_DAY, 0)
        calBase.set(Calendar.MINUTE, 0)
        calBase.set(Calendar.SECOND, 0)
        calBase.set(Calendar.MILLISECOND, 0)
        val startOfDay = calBase.timeInMillis / 1000L

        fun epoch(h: Double): Long = startOfDay + (h * 3600.0).toLong()

        return if (riseHour < 0 || setHour < 0) {
            // Polar: midnight-sun (-2.0) → 1440L; polar-night (-1.0) → 0L.
            val duration = if (riseHour == -2.0 || setHour == -2.0) 1440L else 0L
            val noonEpoch = epoch(12.0)
            SolarTimesResult(
                sunStatus = "polar",
                riseEpochSec = noonEpoch,
                setEpochSec = noonEpoch,
                dawnEpochSec = noonEpoch,
                duskEpochSec = noonEpoch,
                noonEpochSec = noonEpoch,
                sunlightDurationMinutes = duration,
            )
        } else {
            val riseEpoch = epoch(riseHour)
            val setEpoch = epoch(setHour)
            var durationMins = (setEpoch - riseEpoch) / 60.0
            if (durationMins < 0) durationMins += 1440.0
            SolarTimesResult(
                sunStatus = "ok",
                riseEpochSec = riseEpoch,
                setEpochSec = setEpoch,
                dawnEpochSec = epoch(dawnHour),
                duskEpochSec = epoch(duskHour),
                noonEpochSec = epoch(noonHour),
                sunlightDurationMinutes = Math.round(durationMins),
            )
        }
    }

    /** Build schedule windows from SolarTimesResult + scaleTransitionFactor (task90 act59–act76). */
    fun buildScheduleWindows(solar: SolarTimesResult, scaleTransitionFactor: Double): SolarWindows {
        // task90 act59: epoch → seconds-of-day (% 86400).
        val rawDawn = (solar.dawnEpochSec % 86400).toDouble()
        val rawRise = (solar.riseEpochSec % 86400).toDouble()
        val rawNoon = (solar.noonEpochSec % 86400).toDouble()
        val rawSet = (solar.setEpochSec % 86400).toDouble()
        val rawDusk = (solar.duskEpochSec % 86400).toDouble()

        var calcDawn = rawDawn
        var calcRise = rawRise
        var calcNoon = rawNoon
        var calcSet = rawSet
        var calcDusk = rawDusk

        // task90 act61–72: monotonic ordering (dawn ≤ rise ≤ noon ≤ set ≤ dusk).
        if (calcDawn > calcRise) calcDawn -= 86400.0
        if (calcNoon < calcRise) calcNoon += 86400.0
        if (calcSet < calcRise) calcSet += 86400.0
        if (calcDusk < calcSet) calcDusk += 86400.0

        // task90 act73–75: shift if dawn < 0.
        if (calcDawn < 0) {
            calcDawn += 86400.0; calcRise += 86400.0; calcNoon += 86400.0
            calcSet += 86400.0; calcDusk += 86400.0
        }

        // task90 act76: transition windows.
        val daylength = calcSet - calcRise
        val nightlength = 86400.0 - daylength
        val deltaDay = daylength * scaleTransitionFactor
        val deltaNight = nightlength * scaleTransitionFactor

        val morningStart = calcDawn - deltaNight
        val morningEnd = calcRise + deltaDay
        val eveningStart = calcSet - deltaDay
        val eveningEnd = calcDusk + deltaNight

        return SolarWindows(
            dawnSecOfDay = calcDawn,
            sunriseSecOfDay = calcRise,
            noonSecOfDay = calcNoon,
            sunsetSecOfDay = calcSet,
            duskSecOfDay = calcDusk,
            morningStart = morningStart,
            morningEnd = morningEnd,
            morningDuration = morningEnd - morningStart,
            eveningStart = eveningStart,
            eveningEnd = eveningEnd,
            eveningDuration = eveningEnd - eveningStart,
            daylengthSec = daylength,
            nightlengthSec = nightlength,
        )
    }
}
