package com.tideo.autobrightness.domain.display

import com.tideo.autobrightness.domain.context.BatteryConstraint
import com.tideo.autobrightness.domain.context.LocationConstraint
import com.tideo.autobrightness.domain.context.TimeRange

/**
 * Display-rule scheduling model (Privileged Display Control, D-149 — rebuild-only feature, no
 * Tasker source). A rule says "turn [DisplayAction] ON while my triggers match"; releasing is
 * always "restore what was there before", decided by the app-side coordinator, never encoded in
 * the rule.
 *
 * Deliberately a SEPARATE list from `ContextRuleSpec`: context rules swap the whole brightness
 * profile with winner-takes-all precedence and a Tasker-interop `contexts.json`; display rules
 * are all-matching per-action toggles. Keeping the models apart leaves the golden context
 * resolver and its storage format untouched.
 */

/**
 * The secure-settings toggle a matching rule holds ON ([SecureDisplayController] key in
 * parentheses). Small on purpose — only actions with a clean binary engage/restore contract;
 * Extra Dim stays pipeline-owned (D-144) and is deliberately absent.
 */
enum class DisplayAction {
    /** Daltonizer grayscale (`accessibility_display_daltonizer[_enabled]`, mode 0). */
    GRAYSCALE,

    /** Night Light (`night_display_activated`). */
    NIGHT_LIGHT,

    /** Color inversion (`accessibility_display_inversion_enabled`). */
    INVERSION,
}

/**
 * One display rule. Trigger fields mirror `ContextRuleSpec` exactly (all six dimensions modelled;
 * the v1 editor exposes apps/time/days first) and evaluate with the SAME semantics via the shared
 * `ContextMatching` — a present field is an active constraint, null = "dimension unused".
 *
 * @param enabled disabled rules are fully inert: they neither match nor contribute wake-time
 *   boundaries (unlike context rules, display rules carry an enabled flag — an off switch must
 *   not keep scheduling wake-ups).
 */
data class DisplayRuleSpec(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val action: DisplayAction,
    val apps: List<String>? = null,
    val wifi: List<String>? = null,
    val battery: BatteryConstraint? = null,
    val location: LocationConstraint? = null,
    val timeRange: TimeRange? = null,
    /** Calendar.DAY_OF_WEEK values 1=Sun..7=Sat; null/empty means all days. */
    val days: List<Int>? = null,
)

/**
 * Per-action desired state after evaluating every enabled rule.
 *
 * Each action is `true` when at least one enabled matching rule wants it ON (all-matching,
 * per-action OR — no precedence between display rules), and **null when no matching rule holds an
 * opinion → the coordinator restores the pre-engage state**. `false` is never produced by v1
 * rules (a rule only asserts ON-while-matching); the type stays `Boolean?` so a future
 * hold-OFF action needs no output change.
 *
 * @param nextBoundary nearest future rule endpoint as "HH.MM" (same format as
 *   `ContextResolution.nextContextTime`, so the app side reuses `millisUntilNextContextWake`
 *   for self-scheduling), or null when no enabled rule carries a time range.
 */
data class DisplayResolution(
    val grayscale: Boolean? = null,
    val nightLight: Boolean? = null,
    val inversion: Boolean? = null,
    val nextBoundary: String? = null,
) {
    /** The desired state for [action] — lets the coordinator iterate `DisplayAction.entries`. */
    fun desired(action: DisplayAction): Boolean? = when (action) {
        DisplayAction.GRAYSCALE -> grayscale
        DisplayAction.NIGHT_LIGHT -> nightLight
        DisplayAction.INVERSION -> inversion
    }
}
