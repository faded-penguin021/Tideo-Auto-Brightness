package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.app.ui.theme.AabChartBlue
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import kotlin.math.max
import kotlin.math.pow

/**
 * AAB Dimming Graph (Tasker: task556 `_GenerateDimmingCurveGraph`, feeds %AAB_HTML_Graph6). Follows
 * the [BrightnessCurveChart] template — sample the domain super-dimming math over a brightness grid.
 *
 * - X-axis: target brightness level (linear, `minBright` → `max(dimmingThreshold, 15)`).
 * - Y-axis: dim progress percentage (0..100) + the strength-weighted dim shell.
 * - **Dim progress** (primary): `SoftwareDimming.dimProgress × 100` for `b < threshold`, else 0.
 * - **Dim shell** (gold): `dimmingStrength × dimProgress` — the applied dimming magnitude.
 * - **Reference** (dashed blue): `pow(1 − b/15, 2.5) × 100` for `b < 15` (dimming_graph.md ref curve).
 */
@Composable
fun DimmingChart(
    minBrightness: Int,
    dimmingThreshold: Int,
    dimmingExponent: Double,
    dimmingStrength: Int,
    modifier: Modifier = Modifier,
) {
    val xStart = minBrightness.toFloat()
    // dimming_graph.md: loop minbright → max(dimmingthreshold, 15).
    val xEnd = max(dimmingThreshold, 15).toFloat()
    val span = (xEnd - xStart).toInt().coerceAtLeast(1)

    val progressPoints = ArrayList<Offset>(span + 1)
    val shellPoints = ArrayList<Offset>(span + 1)
    val referencePoints = ArrayList<Offset>(span + 1)

    for (i in 0..span) {
        val b = xStart + i
        val progress = if (b < dimmingThreshold) {
            SoftwareDimming.dimProgress(b.toDouble(), minBrightness.toDouble(), dimmingThreshold.toDouble(), dimmingExponent)
        } else {
            0.0
        }
        progressPoints += Offset(b, (progress * 100.0).toFloat())
        // dim_ds_points = dimmingstrength * dim_progress (the applied magnitude, dimming_graph.md).
        shellPoints += Offset(b, (dimmingStrength * progress).toFloat())
        val ref = if (b < 15f) (1.0 - b / 15.0).pow(2.5) * 100.0 else 0.0
        referencePoints += Offset(b, ref.toFloat())
    }

    val series = listOf(
        ChartSeries("Reference", referencePoints, AabChartBlue, strokeWidthPx = 2f, dashed = true),
        ChartSeries("Dim shell", shellPoints, AabGold, strokeWidthPx = 3f),
        ChartSeries("Dim %", progressPoints, MaterialTheme.colorScheme.primary),
    )

    ChartCanvas(
        series = series,
        xRange = xStart..xEnd,
        yRange = 0f..100f,
        xScale = AxisScale.Linear,
        xAxisLabel = "Brightness",
        yAxisLabel = "Dim %",
        showLegend = true,
        interactive = true,
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
