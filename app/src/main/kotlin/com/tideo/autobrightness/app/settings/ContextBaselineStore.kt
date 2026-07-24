package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persisted pre-override baseline snapshot (D-170 — Tasker task626 `_ContextResume` / the
 * `%AAB_ProfileUser` revert file). Context-rule profile loads WRITE THROUGH to the live settings
 * DataStore (Tasker parity: `_ProfileManager LOAD_FILE` repopulates the live `%AAB_*` variables, so
 * every screen shows the loaded values); this store holds the settings the user was running before
 * the first override, so the PASS 4 no-match revert (contexts_spec §4) can restore them.
 *
 * `snapshot == null` = no override in flight (the live settings ARE the baseline). The snapshot is
 * taken once, on the baseline→override transition; rule→rule switches keep it. It is cleared when
 * the revert restores it — and by any manual "these settings are now authoritative" moment (manual
 * profile load / Resume via [ProfileApplier]), mirroring task626 re-snapshotting the live var set.
 */
interface ContextBaselineStore {
    suspend fun snapshot(): AabSettings?
    suspend fun save(baseline: AabSettings)
    suspend fun clear()
}

/** On-disk wrapper for the [ContextBaselineStore] snapshot. */
@Serializable
data class ContextBaseline(
    val schemaVersion: Int = SCHEMA_VERSION,
    val snapshot: AabSettings? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
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
    override suspend fun save(baseline: AabSettings) {
        store.updateData { ContextBaseline(snapshot = baseline) }
    }
    override suspend fun clear() {
        store.updateData { ContextBaseline(snapshot = null) }
    }
}
