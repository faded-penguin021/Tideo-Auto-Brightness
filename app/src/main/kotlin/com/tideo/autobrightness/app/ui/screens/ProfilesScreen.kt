package com.tideo.autobrightness.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.DefaultProfiles
import com.tideo.autobrightness.app.settings.ProfileImportExportManager
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import kotlinx.coroutines.launch

/** Profiles & Import/Export (Tasker AAB Profile — task592/637/622 defaults + file import/export). */
@Composable
fun ProfilesScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ProfileImportExportManager(context.applicationContext) }
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching { manager.exportToDocument(uri, settings); "Exported." }
                .getOrElse { "Export failed: ${it.message}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching {
                val imported = manager.importFromDocument(uri)
                vm.replaceAll(imported)
                "Imported."
            }.getOrElse { "Import failed: ${it.message}" }
        }
    }

    ProfilesContent(
        profileNames = DefaultProfiles.all.keys.toList(),
        status = status,
        onBack = { navController.popBackStack() },
        onApplyProfile = vm::applyProfile,
        onReset = vm::resetDefaults,
        onExport = { exportLauncher.launch("tideo-profile.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
    )
}

@Composable
fun ProfilesContent(
    profileNames: List<String>,
    status: String?,
    onBack: () -> Unit,
    onApplyProfile: (String) -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SettingsScaffold("Profiles & Import/Export", onBack) { padding ->
        SettingsColumn(padding) {
            SectionHeader("Built-in profiles")
            profileNames.forEach { name ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { onApplyProfile(name) },
                            modifier = Modifier.testTag("apply_profile_$name"),
                        ) { Text("Apply") }
                    }
                }
            }

            SectionHeader("Manage")
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().testTag("export_profile")) {
                Text("Export current settings…")
            }
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import_profile")) {
                Text("Import settings (incl. legacy Tasker)…")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().testTag("reset_defaults")) {
                Text("Reset to defaults")
            }

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
