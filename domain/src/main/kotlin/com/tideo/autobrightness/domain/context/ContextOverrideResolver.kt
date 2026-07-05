package com.tideo.autobrightness.domain.context

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
 *  - The per-dimension trigger verdicts (time/day window incl. the overnight prev-day rule, time
 *    tokens, battery incl. the D-108 unknown sentinel, location haversine, wifi trim-compare,
 *    next-wake-time) live in [ContextMatching], shared with the display-rules resolver. This
 *    resolver keeps the precedence policy and the wake-time collection order.
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
                val start = ContextMatching.resolveTimeToken(range.start, signals)
                val end = ContextMatching.resolveTimeToken(range.end, signals)

                // wakeTimes collects EVERY rule's endpoints (before the match check) — task43 L341-342.
                wakeTimes.add(start)
                wakeTimes.add(end)

                timeDayMatch = ContextMatching.timeDayWindowMatches(start, end, activeDays, signals)
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
                if (!ContextMatching.batteryMatches(rule.battery, signals)) isMatch = false
                specificity++
            }

            if (isMatch && rule.location != null) {
                if (!ContextMatching.locationMatches(rule.location, signals)) isMatch = false
                specificity++
            }

            if (isMatch && rule.wifi != null) {
                if (!ContextMatching.wifiMatches(rule.wifi, signals)) isMatch = false
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

        val nextContextTime = ContextMatching.nextWakeTime(wakeTimes, signals.nowSecondsOfDay)

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
}
