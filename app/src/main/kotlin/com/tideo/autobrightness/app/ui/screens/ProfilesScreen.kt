package com.tideo.autobrightness.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.LegacyConfigImporter
import com.tideo.autobrightness.app.settings.ProfileImportExportManager
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.rememberToaster
import kotlinx.coroutines.launch

/**
 * Profiles & Import/Export (Tasker AAB Profile — task592/637/622). S12.6d makes every profile an
 * editable saved entry (G2R-F15, owner-decision 3): save current as…, overwrite, delete, and
 * "Restore factory profiles". Legacy import is via a one-time SAF folder grant to
 * `Download/AAB/configs` (G2R-F16, owner-decision 4). A manual profile load latches the context lock,
 * surfaced here with a Resume banner (G2R-F30).
 */
@Composable
fun ProfilesScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ProfileImportExportManager(context.applicationContext) }
    var status by remember { mutableStateOf<String?>(null) }
    val toast = rememberToaster()

    // Previously-granted Download/AAB/configs tree (persisted SAF permission), if any.
    var legacyTree by remember {
        mutableStateOf(
            context.contentResolver.persistedUriPermissions.firstOrNull { it.isReadPermission }?.uri,
        )
    }
    var legacyEntries by remember { mutableStateOf<List<LegacyConfigEntry>>(emptyList()) }

    fun refreshLegacy(tree: Uri?) {
        legacyEntries = if (tree != null) LegacyConfigImporter.listJson(context, tree) else emptyList()
    }
    androidx.compose.runtime.LaunchedEffect(legacyTree) { refreshLegacy(legacyTree) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching { manager.exportToDocument(uri, settings); "Exported." }
                .getOrElse { "Export failed: ${it.message}" }
            status?.let(toast)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching {
                vm.replaceAll(manager.importFromDocument(uri)); "Imported."
            }.getOrElse { "Import failed: ${it.message}" }
            status?.let(toast)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            LegacyConfigImporter.persistGrant(context, uri)
            legacyTree = uri
            refreshLegacy(uri)
            toast("Folder linked")
        }
    }

    ProfilesContent(
        profiles = profiles,
        legacyEntries = legacyEntries,
        contextLocked = settings.contextOverride,
        status = status,
        onBack = { navController.popBackStack() },
        onApplyProfile = { name -> vm.applyProfile(name); toast("Applied profile: $name") },
        onOverwriteProfile = { name -> vm.saveCurrentAs(name); toast("Overwrote: $name") },
        onDeleteProfile = { name -> vm.deleteProfile(name); toast("Deleted: $name") },
        onSaveCurrentAs = { name -> vm.saveCurrentAs(name); toast("Saved profile: $name") },
        onRestoreFactory = { vm.restoreFactoryProfiles(); toast("Factory profiles restored") },
        onResumeContext = { vm.resumeContextAutomation(); toast("Context automation resumed") },
        onReset = { vm.resetDefaults(); toast("Reset to defaults") },
        onExport = { exportLauncher.launch("tideo-profile.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onChooseLegacyFolder = { folderLauncher.launch(null) },
        onLoadLegacy = { entry ->
            scope.launch {
                status = runCatching {
                    vm.replaceAll(manager.importFromDocument(entry.uri)); "Loaded ${entry.name}"
                }.getOrElse { "Load failed: ${it.message}" }
                status?.let(toast)
            }
        },
    )
}

@Composable
fun ProfilesContent(
    profiles: List<SavedProfile>,
    legacyEntries: List<LegacyConfigEntry>,
    contextLocked: Boolean,
    status: String?,
    onBack: () -> Unit,
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
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    SettingsScaffold("Profiles & Import/Export", onBack) { padding ->
        SettingsColumn(padding) {
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
                ProfileCard(entry, onApplyProfile, onOverwriteProfile, onDeleteProfile)
            }

            SectionHeader("Manage profiles")
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
        }
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name -> showSaveDialog = false; onSaveCurrentAs(name) },
        )
    }
}

@Composable
private fun ProfileCard(
    entry: SavedProfile,
    onApply: (String) -> Unit,
    onOverwrite: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("profile_${entry.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                entry.name + if (entry.builtIn) "  (built-in)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onApply(entry.name) },
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
private fun SaveProfileDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("save_profile_name"),
            )
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
