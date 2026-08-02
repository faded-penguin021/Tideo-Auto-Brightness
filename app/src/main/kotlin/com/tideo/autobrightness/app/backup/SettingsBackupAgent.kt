package com.tideo.autobrightness.app.backup

import android.app.backup.BackupAgentHelper
import android.util.Log
import java.io.File

/**
 * DB-002: the restore-side hook for [SettingsBackupSanitizer].
 *
 * Auto-backup (`android:allowBackup` + `data_extraction_rules.xml`) does the transfer; this agent
 * exists only for [onRestoreFinished], the documented callback that runs after the restored files
 * are in place and before the app is used. [BackupAgentHelper] rather than `BackupAgent` because the
 * key-value `onBackup`/`onRestore` pair is irrelevant to an auto-backup app and would otherwise have
 * to be implemented as dead no-ops.
 *
 * Deliberately best-effort: a failure here must never leave the app unusable after a restore, so a
 * sanitize that cannot be completed leaves the restored file alone. The runtime consequence is
 * bounded — a restored `serviceEnabled=true` starts a service the user can stop, and the settings
 * serializer already tolerates a malformed file by falling back to defaults.
 */
class SettingsBackupAgent : BackupAgentHelper() {

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        runCatching { sanitizeSettingsFile(File(filesDir, SETTINGS_RELATIVE_PATH)) }
            .onFailure { Log.w(TAG, "Restored settings could not be sanitized") }
    }

    internal fun sanitizeSettingsFile(file: File) {
        if (!file.isFile) return
        val sanitized = SettingsBackupSanitizer.sanitize(file.readText()) ?: return
        file.writeText(sanitized)
    }

    private companion object {
        const val TAG = "SettingsBackupAgent"

        /** Matches the `datastore/aab_settings.json` include in `data_extraction_rules.xml`. */
        const val SETTINGS_RELATIVE_PATH = "datastore/aab_settings.json"
    }
}
