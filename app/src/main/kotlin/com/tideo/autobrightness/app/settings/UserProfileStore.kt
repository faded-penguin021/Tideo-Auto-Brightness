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

@Serializable
data class SavedProfile(
    val name: String,
    val settings: AabSettings,
    val builtIn: Boolean = false,
)

@Serializable
data class SavedProfiles(
    val profiles: List<SavedProfile> = emptyList(),
    val seeded: Boolean = false,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

object SavedProfilesSerializer : Serializer<SavedProfiles> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // DB-004: allocation ceiling for saved profile set.
    internal const val MAX_ENCODED_PROFILES_BYTES = 4 * 1024 * 1024

    override val defaultValue: SavedProfiles = SavedProfiles()

    // DB-004: bound READ before parsing.
    override suspend fun readFrom(input: InputStream): SavedProfiles =
        runCatching {
            // readNBytes is API 33; minSdk is 31, so read bound by hand.
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

// User-editable profiles CRUD (S12.6d). Five DefaultProfiles seeded once; re-seeded on restore.
class UserProfileStore(private val dataStore: DataStore<SavedProfiles>) {

    companion object {
        internal const val MAX_PROFILES = 128
        internal const val MAX_PROFILE_NAME_CHARS = 96
    }

    fun profilesFlow(): Flow<List<SavedProfile>> = dataStore.data.map { seedIfNeeded(it).profiles }

    suspend fun profiles(): List<SavedProfile> {
        ensureSeeded()
        return dataStore.data.first().profiles
    }

    suspend fun names(): List<String> = profiles().map { it.name }

    suspend fun get(name: String): AabSettings? = profiles().firstOrNull { it.name == name }?.settings?.validate()

    suspend fun ensureSeeded() {
        dataStore.updateData { current -> seedIfNeeded(current) }
    }

    // Save (create or overwrite) profile. Overwrite keeps list position + builtIn flag.
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

    suspend fun delete(name: String) {
        dataStore.updateData { raw ->
            val current = seedIfNeeded(raw)
            current.copy(profiles = current.profiles.filterNot { it.name == name })
        }
    }

    // Re-seed five built-ins from DefaultProfiles; keep user profiles.
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
