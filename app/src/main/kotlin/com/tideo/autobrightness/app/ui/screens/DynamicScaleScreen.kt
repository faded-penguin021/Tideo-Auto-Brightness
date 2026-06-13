package com.tideo.autobrightness.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow

/** Dynamic Scale (Tasker AAB Experiment Settings + Experiment/Taper graphs). Draft → Apply (S12.5b). */
@Composable
fun DynamicScaleScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    DynamicScaleContent(
        draft, committed, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun DynamicScaleContent(
    draft: AabSettings,
    committed: AabSettings,
    epoch: Int,
    dirty: Boolean,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    DraftSettingsScaffold("Dynamic Scale", dirty, onApply, onDiscard, onBack) { padding ->
        SettingsColumn(padding) {
            ChartPlaceholder("ExperimentChart / TaperChart", "dynamic_scale_chart")

            SectionHeader("Circadian scaling")
            SwitchSettingRow(
                "Enable dynamic scaling", draft.scalingEnabled,
                { onEdit { s -> s.copy(scalingEnabled = it) } },
                helper = "Shift the whole curve across the day using sun position.",
                testTag = "switch_scalingEnabled",
            )
            NumberSettingField(
                "Scale spread", draft.scaleSpread, { onEdit { s -> s.copy(scaleSpread = it.toInt()) } },
                epoch = epoch, committed = committed.scaleSpread,
                helper = "How wide the scale shifts over the day (%).", testTag = "field_scaleSpread",
            )
            NumberSettingField(
                "Scale steepness", draft.scaleSteepness, { onEdit { s -> s.copy(scaleSteepness = it.toInt()) } },
                epoch = epoch, committed = committed.scaleSteepness,
                helper = "Sharpness of the day/night transition.", testTag = "field_scaleSteepness",
            )
            NumberSettingField(
                "Transition factor", draft.scaleTransitionFactor, { onEdit { s -> s.copy(scaleTransitionFactor = it.toFloat()) } },
                epoch = epoch, committed = committed.scaleTransitionFactor, isInt = false,
                helper = "Duration of the dawn/dusk transition.", testTag = "field_scaleTransitionFactor",
            )
            // task517/674: large transition factors make the graph non-sensical.
            if (draft.scaleTransitionFactor > 0.5f) {
                ErrorBanner("Transition factor > 0.5 may produce a non-sensical curve.", "error_scaleTransitionFactor")
            }

            SectionHeader("Compression taper")
            // Tasker Experiment slider: taper midpoint 130–240 (experiment_settings.md elements26, G2-F13).
            IntSliderSettingField(
                "Taper midpoint", draft.scaleTaperMidpoint, 130..240,
                { onEdit { s -> s.copy(scaleTaperMidpoint = it) } },
                committed = committed.scaleTaperMidpoint,
                helper = "Brightness level where compression centres.", testTag = "slider_scaleTaperMidpoint",
            )
            // task689: taper midpoint cannot exceed current maximum brightness.
            if (draft.scaleTaperMidpoint > draft.maxBrightness) {
                ErrorBanner("Taper midpoint cannot exceed maximum brightness.", "error_scaleTaperMidpoint")
            }
            NumberSettingField(
                "Taper steepness", draft.scaleTaperSteepness, { onEdit { s -> s.copy(scaleTaperSteepness = it.toFloat()) } },
                epoch = epoch, committed = committed.scaleTaperSteepness, isInt = false,
                helper = "Slope of the dynamic-range compression.", testTag = "field_scaleTaperSteepness",
            )
        }
    }
}
