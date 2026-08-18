package com.tideo.autobrightness.app.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

object AabSettingsSerializer : Serializer<AabSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override val defaultValue: AabSettings = AabSettings()

    override suspend fun readFrom(input: InputStream): AabSettings {
        return runCatching {
            val raw = json.decodeFromString(AabSettings.serializer(), input.readBytes().decodeToString())
            require(raw.schemaVersion in 1..CURRENT_SCHEMA_VERSION)
            migrate(raw).validate()
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: AabSettings, output: OutputStream) {
        output.write(json.encodeToString(AabSettings.serializer(), t).encodeToByteArray())
    }

    // v1→v2: animSteps, thresholdMidpoint, contextOverride, setupTitle added; scale Int→Float.
    // v2→v3 (G2R-F85): dropped thresholdDynamic; ignoreUnknownKeys handles it.
    internal fun migrate(settings: AabSettings): AabSettings {
        if (settings.schemaVersion >= CURRENT_SCHEMA_VERSION) return settings
        var s = settings
        if (s.schemaVersion < 2) s = s.copy(schemaVersion = 2)
        if (s.schemaVersion < 3) s = s.copy(schemaVersion = 3)
        return s
    }
}
