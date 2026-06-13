package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import kotlin.math.log10
import kotlin.math.pow

private val engine = BrightnessEngine()

/**
 * THE chart template (Tasker: AAB Brightness Graph / task663 `_GenerateGraph`). Samples the domain
 * `mapLuxToBrightness` over a log-spaced lux grid and draws lux→brightness with a taper overlay and
 * an optional current-operating-point marker, on the reusable [ChartCanvas].
 *
 * **S13 / Haiku: copy this pattern exactly for the other six charts.** The recipe is:
 *   1. sample a domain function over a grid → `List<Offset>` in data-space,
 *   2. wrap each line in a [ChartSeries] with an M3 color,
 *   3. add any threshold/marker via [ChartMarker],
 *   4. hand it all to [ChartCanvas] with the right [AxisScale] and ranges.
 * Do NOT compute anything chart-specific inside ChartCanvas; keep the math here.
 */
@Composable
fun BrightnessCurveChart(
    curve: BrightnessCurveConfig,
    modifier: Modifier = Modifier,
    currentLux: Double? = null,
    currentBrightness: Int? = null,
) {
    val minLux = 1f
    val maxLux = 120_000f
    val samples = 80

    // 1. sample mapLuxToBrightness over a log-spaced lux grid (the "new curve" series)
    val curvePoints = logSpaced(minLux, maxLux, samples).map { lux ->
        val b = engine.mapLuxToBrightness(lux.toDouble(), curve)
            .coerceIn(0.0, curve.maxBrightness.toDouble())
        Offset(lux, b.toFloat())
    }

    // taper overlay: the same curve through calculatedBrightness (scaling/taper applied at scale=1)
    val taperPoints = logSpaced(minLux, maxLux, samples).map { lux ->
        val b = engine.calculatedBrightness(lux.toDouble(), curve, scaleDynamic = 1.0)
        Offset(lux, b.toFloat())
    }

    val series = buildList {
        add(ChartSeries("Curve", curvePoints, MaterialTheme.colorScheme.primary))
        if (curve.scalingUse) add(ChartSeries("Taper", taperPoints, MaterialTheme.colorScheme.tertiary, strokeWidthPx = 2f))
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
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}

/** Log-spaced grid from [min] to [max] inclusive (shared sampling helper for chart instances). */
internal fun logSpaced(min: Float, max: Float, count: Int): List<Float> {
    val lo = log10(min.coerceAtLeast(1e-3f))
    val hi = log10(max.coerceAtLeast(min))
    return (0 until count).map { i ->
        10f.pow(lo + (hi - lo) * i / (count - 1).coerceAtLeast(1)).coerceIn(min, max)
    }
}
