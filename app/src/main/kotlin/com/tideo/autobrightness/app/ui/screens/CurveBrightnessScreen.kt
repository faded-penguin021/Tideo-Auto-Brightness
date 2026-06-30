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
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart
import com.tideo.autobrightness.domain.wizard.OverridePoint

internal fun List<FieldError>.forField(name: String): String? = firstOrNull { it.field == name }?.message

/**
 * Curve & Brightness (Tasker AAB Brightness Settings scene, banner "General"). Edits the curve-zone
 * coefficients against a **draft** that the graph previews live; **Apply** commits + re-runs the
 * pipeline (S12.5b). Brightness range (min/max/offset/scale) moved to the Misc screen (G2-F2).
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

    // D-125: a wizard suggestion reaches this screen ONLY when the user ran the Tools wizard and tapped
    // "Preview graph" — it is applied to this VM's draft during its initial seed (see
    // DraftSettingsViewModel / CurveSuggestionPreview), so the fields show the suggested values with the
    // current values in [brackets] and the live "Curve" traces the fit. Leaving discards the draft, so
    // the line disappears on close. There is deliberately NO auto-fit at ≥ 9 override points anymore.
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

/**
 * task38 needs ≥ 9 **real, user-recorded** override points before it fits/suggests a curve. This is
 * the user-facing gate on the **Tools wizard** (G2R-F62): the domain engine has its own post-
 * ghost-injection ≥9 check, but ghost/synthetic priors must NOT count toward the gate the user sees —
 * the owner ran the wizard on just 7 real points and it still fired. [OverridePointStore] only holds
 * real points, so gating on its size is correct.
 *
 * D-125: this gates only the *wizard run*, never an auto-preview. The Curve & Brightness screen no
 * longer fits/draws a suggested curve just because ≥ 9 points exist — a suggestion appears there only
 * after the user runs the wizard and taps "Preview graph" (see
 * [com.tideo.autobrightness.app.state.CurveSuggestionPreview]).
 */
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
            // D-125 / Tasker parity (task663 `ref_data`): the dashed gold reference is the HARDCODED
            // baseline curve — the AabSettings defaults, i.e. 5·√lux; 29.58 + 8.8·(…); 255 − (2513/lux)·255
            // — NOT the committed snapshot (corrects F69). So a previewed suggestion (or any edit) shows
            // against the fixed reference, exactly like the Tasker graph: the live "Curve" (`new_data`,
            // suggested/current) vs the hardcoded `ref_data`. It never moves with the draft or committed.
            val referenceConfig = remember { AabSettings().toBrightnessCurveConfig() }
            // Gate-2(5th) obs: a 0-lux override (which can happen) would plot at/below the log x-axis
            // floor (0.1) and be invisible/un-tappable — clamp the DISPLAYED lux up to 0.1 so it draws
            // at the left edge. The recorded value is untouched (deletion matches against the original).
            val overlay = remember(overridePoints) {
                overridePoints.map { Offset(it.lux.toFloat().coerceAtLeast(0.1f), it.brightness.toFloat()) }
            }
            // F36: tapping a recorded override dot raises a confirm dialog before deleting it.
            var pendingDelete by remember { mutableStateOf<OverridePoint?>(null) }
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
                currentBrightness = (live.targetBrightness ?: live.lastAppliedBrightness)?.takeIf { live.serviceOn },
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

            // Labels + verbatim long-press help re-derived from extraction/scenes/brightness_settings.md
            // (S12.6e, G2R-F19/F21). Tasker labels: "Zone 1 Scaling" (form1A), "Zone 2 Scaling" (form2B),
            // "Zone 2 Offset" (form2C); zone-end fields keep the lux annotation.
            // S13c restyle (m3_audit §3 row 3): the bare field stack is grouped into an `AabCard`; the
            // derived continuity readout becomes a distinct gold `KeyValueRow` data-pop card (§4 B4).
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

            // task659 live-derived continuity coefficients (task613/614/615 _UpdateBrightnessFormulae).
            // G2R-F61: labelled as the zone-alignment hinge points (not bare "form2A/form3A" placeholders).
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
        }
    }
}

/** Format a derived coefficient, guarding the NaN that an inverted zone range produces (G2-F6). */
private fun Double.fmtCoeff(fmt: String): String = if (isNaN()) "—" else fmt.format(this)

/**
 * Tap-to-delete confirmation for a recorded override point (F36): shows the captured lux/brightness
 * pair and confirms before removing it from [OverridePointStore].
 */
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
