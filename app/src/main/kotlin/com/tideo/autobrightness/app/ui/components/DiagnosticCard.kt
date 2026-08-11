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

/** S12.6b, G2R-F7/F8: diagnostic card with live %AAB_* readouts and AAB gold accents. */
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

/** Diagnostic line with optional gold-highlighted values. */
@Composable
fun DiagnosticLine(testTag: String? = null, build: AnnotatedString.Builder.() -> Unit) {
    val text = buildAnnotatedString { build() }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier,
    )
}

/** S13c': append value in AAB gold + Plex Mono tabular figures (instrument-style readout). */
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

/** G2R-F56: format 0..1 fraction as whole percentage (Tasker parity %aab_thresh*pc). */
internal fun fmtPercent(value: Double?): String =
    value?.let { "${Math.round(it * 100.0)}%" } ?: "—"

/** G2R-F86: display clamps alpha to ≥0 (engine unclamped for task535 parity, D-010(a)). */
internal fun fmtAlpha(value: Double?): String = fmt(value?.coerceAtLeast(0.0), 3)

private fun nowHhMm(): String {
    val c = Calendar.getInstance()
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

// G2R-F7: Reactivity screen card

/** G2R-F7: Reactivity diagnostic (%AAB_ThreshDynamic, sensor dead zone). */
@Composable
fun ReactivityDiagnosticCardContent(state: PipelineState) {
    DiagnosticCard("Live reactivity", "reactivity_diagnostic_card") {
        DiagnosticLine("diag_reactivity_threshold") {
            append("Current threshold ")
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

/** Live wrapper for Reactivity diagnostic. */
@Composable
fun ReactivityDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    ReactivityDiagnosticCardContent(state)
}

// G2R-F8: Circadian screen card

/** G2R-F8: Circadian diagnostic (%AAB_ScaleDynamic, compressed scale, brightness). */
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

/** Live wrapper for Circadian diagnostic. */
@Composable
fun CircadianDiagnosticCard(minBrightness: Int, maxBrightness: Int) {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    CircadianDiagnosticCardContent(state, minBrightness, maxBrightness, nowHhMm())
}

// G2R-F58: Curve & Brightness screen card

/** G2R-F58: Curve & Brightness readout (task535 current_lux_and_bright); shows PERCEIVED brightness (D-117). */
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
            // D-117: PERCEIVED brightness (un-floored target); falls back to applied when equal.
            goldValue(fmtInt(state.targetBrightness ?: state.lastAppliedBrightness))
        }
    }
}

/** Live wrapper for Curve & Brightness readout. */
@Composable
fun CurveBrightnessDiagnosticCard(minBrightness: Int, maxBrightness: Int) {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    CurveBrightnessDiagnosticCardContent(state, minBrightness, maxBrightness)
}

// G2R-F58: Misc screen card

/** G2R-F58: Misc readout (throttle, smoothing alpha). */
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
            goldValue(fmtAlpha(state.luxAlpha))
        }
    }
}

/** Live wrapper for Misc readout. */
@Composable
fun MiscDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    MiscDiagnosticCardContent(state)
}

// G2R-F58: Super Dimming screen card

/** G2R-F58: Super Dimming readout (strength, level, brightness). */
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

/** Live wrapper for Super Dimming readout. */
@Composable
fun SuperDimmingDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    SuperDimmingDiagnosticCardContent(state)
}
