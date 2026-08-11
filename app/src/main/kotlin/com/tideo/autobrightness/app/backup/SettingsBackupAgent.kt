package com.tideo.autobrightness.app.backup

import android.app.backup.BackupAgentHelper
import android.util.Log
import java.io.File

// DB-002: restore-side hook for SettingsBackupSanitizer. Runs onRestoreFinished (best-effort).
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
        const val SETTINGS_RELATIVE_PATH = "datastore/aab_settings.json"
    }
}
