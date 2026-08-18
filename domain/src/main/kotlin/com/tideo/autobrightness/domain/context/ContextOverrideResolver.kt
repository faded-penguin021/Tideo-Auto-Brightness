package com.tideo.autobrightness.domain.context

/** Pure decision engine for context-override system (Kotlin port of task43 _EvaluateContexts V2 PASS 3/4).
 * Precedence: D-014 priority > specificity > array order. D-108/D-014/contexts_spec §4 semantics.
 * PASS 1/2 stateful scheduling live in ContextEngine; per-dimension verdicts live in ContextMatching. */
object ContextOverrideResolver {

    /** @param rules ordered list (array order is final tie-break). @param signals current environment snapshot.
     * @param overrideActive manual context lock (%AAB_ContextOverride); skips profile switch but computes wake times.
     * @param userProfile baseline profile (%AAB_ProfileUser); no-match fallback. */
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

                // wakeTimes collects all endpoints before match check (task43 L341-342)
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

        // PASS 4: manual lock active; skip switch (only wake times)
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

        // No match; fall back to user's baseline or Default if missing
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
