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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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

/** D-070: unified Profiles & Contexts screen (S12.9f IA merge). Profiles UI above Context rules editor; no backend changes. */
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
    val clipboard = LocalClipboardManager.current
    // D-130: hold SSID help lead for no-Location case (Shizuku/root/DUMP alternatives)
    var ssidHelp by remember { mutableStateOf<String?>(null) }

    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val profiles by settingsVm.profiles.collectAsStateWithLifecycle()
    val activeProfile by com.tideo.autobrightness.app.runtime.LiveRuntimeState.activeProfile.collectAsStateWithLifecycle()
    val activeContext by com.tideo.autobrightness.app.runtime.LiveRuntimeState.activeContext.collectAsStateWithLifecycle()
    val manager = remember { ProfileImportExportManager(context.applicationContext) }
    var status by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Apply ProfileLoadResult: Success/LegacyFallback apply settings; TotalFailure shows error card
    fun handleLoad(
        result: ProfileLoadResult,
        okMessage: String,
        apply: (AabSettings) -> Unit,
    ): String = when (result) {
        is ProfileLoadResult.Success -> { apply(result.settings); loadError = null; okMessage }
        is ProfileLoadResult.LegacyFallback -> { apply(result.settings); loadError = null; okMessage }
        is ProfileLoadResult.TotalFailure -> {
            loadError = context.getString(R.string.profiles_unreadable)
            context.getString(R.string.toast_load_failed)
        }
        ProfileLoadResult.TooLarge -> {
            loadError = context.getString(R.string.profiles_too_large)
            context.getString(R.string.toast_load_failed)
        }
        ProfileLoadResult.ReadFailure -> {
            loadError = context.getString(R.string.profiles_read_failed)
            context.getString(R.string.toast_load_failed)
        }
    }

    // Persisted SAF permission for legacy configs tree, if any
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
            status = runCatching { manager.exportToDocument(uri, settings); context.getString(R.string.toast_exported) }
                .getOrElse { context.getString(R.string.toast_export_failed, it.message ?: "") }
            status?.let(toast)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching {
                handleLoad(manager.importFromDocument(uri), context.getString(R.string.toast_imported)) { settingsVm.replaceAll(it) }
            }.getOrElse { loadError = it.message; context.getString(R.string.toast_import_failed, it.message ?: "") }
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
            toast(R.string.toast_folder_linked)
        }
    }

    // --- Contexts side (ContextsViewModel) ---
    val rules by contextsVm.rules.collectAsStateWithLifecycle()
    val profileNames by contextsVm.profileNames.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) { apps = runCatching { contextsVm.installedApps() }.getOrDefault(emptyList()) }
    // G2R-F68: resolve today's sunrise/sunset for token labels
    var solarLabel by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { solarLabel = runCatching { contextsVm.solarTimes() }.getOrNull() }

    // D-111: sticky Load / Save / Contexts action bar (stays put while list scrolls)
    var showLoad by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showContexts by remember { mutableStateOf(false) }
    // G2R-F38: preview before applying saved profile
    var previewProfile by remember { mutableStateOf<SavedProfile?>(null) }
    var showCurrentSettings by remember { mutableStateOf(false) }

    fun loadLegacy(entry: LegacyConfigEntry) {
        scope.launch {
            status = runCatching {
                handleLoad(manager.importFromDocument(entry.uri), context.getString(R.string.toast_loaded_entry, entry.name)) { imported ->
                    // G2R-F44: register legacy profile by file name for rule targeting
                    val profileName = entry.name.removeSuffix(".json").removeSuffix(".JSON")
                    settingsVm.saveImportedProfile(profileName, imported)
                    settingsVm.replaceAll(imported)
                }
            }.getOrElse { loadError = it.message; context.getString(R.string.toast_load_failed_detail, it.message ?: "") }
            status?.let(toast)
        }
    }

    SettingsScaffold(stringResource(R.string.title_profiles_contexts), { navController.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Sticky action bar — pinned outside scroll
            ProfilesActionBar(
                onLoad = { showLoad = true },
                onSave = { showSave = true },
                onContexts = { showContexts = true },
            )
            // Scrolling content: resume banner + profiles
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(Dimens.fieldSpacing),
            ) {
                if (settings.contextOverride) {
                    ContextLockBanner { settingsVm.resumeContextAutomation(); toast(R.string.toast_context_resumed) }
                }
                SectionHeader(stringResource(R.string.seg_profiles), divider = true)
                if (profiles.isEmpty()) EmptyState(stringResource(R.string.profiles_no_saved), testTag = "empty_profiles")
                profiles.forEach { entry ->
                    ProfileCard(
                        entry,
                        isActive = entry.name == activeProfile,
                        onApply = { previewProfile = entry },
                        onOverwrite = { name -> settingsVm.saveCurrentAs(name); toast(R.string.toast_overwrote, name) },
                        onDelete = { name -> settingsVm.deleteProfile(name); toast(R.string.toast_deleted, name) },
                    )
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }

    // Each top action opens its own modal

    // Save: name current settings as new profile
    if (showSave) {
        SaveProfileDialog(
            currentSettings = settings,
            onDismiss = { showSave = false },
            onConfirm = { name -> showSave = false; settingsVm.saveCurrentAs(name); toast(R.string.toast_saved_profile, name) },
        )
    }

    // Load & manage: import/legacy/restore/reset/export/view
    if (showLoad) {
        LoadManageDialog(
            legacyEntries = legacyEntries,
            onImport = { showLoad = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onChooseLegacyFolder = { folderLauncher.launch(null) },
            onLoadLegacy = { entry -> showLoad = false; loadLegacy(entry) },
            onRestoreFactory = { showLoad = false; settingsVm.restoreFactoryProfiles(); toast(R.string.toast_factory_restored) },
            onReset = { showLoad = false; settingsVm.resetDefaults(); toast(R.string.toast_reset_defaults) },
            onExport = { showLoad = false; exportLauncher.launch("tideo-profile.json") },
            onViewCurrent = { showLoad = false; showCurrentSettings = true },
            onDismiss = { showLoad = false },
        )
    }

    // Preview and apply saved profile
    previewProfile?.let { entry ->
        LoadProfileDialog(
            profile = entry,
            onDismiss = { previewProfile = null },
            onConfirm = { previewProfile = null; settingsVm.applyProfile(entry.name); toast(R.string.toast_applied_profile, entry.name) },
        )
    }

    if (showCurrentSettings) {
        CurrentSettingsDialog(settings = settings, onDismiss = { showCurrentSettings = false })
    }

    // Show unreadable profile error
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

    // D-130: explain SSID alternatives when Location unavailable
    ssidHelp?.let { lead ->
        val dumpCmd = remember { contextsVm.dumpGrantCommand() }
        AlertDialog(
            onDismissRequest = { ssidHelp = null },
            title = { Text(stringResource(R.string.ssid_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.rowGap)) {
                    Text(lead)
                    Text(stringResource(R.string.ssid_help_options))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            dumpCmd,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(Dimens.rowGap).testTag("ssid_dump_command"),
                        )
                    }
                    Text(
                        stringResource(R.string.ssid_help_dump_security),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.ssid_help_regex_caveat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ssidHelp = null }) {
                    Text(stringResource(R.string.profiles_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(dumpCmd))
                        toast(context.getString(R.string.ssid_help_copied))
                    },
                    modifier = Modifier.testTag("copy_dump_command"),
                ) { Text(stringResource(R.string.ssid_help_copy_dump)) }
            },
        )
    }

    // Contexts: full rule list + editor in full-screen modal
    if (showContexts) {
        ContextsModal(onClose = { showContexts = false }) {
            ContextRulesSection(
                rules = rules,
                profileNames = profileNames.ifEmpty { listOf("Default") },
                apps = apps,
                solarLabel = solarLabel,
                activeContext = activeContext,
                onSave = { toast(R.string.toast_rule_saved); contextsVm.save(it) },
                onDelete = { contextsVm.delete(it); toast(R.string.toast_rule_deleted) },
                onUseCurrentSsid = { setSsid ->
                    scope.launch {
                        // G2R-F22: targeted message per failure mode; D-130: show help for no-Location
                        when (val result = contextsVm.currentSsid()) {
                            is SsidResult.Connected -> { setSsid(result.ssid); toast(R.string.toast_wifi_connected, result.ssid) }
                            SsidResult.NotOnWifi -> toast(R.string.toast_not_on_wifi)
                            SsidResult.NeedsLocationPermission ->
                                ssidHelp = context.getString(R.string.ssid_help_lead_permission)
                            SsidResult.LocationServicesOff ->
                                ssidHelp = context.getString(R.string.ssid_help_lead_services)
                            SsidResult.Unknown -> toast(R.string.toast_ssid_unknown)
                        }
                    }
                },
                onUseCurrentLocation = { setLatLon ->
                    // G2R-F22/F42/D-122: recheck grant + actively acquire fresh fix with targeted message
                    scope.launch {
                        toast(R.string.toast_acquiring_location)
                        when (val result = contextsVm.currentLocation()) {
                            is LocationResult.Available -> {
                                setLatLon(result.snapshot.latitude, result.snapshot.longitude)
                                toast(R.string.toast_location_fix, result.snapshot.latitude, result.snapshot.longitude)
                            }
                            LocationResult.NeedsPermission ->
                                toast(R.string.toast_location_needs_permission)
                            LocationResult.Unavailable ->
                                toast(R.string.toast_location_unavailable)
                        }
                    }
                },
                hasUsageAccess = contextsVm::hasUsageAccess,
                onRequestUsageAccess = {
                    toast(R.string.toast_grant_usage_hint)
                    runCatching { context.startActivity(contextsVm.usageAccessIntent()) }
                },
            )
        }
    }
}

/** D-111: pinned Load / Save / Contexts action bar (m3_audit B5 pattern). Each opens its own modal. */
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

@Composable
private fun ActionButtonContent(icon: Int, label: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(stringResource(label), maxLines = 1, softWrap = false)
}

/** D-111: "Load & manage" modal (import/legacy/housekeeping). Saving/applying are separate. */
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

/** D-111: full-screen modal host for context-rules editor (too tall for AlertDialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextsModal(onClose: () -> Unit, content: @Composable () -> Unit) {
    // D-118: edge-to-edge; bottom handled by trailing Spacer (D-098)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
                    // Clearance for gesture pill / 3-button bar (D-098/D-118)
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}
