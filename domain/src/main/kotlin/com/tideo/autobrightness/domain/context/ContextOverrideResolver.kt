package com.tideo.autobrightness.domain.context

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure decision engine for the context-override system — the Kotlin port of task43
 * `_EvaluateContexts V2` PASS 3 (match + rank) and PASS 4 (output + next wake time).
 *
 * Tasker: task43 `_EvaluateContexts V2` Java L12093 (`extraction/java/task43_1_evaluatecontexts-v2.java`),
 * semantics in `extraction/contexts_spec.md` §4.
 *
 * What is and isn't here:
 *  - PASS 1 (per-caller cooldown debounce) and PASS 2 (signal-change veto gates) are stateful,
 *    clock- and persisted-state-driven scheduling concerns → they live in the app-side ContextEngine.
 *  - PASS 3/4 are the precedence/merge matrix and are pure → here, with a 1:1 unit-test matrix.
 *
 * Precedence (contexts_spec §4, D-014): among matching rules, highest [ContextRuleSpec.priority]
 * wins; ties broken by higher specificity (# of trigger dimensions present on the match path);
 * remaining ties keep array order (first seen). `priority` defaults to 0.
 *
 * An override **swaps the entire active profile** (contexts_spec §4 "What an override actually
 * CHANGES") — it is not a scale/min/max modifier. The app layer loads the winning profile's full
 * parameter set; this resolver only names the winner.
 */
object ContextOverrideResolver {

    /** Mean Earth radius (m) for the great-circle distance used by the location trigger. */
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val SECONDS_PER_DAY = 86_400L

    /**
     * @param rules ordered rule list (array order is the final tie-break, faithful to contexts.json).
     * @param signals the current environment snapshot (already resolved to LOCAL seconds-of-day etc.).
     * @param overrideActive `%AAB_ContextOverride == "true"` — a manual context lock. When set, the
     *   profile switch is skipped entirely (PASS 4 else branch) but wake times are still computed.
     * @param userProfile `%AAB_ProfileUser` — the user's baseline profile name (no-match fallback).
     * @param profileExists existence probe for the fallback profile file (act 433-437); when the
     *   user's saved profile is gone the fallback collapses to "Default".
     */
    fun resolve(
        rules: List<ContextRuleSpec>,
        signals: ContextSignals,
        overrideActive: Boolean = false,
        userProfile: String = "Default",
        profileExists: (String) -> Boolean = { true },
    ): ContextResolution {
        var winner: ContextRuleSpec? = null
        var highestPriority = -1
        var highestSpecificity = -1
        val wakeTimes = ArrayList<Long>()

        for (rule in rules) {
            var isMatch = true
            var specificity = 0

            val hasDays = rule.days != null
            val activeDays: List<Int> = rule.days ?: emptyList()

            val hasTime = rule.timeRange != null
            var timeDayMatch = true

            if (hasTime) {
                val range = rule.timeRange!!
                val start = resolveTimeToken(range.start, signals)
                val end = resolveTimeToken(range.end, signals)

                // wakeTimes collects EVERY rule's endpoints (before the match check) — task43 L341-342.
                wakeTimes.add(start)
                wakeTimes.add(end)

                val activeToday = activeDays.isEmpty() || activeDays.contains(signals.dayOfWeek)
                if (start <= end) {
                    if (!activeToday || signals.nowSecondsOfDay < start || signals.nowSecondsOfDay > end) {
                        timeDayMatch = false
                    }
                } else {
                    // Overnight range: the post-midnight tail belongs to YESTERDAY's membership.
                    val prevDay = if (signals.dayOfWeek == 1) 7 else signals.dayOfWeek - 1
                    val activeYesterday = activeDays.isEmpty() || activeDays.contains(prevDay)
                    val matchToday = activeToday && signals.nowSecondsOfDay >= start
                    val matchYest = activeYesterday && signals.nowSecondsOfDay <= end
                    if (!matchToday && !matchYest) timeDayMatch = false
                }
                specificity++
                if (hasDays) specificity++
            } else if (hasDays) {
                if (!activeDays.contains(signals.dayOfWeek)) timeDayMatch = false
                specificity++
            }

            if (!timeDayMatch) isMatch = false

            if (isMatch && rule.apps != null) {
                if (!rule.apps.contains(signals.app)) isMatch = false
                specificity++
            }

            if (isMatch && rule.battery != null) {
                val batt = rule.battery
                // Battery unknown (no reading yet) → a battery condition cannot be asserted, so the
                // rule does not match. Guards the service-start flash where the placeholder 0% would
                // satisfy a "battery <= max" saver rule before the first real reading arrives
                // (D-108): without this, an unplugged max-only rule would falsely match at percent 0.
                if (signals.batteryPercent < 0) isMatch = false
                if (isMatch && batt.min != null && signals.batteryPercent < batt.min) isMatch = false
                if (isMatch && batt.max != null && signals.batteryPercent > batt.max) isMatch = false
                if (isMatch && batt.onPower != null && batt.onPower != signals.plugged) isMatch = false
                specificity++
            }

            if (isMatch && rule.location != null) {
                val loc = rule.location
                val dist = distanceMeters(loc.lat, loc.lon, signals.lat, signals.lon)
                if (dist > loc.radius) isMatch = false
                specificity++
            }

            if (isMatch && rule.wifi != null) {
                if (rule.wifi.none { signals.wifi == it.trim() }) isMatch = false
                specificity++
            }

            if (isMatch) {
                val priority = rule.priority
                val newWinner = priority > highestPriority ||
                    (priority == highestPriority && specificity > highestSpecificity)
                if (newWinner) {
                    highestPriority = priority
                    highestSpecificity = specificity
                    winner = rule
                }
            }
        }

        val nextContextTime = nextWakeTime(wakeTimes, signals.nowSecondsOfDay)

        // PASS 4: when a manual context lock is active, skip the switch (only wake times refresh).
        if (overrideActive) {
            return ContextResolution(
                targetProfile = null,
                activeContextName = null,
                matchedRuleId = null,
                nextContextTime = nextContextTime,
            )
        }

        if (winner != null) {
            return ContextResolution(
                targetProfile = winner.profile,
                activeContextName = winner.name,
                matchedRuleId = winner.id,
                nextContextTime = nextContextTime,
            )
        }

        // No match → fall back to the user's baseline profile; collapse to Default if it is gone.
        val fallback = userProfile.ifEmpty { "Default" }
        val target = if (profileExists(fallback)) fallback else "Default"
        return ContextResolution(
            targetProfile = target,
            activeContextName = null,
            matchedRuleId = null,
            nextContextTime = nextContextTime,
        )
    }

    /** Resolve a time token ("HH:MM" | "SUNRISE" | "SUNSET") to local seconds-of-day. */
    private fun resolveTimeToken(token: String, signals: ContextSignals): Long = when (token) {
        "SUNRISE" -> signals.sunriseLocalSecs
        "SUNSET" -> signals.sunsetLocalSecs
        else -> {
            val parts = token.split(":")
            parts[0].trim().toLong() * 3600 + parts[1].trim().toLong() * 60
        }
    }

    /**
     * Nearest future endpoint as "HH.MM" (drives prof764's self-scheduling Time context), or null
     * when no rule carries a time range. task43 L459-475: a non-positive diff wraps to tomorrow.
     */
    private fun nextWakeTime(wakeTimes: List<Long>, nowSecs: Int): String? {
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
