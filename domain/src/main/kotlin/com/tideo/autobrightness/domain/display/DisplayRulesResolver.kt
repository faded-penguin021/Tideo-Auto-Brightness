package com.tideo.autobrightness.domain.display

import com.tideo.autobrightness.domain.context.ContextMatching
import com.tideo.autobrightness.domain.context.ContextSignals

/**
 * Pure resolver for display schedule rules (Privileged Display Control, D-149). Evaluates every
 * enabled [DisplayRuleSpec] against the current [ContextSignals] snapshot with the SAME
 * per-dimension trigger semantics as the context-override system (shared [ContextMatching] —
 * incl. the overnight prev-day rule, SUNRISE/SUNSET tokens, the D-108 unknown-battery sentinel,
 * haversine location, trimmed wifi compare).
 *
 * Merge policy — deliberately DIFFERENT from [ContextOverrideResolver]'s winner-takes-all:
 * **all matching enabled rules apply, per-action OR.** Two matching rules with different actions
 * both engage; a second matching rule with the same action changes nothing. There is no
 * priority/specificity between display rules because actions are independent booleans, not
 * competing profiles.
 *
 * Wake-time boundaries: every ENABLED rule's time endpoints schedule (matching or not — a
 * non-matching window's start IS the next engage boundary); disabled rules are fully inert.
 * Context rules collect endpoints from all rules (task43 fidelity), but they have no enabled
 * flag — same spirit: every rule that can ever match, schedules.
 */
object DisplayRulesResolver {

    fun resolve(rules: List<DisplayRuleSpec>, signals: ContextSignals): DisplayResolution {
        val wakeTimes = ArrayList<Long>()
        val desired = HashSet<DisplayAction>()

        for (rule in rules) {
            if (!rule.enabled) continue

            var isMatch = true
            val activeDays: List<Int> = rule.days ?: emptyList()

            if (rule.timeRange != null) {
                val start = ContextMatching.resolveTimeToken(rule.timeRange.start, signals)
                val end = ContextMatching.resolveTimeToken(rule.timeRange.end, signals)
                wakeTimes.add(start)
                wakeTimes.add(end)
                if (!ContextMatching.timeDayWindowMatches(start, end, activeDays, signals)) {
                    isMatch = false
                }
            } else if (rule.days != null) {
                if (!activeDays.contains(signals.dayOfWeek)) isMatch = false
            }

            if (isMatch && rule.apps != null && !rule.apps.contains(signals.app)) isMatch = false
            if (isMatch && rule.battery != null &&
                !ContextMatching.batteryMatches(rule.battery, signals)
            ) {
                isMatch = false
            }
            if (isMatch && rule.location != null &&
                !ContextMatching.locationMatches(rule.location, signals)
            ) {
                isMatch = false
            }
            if (isMatch && rule.wifi != null && !ContextMatching.wifiMatches(rule.wifi, signals)) {
                isMatch = false
            }

            if (isMatch) desired.add(rule.action)
        }

        return DisplayResolution(
            grayscale = if (DisplayAction.GRAYSCALE in desired) true else null,
            nightLight = if (DisplayAction.NIGHT_LIGHT in desired) true else null,
            inversion = if (DisplayAction.INVERSION in desired) true else null,
            nextBoundary = ContextMatching.nextWakeTime(wakeTimes, signals.nowSecondsOfDay),
        )
    }
}
