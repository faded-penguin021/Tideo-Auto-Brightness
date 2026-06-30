package com.tideo.autobrightness.app.ui.screens

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.tideo.autobrightness.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.runtime.PowerDrawCalibrator
import com.tideo.autobrightness.app.runtime.PowerDrawProgress
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.CurveSuggestionPreview
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
        // D-125: stash the wizard's fit as a transient draft transform (curve → suggested), then jump to
        // Curve & Brightness — whose VM applies it on its initial seed: suggested values in the fields,
        // current values in [brackets], the fit traced against the hardcoded reference. User-driven, like
        // Tasker's task38 → preview → task655; leaving the screen discards it. (Was: auto-draw a fitted
        // line whenever ≥ 9 points existed.) The mapping mirrors "Apply suggestion" (onApplyWizard):
        // continuous form1A lands exactly, the Int/Float fields round (task655); form2A/3A stay derived.
        onPreviewGraph = { result ->
            CurveSuggestionPreview.request { s ->
                val cfg = CurveSuggestionEngine.applyToLiveCurve(result, s.toBrightnessCurveConfig())
                s.copy(
                    form1A = cfg.form1A,
                    zone1End = Math.round(cfg.zone1End).toInt(),
                    form2B = cfg.form2B.toFloat(),
                    form2C = Math.round(cfg.form2C).toInt(),
                    zone2End = Math.round(cfg.zone2End).toInt(),
                )
            }
            navController.navigate(AppRoute.CurveBrightness.route)
        },
        onRunWizard = { overrides, tau ->
            CurveSuggestionEngine.suggest(
                // G3-F17: τ comes from the wizard control (default 0.001 = follow the recorded points).
                CurveSuggestionInput(overrides, settings.toBrightnessCurveConfig(), tau = tau),
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

    // task524 entry: the prep dialog (Airplane Mode / close apps / don't touch / unplug) → Start shows
    // the WHITE calibration overlay and runs the sweep. The owner finding: OLED power is colour-
    // dependent, so the panel MUST draw a full-white screen during measurement (Tasker uses a white
    // FrameLayout dialog). The overlay is a fullscreen white Dialog whose OWN window brightness the
    // sweep drives (no WRITE_SETTINGS; the override dies with the dialog → nothing to restore).
    var calibrating by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }
    if (showPrep) {
        PowerCalibrationPrepDialog(
            onStart = { showPrep = false; cancelRequested = false; calibrating = true },
            onCancel = { showPrep = false },
        )
    }
    if (calibrating) {
        PowerCalibrationOverlay(
            progress = powerProgress,
            onCancel = { cancelRequested = true },
            startCalibration = { setBrightness ->
                powerVm.calibrate(
                    setScreenBrightness = setBrightness,
                    isCancelled = { cancelRequested },
                    onResult = { result ->
                        calibrating = false
                        toast(powerResultMessage(context, result))
                    },
                )
            },
        )
    }
}

/**
 * The full-white calibration screen (Tasker task524's white `FrameLayout` dialog). A fullscreen
 * [Dialog] painted white so the OLED panel draws maximum-load white pixels while the sweep measures
 * power; dark status text + a progress bar overlay it. The sweep drives THIS dialog window's
 * `screenBrightness` (it is the front-most window), so no WRITE_SETTINGS is needed and the override is
 * discarded when the dialog dismisses. [startCalibration] is invoked once the window is available, with
 * a brightness setter bound to that window.
 */
@Composable
private fun PowerCalibrationOverlay(
    progress: PowerDrawProgress?,
    onCancel: () -> Unit,
    startCalibration: (setBrightness: suspend (Int) -> Unit) -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            val w = window ?: return@LaunchedEffect
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startCalibration { level ->
                withContext(Dispatchers.Main) {
                    val lp = w.attributes
                    lp.screenBrightness = (level / 255f).coerceIn(0.004f, 1f)
                    w.attributes = lp
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(Color.White).testTag("power_calibration_overlay"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.power_calibrating),
                    color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    progress?.let { "${it.message} (${it.step}/${it.total})" } ?: "",
                    color = Color(0xFF666666), fontSize = 14.sp,
                )
                LinearProgressIndicator(
                    progress = {
                        val total = progress?.total ?: 0
                        if (total > 0) (progress!!.step.toFloat() / total).coerceIn(0f, 1f) else 0f
                    },
                    color = Color(0xFF007C63), // task524 progress-bar teal
                    trackColor = Color(0xFFEEEEEE),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onCancel, modifier = Modifier.testTag("power_cancel_overlay")) {
                    Text(stringResource(R.string.power_cancel), color = Color.Black)
                }
            }
        }
    }
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
    onRunWizard: (List<OverridePoint>, tau: Double) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
    recordedPoints: List<OverridePoint> = emptyList(),
    onPreviewGraph: (CurveSuggestionResult) -> Unit = {},
    powerSamples: List<PowerDrawSample> = emptyList(),
    powerRunning: Boolean = false,
    powerProgress: String? = null,
    powerHasData: Boolean = false,
    onCalibratePower: () -> Unit = {},
) {
    SettingsScaffold(stringResource(R.string.title_tools), onBack) { padding ->
        SettingsColumn(padding) {
            WizardCard(recordedPoints, onRunWizard, onApplyWizard, onPreviewGraph)

            // S13c restyle (m3_audit §3 row 8): each tool is its own `AabCard` with a clear title.
            AabCard {
                SectionHeader(stringResource(R.string.tools_power_header), divider = true)
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

@OptIn(ExperimentalLayoutApi::class) // FlowRow (button row that wraps on narrow screens)
@Composable
private fun WizardCard(
    recorded: List<OverridePoint>,
    onRunWizard: (List<OverridePoint>, tau: Double) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
    onPreviewGraph: (CurveSuggestionResult) -> Unit = {},
) {
    // Override points are now captured at runtime + persisted (G2R-F13). The wizard runs against the
    // recorded set; with < 9 it returns null (task38 error path).
    var result by remember { mutableStateOf<CurveSuggestionResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // G3-F17: inertia regularization τ, exposed so the user can trade fidelity-to-points vs.
    // closeness-to-current-curve. Default 0.001 (≈0) = follow the recorded points (the faithful
    // task38 act2 default); the Tasker label recommends 0.001–5, never exactly 0 (τ is a divisor).
    var tau by remember { mutableFloatStateOf(0.001f) }
    val clipboard = LocalClipboardManager.current
    val toast = rememberToaster()
    val context = LocalContext.current

    AabCard(Modifier.testTag("wizard_card")) {
            Text(stringResource(R.string.tools_wizard_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.tools_wizard_desc),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.tools_recorded_points, recorded.size), style = MaterialTheme.typography.bodyMedium)
            // τ (inertia) control: lower = follow my points, higher = stay near the current curve.
            Text(
                stringResource(R.string.tools_inertia, "%.3f".format(tau)),
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = tau,
                onValueChange = { tau = it },
                valueRange = 0.001f..5f,
                modifier = Modifier.testTag("wizard_tau"),
            )
            OutlinedButton(
                onClick = {
                    // G2R-F62: gate on REAL recorded points only (MIN_FIT_POINTS), matching the Curve &
                    // Brightness chart's gate. The domain engine would otherwise inject synthetic "ghost"
                    // priors and fit on as few as 7 real points — the owner saw exactly that. Block here
                    // before calling the engine so the user-facing contract ("needs ≥ 9") actually holds.
                    if (recorded.size < MIN_FIT_POINTS) {
                        result = null
                        message = context.getString(R.string.tools_not_enough_points, MIN_FIT_POINTS, recorded.size)
                    } else {
                        val r = onRunWizard(recorded, tau.toDouble())
                        result = r
                        message = if (r == null) context.getString(R.string.tools_fit_failed) else null
                        // Tasker: task38 "_SuggestCurveParameters" act13 code105 (Set Clipboard, arg0=%AAB_Test)
                        // — every successful run copies the %AAB_Test diagnostics to the clipboard, not just
                        // the manual "Copy full report" button below. Mirror that auto-copy here.
                        if (r != null) {
                            clipboard.setText(AnnotatedString(r.diagnosticsLog.trim()))
                            toast(R.string.toast_diagnostics_copied)
                        }
                    }
                },
                modifier = Modifier.testTag("run_wizard"),
            ) { Text(stringResource(R.string.tools_run_wizard)) }

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
                // FlowRow so all three actions wrap onto the next line on narrow screens — in a fixed Row
                // the third button ("Copy full report") was pushed off-screen and looked missing (owner).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onApplyWizard(r); toast(R.string.toast_suggestion_applied) },
                        modifier = Modifier.testTag("apply_wizard"),
                    ) { Text(stringResource(R.string.tools_apply_suggestion)) }
                    // D-125: preview THIS fit on the Curve & Brightness chart — it loads the suggestion
                    // into that screen's draft (user-driven; was an auto-fit whenever ≥ 9 points existed).
                    OutlinedButton(
                        onClick = { onPreviewGraph(r) },
                        modifier = Modifier.testTag("preview_graph"),
                    ) { Text(stringResource(R.string.tools_preview_graph)) }
                    // %AAB_Test diagnostics → clipboard (D-025, G2-F15): copy the FULL verbose report.
                    // (The wizard also auto-copies on a successful run — task38 act13 code105.)
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(r.diagnosticsLog.trim()))
                            toast(R.string.toast_full_report_copied)
                        },
                        modifier = Modifier.testTag("copy_diagnostics"),
                    ) { Text(stringResource(R.string.tools_copy_full_report)) }
                }
            }
    }
}
