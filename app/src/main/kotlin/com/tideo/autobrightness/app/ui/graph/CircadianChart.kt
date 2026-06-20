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
import java.util.TimeZone

/** A representative temperate latitude/longitude used when the screen has no real location fix yet, so
 *  the preview chart still shows a plausible sunrise/sunset shape (a preview default, not a setting). */
private const val DEFAULT_LAT = 51.5
private const val DEFAULT_LON = 0.0

/**
 * Sample the shared task90 day/night curve across a 24h day. Returns `Offset(hourOfDay, value)` where
 * value is either the scaling multiplier (`scaleDynamic`) or the dimming multiplier (`dimDynamic`),
 * both produced by the **golden-tested** [DynamicScaleEngine] over [SolarCalculator] windows. Windows
 * are computed in the local frame (so the x-axis reads as local time-of-day, matching the Tasker
 * HH:MM labels).
 */
internal fun circadianDaySamples(
    scaling: DynamicScalingConfig,
    latitude: Double?,
    longitude: Double?,
    dateEpochSec: Long,
    tzOffsetHours: Double,
    pickScale: Boolean,
    steps: Int = 96,
): List<Offset> {
    val solar = SolarCalculator.compute(
        latitude ?: DEFAULT_LAT,
        longitude ?: DEFAULT_LON,
        dateEpochSec,
        tzOffsetHours,
    )
    val windows = SolarCalculator.buildScheduleWindows(solar, scaleTransitionFactor = 0.1)
    val isPolar = solar.sunStatus == "polar"

    return (0..steps).map { i ->
        val hour = 24f * i / steps
        val result = DynamicScaleEngine.compute(
            DynamicScaleInput(
                nowSecOfDay = (hour * 3600.0),
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
}

/** The device's UTC offset in hours at [epochSec] — the local frame the chart's x-axis is drawn in. */
internal fun deviceTzOffsetHours(epochSec: Long): Double =
    TimeZone.getDefault().getOffset(epochSec * 1000L) / 3_600_000.0

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
    val tz = remember(dateEpochSec) { deviceTzOffsetHours(dateEpochSec) }
    val points = remember(scaling, latitude, longitude, dateEpochSec, tz) {
        circadianDaySamples(scaling, latitude, longitude, dateEpochSec, tz, pickScale = false)
    }
    val yMin = (points.minOf { it.y } - 0.05f)
    val yMax = (points.maxOf { it.y } + 0.05f)

    ChartCanvas(
        series = listOf(ChartSeries("Dim ×", points, MaterialTheme.colorScheme.primary)),
        xRange = 0f..24f,
        yRange = yMin..yMax,
        markers = listOf(ChartMarker(color = MaterialTheme.colorScheme.outline, y = 1f)),
        xAxisLabel = "Hour",
        yAxisLabel = "Dim ×",
        interactive = true,
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
