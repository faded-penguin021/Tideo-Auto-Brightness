package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import kotlin.math.exp

private val engine = BrightnessEngine()

/** AAB Reactivity Graph (Tasker task703, %AAB_HTML_Graph2). */
@Composable
fun ReactivityChart(
    threshold: ThresholdConfig,
    modifier: Modifier = Modifier,
    // S14: live smoothed lux as "Now" line; null (service off) → no line.
    currentLux: Double? = null,
) {
    val minLux = 1f
    val maxLux = 100_000f
    val samples = 80

    fun sample(cfg: ThresholdConfig): List<Offset> = logSpaced(minLux, maxLux, samples).map { lux ->
        val pct = engine.dynamicThreshold(lux.toDouble(), lux.toDouble(), cfg) * 100.0
        Offset(lux, pct.toFloat())
    }

    val referenceCfg = ThresholdConfig(
        threshDark = 0.30, threshDim = 0.25, threshBright = 0.08,
        threshSteepness = 2.1, threshMidpoint = 4.0, zone1End = 35.0,
    )

    val curvePoints = sample(threshold)
    val referencePoints = sample(referenceCfg)
    val series = listOf(
        ChartSeries(stringResource(R.string.chart_reference), referencePoints, AabGold, strokeWidthPx = 3f, dashed = true),
        ChartSeries(stringResource(R.string.chart_curve), curvePoints, MaterialTheme.colorScheme.primary),
    )

    // Dynamic y-axis: frame to data, keeping 0 baseline.
    val yMax = ((curvePoints + referencePoints).maxOf { it.y } * 1.2f).coerceAtLeast(1f)

    val markers = currentLux?.let {
        listOf(ChartMarker(color = MaterialTheme.colorScheme.error, x = it.toFloat().coerceIn(minLux, maxLux), label = stringResource(R.string.chart_now)))
    } ?: emptyList()

    ChartCanvas(
        series = series,
        xRange = minLux..maxLux,
        yRange = 0f..yMax,
        xScale = AxisScale.Log10,
        markers = markers,
        xAxisLabel = stringResource(R.string.chart_lux),
        yAxisLabel = stringResource(R.string.chart_threshold_pct),
        showLegend = true,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        contentDescription = stringResource(R.string.a11y_graph_reactivity),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}

/** AAB Alpha Graph (Tasker task557, %AAB_HTML_Graph3). Smoothing response overlay. */
@Composable
fun AlphaResponseChart(
    deltaFactor: Double,
    modifier: Modifier = Modifier,
    // G3-F2/F15: live smoothing response (%AAB_LuxAlpha) as horizontal "Now" line.
    currentAlpha: Double? = null,
) {
    val minPercent = 1f
    val maxPercent = 2000f
    val samples = 80

    fun sample(factor: Double): List<Offset> = logSpaced(minPercent, maxPercent, samples).map { pct ->
        val fraction = pct / 100.0
        val alpha = 1.0 - exp(-factor * fraction)
        Offset(pct, alpha.toFloat())
    }

    val series = listOf(
        ChartSeries(stringResource(R.string.chart_reference), sample(1.8), AabGold, strokeWidthPx = 3f, dashed = true),
        ChartSeries(stringResource(R.string.chart_curve), sample(deltaFactor), MaterialTheme.colorScheme.primary),
    )

    val markers = currentAlpha?.let {
        listOf(ChartMarker(color = MaterialTheme.colorScheme.error, y = it.toFloat().coerceIn(0f, 1f), label = stringResource(R.string.chart_now)))
    } ?: emptyList()

    ChartCanvas(
        series = series,
        xRange = minPercent..maxPercent,
        yRange = 0f..1f,
        xScale = AxisScale.Log10,
        markers = markers,
        xAxisLabel = stringResource(R.string.chart_rel_lux_pct),
        yAxisLabel = stringResource(R.string.chart_alpha),
        showLegend = true,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        contentDescription = stringResource(R.string.a11y_graph_alpha),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
