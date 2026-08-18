package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabChartBlue
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import kotlin.math.log10
import kotlin.math.pow

private val engine = BrightnessEngine()

/** Brightness curve chart (task663): curve, fixed reference, overrides (D-125, F36, F69). */
@Composable
fun BrightnessCurveChart(
    curve: BrightnessCurveConfig,
    modifier: Modifier = Modifier,
    currentLux: Double? = null,
    currentBrightness: Int? = null,
    overridePoints: List<Offset> = emptyList(),
    referenceCurve: BrightnessCurveConfig? = null,
    onDeleteOverridePoint: ((Offset) -> Unit)? = null,
) {
    // F55: log-spaced lux 0.1 → 100000.
    val minLux = 0.1f
    val maxLux = 100_000f
    val samples = 80

    fun sample(c: BrightnessCurveConfig): List<Offset> = logSpaced(minLux, maxLux, samples).map { lux ->
        val b = engine.mapLuxToBrightness(lux.toDouble(), c)
            .coerceIn(c.minBrightness.toDouble(), c.maxBrightness.toDouble())
        Offset(lux, b.toFloat())
    }

    // Curve (G2-F4); fixed reference (D-125).
    val curvePoints = sample(curve)
    val referencePoints = referenceCurve?.let { sample(it) }

    // D-125: no separate suggested line; suggestion loaded into draft (preview against reference).
    val series = buildList {
        referencePoints?.let { add(ChartSeries(stringResource(R.string.chart_reference), it, AabGold, strokeWidthPx = 3f, dashed = true)) }
        add(ChartSeries(stringResource(R.string.chart_curve), curvePoints, MaterialTheme.colorScheme.primary))
    }

    val scatter = if (overridePoints.isNotEmpty()) {
        ChartScatter(
            points = overridePoints,
            color = AabChartBlue,
            onTap = onDeleteOverridePoint,
        )
    } else {
        null
    }

    val markers = buildList {
        if (currentLux != null && currentBrightness != null) {
            add(ChartMarker(color = MaterialTheme.colorScheme.error, x = currentLux.toFloat()))
            add(ChartMarker(color = MaterialTheme.colorScheme.error, y = currentBrightness.toFloat()))
        }
    }

    ChartCanvas(
        series = series,
        xRange = minLux..maxLux,
        yRange = 0f..curve.maxBrightness.toFloat(),
        xScale = AxisScale.Log10,
        markers = markers,
        scatter = scatter,
        xAxisLabel = stringResource(R.string.chart_lux),
        yAxisLabel = stringResource(R.string.chart_brightness),
        showLegend = true,
        interactive = true,
        contentDescription = stringResource(R.string.a11y_graph_brightness_curve, curve.minBrightness, curve.maxBrightness),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}

internal fun logSpaced(min: Float, max: Float, count: Int): List<Float> {
    val lo = log10(min.coerceAtLeast(1e-3f))
    val hi = log10(max.coerceAtLeast(min))
    return (0 until count).map { i ->
        10f.pow(lo + (hi - lo) * i / (count - 1).coerceAtLeast(1)).coerceIn(min, max)
    }
}
