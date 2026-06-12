package com.tideo.autobrightness.app.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Storage model for the context-override rule system.
 *
 * Data classes mirror the exact JSON schema used by Tasker task623 `_ContextManager` and
 * task43 `_EvaluateContexts V2` for `contexts.json` interoperability. Engine logic is S10.
 *
 * Disk format: JSON array of [ContextRule] at Download/AAB/configs/contexts.json (Tasker interop).
 * Rebuild storage: DataStore or app-private JSON (path decided by S10).
 *
 * Tasker: contexts_spec.md §2.3 — schema verified against task43 PASS 3 reader + task623 writer.
 */
@Serializable
data class ContextRule(
    /** Stable unique identifier for upsert/delete. */
    val id: String,
    /** Display name and log label. */
    val name: String,
    /** Profile file name to load (configs/<profile>.json) when this rule wins. */
    val profile: String,
    /** Precedence integer — higher wins; ties broken by specificity then array order (D-014). */
    val priority: Int = 0,
    val triggers: ContextTriggers = ContextTriggers(),
)

@Serializable
data class ContextTriggers(
    /** Foreground app package names. Present if ≥1 app rule; null omitted from JSON. */
    val apps: List<String>? = null,
    /** Wi-Fi SSID names (trimmed-compare). */
    val wifi: List<String>? = null,
    /** Battery state constraints. */
    val battery: BatteryTrigger? = null,
    /** GPS/network location radius. */
    val location: LocationTrigger? = null,
    /** [start, end] as "HH:MM" | "SUNRISE" | "SUNSET"; supports overnight ranges (start > end). */
    @SerialName("time_range") val timeRange: List<String>? = null,
    /** Calendar.DAY_OF_WEEK values 1=Sun..7=Sat; null means all days. */
    val days: List<Int>? = null,
) {
    /** Number of trigger dimensions present — used for tie-breaking (D-014 specificity rule). */
    fun specificity(): Int =
        listOfNotNull(timeRange, if (days != null) days else null, apps, battery, location, wifi).size
}

@Serializable
data class BatteryTrigger(
    val min: Int = 0,
    val max: Int = 100,
    /** null = any power state; true = must be on power; false = must be off power. */
    @SerialName("on_power") val onPower: Boolean? = null,
)

@Serializable
data class LocationTrigger(
    val lat: Double,
    val lon: Double,
    /** Radius in metres. */
    val radius: Double,
)

/**
 * Top-level wrapper persisted to DataStore / exported as JSON array.
 * Use [ContextOverrideConfig.toJson] / [ContextOverrideConfig.fromJson] for serialization.
 */
@Serializable
data class ContextOverrideConfig(
    val rules: List<ContextRule> = emptyList(),
) {
    fun toJson(): String = contextJson.encodeToString(serializer(), this)

    /** Compact array format for Tasker %AAB_ContextJSONCache interop. */
    fun toTaskerJson(): String = contextJson.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ContextRule.serializer()),
        rules,
    )

    companion object {
        fun fromJson(json: String): ContextOverrideConfig = runCatching {
            contextJson.decodeFromString(serializer(), json)
        }.getOrElse {
            // Fallback: try parsing as Tasker's bare JSON array format
            runCatching {
                ContextOverrideConfig(
                    rules = contextJson.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(ContextRule.serializer()),
                        json,
                    ),
                )
            }.getOrDefault(ContextOverrideConfig())
        }
    }
}

private val contextJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false  // omit null/default optional trigger fields
    prettyPrint = false
}
