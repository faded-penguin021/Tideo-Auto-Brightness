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
            migrate(raw)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: AabSettings, output: OutputStream) {
        output.write(json.encodeToString(AabSettings.serializer(), t).encodeToByteArray())
    }

    // v1→v2: added animSteps(20), thresholdMidpoint(4.0), contextOverride(true), setupTitle;
    // scale type Int→Float (transparent — JSON integers decode as Float without loss).
    // New fields absent from v1 JSON get Kotlin default values automatically via ignoreUnknownKeys.
    internal fun migrate(settings: AabSettings): AabSettings {
        if (settings.schemaVersion >= CURRENT_SCHEMA_VERSION) return settings
        var s = settings
        if (s.schemaVersion < 2) s = s.copy(schemaVersion = 2)
        return s
    }
}
