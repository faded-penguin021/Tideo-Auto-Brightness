package com.tideo.autobrightness.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.ReactivityDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.fmtPercent
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster

/** Reactivity (Tasker AAB Reactivity Settings + Reactivity/Alpha graphs). Draft → Apply (S12.5b). */
@Composable
fun ReactivityScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val live by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    ReactivityContent(
        draft, committed, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        criticalError = criticalError,
        live = live,
        // G2R-F17: reset only this screen's reactivity fields to the task570 baseline (defaults).
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    thresholdDark = d.thresholdDark, thresholdDim = d.thresholdDim,
                    thresholdBright = d.thresholdBright, thresholdSteepness = d.thresholdSteepness,
                    thresholdMidpoint = d.thresholdMidpoint, thresholdDynamic = d.thresholdDynamic,
                    deltaFactor = d.deltaFactor, trustUnreliableSensor = d.trustUnreliableSensor,
                )
            }
            toast("Reset to defaults")
        },
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
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
    live: PipelineState = PipelineState(),
) {
    DraftSettingsScaffold("Reactivity", dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            ChartPlaceholder("ReactivityChart", "reactivity_chart")

            // Live glass-box readout: current dynamic threshold (as %, G2R-F56) + sensor dead zone (G2R-F7).
            ReactivityDiagnosticCardContent(live)

            // Labels + verbatim long-press help re-derived from extraction/scenes/reactivity_settings.md
            // (S12.6e, G2R-F19/F20/F21). The threshold fields are %aab_thresh*pc reactivity levels.
            SectionHeader("Smoothing thresholds")
            NumberSettingField(
                "Dark threshold", draft.thresholdDark, { onEdit { s -> s.copy(thresholdDark = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdDark, isInt = false,
                help = TaskerHelp.THRESH_DARK, testTag = "field_thresholdDark",
            )
            NumberSettingField(
                "Dim threshold", draft.thresholdDim, { onEdit { s -> s.copy(thresholdDim = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdDim, isInt = false,
                help = TaskerHelp.THRESH_DIM, testTag = "field_thresholdDim",
            )
            NumberSettingField(
                "Bright threshold", draft.thresholdBright, { onEdit { s -> s.copy(thresholdBright = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdBright, isInt = false,
                help = TaskerHelp.THRESH_BRIGHT, testTag = "field_thresholdBright",
            )
            NumberSettingField(
                "Curve slope", draft.thresholdSteepness, { onEdit { s -> s.copy(thresholdSteepness = it.toFloat()) } },
                epoch = epoch, committed = committed.thresholdSteepness, isInt = false,
                help = TaskerHelp.CURVE_SLOPE, testTag = "field_thresholdSteepness",
            )
            NumberSettingField(
                "Curve mid (log lux)", draft.thresholdMidpoint, { onEdit { s -> s.copy(thresholdMidpoint = it) } },
                epoch = epoch, committed = committed.thresholdMidpoint, isInt = false,
                help = TaskerHelp.CURVE_MID, testTag = "field_thresholdMidpoint",
            )
            // G2R-F59: the dynamic-threshold description must substitute the LIVE %AAB_ThreshDynamic
            // value (not the literal token) and sit behind the ⓘ reveal like the other help (use
            // help= not helper=). The live runtime value is a 0..1 fraction → shown as a percentage.
            NumberSettingField(
                "Dynamic threshold", draft.thresholdDynamic, { onEdit { s -> s.copy(thresholdDynamic = it.toInt()) } },
                epoch = epoch, committed = committed.thresholdDynamic,
                help = "Adaptive dead-band scaling. Current %AAB_ThreshDynamic: ${fmtPercent(live.threshDynamic)}.",
                testTag = "field_thresholdDynamic",
            )
            // G2R-F19/F20: "Delta factor" was mislabelled with a wrong help ("brightness only changes
            // once lux exceeds this"). It is the SENSOR-SMOOTHING factor (%AAB_DeltaFactor, Misc scene
            // "Smoothing Δ"): luxAlpha = 1 - exp(-deltaFactor·effectiveDelta) in BrightnessEngine — the
            // binding was already correct, only the label/help were wrong. Fixed to the verbatim help.
            NumberSettingField(
                "Smoothing Δ", draft.deltaFactor, { onEdit { s -> s.copy(deltaFactor = it.toFloat()) } },
                epoch = epoch, committed = committed.deltaFactor, isInt = false,
                help = TaskerHelp.DELTA_FACTOR, testTag = "field_deltaFactor",
            )

            SectionHeader("Override & trust")
            // task525/526 _OverrideToggle — DetectOverrides (Gate-1 G1-F2 deferral, surfaced in S12).
            SwitchSettingRow(
                "Use override detection", draft.detectOverrides,
                { onEdit { s -> s.copy(detectOverrides = it) } },
                help = TaskerHelp.DETECT_OVERRIDES,
                testTag = "switch_detectOverrides",
            )
            SwitchSettingRow(
                "Trust low-accuracy sensor", draft.trustUnreliableSensor,
                { onEdit { s -> s.copy(trustUnreliableSensor = it) } },
                help = TaskerHelp.TRUST_UNRELIABLE,
                testTag = "switch_trustUnreliableSensor",
            )
        }
    }
}
