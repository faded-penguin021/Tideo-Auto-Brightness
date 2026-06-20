package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig

/**
 * AAB Experiment / Circadian Graph (Tasker: task549 `_GenerateCircadianGraph V8`, feeds
 * %AAB_HTML_Graph4). The day-scaling preview shown on the Circadian screen ("Circadian" slot). Plots
 * the brightness-scaling multiplier across the day (`scaled_value = 1 + (spread/100)·modifier` = the
 * engine's `scaleDynamic`), highest by day — the inverse of the Circadian Dimming graph.
 *
 * Shares [circadianDaySamples] / [DynamicScaleEngine] with [CircadianDimmingChart]; this instance just
 * picks the scale series instead of the dim series, per the [BrightnessCurveChart] template recipe.
 */
@Composable
fun CircadianScaleChart(
    scaling: DynamicScalingConfig,
    modifier: Modifier = Modifier,
    latitude: Double? = null,
    longitude: Double? = null,
    dateEpochSec: Long = System.currentTimeMillis() / 1000L,
) {
    val tz = remember(dateEpochSec) { deviceTzOffsetHours(dateEpochSec) }
    val points = remember(scaling, latitude, longitude, dateEpochSec, tz) {
        circadianDaySamples(scaling, latitude, longitude, dateEpochSec, tz, pickScale = true)
    }
    val yMin = (points.minOf { it.y } - 0.05f)
    val yMax = (points.maxOf { it.y } + 0.05f)

    ChartCanvas(
        series = listOf(ChartSeries("Scale ×", points, MaterialTheme.colorScheme.primary)),
        xRange = 0f..24f,
        yRange = yMin..yMax,
        markers = listOf(ChartMarker(color = MaterialTheme.colorScheme.outline, y = 1f)),
        xAxisLabel = "Hour",
        yAxisLabel = "Scale ×",
        interactive = true,
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
