package com.tideo.autobrightness.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow

/** Reactivity (Tasker AAB Reactivity Settings + Reactivity/Alpha graphs). */
@Composable
fun ReactivityScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ReactivityContent(settings, onBack = { navController.popBackStack() }, onUpdate = vm::update)
}

@Composable
fun ReactivityContent(
    settings: AabSettings,
    onBack: () -> Unit,
    onUpdate: ((AabSettings) -> AabSettings) -> Unit,
) {
    SettingsScaffold("Reactivity", onBack) { padding ->
        SettingsColumn(padding) {
            ChartPlaceholder("ReactivityChart", "reactivity_chart")

            SectionHeader("Smoothing thresholds")
            NumberSettingField(
                "Threshold dark", settings.thresholdDark, { onUpdate { s -> s.copy(thresholdDark = it.toFloat()) } },
                isInt = false, helper = "Reactivity in complete darkness.", testTag = "field_thresholdDark",
            )
            NumberSettingField(
                "Threshold dim", settings.thresholdDim, { onUpdate { s -> s.copy(thresholdDim = it.toFloat()) } },
                isInt = false, helper = "Baseline reactivity for most situations.", testTag = "field_thresholdDim",
            )
            NumberSettingField(
                "Threshold bright", settings.thresholdBright, { onUpdate { s -> s.copy(thresholdBright = it.toFloat()) } },
                isInt = false, helper = "Reactivity in bright outdoor light.", testTag = "field_thresholdBright",
            )
            NumberSettingField(
                "Threshold steepness", settings.thresholdSteepness, { onUpdate { s -> s.copy(thresholdSteepness = it.toFloat()) } },
                isInt = false, helper = "How quickly reactivity changes across light levels.", testTag = "field_thresholdSteepness",
            )
            NumberSettingField(
                "Threshold midpoint (log lux)", settings.thresholdMidpoint, { onUpdate { s -> s.copy(thresholdMidpoint = it) } },
                isInt = false, helper = "Log-lux level where the system switches behavior.", testTag = "field_thresholdMidpoint",
            )
            NumberSettingField(
                "Dynamic threshold", settings.thresholdDynamic, { onUpdate { s -> s.copy(thresholdDynamic = it.toInt()) } },
                helper = "Adaptive dead-band scaling.", testTag = "field_thresholdDynamic",
            )
            NumberSettingField(
                "Delta factor", settings.deltaFactor, { onUpdate { s -> s.copy(deltaFactor = it.toFloat()) } },
                isInt = false, helper = "Brightness only changes once the lux change exceeds this.", testTag = "field_deltaFactor",
            )

            SectionHeader("Override & trust")
            // task525/526 _OverrideToggle — surfaces the DetectOverrides toggle deferred from Gate 1
            // (D-041 F2). When on, dragging the system slider mid-run pauses the pipeline.
            SwitchSettingRow(
                "Detect manual overrides", settings.detectOverrides,
                { onUpdate { s -> s.copy(detectOverrides = it) } },
                helper = "Pause when you change brightness manually; resume automatically.",
                testTag = "switch_detectOverrides",
            )
            SwitchSettingRow(
                "Trust unreliable sensor", settings.trustUnreliableSensor,
                { onUpdate { s -> s.copy(trustUnreliableSensor = it) } },
                helper = "Enable only if auto-brightness seems unresponsive on your device.",
                testTag = "switch_trustUnreliableSensor",
            )
        }
    }
}
