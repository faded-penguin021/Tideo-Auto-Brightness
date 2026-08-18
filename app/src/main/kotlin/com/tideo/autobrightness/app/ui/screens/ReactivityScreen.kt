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
            toast(R.string.toast_reset_defaults)
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
            // G2R-F81: graphs above settings, swiped (no vertical stack); S13 fills slots.
            ChartPager(
                listOf(
                    ChartSlot(stringResource(R.string.react_graph_curve), "reactivity_chart") {
                        ReactivityChart(
                            draft.toThresholdConfig(), Modifier.testTag("reactivity_chart"),
                            currentLux = live.smoothedLux?.takeIf { live.serviceOn },
                        )
                    },
                    ChartSlot(stringResource(R.string.react_graph_alpha), "alpha_chart") {
                        AlphaResponseChart(
                            draft.deltaFactor.toDouble(), Modifier.testTag("alpha_chart"),
                            currentAlpha = live.luxAlpha?.takeIf { live.serviceOn },
                        )
                    },
                ),
            )

            ReactivityDiagnosticCardContent(live)

            // G2R-F82: threshold fields grouped by graph (G2R-F19/F20/F21).
            GraphSettingsGroup(stringResource(R.string.react_graph_curve)) {
                SectionHeader(stringResource(R.string.react_thresholds_header), divider = true)
                NumberSettingField(
                    stringResource(R.string.react_dark), draft.thresholdDark, { onEdit { s -> s.copy(thresholdDark = it.toFloat()) } },
                    epoch = epoch, committed = committed.thresholdDark, isInt = false,
                    help = TaskerHelp.THRESH_DARK, testTag = "field_thresholdDark",
                )
                NumberSettingField(
                    stringResource(R.string.react_dim), draft.thresholdDim, { onEdit { s -> s.copy(thresholdDim = it.toFloat()) } },
                    epoch = epoch, committed = committed.thresholdDim, isInt = false,
                    help = TaskerHelp.THRESH_DIM, testTag = "field_thresholdDim",
                )
                NumberSettingField(
                    stringResource(R.string.react_bright), draft.thresholdBright, { onEdit { s -> s.copy(thresholdBright = it.toFloat()) } },
                    epoch = epoch, committed = committed.thresholdBright, isInt = false,
                    help = TaskerHelp.THRESH_BRIGHT, testTag = "field_thresholdBright",
                )
                NumberSettingField(
                    stringResource(R.string.react_curve_slope), draft.thresholdSteepness, { onEdit { s -> s.copy(thresholdSteepness = it.toFloat()) } },
                    epoch = epoch, committed = committed.thresholdSteepness, isInt = false,
                    help = TaskerHelp.CURVE_SLOPE, testTag = "field_thresholdSteepness",
                )
                NumberSettingField(
                    stringResource(R.string.react_curve_mid), draft.thresholdMidpoint, { onEdit { s -> s.copy(thresholdMidpoint = it) } },
                    epoch = epoch, committed = committed.thresholdMidpoint, isInt = false,
                    help = TaskerHelp.CURVE_MID, testTag = "field_thresholdMidpoint",
                )
            }
            // G2R-F85: %AAB_ThreshDynamic is computed only (task544), not editable.
            // G2R-F19/F20: "Delta factor" (smoothing) help was wrong; fixed to task570 verbatim.
            GraphSettingsGroup(stringResource(R.string.react_graph_alpha)) {
                SectionHeader(stringResource(R.string.react_smoothing_header), divider = true)
                NumberSettingField(
                    stringResource(R.string.react_smoothing_delta), draft.deltaFactor, { onEdit { s -> s.copy(deltaFactor = it.toFloat()) } },
                    epoch = epoch, committed = committed.deltaFactor, isInt = false,
                    help = TaskerHelp.DELTA_FACTOR, testTag = "field_deltaFactor",
                )
            }

            // S13c restyle: switch stack grouped into AabCard (m3_audit §3 row 4).
            AabCard {
                SectionHeader(stringResource(R.string.react_override_header), divider = true)
                SwitchSettingRow(
                    stringResource(R.string.react_use_override), draft.detectOverrides,
                    { onEdit { s -> s.copy(detectOverrides = it) } },
                    help = TaskerHelp.DETECT_OVERRIDES,
                    testTag = "switch_detectOverrides",
                )
                SwitchSettingRow(
                    stringResource(R.string.react_trust_sensor), draft.trustUnreliableSensor,
                    { onEdit { s -> s.copy(trustUnreliableSensor = it) } },
                    help = TaskerHelp.TRUST_UNRELIABLE,
                    testTag = "switch_trustUnreliableSensor",
                )
            }
        }
    }
}
