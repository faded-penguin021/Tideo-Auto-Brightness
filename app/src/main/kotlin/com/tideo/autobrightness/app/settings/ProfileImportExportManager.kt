package com.tideo.autobrightness.app.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ProfileImportExportManager(
    private val context: Context,
) {
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

    suspend fun importFromAppPrivate(profileName: String): AabSettings {
        val content = context.openFileInput(sanitizeFileName(profileName)).bufferedReader().use { it.readText() }
        return decodePayload(content)
    }

    suspend fun importFromDocument(uri: Uri, resolver: ContentResolver = context.contentResolver): AabSettings {
        val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("Unable to open input stream for uri=$uri")
        return decodePayload(content)
    }

    fun importLegacyTaskerProfile(rawLegacyValues: String): AabSettings {
        return TaskerLegacyProfileSerializer.deserialize(rawLegacyValues)
    }

    private fun decodePayload(content: String): AabSettings {
        return runCatching {
            json.decodeFromString(AabProfilePayload.serializer(), content).settings.validate()
        }.getOrElse {
            TaskerLegacyProfileSerializer.deserialize(content)
        }
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
