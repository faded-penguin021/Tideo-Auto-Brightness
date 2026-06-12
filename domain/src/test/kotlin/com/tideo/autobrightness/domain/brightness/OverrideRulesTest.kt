package com.tideo.autobrightness.domain.brightness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [OverrideRules].
 *
 * Covers all gate polarities for [OverrideRules.isManualOverride] and
 * [OverrideRules.shouldCommitPause], and correctness of [OverrideRules.recordOverridePoint]
 * (scalingUse, 255 cap, 50-entry cap, newest-first ordering).
 *
 * Tasker sources: task567 (prof755 gate + act8 re-check), task561 (override history).
 */
class OverrideRulesTest {

    // ---- isManualOverride: each gate independently suppresses detection -------------------

    @Test
    fun isManualOverride_allGatesPass_returnsTrue() {
        assertTrue(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = true,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_serviceOff_returnsFalse() {
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = false, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = true,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_autoRunning_returnsFalse() {
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = true, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = true,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_alreadyPaused_returnsFalse() {
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = true,
                isInitializing = false, detectOverrides = true,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_initializing_returnsFalse() {
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = true, detectOverrides = true,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_detectOff_returnsFalse() {
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = false,
                observedValue = 100, expectedValues = emptySet(),
            ),
        )
    }

    @Test
    fun isManualOverride_observedValueInExpectedSet_suppressesEcho() {
        // Pipeline registered its own writes; observed value matches → suppress
        assertFalse(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = true,
                observedValue = 128, expectedValues = setOf(128, 130),
            ),
        )
    }

    @Test
    fun isManualOverride_observedValueNotInExpectedSet_returnsTrue() {
        assertTrue(
            OverrideRules.isManualOverride(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false,
                isInitializing = false, detectOverrides = true,
                observedValue = 99, expectedValues = setOf(128, 130),
            ),
        )
    }

    // ---- shouldCommitPause: 4 suppression conditions -------------------------------------

    @Test
    fun shouldCommitPause_allGatesPass_returnsTrue() {
        assertTrue(
            OverrideRules.shouldCommitPause(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false, isInitializing = false,
            ),
        )
    }

    @Test
    fun shouldCommitPause_serviceOff_returnsFalse() {
        assertFalse(
            OverrideRules.shouldCommitPause(
                isServiceOn = false, isAutoRunning = false, isAlreadyPaused = false, isInitializing = false,
            ),
        )
    }

    @Test
    fun shouldCommitPause_autoRunning_returnsFalse() {
        assertFalse(
            OverrideRules.shouldCommitPause(
                isServiceOn = true, isAutoRunning = true, isAlreadyPaused = false, isInitializing = false,
            ),
        )
    }

    @Test
    fun shouldCommitPause_alreadyPaused_returnsFalse() {
        assertFalse(
            OverrideRules.shouldCommitPause(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = true, isInitializing = false,
            ),
        )
    }

    @Test
    fun shouldCommitPause_initializing_returnsFalse() {
        assertFalse(
            OverrideRules.shouldCommitPause(
                isServiceOn = true, isAutoRunning = false, isAlreadyPaused = false, isInitializing = true,
            ),
        )
    }

    // ---- recordOverridePoint: scalingUse gate, 255 cap, ordering, 50-entry cap -----------

    @Test
    fun recordOverridePoint_scalingUseOff_storesRawBrightness() {
        val result = OverrideRules.recordOverridePoint(
            history = emptyList(), lux = 50.0, brightness = 180.0,
            dynamicCompress = 0.8, scalingUse = false,
        )
        assertEquals(1, result.size)
        assertEquals(50.0, result[0].first, 1e-9)
        assertEquals(180.0, result[0].second, 1e-9, "raw brightness stored when scalingUse=false")
    }

    @Test
    fun recordOverridePoint_scalingUseOnWithCompress_decompress() {
        // idealBase = brightness / dynamicCompress = 160 / 0.8 = 200
        val result = OverrideRules.recordOverridePoint(
            history = emptyList(), lux = 50.0, brightness = 160.0,
            dynamicCompress = 0.8, scalingUse = true,
        )
        assertEquals(200.0, result[0].second, 1e-9)
    }

    @Test
    fun recordOverridePoint_scalingUseOnZeroCompress_storesRaw() {
        // dynamicCompress=0 → condition false even with scalingUse=true
        val result = OverrideRules.recordOverridePoint(
            history = emptyList(), lux = 50.0, brightness = 160.0,
            dynamicCompress = 0.0, scalingUse = true,
        )
        assertEquals(160.0, result[0].second, 1e-9)
    }

    @Test
    fun recordOverridePoint_decompress_capAt255() {
        // brightness / dynamicCompress > 255 → cap at 255
        val result = OverrideRules.recordOverridePoint(
            history = emptyList(), lux = 50.0, brightness = 200.0,
            dynamicCompress = 0.5, scalingUse = true,
        )
        assertEquals(255.0, result[0].second, 1e-9, "200/0.5=400 should be capped at 255")
    }

    @Test
    fun recordOverridePoint_newestFirst() {
        // After first insert: [entry1]
        val after1 = OverrideRules.recordOverridePoint(
            history = emptyList(), lux = 10.0, brightness = 100.0,
            dynamicCompress = 0.0, scalingUse = false,
        )
        // After second insert: [entry2, entry1] (newest at index 0)
        val after2 = OverrideRules.recordOverridePoint(
            history = after1, lux = 20.0, brightness = 150.0,
            dynamicCompress = 0.0, scalingUse = false,
        )
        assertEquals(2, after2.size)
        assertEquals(20.0, after2[0].first, 1e-9, "newest entry must be at index 0")
        assertEquals(10.0, after2[1].first, 1e-9, "older entry must be at index 1")
    }

    @Test
    fun recordOverridePoint_dropOldestWhenFull() {
        // Build a history of exactly 50 entries (lux = 0..49)
        var history = emptyList<Pair<Double, Double>>()
        for (i in 0 until 50) {
            history = OverrideRules.recordOverridePoint(
                history = history, lux = i.toDouble(), brightness = 100.0,
                dynamicCompress = 0.0, scalingUse = false,
            )
        }
        assertEquals(50, history.size)
        // History is newest-first: index 0 = lux 49, index 49 = lux 0
        assertEquals(49.0, history[0].first, 1e-9)
        assertEquals(0.0, history[49].first, 1e-9)

        // Insert one more: should drop lux=0 (oldest, at tail)
        history = OverrideRules.recordOverridePoint(
            history = history, lux = 50.0, brightness = 100.0,
            dynamicCompress = 0.0, scalingUse = false,
        )
        assertEquals(50, history.size, "size must remain capped at 50")
        assertEquals(50.0, history[0].first, 1e-9, "newest (lux=50) at index 0")
        assertEquals(1.0, history[49].first, 1e-9, "oldest surviving entry is lux=1 (lux=0 was dropped)")
    }
}
