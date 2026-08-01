package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * One saved, named brightness profile (`configs/<name>.json` in Tasker). [builtIn] marks the five
 * seeded [DefaultProfiles] (task592) so "Restore factory profiles" can re-seed them while leaving
 * user-created entries alone. Per owner-decision 3 (S12.6d, G2R-F15) ALL profiles are editable saved
 * entries — built-ins may be overwritten.
 */
@Serializable
data class SavedProfile(
    val name: String,
    val settings: AabSettings,
    val builtIn: Boolean = false,
)

/** The persisted profile set; [seeded] guards the one-time built-in seeding. */
@Serializable
data class SavedProfiles(
    val profiles: List<SavedProfile> = emptyList(),
    val seeded: Boolean = false,
) {
    companion object {
        /** On-disk schema version (S12.9c #5; datastore_map.md). v1 = initial; bump on a breaking change. */
        const val SCHEMA_VERSION = 1
    }
}

/** DataStore serializer for the saved profile set (mirrors [OverridePointsSerializer]). */
object SavedProfilesSerializer : Serializer<SavedProfiles> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * DB-004: allocation ceiling for the persisted profile set, derived from the limits that already
     * exist — [UserProfileStore.MAX_PROFILES] profiles, each a pretty-printed settings object of a
     * few KiB — with generous slack. Not a schema constraint: raise it if the settings object grows.
     */
    internal const val MAX_ENCODED_PROFILES_BYTES = 4 * 1024 * 1024

    override val defaultValue: SavedProfiles = SavedProfiles()

    /**
     * DB-004: bound the READ before parsing it.
     *
     * The profile-count and name-length limits below are applied to the *decoded object*, so
     * `readBytes()` had already materialised the entire file — and the count limit implies a size
     * limit anyway (128 profiles of a bounded settings object is well under a megabyte). This store
     * is app-private, so the input is corrupt state rather than an attacker's, but "corrupt state
     * cannot make us allocate without limit" is cheap to hold and the alternative is an OOM on a
     * path whose whole job is recovering from bad data.
     */
    override suspend fun readFrom(input: InputStream): SavedProfiles =
        runCatching {
            // readNBytes is API 33; minSdk is 31, so read the bound by hand (+1 probe byte, which is
            // what distinguishes "exactly at the cap" from "truncated at the cap").
            val raw = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = MAX_ENCODED_PROFILES_BYTES + 1
            while (remaining > 0) {
                val count = input.read(chunk, 0, minOf(chunk.size, remaining))
                if (count < 0) break
                raw.write(chunk, 0, count)
                remaining -= count
            }
            require(raw.size() <= MAX_ENCODED_PROFILES_BYTES) { "Saved profiles file is implausibly large" }
            val decoded = json.decodeFromString(SavedProfiles.serializer(), raw.toByteArray().decodeToString())
            require(decoded.profiles.size <= UserProfileStore.MAX_PROFILES)
            decoded.copy(
                profiles = decoded.profiles.map {
                    require(it.name.isNotBlank() && it.name != "." && it.name != ".." &&
                        it.name.length <= UserProfileStore.MAX_PROFILE_NAME_CHARS)
                    it.copy(settings = it.settings.validate())
                }.distinctBy { it.name },
            )
        }.getOrDefault(defaultValue)

    override suspend fun writeTo(t: SavedProfiles, output: OutputStream) {
        output.write(json.encodeToString(SavedProfiles.serializer(), t).encodeToByteArray())
    }
}

/**
 * Persistence + CRUD for user-editable named profiles (S12.6d, G2R-F15; owner-decision 3). The five
 * [DefaultProfiles] are seeded once (then overwritable); "Restore factory profiles" re-seeds them.
 * [AppProfileCatalog] reads this store so context rules can target user profiles too (closes the
 * D-042c "unknown rule.profile → null" gap).
 *
 * The global preferences (serviceEnabled / contextOverride / detectOverrides / debugLevel) are NOT a
 * profile's concern — they are preserved by the apply path (SettingsViewModel.applyProfile), never by
 * what gets stored here; a profile snapshot is the tunable parameter set only.
 */
class UserProfileStore(private val dataStore: DataStore<SavedProfiles>) {

    companion object {
        internal const val MAX_PROFILES = 128
        internal const val MAX_PROFILE_NAME_CHARS = 96
    }

    /** The saved profiles in display order (built-ins first), seeding lazily on collect. */
    fun profilesFlow(): Flow<List<SavedProfile>> = dataStore.data.map { seedIfNeeded(it).profiles }

    /** Snapshot of the saved profiles (ensures the built-ins are seeded first). */
    suspend fun profiles(): List<SavedProfile> {
        ensureSeeded()
        return dataStore.data.first().profiles
    }

    suspend fun names(): List<String> = profiles().map { it.name }

    /** Resolve a profile NAME to its parameter set, or null if unknown (catalog fallback handles that). */
    suspend fun get(name: String): AabSettings? = profiles().firstOrNull { it.name == name }?.settings?.validate()

    /** Seed the five built-ins exactly once. Idempotent after the first call. */
    suspend fun ensureSeeded() {
        dataStore.updateData { current -> seedIfNeeded(current) }
    }

    /**
     * Save (create or overwrite) a named profile. Overwrite keeps the entry's list position + its
     * [SavedProfile.builtIn] flag (so an edited built-in is still "factory" for restore purposes).
     */
    suspend fun save(name: String, settings: AabSettings) {
        val safeName = name.trim()
        require(safeName.isNotBlank() && safeName != "." && safeName != ".." &&
            safeName.length <= MAX_PROFILE_NAME_CHARS) { "Invalid profile name" }
        dataStore.updateData { raw ->
            val current = seedIfNeeded(raw)
            val exists = current.profiles.any { it.name == safeName }
            require(exists || current.profiles.size < MAX_PROFILES) { "Too many saved profiles" }
            val profiles = if (exists) {
                current.profiles.map { if (it.name == safeName) it.copy(settings = settings.validate()) else it }
            } else {
                current.profiles + SavedProfile(name = safeName, settings = settings.validate(), builtIn = false)
            }
            current.copy(profiles = profiles)
        }
    }

    /** Delete a saved profile by name (built-ins can be deleted; restore re-seeds them). */
    suspend fun delete(name: String) {
        dataStore.updateData { raw ->
            val current = seedIfNeeded(raw)
            current.copy(profiles = current.profiles.filterNot { it.name == name })
        }
    }

    /** Re-seed the five built-ins from [DefaultProfiles], leaving user-created profiles untouched. */
    suspend fun restoreFactory() {
        dataStore.updateData { current ->
            val factory = factoryProfiles()
            val factoryNames = factory.map { it.name }.toSet()
            val userOnly = current.profiles.filterNot { it.name in factoryNames }
            SavedProfiles(profiles = factory + userOnly, seeded = true)
        }
    }

    private fun seedIfNeeded(current: SavedProfiles): SavedProfiles =
        if (current.seeded) current else SavedProfiles(profiles = factoryProfiles(), seeded = true)

    private fun factoryProfiles(): List<SavedProfile> =
        DefaultProfiles.all.map { (name, settings) -> SavedProfile(name, settings, builtIn = true) }
}
