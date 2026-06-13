package com.tideo.autobrightness.domain.context

/**
 * Pure domain mirror of one context-override rule (contexts_spec §2.3). The app-side storage model
 * (`ContextRule`/`ContextTriggers`, S8) maps onto this for resolution; domain stays Android-free.
 *
 * A present trigger field is an active constraint; null = "this dimension is not used by the rule".
 */
data class ContextRuleSpec(
    val id: String,
    val name: String,
    /** Profile file name to load when this rule wins. */
    val profile: String,
    /** Precedence integer — higher wins (D-014). Defaults to 0. */
    val priority: Int = 0,
    val apps: List<String>? = null,
    val wifi: List<String>? = null,
    val battery: BatteryConstraint? = null,
    val location: LocationConstraint? = null,
    val timeRange: TimeRange? = null,
    /** Calendar.DAY_OF_WEEK values 1=Sun..7=Sat; null/empty means all days. */
    val days: List<Int>? = null,
)

/** Battery trigger: `min ≤ batt ≤ max` and (onPower == plugged) when present (task43 L375-381). */
data class BatteryConstraint(
    val min: Int? = null,
    val max: Int? = null,
    val onPower: Boolean? = null,
)

/** Location trigger: within [radius] metres of (lat,lon) (task43 L383-390). */
data class LocationConstraint(
    val lat: Double,
    val lon: Double,
    val radius: Double,
)

/** Start/end tokens: "HH:MM" | "SUNRISE" | "SUNSET". start > end ⇒ overnight (task43 L314-354). */
data class TimeRange(
    val start: String,
    val end: String,
)

/**
 * The current environment snapshot the resolver matches against. All time-of-day values are LOCAL
 * seconds-of-day; the app layer is responsible for the UTC→local shift of solar times (task43
 * L77-80) and for reading Calendar.DAY_OF_WEEK.
 */
data class ContextSignals(
    val app: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val batteryPercent: Int = 0,
    val plugged: Boolean = false,
    /** Calendar.DAY_OF_WEEK 1=Sun..7=Sat. */
    val dayOfWeek: Int = 1,
    /** Local seconds since midnight. */
    val nowSecondsOfDay: Int = 0,
    val wifi: String = "",
    /** Local seconds-of-day for SUNRISE/SUNSET tokens (defaults 06:00/18:00 per task43 L67/72). */
    val sunriseLocalSecs: Long = 21_600L,
    val sunsetLocalSecs: Long = 64_800L,
)

/**
 * Resolution outcome.
 *
 * @param targetProfile the profile to apply, or **null** when a manual context override is active
 *   (PASS 4 skips the switch). A non-null value is always set otherwise (winner or baseline fallback).
 * @param activeContextName the winning rule's name (`%AAB_ActiveContext`), or null on no-match/override.
 * @param matchedRuleId the winning rule's id, or null.
 * @param nextContextTime nearest future endpoint as "HH.MM" for prof764 scheduling, or null.
 */
data class ContextResolution(
    val targetProfile: String?,
    val activeContextName: String?,
    val matchedRuleId: String?,
    val nextContextTime: String?,
)
