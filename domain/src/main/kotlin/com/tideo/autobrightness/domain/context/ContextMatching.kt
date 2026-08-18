package com.tideo.autobrightness.domain.context

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared per-dimension trigger matching from Tasker: task43 `_EvaluateContexts V2`
 * (extracted; behavior-preserving). Precedence (priority/specificity) is caller policy, not here.
 */
internal object ContextMatching {

    private const val EARTH_RADIUS_M = 6_371_000.0
    internal const val SECONDS_PER_DAY = 86_400L

    internal fun resolveTimeToken(token: String, signals: ContextSignals): Long = when (token) {
        "SUNRISE" -> signals.sunriseLocalSecs
        "SUNSET" -> signals.sunsetLocalSecs
        else -> {
            val parts = token.split(":")
            parts[0].trim().toLong() * 3600 + parts[1].trim().toLong() * 60
        }
    }

    /** Time-of-day + day-of-week window verdict (task43 L314-354). start > end = overnight range. */
    internal fun timeDayWindowMatches(
        start: Long,
        end: Long,
        activeDays: List<Int>,
        signals: ContextSignals,
    ): Boolean {
        val activeToday = activeDays.isEmpty() || activeDays.contains(signals.dayOfWeek)
        return if (start <= end) {
            activeToday && signals.nowSecondsOfDay >= start && signals.nowSecondsOfDay <= end
        } else {
            val prevDay = if (signals.dayOfWeek == 1) 7 else signals.dayOfWeek - 1
            val activeYesterday = activeDays.isEmpty() || activeDays.contains(prevDay)
            val matchToday = activeToday && signals.nowSecondsOfDay >= start
            val matchYest = activeYesterday && signals.nowSecondsOfDay <= end
            matchToday || matchYest
        }
    }

    /** Battery trigger verdict (task43 L375-381). Unknown (negative) → no match. Guards D-108 start flash. */
    internal fun batteryMatches(batt: BatteryConstraint, signals: ContextSignals): Boolean {
        if (signals.batteryPercent < 0) return false
        if (batt.min != null && signals.batteryPercent < batt.min) return false
        if (batt.max != null && signals.batteryPercent > batt.max) return false
        if (batt.onPower != null && batt.onPower != signals.plugged) return false
        return true
    }

    internal fun locationMatches(loc: LocationConstraint, signals: ContextSignals): Boolean =
        distanceMeters(loc.lat, loc.lon, signals.lat, signals.lon) <= loc.radius

    internal fun wifiMatches(networks: List<String>, signals: ContextSignals): Boolean =
        networks.any { signals.wifi == it.trim() }

    /** Nearest future endpoint as "HH.MM" (task43 L459-475); null if no time range. */
    internal fun nextWakeTime(wakeTimes: List<Long>, nowSecs: Int): String? {
        var minDiff = Long.MAX_VALUE
        var nextWake = -1L
        for (timeValue in wakeTimes) {
            var diff = timeValue - nowSecs
            if (diff <= 0) diff += SECONDS_PER_DAY
            if (diff < minDiff) {
                minDiff = diff
                nextWake = timeValue
            }
        }
        if (nextWake == -1L) return null
        val h = nextWake / 3600
        val m = (nextWake % 3600) / 60
        return "%02d.%02d".format(h, m)
    }

    /** Great-circle (haversine) distance in metres. Agrees to metre-scale with Tasker ellipsoidal. */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
