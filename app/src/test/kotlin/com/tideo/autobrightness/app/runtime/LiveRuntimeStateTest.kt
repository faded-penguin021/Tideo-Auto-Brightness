package com.tideo.autobrightness.app.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S12.9d: staleness gate (FRESH → AGING → STALE). */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveRuntimeStateTest {

    private val owner = Any()

    @Before fun setUp() {
        LiveRuntimeState.reset()
        LiveRuntimeState.claim(owner)
    }
    @After fun tearDown() = LiveRuntimeState.reset()

    @Test
    fun classify_boundaries() {
        assertEquals(Staleness.STALE, classifyStaleness(null, 0))
        assertEquals(Staleness.FRESH, classifyStaleness(1_000L, 1_000L))
        assertEquals(Staleness.FRESH, classifyStaleness(0L, 2_999L))
        assertEquals(Staleness.AGING, classifyStaleness(0L, 3_000L))
        assertEquals(Staleness.AGING, classifyStaleness(0L, 10_000L))
        assertEquals(Staleness.STALE, classifyStaleness(0L, 10_001L))
    }

    @Test
    fun publish_stampsLastPublishMs() {
        assertNull(LiveRuntimeState.pipeline.value.lastPublishMs)
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 12.0), activeContext = null, nowMs = 42L)
        assertEquals(42L, LiveRuntimeState.pipeline.value.lastPublishMs)
        assertTrue(LiveRuntimeState.serviceRunning.value)
    }

    @Test
    fun staleness_freshAfterPublishThenStaleAfter11s() = runTest {
        var now = 0L
        LiveRuntimeState.publish(owner, PipelineState(), activeContext = null, nowMs = 0L)

        val emissions = mutableListOf<Staleness>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            LiveRuntimeState.staleness(clock = { now }, intervalMs = 1_000L).collect { emissions.add(it) }
        }
        runCurrent()
        assertEquals(Staleness.FRESH, emissions.last(), "fresh immediately after a publish")

        now = 11_000L
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(Staleness.STALE, emissions.last(), "stale after 11 s with no republish")
    }

    @Test
    fun reset_clearsSnapshotAndRunning() {
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 5.0), activeContext = "Cinema", nowMs = 100L)
        LiveRuntimeState.reset()
        assertNull(LiveRuntimeState.pipeline.value.lastPublishMs)
        assertNull(LiveRuntimeState.pipeline.value.smoothedLux)
        assertNull(LiveRuntimeState.activeContext.value)
        assertFalse(LiveRuntimeState.serviceRunning.value)
        // Null stamp = STALE (UI never shows dead loop as live).
        assertEquals(Staleness.STALE, classifyStaleness(LiveRuntimeState.pipeline.value.lastPublishMs, 0L))
    }

    @Test
    fun publish_withoutAClaim_isIgnored() {
        LiveRuntimeState.reset()
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 5.0), activeContext = null, nowMs = 1L)
        assertNull(LiveRuntimeState.pipeline.value.lastPublishMs)
        assertFalse(LiveRuntimeState.serviceRunning.value)
    }

    @Test
    fun publish_afterRelease_keepsTheLastAcceptedSnapshot() {
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 5.0), activeContext = "Cinema", nowMs = 1L)
        LiveRuntimeState.release(owner)
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 9.0), activeContext = "Night", nowMs = 2L)
        assertEquals(1L, LiveRuntimeState.pipeline.value.lastPublishMs)
        assertEquals(5.0, LiveRuntimeState.pipeline.value.smoothedLux)
        assertEquals("Cinema", LiveRuntimeState.activeContext.value)
        assertTrue(LiveRuntimeState.serviceRunning.value, "the grace window still shows the last snapshot")
    }

    @Test
    fun publish_afterTheWatchdogReset_doesNotMarkTheServiceRunning() {
        LiveRuntimeState.publish(owner, PipelineState(), activeContext = null, nowMs = 1L)
        assertTrue(LiveRuntimeState.resetIfUnowned(LiveRuntimeState.release(owner)))
        LiveRuntimeState.publish(owner, PipelineState(serviceOn = true), activeContext = "Night", nowMs = 2L)
        assertFalse(LiveRuntimeState.serviceRunning.value)
        assertNull(LiveRuntimeState.pipeline.value.lastPublishMs)
        assertNull(LiveRuntimeState.activeContext.value)
    }

    @Test
    fun publish_fromAPredecessor_afterASuccessorClaims_leavesTheSuccessorsSnapshot() {
        val successor = Any()
        LiveRuntimeState.release(owner)
        LiveRuntimeState.claim(successor)
        LiveRuntimeState.publish(successor, PipelineState(smoothedLux = 7.0), activeContext = "Day", nowMs = 3L)
        LiveRuntimeState.publish(owner, PipelineState(smoothedLux = 1.0), activeContext = "Night", manualOverride = true, nowMs = 4L)
        assertEquals(3L, LiveRuntimeState.pipeline.value.lastPublishMs)
        assertEquals(7.0, LiveRuntimeState.pipeline.value.smoothedLux)
        assertEquals("Day", LiveRuntimeState.activeContext.value)
        assertFalse(LiveRuntimeState.manualOverride.value)
    }
}
