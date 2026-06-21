package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.app.ui.theme.AabChartBlue
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine

private val engine = BrightnessEngine()

/**
 * AAB Taper Graph (Tasker: task657 `_GenerateCompressionGraph`, feeds %AAB_HTML_Graph5). Shows how the
 * day/night scale spread is tapered by the compression sigmoid as brightness approaches the extremes.
 *
 * Built on the [BrightnessCurveChart] template: for each brightness level the **effective** day and
 * night scale come straight from `BrightnessEngine.compressedDynamicScale` (the same taper math the
 * runtime applies), so the chart is the live function, not a transcription.
 *
 * - X-axis: mapped brightness level (linear, `minBright` → `maxBright`).
 * - Y-axis: effective scaling multiplier around 1.0 (>1 day boost, <1 night reduction).
 * - **Day** (primary): `compressedDynamicScale(b, 1 + spread/100).effectiveScale`.
 * - **Night** (gold): `compressedDynamicScale(b, 1 − spread/100).effectiveScale`.
 */
@Composable
fun TaperChart(
    curve: BrightnessCurveConfig,
    scaleSpreadPercent: Int,
    modifier: Modifier = Modifier,
) {
    val xStart = curve.minBrightness.toFloat()
    val xEnd = curve.maxBrightness.toFloat().coerceAtLeast(xStart + 1f)
    val span = (xEnd - xStart).toInt().coerceAtLeast(1)
    val dayScale = 1.0 + scaleSpreadPercent / 100.0
    val nightScale = 1.0 - scaleSpreadPercent / 100.0

    val dayPoints = ArrayList<Offset>(span + 1)
    val nightPoints = ArrayList<Offset>(span + 1)
    for (i in 0..span) {
        val b = xStart + i
        dayPoints += Offset(b, engine.compressedDynamicScale(b.toDouble(), dayScale, curve).effectiveScale.toFloat())
        nightPoints += Offset(b, engine.compressedDynamicScale(b.toDouble(), nightScale, curve).effectiveScale.toFloat())
    }

    // Frame the y-axis around 1.0 with a little headroom (the 1.0 baseline is always visible).
    val ys = (dayPoints + nightPoints).map { it.y } + 1f
    val yMin = ys.min() - 0.05f
    val yMax = ys.max() + 0.05f

    // Day = primary teal, Night = blue (gold is reserved for reference lines, Tasker convention).
    val series = listOf(
        ChartSeries("Night", nightPoints, AabChartBlue, strokeWidthPx = 3f),
        ChartSeries("Day", dayPoints, MaterialTheme.colorScheme.primary),
    )

    ChartCanvas(
        series = series,
        xRange = xStart..xEnd,
        yRange = yMin..yMax,
        markers = listOf(ChartMarker(color = MaterialTheme.colorScheme.outline, y = 1f)),
        xAxisLabel = "Brightness",
        yAxisLabel = "Scale ×",
        showLegend = true,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
