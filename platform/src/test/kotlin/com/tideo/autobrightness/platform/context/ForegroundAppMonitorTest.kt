package com.tideo.autobrightness.platform.context

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** H3 glue-seam audit: the D-034 (f) review fix (retain last-known package) had no test. */
@RunWith(RobolectricTestRunner::class)
class ForegroundAppMonitorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private fun resumedEvent(pkg: String, ts: Long): UsageEvents.Event =
        ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(pkg)
            .setTimeStamp(ts)
            .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
            .build()

    @Test
    fun foregroundPackage_retainsLastKnown_whenEventAgesOutOfWindow_D034f() = runTest {
        // An app foregrounded longer than the 3 s query window has no RESUMED event inside it.
        // The clock seam advances 60 s per poll, so poll 1 sees the event and poll 2's window is
        // empty — the flow must keep reporting the last known package, not flip to null
        // (D-034 f: a null here silently breaks every per-app context rule).
        // ts strictly inside the first poll's (now-3000, now) window — the shadow's queryEvents
        // excludes the endTime boundary.
        shadowOf(usm).addEvent(resumedEvent("com.example.reader", 999_000L))
        var t = 1_000_000L
        val monitor = AndroidForegroundAppMonitor(context, clock = { t.also { t += 60_000L } })

        val emissions = monitor.foregroundPackage(intervalMs = 1L).take(2).toList()

        assertEquals(listOf("com.example.reader", "com.example.reader"), emissions)
        // Non-vacuous: the same aged-out window on a FRESH flow (no last-known yet) is null,
        // proving poll 2 above really found nothing and emitted the retained value.
        val fresh = AndroidForegroundAppMonitor(context, clock = { 2_000_000L })
        assertEquals(listOf(null), fresh.foregroundPackage(intervalMs = 1L).take(1).toList())
    }

    @Test
    fun foregroundPackage_isNull_untilFirstResumeEvent() = runTest {
        val monitor = AndroidForegroundAppMonitor(context, clock = { 5_000L })
        assertEquals(listOf(null), monitor.foregroundPackage(intervalMs = 1L).take(1).toList())
    }

    @Test
    fun foregroundPackage_picksTheLatestResumedEventInWindow() = runTest {
        val now = 2_000_000L
        shadowOf(usm).addEvent(resumedEvent("com.example.first", now - 2_000L))
        shadowOf(usm).addEvent(resumedEvent("com.example.second", now - 1_000L))
        val monitor = AndroidForegroundAppMonitor(context, clock = { now })
        assertEquals(
            listOf("com.example.second"),
            monitor.foregroundPackage(intervalMs = 1L).take(1).toList(),
        )
    }
}
