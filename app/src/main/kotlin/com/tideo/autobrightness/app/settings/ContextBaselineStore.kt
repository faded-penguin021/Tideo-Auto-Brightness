package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Pre-override baseline snapshot (D-170, task626): holds settings before first override for PASS 4 revert (contexts_spec).
 *  [userProfileName] is persisted %AAB_ProfileUser, the last manually-loaded profile (DA-018), separate from snapshot lifecycle. */
interface ContextBaselineStore {
    suspend fun snapshot(): AabSettings?
    suspend fun save(baseline: AabSettings)
    suspend fun clear()

    /** %AAB_ProfileUser: last manually-loaded profile (DA-018); independent of snapshot. */
    suspend fun userProfileName(): String

    /** Record %AAB_ProfileUser (DA-018). */
    suspend fun setUserProfileName(name: String)
}

/** On-disk wrapper for the [ContextBaselineStore] snapshot + the persisted `%AAB_ProfileUser` name. */
@Serializable
data class ContextBaseline(
    val schemaVersion: Int = SCHEMA_VERSION,
    val snapshot: AabSettings? = null,
    // DA-018: last manually-loaded profile name (%AAB_ProfileUser), no-match revert target; outlives snapshot (D-170, D-014(c)).
    val userProfileName: String = "Default",
) {
    companion object {
        // v2 (DA-018): added userProfileName; additive + ignoreUnknownKeys for v1 compat.
        const val SCHEMA_VERSION = 2
    }
}

object ContextBaselineSerializer : Serializer<ContextBaseline> {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override val defaultValue: ContextBaseline = ContextBaseline()

    override suspend fun readFrom(input: InputStream): ContextBaseline {
        // An unreadable file degrades to "no snapshot": the revert then keeps the live settings,
        // never guesses — the same fail-safe shape as AabSettingsSerializer.
        return runCatching {
            json.decodeFromString(ContextBaseline.serializer(), input.readBytes().decodeToString())
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: ContextBaseline, output: OutputStream) {
        output.write(json.encodeToString(ContextBaseline.serializer(), t).encodeToByteArray())
    }
}

/** The production [ContextBaselineStore]: a typed-JSON DataStore (`aab_context_baseline.json`). */
class DataStoreContextBaselineStore(
    private val store: DataStore<ContextBaseline>,
) : ContextBaselineStore {
    override suspend fun snapshot(): AabSettings? = store.data.first().snapshot
    // DA-018: `copy` (not a fresh ContextBaseline) so the snapshot save/clear preserves the persisted
    // %AAB_ProfileUser name — the name outlives the snapshot's baseline→override→revert lifecycle.
    override suspend fun save(baseline: AabSettings) {
        store.updateData { it.copy(snapshot = baseline) }
    }
    override suspend fun clear() {
        store.updateData { it.copy(snapshot = null) }
    }
    override suspend fun userProfileName(): String = store.data.first().userProfileName
    override suspend fun setUserProfileName(name: String) {
        store.updateData { it.copy(userProfileName = name) }
    }
}
