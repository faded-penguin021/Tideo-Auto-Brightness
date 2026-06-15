package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 *   - **Reference** (gold, dashed) = the FIXED [referenceCurve] snapshot (the committed/default curve),
 *     so a draft edit shows *against* where you started (F69). It does NOT move with the draft.
 *   - **Suggested** (secondary) = the wizard fit, shown only once ≥ 9 override points exist (F62/G2R-F14).
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
    fittedCurve: BrightnessCurveConfig? = null,
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

    // 2. FIXED reference = the committed/default snapshot (dashed gold), never the live draft (F69).
    val referencePoints = referenceCurve?.let { sample(it) }

    // 3. task38/task663 suggested (fitted) curve, only once ≥ 9 override points exist (G2R-F14/F62).
    val fittedPoints = fittedCurve?.let { sample(it) }

    val series = buildList {
        referencePoints?.let { add(ChartSeries("Reference", it, AabGold, strokeWidthPx = 3f, dashed = true)) }
        add(ChartSeries("Curve", curvePoints, MaterialTheme.colorScheme.primary))
        fittedPoints?.let { add(ChartSeries("Suggested", it, MaterialTheme.colorScheme.secondary, strokeWidthPx = 2f)) }
    }

    val scatter = if (overridePoints.isNotEmpty()) {
        ChartScatter(
            points = overridePoints,
            color = MaterialTheme.colorScheme.error,
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
        xAxisLabel = "Lux",
        yAxisLabel = "Brightness",
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
