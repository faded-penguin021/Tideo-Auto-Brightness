package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSample
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// DA-043: downstream half of external-control admission; tests command flood handling in pipeline.
@OptIn(ExperimentalCoroutinesApi::class)
class ControlFloodBoundTest {

    private class SilentSensor : LightSensorSource {
        override fun samples(): Flow<LightSample> = MutableSharedFlow()
    }

    private class SilentObserver : BrightnessObserver {
        override fun externalChanges(): Flow<Int> = MutableSharedFlow()
    }

    private class NoOpBrightness : ScreenBrightnessController {
        private var value = 128
        override fun read(): Int = value
        override fun write(level: Int) { value = level }
        override fun forceManualMode() = Unit
        override fun restoreMode() = Unit
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = false
        override fun isOnScreenSelfWrite(): Boolean = false
        override fun clearSelfWriteMarker() = Unit
    }

    private fun TestScope.newController(): Pair<BrightnessPipelineController, CoroutineScope> {
        // Not Unconfined: the consumer must NOT drain while the flood is being posted.
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = SilentSensor(),
            brightness = NoOpBrightness(),
            brightnessObserver = SilentObserver(),
            settingsProvider = { AabSettings() },
            scope = scope,
            clock = { 0L },
        )
        return controller to scope
    }

    @Test
    fun tenThousandReapplies_doNotAccumulateTenThousandQueuedEvents() {
        runTest {
            val (controller, scope) = newController()
            controller.start()

            repeat(10_000) { controller.reapply() }

            assertTrue(
                controller.controlBacklog.pendingCount <= 64,
                "queued control events grew to ${controller.controlBacklog.pendingCount}",
            )
            // Consecutive duplicates collapse, so this flood should not even reach the hard cap.
            assertEquals(
                1,
                controller.controlBacklog.pendingCount,
                "identical back-to-back reapplies should coalesce to a single pending recompute",
            )
            assertEquals(0, controller.controlBacklog.droppedCount, "coalescing should absorb this, not the cap")
            scope.cancel()
        }
    }

    @Test
    fun alternatingVerbs_areBoundedByTheCapEvenThoughNothingCoalesces() {
        runTest {
            val (controller, scope) = newController()
            controller.start()

            // The adversarial shape: alternating opposites, so consecutive-duplicate coalescing can
            // never fire. Without the hard cap this queue grows without limit.
            repeat(5_000) {
                controller.pause()
                controller.resume()
            }

            assertTrue(
                controller.controlBacklog.pendingCount <= 64,
                "alternating flood queued ${controller.controlBacklog.pendingCount} events",
            )
            assertTrue(
                controller.controlBacklog.droppedCount > 0,
                "the cap never engaged, so the bound was not exercised",
            )
            scope.cancel()
        }
    }

    @Test
    fun coalescingNeverLosesAStateTransition() {
        runTest {
            val (controller, scope) = newController()
            controller.start()

            // Pause → Resume → Pause. Same-type-anywhere coalescing would drop the trailing Pause
            // (one is already queued) and leave the pipeline RESUMED against the user's last intent.
            // Only consecutive duplicates may collapse.
            controller.pause()
            controller.resume()
            controller.pause()
            advanceUntilIdle()

            assertTrue(controller.state.value.paused, "the final Pause was swallowed by coalescing")
            scope.cancel()
        }
    }

    @Test
    fun aDrainedQueueAcceptsTheSameVerbAgain() {
        runTest {
            val (controller, scope) = newController()
            controller.start()

            controller.pause()
            advanceUntilIdle()
            controller.resume()
            advanceUntilIdle()
            controller.pause()
            advanceUntilIdle()

            // Coalescing keys off the newest QUEUED event, so once an event is consumed the same
            // verb must be admissible again — otherwise a paused pipeline could never be re-paused.
            assertTrue(controller.state.value.paused)
            assertEquals(0, controller.controlBacklog.pendingCount)
            scope.cancel()
        }
    }
}
