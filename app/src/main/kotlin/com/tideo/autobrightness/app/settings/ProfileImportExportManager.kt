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
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Outcome of loading a profile file (S12.9c #3): loaded our format, fell back to legacy Tasker parser, or failed.
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

        // DA-029: allocation bound to stop untrusted SAF providers from unbounded reads.
        // The slack allows for future fields and fat legacy configs.
        internal const val MAX_ENCODED_PROFILE_BYTES = 256 * 1024

        /**
         * DA-044: wall-clock bound on one SAF import/export. Generous enough for a large document on
         * slow cloud-backed storage, short enough that a stalled provider surfaces as an error rather
         * than an apparently hung app.
         */
        internal const val PROVIDER_TIMEOUT_MS = 20_000L
    }

    private val json = Json { ignoreUnknownKeys = false; prettyPrint = true }

    suspend fun exportToAppPrivate(profileName: String, settings: AabSettings): String {
        val fileName = sanitizeFileName(profileName)
        val payload = json.encodeToString(
            AabProfilePayload.serializer(),
            AabProfilePayload(settings = settings.validate()),
        ).encodeToByteArray()
        withContext(Dispatchers.IO) {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output -> output.write(payload) }
        }
        return fileName
    }

    /**
     * DA-044: encode first, then dispatch IO to avoid blocking the UI thread with untrusted providers.
     */
    suspend fun exportToDocument(uri: Uri, settings: AabSettings, resolver: ContentResolver = context.contentResolver) {
        val payload = json.encodeToString(
            AabProfilePayload.serializer(),
            AabProfilePayload(settings = settings.validate()),
        ).encodeToByteArray()
        withContext(Dispatchers.IO) {
            withTimeout(PROVIDER_TIMEOUT_MS) {
                resolver.openOutputStream(uri)?.use { output -> output.write(payload) }
                    ?: throw FileNotFoundException("Unable to open output stream for uri=$uri")
            }
        }
    }

    /** App-private read: trusted bytes, but still file I/O and still cancellable (DA-044). */
    suspend fun importFromAppPrivate(profileName: String): ProfileLoadResult = withContext(Dispatchers.IO) {
        try {
            context.openFileInput(sanitizeFileName(profileName)).use { readAndDecode(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(TAG, "Profile input could not be read")
            ProfileLoadResult.ReadFailure
        }
    }

    /**
     * DA-044: all provider calls run on [Dispatchers.IO] under a wall-clock timeout.
     * DA-029 caps allocation; [PROVIDER_TIMEOUT_MS] bounds the caller's wait.
     */
    suspend fun importFromDocument(uri: Uri, resolver: ContentResolver = context.contentResolver): ProfileLoadResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(PROVIDER_TIMEOUT_MS) {
                    // DA-029: OpenableColumns.SIZE is a HINT, never the bound. It comes from the same
                    // untrusted provider as the bytes, so it can only buy an early reject — a provider
                    // that under-reports still meets the streamed cap in readAndDecode.
                    val declaredSize = try {
                        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    resolver.openInputStream(uri)?.use { readAndDecode(it, declaredSize) }
                        ?: ProfileLoadResult.ReadFailure
                }
            } catch (timeout: TimeoutCancellationException) {
                Log.w(TAG, "Profile input timed out")
                ProfileLoadResult.ReadFailure
            } catch (cancelled: CancellationException) {
                // The screen was left / the scope died. Cancellation is control flow, not a parse
                // failure — swallowing it here would report a bogus error and break structured
                // concurrency for the caller.
                throw cancelled
            } catch (_: Exception) {
                // URI and provider exception details can contain private document names or authorities.
                Log.w(TAG, "Profile input could not be read")
                ProfileLoadResult.ReadFailure
            }
        }

    /**
     * DA-029: read [MAX_ENCODED_PROFILE_BYTES] + one probe byte as strict UTF-8.
     * Probe byte distinguishes full buffer from truncated; strict decoding prevents confusion.
     */
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
        runCatching { ImportStructureGuard.requireBoundedJson(content) }.exceptionOrNull()?.let {
            return ProfileLoadResult.TotalFailure(it.message ?: "JSON structure rejected", "Legacy parse not attempted")
        }
        val jsonAttempt = runCatching {
            val payload = json.decodeFromString(AabProfilePayload.serializer(), content)
            require(payload.schemaVersion in 1..CURRENT_SCHEMA_VERSION) { "Unsupported profile schema" }
            require(payload.settings.schemaVersion in 1..CURRENT_SCHEMA_VERSION) { "Unsupported settings schema" }
            AabSettingsSerializer.migrate(payload.settings).validate()
        }
        jsonAttempt.getOrNull()?.let { return ProfileLoadResult.Success(it) }
        val jsonError = jsonAttempt.exceptionOrNull()?.message ?: "JSON parse failed"

        // Native format payloads cannot fallback to legacy parser to prevent schema bypass.
        val nativeShape = runCatching {
            (json.parseToJsonElement(content) as? JsonObject)?.keys?.any { it == "schemaVersion" || it == "settings" }
        }.getOrNull() == true
        if (nativeShape) {
            return ProfileLoadResult.TotalFailure(jsonError, "Native payload is not eligible for legacy fallback")
        }

        val legacyAttempt = runCatching { TaskerLegacyProfileSerializer.deserialize(content).validate() }
        legacyAttempt.getOrNull()?.let {
            Log.w(TAG, "Profile not in app format; loaded via legacy parser")
            return ProfileLoadResult.LegacyFallback(it, jsonError)
        }
        val legacyError = legacyAttempt.exceptionOrNull()?.message ?: "Legacy parse failed"
        Log.e(TAG, "Profile load failed validation")
        return ProfileLoadResult.TotalFailure(jsonError, legacyError)
    }

    internal fun sanitizeFileName(profileName: String): String {
        val trimmed = profileName.trim().removeSuffix(".json")
        val normalized = trimmed.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('.', '_', '-')
            .take(96)
        val base = normalized.ifBlank { "profile" }
        val suffix = if (base == trimmed) "" else "-${profileName.sha256Prefix()}"
        return "$base$suffix.json"
    }

    private fun String.sha256Prefix(): String = MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray()).take(6).joinToString("") { "%02x".format(it) }
}

@Serializable
internal data class AabProfilePayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val settings: AabSettings,
)
