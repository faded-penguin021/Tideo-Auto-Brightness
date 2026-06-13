package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AnimationRunnerTest {

    private class FakeBrightness : ScreenBrightnessController {
        val writes = mutableListOf<Int>()
        var current = 0
        var overrideRead: Int? = null
        override fun read(): Int = overrideRead ?: current
        override fun write(level: Int) { current = level; writes += level }
        override fun forceManualMode() = Unit
        override fun restoreMode() = Unit
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == current
        override fun clearSelfWriteMarker() = Unit
    }

    @Test
    fun animate_completes_finalFrameLandsOnTarget() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 4, waitMs = 5, detectOverrides = false)
        assertEquals(AnimationRunner.Result.COMPLETED, result)
        assertEquals(4, fake.writes.size)
        assertEquals(100, fake.writes.last())
        // Monotonic non-decreasing toward the target.
        assertTrue(fake.writes.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun animate_singleStep_writesOnlyTarget() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        runner.animate(from = 200, to = 50, steps = 1, waitMs = 0, detectOverrides = false)
        assertEquals(listOf(50), fake.writes)
    }

    @Test
    fun animate_externalChangeMidAnimation_returnsOverridden() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {
            // Simulate an external write landing after the first frame.
            if (fake.writes.size == 1) fake.overrideRead = 240
        })
        val result = runner.animate(from = 0, to = 100, steps = 5, waitMs = 5, detectOverrides = true)
        assertEquals(AnimationRunner.Result.OVERRIDDEN, result)
        // Aborted early — did not run all 5 frames.
        assertTrue(fake.writes.size < 5)
    }

    @Test
    fun animate_detectionOff_ignoresExternalChange() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = { fake.overrideRead = 240 })
        val result = runner.animate(from = 0, to = 100, steps = 5, waitMs = 5, detectOverrides = false)
        assertEquals(AnimationRunner.Result.COMPLETED, result)
        assertEquals(5, fake.writes.size)
    }
}
