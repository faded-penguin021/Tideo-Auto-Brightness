package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.state.derivedCoefficients
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.wizard.CurveSuggestionEngine
import com.tideo.autobrightness.domain.wizard.CurveSuggestionInput
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
    val toast = com.tideo.autobrightness.app.ui.components.rememberToaster()
    CurveBrightnessContent(
        draft, committed, errors, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        overridePoints = overridePoints,
        criticalError = criticalError,
        // G2R-F17: reset only the curve-zone coefficients to the task570 baseline (defaults).
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    form1A = d.form1A, zone1End = d.zone1End, form2B = d.form2B,
                    form2C = d.form2C, zone2End = d.zone2End,
                )
            }
            toast("Reset to defaults")
        },
    )
}

/** task38 needs ≥ 9 recorded override points before it fits/suggests a curve. */
private const val MIN_FIT_POINTS = 9

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
) {
    DraftSettingsScaffold("Curve & Brightness", dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            val curveConfig = draft.toBrightnessCurveConfig()
            val overlay = remember(overridePoints) {
                overridePoints.map { Offset(it.lux.toFloat(), it.brightness.toFloat()) }
            }
            // The fitted/suggested curve only appears once ≥ 9 points are recorded (task38, G2R-F14).
            val fittedCurve: BrightnessCurveConfig? = remember(overridePoints, curveConfig) {
                if (overridePoints.size >= MIN_FIT_POINTS) {
                    CurveSuggestionEngine.suggest(CurveSuggestionInput(overridePoints, curveConfig))
                        ?.let { CurveSuggestionEngine.applyToLiveCurve(it, curveConfig) }
                } else {
                    null
                }
            }
            BrightnessCurveChart(
                curveConfig,
                modifier = Modifier.testTag("brightness_curve_chart"),
                overridePoints = overlay,
                fittedCurve = fittedCurve,
            )

            SectionHeader("Curve zones")
            NumberSettingField(
                "Form 1A", draft.form1A, { onEdit { s -> s.copy(form1A = it.toInt()) } },
                epoch = epoch, committed = committed.form1A,
                helper = "How quickly brightness rises in dim light.", testTag = "field_form1A",
            )
            NumberSettingField(
                "Zone 1 end (lux)", draft.zone1End, { onEdit { s -> s.copy(zone1End = it.toInt()) } },
                epoch = epoch, committed = committed.zone1End,
                helper = "Where dim-light zone 1 ends and zone 2 begins.", testTag = "field_zone1End",
            )
            NumberSettingField(
                "Form 2B", draft.form2B, { onEdit { s -> s.copy(form2B = it.toFloat()) } },
                epoch = epoch, committed = committed.form2B, isInt = false,
                helper = "Boost in medium light.", testTag = "field_form2B",
            )
            NumberSettingField(
                "Form 2C (zone 2 offset)", draft.form2C, { onEdit { s -> s.copy(form2C = it.toInt()) } },
                epoch = epoch, committed = committed.form2C, error = errors.forField("form2C"),
                helper = "Midrange offset; must be ≤ zone 1 end.", testTag = "field_form2C",
            )
            NumberSettingField(
                "Zone 2 end (lux)", draft.zone2End, { onEdit { s -> s.copy(zone2End = it.toInt()) } },
                epoch = epoch, committed = committed.zone2End, error = errors.forField("zone2End"),
                helper = "Where indoor zone 2 ends and bright zone 3 begins.", testTag = "field_zone2End",
            )

            // task659 live-derived continuity coefficients (task613/614/615 _UpdateBrightnessFormulae).
            SectionHeader("Derived (continuity)")
            val coeffs = draft.derivedCoefficients()
            DerivedReadout("form2A", coeffs.form2A.fmtCoeff("%.3f"), testTag = "derived_form2A")
            DerivedReadout("form3A", coeffs.form3A.fmtCoeff("%.1f"), testTag = "derived_form3A")
            errors.forField("form2A")?.let { ErrorBanner(it, "error_form2A") }
            errors.forField("form3A")?.let { ErrorBanner(it, "error_form3A") }
            errors.forField("zone2End")?.let { ErrorBanner(it, "error_zone2End") }
            errors.forField("safetyBrightness")?.let { ErrorBanner(it, "error_safetyBrightness") }
        }
    }
}

/** Format a derived coefficient, guarding the NaN that an inverted zone range produces (G2-F6). */
private fun Double.fmtCoeff(fmt: String): String = if (isNaN()) "—" else fmt.format(this)

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
