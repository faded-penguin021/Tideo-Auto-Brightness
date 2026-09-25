package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingReadingsTest {

    private fun slot() = PendingReadings(CoroutineScope(UnconfinedTestDispatcher())) {}

    @Test
    fun oneSlot_aNewerReadingReplacesTheOlder_DC069() {
        val p = slot()
        val session = p.newSession()
        assertEquals(PendingReadings.Offer.HELD, p.offer(p.reading(10.0, 3, session)))
        val newer = p.reading(20.0, 3, session)
        assertEquals(PendingReadings.Offer.REPLACED, p.offer(newer), "the older reading is replaced, not queued")
        assertSame(newer, p.take(session, fence = 0L))
        assertNull(p.take(session, fence = 0L))
    }

    @Test
    fun aReplacedRegistrationsLateReading_isNeverHeld_andCannotTakeTheNewOne_DC069() {
        val p = slot()
        val old = p.newSession()
        val late = p.reading(10.0, 3, old)
        val current = p.newSession()
        val fresh = p.reading(20.0, 3, current)
        p.offer(fresh)

        assertEquals(PendingReadings.Offer.STALE, p.offer(late))
        assertSame(fresh, p.current, "a late callback must not displace the live session's reading")
        assertNull(p.take(old, fence = 0L), "a stale tick takes nothing")
        assertSame(fresh, p.current)
        assertTrue(p.restore(late), "a stale reading is dropped, never put back")
        assertSame(fresh, p.take(current, fence = 0L))
    }

    @Test
    fun aReadingThatArrivedBehindAControlEvent_isNotTakenByAnEarlierTick_DC069() {
        val p = slot()
        val session = p.newSession()
        val tickFence = p.behindFence { it }
        p.fenced { true }
        p.offer(p.reading(10.0, 3, session))

        assertNull(p.take(session, tickFence))
        assertEquals(10.0, p.take(session, p.behindFence { it })?.lux)
    }

    @Test
    fun cooldownTimer_firesOnceAndDisarms_DC069() = runTest {
        var fired = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val p = PendingReadings(scope) { fired++ }
        p.armCooldown(500L)
        assertFalse(p.coolingDown, "DC-070: nothing held, so no timer")
        p.offer(p.reading(10.0, 3, p.session))
        p.armCooldown(500L)
        p.armCooldown(500L)
        assertTrue(p.coolingDown)
        testScheduler.advanceUntilIdle()
        assertEquals(1, fired, "an armed timer is not re-armed")
        assertFalse(p.coolingDown)
        scope.cancel()
    }
}
