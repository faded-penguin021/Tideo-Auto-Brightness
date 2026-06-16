package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.domain.wizard.CurveSuggestionEngine
import com.tideo.autobrightness.domain.wizard.CurveSuggestionInput
import com.tideo.autobrightness.domain.wizard.CurveSuggestionResult
import com.tideo.autobrightness.domain.wizard.OverridePoint

/** Tools: curve wizard + power-draw calibration (Tasker Debug Scene + wizard). The debug-category
 * selector moved to the Misc screen (G2-F2). */
@Composable
fun ToolsScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overridePoints by vm.overridePoints.collectAsStateWithLifecycle()
    ToolsContent(
        recordedPoints = overridePoints,
        onBack = { navController.popBackStack() },
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
    )
}

@Composable
fun ToolsContent(
    onBack: () -> Unit,
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
    recordedPoints: List<OverridePoint> = emptyList(),
) {
    SettingsScaffold("Tools", onBack) { padding ->
        SettingsColumn(padding) {
            WizardCard(recordedPoints, onRunWizard, onApplyWizard)

            SectionHeader("Power-draw calibration")
            Text(
                "Measures screen power across brightness levels to build the power-draw chart. " +
                    "On-device calibration (battery-current sampling) runs at Gate 2/3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChartPlaceholder("PowerDrawChart", "power_draw_chart")
        }
    }
}

@Composable
private fun WizardCard(
    recorded: List<OverridePoint>,
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
) {
    // Override points are now captured at runtime + persisted (G2R-F13). The wizard runs against the
    // recorded set; with < 9 it returns null (task38 error path).
    var result by remember { mutableStateOf<CurveSuggestionResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val toast = rememberToaster()

    Card(Modifier.fillMaxWidth().testTag("wizard_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Card(Modifier.fillMaxWidth().testTag("wizard_report")) {
                    Text(
                        r.diagnosticsLog.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onApplyWizard(r); toast("Suggestion applied") },
                        modifier = Modifier.testTag("apply_wizard"),
                    ) { Text("Apply suggestion") }
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
}
