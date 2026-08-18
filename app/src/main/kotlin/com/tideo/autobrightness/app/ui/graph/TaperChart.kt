package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabChartBlue
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine

private val engine = BrightnessEngine()

/** AAB Taper Graph (Tasker task657, %AAB_HTML_Graph5). Day/night scale spread taper. */
@Composable
fun TaperChart(
    curve: BrightnessCurveConfig,
    scaleSpreadPercent: Int,
    modifier: Modifier = Modifier,
    // S14: current applied brightness as "Now" line; null (scaling inactive) → no marker.
    currentBrightness: Int? = null,
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

    // Y-axis framed around 1.0 with headroom.
    val ys = (dayPoints + nightPoints).map { it.y } + 1f
    val yMin = ys.min() - 0.05f
    val yMax = ys.max() + 0.05f

    val series = listOf(
        ChartSeries(stringResource(R.string.chart_night), nightPoints, AabChartBlue, strokeWidthPx = 3f),
        ChartSeries(stringResource(R.string.chart_day), dayPoints, MaterialTheme.colorScheme.primary),
    )

    val markers = buildList {
        add(ChartMarker(color = MaterialTheme.colorScheme.outline, y = 1f)) // 1.0 baseline
        currentBrightness?.let {
            add(ChartMarker(color = MaterialTheme.colorScheme.error, x = it.toFloat().coerceIn(xStart, xEnd), label = stringResource(R.string.chart_now)))
        }
    }

    ChartCanvas(
        series = series,
        xRange = xStart..xEnd,
        yRange = yMin..yMax,
        markers = markers,
        xAxisLabel = stringResource(R.string.chart_brightness),
        yAxisLabel = stringResource(R.string.chart_scale_x),
        showLegend = true,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        contentDescription = stringResource(R.string.a11y_graph_taper),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
