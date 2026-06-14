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

/** Append a live value highlighted in the AAB gold accent (the Tasker debug "strong" colour). */
fun AnnotatedString.Builder.goldValue(value: String) {
    withStyle(SpanStyle(color = AabGold, fontWeight = FontWeight.SemiBold)) { append(value) }
}

internal fun fmt(value: Double?, digits: Int = 1): String =
    value?.let { String.format("%.${digits}f", it) } ?: "—"

internal fun fmtInt(value: Int?): String = value?.toString() ?: "—"

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
            goldValue(fmt(state.threshDynamic))
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
