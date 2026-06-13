package com.tideo.autobrightness.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow

/** Reactivity (Tasker AAB Reactivity Settings + Reactivity/Alpha graphs). Draft → Apply (S12.5b). */
@Composable
fun ReactivityScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    ReactivityContent(
        draft, committed, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun ReactivityContent(
    draft: AabSettings,
    committed: AabSettings,
    epoch: Int,
    dirty: Boolean,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    DraftSettingsScaffold("Reactivity", dirty, onApply, onDiscard, onBack) { padding ->
        SettingsColumn(padding) {
            ChartPlaceholder("ReactivityChart", "reactivity_chart")

            SectionHeader("Smoothing thresholds")
            NumberSettingField(
                "Threshold dark", draft.thresholdDark, { onEdit { s -> s.copy(thresholdDark = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdDark, isInt = false,
                helper = "Reactivity in complete darkness.", testTag = "field_thresholdDark",
            )
            NumberSettingField(
                "Threshold dim", draft.thresholdDim, { onEdit { s -> s.copy(thresholdDim = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdDim, isInt = false,
                helper = "Baseline reactivity for most situations.", testTag = "field_thresholdDim",
            )
            NumberSettingField(
                "Threshold bright", draft.thresholdBright, { onEdit { s -> s.copy(thresholdBright = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdBright, isInt = false,
                helper = "Reactivity in bright outdoor light.", testTag = "field_thresholdBright",
            )
            NumberSettingField(
                "Threshold steepness", draft.thresholdSteepness, { onEdit { s -> s.copy(thresholdSteepness = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdSteepness, isInt = false,
                helper = "How quickly reactivity changes across light levels.", testTag = "field_thresholdSteepness",
            )
            NumberSettingField(
                "Threshold midpoint (log lux)", draft.thresholdMidpoint, { onEdit { s -> s.copy(thresholdMidpoint = it) } },
                epoch = epoch, committed = committed.thresholdMidpoint, isInt = false,
                helper = "Log-lux level where the system switches behavior.", testTag = "field_thresholdMidpoint",
            )
            NumberSettingField(
                "Dynamic threshold", draft.thresholdDynamic, { onEdit { s -> s.copy(thresholdDynamic = it.toInt()) } },
                epoch = epoch, committed = committed.thresholdDynamic,
                helper = "Adaptive dead-band scaling.", testTag = "field_thresholdDynamic",
            )
            NumberSettingField(
                "Delta factor", draft.deltaFactor, { onEdit { s -> s.copy(deltaFactor = it.toFloat()) } },
                epoch = epoch, committed = committed.deltaFactor, isInt = false,
                helper = "Brightness only changes once the lux change exceeds this.", testTag = "field_deltaFactor",
            )

            SectionHeader("Override & trust")
            // task525/526 _OverrideToggle — DetectOverrides (Gate-1 G1-F2 deferral, surfaced in S12).
            SwitchSettingRow(
                "Detect manual overrides", draft.detectOverrides,
                { onEdit { s -> s.copy(detectOverrides = it) } },
                helper = "Pause when you change brightness manually; resume automatically.",
                testTag = "switch_detectOverrides",
            )
            SwitchSettingRow(
                "Trust unreliable sensor", draft.trustUnreliableSensor,
                { onEdit { s -> s.copy(trustUnreliableSensor = it) } },
                helper = "Enable only if auto-brightness seems unresponsive on your device.",
                testTag = "switch_trustUnreliableSensor",
            )
        }
    }
}
