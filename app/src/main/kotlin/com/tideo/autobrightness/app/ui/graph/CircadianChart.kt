package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import com.tideo.autobrightness.domain.circadian.SolarCalculator

// Default temperate coordinates for location-less preview (not a setting).
private const val DEFAULT_LAT = 51.5
private const val DEFAULT_LON = 0.0

// D-061/D-065: circadian graphs run in UTC frame (task90); x-axis is UTC time-of-day.
internal data class CircadianCurve(val points: List<Offset>, val events: List<Float>)

internal fun circadianCurve(
    scaling: DynamicScalingConfig,
    latitude: Double?,
    longitude: Double?,
    dateEpochSec: Long,
    pickScale: Boolean,
    transitionFactor: Double = 0.1,
    steps: Int = 96,
): CircadianCurve {
    val solar = SolarCalculator.compute(latitude ?: DEFAULT_LAT, longitude ?: DEFAULT_LON, dateEpochSec, 0.0)
    val windows = SolarCalculator.buildScheduleWindows(solar, scaleTransitionFactor = transitionFactor)
    val isPolar = solar.sunStatus == "polar"

    val points = (0..steps).map { i ->
        val hour = 24f * i / steps
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = hour * 3600.0,
                morningStart = windows.morningStart,
                morningEnd = windows.morningEnd,
                eveningStart = windows.eveningStart,
                eveningEnd = windows.eveningEnd,
                sunlightDurationMinutes = solar.sunlightDurationMinutes.toDouble(),
                isPolar = isPolar,
                steepness = scaling.steepness,
                dimSpreadPercent = scaling.dimSpreadPercent,
                scaleSpreadPercent = scaling.spreadPercent,
            ),
        )
        Offset(hour, (if (pickScale) result.scaleDynamic else result.dimDynamic).toFloat())
    }

    val events = listOf(
        windows.dawnSecOfDay, windows.sunriseSecOfDay, windows.noonSecOfDay,
        windows.sunsetSecOfDay, windows.duskSecOfDay,
    ).map { (((it % 86400.0) + 86400.0) % 86400.0 / 3600.0).toFloat() }

    return CircadianCurve(points, events)
}

// Event names from strings.xml circadian_event_labels array (D-131 i18n).
// Labelled vertical event-line markers; ChartCanvas renders labels alongside lines (S13d).
internal fun eventMarkers(
    events: List<Float>,
    color: androidx.compose.ui.graphics.Color,
    labels: List<String>,
): List<ChartMarker> =
    events.mapIndexed { i, h -> ChartMarker(color = color, x = h, label = labels.getOrNull(i)) }

internal fun nowUtcHour(): Float = (System.currentTimeMillis() / 1000L % 86_400L) / 3600f

internal fun hourToHhmm(hour: Float): String {
    val total = (((hour % 24f) + 24f) % 24f) * 60f
    val h = (total / 60f).toInt()
    val m = (total % 60f).toInt()
    return "%02d:%02d".format(h, m)
}

// Circadian Dimming Graph (task705, D-026); plots dim modifier multiplier across day.
@Composable
fun CircadianDimmingChart(
    scaling: DynamicScalingConfig,
    modifier: Modifier = Modifier,
    latitude: Double? = null,
    longitude: Double? = null,
    dateEpochSec: Long = System.currentTimeMillis() / 1000L,
    transitionFactor: Double = 0.1,
) {
    val curve = remember(scaling, latitude, longitude, dateEpochSec, transitionFactor) {
        circadianCurve(scaling, latitude, longitude, dateEpochSec, pickScale = false, transitionFactor = transitionFactor)
    }
    val yMin = curve.points.minOf { it.y } - 0.05f
    val yMax = curve.points.maxOf { it.y } + 0.05f
    val eventColor = MaterialTheme.colorScheme.outline
    val eventLabels = stringArrayResource(R.array.circadian_event_labels).toList()

    ChartCanvas(
        series = listOf(ChartSeries(stringResource(R.string.chart_dim_x), curve.points, MaterialTheme.colorScheme.primary)),
        xRange = 0f..24f,
        yRange = yMin..yMax,
        markers = eventMarkers(curve.events, eventColor, eventLabels) +
            ChartMarker(color = MaterialTheme.colorScheme.outlineVariant, y = 1f) +
            ChartMarker(color = MaterialTheme.colorScheme.error, x = nowUtcHour(), label = stringResource(R.string.chart_now)),
        xAxisLabel = stringResource(R.string.chart_time_utc),
        yAxisLabel = stringResource(R.string.chart_dim_x),
        xTickFormatter = ::hourToHhmm,
        interactive = true,
        contentDescription = stringResource(R.string.a11y_graph_circadian_dimming),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
