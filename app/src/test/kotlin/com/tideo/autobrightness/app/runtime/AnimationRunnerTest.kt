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
        private var lastWrite: Int? = null
        override fun read(): Int = overrideRead ?: current
        override fun write(level: Int) { current = level; lastWrite = level; writes += level }
        override fun forceManualMode() = Unit
        override fun restoreMode() = Unit
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastWrite
        override fun isOnScreenSelfWrite(): Boolean = read() == lastWrite
        override fun clearSelfWriteMarker() { lastWrite = null }
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

    // S12.7a/F34: with detection ON but only our own in-flight writes on screen, the band check
    // (task696 java L121-137) sees every read inside [minTarget-2, maxTarget+2] → no override. This
    // is the case the old exact-match self-write check could fire spuriously on (OEM round-trip drift).
    @Test
    fun animate_withDetection_selfWritesOnly_completes() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 4, waitMs = 5, detectOverrides = true)
        assertEquals(AnimationRunner.Result.COMPLETED, result)
        assertEquals(4, fake.writes.size)
        assertEquals(100, fake.writes.last())
    }

    // S12.7a/F34: an opposing-direction external write (rising while we dim) sustained past the
    // 2-consecutive-read debounce aborts as OVERRIDDEN.
    @Test
    fun animate_opposingDirectionWrite_returnsOverridden() = runTest {
        val fake = FakeBrightness()
        // Dimming 200 -> 50; an external app yanks brightness UP to 255 (above maxTarget+2).
        val runner = AnimationRunner(fake, sleep = { if (fake.writes.size >= 1) fake.overrideRead = 255 })
        val result = runner.animate(from = 200, to = 50, steps = 6, waitMs = 5, detectOverrides = true)
        assertEquals(AnimationRunner.Result.OVERRIDDEN, result)
        assertTrue(fake.writes.size < 6)
    }

    // S12.7a/F34: a SINGLE out-of-band transient (one delayed/coalesced read) does not trip the
    // override — the 2-consecutive-read debounce (task696 java L129) absorbs it.
    @Test
    fun animate_singleOutOfBandTransient_completes() = runTest {
        val fake = FakeBrightness()
        var sleeps = 0
        val runner = AnimationRunner(fake, sleep = {
            sleeps++
            // Inject one out-of-band read for exactly the next check, then clear it.
            fake.overrideRead = if (sleeps == 1) 250 else null
        })
        val result = runner.animate(from = 0, to = 100, steps = 5, waitMs = 5, detectOverrides = true)
        assertEquals(AnimationRunner.Result.COMPLETED, result)
        assertEquals(5, fake.writes.size)
    }
}
