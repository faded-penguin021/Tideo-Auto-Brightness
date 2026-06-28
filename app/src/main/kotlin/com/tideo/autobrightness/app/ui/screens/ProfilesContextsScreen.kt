package com.tideo.autobrightness.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.LegacyConfigImporter
import com.tideo.autobrightness.app.settings.ProfileImportExportManager
import com.tideo.autobrightness.app.settings.ProfileLoadResult
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.ContextsViewModel
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.theme.Dimens
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
@OptIn(ExperimentalMaterial3Api::class)
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
    // %AAB_CurrentActiveProfile — highlight the in-force profile in the list (owner: "seeing the active
    // profile here is useful"), mirroring Tasker's "Active Profile: …" readout.
    val activeProfile by com.tideo.autobrightness.app.runtime.LiveRuntimeState.activeProfile.collectAsStateWithLifecycle()
    // %AAB_ActiveContext — the winning rule's name, to emphasise the active rule in the Contexts modal.
    val activeContext by com.tideo.autobrightness.app.runtime.LiveRuntimeState.activeContext.collectAsStateWithLifecycle()
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

    // D-111 (owner): the Tasker information architecture — a STICKY Load / Save / Contexts action bar
    // pinned under the app bar (it stays put while the list scrolls), with the built-in/saved profiles
    // listed directly below it (no longer hidden behind a tab). Each top action opens its own modal,
    // the way the rule editor already does. Replaces the scrolling Profiles/Rules SegmentedButton.
    var showLoad by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showContexts by remember { mutableStateOf(false) }
    // G2R-F38 "Load Anyway" preview before applying a saved profile from the visible list.
    var previewProfile by remember { mutableStateOf<SavedProfile?>(null) }
    var showCurrentSettings by remember { mutableStateOf(false) }

    fun loadLegacy(entry: LegacyConfigEntry) {
        scope.launch {
            status = runCatching {
                handleLoad(manager.importFromDocument(entry.uri), "Loaded ${entry.name}") { imported ->
                    // G2R-F44: register the legacy profile under its file name so it's selectable as a
                    // context-rule target without a manual re-save, then apply it live.
                    val profileName = entry.name.removeSuffix(".json").removeSuffix(".JSON")
                    settingsVm.saveImportedProfile(profileName, imported)
                    settingsVm.replaceAll(imported)
                }
            }.getOrElse { loadError = it.message; "Load failed: ${it.message}" }
            status?.let(toast)
        }
    }

    SettingsScaffold(stringResource(R.string.title_profiles_contexts), { navController.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // STICKY action bar — pinned outside the scroll so Load/Save/Contexts stay reachable.
            ProfilesActionBar(
                onLoad = { showLoad = true },
                onSave = { showSave = true },
                onContexts = { showContexts = true },
            )
            // SCROLLING content: the resume banner + the saved/built-in profiles, directly visible.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(Dimens.fieldSpacing),
            ) {
                if (settings.contextOverride) {
                    ContextLockBanner { settingsVm.resumeContextAutomation(); toast("Context automation resumed") }
                }
                SectionHeader(stringResource(R.string.seg_profiles), divider = true)
                if (profiles.isEmpty()) EmptyState(stringResource(R.string.profiles_no_saved), testTag = "empty_profiles")
                profiles.forEach { entry ->
                    ProfileCard(
                        entry,
                        isActive = entry.name == activeProfile,
                        onApply = { previewProfile = entry },
                        onOverwrite = { name -> settingsVm.saveCurrentAs(name); toast("Overwrote: $name") },
                        onDelete = { name -> settingsVm.deleteProfile(name); toast("Deleted: $name") },
                    )
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }

    // --- Modals (each top action opens its own, like the rule editor) ---

    // Save: name the current settings as a new profile (gold changed-vs-default diff inside).
    if (showSave) {
        SaveProfileDialog(
            currentSettings = settings,
            onDismiss = { showSave = false },
            onConfirm = { name -> showSave = false; settingsVm.saveCurrentAs(name); toast("Saved profile: $name") },
        )
    }

    // Load & manage: import/legacy/restore/reset/export/view — the "load from elsewhere" + housekeeping.
    if (showLoad) {
        LoadManageDialog(
            legacyEntries = legacyEntries,
            onImport = { showLoad = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onChooseLegacyFolder = { folderLauncher.launch(null) },
            onLoadLegacy = { entry -> showLoad = false; loadLegacy(entry) },
            onRestoreFactory = { showLoad = false; settingsVm.restoreFactoryProfiles(); toast("Factory profiles restored") },
            onReset = { showLoad = false; settingsVm.resetDefaults(); toast("Reset to defaults") },
            onExport = { showLoad = false; exportLauncher.launch("tideo-profile.json") },
            onViewCurrent = { showLoad = false; showCurrentSettings = true },
            onDismiss = { showLoad = false },
        )
    }

    // Apply a saved profile picked from the visible list (preview then apply).
    previewProfile?.let { entry ->
        LoadProfileDialog(
            profile = entry,
            onDismiss = { previewProfile = null },
            onConfirm = { previewProfile = null; settingsVm.applyProfile(entry.name); toast("Applied profile: ${entry.name}") },
        )
    }

    if (showCurrentSettings) {
        CurrentSettingsDialog(settings = settings, onDismiss = { showCurrentSettings = false })
    }

    // An unreadable-profile failure (ProfileLoadResult.TotalFailure).
    loadError?.let { msg ->
        AlertDialog(
            onDismissRequest = { loadError = null },
            title = { Text(stringResource(R.string.profiles_load_failed_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { loadError = null }, modifier = Modifier.testTag("dismiss_load_error")) {
                    Text(stringResource(R.string.profiles_close))
                }
            },
        )
    }

    // Contexts: the full rule list + editor, in a full-screen modal (like the rule editor already is).
    if (showContexts) {
        ContextsModal(onClose = { showContexts = false }) {
            ContextRulesSection(
                rules = rules,
                profileNames = profileNames.ifEmpty { listOf("Default") },
                apps = apps,
                solarLabel = solarLabel,
                activeContext = activeContext,
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

/**
 * D-111: the pinned Load / Save / Contexts action bar (m3_audit B5 `ActionButtonBar` pattern), matched
 * to the Tasker original — each action carries a leading icon and the three are visually distinct:
 * **Load** filled teal (primary, high-emphasis), **Save** tonal grey, **Contexts** teal-outlined. Sits
 * outside the scroll so it stays reachable while the profile list scrolls; each opens its own modal.
 *
 * The compact `contentPadding` (8 dp vs the M3 default 24 dp) + single-line labels keep "Contexts" on
 * one line at equal thirds — the default padding squeezed it onto two lines.
 */
@Composable
private fun ProfilesActionBar(onLoad: () -> Unit, onSave: () -> Unit, onContexts: () -> Unit) {
    val pad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPaddingHorizontal, vertical = Dimens.sectionSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
    ) {
        Button(
            onClick = onLoad,
            modifier = Modifier.weight(1f).testTag("action_load"),
            contentPadding = pad,
        ) { ActionButtonContent(R.drawable.ic_folder, R.string.profiles_action_load) }
        FilledTonalButton(
            onClick = onSave,
            modifier = Modifier.weight(1f).testTag("action_save"),
            contentPadding = pad,
        ) { ActionButtonContent(R.drawable.ic_save, R.string.profiles_action_save) }
        OutlinedButton(
            onClick = onContexts,
            modifier = Modifier.weight(1f).testTag("action_contexts"),
            contentPadding = pad,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) { ActionButtonContent(R.drawable.ic_tune, R.string.profiles_action_contexts) }
    }
}

/** A leading 18 dp icon + single-line label, shared by the three action-bar buttons. */
@Composable
private fun ActionButtonContent(icon: Int, label: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(stringResource(label), maxLines = 1, softWrap = false)
}

/**
 * D-111: the "Load & manage" modal opened by the action bar's Load button — import a profile from a
 * file, (re)link the legacy Tasker folder and load its configs, plus the housekeeping actions
 * (restore factory / reset / export / view current). Saving lives in its own dialog (the Save button);
 * applying a saved profile is done from the visible list.
 */
@Composable
private fun LoadManageDialog(
    legacyEntries: List<LegacyConfigEntry>,
    onImport: () -> Unit,
    onChooseLegacyFolder: () -> Unit,
    onLoadLegacy: (LegacyConfigEntry) -> Unit,
    onRestoreFactory: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onViewCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_load_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import_profile")) {
                    Text(stringResource(R.string.profiles_import_file))
                }
                OutlinedButton(
                    onClick = onChooseLegacyFolder,
                    modifier = Modifier.fillMaxWidth().testTag("choose_legacy_folder"),
                ) {
                    Text(
                        stringResource(
                            if (legacyEntries.isEmpty()) R.string.profiles_link_legacy else R.string.profiles_relink_legacy,
                        ),
                    )
                }
                legacyEntries.forEach { entry ->
                    OutlinedButton(
                        onClick = { onLoadLegacy(entry) },
                        modifier = Modifier.fillMaxWidth().testTag("load_${entry.name}"),
                    ) { Text(stringResource(R.string.profiles_load_legacy_entry, entry.name)) }
                }
                OutlinedButton(onClick = onRestoreFactory, modifier = Modifier.fillMaxWidth().testTag("restore_factory")) {
                    Text(stringResource(R.string.profiles_restore_factory))
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().testTag("reset_defaults")) {
                    Text(stringResource(R.string.profiles_reset_defaults))
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().testTag("export_profile")) {
                    Text(stringResource(R.string.profiles_export))
                }
                OutlinedButton(onClick = onViewCurrent, modifier = Modifier.fillMaxWidth().testTag("view_current_settings")) {
                    Text(stringResource(R.string.profiles_view_current))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_close)) }
        },
    )
}

/**
 * D-111: a full-screen modal host for the context-rules editor opened by the action bar's Contexts
 * button — a top bar (title + close) over the scrolling [ContextRulesSection]. Full-screen because the
 * rule list + editor is too tall for an AlertDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextsModal(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.profiles_contexts_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.testTag("contexts_close")) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.profiles_close))
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(Dimens.fieldSpacing),
                ) {
                    content()
                }
            }
        }
    }
}
