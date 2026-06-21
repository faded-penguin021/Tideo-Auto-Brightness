package com.tideo.autobrightness.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.runtime.PowerDrawCalibrator
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.PowerDrawViewModel
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.graph.PowerDrawChart
import com.tideo.autobrightness.domain.power.PowerDrawSample
import com.tideo.autobrightness.domain.wizard.CurveSuggestionEngine
import com.tideo.autobrightness.domain.wizard.CurveSuggestionInput
import com.tideo.autobrightness.domain.wizard.CurveSuggestionResult
import com.tideo.autobrightness.domain.wizard.OverridePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tools: curve wizard + power-draw calibration (Tasker Debug Scene + wizard). The debug-category
 * selector moved to the Misc screen (G2-F2). */
@Composable
fun ToolsScreen(
    navController: NavHostController,
    vm: SettingsViewModel = viewModel(),
    powerVm: PowerDrawViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overridePoints by vm.overridePoints.collectAsStateWithLifecycle()
    val powerSamples by powerVm.samples.collectAsStateWithLifecycle()
    val powerRunning by powerVm.running.collectAsStateWithLifecycle()
    val powerProgress by powerVm.progress.collectAsStateWithLifecycle()
    val powerHasData by powerVm.hasData.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = rememberToaster()
    var showPrep by remember { mutableStateOf(false) }

    ToolsContent(
        recordedPoints = overridePoints,
        onBack = { navController.popBackStack() },
        // Gate-2(5th) obs: jump straight to Curve & Brightness to see the suggested line on the chart.
        onPreviewGraph = { navController.navigate(AppRoute.CurveBrightness.route) },
        onRunWizard = { overrides ->
            CurveSuggestionEngine.suggest(
                CurveSuggestionInput(overrides, settings.toBrightnessCurveConfig()),
            )
        },
        onApplyWizard = { result ->
            val cfg = CurveSuggestionEngine.applyToLiveCurve(result, settings.toBrightnessCurveConfig())
            vm.update { s ->
                // G2R-F70: the fitted curve params are continuous doubles; form1A keeps its decimal now
                // (Double schema) — the wizard suggestion lands exactly. The remaining Int fields round.
                s.copy(
                    form1A = cfg.form1A,
                    zone1End = Math.round(cfg.zone1End).toInt(),
                    form2B = cfg.form2B.toFloat(),
                    form2C = Math.round(cfg.form2C).toInt(),
                    zone2End = Math.round(cfg.zone2End).toInt(),
                )
            }
        },
        powerSamples = powerSamples,
        powerRunning = powerRunning,
        powerProgress = powerProgress?.let { "${it.message} (${it.step}/${it.total})" },
        powerHasData = powerHasData,
        onCalibratePower = { showPrep = true },
    )

    // task524 entry: the prep dialog (Airplane Mode / close apps / don't touch / unplug) → Start runs
    // the sweep, driving THIS Activity's window brightness (no WRITE_SETTINGS) and restoring it after.
    if (showPrep) {
        PowerCalibrationPrepDialog(
            onStart = {
                showPrep = false
                val activity = context as? Activity
                powerVm.calibrate(
                    setScreenBrightness = { level ->
                        withContext(Dispatchers.Main) { activity?.applyWindowBrightness(level) }
                    },
                    onResult = { result ->
                        activity?.clearWindowBrightness()
                        toast(powerResultMessage(context, result))
                    },
                )
            },
            onCancel = { showPrep = false },
        )
    }
}

private fun Activity.applyWindowBrightness(level: Int) {
    val lp = window.attributes
    lp.screenBrightness = (level / 255f).coerceIn(0.004f, 1f)
    window.attributes = lp
}

private fun Activity.clearWindowBrightness() {
    val lp = window.attributes
    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = lp
}

private fun powerResultMessage(context: android.content.Context, result: PowerDrawCalibrator.Result): String =
    when (result) {
        is PowerDrawCalibrator.Result.Success -> context.getString(R.string.power_result_success, result.samples.size)
        PowerDrawCalibrator.Result.SensorUnavailable -> context.getString(R.string.power_result_no_sensor)
        PowerDrawCalibrator.Result.Charging -> context.getString(R.string.power_result_charging)
        PowerDrawCalibrator.Result.Cancelled -> context.getString(R.string.power_result_cancelled)
    }

@Composable
private fun PowerCalibrationPrepDialog(onStart: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.power_prep_title)) },
        text = { Text(stringResource(R.string.power_prep_message)) },
        confirmButton = {
            Button(onClick = onStart, modifier = Modifier.testTag("power_start")) {
                Text(stringResource(R.string.power_start))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.power_cancel)) } },
        modifier = Modifier.testTag("power_prep_dialog"),
    )
}

@Composable
fun ToolsContent(
    onBack: () -> Unit,
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
    recordedPoints: List<OverridePoint> = emptyList(),
    onPreviewGraph: () -> Unit = {},
    powerSamples: List<PowerDrawSample> = emptyList(),
    powerRunning: Boolean = false,
    powerProgress: String? = null,
    powerHasData: Boolean = false,
    onCalibratePower: () -> Unit = {},
) {
    SettingsScaffold("Tools", onBack) { padding ->
        SettingsColumn(padding) {
            WizardCard(recordedPoints, onRunWizard, onApplyWizard, onPreviewGraph)

            // S13c restyle (m3_audit §3 row 8): each tool is its own `AabCard` with a clear title.
            AabCard {
                SectionHeader("Power-draw calibration", divider = true)
                Text(
                    stringResource(R.string.power_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The real measured chart once samples exist; an EmptyState until the first calibration.
                PowerDrawChart(powerSamples, Modifier.testTag("power_draw_chart"))
                if (powerRunning) {
                    Text(
                        powerProgress ?: stringResource(R.string.power_calibrating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("power_progress"),
                    )
                } else {
                    OutlinedButton(
                        onClick = onCalibratePower,
                        modifier = Modifier.fillMaxWidth().testTag("calibrate_power"),
                    ) {
                        Text(stringResource(if (powerHasData) R.string.power_recalibrate else R.string.power_calibrate))
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardCard(
    recorded: List<OverridePoint>,
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
    onPreviewGraph: () -> Unit = {},
) {
    // Override points are now captured at runtime + persisted (G2R-F13). The wizard runs against the
    // recorded set; with < 9 it returns null (task38 error path).
    var result by remember { mutableStateOf<CurveSuggestionResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val toast = rememberToaster()

    AabCard(Modifier.testTag("wizard_card")) {
            Text("Curve suggestion wizard", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fits a brightness curve to your recorded manual overrides (needs ≥ 9 points).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Recorded points: ${recorded.size}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    // G2R-F62: gate on REAL recorded points only (MIN_FIT_POINTS), matching the Curve &
                    // Brightness chart's gate. The domain engine would otherwise inject synthetic "ghost"
                    // priors and fit on as few as 7 real points — the owner saw exactly that. Block here
                    // before calling the engine so the user-facing contract ("needs ≥ 9") actually holds.
                    if (recorded.size < MIN_FIT_POINTS) {
                        result = null
                        message = "Not enough recorded override points (need ≥ $MIN_FIT_POINTS real points, have ${recorded.size})."
                    } else {
                        val r = onRunWizard(recorded)
                        result = r
                        message = if (r == null) "Could not fit a curve to the recorded points." else null
                    }
                },
                modifier = Modifier.testTag("run_wizard"),
            ) { Text("Run wizard") }

            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            result?.let { r ->
                r.qualityLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                // The full Tasker-style %AAB_Test diagnostics report (zone boundaries, curve params,
                // per-zone R²/nRMSE/bias, fit stability) — the engine already produces it verbatim, so
                // surface ALL of it, not just the 4-line summary (owner finding: report too terse).
                AabCard(Modifier.testTag("wizard_report")) {
                    Text(
                        r.diagnosticsLog.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onApplyWizard(r); toast("Suggestion applied") },
                        modifier = Modifier.testTag("apply_wizard"),
                    ) { Text("Apply suggestion") }
                    // Gate-2(5th) obs: jump to the Curve & Brightness chart to see the suggested line
                    // (the chart draws the fit from the recorded points once ≥ 9 exist).
                    OutlinedButton(
                        onClick = onPreviewGraph,
                        modifier = Modifier.testTag("preview_graph"),
                    ) { Text("Preview graph") }
                    // %AAB_Test diagnostics → clipboard (D-025, G2-F15): copy the FULL verbose report.
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(r.diagnosticsLog.trim()))
                            toast("Full report copied to clipboard")
                        },
                        modifier = Modifier.testTag("copy_diagnostics"),
                    ) { Text("Copy full report") }
                }
            }
    }
}
