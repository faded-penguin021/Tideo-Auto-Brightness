package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig

/**
 * AAB Experiment / Circadian Graph (Tasker: task549 `_GenerateCircadianGraph V8`, feeds
 * %AAB_HTML_Graph4). The day-scaling preview shown on the Circadian screen ("Circadian" slot). Plots
 * the brightness-scaling multiplier across the day (`scaled_value = 1 + (spread/100)·modifier` = the
 * engine's `scaleDynamic`), highest by day — the inverse of the Circadian Dimming graph.
 *
 * Shares [circadianCurve] / [DynamicScaleEngine] with [CircadianDimmingChart]; this instance picks the
 * scale series and runs in the same UTC time-of-day frame with the five sun-event lines.
 */
@Composable
fun CircadianScaleChart(
    scaling: DynamicScalingConfig,
    modifier: Modifier = Modifier,
    latitude: Double? = null,
    longitude: Double? = null,
    dateEpochSec: Long = System.currentTimeMillis() / 1000L,
    transitionFactor: Double = 0.1,
) {
    val curve = remember(scaling, latitude, longitude, dateEpochSec, transitionFactor) {
        circadianCurve(scaling, latitude, longitude, dateEpochSec, pickScale = true, transitionFactor = transitionFactor)
    }
    val yMin = curve.points.minOf { it.y } - 0.05f
    val yMax = curve.points.maxOf { it.y } + 0.05f
    val eventColor = MaterialTheme.colorScheme.outline
    val eventLabels = stringArrayResource(R.array.circadian_event_labels).toList()

    ChartCanvas(
        series = listOf(ChartSeries(stringResource(R.string.chart_scale_x), curve.points, MaterialTheme.colorScheme.primary)),
        xRange = 0f..24f,
        yRange = yMin..yMax,
        markers = eventMarkers(curve.events, eventColor, eventLabels) +
            ChartMarker(color = MaterialTheme.colorScheme.outlineVariant, y = 1f) +
            ChartMarker(color = MaterialTheme.colorScheme.error, x = nowUtcHour(), label = stringResource(R.string.chart_now)),
        xAxisLabel = stringResource(R.string.chart_time_utc),
        yAxisLabel = stringResource(R.string.chart_scale_x),
        xTickFormatter = ::hourToHhmm,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
