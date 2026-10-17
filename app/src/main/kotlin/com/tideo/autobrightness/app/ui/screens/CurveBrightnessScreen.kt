package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.state.derivedCoefficients
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.CurveBrightnessDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.KeyValueRow
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.HandoffRules
import com.tideo.autobrightness.domain.wizard.OverridePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt

internal fun List<FieldError>.forField(name: String): String? = firstOrNull { it.field == name }?.message

/**
 * Curve & Brightness screen: edit curve-zone coefficients with live graph preview.
 * Apply commits and re-runs pipeline (S12.5b).
 */
@Composable
fun CurveBrightnessScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val overridePoints by vm.overridePoints.collectAsStateWithLifecycle()
    val live by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    val toast = com.tideo.autobrightness.app.ui.components.rememberToaster()

    // D-169: no MaxBright auto-raise on this screen (Misc-only, per Tasker parity).

    // D-125: wizard suggestion only shown after "Preview graph" in the Tools wizard.
    // Seeded to this VM's draft with current values in [brackets]. Leaving discards the draft.
    CurveBrightnessContent(
        draft, committed, errors, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        overridePoints = overridePoints,
        criticalError = criticalError,
        onDeleteOverridePoint = vm::deleteOverridePoint,
        live = live,
        // G2R-F17: reset only the curve-zone coefficients to the task570 baseline (defaults).
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    form1A = d.form1A, zone1End = d.zone1End, form2B = d.form2B,
                    form2C = d.form2C, zone2End = d.zone2End,
                )
            }
            toast(R.string.toast_reset_defaults)
        },
    )
}

// task38 curve fit gate (D-125): ≥9 real, user-recorded override points for wizard suggestion.
internal const val MIN_FIT_POINTS = 9

@Composable
fun CurveBrightnessContent(
    draft: AabSettings,
    committed: AabSettings,
    errors: List<FieldError>,
    epoch: Int,
    dirty: Boolean,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    overridePoints: List<OverridePoint> = emptyList(),
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
    onDeleteOverridePoint: ((OverridePoint) -> Unit)? = null,
    live: PipelineState = PipelineState(),
) {
    DraftSettingsScaffold(stringResource(R.string.title_curve_brightness), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            val curveConfig = draft.toBrightnessCurveConfig()
            // D-125 / Tasker parity: dashed gold reference is hardcoded baseline (AabSettings defaults),
            // not the committed snapshot. Never moves with draft or committed.
            val referenceConfig = remember { AabSettings().toBrightnessCurveConfig() }
            // Gate-2(5th) obs: clamp displayed lux to 0.1 for visibility; recorded value untouched.
            val overlay = remember(overridePoints) {
                overridePoints.map { Offset(it.lux.toFloat().coerceAtLeast(0.1f), it.brightness.toFloat()) }
            }
            var pendingDelete by remember { mutableStateOf<OverridePoint?>(null) }
            val handoff by produceState<HandoffPreview?>(null, draft.handoffEnabled, draft.handoffLux, curveConfig) {
                value = withContext(Dispatchers.Default) { handoffPreview(draft, curveConfig) }
            }
            BrightnessCurveChart(
                curveConfig,
                modifier = Modifier.testTag("brightness_curve_chart"),
                // G3-F2: live "Now" cross-hair (current smoothed lux × current brightness), only while
                // the service is running. The chart draws both marker lines when BOTH are non-null.
                // D-117: track the PERCEIVED brightness (targetBrightness, un-floored) like the Dashboard
                // hero — in PWM-sensitive mode the hardware value is floored above the perceived one, and
                // the crosshair should sit where the user actually sees the screen, not at the floor.
                // Falls back to the applied value when they're equal (PWM-sensitive off → target==applied).
                currentLux = live.smoothedLux?.takeIf { live.serviceOn },
                currentBrightness = live.shownBrightness?.takeIf { live.serviceOn },
                handoffLux = handoff?.effective,
                overridePoints = overlay,
                referenceCurve = referenceConfig,
                onDeleteOverridePoint = if (onDeleteOverridePoint == null) {
                    null
                } else {
                    { tapped ->
                        pendingDelete = overridePoints.minByOrNull { p ->
                            val dl = p.lux - tapped.x
                            val db = p.brightness - tapped.y
                            dl * dl + db * db
                        }
                    }
                },
            )
            pendingDelete?.let { pt ->
                OverridePointDeleteDialog(
                    point = pt,
                    onConfirm = {
                        onDeleteOverridePoint?.invoke(pt)
                        pendingDelete = null
                    },
                    onDismiss = { pendingDelete = null },
                )
            }

            // Tasker live readout (current_lux_and_bright, brightness_settings.md elements22, G2R-F58):
            // current smoothed lux + current brightness within the active min–max range.
            CurveBrightnessDiagnosticCardContent(live, committed.minBrightness, committed.maxBrightness)

            // Labels from extraction/scenes/brightness_settings.md (S12.6e, G2R-F19/F21).
            // S13c restyle: fields grouped in AabCard; continuity readout in separate gold KeyValueRow (m3_audit §3/4).
            AabCard {
                SectionHeader(stringResource(R.string.curve_zones_header), divider = true)
                NumberSettingField(
                    stringResource(R.string.curve_form1a), draft.form1A, { onEdit { s -> s.copy(form1A = it) } },
                    epoch = epoch, committed = committed.form1A, isInt = false,
                    help = TaskerHelp.FORM_1A, testTag = "field_form1A",
                )
                NumberSettingField(
                    stringResource(R.string.curve_zone1_end), draft.zone1End, { onEdit { s -> s.copy(zone1End = it.toInt()) } },
                    epoch = epoch, committed = committed.zone1End,
                    help = TaskerHelp.ZONE_1_END, testTag = "field_zone1End",
                )
                NumberSettingField(
                    stringResource(R.string.curve_form2b), draft.form2B, { onEdit { s -> s.copy(form2B = it.toFloat()) } },
                    epoch = epoch, committed = committed.form2B, isInt = false,
                    help = TaskerHelp.FORM_2B, testTag = "field_form2B",
                )
                NumberSettingField(
                    stringResource(R.string.curve_form2c), draft.form2C, { onEdit { s -> s.copy(form2C = it.toInt()) } },
                    epoch = epoch, committed = committed.form2C, error = errors.forField("form2C"),
                    help = TaskerHelp.FORM_2C, testTag = "field_form2C",
                )
                NumberSettingField(
                    stringResource(R.string.curve_zone2_end), draft.zone2End, { onEdit { s -> s.copy(zone2End = it.toInt()) } },
                    epoch = epoch, committed = committed.zone2End, error = errors.forField("zone2End"),
                    help = TaskerHelp.ZONE_2_END, testTag = "field_zone2End",
                )
            }

            // task659 continuity coefficients (task613/614/615 _UpdateBrightnessFormulae), labeled as zone-alignment hinges (G2R-F61).
            AabCard {
                SectionHeader(stringResource(R.string.curve_derived_header), divider = true)
                val coeffs = draft.derivedCoefficients()
                KeyValueRow(stringResource(R.string.curve_form2a), coeffs.form2A.fmtCoeff("%.3f"), testTag = "derived_form2A")
                KeyValueRow(stringResource(R.string.curve_form3a), coeffs.form3A.fmtCoeff("%.1f"), testTag = "derived_form3A", showDivider = false)
                errors.forField("form2A")?.let { ErrorBanner(it, "error_form2A") }
                errors.forField("form3A")?.let { ErrorBanner(it, "error_form3A") }
                errors.forField("zone2End")?.let { ErrorBanner(it, "error_zone2End") }
                errors.forField("safetyBrightness")?.let { ErrorBanner(it, "error_safetyBrightness") }
            }

            HandoffCard(draft, committed, epoch, handoff, onEdit)
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

private fun Double.fmtCoeff(fmt: String): String = if (isNaN()) "—" else fmt.format(this)

/** Confirmation dialog for deleting a recorded override point (F36). */
@Composable
fun OverridePointDeleteDialog(point: OverridePoint, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("override_delete_dialog"),
        title = { Text(stringResource(R.string.curve_delete_point_title)) },
        text = {
            Text(stringResource(R.string.curve_delete_point_msg, point.lux, point.brightness.toInt()))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("override_delete_confirm")) {
                Text(stringResource(R.string.confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_cancel)) }
        },
    )
}

@Composable
internal fun ErrorBanner(message: String, testTag: String) {
    Card(modifier = Modifier.testTag(testTag)) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
