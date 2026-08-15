package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.validate
import com.tideo.autobrightness.app.state.DisplayTogglesViewModel
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.state.PrivilegedDisplayUiState
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.DraftApplyBar
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.theme.Dimens
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.display.SecureDisplayController
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.Tier
import kotlin.math.roundToInt

/**
 * Privileged Display (D-149/D-151/D-152): AOSP display settings via draft→Apply.
 * Self-guards below ELEVATED tier with grant card. One set of controls, no "device now" duplicates.
 */
@Composable
fun PrivilegedDisplayScreen(
    navController: NavHostController,
    vm: DisplayTogglesViewModel = viewModel(),
    draftVm: DraftSettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by draftVm.draft.collectAsStateWithLifecycle()
    val dirty by draftVm.dirty.collectAsStateWithLifecycle()
    val committed by draftVm.committed.collectAsStateWithLifecycle()
    val deviceSnapshot by vm.deviceSnapshot.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val toast = rememberToaster()

    // DB-034: show what the device actually reads, never over uncommitted edits. DB-040: keyed on
    // everything the decision reads — the snapshot alone missed the gate RE-OPENING (Discard, or the
    // post-seed epoch), which left the screen stale exactly as the original `dirty` gate did.
    val epoch by draftVm.epoch.collectAsStateWithLifecycle()
    LaunchedEffect(deviceSnapshot, draft, committed, epoch) {
        deviceSnapshot?.let { draftVm.mergeDeviceReadBack(it) }
    }

    // Re-probe on foreground return to catch external changes (adb grant, Shizuku, Night Light schedule).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PrivilegedDisplayContent(
        state = state,
        onBack = { navController.popBackStack() },
        onCopyAdb = {
            clipboard.setText(AnnotatedString(state.adbCommand))
            toast(R.string.pd_adb_copied)
        },
        onRequestShizuku = vm::requestShizukuGrant,
        onTryRoot = vm::tryRootGrant,
        draft = draft,
        draftDirty = dirty,
        onEditDraft = draftVm::edit,
        onApplyDraft = {
            // D-152: service off → write directly; service on → reapply via coordinator.
            if (!committed.serviceEnabled) vm.applyNow(draft.validate())
            draftVm.apply()
        },
        onDiscardDraft = draftVm::discard,
    )
}

@Composable
fun PrivilegedDisplayContent(
    state: PrivilegedDisplayUiState,
    onBack: () -> Unit,
    onCopyAdb: () -> Unit = {},
    onRequestShizuku: () -> Unit = {},
    onTryRoot: () -> Unit = {},
    draft: AabSettings = AabSettings(),
    draftDirty: Boolean = false,
    onEditDraft: ((AabSettings) -> AabSettings) -> Unit = {},
    onApplyDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
) {
    // AOSP-keys / OEM-variance note behind ⓘ in top bar (always reachable).
    var showInfo by remember { mutableStateOf(false) }
    SettingsScaffold(
        title = stringResource(R.string.title_privileged_display),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { showInfo = true },
                modifier = Modifier.testTag("pd_info_action"),
            ) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.pd_info_title))
            }
        },
    ) { padding ->
        SettingsColumn(padding) {
            if (state.tier != Tier.ELEVATED) {
                GrantChannelsCard(state, onCopyAdb, onRequestShizuku, onTryRoot)
            } else {
                if (state.writeFailed) {
                    Text(
                        stringResource(R.string.pd_write_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("pd_write_error"),
                    )
                }
                Text(
                    stringResource(R.string.pd_profile_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pd_profile_intro"),
                )

                if (state.nightLightAvailable) {
                    SectionHeader(stringResource(R.string.pd_section_night_light), divider = true)
                    AabCard {
                        SwitchSettingRow(
                            stringResource(R.string.pd_night_light_switch), draft.nightLightEnabled,
                            { on -> onEditDraft { it.copy(nightLightEnabled = on) } },
                            testTag = "switch_nightLight",
                        )
                        if (state.nightLightAutoMode != NightLightAutoMode.MANUAL) {
                            Text(
                                stringResource(R.string.pd_night_light_schedule_caveat),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("pd_schedule_caveat"),
                            )
                        }
                        NightLightTemperatureSlider(
                            kelvin = draft.nightLightTemperature,
                            onCommit = { k -> onEditDraft { it.copy(nightLightTemperature = k) } },
                        )
                        if (draft.nightLightTemperature != null) {
                            TextButton(
                                onClick = { onEditDraft { it.copy(nightLightTemperature = null) } },
                                modifier = Modifier.testTag("pd_temp_clear"),
                            ) { Text(stringResource(R.string.pd_profile_temp_clear)) }
                        }
                        SwitchSettingRow(
                            stringResource(R.string.pd_night_light_circadian), draft.nightLightCircadianEnabled,
                            { on -> onEditDraft { it.copy(nightLightCircadianEnabled = on) } },
                            help = R.string.pd_night_light_circadian_help,
                            testTag = "switch_nightLightCircadian",
                        )
                    }
                }

                SectionHeader(stringResource(R.string.pd_section_color), divider = true)
                AabCard {
                    DaltonizerPicker(
                        selected = DaltonizerMode.entries.firstOrNull { it.name == draft.daltonizerMode }
                            ?: DaltonizerMode.OFF,
                        onSelect = { mode -> onEditDraft { it.copy(daltonizerMode = mode.name) } },
                    )
                    SwitchSettingRow(
                        stringResource(R.string.pd_inversion), draft.inversionEnabled,
                        { on -> onEditDraft { it.copy(inversionEnabled = on) } },
                        testTag = "switch_inversion",
                    )
                }

                SectionHeader(stringResource(R.string.pd_section_screen), divider = true)
                AabCard {
                    if (state.alwaysOnDisplayAvailable) {
                        SwitchSettingRow(
                            stringResource(R.string.pd_always_on), draft.alwaysOnDisplayEnabled,
                            { on -> onEditDraft { it.copy(alwaysOnDisplayEnabled = on) } },
                            testTag = "switch_alwaysOn",
                        )
                    }
                    SwitchSettingRow(
                        stringResource(R.string.pd_stay_awake), draft.stayAwakeChargingEnabled,
                        { on -> onEditDraft { it.copy(stayAwakeChargingEnabled = on) } },
                        help = R.string.pd_stay_awake_help, testTag = "switch_stayAwake",
                    )
                }

                if (state.hdrAvailable) {
                    SectionHeader(stringResource(R.string.pd_section_experimental), divider = true)
                    AabCard {
                        SwitchSettingRow(
                            stringResource(R.string.pd_hdr_force_sdr), draft.hdrForceSdrEnabled,
                            { on -> onEditDraft { it.copy(hdrForceSdrEnabled = on) } },
                            help = R.string.pd_hdr_help, testTag = "switch_hdrForceSdr",
                        )
                    }
                } else if (state.hdrPreferenceCustom) {
                    SectionHeader(stringResource(R.string.pd_section_experimental), divider = true)
                    AabCard {
                        Text(
                            stringResource(R.string.pd_hdr_custom_preserved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("pd_hdr_custom_preserved"),
                        )
                    }
                }

                DraftApplyBar(dirty = draftDirty, onApply = onApplyDraft, onDiscard = onDiscardDraft)
            }
            Spacer(Modifier.height(Dimens.sectionSpacing))
        }
    }

    // Info dialog: what toggles write + OEM-variance caveat (D-048, documented for user).
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.pd_info_title)) },
            text = {
                Text(
                    stringResource(R.string.pd_info_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfo = false },
                    modifier = Modifier.testTag("pd_info_dismiss"),
                ) { Text(stringResource(R.string.action_ok)) }
            },
            modifier = Modifier.testTag("pd_info_dialog"),
        )
    }
}

/** Below-ELEVATED guard card: all three grant channels, mirroring Onboarding's ElevatedStepCard. */
@Composable
private fun GrantChannelsCard(
    state: PrivilegedDisplayUiState,
    onCopyAdb: () -> Unit,
    onRequestShizuku: () -> Unit,
    onTryRoot: () -> Unit,
) {
    AabCard(modifier = Modifier.testTag("pd_grant_card")) {
        Text(stringResource(R.string.pd_grant_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pd_grant_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.onboarding_adb_label), style = MaterialTheme.typography.labelMedium)
        Text(state.adbCommand, style = MaterialTheme.typography.bodySmall)
        if (state.shizukuAvailability == ShizukuAvailability.INSTALLED_NOT_RUNNING) {
            Text(
                stringResource(R.string.onboarding_shizuku_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("pd_shizuku_start_prompt"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap)) {
            OutlinedButton(onClick = onCopyAdb, modifier = Modifier.testTag("pd_copy_adb")) {
                Text(stringResource(R.string.onboarding_copy_command))
            }
            if (state.shizukuAvailability == ShizukuAvailability.RUNNING) {
                OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.testTag("pd_grant_shizuku")) {
                    Text(stringResource(R.string.onboarding_use_shizuku))
                }
            }
            TextButton(onClick = onTryRoot, modifier = Modifier.testTag("pd_grant_root")) {
                Text(stringResource(R.string.onboarding_try_root))
            }
        }
        state.grantMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/**
 * Kelvin slider for `night_display_color_temperature`. Commits on drag END; null = device default.
 * AOSP bounds 2596–4082, default 2850; OEMs may vary (ColorDisplayService clamps).
 */
@Composable
private fun NightLightTemperatureSlider(kelvin: Int?, onCommit: (Int) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag?.roundToInt() ?: kelvin
    Column {
        Text(
            if (shown != null) stringResource(R.string.pd_night_light_temp_label, shown)
            else stringResource(R.string.pd_night_light_temp_default),
            style = MaterialTheme.typography.bodyLarge,
        )
        // D-156: the Kelvin label above is a sibling Text (does not merge onto the Slider node), so
        // the slider carries its own contentDescription for TalkBack.
        val tempLabel = stringResource(R.string.a11y_night_light_temp)
        Slider(
            value = drag ?: (kelvin ?: AOSP_NIGHT_LIGHT_DEFAULT_K).toFloat(),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.roundToInt()?.let(onCommit)
                drag = null
            },
            valueRange = AOSP_NIGHT_LIGHT_MIN_K.toFloat()..AOSP_NIGHT_LIGHT_MAX_K.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag("slider_nightLightTemp")
                .semantics { contentDescription = tempLabel },
        )
        Text(
            stringResource(R.string.pd_night_light_temp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow wraps on narrow screens
@Composable
private fun DaltonizerPicker(selected: DaltonizerMode, onSelect: (DaltonizerMode) -> Unit) {
    Column {
        Text(stringResource(R.string.pd_daltonizer_label), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            DaltonizerMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(stringResource(mode.labelRes())) },
                    modifier = Modifier.testTag("daltonizer_${mode.name.lowercase()}"),
                )
            }
        }
    }
}

private fun DaltonizerMode.labelRes(): Int = when (this) {
    DaltonizerMode.OFF -> R.string.pd_daltonizer_off
    DaltonizerMode.GRAYSCALE -> R.string.pd_daltonizer_grayscale
    DaltonizerMode.PROTANOMALY -> R.string.pd_daltonizer_protan
    DaltonizerMode.DEUTERANOMALY -> R.string.pd_daltonizer_deutan
    DaltonizerMode.TRITANOMALY -> R.string.pd_daltonizer_tritan
}

// AOSP frameworks/base config; shared with D-154 circadian ramp via SecureDisplayController.
private const val AOSP_NIGHT_LIGHT_MIN_K = SecureDisplayController.NIGHT_LIGHT_MIN_K
private const val AOSP_NIGHT_LIGHT_MAX_K = SecureDisplayController.NIGHT_LIGHT_MAX_K
private const val AOSP_NIGHT_LIGHT_DEFAULT_K = SecureDisplayController.NIGHT_LIGHT_DEFAULT_K
