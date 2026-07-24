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
 *
 * [userProfileName] is the persisted NAME of `%AAB_ProfileUser` — the user's baseline profile, which
 * is the last profile the user loaded by hand (DA-018 / contexts_spec §4). It is the no-match revert
 * TARGET (`ContextOverrideResolver.userProfile`), so it must survive the snapshot's shorter lifecycle
 * (a snapshot `clear()` must NOT reset the name). Defaults `"Default"` (D-014(c)); a manual load
 * updates it via [setUserProfileName].
 */
interface ContextBaselineStore {
    suspend fun snapshot(): AabSettings?
    suspend fun save(baseline: AabSettings)
    suspend fun clear()

    /** `%AAB_ProfileUser` — the user's baseline profile name (the last manually-loaded profile). */
    suspend fun userProfileName(): String

    /** Record `%AAB_ProfileUser` (a manual profile load, DA-018). Independent of the snapshot. */
    suspend fun setUserProfileName(name: String)
}

/** On-disk wrapper for the [ContextBaselineStore] snapshot + the persisted `%AAB_ProfileUser` name. */
@Serializable
data class ContextBaseline(
    val schemaVersion: Int = SCHEMA_VERSION,
    val snapshot: AabSettings? = null,
    // DA-018: the last manually-loaded profile name (`%AAB_ProfileUser`), the no-match revert target.
    // Persisted alongside the snapshot (this record IS the "%AAB_ProfileUser revert file", D-170) but
    // outliving it — snapshot clears leave this untouched. Defaults "Default" (D-014(c)).
    val userProfileName: String = "Default",
) {
    companion object {
        // v2 (DA-018): added userProfileName. Additive + ignoreUnknownKeys → v1 files decode with the
        // "Default" default (no migration hook reads this constant).
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
