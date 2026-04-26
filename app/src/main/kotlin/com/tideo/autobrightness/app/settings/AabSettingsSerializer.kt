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
            json.decodeFromString(AabSettings.serializer(), input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: AabSettings, output: OutputStream) {
        output.write(json.encodeToString(AabSettings.serializer(), t).encodeToByteArray())
    }
}
