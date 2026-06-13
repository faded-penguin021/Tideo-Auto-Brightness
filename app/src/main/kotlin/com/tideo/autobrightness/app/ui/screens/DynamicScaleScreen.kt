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

/** Dynamic Scale (Tasker AAB Experiment Settings + Experiment/Taper graphs). */
@Composable
fun DynamicScaleScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    DynamicScaleContent(settings, onBack = { navController.popBackStack() }, onUpdate = vm::update)
}

@Composable
fun DynamicScaleContent(
    settings: AabSettings,
    onBack: () -> Unit,
    onUpdate: ((AabSettings) -> AabSettings) -> Unit,
) {
    SettingsScaffold("Dynamic Scale", onBack) { padding ->
        SettingsColumn(padding) {
            ChartPlaceholder("ExperimentChart / TaperChart", "dynamic_scale_chart")

            SectionHeader("Circadian scaling")
            SwitchSettingRow(
                "Enable dynamic scaling", settings.scalingEnabled,
                { onUpdate { s -> s.copy(scalingEnabled = it) } },
                helper = "Shift the whole curve across the day using sun position.",
                testTag = "switch_scalingEnabled",
            )
            NumberSettingField(
                "Scale spread", settings.scaleSpread, { onUpdate { s -> s.copy(scaleSpread = it.toInt()) } },
                helper = "How wide the scale shifts over the day (%).", testTag = "field_scaleSpread",
            )
            NumberSettingField(
                "Scale steepness", settings.scaleSteepness, { onUpdate { s -> s.copy(scaleSteepness = it.toInt()) } },
                helper = "Sharpness of the day/night transition.", testTag = "field_scaleSteepness",
            )
            NumberSettingField(
                "Transition factor", settings.scaleTransitionFactor, { onUpdate { s -> s.copy(scaleTransitionFactor = it.toFloat()) } },
                isInt = false, helper = "Duration of the dawn/dusk transition.", testTag = "field_scaleTransitionFactor",
            )
            // task517/674: large transition factors make the graph non-sensical.
            if (settings.scaleTransitionFactor > 0.5f) {
                ErrorBanner("Transition factor > 0.5 may produce a non-sensical curve.", "error_scaleTransitionFactor")
            }

            SectionHeader("Compression taper")
            NumberSettingField(
                "Taper midpoint", settings.scaleTaperMidpoint, { onUpdate { s -> s.copy(scaleTaperMidpoint = it.toInt()) } },
                helper = "Brightness level where compression centres (130–240).", testTag = "field_scaleTaperMidpoint",
            )
            // task689: taper midpoint cannot exceed current maximum brightness.
            if (settings.scaleTaperMidpoint > settings.maxBrightness) {
                ErrorBanner("Taper midpoint cannot exceed maximum brightness.", "error_scaleTaperMidpoint")
            }
            NumberSettingField(
                "Taper steepness", settings.scaleTaperSteepness, { onUpdate { s -> s.copy(scaleTaperSteepness = it.toFloat()) } },
                isInt = false, helper = "Slope of the dynamic-range compression.", testTag = "field_scaleTaperSteepness",
            )
        }
    }
}
