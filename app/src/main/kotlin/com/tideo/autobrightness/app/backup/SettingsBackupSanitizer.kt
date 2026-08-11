package com.tideo.autobrightness.app.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** DB-002: strip runtime state (serviceEnabled, contextOverride) from restored aab_settings.json. */
object SettingsBackupSanitizer {

    /** Fields reset on restore, with the value they are reset to. */
    internal val RUNTIME_FIELD_RESETS: Map<String, JsonPrimitive> = mapOf(
        "serviceEnabled" to JsonPrimitive(false),
        "contextOverride" to JsonPrimitive(false),
    )

    private val json = Json { prettyPrint = true }

    /** Sanitize runtime fields to safe values; return null if not a JSON object. */
    fun sanitize(rawSettingsJson: String): String? {
        val root = runCatching { json.parseToJsonElement(rawSettingsJson).jsonObject }.getOrNull() ?: return null
        val sanitized = buildMap {
            putAll(root)
            // Always write reset; missing keys use default (serviceEnabled defaults true).
            putAll(RUNTIME_FIELD_RESETS)
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(sanitized))
    }
}
