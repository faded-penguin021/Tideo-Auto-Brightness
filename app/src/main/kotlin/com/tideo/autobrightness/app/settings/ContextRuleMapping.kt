package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.context.BatteryConstraint
import com.tideo.autobrightness.domain.context.ContextRuleSpec
import com.tideo.autobrightness.domain.context.LocationConstraint
import com.tideo.autobrightness.domain.context.TimeRange

/** Map storage ContextRule onto domain ContextRuleSpec. */
fun ContextRule.toSpec(): ContextRuleSpec = ContextRuleSpec(
    id = id,
    name = name,
    profile = profile,
    priority = priority,
    apps = triggers.apps,
    wifi = triggers.wifi,
    battery = triggers.battery?.let { BatteryConstraint(min = it.min, max = it.max, onPower = it.onPower) },
    location = triggers.location?.let { LocationConstraint(lat = it.lat, lon = it.lon, radius = it.radius) },
    timeRange = triggers.timeRange?.takeIf { it.size >= 2 }?.let { TimeRange(start = it[0], end = it[1]) },
    days = triggers.days,
)

/** Order rules by priority (highest first), matching resolution precedence (G2R-F43, D-014). */
fun List<ContextRule>.byPriority(): List<ContextRule> =
    sortedWith(compareByDescending<ContextRule> { it.priority }.thenBy { it.name.lowercase() })

/** Signal-token pre-filter (%AAB_ContextCache): which signal types any rule uses. */
data class ContextSignalTokens(
    val usesBattery: Boolean,
    val usesLocation: Boolean,
    val usesWifi: Boolean,
    val usesApps: Boolean,
    val usesTime: Boolean,
    val appPackages: Set<String>,
) {
    companion object {
        fun from(rules: List<ContextRule>): ContextSignalTokens = ContextSignalTokens(
            usesBattery = rules.any { it.triggers.battery != null },
            usesLocation = rules.any { it.triggers.location != null },
            usesWifi = rules.any { !it.triggers.wifi.isNullOrEmpty() },
            usesApps = rules.any { !it.triggers.apps.isNullOrEmpty() },
            usesTime = rules.any { !it.triggers.timeRange.isNullOrEmpty() },
            appPackages = rules.flatMap { it.triggers.apps ?: emptyList() }.toSet(),
        )
    }
}
