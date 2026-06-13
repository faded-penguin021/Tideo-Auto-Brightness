package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.settings.toBrightnessCurveConfig
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.state.derivedCoefficients
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart

internal fun List<FieldError>.forField(name: String): String? = firstOrNull { it.field == name }?.message

/** Curve & Brightness (Tasker AAB Brightness Settings + Brightness Graph). */
@Composable
fun CurveBrightnessScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    CurveBrightnessContent(settings, errors, onBack = { navController.popBackStack() }, onUpdate = vm::update)
}

@Composable
fun CurveBrightnessContent(
    settings: AabSettings,
    errors: List<FieldError>,
    onBack: () -> Unit,
    onUpdate: ((AabSettings) -> AabSettings) -> Unit,
) {
    SettingsScaffold("Curve & Brightness", onBack) { padding ->
        SettingsColumn(padding) {
            BrightnessCurveChart(settings.toBrightnessCurveConfig(), modifier = Modifier.testTag("brightness_curve_chart"))

            SectionHeader("Brightness range")
            NumberSettingField(
                "Min brightness", settings.minBrightness, { onUpdate { s -> s.copy(minBrightness = it.toInt()) } },
                helper = "Lowest level the screen will use (1–255).", testTag = "field_minBrightness",
            )
            NumberSettingField(
                "Max brightness", settings.maxBrightness, { onUpdate { s -> s.copy(maxBrightness = it.toInt()) } },
                helper = "Highest level the screen will use (1–255).", testTag = "field_maxBrightness",
            )
            NumberSettingField(
                "Offset", settings.offset, { onUpdate { s -> s.copy(offset = it.toInt()) } },
                helper = "A flat boost or cut applied to the whole curve.", testTag = "field_offset",
            )
            NumberSettingField(
                "Scale", settings.scale, { onUpdate { s -> s.copy(scale = it.toFloat()) } },
                isInt = false, helper = "Global multiplier for the entire curve.", testTag = "field_scale",
            )

            SectionHeader("Curve zones")
            NumberSettingField(
                "Zone 1 end (lux)", settings.zone1End, { onUpdate { s -> s.copy(zone1End = it.toInt()) } },
                helper = "Where dim-light zone 1 ends and zone 2 begins.", testTag = "field_zone1End",
            )
            NumberSettingField(
                "Zone 2 end (lux)", settings.zone2End, { onUpdate { s -> s.copy(zone2End = it.toInt()) } },
                helper = "Where indoor zone 2 ends and bright zone 3 begins.", testTag = "field_zone2End",
            )
            NumberSettingField(
                "Form 1A", settings.form1A, { onUpdate { s -> s.copy(form1A = it.toInt()) } },
                helper = "How quickly brightness rises in dim light.", testTag = "field_form1A",
            )
            NumberSettingField(
                "Form 2B", settings.form2B, { onUpdate { s -> s.copy(form2B = it.toFloat()) } },
                isInt = false, helper = "Boost in medium light.", testTag = "field_form2B",
            )
            NumberSettingField(
                "Form 2C (zone 2 offset)", settings.form2C, { onUpdate { s -> s.copy(form2C = it.toInt()) } },
                error = errors.forField("form2C"),
                helper = "Midrange offset; must be ≤ zone 1 end.", testTag = "field_form2C",
            )

            // task659 live-derived continuity coefficients (task613/614/615 _UpdateBrightnessFormulae).
            SectionHeader("Derived (continuity)")
            val coeffs = settings.derivedCoefficients()
            DerivedReadout("form2A", "%.3f".format(coeffs.form2A), testTag = "derived_form2A")
            DerivedReadout("form3A", "%.1f".format(coeffs.form3A), testTag = "derived_form3A")
            errors.forField("form2A")?.let { ErrorBanner(it, "error_form2A") }
            errors.forField("form3A")?.let { ErrorBanner(it, "error_form3A") }
            errors.forField("safetyBrightness")?.let { ErrorBanner(it, "error_safetyBrightness") }
        }
    }
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
