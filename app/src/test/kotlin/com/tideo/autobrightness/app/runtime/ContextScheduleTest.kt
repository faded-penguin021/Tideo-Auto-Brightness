package com.tideo.autobrightness.app.runtime

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the time math behind the context engine's prof764 self-scheduling Time context
 * ([millisUntilNextContextWake]) — the wake-at-the-boundary fix for time/Sunrise/Sunset rules not
 * firing in constant light (an on-change sensor stops delivering, so nothing else re-evaluates).
 */
class ContextScheduleTest {

    private fun tokenFor(now: Long, addMinutes: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.MINUTE, addMinutes)
        return "%02d.%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    @Test
    fun futureToday_returnsApproximatelyThatDelay() {
        val now = System.currentTimeMillis()
        val ms = millisUntilNextContextWake(tokenFor(now, 90), now)
        // ~90 min; allow a minute of slack for the seconds-truncation to the minute boundary.
        assertTrue(ms in (88 * 60_000L)..(91 * 60_000L), "expected ~90 min, got ${ms}ms")
    }

    @Test
    fun alreadyPassed_wrapsToTomorrow() {
        val now = System.currentTimeMillis()
        val ms = millisUntilNextContextWake(tokenFor(now, -90), now)
        // A time 90 min in the past wraps to tomorrow → ~22.5 h; definitely more than 12 h.
        assertTrue(ms > 12 * 3_600_000L, "a passed time must wrap to tomorrow, got ${ms}ms")
        assertTrue(ms < 24 * 3_600_000L, "and stay within a day, got ${ms}ms")
    }

    @Test
    fun unparseableToken_returnsMinusOne() {
        assertEquals(-1L, millisUntilNextContextWake("nope", 0L))
        assertEquals(-1L, millisUntilNextContextWake("25.00", 0L)) // hour out of range
        assertEquals(-1L, millisUntilNextContextWake("12.99", 0L)) // minute out of range
        assertEquals(-1L, millisUntilNextContextWake("1200", 0L)) // no separator
    }
}
