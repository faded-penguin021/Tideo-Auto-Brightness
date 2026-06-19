package com.tideo.autobrightness.app.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Outcome of loading a profile file (S12.9c #3). Replaces the old silent
 * `runCatching{}.getOrElse{legacy}` so the caller can distinguish "loaded our format", "fell back to
 * the legacy Tasker parser" and "could not parse at all" — only the last is surfaced to the user.
 */
sealed interface ProfileLoadResult {
    /** Parsed as our own [AabProfilePayload] export format. */
    data class Success(val settings: AabSettings) : ProfileLoadResult

    /** Our format failed; the legacy Tasker parser succeeded. [jsonError] is informational (logged). */
    data class LegacyFallback(val settings: AabSettings, val jsonError: String) : ProfileLoadResult

    /** Neither parser succeeded — the caller shows a user-visible error card. */
    data class TotalFailure(val jsonError: String, val legacyError: String) : ProfileLoadResult
}

class ProfileImportExportManager(
    private val context: Context,
) {
    private companion object {
        const val TAG = "ProfileImport"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun exportToAppPrivate(profileName: String, settings: AabSettings): String {
        val fileName = sanitizeFileName(profileName)
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
            output.write(json.encodeToString(AabProfilePayload.serializer(), AabProfilePayload(settings = settings.validate())).encodeToByteArray())
        }
        return fileName
    }

    suspend fun exportToDocument(uri: Uri, settings: AabSettings, resolver: ContentResolver = context.contentResolver) {
        resolver.openOutputStream(uri)?.use { output ->
            output.write(json.encodeToString(AabProfilePayload.serializer(), AabProfilePayload(settings = settings.validate())).encodeToByteArray())
        } ?: throw FileNotFoundException("Unable to open output stream for uri=$uri")
    }

    suspend fun importFromAppPrivate(profileName: String): ProfileLoadResult {
        val content = context.openFileInput(sanitizeFileName(profileName)).bufferedReader().use { it.readText() }
        return decodePayload(content)
    }

    suspend fun importFromDocument(uri: Uri, resolver: ContentResolver = context.contentResolver): ProfileLoadResult {
        val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("Unable to open input stream for uri=$uri")
        return decodePayload(content)
    }

    fun importLegacyTaskerProfile(rawLegacyValues: String): AabSettings {
        return TaskerLegacyProfileSerializer.deserialize(rawLegacyValues)
    }

    /**
     * Decode a profile file, trying our [AabProfilePayload] format first and the legacy Tasker parser
     * second. Logs the JSON failure on a legacy fallback and both failures on a total failure (S12.9c #3).
     */
    fun decodePayload(content: String): ProfileLoadResult {
        val jsonAttempt = runCatching {
            json.decodeFromString(AabProfilePayload.serializer(), content).settings.validate()
        }
        jsonAttempt.getOrNull()?.let { return ProfileLoadResult.Success(it) }
        val jsonError = jsonAttempt.exceptionOrNull()?.message ?: "JSON parse failed"

        val legacyAttempt = runCatching { TaskerLegacyProfileSerializer.deserialize(content).validate() }
        legacyAttempt.getOrNull()?.let {
            Log.w(TAG, "Profile not in app format; loaded via legacy parser. JSON error: $jsonError")
            return ProfileLoadResult.LegacyFallback(it, jsonError)
        }
        val legacyError = legacyAttempt.exceptionOrNull()?.message ?: "Legacy parse failed"
        Log.e(TAG, "Profile load failed. JSON error: $jsonError; legacy error: $legacyError")
        return ProfileLoadResult.TotalFailure(jsonError, legacyError)
    }

    private fun sanitizeFileName(profileName: String): String {
        val safeName = profileName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (safeName.endsWith(".json")) safeName else "$safeName.json"
    }
}

@Serializable
private data class AabProfilePayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val settings: AabSettings,
)
