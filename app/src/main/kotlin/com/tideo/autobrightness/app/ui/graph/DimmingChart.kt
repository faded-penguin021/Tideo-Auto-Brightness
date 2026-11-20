package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.theme.AabChartBlue
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import kotlin.math.max
import kotlin.math.pow

/**
 * AAB Dimming Graph (task556 `_GenerateDimmingCurveGraph`).
 * X: brightness linear; Y: dim% + strength-weighted shell + reference curve.
 * S14: optional "Now" line when dimming is engaged. DE-046: PWM mode plots the task700 level instead.
 */
@Composable
fun DimmingChart(
    minBrightness: Int,
    dimmingThreshold: Int,
    dimmingExponent: Double,
    dimmingStrength: Int,
    modifier: Modifier = Modifier,
    currentBrightness: Int? = null,
    pwmSensitive: Boolean = false,
    pwmExponent: Double = 0.8,
) {
    val xStart = minBrightness.toFloat()
    // dimming_graph.md: loop minbright → max(dimmingthreshold, 15).
    val xEnd = max(dimmingThreshold, 15).toFloat()
    val span = (xEnd - xStart).toInt().coerceAtLeast(1)

    val series: List<ChartSeries>
    val secondaryYRange: ClosedFloatingPointRange<Float>?
    if (pwmSensitive) {
        series = listOf(
            ChartSeries(
                stringResource(R.string.chart_pwm_dim_level),
                pwmDimLevelPoints(minBrightness, dimmingThreshold, pwmExponent, span),
                MaterialTheme.colorScheme.primary,
            ),
        )
        secondaryYRange = null
    } else {
        val progressPoints = ArrayList<Offset>(span + 1)
        val shellPoints = ArrayList<Offset>(span + 1)
        val referencePoints = ArrayList<Offset>(span + 1)
        for (i in 0..span) {
            val b = xStart + i
            val progress = if (b < dimmingThreshold) {
                SoftwareDimming.dimProgress(b.toDouble(), minBrightness.toDouble(), dimmingThreshold.toDouble(), dimmingExponent)
            } else {
                0.0
            }
            progressPoints += Offset(b, (progress * 100.0).toFloat())
            // dim_ds_points = dimmingstrength * dim_progress (the applied magnitude, dimming_graph.md).
            shellPoints += Offset(b, (dimmingStrength * progress).toFloat())
            val ref = if (b < 15f) (1.0 - b / 15.0).pow(2.5) * 100.0 else 0.0
            referencePoints += Offset(b, ref.toFloat())
        }
        // Tasker's dimming graph has two y-axes (dimming_graph.md): LEFT = dim%, RIGHT = dim shell 0 → strength.
        series = listOf(
            ChartSeries(stringResource(R.string.chart_reference), referencePoints, AabGold, strokeWidthPx = 3f, dashed = true),
            ChartSeries(stringResource(R.string.chart_dim_shell), shellPoints, AabChartBlue, strokeWidthPx = 2f, onSecondaryAxis = true),
            ChartSeries(stringResource(R.string.chart_dim_pct), progressPoints, MaterialTheme.colorScheme.primary),
        )
        secondaryYRange = 0f..dimmingStrength.toFloat().coerceAtLeast(1f)
    }

    val markers = currentBrightness?.let {
        listOf(ChartMarker(color = MaterialTheme.colorScheme.error, x = it.toFloat().coerceIn(xStart, xEnd), label = stringResource(R.string.chart_now)))
    } ?: emptyList()

    ChartCanvas(
        series = series,
        xRange = xStart..xEnd,
        yRange = 0f..100f,
        secondaryYRange = secondaryYRange,
        secondaryYAxisLabel = secondaryYRange?.let { stringResource(R.string.chart_dim_shell) },
        markers = markers,
        xScale = AxisScale.Linear,
        xAxisLabel = stringResource(R.string.chart_brightness),
        yAxisLabel = stringResource(R.string.chart_dim_pct),
        showLegend = true,
        interactive = true, // charts must stay interactive for scrub readout
        contentDescription = if (pwmSensitive) {
            stringResource(R.string.a11y_graph_dimming_pwm, dimmingThreshold)
        } else {
            stringResource(R.string.a11y_graph_dimming, dimmingThreshold, dimmingStrength)
        },
        modifier = modifier,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        axisColor = MaterialTheme.colorScheme.outline,
    )
}

/** The runtime's PWM call (DimmingPlan.of), unrounded, over the chart's x-range; 0 at or above the threshold. */
internal fun pwmDimLevelPoints(minBrightness: Int, dimmingThreshold: Int, pwmExponent: Double, span: Int): List<Offset> =
    (0..span).map { i ->
        val b = minBrightness + i
        val level = if (b < dimmingThreshold) {
            SoftwareDimming.finalDimLevel(b.toDouble(), isElevated = true, dimmingThreshold.toDouble(), pwmExponent)
        } else {
            0.0
        }
        Offset(b.toFloat(), level.toFloat())
    }
