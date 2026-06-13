package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.context.BatteryConstraint
import com.tideo.autobrightness.domain.context.ContextRuleSpec
import com.tideo.autobrightness.domain.context.LocationConstraint
import com.tideo.autobrightness.domain.context.TimeRange

/**
 * Maps the app/storage [ContextRule] (S8, mirrors Tasker's contexts.json schema) onto the pure
 * domain [ContextRuleSpec] consumed by `ContextOverrideResolver`. Keeps Android-free domain code
 * decoupled from the serialization model.
 */
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

/**
 * The cheap signal-token pre-filter (`%AAB_ContextCache`, contexts_spec §2.1): which signal types
 * any configured rule uses. Drives the watcher gates so we only run the full evaluator when a
 * relevant signal type is actually in play.
 */
data class ContextSignalTokens(
    val usesBattery: Boolean,
    val usesLocation: Boolean,
    val usesWifi: Boolean,
    val usesApps: Boolean,
    val usesTime: Boolean,
    /** Deduplicated set of every app package referenced by any rule (task623 L93-103). */
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
