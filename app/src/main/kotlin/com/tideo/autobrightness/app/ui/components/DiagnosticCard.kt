package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabMono
import com.tideo.autobrightness.app.ui.theme.AabTeal
import java.util.Calendar

/**
 * The reusable glass-box **diagnostic card** (S12.6b, G2R-F7/F8). The Tasker AAB scenes embed live
 * `%AAB_*` readouts beneath their controls; these cards rebuild that on the relevant parameter
 * screens (and the dedicated Live Debug scene) from the live [PipelineState]. Live values render in
 * the AAB gold accent (`#FFC107`, [AabGold]) exactly like the Tasker debug HTML's strong-value colour.
 */
@Composable
fun DiagnosticCard(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AabTeal)
            content()
        }
    }
}

/** One diagnostic line; [build] appends plain text + gold [GoldenValue] runs into an annotated string. */
@Composable
fun DiagnosticLine(testTag: String? = null, build: AnnotatedString.Builder.() -> Unit) {
    val text = buildAnnotatedString { build() }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
    )
}

/** Append a live value highlighted in the AAB gold accent (the Tasker debug "strong" colour). S13c':
 *  the gold run is set in Plex Mono with tabular figures so the glass-box readouts read as instrument
 *  data, not inline prose. */
fun AnnotatedString.Builder.goldValue(value: String) {
    withStyle(
        SpanStyle(
            color = AabGold,
            fontFamily = AabMono,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
    ) { append(value) }
}

internal fun fmt(value: Double?, digits: Int = 1): String =
    value?.let { String.format("%.${digits}f", it) } ?: "—"

internal fun fmtInt(value: Int?): String = value?.toString() ?: "—"

/**
 * Format a 0..1 reactivity fraction as a whole percentage (G2R-F56): the Tasker scenes bind the
 * threshold readouts to the `%aab_thresh*pc` percentage variables, so 0.5 must read "50%". Rounds to
 * the nearest whole percent (the on-screen Tasker value carries no decimals).
 */
internal fun fmtPercent(value: Double?): String =
    value?.let { "${Math.round(it * 100.0)}%" } ?: "—"

/**
 * Format the smoothing alpha for DISPLAY, clamped to ≥ 0 (G2R-F86). The engine value is intentionally
 * left unclamped (Tasker task535 parity, D-010a — `domain/` untouched); a brief transient can compute a
 * small negative `1 - exp(-Δ·effectiveDelta)` which is meaningless to show, so only the readout floors.
 */
internal fun fmtAlpha(value: Double?): String = fmt(value?.coerceAtLeast(0.0), 3)

private fun nowHhMm(): String {
    val c = Calendar.getInstance()
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

// --- Reactivity screen card (G2R-F7) ------------------------------------------------------------

/**
 * Stateless Reactivity diagnostic: "Current threshold [%AAB_ThreshDynamic] at [%SmoothedLux] lx;
 * Sensor dead zone [%AAB_ThreshAbsLow]–[%AAB_ThreshAbsHigh] lx" (G2R-F7 verbatim).
 */
@Composable
fun ReactivityDiagnosticCardContent(state: PipelineState) {
    DiagnosticCard("Live reactivity", "reactivity_diagnostic_card") {
        DiagnosticLine("diag_reactivity_threshold") {
            append("Current threshold ")
            // G2R-F56: the live reactivity threshold (%AAB_ThreshDynamic, a 0..1 fraction) reads as a
            // percentage in the Tasker scene (bound to %aab_thresh*pc) — 0.5 → "50%".
            goldValue(fmtPercent(state.threshDynamic))
            append(" at ")
            goldValue(fmt(state.smoothedLux))
            append(" lx")
        }
        DiagnosticLine("diag_reactivity_deadzone") {
            append("Sensor dead zone ")
            goldValue(fmt(state.threshAbsLow))
            append(" – ")
            goldValue(fmt(state.threshAbsHigh))
            append(" lx")
        }
    }
}

/** Live wrapper: collects the pipeline snapshot and renders the Reactivity diagnostic card. */
@Composable
fun ReactivityDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    ReactivityDiagnosticCardContent(state)
}

// --- Circadian screen card (G2R-F8) -------------------------------------------------------------

/**
 * Stateless Circadian diagnostic: "Uncompressed scale [%AAB_ScaleDynamic] at [%TIME]; True scale
 * [%AAB_ScaleDynamicCompress] at [%AAB_CurrentBright] brightness ([%AAB_MinBright]–[%AAB_MaxBright])"
 * (G2R-F8 verbatim). [timeLabel] is the current local HH:mm.
 */
@Composable
fun CircadianDiagnosticCardContent(
    state: PipelineState,
    minBrightness: Int,
    maxBrightness: Int,
    timeLabel: String,
) {
    DiagnosticCard("Live circadian scale", "circadian_diagnostic_card") {
        DiagnosticLine("diag_circadian_uncompressed") {
            append("Uncompressed scale ")
            goldValue(fmt(state.scaleDynamic, 3))
            append(" at ")
            goldValue(timeLabel)
        }
        DiagnosticLine("diag_circadian_true") {
            append("True scale ")
            goldValue(fmt(state.scaleDynamicCompress, 3))
            append(" at ")
            goldValue(fmtInt(state.lastAppliedBrightness))
            append(" brightness (")
            goldValue(minBrightness.toString())
            append("–")
            goldValue(maxBrightness.toString())
            append(")")
        }
    }
}

/** Live wrapper: collects the pipeline snapshot and renders the Circadian diagnostic card. */
@Composable
fun CircadianDiagnosticCard(minBrightness: Int, maxBrightness: Int) {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    CircadianDiagnosticCardContent(state, minBrightness, maxBrightness, nowHhMm())
}

// --- Curve & Brightness screen card (G2R-F58) ---------------------------------------------------

/**
 * Stateless Curve & Brightness live readout (Tasker `current_lux_and_bright`, brightness_settings.md
 * elements22): "Current smoothed lux [%SmoothedLux]" + "Current brightness (%AAB_MinBright–
 * %AAB_MaxBright) [%AAB_CurrentBright]" (G2R-F58). [minBrightness]/[maxBrightness] are the committed
 * range; the brightness shown is the PERCEIVED value (D-117).
 */
@Composable
fun CurveBrightnessDiagnosticCardContent(state: PipelineState, minBrightness: Int, maxBrightness: Int) {
    DiagnosticCard("Live brightness", "curve_diagnostic_card") {
        DiagnosticLine("diag_curve_smoothed_lux") {
            append("Current smoothed lux ")
            goldValue(fmt(state.smoothedLux))
        }
        DiagnosticLine("diag_curve_current_bright") {
            append("Current brightness (")
            goldValue(minBrightness.toString())
            append("–")
            goldValue(maxBrightness.toString())
            append(") ")
            // D-117: show the PERCEIVED brightness (un-floored targetBrightness) like the Dashboard and
            // the curve graph's "Now" line. In PWM-sensitive mode lastAppliedBrightness is the floored
            // hardware value held at the dimming threshold; the perceived value is what the screen looks
            // like. Falls back to the applied value when equal (PWM-sensitive off → target == applied).
            goldValue(fmtInt(state.targetBrightness ?: state.lastAppliedBrightness))
        }
    }
}

/** Live wrapper: collects the pipeline snapshot and renders the Curve & Brightness readout. */
@Composable
fun CurveBrightnessDiagnosticCard(minBrightness: Int, maxBrightness: Int) {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    CurveBrightnessDiagnosticCardContent(state, minBrightness, maxBrightness)
}

// --- Misc screen card (G2R-F58) -----------------------------------------------------------------

/**
 * Stateless Misc live readout (Tasker `current_throttle_and_alpha`, misc_settings.md elements31):
 * "Current throttle [%AAB_Throttle] ms" + "Current smoothing α [%LuxAlpha]" (G2R-F58).
 */
@Composable
fun MiscDiagnosticCardContent(state: PipelineState) {
    DiagnosticCard("Live timing", "misc_diagnostic_card") {
        DiagnosticLine("diag_misc_throttle") {
            append("Current throttle ")
            goldValue(state.throttleMs?.toString() ?: "—")
            append(" ms")
        }
        DiagnosticLine("diag_misc_alpha") {
            append("Current smoothing α ")
            // G2R-F86: display clamps to ≥ 0 (engine value left unclamped for parity).
            goldValue(fmtAlpha(state.luxAlpha))
        }
    }
}

/** Live wrapper: collects the pipeline snapshot and renders the Misc readout. */
@Composable
fun MiscDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    MiscDiagnosticCardContent(state)
}

// --- Super Dimming screen card (G2R-F58) --------------------------------------------------------

/**
 * Stateless Super Dimming live readout (superdimming_settings.md): "Dimming strength (rel)
 * [%AAB_DimmingCurrent]" + "Dimming level (abs) [%AAB_DimmingDS]" + "at [%AAB_CurrentBright]
 * brightness" (G2R-F58). The values are 0 while dimming is not engaged (target ≥ threshold).
 */
@Composable
fun SuperDimmingDiagnosticCardContent(state: PipelineState) {
    DiagnosticCard("Live super dimming", "super_dimming_diagnostic_card") {
        DiagnosticLine("diag_dimming_rel") {
            append("Dimming strength (rel) ")
            goldValue(fmt(state.dimmingCurrent, 1))
        }
        DiagnosticLine("diag_dimming_abs") {
            append("Dimming level (abs) ")
            goldValue(fmt(state.dimmingDS, 1))
            append(" at ")
            goldValue(fmtInt(state.lastAppliedBrightness))
            append(" brightness")
        }
    }
}

/** Live wrapper: collects the pipeline snapshot and renders the Super Dimming readout. */
@Composable
fun SuperDimmingDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    SuperDimmingDiagnosticCardContent(state)
}
