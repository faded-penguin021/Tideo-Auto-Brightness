package com.tideo.autobrightness.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Whether an axis is drawn on a linear or base-10 logarithmic scale. */
enum class AxisScale { Linear, Log10 }

/** One polyline series in data-space coordinates (x,y). */
data class ChartSeries(
    val label: String,
    val points: List<Offset>,
    val color: Color,
    val strokeWidthPx: Float = 4f,
)

/** A reference line: vertical at data-[x] or horizontal at data-[y] (e.g. a threshold or marker). */
data class ChartMarker(
    val color: Color,
    val x: Float? = null,
    val y: Float? = null,
    val label: String? = null,
)

/**
 * Reusable Compose-Canvas chart engine: axes + ticks, linear & log10 x-scale, gridlines,
 * multi-series polylines, threshold/marker lines, M3 theming. Pure presentation — series are sampled
 * from domain functions by the caller.
 *
 * **This is THE chart engine S13 builds its six remaining charts on.** Do not special-case any one
 * chart here; keep it generic (S13's hard fence forbids modifying this file). See
 * [BrightnessCurveChart] for the copy-this-pattern template instance.
 */
@Composable
fun ChartCanvas(
    series: List<ChartSeries>,
    xRange: ClosedFloatingPointRange<Float>,
    yRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    xScale: AxisScale = AxisScale.Linear,
    markers: List<ChartMarker> = emptyList(),
    height: Dp = 240.dp,
    axisColor: Color = Color.Gray,
    gridColor: Color = Color.Gray.copy(alpha = 0.25f),
    labelColor: Color = Color.Gray,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 9.sp)
    val density = LocalDensity.current
    val leftPad = with(density) { 34.dp.toPx() }
    val bottomPad = with(density) { 18.dp.toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    val rightPad = with(density) { 8.dp.toPx() }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val plotLeft = leftPad
        val plotRight = size.width - rightPad
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotH = (plotBottom - plotTop).coerceAtLeast(1f)

        fun xToPx(x: Float): Float = when (xScale) {
            AxisScale.Linear -> {
                val span = (xRange.endInclusive - xRange.start).takeIf { it != 0f } ?: 1f
                plotLeft + (x - xRange.start) / span * plotW
            }
            AxisScale.Log10 -> {
                val lo = log10(xRange.start.coerceAtLeast(1e-3f))
                val hi = log10(xRange.endInclusive.coerceAtLeast(lo + 1e-3f))
                val v = log10(x.coerceAtLeast(1e-3f))
                plotLeft + ((v - lo) / (hi - lo)) * plotW
            }
        }

        fun yToPx(y: Float): Float {
            val span = (yRange.endInclusive - yRange.start).takeIf { it != 0f } ?: 1f
            return plotBottom - (y - yRange.start) / span * plotH
        }

        // ---- gridlines + axis ticks --------------------------------------------------------
        drawAxisTicks(
            xScale, xRange, yRange, measurer, labelStyle, gridColor,
            plotLeft, plotRight, plotTop, plotBottom, ::xToPx, ::yToPx,
        )

        // axes
        drawLine(axisColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), 2f)
        drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), 2f)

        // ---- marker / threshold lines ------------------------------------------------------
        markers.forEach { m ->
            m.x?.let { drawLine(m.color, Offset(xToPx(it), plotTop), Offset(xToPx(it), plotBottom), 2f) }
            m.y?.let { drawLine(m.color, Offset(plotLeft, yToPx(it)), Offset(plotRight, yToPx(it)), 2f) }
        }

        // ---- series polylines --------------------------------------------------------------
        series.forEach { s ->
            if (s.points.size < 2) {
                // single point → dot
                s.points.firstOrNull()?.let { p ->
                    drawCircle(s.color, radius = 5f, center = Offset(xToPx(p.x), yToPx(p.y)))
                }
                return@forEach
            }
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val px = xToPx(p.x)
                val py = yToPx(p.y)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, s.color, style = Stroke(width = s.strokeWidthPx))
        }
    }
}

private fun DrawScope.drawAxisTicks(
    xScale: AxisScale,
    xRange: ClosedFloatingPointRange<Float>,
    yRange: ClosedFloatingPointRange<Float>,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    gridColor: Color,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    xToPx: (Float) -> Float,
    yToPx: (Float) -> Float,
) {
    // Y ticks: 4 even divisions.
    val ySteps = 4
    for (i in 0..ySteps) {
        val v = yRange.start + (yRange.endInclusive - yRange.start) * i / ySteps
        val py = yToPx(v)
        drawLine(gridColor, Offset(plotLeft, py), Offset(plotRight, py), 1f)
        val txt = measurer.measure(formatTick(v), labelStyle)
        drawText(txt, topLeft = Offset(0f, py - txt.size.height / 2f))
    }

    // X ticks.
    when (xScale) {
        AxisScale.Linear -> {
            val xSteps = 4
            for (i in 0..xSteps) {
                val v = xRange.start + (xRange.endInclusive - xRange.start) * i / xSteps
                val px = xToPx(v)
                drawLine(gridColor, Offset(px, plotTop), Offset(px, plotBottom), 1f)
                val txt = measurer.measure(formatTick(v), labelStyle)
                drawText(txt, topLeft = Offset(px - txt.size.width / 2f, plotBottom + 2f))
            }
        }
        AxisScale.Log10 -> {
            val lo = floor(log10(xRange.start.coerceAtLeast(1e-3f))).toInt()
            val hi = ceil(log10(xRange.endInclusive.coerceAtLeast(1e-3f))).toInt()
            for (d in lo..hi) {
                val v = 10f.pow(d)
                if (v < xRange.start || v > xRange.endInclusive) continue
                val px = xToPx(v)
                drawLine(gridColor, Offset(px, plotTop), Offset(px, plotBottom), 1f)
                val txt = measurer.measure(formatDecade(d), labelStyle)
                drawText(txt, topLeft = Offset(px - txt.size.width / 2f, plotBottom + 2f))
            }
        }
    }
}

private fun formatTick(v: Float): String =
    if (v >= 1000f || v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)

private fun formatDecade(d: Int): String = when {
    d < 0 -> "%.2f".format(10.0.pow(d))
    d <= 3 -> 10.0.pow(d).toInt().toString()
    else -> "1e$d"
}
