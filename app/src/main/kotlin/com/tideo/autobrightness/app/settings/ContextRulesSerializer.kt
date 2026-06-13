package com.tideo.autobrightness.app.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

/**
 * DataStore serializer for the persisted context-rule set ([ContextOverrideConfig], S8 storage model).
 *
 * This is the rebuild's source of truth for context rules (replacing Tasker's
 * `Download/AAB/configs/contexts.json` + dual RAM caches — contexts_spec §2). Export to the legacy
 * Tasker JSON-array format is available via [ContextOverrideConfig.toTaskerJson].
 */
object ContextRulesSerializer : Serializer<ContextOverrideConfig> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = false
    }

    override val defaultValue: ContextOverrideConfig = ContextOverrideConfig()

    override suspend fun readFrom(input: InputStream): ContextOverrideConfig =
        runCatching {
            json.decodeFromString(ContextOverrideConfig.serializer(), input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: ContextOverrideConfig, output: OutputStream) {
        output.write(json.encodeToString(ContextOverrideConfig.serializer(), t).encodeToByteArray())
    }
}
