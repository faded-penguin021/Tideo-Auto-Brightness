package com.tideo.autobrightness.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Whether an axis is drawn on a linear or base-10 logarithmic scale. */
enum class AxisScale { Linear, Log10 }

/** One polyline series in data-space coordinates (x,y). [dashed] draws a dashed stroke (reference lines). */
data class ChartSeries(
    val label: String,
    val points: List<Offset>,
    val color: Color,
    val strokeWidthPx: Float = 4f,
    val dashed: Boolean = false,
    /** Whether this series appears in the legend (override scatter etc. opt out). */
    val inLegend: Boolean = true,
    /** Map this series against the RIGHT (secondary) y-axis instead of the left (S13d — dual-axis
     *  dimming graph). Requires [ChartCanvas]'s `secondaryYRange` to be set; ignored otherwise. */
    val onSecondaryAxis: Boolean = false,
)

/** A reference line: vertical at data-[x] or horizontal at data-[y] (e.g. a threshold or marker). */
data class ChartMarker(
    val color: Color,
    val x: Float? = null,
    val y: Float? = null,
    val label: String? = null,
)

/**
 * A set of tappable scatter points (data-space) drawn as larger dots — the recorded manual-override
 * training points. [onTap] receives the data-space point nearest the touch (within the dot radius),
 * enabling tap-to-delete (S12.7g / F36).
 */
data class ChartScatter(
    val points: List<Offset>,
    val color: Color,
    val radiusPx: Float = 12f,
    val onTap: ((Offset) -> Unit)? = null,
)

/**
 * Reusable Compose-Canvas chart engine: axes + ticks (nice rounded values, no 191.25 artefacts),
 * linear & log10 x-scale, gridlines, multi-series polylines (solid + dashed), threshold/marker lines,
 * axis titles, an optional legend, a draggable scrub readout, and tappable scatter points. Pure
 * presentation — series are sampled from domain functions by the caller.
 *
 * **This is THE chart engine S13 builds its six remaining charts on.** Do not special-case any one
 * chart here; keep it generic (S13's hard fence forbids modifying this file). See
 * [BrightnessCurveChart] for the copy-this-pattern template instance. S12.7g extended it with axis
 * labels + the interactive scrub readout + scatter taps (sanctioned engine features per the brief).
 */
@Composable
fun ChartCanvas(
    series: List<ChartSeries>,
    xRange: ClosedFloatingPointRange<Float>,
    yRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    xScale: AxisScale = AxisScale.Linear,
    markers: List<ChartMarker> = emptyList(),
    scatter: ChartScatter? = null,
    xAxisLabel: String? = null,
    yAxisLabel: String? = null,
    /** Optional secondary (right) y-axis range; series with `onSecondaryAxis=true` map against it. */
    secondaryYRange: ClosedFloatingPointRange<Float>? = null,
    secondaryYAxisLabel: String? = null,
    /** Custom formatter for linear x-axis tick labels (e.g. hour → "HH:MM"); null = default numeric. */
    xTickFormatter: ((Float) -> String)? = null,
    showLegend: Boolean = false,
    interactive: Boolean = false,
    height: Dp = 240.dp,
    axisColor: Color = Color.Gray,
    gridColor: Color = Color.Gray.copy(alpha = 0.25f),
    labelColor: Color = Color.Gray,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 9.sp)
    val readoutStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val density = LocalDensity.current
    // Extra left/bottom room for the rotated y-title and the x-title when axis labels are present.
    val leftPad = with(density) { (if (yAxisLabel != null) 46.dp else 34.dp).toPx() }
    val bottomPad = with(density) { (if (xAxisLabel != null) 32.dp else 18.dp).toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    // Room on the right for the secondary y-axis ticks + rotated title when present.
    val rightPad = with(density) { (if (secondaryYRange != null) 46.dp else 10.dp).toPx() }

    // Scrub position in px (null = not scrubbing). Set by tap/drag when [interactive].
    var scrubPx by remember { mutableStateOf<Float?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showLegend) ChartLegend(series.filter { it.inLegend }, scatter, labelColor)

        var canvasModifier: Modifier = Modifier.fillMaxWidth().height(height)
        if (interactive) {
            canvasModifier = canvasModifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { scrubPx = null },
                    onDragCancel = { scrubPx = null },
                ) { change, _ -> scrubPx = change.position.x }
            }
        }
        if (scatter?.onTap != null || interactive) {
            canvasModifier = canvasModifier.pointerInput(scatter) {
                detectTapGestures { tap ->
                    val plotLeft = leftPad
                    val plotW = (size.width - rightPad - plotLeft).coerceAtLeast(1f)
                    val plotTop = topPad
                    val plotBottom = size.height - bottomPad
                    val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
                    val hit = scatter?.let { sc ->
                        val pxPoints = sc.points.map {
                            Offset(
                                xToPx(it.x, xScale, xRange, plotLeft, plotW),
                                yToPx(it.y, yRange, plotTop, plotH),
                            )
                        }
                        val idx = nearestIndex(pxPoints, tap, sc.radiusPx * 1.8f)
                        if (idx >= 0) sc.points[idx] else null
                    }
                    when {
                        hit != null -> scatter?.onTap?.invoke(hit)
                        interactive -> scrubPx = tap.x
                    }
                }
            }
        }

        Canvas(modifier = canvasModifier) {
            val plotLeft = leftPad
            val plotRight = size.width - rightPad
            val plotTop = topPad
            val plotBottom = size.height - bottomPad
            val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotH = (plotBottom - plotTop).coerceAtLeast(1f)

            fun toPxX(x: Float) = xToPx(x, xScale, xRange, plotLeft, plotW)
            fun toPxY(y: Float) = yToPx(y, yRange, plotTop, plotH)
            // Secondary (right) axis mapping for series flagged onSecondaryAxis (dual-axis dimming).
            fun toPxYSec(y: Float) = yToPx(y, secondaryYRange ?: yRange, plotTop, plotH)

            // ---- gridlines + axis ticks --------------------------------------------------------
            drawAxisTicks(
                xScale, xRange, yRange, measurer, labelStyle, gridColor,
                plotLeft, plotRight, plotTop, plotBottom, ::toPxX, ::toPxY, xTickFormatter,
            )
            // Right-side ticks for the secondary axis (no gridlines — they belong to the left axis).
            if (secondaryYRange != null) {
                niceTicks(secondaryYRange.start, secondaryYRange.endInclusive).forEach { v ->
                    val py = toPxYSec(v)
                    val txt = measurer.measure(formatTick(v), labelStyle)
                    drawText(txt, topLeft = Offset(plotRight + 4f, py - txt.size.height / 2f))
                }
            }

            // axes
            drawLine(axisColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), 2f)
            drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), 2f)
            if (secondaryYRange != null) {
                drawLine(axisColor, Offset(plotRight, plotTop), Offset(plotRight, plotBottom), 2f)
            }

            // ---- axis titles -------------------------------------------------------------------
            xAxisLabel?.let {
                val t = measurer.measure(it, labelStyle)
                drawText(t, topLeft = Offset((plotLeft + plotRight) / 2f - t.size.width / 2f, size.height - t.size.height - 1f))
            }
            yAxisLabel?.let {
                val t = measurer.measure(it, labelStyle)
                // rotate -90° around its centre, parked along the left edge.
                val cx = t.size.width / 2f
                val cy = (plotTop + plotBottom) / 2f
                rotate(degrees = -90f, pivot = Offset(cx, cy)) {
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy - t.size.height / 2f))
                }
            }
            secondaryYAxisLabel?.let {
                val t = measurer.measure(it, labelStyle)
                val cx = size.width - t.size.height / 2f
                val cy = (plotTop + plotBottom) / 2f
                rotate(degrees = 90f, pivot = Offset(cx, cy)) {
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy - t.size.height / 2f))
                }
            }

            // ---- marker / threshold lines ------------------------------------------------------
            markers.forEach { m ->
                m.x?.let {
                    val mx = toPxX(it)
                    drawLine(m.color, Offset(mx, plotTop), Offset(mx, plotBottom), 2f)
                    // Vertical event label (e.g. "Sunrise") parked just inside the line near the top.
                    m.label?.let { lbl ->
                        val t = measurer.measure(lbl, TextStyle(color = m.color, fontSize = 8.sp))
                        rotate(degrees = -90f, pivot = Offset(mx + 4f, plotTop + t.size.width / 2f + 2f)) {
                            drawText(t, topLeft = Offset(mx + 4f, plotTop + 2f))
                        }
                    }
                }
                m.y?.let { drawLine(m.color, Offset(plotLeft, toPxY(it)), Offset(plotRight, toPxY(it)), 2f) }
            }

            // ---- series polylines --------------------------------------------------------------
            series.forEach { s ->
                val mapY: (Float) -> Float = if (s.onSecondaryAxis) ::toPxYSec else ::toPxY
                if (s.points.size < 2) {
                    s.points.firstOrNull()?.let { p ->
                        drawCircle(s.color, radius = 5f, center = Offset(toPxX(p.x), mapY(p.y)))
                    }
                    return@forEach
                }
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    val px = toPxX(p.x)
                    val py = mapY(p.y)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                val effect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
                drawPath(path, s.color, style = Stroke(width = s.strokeWidthPx, pathEffect = effect))
            }

            // ---- tappable scatter points -------------------------------------------------------
            scatter?.points?.forEach { p ->
                val c = Offset(toPxX(p.x), toPxY(p.y))
                drawCircle(scatter.color, radius = scatter.radiusPx, center = c)
                drawCircle(Color.White, radius = scatter.radiusPx, center = c, style = Stroke(width = 2f))
            }

            // ---- interactive scrub readout -----------------------------------------------------
            val sx = scrubPx
            if (interactive && sx != null) {
                val px = sx.coerceIn(plotLeft, plotRight)
                drawLine(axisColor, Offset(px, plotTop), Offset(px, plotBottom), 1.5f)
                val dataX = pxToX(px, xScale, xRange, plotLeft, plotW)
                val lines = buildList {
                    add("x: ${formatReadout(dataX)}")
                    series.filter { it.inLegend && it.points.size >= 2 }.forEach { s ->
                        seriesValueAt(s.points, dataX)?.let { v ->
                            drawCircle(s.color, radius = 5f, center = Offset(px, toPxY(v)))
                            add("${s.label}: ${formatReadout(v)}")
                        }
                    }
                }
                drawReadoutBox(measurer, readoutStyle, lines, px, plotLeft, plotRight, plotTop, gridColor, axisColor)
            }
        }
    }
}

/** Legend row: a coloured (solid/dashed) swatch + label per series, plus the scatter marker. */
@Composable
private fun ChartLegend(series: List<ChartSeries>, scatter: ChartScatter?, labelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("legend_${s.label}")) {
                Canvas(modifier = Modifier.width(22.dp).height(8.dp)) {
                    val y = size.height / 2f
                    val effect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                    drawLine(s.color, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, pathEffect = effect)
                }
                Text(
                    "  ${s.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
        if (scatter != null && scatter.points.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("legend_points")) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(scatter.color, radius = size.minDimension / 2f, center = center)
                }
                Text("  Overrides", style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

// ---- coordinate mappings (shared by Canvas draw + pointer hit-testing) ----------------------------

private fun xToPx(x: Float, xScale: AxisScale, xRange: ClosedFloatingPointRange<Float>, plotLeft: Float, plotW: Float): Float =
    when (xScale) {
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

private fun pxToX(px: Float, xScale: AxisScale, xRange: ClosedFloatingPointRange<Float>, plotLeft: Float, plotW: Float): Float =
    when (xScale) {
        AxisScale.Linear -> {
            val span = xRange.endInclusive - xRange.start
            xRange.start + (px - plotLeft) / plotW * span
        }
        AxisScale.Log10 -> {
            val lo = log10(xRange.start.coerceAtLeast(1e-3f))
            val hi = log10(xRange.endInclusive.coerceAtLeast(lo + 1e-3f))
            10f.pow(lo + (px - plotLeft) / plotW * (hi - lo))
        }
    }

private fun yToPx(y: Float, yRange: ClosedFloatingPointRange<Float>, plotTop: Float, plotH: Float): Float {
    val span = (yRange.endInclusive - yRange.start).takeIf { it != 0f } ?: 1f
    return plotTop + plotH - (y - yRange.start) / span * plotH
}

private fun DrawScope.drawReadoutBox(
    measurer: TextMeasurer,
    style: TextStyle,
    lines: List<String>,
    scrubPx: Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    bgColor: Color,
    borderColor: Color,
) {
    if (lines.isEmpty()) return
    val measured = lines.map { measurer.measure(it, style) }
    val boxW = (measured.maxOf { it.size.width } + 12f)
    val lineH = measured.first().size.height.toFloat()
    val boxH = lineH * lines.size + 10f
    // place to the right of the scrub line unless it would clip the right edge.
    val left = if (scrubPx + 10f + boxW < plotRight) scrubPx + 10f else scrubPx - 10f - boxW
    val top = plotTop + 4f
    drawRoundRect(
        color = bgColor.copy(alpha = 0.92f),
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(boxW, boxH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
    )
    measured.forEachIndexed { i, t ->
        drawText(t, topLeft = Offset(left + 6f, top + 5f + i * lineH))
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
    xTickFormatter: ((Float) -> String)? = null,
) {
    // Y ticks: nice rounded values (no 191.25 artefacts, F55).
    niceTicks(yRange.start, yRange.endInclusive).forEach { v ->
        val py = yToPx(v)
        drawLine(gridColor, Offset(plotLeft, py), Offset(plotRight, py), 1f)
        val txt = measurer.measure(formatTick(v), labelStyle)
        drawText(txt, topLeft = Offset(0f, py - txt.size.height / 2f))
    }

    // X ticks.
    when (xScale) {
        AxisScale.Linear -> {
            niceTicks(xRange.start, xRange.endInclusive).forEach { v ->
                val px = xToPx(v)
                drawLine(gridColor, Offset(px, plotTop), Offset(px, plotBottom), 1f)
                val txt = measurer.measure(xTickFormatter?.invoke(v) ?: formatTick(v), labelStyle)
                drawText(txt, topLeft = Offset(px - txt.size.width / 2f, plotBottom + 2f))
            }
        }
        AxisScale.Log10 -> {
            val lo = floor(log10(xRange.start.coerceAtLeast(1e-3f))).toInt()
            val hi = kotlin.math.ceil(log10(xRange.endInclusive.coerceAtLeast(1e-3f))).toInt()
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

/**
 * "Nice" evenly-spaced ticks spanning [min,max] using a 1/2/5×10ⁿ step, so a 0..255 axis labels
 * 0/50/…/250 rather than 0/63.75/127.5/191.25/255 (F55). Returns ascending ticks within range.
 */
internal fun niceTicks(min: Float, max: Float, target: Int = 5): List<Float> {
    val span = max - min
    if (span <= 0f || target < 1) return listOf(min)
    val rawStep = span / target
    val mag = 10.0.pow(floor(log10(rawStep.toDouble()))).toFloat()
    val norm = rawStep / mag
    val niceNorm = when {
        norm < 1.5f -> 1f
        norm < 3f -> 2f
        norm < 7f -> 5f
        else -> 10f
    }
    val step = niceNorm * mag
    val first = kotlin.math.ceil(min / step) * step
    val ticks = mutableListOf<Float>()
    var v = first
    // guard against float drift producing a runaway loop.
    var guard = 0
    while (v <= max + step * 1e-3f && guard < 1000) {
        ticks.add(v)
        v += step
        guard++
    }
    return ticks
}

/** Linear interpolation of a sorted-ascending-x polyline's y at data-[x] (null if x is out of span). */
internal fun seriesValueAt(points: List<Offset>, x: Float): Float? {
    if (points.size < 2) return points.firstOrNull()?.takeIf { abs(it.x - x) < 1e-3f }?.y
    if (x < points.first().x || x > points.last().x) return null
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (x in a.x..b.x || x in b.x..a.x) {
            val dx = b.x - a.x
            if (abs(dx) < 1e-6f) return a.y
            val t = (x - a.x) / dx
            return a.y + t * (b.y - a.y)
        }
    }
    return null
}

/** Index of the candidate point nearest [target] within [maxDist] px, else -1 (scatter tap hit-test). */
internal fun nearestIndex(points: List<Offset>, target: Offset, maxDist: Float): Int {
    var best = -1
    var bestD = maxDist
    points.forEachIndexed { i, p ->
        val d = (p - target).getDistance()
        if (d <= bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

private fun formatTick(v: Float): String =
    if (v >= 1000f || v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)

private fun formatReadout(v: Float): String = when {
    abs(v) >= 1000f -> v.toInt().toString()
    abs(v) >= 10f -> "%.0f".format(v)
    abs(v) >= 1f -> "%.1f".format(v)
    else -> "%.2f".format(v)
}

private fun formatDecade(d: Int): String = when {
    d < 0 -> "%.2f".format(10.0.pow(d))
    d <= 3 -> 10.0.pow(d).toInt().toString()
    else -> "1e$d"
}
