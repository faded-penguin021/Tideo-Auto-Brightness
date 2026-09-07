package com.tideo.autobrightness.app.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * S12.7g pure-engine checks for the [ChartCanvas] helpers: nice y-ticks (no 191.25 artefacts, F55),
 * log-x sampling (0.1 start), polyline scrub interpolation, and scatter tap hit-testing (F36).
 */
class ChartCanvasTest {

    @Test
    fun niceTicks_brightnessAxis_usesRoundFifties_notQuarterArtefacts() {
        // 0..255 with the default ~5 divisions must label 0/50/…/250, never 63.75/127.5/191.25 (F55).
        val ticks = niceTicks(0f, 255f)
        assertTrue(ticks.contains(50f), "expected a round 50 tick, got $ticks")
        assertTrue(ticks.contains(200f), "expected a round 200 tick, got $ticks")
        assertTrue(ticks.none { it % 10f != 0f }, "no fractional/quarter ticks allowed, got $ticks")
        assertTrue(ticks.all { it in 0f..255f })
    }

    @Test
    fun logSpaced_startsAtTenth_andIsMonotonicLog() {
        // The lux x-axis is log-spaced from 0.1 → 100000 (brightness_graph.md, F55).
        val grid = logSpaced(0.1f, 100_000f, 41)
        assertEquals(0.1f, grid.first(), 1e-4f)
        assertEquals(100_000f, grid.last(), 1f)
        // Equal log steps ⇒ a roughly constant ratio between successive samples.
        val r0 = grid[1] / grid[0]
        val r1 = grid[2] / grid[1]
        assertEquals(r0.toDouble(), r1.toDouble(), 1e-3)
    }

    @Test
    fun seriesValueAt_interpolatesAndClampsOutOfRange() {
        val line = listOf(Offset(0f, 0f), Offset(10f, 100f))
        assertEquals(50f, seriesValueAt(line, 5f)!!, 1e-3f)
        assertNull(seriesValueAt(line, 20f), "x beyond the series span yields no readout")
    }

    @Test
    fun nearestIndex_findsTappedPointWithinThreshold_elseNone() {
        val pts = listOf(Offset(0f, 0f), Offset(100f, 100f), Offset(200f, 50f))
        assertEquals(1, nearestIndex(pts, Offset(105f, 95f), maxDist = 20f))
        assertEquals(-1, nearestIndex(pts, Offset(500f, 500f), maxDist = 20f))
    }

    // DC-001: Graph Metrics (re)draw dedupe — equal inputs must not re-time; a real data change must.
    @Test
    fun graphSignature_stableForEqualInputs_changesWhenPointsChange() {
        val a = listOf(ChartSeries("s", listOf(Offset(0f, 0f), Offset(1f, 2f)), Color.Red))
        val b = listOf(ChartSeries("s", listOf(Offset(0f, 0f), Offset(1f, 2f)), Color.Red))
        val sigA = graphSignature(a, 0f..1f, 0f..2f, emptyList(), null)
        assertEquals(sigA, graphSignature(b, 0f..1f, 0f..2f, emptyList(), null),
            "equal inputs must yield the same signature (an unchanged redraw is not a regeneration)")

        val moved = listOf(ChartSeries("s", listOf(Offset(0f, 0f), Offset(1f, 3f)), Color.Red))
        assertNotEquals(sigA, graphSignature(moved, 0f..1f, 0f..2f, emptyList(), null),
            "a changed data point must change the signature (a real regeneration)")
    }

    @Test
    fun graphSignature_ignoresScatterOnTapLambda() {
        val pts = listOf(Offset(1f, 1f))
        // Two scatters differing only by a fresh onTap lambda (what a recompose produces); the
        // signature keys on points, so this must not read as a regeneration.
        val s1 = ChartScatter(pts, Color.Blue, onTap = { })
        val s2 = ChartScatter(pts, Color.Blue, onTap = { })
        val series = listOf(ChartSeries("s", listOf(Offset(0f, 0f)), Color.Red))
        assertEquals(
            graphSignature(series, 0f..1f, 0f..1f, emptyList(), s1.points),
            graphSignature(series, 0f..1f, 0f..1f, emptyList(), s2.points),
        )
    }
}
