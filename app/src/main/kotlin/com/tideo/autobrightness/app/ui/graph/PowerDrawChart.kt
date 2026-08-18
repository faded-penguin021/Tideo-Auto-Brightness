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

// S14: canonical PowerDrawSample type fed by task524 calibrator.

/** Power Draw Graph (Tasker: task524 CalibratePowerDraw). Dual y-axis: Power (left, teal), Current (right, gold, dashed). */
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
        contentDescription = stringResource(R.string.a11y_graph_power),
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
