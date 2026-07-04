package com.tideo.autobrightness.domain.context

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared per-dimension trigger matching, extracted verbatim from [ContextOverrideResolver] (at
 * the time for the D-150 display-rules resolver, since removed by D-151; the extraction stays —
 * it is where these semantics live now). Extraction is behavior-preserving — the golden
 * `ContextOverrideResolverTest` matrix is the proof; the Tasker provenance below moved here with
 * the code.
 *
 * Tasker: task43 `_EvaluateContexts V2` Java L12093 (`extraction/java/task43_1_evaluatecontexts-v2.java`),
 * semantics in `extraction/contexts_spec.md` §4.
 *
 * What is deliberately NOT here: precedence (priority/specificity/array-order — override-resolver
 * policy) — each caller owns its own merge policy on top of these single-dimension verdicts.
 */
internal object ContextMatching {

    /** Mean Earth radius (m) for the great-circle distance used by the location trigger. */
    private const val EARTH_RADIUS_M = 6_371_000.0
    internal const val SECONDS_PER_DAY = 86_400L

    /** Resolve a time token ("HH:MM" | "SUNRISE" | "SUNSET") to local seconds-of-day. */
    internal fun resolveTimeToken(token: String, signals: ContextSignals): Long = when (token) {
        "SUNRISE" -> signals.sunriseLocalSecs
        "SUNSET" -> signals.sunsetLocalSecs
        else -> {
            val parts = token.split(":")
            parts[0].trim().toLong() * 3600 + parts[1].trim().toLong() * 60
        }
    }

    /**
     * Time-of-day + day-of-week window verdict for an already-resolved [start]/[end] (task43
     * L314-354). [activeDays] empty means "all days"; `start > end` is an overnight range, where
     * the post-midnight tail belongs to YESTERDAY's day membership.
     */
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
            // Overnight range: the post-midnight tail belongs to YESTERDAY's membership.
            val prevDay = if (signals.dayOfWeek == 1) 7 else signals.dayOfWeek - 1
            val activeYesterday = activeDays.isEmpty() || activeDays.contains(prevDay)
            val matchToday = activeToday && signals.nowSecondsOfDay >= start
            val matchYest = activeYesterday && signals.nowSecondsOfDay <= end
            matchToday || matchYest
        }
    }

    /**
     * Battery trigger verdict (task43 L375-381). Battery unknown (no reading yet, negative
     * sentinel) → a battery condition cannot be asserted, so the rule does not match. Guards the
     * service-start flash where the placeholder 0% would satisfy a "battery <= max" saver rule
     * before the first real reading arrives (D-108): without this, an unplugged max-only rule
     * would falsely match at percent 0.
     */
    internal fun batteryMatches(batt: BatteryConstraint, signals: ContextSignals): Boolean {
        if (signals.batteryPercent < 0) return false
        if (batt.min != null && signals.batteryPercent < batt.min) return false
        if (batt.max != null && signals.batteryPercent > batt.max) return false
        if (batt.onPower != null && batt.onPower != signals.plugged) return false
        return true
    }

    /** Location trigger verdict: within [LocationConstraint.radius] metres (task43 L383-390). */
    internal fun locationMatches(loc: LocationConstraint, signals: ContextSignals): Boolean =
        distanceMeters(loc.lat, loc.lon, signals.lat, signals.lon) <= loc.radius

    /** Wi-Fi trigger verdict: current SSID equals any listed network, entries trimmed. */
    internal fun wifiMatches(networks: List<String>, signals: ContextSignals): Boolean =
        networks.any { signals.wifi == it.trim() }

    /**
     * Nearest future endpoint as "HH.MM" (drives prof764's self-scheduling Time context), or null
     * when no rule carries a time range. task43 L459-475: a non-positive diff wraps to tomorrow.
     */
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

    /**
     * Great-circle (haversine) distance in metres. Tasker used `android.location.Location
     * .distanceBetween` (ellipsoidal); haversine agrees to well within the metre-scale, user-set
     * radius this gate compares against (documented approximation — only the inside/outside verdict
     * matters, never the exact metres).
     */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
