package com.tideo.autobrightness.app.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

/**
 * DataStore serializer for the persisted display schedule rules ([DisplayRuleSet], D-150 —
 * `ContextRulesSerializer` pattern). App-private storage only; no Tasker-interop export exists
 * for this rule set (the feature has no Tasker source).
 */
object DisplayRulesSerializer : Serializer<DisplayRuleSet> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = false
    }

    override val defaultValue: DisplayRuleSet = DisplayRuleSet()

    override suspend fun readFrom(input: InputStream): DisplayRuleSet =
        runCatching {
            json.decodeFromString(DisplayRuleSet.serializer(), input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: DisplayRuleSet, output: OutputStream) {
        output.write(json.encodeToString(DisplayRuleSet.serializer(), t).encodeToByteArray())
    }
}
