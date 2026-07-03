package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.context.BatteryConstraint
import com.tideo.autobrightness.domain.context.LocationConstraint
import com.tideo.autobrightness.domain.context.TimeRange
import com.tideo.autobrightness.domain.display.DisplayAction
import com.tideo.autobrightness.domain.display.DisplayRuleSpec
import kotlinx.serialization.Serializable

/**
 * Storage model for the Privileged Display schedule rules (D-149/D-150, rebuild-only feature —
 * `plans/privileged-display.md` Segment 4). Deliberately a SEPARATE store from the context rules
 * and OUTSIDE `AabSettings` (no migration/import-export coupling — future work): display rules
 * are all-matching per-action toggles, context rules are winner-takes-all profile swaps with a
 * Tasker-interop JSON format that must stay untouched.
 *
 * Triggers reuse the [ContextTriggers] schema (all six dimensions modelled; the v1 editor exposes
 * apps/time/days) so the two rule systems stay field-compatible without sharing a store.
 */
@Serializable
data class DisplayRule(
    /** Stable unique identifier for upsert/delete. */
    val id: String,
    val name: String,
    /** Disabled rules are fully inert — no match, no wake-time scheduling (resolver contract). */
    val enabled: Boolean = true,
    /** [DisplayAction] enum NAME, stored as a string: an unknown value (e.g. written by a newer
     *  schema) makes just that rule inert instead of failing the whole file's decode. */
    val action: String,
    val triggers: ContextTriggers = ContextTriggers(),
)

/** Top-level wrapper persisted to the `aab_display_rules.json` DataStore. */
@Serializable
data class DisplayRuleSet(
    val rules: List<DisplayRule> = emptyList(),
)

/**
 * Maps the storage [DisplayRule] onto the pure domain [DisplayRuleSpec] consumed by
 * `DisplayRulesResolver` (the [ContextRule.toSpec] pattern). Returns null for an unrecognized
 * action name — callers `mapNotNull`, so the rule is simply inert.
 */
fun DisplayRule.toSpec(): DisplayRuleSpec? {
    val parsedAction = DisplayAction.entries.firstOrNull { it.name == action } ?: return null
    return DisplayRuleSpec(
        id = id,
        name = name,
        enabled = enabled,
        action = parsedAction,
        apps = triggers.apps,
        wifi = triggers.wifi,
        battery = triggers.battery?.let { BatteryConstraint(min = it.min, max = it.max, onPower = it.onPower) },
        location = triggers.location?.let { LocationConstraint(lat = it.lat, lon = it.lon, radius = it.radius) },
        timeRange = triggers.timeRange?.takeIf { it.size >= 2 }?.let { TimeRange(start = it[0], end = it[1]) },
        days = triggers.days,
    )
}
