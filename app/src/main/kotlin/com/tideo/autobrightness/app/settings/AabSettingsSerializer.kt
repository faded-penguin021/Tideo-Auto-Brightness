package com.tideo.autobrightness.app.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object AabSettingsSerializer : Serializer<AabSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override val defaultValue: AabSettings = AabSettings()

    override suspend fun readFrom(input: InputStream): AabSettings {
        return runCatching {
            val stored = json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
            val raw = json.decodeFromJsonElement(AabSettings.serializer(), upgradeJson(stored))
            require(raw.schemaVersion in 1..CURRENT_SCHEMA_VERSION)
            migrate(raw).validate()
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: AabSettings, output: OutputStream) {
        output.write(json.encodeToString(AabSettings.serializer(), t).encodeToByteArray())
    }

    // DD-030: a writer before DD-030 omitted every default-valued key, so an absent one meant v3's default.
    private val V3_ANIMATION_DEFAULTS: Map<String, JsonPrimitive> = mapOf(
        "animSteps" to JsonPrimitive(20),
        "minWaitMs" to JsonPrimitive(25),
        "maxWaitMs" to JsonPrimitive(65),
        "throttleDefaultMs" to JsonPrimitive(1310),
    )

    internal fun upgradeJson(stored: JsonObject): JsonObject =
        if (V3_ANIMATION_DEFAULTS.keys.none { it in stored }) stored else JsonObject(V3_ANIMATION_DEFAULTS + stored)

    // v1→v2 added animSteps/thresholdMidpoint/contextOverride/setupTitle; v2→v3 dropped thresholdDynamic (G2R-F85).
    internal fun migrate(settings: AabSettings): AabSettings {
        if (settings.schemaVersion >= CURRENT_SCHEMA_VERSION) return settings
        var s = settings
        if (s.schemaVersion < 2) s = s.copy(schemaVersion = 2)
        if (s.schemaVersion < 3) s = s.copy(schemaVersion = 3)
        return s
    }
}
