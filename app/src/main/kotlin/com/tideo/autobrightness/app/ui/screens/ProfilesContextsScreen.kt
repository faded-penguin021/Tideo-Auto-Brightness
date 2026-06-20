package com.tideo.autobrightness.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.LegacyConfigImporter
import com.tideo.autobrightness.app.settings.ProfileImportExportManager
import com.tideo.autobrightness.app.settings.ProfileLoadResult
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.ContextsViewModel
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.platform.context.LocationResult
import com.tideo.autobrightness.platform.context.SsidResult
import kotlinx.coroutines.launch

/**
 * Unified **Profiles & Contexts** screen (S12.9f IA merge, D-070): the Tasker UX where a profile owns
 * its context rules and a rule targets a saved profile, folded onto one destination. The saved-profiles
 * surface ([ProfilesBody] — save/load/overwrite/delete/import/export/factory-restore, unchanged) sits
 * above a "Context rules" section ([ContextRulesSection]) listing the priority-ordered rules with their
 * target profile; editing a rule opens the full editor in a modal. Replaces the separate Profiles and
 * Contexts destinations.
 *
 * Hosts both [SettingsViewModel] (profiles) and [ContextsViewModel] (rules); no change to
 * `ContextEngine`/`ContextOverrideResolver` or any store. Plumbing only — S13c restyles.
 */
@Composable
fun ProfilesContextsScreen(
    navController: NavHostController,
    settingsVm: SettingsViewModel = viewModel(),
    contextsVm: ContextsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()

    // --- Profiles side (SettingsViewModel) ---
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val profiles by settingsVm.profiles.collectAsStateWithLifecycle()
    val manager = remember { ProfileImportExportManager(context.applicationContext) }
    var status by remember { mutableStateOf<String?>(null) }
    // S12.9c #3: a user-visible error card for an unreadable profile file (ProfileLoadResult.TotalFailure).
    var loadError by remember { mutableStateOf<String?>(null) }

    // Apply a ProfileLoadResult: Success/LegacyFallback both apply the settings; TotalFailure shows the
    // error card. Returns the toast status string.
    fun handleLoad(
        result: ProfileLoadResult,
        okMessage: String,
        apply: (AabSettings) -> Unit,
    ): String = when (result) {
        is ProfileLoadResult.Success -> { apply(result.settings); loadError = null; okMessage }
        is ProfileLoadResult.LegacyFallback -> { apply(result.settings); loadError = null; okMessage }
        is ProfileLoadResult.TotalFailure -> {
            loadError = "Couldn't read this profile. It isn't a Tideo export or a Tasker AAB config."
            "Load failed"
        }
    }

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
    LaunchedEffect(legacyTree) { refreshLegacy(legacyTree) }

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
                handleLoad(manager.importFromDocument(uri), "Imported.") { settingsVm.replaceAll(it) }
            }.getOrElse { loadError = it.message; "Import failed: ${it.message}" }
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

    // --- Contexts side (ContextsViewModel) ---
    val rules by contextsVm.rules.collectAsStateWithLifecycle()
    val profileNames by contextsVm.profileNames.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = runCatching { contextsVm.installedApps() }.getOrDefault(emptyList()) }
    // G2R-F68: resolve today's sunrise/sunset for the token labels (gold "Sunrise (06:42)").
    var solarLabel by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { solarLabel = runCatching { contextsVm.solarTimes() }.getOrNull() }

    SettingsScaffold(stringResource(R.string.title_profiles_contexts), { navController.popBackStack() }) { padding ->
        SettingsColumn(padding) {
            ProfilesBody(
                profiles = profiles,
                legacyEntries = legacyEntries,
                contextLocked = settings.contextOverride,
                currentSettings = settings,
                status = status,
                onApplyProfile = { name -> settingsVm.applyProfile(name); toast("Applied profile: $name") },
                onOverwriteProfile = { name -> settingsVm.saveCurrentAs(name); toast("Overwrote: $name") },
                onDeleteProfile = { name -> settingsVm.deleteProfile(name); toast("Deleted: $name") },
                onSaveCurrentAs = { name -> settingsVm.saveCurrentAs(name); toast("Saved profile: $name") },
                onRestoreFactory = { settingsVm.restoreFactoryProfiles(); toast("Factory profiles restored") },
                onResumeContext = { settingsVm.resumeContextAutomation(); toast("Context automation resumed") },
                onReset = { settingsVm.resetDefaults(); toast("Reset to defaults") },
                onExport = { exportLauncher.launch("tideo-profile.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onChooseLegacyFolder = { folderLauncher.launch(null) },
                onLoadLegacy = { entry ->
                    scope.launch {
                        status = runCatching {
                            handleLoad(manager.importFromDocument(entry.uri), "Loaded ${entry.name}") { imported ->
                                // G2R-F44: register the legacy profile under its file name so it's selectable
                                // as a context-rule target without a manual re-save, then apply it live.
                                val profileName = entry.name.removeSuffix(".json").removeSuffix(".JSON")
                                settingsVm.saveImportedProfile(profileName, imported)
                                settingsVm.replaceAll(imported)
                            }
                        }.getOrElse { loadError = it.message; "Load failed: ${it.message}" }
                        status?.let(toast)
                    }
                },
                loadError = loadError,
                onDismissLoadError = { loadError = null },
            )

            SectionHeader("Context rules")
            ContextRulesSection(
                rules = rules,
                profileNames = profileNames.ifEmpty { listOf("Default") },
                apps = apps,
                solarLabel = solarLabel,
                onSave = { toast("Rule saved"); contextsVm.save(it) },
                onDelete = { contextsVm.delete(it); toast("Rule deleted") },
                onUseCurrentSsid = { setSsid ->
                    scope.launch {
                        // G2R-F22: targeted message per failure mode, not a blanket "Not connected".
                        when (val result = contextsVm.currentSsid()) {
                            is SsidResult.Connected -> { setSsid(result.ssid); toast("Wi-Fi: ${result.ssid}") }
                            SsidResult.NotOnWifi -> toast("Not connected to Wi-Fi")
                            SsidResult.NeedsLocationPermission ->
                                toast("Reading the Wi-Fi name needs Location permission (grant it in Setup)")
                            SsidResult.LocationServicesOff ->
                                toast("Turn on Location services to read the Wi-Fi name")
                            SsidResult.Unknown -> toast("Could not read the current Wi-Fi name")
                        }
                    }
                },
                onUseCurrentLocation = { setLatLon ->
                    // G2R-F22/F42: recheck the grant at call time + request a fresh fix; targeted
                    // message per outcome (no longer wrongly reports "not granted" right after the grant).
                    scope.launch {
                        when (val result = contextsVm.currentLocation()) {
                            is LocationResult.Available -> {
                                setLatLon(result.snapshot.latitude, result.snapshot.longitude)
                                toast("Location: %.4f, %.4f".format(result.snapshot.latitude, result.snapshot.longitude))
                            }
                            LocationResult.NeedsPermission ->
                                toast("Grant Location permission to use the current location")
                            LocationResult.Unavailable ->
                                toast("No location fix yet — try again in a moment")
                        }
                    }
                },
                hasUsageAccess = contextsVm::hasUsageAccess,
                onRequestUsageAccess = {
                    toast("Grant usage access so per-app rules can detect the foreground app")
                    runCatching { context.startActivity(contextsVm.usageAccessIntent()) }
                },
            )
        }
    }
}
