package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import com.tideo.autobrightness.domain.circadian.SolarCalculator

/** A representative temperate latitude/longitude used when the screen has no real location fix yet, so
 *  the preview chart still shows a plausible sunrise/sunset shape (a preview default, not a setting). */
private const val DEFAULT_LAT = 51.5
private const val DEFAULT_LON = 0.0

/**
 * The sampled day-curve plus the five sun-event positions, both in **UTC hours-of-day** (0–24).
 *
 * Tasker's circadian graphs run in the UTC frame (task90 `%TIMES%86400`), so the chart does too — the
 * x-axis is UTC time-of-day (and labelled as such), matching both Tasker and the runtime windows
 * (D-061/D-065). Event lines (dawn/sunrise/solar-noon/sunset/dusk) are drawn as vertical markers.
 */
internal data class CircadianCurve(val points: List<Offset>, val events: List<Float>)

internal fun circadianCurve(
    scaling: DynamicScalingConfig,
    latitude: Double?,
    longitude: Double?,
    dateEpochSec: Long,
    pickScale: Boolean,
    steps: Int = 96,
): CircadianCurve {
    // UTC frame (tzOffsetHours = 0) → windows are UTC seconds-of-day, so the x-axis reads as UTC time.
    val solar = SolarCalculator.compute(latitude ?: DEFAULT_LAT, longitude ?: DEFAULT_LON, dateEpochSec, 0.0)
    val windows = SolarCalculator.buildScheduleWindows(solar, scaleTransitionFactor = 0.1)
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

    // dawn / sunrise / solar-noon / sunset / dusk → UTC hour-of-day in [0,24).
    val events = listOf(
        windows.dawnSecOfDay, windows.sunriseSecOfDay, windows.noonSecOfDay,
        windows.sunsetSecOfDay, windows.duskSecOfDay,
    ).map { (((it % 86400.0) + 86400.0) % 86400.0 / 3600.0).toFloat() }

    return CircadianCurve(points, events)
}

/** Names for the five sun events, in the order [circadianCurve] returns them. */
internal val EVENT_LABELS = listOf("Dawn", "Sunrise", "Noon", "Sunset", "Dusk")

/** Labelled vertical event-line markers for the five sun events (Tasker draws these on the circadian
 *  graphs). ChartCanvas renders the [ChartMarker.label] alongside each line (S13d, fence lifted). */
internal fun eventMarkers(events: List<Float>, color: androidx.compose.ui.graphics.Color): List<ChartMarker> =
    events.mapIndexed { i, h -> ChartMarker(color = color, x = h, label = EVENT_LABELS.getOrNull(i)) }

/** Current UTC time-of-day as an hour (0..24) — the Tasker `now_utc` event line position. */
internal fun nowUtcHour(): Float = (System.currentTimeMillis() / 1000L % 86_400L) / 3600f

/** Format an hour-of-day (0..24, may be fractional) as a 24-h "HH:MM" clock label. */
internal fun hourToHhmm(hour: Float): String {
    val total = (((hour % 24f) + 24f) % 24f) * 60f
    val h = (total / 60f).toInt()
    val m = (total % 60f).toInt()
    return "%02d:%02d".format(h, m)
}

/**
 * AAB Circadian Dimming Graph (Tasker: task705 `_GenerateCircadianDimmingGraph`, feeds
 * %AAB_HTML_Graph7; re-homed to Super Dimming per D-026). Plots the dim modifier multiplier across the
 * day (`dim_val = 2 − (1 + (dimSpread/100)·modifier)` = the engine's `dimDynamic`), highest at night.
 */
@Composable
fun CircadianDimmingChart(
    scaling: DynamicScalingConfig,
    modifier: Modifier = Modifier,
    latitude: Double? = null,
    longitude: Double? = null,
    dateEpochSec: Long = System.currentTimeMillis() / 1000L,
) {
    val curve = remember(scaling, latitude, longitude, dateEpochSec) {
        circadianCurve(scaling, latitude, longitude, dateEpochSec, pickScale = false)
    }
    val yMin = curve.points.minOf { it.y } - 0.05f
    val yMax = curve.points.maxOf { it.y } + 0.05f
    val eventColor = MaterialTheme.colorScheme.outline

    ChartCanvas(
        series = listOf(ChartSeries("Dim ×", curve.points, MaterialTheme.colorScheme.primary)),
        xRange = 0f..24f,
        yRange = yMin..yMax,
        markers = eventMarkers(curve.events, eventColor) +
            ChartMarker(color = MaterialTheme.colorScheme.outlineVariant, y = 1f) +
            ChartMarker(color = MaterialTheme.colorScheme.error, x = nowUtcHour(), label = "Now"),
        xAxisLabel = "Time of day (UTC)",
        yAxisLabel = "Dim ×",
        xTickFormatter = ::hourToHhmm,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
