package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.domain.brightness.BrightnessEngine
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import kotlin.math.exp

private val engine = BrightnessEngine()

/**
 * AAB Reactivity Graph (Tasker: task703 `_GenerateReactivityGraph`, feeds %AAB_HTML_Graph2).
 * Built on the [ChartCanvas] engine following the [BrightnessCurveChart] template exactly: sample a
 * domain function over a grid → `List<Offset>` in data-space, wrap each line in a [ChartSeries], hand
 * it to [ChartCanvas] with the right [AxisScale] + ranges.
 *
 * - X-axis: lux (log10, 1 → 100000 per reactivity_graph.md).
 * - Y-axis: reactivity threshold percentage (0..100) — the change magnitude required to trigger an
 *   update at a given lux.
 * - **Curve** (primary): the user's threshold curve = `BrightnessEngine.dynamicThreshold × 100`
 *   (linear below zone1End, sigmoid above — the exact runtime function, not a transcription).
 * - **Reference** (gold, dashed): the hardcoded reference curve (threshDark/Dim/Bright 0.30/0.25/0.08,
 *   steepness 2.1, midpoint 4, zone1End 35) — also evaluated through the same domain function.
 */
@Composable
fun ReactivityChart(
    threshold: ThresholdConfig,
    modifier: Modifier = Modifier,
) {
    val minLux = 1f
    val maxLux = 100_000f
    val samples = 80

    // reactivity_graph.md: percent = dynamicThreshold(lux) * 100. dynamicThreshold uses smoothedLux for
    // both the linear knee and the sigmoid's log10(lux+1); pass lux as both args.
    fun sample(cfg: ThresholdConfig): List<Offset> = logSpaced(minLux, maxLux, samples).map { lux ->
        val pct = engine.dynamicThreshold(lux.toDouble(), lux.toDouble(), cfg) * 100.0
        Offset(lux, pct.toFloat())
    }

    // The reference curve is the task703 hardcoded baseline expressed as a ThresholdConfig so it runs
    // through the identical domain function (reactivity_graph.md ref_data).
    val referenceCfg = ThresholdConfig(
        threshDark = 0.30, threshDim = 0.25, threshBright = 0.08,
        threshSteepness = 2.1, threshMidpoint = 4.0, zone1End = 35.0,
    )

    val series = listOf(
        ChartSeries("Reference", sample(referenceCfg), AabGold, strokeWidthPx = 3f, dashed = true),
        ChartSeries("Curve", sample(threshold), MaterialTheme.colorScheme.primary),
    )

    ChartCanvas(
        series = series,
        xRange = minLux..maxLux,
        yRange = 0f..100f,
        xScale = AxisScale.Log10,
        xAxisLabel = "Lux",
        yAxisLabel = "Threshold %",
        showLegend = true,
        interactive = true,
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}

/**
 * AAB Alpha Graph (Tasker: task557 `_GenerateAlphaGraph`, feeds %AAB_HTML_Graph3). The smoothing
 * response overlay shown alongside the reactivity curve.
 *
 * - X-axis: lux-change delta (log10, 0.01 → 20 — the task557 labelValues 1..2000 / 100).
 * - Y-axis: alpha (smoothing response factor) 0..1.
 * - **Curve**: `1 − exp(−deltaFactor · delta)` (the user's current alpha response, alpha_graph.md).
 * - **Reference** (gold, dashed): `1 − exp(−1.8 · delta)` (deltaFactor = 1.8 baseline).
 */
@Composable
fun AlphaResponseChart(
    deltaFactor: Double,
    modifier: Modifier = Modifier,
) {
    val minDelta = 0.01f
    val maxDelta = 20f
    val samples = 80

    fun sample(factor: Double): List<Offset> = logSpaced(minDelta, maxDelta, samples).map { delta ->
        val alpha = 1.0 - exp(-factor * delta)
        Offset(delta, alpha.toFloat())
    }

    val series = listOf(
        ChartSeries("Reference", sample(1.8), AabGold, strokeWidthPx = 3f, dashed = true),
        ChartSeries("Curve", sample(deltaFactor), MaterialTheme.colorScheme.primary),
    )

    ChartCanvas(
        series = series,
        xRange = minDelta..maxDelta,
        yRange = 0f..1f,
        xScale = AxisScale.Log10,
        xAxisLabel = "Lux change",
        yAxisLabel = "Alpha",
        showLegend = true,
        interactive = true,
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
