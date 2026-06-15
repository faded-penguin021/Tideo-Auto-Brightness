package com.tideo.autobrightness.app.ui.graph

import androidx.compose.ui.geometry.Offset
import kotlin.test.assertEquals
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
}
