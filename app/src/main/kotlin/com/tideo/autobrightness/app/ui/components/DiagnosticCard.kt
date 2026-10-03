package com.tideo.autobrightness.app.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabMono
import com.tideo.autobrightness.app.ui.theme.AabTeal
import java.math.BigDecimal
import java.math.RoundingMode
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

private val diagnosticArgument = Regex("""%([1-9][0-9]*)[$]s""")

/** Keeps numbered string arguments gold when translations reorder them. */
@Composable
fun DiagnosticLine(@StringRes textRes: Int, testTag: String, vararg values: String) {
    val template = stringResource(textRes)
    DiagnosticLine(testTag) {
        var end = 0
        diagnosticArgument.findAll(template).forEach { argument ->
            append(template.substring(end, argument.range.first))
            goldValue(values[argument.groupValues[1].toInt() - 1])
            end = argument.range.last + 1
        }
        append(template.substring(end))
    }
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

private fun fmtStored(value: Double, movePoint: Int, maxDigits: Int): String {
    if (!value.isFinite()) return value.toString()
    val stored = BigDecimal.valueOf(value).movePointRight(movePoint)
        .setScale(maxDigits, RoundingMode.HALF_UP).stripTrailingZeros()
    return String.format("%.${stored.scale().coerceAtLeast(0)}f", stored)
}

/** Lux as stored (task535: 2 dp below Zone1End, 0 dp above), capped at task554's 3 dp for raw floats. */
internal fun fmtLux(value: Double?): String = value?.let { fmtStored(it, 0, 3) } ?: "—"

/** G2R-F56: a round3 0..1 fraction as a percentage at its full precision (%AAB_ThreshDynamic is a percent). */
internal fun fmtPercent(value: Double?): String = value?.let { "${fmtStored(it, 2, 1)}%" } ?: "—"

/** G2R-F86: display clamps alpha to ≥0 (engine unclamped for task535 parity, D-010(a)). */
internal fun fmtAlpha(value: Double?): String = fmt(value?.coerceAtLeast(0.0), 3)

@Composable
internal fun relativeAgeLabel(ms: Long?, now: Long = System.currentTimeMillis()): String {
    if (ms == null) return stringResource(R.string.relative_time_never)
    val secs = ((now - ms) / 1000L).coerceAtLeast(0L)
    return when {
        secs < 1L -> stringResource(R.string.relative_time_just_now)
        secs < 60L -> stringResource(R.string.relative_time_seconds_ago, secs)
        secs < 3600L -> stringResource(R.string.relative_time_minutes_ago, secs / 60L)
        else -> stringResource(R.string.relative_time_hours_ago, secs / 3600L)
    }
}

private fun nowHhMm(): String {
    val c = Calendar.getInstance()
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

// G2R-F7: Reactivity screen card

/** G2R-F7: Reactivity diagnostic (%AAB_ThreshDynamic, sensor dead zone). */
@Composable
fun ReactivityDiagnosticCardContent(state: PipelineState) {
    DiagnosticCard(stringResource(R.string.diag_reactivity_title), "reactivity_diagnostic_card") {
        DiagnosticLine(
            R.string.diag_reactivity_threshold, "diag_reactivity_threshold",
            fmtPercent(state.threshDynamic), fmtLux(state.smoothedLux),
        )
        DiagnosticLine(
            R.string.diag_reactivity_deadzone, "diag_reactivity_deadzone",
            fmtLux(state.threshAbsLow), fmtLux(state.threshAbsHigh),
        )
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
    DiagnosticCard(stringResource(R.string.diag_circadian_title), "circadian_diagnostic_card") {
        DiagnosticLine(
            R.string.diag_circadian_uncompressed, "diag_circadian_uncompressed",
            fmt(state.scaleDynamic, 3), timeLabel,
        )
        DiagnosticLine(
            R.string.diag_circadian_true, "diag_circadian_true",
            fmt(state.scaleDynamicCompress, 3), fmtInt(state.lastAppliedBrightness),
            minBrightness.toString(), maxBrightness.toString(),
        )
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
    DiagnosticCard(stringResource(R.string.diag_curve_title), "curve_diagnostic_card") {
        DiagnosticLine(R.string.diag_curve_smoothed_lux, "diag_curve_smoothed_lux", fmtLux(state.smoothedLux))
        // D-117: PERCEIVED brightness (un-floored target); falls back to applied when equal.
        DiagnosticLine(
            R.string.diag_curve_current_bright, "diag_curve_current_bright",
            minBrightness.toString(), maxBrightness.toString(), fmtInt(state.targetBrightness ?: state.lastAppliedBrightness),
        )
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
    DiagnosticCard(stringResource(R.string.diag_misc_title), "misc_diagnostic_card") {
        DiagnosticLine(R.string.diag_misc_throttle, "diag_misc_throttle", state.throttleMs?.toString() ?: "—")
        DiagnosticLine(R.string.diag_misc_alpha, "diag_misc_alpha", fmtAlpha(state.luxAlpha))
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
    DiagnosticCard(stringResource(R.string.diag_dimming_title), "super_dimming_diagnostic_card") {
        DiagnosticLine(R.string.diag_dimming_rel, "diag_dimming_rel", fmt(state.dimmingCurrent, 1))
        DiagnosticLine(R.string.diag_dimming_abs, "diag_dimming_abs", fmt(state.dimmingDS, 1), fmtInt(state.lastAppliedBrightness))
    }
}

/** Live wrapper for Super Dimming readout. */
@Composable
fun SuperDimmingDiagnosticCard() {
    val state by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    SuperDimmingDiagnosticCardContent(state)
}
