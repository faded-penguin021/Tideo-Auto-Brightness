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
    // S14: the live smoothed lux, shown as a "Now" line so the user sees where on the curve the
    // pipeline is currently operating. null (service off / no reading yet) → no line.
    currentLux: Double? = null,
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

    val curvePoints = sample(threshold)
    val referencePoints = sample(referenceCfg)
    val series = listOf(
        ChartSeries(stringResource(R.string.chart_reference), referencePoints, AabGold, strokeWidthPx = 3f, dashed = true),
        ChartSeries(stringResource(R.string.chart_curve), curvePoints, MaterialTheme.colorScheme.primary),
    )

    // Dynamic y-axis: reactivity thresholds top out well below 100 % (≈8–35 %), so a fixed 0–100 axis
    // wasted most of the height — frame to the data (rounded up, min 1) keeping the 0 baseline.
    val yMax = ((curvePoints + referencePoints).maxOf { it.y } * 1.2f).coerceAtLeast(1f)

    // S14: live "Now" line at the current smoothed lux (clamped into range so it stays visible).
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
 * - X-axis: **relative lux change, in %** (log10, 1 → 2000). G3-F15: this is NOT an absolute "lux
 *   change" — the underlying quantity is the fold change `luxDelta = |rawLux − prevSmoothed| /
 *   (prevSmoothed + 1)` (BrightnessEngine.smoothLux), a *fraction*. Tasker's task557 `labelValues`
 *   are 1..2000 and it divides each by 100 to get that fraction, so the native axis the user reads is
 *   the relative change as a percentage. The rebuild previously plotted the raw fraction (0.01..20)
 *   and labelled it "Lux change", which read as absolute lux. Now plotted/labelled as %.
 * - Y-axis: alpha (smoothing response factor) 0..1.
 * - **Curve**: `1 − exp(−deltaFactor · fraction)` (the user's current alpha response, alpha_graph.md).
 * - **Reference** (gold, dashed): `1 − exp(−1.8 · fraction)` (deltaFactor = 1.8 baseline).
 */
@Composable
fun AlphaResponseChart(
    deltaFactor: Double,
    modifier: Modifier = Modifier,
    // G3-F2/F15: the live smoothing response (%AAB_LuxAlpha) as a horizontal "Now" line, so the user
    // sees how reactive smoothing is at this instant. null (service off / no reading yet) → no line.
    currentAlpha: Double? = null,
) {
    // Percent axis (Tasker labelValues): 1 % → 2000 %. The alpha curve is evaluated on the underlying
    // fraction (= percent / 100), but the axis the user reads is the relative change as a percentage.
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

    // Live "Now" smoothing response as a horizontal line at the current alpha (the published curve
    // output; the relative-change x is instantaneous, so the y-value is the stable thing to surface).
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
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
