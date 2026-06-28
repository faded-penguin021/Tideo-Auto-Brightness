package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.settings.changedCount
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsDiffList
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnGold

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
    // Owner (final pre-S14): "Manage profiles & Export" collapse into ONE toggle so the saved-profiles
    // list is the uncluttered default surface; the management/export actions are one tap away.
    var showManage by remember { mutableStateOf(false) }
    // S13c' §07-C: the legacy Tasker import is collapsed by default (one-time migration).
    var showLegacy by remember { mutableStateOf(false) }

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
    if (contextLocked) ContextLockBanner(onResumeContext)

    // S13c restyle (m3_audit §3 row 10): empty hints become `EmptyState`; rows become `AabCard`s.
    SectionHeader("Saved profiles", divider = true)
    if (profiles.isEmpty()) {
        EmptyState("No saved profiles yet.", testTag = "empty_profiles")
    }
    profiles.forEach { entry ->
        ProfileCard(entry, onApply = { previewProfile = entry }, onOverwriteProfile, onDeleteProfile)
    }

    // Owner (final pre-S14): the profile-management actions + Export folded under ONE collapsed toggle.
    ExpandableSection(
        title = stringResource(R.string.manage_profiles_header),
        expanded = showManage,
        onToggle = { showManage = !showManage },
        testTag = "manage_section",
    ) {
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
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().testTag("export_profile")) {
            Text("Export current settings…")
        }
    }

    // S13c' §07-C: the legacy Tasker import is a one-time migration, not a daily control — demote it to
    // a collapsed expandable (closed by default) so it no longer dominates the first screenful. Owner:
    // the single-file "Import a settings file" picker belongs here too (it loads Tideo exports AND legacy
    // Tasker configs), grouped with the folder-link import rather than as a top-level button.
    ExpandableSection(
        title = stringResource(R.string.legacy_import_header),
        expanded = showLegacy,
        onToggle = { showLegacy = !showLegacy },
        testTag = "legacy_section",
    ) {
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import_profile")) {
            Text("Import a settings file…")
        }
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
            AabCard(Modifier.testTag("legacy_${entry.name}")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onLoadLegacy(entry) }, modifier = Modifier.testTag("load_${entry.name}")) {
                        Text("Load")
                    }
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

/**
 * G2R-F30 / D-111: a manual profile load pauses context automation; offer a Resume control. Styled as
 * the Tasker "golden banner with a play button" — the gold `secondaryContainer` + leading ▶ icon,
 * coherent with the dashboard's `OverrideCard` resume affordance and the M3 audit's gold-emphasis role
 * (design/m3_audit.md §2.4: `secondary`/gold = emphasis/warnings). Shared by [ProfilesBody] and the
 * unified [ProfilesContextsScreen].
 */
@Composable
internal fun ContextLockBanner(onResumeContext: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().testTag("context_lock_banner"),
        // Vivid brand gold (like the dashboard StaleBanner / Tasker's "Automation Paused" bar), not the
        // muted dark-theme `secondaryContainer` — the owner wanted the Tasker golden banner.
        colors = CardDefaults.cardColors(containerColor = AabGold, contentColor = AabOnGold),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Context automation is paused after a manual profile load.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // High-contrast resume — Tasker's black RESUME on gold: dark container, gold label + ▶ icon.
            Button(
                onClick = onResumeContext,
                colors = ButtonDefaults.buttonColors(containerColor = AabOnGold, contentColor = AabGold),
                modifier = Modifier.testTag("resume_context"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Resume context automation")
            }
        }
    }
}

@Composable
internal fun ProfileCard(
    entry: SavedProfile,
    onApply: () -> Unit,
    onOverwrite: (String) -> Unit,
    onDelete: (String) -> Unit,
    isActive: Boolean = false,
) {
    val changed = entry.settings.changedCount()
    var menu by remember { mutableStateOf(false) }
    // D-111 (owner): highlight the in-force profile with a gold edge + an "Active" tag, so the list
    // answers "which profile is loaded right now?" the way Tasker's "Active Profile: …" readout does.
    val cardModifier = Modifier.testTag("profile_${entry.name}").let {
        if (isActive) it.border(1.5.dp, AabGold, MaterialTheme.shapes.medium) else it
    }
    AabCard(
        cardModifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // S13c' §07-B: the row shows the name + the PRIMARY action (Apply); secondary actions
        // (Overwrite / Delete) collapse into a trailing overflow menu so the row reads clean.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name + if (entry.builtIn) "  (built-in)" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.profiles_active_tag),
                            style = MaterialTheme.typography.labelMedium,
                            color = AabGold,
                            modifier = Modifier.testTag("profile_active_${entry.name}"),
                        )
                    }
                }
                Text(
                    if (changed == 0) "Factory defaults" else "$changed changed from default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.testTag("profile_menu_${entry.name}"),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.profile_more_actions),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Overwrite") },
                        onClick = { menu = false; onOverwrite(entry.name) },
                        modifier = Modifier.testTag("overwrite_profile_${entry.name}"),
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete(entry.name) },
                        modifier = Modifier.testTag("delete_profile_${entry.name}"),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().testTag("apply_profile_${entry.name}"),
        ) { Text("Apply") }
    }
}

/**
 * A lightweight collapsible group (S13c' §07): a clickable header row (title + chevron) over [content]
 * shown only when [expanded]. Used to demote the one-time legacy Tasker import out of the daily flow.
 */
@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) content()
}

@Composable
internal fun SaveProfileDialog(
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
