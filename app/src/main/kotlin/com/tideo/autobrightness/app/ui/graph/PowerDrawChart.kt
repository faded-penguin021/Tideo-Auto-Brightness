package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabTeal

/** One measured calibration point (Tasker: task524 `_CalibratePowerDraw` JSON `data` row). */
data class PowerDrawSample(val brightness: Int, val powerW: Double, val currentMa: Double)

/**
 * AAB Power Draw Graph (Tasker: task524 `_CalibratePowerDraw`, feeds %AAB_HTML_Graph8). Unlike the
 * other charts this has no parametric formula — the data is *measured* at runtime by stepping the
 * screen through brightness levels and sampling battery current. The on-device calibration is deferred
 * (D-044 / Gate), so until samples exist this renders an [EmptyState]; once measured it plots them on
 * the [ChartCanvas] following the [BrightnessCurveChart] template.
 *
 * - X-axis: brightness level 0..255 (linear).
 * - **Power** (teal): measured screen power (W).
 * - **Current** (gold, dashed): measured current draw (mA), scaled onto the same axis for shape.
 */
@Composable
fun PowerDrawChart(
    samples: List<PowerDrawSample>,
    modifier: Modifier = Modifier,
    emptyText: String = "No power-draw data yet — run the calibration to measure it.",
) {
    if (samples.isEmpty()) {
        EmptyState(emptyText, modifier = modifier, testTag = "power_draw_empty")
        return
    }

    val powerPoints = samples.map { Offset(it.brightness.toFloat(), it.powerW.toFloat()) }
    val maxPower = (powerPoints.maxOf { it.y }).coerceAtLeast(0.001f)
    val maxCurrent = (samples.maxOf { it.currentMa }).coerceAtLeast(0.001)
    // The two series share one axis; rescale current onto the power range so both shapes read clearly.
    val currentPoints = samples.map {
        Offset(it.brightness.toFloat(), (it.currentMa / maxCurrent * maxPower).toFloat())
    }

    val series = listOf(
        ChartSeries("Power (W)", powerPoints, AabTeal),
        ChartSeries("Current (mA)", currentPoints, AabGold, strokeWidthPx = 2f, dashed = true),
    )

    ChartCanvas(
        series = series,
        xRange = 0f..255f,
        yRange = 0f..(maxPower * 1.1f),
        xAxisLabel = "Brightness",
        yAxisLabel = "Power (W)",
        showLegend = true,
        interactive = false, // allow the ChartPager to swipe
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}
