package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSample
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrightnessPipelineControllerTest {

    private class FakeSensor : LightSensorSource {
        val flow = MutableSharedFlow<LightSample>(extraBufferCapacity = 16)
        override fun samples(): Flow<LightSample> = flow
    }

    private class FakeObserver : BrightnessObserver {
        val flow = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        override fun externalChanges(): Flow<Int> = flow
    }

    private class FakeBrightness : ScreenBrightnessController {
        val writes = mutableListOf<Int>()
        var current = 0
        override fun read(): Int = current
        override fun write(level: Int) { current = level; writes += level }
        override fun forceManualMode() = Unit
        override fun restoreMode() = Unit
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == current
        override fun clearSelfWriteMarker() = Unit
    }

    private fun sample(lux: Double, accuracy: Int = 3) = LightSample(lux.toFloat(), accuracy, 0L)

    // trustUnreliable=true keeps the accuracy gate out of the way; detectOverrides=true for the
    // override test; scaling off so the linear curve branch maps lux straight to brightness.
    private val settings = AabSettings(
        serviceEnabled = true,
        detectOverrides = true,
        trustUnreliableSensor = true,
        scalingEnabled = false,
    )

    // Unconfined dispatcher so the sensor/observer collectors subscribe eagerly and emissions are
    // delivered synchronously; animation delays still respect virtual time (advanceUntilIdle).
    private fun TestScope.newController(
        sensor: LightSensorSource = FakeSensor(),
        brightness: ScreenBrightnessController = FakeBrightness(),
        observer: BrightnessObserver = FakeObserver(),
        clock: () -> Long,
    ): Pair<BrightnessPipelineController, CoroutineScope> {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor,
            brightness = brightness,
            brightnessObserver = observer,
            settingsProvider = { settings },
            scope = scope,
            clock = clock,
        )
        return controller to scope
    }

    @Test
    fun firstRun_throttleDrop_acceptAfterWindow() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        var nowMs = 1000L
        val (controller, scope) = newController(sensor, brightness, clock = { nowMs })
        controller.start()

        // First run: no thresholds yet → reading is accepted and a brightness is written.
        sensor.flow.emit(sample(lux = 10.0))
        advanceUntilIdle()
        assertTrue(brightness.writes.isNotEmpty(), "first run should write a brightness")
        assertEquals(10.0, controller.state.value.lastRawLux)
        assertEquals(1000L, controller.state.value.lastAcceptedMs)

        // Within the throttle window (clock unchanged): a far-outside-band reading passes prof760
        // but is dropped by the task544 throttle gate, leaving state unchanged.
        sensor.flow.emit(sample(lux = 5000.0))
        advanceUntilIdle()
        assertEquals(10.0, controller.state.value.lastRawLux, "throttled tick must not update state")
        assertEquals(1000L, controller.state.value.lastAcceptedMs)

        // Past the throttle window: the reading is accepted.
        nowMs += 2000L
        sensor.flow.emit(sample(lux = 5000.0))
        advanceUntilIdle()
        assertEquals(5000.0, controller.state.value.lastRawLux)
        assertEquals(3000L, controller.state.value.lastAcceptedMs)
        scope.cancel()
    }

    @Test
    fun midCycleSensorEvent_isDropped() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()

        // Cycle 1 runs eagerly until it suspends inside the animation (per-frame delay).
        sensor.flow.emit(sample(lux = 10.0))
        assertTrue(brightness.writes.isNotEmpty(), "cycle should have begun writing")
        assertNull(controller.state.value.lastRawLux, "cycle 1 not yet committed")

        // A reading arriving mid-cycle is dropped by the %AAB_MainLoop mutex (not queued).
        sensor.flow.emit(sample(lux = 5000.0))

        advanceUntilIdle()
        // Only cycle 1 committed; the mid-cycle reading never ran.
        assertEquals(10.0, controller.state.value.lastRawLux)
        scope.cancel()
    }

    @Test
    fun externalOverride_pausesPipeline() = runTest {
        val observer = FakeObserver()
        val (controller, scope) = newController(observer = observer, clock = { 1000L })
        controller.start()

        observer.flow.emit(200)
        advanceUntilIdle()

        assertTrue(controller.state.value.paused, "external override should pause")
        assertEquals(1, controller.state.value.overrideHistory.size)
        scope.cancel()
    }

    @Test
    fun resume_clearsPauseAndSetsInitialBrightness() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
        controller.start()

        // Establish a smoothedLux so setInitialBrightness has something to act on.
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        assertNotNull(controller.state.value.smoothedLux)

        observer.flow.emit(200)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused)

        val writesBefore = brightness.writes.size
        controller.resume()
        advanceUntilIdle()
        assertTrue(!controller.state.value.paused, "resume should clear the pause latch")
        assertTrue(brightness.writes.size > writesBefore, "resume should set an initial brightness")
        scope.cancel()
    }

    @Test
    fun screenOff_hibernatesRuntimeState() = runTest {
        val sensor = FakeSensor()
        val (controller, scope) = newController(sensor, clock = { 1000L })
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        assertNotNull(controller.state.value.smoothedLux)

        controller.onScreenOff()
        advanceUntilIdle()
        assertNull(controller.state.value.smoothedLux, "hibernate should clear smoothed lux")
        assertNull(controller.state.value.threshAbsLow)
        scope.cancel()
    }
}
