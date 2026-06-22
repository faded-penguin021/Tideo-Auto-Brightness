package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.GraphSettingsGroup
import com.tideo.autobrightness.app.ui.components.ReactivityDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.graph.AlphaResponseChart
import com.tideo.autobrightness.app.ui.graph.ReactivityChart
import com.tideo.autobrightness.app.settings.toThresholdConfig

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
                    thresholdMidpoint = d.thresholdMidpoint,
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
    DraftSettingsScaffold(stringResource(R.string.title_reactivity), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            // G2R-F81: the relevant graphs sit ABOVE the settings, swiped between (no vertical stack).
            // The smoothing-threshold fields feed the reactivity curve; Smoothing Δ feeds the alpha
            // curve — the two graphs the user pages through here. S13 fills the chart slots.
            ChartPager(
                listOf(
                    ChartSlot("Reactivity curve", "reactivity_chart") {
                        ReactivityChart(
                            draft.toThresholdConfig(), Modifier.testTag("reactivity_chart"),
                            // Live "Now" line at the current smoothed lux (only while running).
                            currentLux = live.smoothedLux?.takeIf { live.serviceOn },
                        )
                    },
                    ChartSlot("Smoothing α", "alpha_chart") {
                        AlphaResponseChart(
                            draft.deltaFactor.toDouble(), Modifier.testTag("alpha_chart"),
                            // Live "Now" smoothing response (only while running).
                            currentAlpha = live.luxAlpha?.takeIf { live.serviceOn },
                        )
                    },
                ),
            )

            // Live glass-box readout: current dynamic threshold (as %, G2R-F56) + sensor dead zone (G2R-F7).
            ReactivityDiagnosticCardContent(live)

            // Labels + verbatim long-press help re-derived from extraction/scenes/reactivity_settings.md
            // (S12.6e, G2R-F19/F20/F21). The threshold fields are %aab_thresh*pc reactivity levels.
            // G2R-F82: grouped + labelled by the graph they feed (the reactivity curve).
            GraphSettingsGroup("Reactivity curve") {
                // These are the reactivity dead-zone levels (Dark/Dim/Bright), not smoothing — they shape
                // the reactivity curve above, so name them for what they are (owner finding).
                SectionHeader("Reactivity thresholds", divider = true)
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
            }
            // G2R-F85: there is NO editable "Dynamic threshold" field — %AAB_ThreshDynamic is the
            // COMPUTED adaptive dead-band for the current lux (task544), surfaced read-only in the live
            // reactivity card above (which shows the value as a percentage, not the literal token →
            // also closes G2R-F59). It was never an input; the seed in task570 act31 is runtime-only.
            // G2R-F19/F20: "Delta factor" was mislabelled with a wrong help ("brightness only changes
            // once lux exceeds this"). It is the SENSOR-SMOOTHING factor (%AAB_DeltaFactor, Misc scene
            // "Smoothing Δ"): luxAlpha = 1 - exp(-deltaFactor·effectiveDelta) in BrightnessEngine — the
            // binding was already correct, only the label/help were wrong. Fixed to the verbatim help.
            GraphSettingsGroup("Smoothing α") {
                SectionHeader("Sensor smoothing", divider = true)
                NumberSettingField(
                    "Smoothing Δ", draft.deltaFactor, { onEdit { s -> s.copy(deltaFactor = it.toFloat()) } },
                    epoch = epoch, committed = committed.deltaFactor, isInt = false,
                    help = TaskerHelp.DELTA_FACTOR, testTag = "field_deltaFactor",
                )
            }

            // S13c restyle (m3_audit §3 row 4): the trailing bare switch stack is grouped into an `AabCard`.
            AabCard {
                SectionHeader("Override & trust", divider = true)
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
}
