package com.tideo.autobrightness.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** One JSON config file discovered under the granted `Download/AAB/configs` tree (G2R-F16). */
data class LegacyConfigEntry(val name: String, val uri: Uri)

/** SAF folder import for legacy Tasker profiles (S12.6d, G2R-F16): persist URI, enumerate *.json with DocumentsContract. */
object LegacyConfigImporter {
    internal const val MAX_LISTED_CONFIGS = 256

    /** Persist the long-lived read permission on a tree URI returned by `OpenDocumentTree`. */
    fun persistGrant(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** True if we still hold a persisted read grant on [treeUri]. */
    fun hasGrant(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    /** List the `*.json` documents directly under the granted tree, sorted by name. */
    fun listJson(context: Context, treeUri: Uri): List<LegacyConfigEntry> {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val out = ArrayList<LegacyConfigEntry>()
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (out.size < MAX_LISTED_CONFIGS && cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (!name.endsWith(".json", ignoreCase = true)) continue
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    out += LegacyConfigEntry(name, fileUri)
                }
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }
}
