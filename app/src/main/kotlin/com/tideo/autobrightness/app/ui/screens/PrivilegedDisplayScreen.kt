package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.DisplayRule
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.ContextsViewModel
import com.tideo.autobrightness.app.state.DisplayTogglesViewModel
import com.tideo.autobrightness.app.state.PrivilegedDisplayUiState
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.theme.Dimens
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.Tier
import kotlin.math.roundToInt

/**
 * Privileged Display (rebuild-only feature, D-149 — `plans/privileged-display.md` Segment 2): manual
 * toggles for the AOSP display settings that `WRITE_SECURE_SETTINGS` unlocks (Night Light,
 * daltonizer/inversion, AOD, stay-awake-charging, experimental HDR force-SDR). Reached from the
 * Menu's tier-gated "Privileged" group; the route itself is always registered, so the screen
 * self-guards: below ELEVATED it renders a grant card offering all three grant channels (adb copy /
 * Shizuku one-tap / root), mirroring Onboarding's ELEVATED step.
 */
@Composable
fun PrivilegedDisplayScreen(
    navController: NavHostController,
    vm: DisplayTogglesViewModel = viewModel(),
    // Reused for the schedule editor's app picker + usage-access plumbing (same VM the Contexts
    // rule editor uses — the installed-apps query and grant intent are identical needs).
    contextsVm: ContextsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheduleRules by vm.scheduleRules.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val toast = rememberToaster()
    val context = LocalContext.current

    // Launchable apps for the schedule editor's optional app trigger; only worth querying once
    // the toggles (and thus the Schedules section) can actually render.
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(state.tier) {
        if (state.tier == Tier.ELEVATED && apps.isEmpty()) {
            apps = runCatching { contextsVm.installedApps() }.getOrDefault(emptyList())
        }
    }

    // Re-probe on every return to the foreground: an adb grant, a Shizuku grant, or a change made
    // in the system Settings app must all show up without leaving the screen (read-back display).
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
        onSetNightLight = vm::setNightLight,
        onSetNightLightTemperature = vm::setNightLightTemperature,
        onSetDaltonizer = vm::setDaltonizer,
        onSetInversion = vm::setInversion,
        onSetAlwaysOn = vm::setAlwaysOnDisplay,
        onSetStayAwake = vm::setStayAwakePlugged,
        onSetHdrForceSdr = vm::setHdrForceSdr,
        scheduleRules = scheduleRules,
        scheduleApps = apps,
        onSaveRule = { vm.saveRule(it); toast(R.string.toast_rule_saved) },
        onDeleteRule = { vm.deleteRule(it); toast(R.string.toast_rule_deleted) },
        hasUsageAccess = contextsVm::hasUsageAccess,
        onRequestUsageAccess = {
            toast(R.string.toast_grant_usage_hint)
            runCatching { context.startActivity(contextsVm.usageAccessIntent()) }
        },
    )
}

@Composable
fun PrivilegedDisplayContent(
    state: PrivilegedDisplayUiState,
    onBack: () -> Unit,
    onCopyAdb: () -> Unit = {},
    onRequestShizuku: () -> Unit = {},
    onTryRoot: () -> Unit = {},
    onSetNightLight: (Boolean) -> Unit = {},
    onSetNightLightTemperature: (Int) -> Unit = {},
    onSetDaltonizer: (DaltonizerMode) -> Unit = {},
    onSetInversion: (Boolean) -> Unit = {},
    onSetAlwaysOn: (Boolean) -> Unit = {},
    onSetStayAwake: (Boolean) -> Unit = {},
    onSetHdrForceSdr: (Boolean) -> Unit = {},
    scheduleRules: List<DisplayRule> = emptyList(),
    scheduleApps: List<AppEntry> = emptyList(),
    onSaveRule: (DisplayRule) -> Unit = {},
    onDeleteRule: (String) -> Unit = {},
    hasUsageAccess: () -> Boolean = { true },
    onRequestUsageAccess: () -> Unit = {},
) {
    SettingsScaffold(stringResource(R.string.title_privileged_display), onBack) { padding ->
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

                SectionHeader(stringResource(R.string.pd_section_night_light), divider = true)
                AabCard {
                    SwitchSettingRow(
                        stringResource(R.string.pd_night_light_switch), state.nightLight,
                        onSetNightLight, testTag = "switch_nightLight",
                    )
                    if (state.nightLightAutoMode != NightLightAutoMode.MANUAL) {
                        Text(
                            stringResource(R.string.pd_night_light_schedule_caveat),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("pd_schedule_caveat"),
                        )
                    }
                    NightLightTemperatureSlider(state.nightLightTemperature, onSetNightLightTemperature)
                }

                SectionHeader(stringResource(R.string.pd_section_color), divider = true)
                AabCard {
                    DaltonizerPicker(state.daltonizer, onSetDaltonizer)
                    SwitchSettingRow(
                        stringResource(R.string.pd_inversion), state.inversion,
                        onSetInversion, testTag = "switch_inversion",
                    )
                }

                SectionHeader(stringResource(R.string.pd_section_screen), divider = true)
                AabCard {
                    SwitchSettingRow(
                        stringResource(R.string.pd_always_on), state.alwaysOnDisplay,
                        onSetAlwaysOn, testTag = "switch_alwaysOn",
                    )
                    SwitchSettingRow(
                        stringResource(R.string.pd_stay_awake), state.stayAwakePlugged,
                        onSetStayAwake, help = R.string.pd_stay_awake_help, testTag = "switch_stayAwake",
                    )
                }

                if (state.hdrAvailable) {
                    SectionHeader(stringResource(R.string.pd_section_experimental), divider = true)
                    AabCard {
                        SwitchSettingRow(
                            stringResource(R.string.pd_hdr_force_sdr), state.hdrForceSdr,
                            onSetHdrForceSdr, help = R.string.pd_hdr_help, testTag = "switch_hdrForceSdr",
                        )
                    }
                }

                // Schedule rules (D-150, Segment 4): ELEVATED-only like the toggles — the runtime
                // coordinator is inert below ELEVATED, so offering the editor there would be a lie.
                SectionHeader(stringResource(R.string.pd_section_schedules), divider = true)
                DisplaySchedulesSection(
                    rules = scheduleRules,
                    apps = scheduleApps,
                    onSave = onSaveRule,
                    onDelete = onDeleteRule,
                    hasUsageAccess = hasUsageAccess,
                    onRequestUsageAccess = onRequestUsageAccess,
                )
            }

            // Info card (always shown): what these toggles write + the OEM-variance caveat (D-048
            // policy: variance is documented for the user, never branched in code).
            AabCard(modifier = Modifier.testTag("pd_info_card")) {
                Text(
                    stringResource(R.string.pd_info_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Dimens.sectionSpacing))
        }
    }
}

/**
 * The below-ELEVATED guard card: all three grant channels via the existing PrivilegeManager
 * affordances, mirroring Onboarding's `ElevatedStepCard` (ADB is ALWAYS offered; Shizuku one-tap
 * only with a live binder; installed-but-not-running prompts to start the app first).
 */
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
 * Kelvin slider for `night_display_color_temperature`. AOSP bounds/default verified 2026-07-03
 * (frameworks/base config.xml): min 2596, max 4082, default 2850 — OEMs may narrow/widen their real
 * range in their framework config (ColorDisplayService clamps what it applies; documented variance,
 * not branched). Commits on drag END (one settings write per gesture, not per pixel); null = never
 * set → the label says "device default" and the thumb parks at the AOSP default until first commit.
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
        Slider(
            value = drag ?: (kelvin ?: AOSP_NIGHT_LIGHT_DEFAULT_K).toFloat(),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.roundToInt()?.let(onCommit)
                drag = null
            },
            valueRange = AOSP_NIGHT_LIGHT_MIN_K.toFloat()..AOSP_NIGHT_LIGHT_MAX_K.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag("slider_nightLightTemp"),
        )
        Text(
            stringResource(R.string.pd_night_light_temp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One chip per daltonizer mode (5 incl. Off — a FlowRow so they wrap on narrow screens). */
@OptIn(ExperimentalLayoutApi::class) // FlowRow (chip row that wraps on narrow screens)
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

// AOSP frameworks/base config_nightDisplayColorTemperature{Min,Default,Max} (verified 2026-07-03).
private const val AOSP_NIGHT_LIGHT_MIN_K = 2596
private const val AOSP_NIGHT_LIGHT_MAX_K = 4082
private const val AOSP_NIGHT_LIGHT_DEFAULT_K = 2850
