package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabTeal
import com.tideo.autobrightness.domain.power.PowerDrawSample

// S14: `PowerDrawSample` is now the canonical domain type (com.tideo.autobrightness.domain.power) so the
// calibrator (task524 port) produces the exact rows this chart renders. Fields are accessed by name.

/**
 * AAB Power Draw Graph (Tasker: task524 `_CalibratePowerDraw`, feeds %AAB_HTML_Graph8). Unlike the
 * other charts this has no parametric formula — the data is *measured* at runtime by stepping the
 * screen through brightness levels and sampling battery current. Until samples exist this renders an
 * [EmptyState]; once measured it plots them on the [ChartCanvas] following the [BrightnessCurveChart]
 * template, with the **dual y-axis the Tasker Chart.js graph uses** (owner finding): Power on the left,
 * the real Current (mA) values on the right — NOT current rescaled onto the power axis.
 *
 * - X-axis: brightness level 0..255 (linear).
 * - **Power** (teal, left axis): measured screen power (W).
 * - **Current** (gold, dashed, right axis): measured current draw (mA), in its own real units.
 */
@Composable
fun PowerDrawChart(
    samples: List<PowerDrawSample>,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(R.string.chart_power_empty),
) {
    if (samples.isEmpty()) {
        EmptyState(emptyText, modifier = modifier, testTag = "power_draw_empty")
        return
    }

    val powerPoints = samples.map { Offset(it.brightness.toFloat(), it.powerW.toFloat()) }
    val currentPoints = samples.map { Offset(it.brightness.toFloat(), it.currentMa.toFloat()) }
    val maxPower = (powerPoints.maxOf { it.y }).coerceAtLeast(0.001f)
    val maxCurrent = (currentPoints.maxOf { it.y }).coerceAtLeast(0.001f)

    val series = listOf(
        ChartSeries(stringResource(R.string.chart_power_w), powerPoints, AabTeal),
        // task524 chart: the Current dataset lives on its own right-hand mA axis (yAxisID 'y1').
        ChartSeries(stringResource(R.string.chart_current_ma), currentPoints, AabGold, strokeWidthPx = 2f, dashed = true, onSecondaryAxis = true),
    )

    ChartCanvas(
        series = series,
        xRange = 0f..255f,
        yRange = 0f..(maxPower * 1.1f),
        secondaryYRange = 0f..(maxCurrent * 1.1f),
        secondaryYAxisLabel = stringResource(R.string.chart_current_ma),
        xAxisLabel = stringResource(R.string.chart_brightness),
        yAxisLabel = stringResource(R.string.chart_power_w),
        showLegend = true,
        interactive = true, // scrub readout (owner: charts must stay interactive)
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
