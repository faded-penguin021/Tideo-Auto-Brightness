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

/**
 * THE chart template (Tasker: AAB Brightness Graph / task663 `_GenerateGraph`). Samples the domain
 * `mapLuxToBrightness` over a log-spaced lux grid and draws lux→brightness, on the reusable
 * [ChartCanvas].
 *
 * Series (S12.7g):
 *   - **Curve** (primary, solid) = the live [curve] = the draft the user is editing — it tracks edits.
 *     When the user previews a wizard suggestion it is loaded INTO that draft (D-125), so this line
 *     also *is* the suggested fit during a preview (no separate auto-drawn line).
 *   - **Reference** (gold, dashed) = the FIXED [referenceCurve] — the HARDCODED baseline curve (Tasker
 *     task663 `ref_data`: the AabSettings defaults), so a draft edit — or a previewed suggestion — shows
 *     *against* the fixed reference, like the Tasker graph (D-125, corrects F69's committed snapshot).
 *     It does NOT move with the draft or committed.
 *   - **Overrides** = the recorded manual-override points as tappable scatter dots (tap → delete, F36).
 *
 * **S13 / Haiku: copy this pattern exactly for the other six charts.** The recipe is: sample a domain
 * function over a grid → `List<Offset>` in data-space, wrap each line in a [ChartSeries], add markers/
 * scatter, hand it to [ChartCanvas] with the right [AxisScale] + ranges. Keep math here, not in
 * ChartCanvas.
 */
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
    // brightness_graph.md: the Tasker x-axis is 41 log-spaced lux values 0.1 → 100000 (F55).
    val minLux = 0.1f
    val maxLux = 100_000f
    val samples = 80

    fun sample(c: BrightnessCurveConfig): List<Offset> = logSpaced(minLux, maxLux, samples).map { lux ->
        val b = engine.mapLuxToBrightness(lux.toDouble(), c)
            .coerceIn(c.minBrightness.toDouble(), c.maxBrightness.toDouble())
        Offset(lux, b.toFloat())
    }

    // 1. live curve through mapLuxToBrightness, floored at minBrightness (G2-F4).
    val curvePoints = sample(curve)

    // 2. FIXED reference = the hardcoded baseline curve (dashed gold), never the draft/committed (D-125).
    val referencePoints = referenceCurve?.let { sample(it) }

    // D-125: there is no separate auto-drawn "Suggested" line. A wizard suggestion is previewed by
    // loading it into the draft (the "Curve" line above), so it shows against the dashed "Reference"
    // (committed) curve — the same two-line comparison, but only when the USER previews it.
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
