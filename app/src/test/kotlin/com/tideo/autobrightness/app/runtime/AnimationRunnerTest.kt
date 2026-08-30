package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.WriteStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AnimationRunnerTest {

    /** [normalize] models an OEM that stores something other than what we asked for. */
    private class FakeBrightness(
        private val normalize: (Int) -> Int = { it },
    ) : ScreenBrightnessController {
        val writes = mutableListOf<Int>()
        var current = 0
        var overrideRead: Int? = null
        private var lastWrite: Int? = null
        override fun read(): Int = overrideRead ?: current
        override fun write(level: Int): BrightnessWriteResult {
            val stored = normalize(level)
            current = stored
            lastWrite = stored
            writes += level
            return ackWrite(level, stored)
        }
        override fun forceManualMode() = true
        override fun restoreMode() = Unit
        override fun isManualMode() = true
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastWrite
        override fun clearSelfWriteMarker() { lastWrite = null }
    }

    @Test
    fun animate_completes_finalFrameLandsOnTarget() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 4, waitMs = 5, detectOverrides = false)
        assertIs<AnimationOutcome.Completed>(result)
        assertEquals(4, fake.writes.size)
        assertEquals(100, fake.writes.last())
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
        assertIs<AnimationOutcome.Overridden>(result)
        // Aborted early — did not run all 5 frames.
        assertTrue(fake.writes.size < 5)
    }

    @Test
    fun animate_detectionOff_ignoresExternalChange() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = { fake.overrideRead = 240 })
        val result = runner.animate(from = 0, to = 100, steps = 5, waitMs = 5, detectOverrides = false)
        assertIs<AnimationOutcome.Completed>(result)
        assertEquals(5, fake.writes.size)
    }

    // S12.7a/F34: detection ON, only our in-flight writes → no override (band check task696 java L121-137).
    @Test
    fun animate_withDetection_selfWritesOnly_completes() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 4, waitMs = 5, detectOverrides = true)
        assertIs<AnimationOutcome.Completed>(result)
        assertEquals(4, fake.writes.size)
        assertEquals(100, fake.writes.last())
    }

    // S12.7a/F34: opposing-direction external write sustained past 2-consecutive-read debounce → OVERRIDDEN.
    @Test
    fun animate_opposingDirectionWrite_returnsOverridden() = runTest {
        val fake = FakeBrightness()
        val runner = AnimationRunner(fake, sleep = { if (fake.writes.size >= 1) fake.overrideRead = 255 })
        val result = runner.animate(from = 200, to = 50, steps = 6, waitMs = 5, detectOverrides = true)
        assertIs<AnimationOutcome.Overridden>(result)
        assertTrue(fake.writes.size < 6)
    }

    // S12.7a/F34: SINGLE out-of-band transient does not trip override (2-consecutive-read debounce absorbs it).
    @Test
    fun animate_singleOutOfBandTransient_completes() = runTest {
        val fake = FakeBrightness()
        var sleeps = 0
        val runner = AnimationRunner(fake, sleep = {
            sleeps++
            fake.overrideRead = if (sleeps == 1) 250 else null
        })
        val result = runner.animate(from = 0, to = 100, steps = 5, waitMs = 5, detectOverrides = true)
        assertIs<AnimationOutcome.Completed>(result)
        assertEquals(5, fake.writes.size)
    }

    // --- DC-004: acknowledged-write awareness ---

    // The #126 headline at the AnimationRunner level: an OEM that stores our frames well OUTSIDE the
    // sweep band must not read as an override. Without the acknowledgement check this returns Overridden.
    @Test
    fun normalizedFramesFarOutsideTheBand_doNotTripTheDetector_126() = runTest {
        val fake = FakeBrightness(normalize = { it + 40 })
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 50, waitMs = 5, detectOverrides = true)
        assertIs<AnimationOutcome.Completed>(result)
        assertEquals(50, fake.writes.size, "no frame should have been skipped")
        assertEquals(140, result.lastAcknowledged?.acknowledgedDomain)
    }

    // Same fixture, but the value on screen is FOREIGN: it must still trip, and report the read that did it.
    @Test
    fun foreignValueOutsideTheBand_stillTrips_andReportsTheTriggeringRead() = runTest {
        val fake = FakeBrightness(normalize = { it + 40 })
        val runner = AnimationRunner(fake, sleep = { fake.overrideRead = 7 })
        val result = runner.animate(from = 200, to = 100, steps = 6, waitMs = 5, detectOverrides = true)
        val overridden = assertIs<AnimationOutcome.Overridden>(result)
        assertEquals(7, overridden.triggerObserved, "the read that tripped it, not a later re-read")
        assertTrue(fake.writes.size < 6, "aborted early")
    }

    @Test
    fun unacknowledgedFrames_neverBecomeTheAcknowledgement() = runTest {
        val fake = RefusingBrightness()
        val runner = AnimationRunner(fake, sleep = {})
        val result = runner.animate(from = 0, to = 100, steps = 4, waitMs = 0, detectOverrides = false)
        assertIs<AnimationOutcome.Completed>(result)
        assertNull(result.lastAcknowledged, "a REFUSED frame says nothing about what is on screen")
    }

    /** Every write is REFUSED, so no frame is ever acknowledged. */
    private class RefusingBrightness : ScreenBrightnessController {
        override fun read(): Int = 0
        override fun write(level: Int) = unlandedWrite(level, WriteStatus.REFUSED)
        override fun forceManualMode() = true
        override fun restoreMode() = Unit
        override fun isManualMode() = true
        override fun isSelfWrite(rawDeviceValue: Int) = false
        override fun clearSelfWriteMarker() = Unit
    }
}
