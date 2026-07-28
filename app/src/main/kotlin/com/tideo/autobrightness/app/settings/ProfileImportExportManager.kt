package com.tideo.autobrightness.app.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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

    /** Our format failed; the legacy Tasker parser succeeded. [jsonError] is diagnostic metadata. */
    data class LegacyFallback(val settings: AabSettings, val jsonError: String) : ProfileLoadResult

    /** Neither parser succeeded — the caller shows a user-visible error card. */
    data class TotalFailure(val jsonError: String, val legacyError: String) : ProfileLoadResult

    /** The encoded input is larger than the profile format can reasonably require. */
    data object TooLarge : ProfileLoadResult

    /** The provider could not be read, or returned bytes that are not valid UTF-8. */
    data object ReadFailure : ProfileLoadResult
}

class ProfileImportExportManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "ProfileImport"

        // A full pretty-printed AabSettings export is only a few KiB. This leaves ample room for
        // future fields and legacy configs while bounding allocations from untrusted providers.
        internal const val MAX_ENCODED_PROFILE_BYTES = 256 * 1024
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
        return runCatching {
            context.openFileInput(sanitizeFileName(profileName)).use { readAndDecode(it) }
        }.getOrElse {
            Log.w(TAG, "Profile input could not be read")
            ProfileLoadResult.ReadFailure
        }
    }

    suspend fun importFromDocument(uri: Uri, resolver: ContentResolver = context.contentResolver): ProfileLoadResult {
        val declaredSize = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        return runCatching {
            resolver.openInputStream(uri)?.use { readAndDecode(it, declaredSize) }
                ?: return ProfileLoadResult.ReadFailure
        }.getOrElse {
            // URI and provider exception details can contain private document names or authorities.
            Log.w(TAG, "Profile input could not be read")
            ProfileLoadResult.ReadFailure
        }
    }

    internal fun readAndDecode(input: InputStream, declaredSize: Long? = null): ProfileLoadResult {
        if (declaredSize != null && declaredSize > MAX_ENCODED_PROFILE_BYTES) {
            return ProfileLoadResult.TooLarge
        }
        val bytes = ByteArrayOutputStream(MAX_ENCODED_PROFILE_BYTES.coerceAtMost(DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = MAX_ENCODED_PROFILE_BYTES + 1
        try {
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) {
                    val byte = input.read()
                    if (byte < 0) break
                    bytes.write(byte)
                    remaining--
                    continue
                }
                bytes.write(buffer, 0, count)
                remaining -= count
            }
        } catch (_: Exception) {
            Log.w(TAG, "Profile input could not be read")
            return ProfileLoadResult.ReadFailure
        }
        if (bytes.size() > MAX_ENCODED_PROFILE_BYTES) return ProfileLoadResult.TooLarge

        val content = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            Log.w(TAG, "Profile input is not valid UTF-8")
            return ProfileLoadResult.ReadFailure
        }
        return decodePayload(content)
    }

    fun importLegacyTaskerProfile(rawLegacyValues: String): AabSettings {
        return TaskerLegacyProfileSerializer.deserialize(rawLegacyValues)
    }

    /**
     * Decode a profile file, trying our [AabProfilePayload] format first and the legacy Tasker parser
     * second. Logs only the outcome, never parser details that could quote imported content.
     */
    fun decodePayload(content: String): ProfileLoadResult {
        val jsonAttempt = runCatching {
            json.decodeFromString(AabProfilePayload.serializer(), content).settings.validate()
        }
        jsonAttempt.getOrNull()?.let { return ProfileLoadResult.Success(it) }
        val jsonError = jsonAttempt.exceptionOrNull()?.message ?: "JSON parse failed"

        val legacyAttempt = runCatching { TaskerLegacyProfileSerializer.deserialize(content).validate() }
        legacyAttempt.getOrNull()?.let {
            Log.w(TAG, "Profile not in app format; loaded via legacy parser")
            return ProfileLoadResult.LegacyFallback(it, jsonError)
        }
        val legacyError = legacyAttempt.exceptionOrNull()?.message ?: "Legacy parse failed"
        Log.e(TAG, "Profile load failed validation")
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
