package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.settings.changedCount
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsDiffList
import com.tideo.autobrightness.app.ui.components.SettingsScaffold

/**
 * Profiles & Import/Export content (Tasker AAB Profile — task592/637/622), the saved-profiles surface
 * (save current as…, overwrite, delete, "Restore factory profiles", legacy SAF import). S12.9f folds
 * this into the unified Profiles & Contexts screen ([ProfilesContextsScreen]) via [ProfilesBody];
 * [ProfilesContent] keeps the standalone scaffold for the existing screen tests. A manual profile load
 * latches the context lock, surfaced with a Resume banner (G2R-F30).
 */
@Composable
fun ProfilesContent(
    profiles: List<SavedProfile>,
    legacyEntries: List<LegacyConfigEntry>,
    contextLocked: Boolean,
    status: String?,
    onBack: () -> Unit,
    currentSettings: AabSettings = AabSettings(),
    onApplyProfile: (String) -> Unit,
    onOverwriteProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveCurrentAs: (String) -> Unit,
    onRestoreFactory: () -> Unit,
    onResumeContext: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChooseLegacyFolder: () -> Unit,
    onLoadLegacy: (LegacyConfigEntry) -> Unit,
    loadError: String? = null,
    onDismissLoadError: () -> Unit = {},
) {
    SettingsScaffold(stringResource(R.string.title_profiles_import_export), onBack) { padding ->
        SettingsColumn(padding) {
            ProfilesBody(
                profiles = profiles,
                legacyEntries = legacyEntries,
                contextLocked = contextLocked,
                status = status,
                currentSettings = currentSettings,
                onApplyProfile = onApplyProfile,
                onOverwriteProfile = onOverwriteProfile,
                onDeleteProfile = onDeleteProfile,
                onSaveCurrentAs = onSaveCurrentAs,
                onRestoreFactory = onRestoreFactory,
                onResumeContext = onResumeContext,
                onReset = onReset,
                onExport = onExport,
                onImport = onImport,
                onChooseLegacyFolder = onChooseLegacyFolder,
                onLoadLegacy = onLoadLegacy,
                loadError = loadError,
                onDismissLoadError = onDismissLoadError,
            )
        }
    }
}

/**
 * The saved-profiles + import/export body, emitted into a scrolling [Column] (e.g. [SettingsColumn]).
 * Hosts its own save/load/current-settings dialog state. Reused by the unified
 * [ProfilesContextsScreen] and the standalone [ProfilesContent] (S12.9f IA merge).
 */
@Composable
fun ProfilesBody(
    profiles: List<SavedProfile>,
    legacyEntries: List<LegacyConfigEntry>,
    contextLocked: Boolean,
    status: String?,
    currentSettings: AabSettings = AabSettings(),
    onApplyProfile: (String) -> Unit,
    onOverwriteProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveCurrentAs: (String) -> Unit,
    onRestoreFactory: () -> Unit,
    onResumeContext: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChooseLegacyFolder: () -> Unit,
    onLoadLegacy: (LegacyConfigEntry) -> Unit,
    loadError: String? = null,
    onDismissLoadError: () -> Unit = {},
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    // G2R-F38: the Tasker "Load Anyway" modal — preview the profile's full settings list (gold
    // changed-vs-default) before applying. `previewProfile` holds the entry whose modal is open.
    var previewProfile by remember { mutableStateOf<SavedProfile?>(null) }
    var showCurrentSettings by remember { mutableStateOf(false) }

    // S12.9c #3: surface an unreadable-profile failure (ProfileLoadResult.TotalFailure).
    if (loadError != null) {
        Card(
            Modifier.fillMaxWidth().testTag("load_error_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    loadError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onDismissLoadError, modifier = Modifier.testTag("dismiss_load_error")) {
                    Text("Dismiss")
                }
            }
        }
    }
    // G2R-F30: a manual profile load pauses context automation; offer a Resume control.
    if (contextLocked) {
        Card(
            Modifier.fillMaxWidth().testTag("context_lock_banner"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Context automation is paused after a manual profile load.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                OutlinedButton(onClick = onResumeContext, modifier = Modifier.testTag("resume_context")) {
                    Text("Resume context automation")
                }
            }
        }
    }

    SectionHeader("Saved profiles")
    if (profiles.isEmpty()) {
        Text("No saved profiles yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    profiles.forEach { entry ->
        ProfileCard(entry, onApply = { previewProfile = entry }, onOverwriteProfile, onDeleteProfile)
    }

    SectionHeader("Manage profiles")
    // G2R-F38: the Tasker dashboard compares active settings vs factory defaults (tuned
    // values shown yellow) — surface the live set with the same gold highlighting.
    OutlinedButton(
        onClick = { showCurrentSettings = true },
        modifier = Modifier.fillMaxWidth().testTag("view_current_settings"),
    ) { Text("View current settings…") }
    Button(
        onClick = { showSaveDialog = true },
        modifier = Modifier.fillMaxWidth().testTag("save_profile_as"),
    ) { Text("Save current settings as…") }
    OutlinedButton(
        onClick = onRestoreFactory,
        modifier = Modifier.fillMaxWidth().testTag("restore_factory"),
    ) { Text("Restore factory profiles") }
    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().testTag("reset_defaults")) {
        Text("Reset settings to defaults")
    }

    SectionHeader("Import / Export")
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth().testTag("export_profile")) {
        Text("Export current settings…")
    }
    Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import_profile")) {
        Text("Import a settings file (incl. legacy Tasker)…")
    }

    SectionHeader("Legacy folder (Download/AAB/configs)")
    OutlinedButton(
        onClick = onChooseLegacyFolder,
        modifier = Modifier.fillMaxWidth().testTag("choose_legacy_folder"),
    ) { Text(if (legacyEntries.isEmpty()) "Link legacy configs folder…" else "Re-link legacy folder…") }
    if (legacyEntries.isEmpty()) {
        Text(
            "Grant the Download/AAB/configs folder once to load profiles saved by the Tasker app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    legacyEntries.forEach { entry ->
        Card(Modifier.fillMaxWidth().testTag("legacy_${entry.name}")) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onLoadLegacy(entry) }, modifier = Modifier.testTag("load_${entry.name}")) {
                    Text("Load")
                }
            }
        }
    }

    status?.let {
        Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            currentSettings = currentSettings,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name -> showSaveDialog = false; onSaveCurrentAs(name) },
        )
    }

    // G2R-F38: the "Load Anyway" modal — full settings list, gold changed-vs-default, then Apply.
    previewProfile?.let { entry ->
        LoadProfileDialog(
            profile = entry,
            onDismiss = { previewProfile = null },
            onConfirm = { previewProfile = null; onApplyProfile(entry.name) },
        )
    }

    if (showCurrentSettings) {
        CurrentSettingsDialog(
            settings = currentSettings,
            onDismiss = { showCurrentSettings = false },
        )
    }
}

@Composable
private fun ProfileCard(
    entry: SavedProfile,
    onApply: () -> Unit,
    onOverwrite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val changed = entry.settings.changedCount()
    Card(Modifier.fillMaxWidth().testTag("profile_${entry.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                entry.name + if (entry.builtIn) "  (built-in)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (changed == 0) "Factory defaults" else "$changed changed from default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onApply,
                    modifier = Modifier.testTag("apply_profile_${entry.name}"),
                ) { Text("Apply") }
                OutlinedButton(
                    onClick = { onOverwrite(entry.name) },
                    modifier = Modifier.testTag("overwrite_profile_${entry.name}"),
                ) { Text("Overwrite") }
                TextButton(
                    onClick = { onDelete(entry.name) },
                    modifier = Modifier.testTag("delete_profile_${entry.name}"),
                ) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(
    currentSettings: AabSettings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_name"),
                )
                Text("Saving these settings:", style = MaterialTheme.typography.labelMedium)
                // G2R-F38: show exactly what is being saved, gold-highlighting changed-vs-default.
                SettingsDiffList(currentSettings, maxHeight = 260)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                modifier = Modifier.testTag("confirm_save_profile"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The Tasker "Load Anyway" modal (profile.md `performTask('_ProfileManager', …, 'LOAD_FILE', …)`,
 * G2R-F38): preview the profile's full settings list (gold changed-vs-default) before applying it to
 * the live configuration.
 */
@Composable
fun LoadProfileDialog(profile: SavedProfile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load “${profile.name}”?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "These settings will replace your current configuration.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsDiffList(profile.settings)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_load_profile")) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Read-only "current settings vs factory defaults" view (profile.md dashboard, G2R-F38). */
@Composable
fun CurrentSettingsDialog(settings: AabSettings, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Current settings") },
        text = { SettingsDiffList(settings) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_current_settings")) {
                Text("Close")
            }
        },
    )
}
