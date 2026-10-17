package com.tideo.autobrightness.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.DisplayCalibrationUi
import com.tideo.autobrightness.app.state.DisplayCalibrationViewModel
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.HandoffRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun HandoffScreen(
    navController: NavHostController,
    vm: DraftSettingsViewModel = viewModel(),
    calibrationVm: DisplayCalibrationViewModel = viewModel(),
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val calibration by calibrationVm.state.collectAsStateWithLifecycle()
    val toast = rememberToaster()

    HandoffContent(
        draft, committed, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        criticalError = criticalError,
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(handoffEnabled = d.handoffEnabled, handoffLux = d.handoffLux, handoffHysteresis = d.handoffHysteresis)
            }
            toast(R.string.toast_reset_defaults)
        },
        calibration = calibration,
        onCalibrate = calibrationVm::calibrate,
    )
}

@Composable
fun HandoffContent(
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
    calibration: DisplayCalibrationUi? = null,
    onCalibrate: () -> Unit = {},
) {
    DraftSettingsScaffold(stringResource(R.string.title_handoff), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            Text(stringResource(R.string.handoff_subtitle), style = MaterialTheme.typography.bodyMedium)
            // DD-076: the minimum follows the applied curve.
            val curveConfig = committed.toBrightnessCurveConfig()
            val preview by produceState<HandoffPreview?>(null, draft.handoffEnabled, draft.handoffLux, curveConfig) {
                value = withContext(Dispatchers.Default) { handoffPreview(draft, curveConfig) }
            }
            HandoffCard(draft, committed, epoch, preview, onEdit)
            if (calibration != null) DisplayCalibrationCard(calibration, onCalibrate)
        }
    }
}

internal data class HandoffPreview(val minimum: Int?, val effective: Int?)

internal fun handoffPreview(draft: AabSettings, curve: BrightnessCurveConfig): HandoffPreview? {
    if (!draft.handoffEnabled) return null
    val minimum = HandoffRules.minimumHandoffLux(curve)
    return HandoffPreview(minimum, minimum?.let { maxOf(draft.handoffLux, it) })
}

@Composable
private fun HandoffCard(
    draft: AabSettings,
    committed: AabSettings,
    epoch: Int,
    preview: HandoffPreview?,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
) {
    val on = draft.handoffEnabled
    AabCard {
        SectionHeader(stringResource(R.string.curve_handoff_header), divider = true)
        SwitchSettingRow(
            stringResource(R.string.settings_handoff_enabled), on, { v -> onEdit { it.copy(handoffEnabled = v) } },
            help = TaskerHelp.HANDOFF_ENABLED, testTag = "switch_handoffEnabled",
        )
        val luxNote = when {
            preview == null -> null
            preview.minimum == null -> stringResource(R.string.curve_handoff_unreachable)
            preview.minimum > draft.handoffLux -> stringResource(R.string.curve_handoff_raised, preview.minimum)
            else -> stringResource(R.string.curve_handoff_minimum, preview.minimum)
        }
        NumberSettingField(
            stringResource(R.string.settings_handoff_lux), draft.handoffLux, { onEdit { s -> s.copy(handoffLux = it.toInt()) } },
            epoch = epoch, committed = committed.handoffLux, helper = luxNote, enabled = on,
            help = TaskerHelp.HANDOFF_LUX, testTag = "field_handoffLux",
        )
        val exitNote = preview?.effective?.let {
            stringResource(R.string.curve_handoff_exit, floor(HandoffRules.exitLux(it, draft.handoffHysteresis)).toInt())
        }
        IntSliderSettingField(
            stringResource(R.string.curve_handoff_hysteresis_pct), (draft.handoffHysteresis * 100).roundToInt(), 50..100,
            { pct -> onEdit { it.copy(handoffHysteresis = pct / 100.0) } },
            committed = (committed.handoffHysteresis * 100).roundToInt(), helper = exitNote, enabled = on,
            help = TaskerHelp.HANDOFF_HYSTERESIS, testTag = "slider_handoffHysteresis",
        )
    }
}
