package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.domain.wizard.CurveSuggestionEngine
import com.tideo.autobrightness.domain.wizard.CurveSuggestionInput
import com.tideo.autobrightness.domain.wizard.CurveSuggestionResult
import com.tideo.autobrightness.domain.wizard.OverridePoint

/** %AAB_Debug 10 named categories, verbatim (D-023). Index == debugLevel. */
val DEBUG_LABELS = listOf(
    "Off", "Skip Animations", "Animation Details", "Light Eval Thresholds", "Dynamic Scale Calcs",
    "Super Dimming Info", "Overlay Preview", "Graph Metrics", "Context Automation", "Context Location",
)

/** Tools: curve wizard, debug-level selector, power-draw calibration (Tasker Debug Scene + wizard). */
@Composable
fun ToolsScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ToolsContent(
        settings = settings,
        onBack = { navController.popBackStack() },
        onSetDebugLevel = { level -> vm.update { it.copy(debugLevel = level) } },
        onRunWizard = { overrides ->
            CurveSuggestionEngine.suggest(
                CurveSuggestionInput(overrides, settings.toBrightnessCurveConfig()),
            )
        },
        onApplyWizard = { result ->
            val cfg = CurveSuggestionEngine.applyToLiveCurve(result, settings.toBrightnessCurveConfig())
            vm.update { s ->
                s.copy(
                    form1A = cfg.form1A.toInt(),
                    zone1End = cfg.zone1End.toInt(),
                    form2B = cfg.form2B.toFloat(),
                    form2C = cfg.form2C.toInt(),
                    zone2End = cfg.zone2End.toInt(),
                )
            }
        },
    )
}

@Composable
fun ToolsContent(
    settings: AabSettings,
    onBack: () -> Unit,
    onSetDebugLevel: (Int) -> Unit,
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
) {
    SettingsScaffold("Tools", onBack) { padding ->
        SettingsColumn(padding) {
            WizardCard(onRunWizard, onApplyWizard)

            SectionHeader("Debug")
            DebugLevelSelector(settings.debugLevel, onSetDebugLevel)

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
    onRunWizard: (List<OverridePoint>) -> CurveSuggestionResult?,
    onApplyWizard: (CurveSuggestionResult) -> Unit,
) {
    // Override-point capture/persistence is not yet wired (D-044); the wizard runs on demand against
    // the currently-available recorded points. With < 9 it returns null (task38 error path).
    var result by remember { mutableStateOf<CurveSuggestionResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var recorded by remember { mutableStateOf<List<OverridePoint>>(emptyList()) }

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
                    val r = onRunWizard(recorded)
                    result = r
                    message = if (r == null) "Not enough recorded override points (need ≥ 9)." else null
                },
                modifier = Modifier.testTag("run_wizard"),
            ) { Text("Run wizard") }

            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            result?.let { r ->
                r.qualityLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    onClick = { onApplyWizard(r); message = "Suggestion applied." },
                    modifier = Modifier.testTag("apply_wizard"),
                ) { Text("Apply suggestion") }
            }
        }
    }
}

@Composable
private fun DebugLevelSelector(current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("debug_selector")) {
        Text("Debug: ${DEBUG_LABELS.getOrElse(current) { "Off" }}")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DEBUG_LABELS.forEachIndexed { level, label ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSelect(level); expanded = false },
            )
        }
    }
}
