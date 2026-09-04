package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.WriteStatus
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSample
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import com.tideo.autobrightness.platform.sensor.ProximitySensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** [normalize] models an OEM that stores something other than what we asked for. */
    private class FakeBrightness(
        private val normalize: (Int) -> Int = { it },
        private val status: WriteStatus = WriteStatus.ACKNOWLEDGED,
    ) : ScreenBrightnessController {
        val writes = mutableListOf<Int>()
        var current = 0
        var modeRestores = 0
        var manualModeForced = 0
        var manualMode = true
        var forceManualSucceeds = true
        private var lastWrite: Int? = null
        override fun read(): Int = current
        override fun write(level: Int): BrightnessWriteResult {
            val stored = normalize(level)
            current = stored
            lastWrite = stored
            writes += level
            return if (status == WriteStatus.ACKNOWLEDGED) ackWrite(level, stored)
            else unlandedWrite(level, status)
        }
        override fun forceManualMode(): Boolean {
            manualModeForced++
            if (forceManualSucceeds) manualMode = true
            return forceManualSucceeds
        }
        override fun restoreMode() { modeRestores++ }
        override fun isManualMode(): Boolean = manualMode
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastWrite
        override fun clearSelfWriteMarker() { lastWrite = null }
    }

    private class FakeDimming : DimmingCoordinator {
        val applied = mutableListOf<Int>()
        val scaleDynamics = mutableListOf<Double>()
        var disengaged = 0
        override fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double) {
            applied += targetBrightness
            scaleDynamics += scaleDynamic
        }
        override fun disengage() { disengaged++ }
    }

    private class FakeProximity : ProximitySensorSource {
        val flow = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
        override fun near(): Flow<Boolean> = flow
    }

    private fun sample(lux: Double, accuracy: Int = 3) = LightSample(lux.toFloat(), accuracy, 0L)

    // Test settings: trustUnreliable, detectOverrides, no scaling.
    private val settings = AabSettings(
        serviceEnabled = true,
        detectOverrides = true,
        trustUnreliableSensor = true,
        scalingEnabled = false,
    )

    @Test
    fun stop_clearsDimmingAndRestoresBrightnessMode_DA038() = runTest {
        val brightness = FakeBrightness()
        val dimming = FakeDimming()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = FakeSensor(), brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { settings }, scope = scope, dimming = dimming,
        )

        controller.start()
        controller.stop()

        assertEquals(1, dimming.disengaged)
        assertEquals(1, brightness.modeRestores)
        scope.cancel()
    }

    // Unconfined: collectors subscribe eagerly, emissions synchronous (advanceUntilIdle respects virtual time).
    private fun TestScope.newController(
        sensor: LightSensorSource = FakeSensor(),
        brightness: ScreenBrightnessController = FakeBrightness(),
        observer: BrightnessObserver = FakeObserver(),
        clock: () -> Long,
        animationRunner: AnimationRunner = AnimationRunner(brightness),
    ): Pair<BrightnessPipelineController, CoroutineScope> {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor,
            brightness = brightness,
            brightnessObserver = observer,
            settingsProvider = { settings },
            scope = scope,
            clock = clock,
            animationRunner = animationRunner,
        )
        return controller to scope
    }

    @Test
    fun proximityNear_propagatesToPipelineState() = runTest {
        // prof759/task545: proximity → %AAB_Proximity state → ×0.1 damp.
        val proximity = FakeProximity()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = FakeSensor(),
            brightness = FakeBrightness(),
            brightnessObserver = FakeObserver(),
            settingsProvider = { settings },
            scope = scope,
            clock = { 0L },
            proximitySource = proximity,
        )
        controller.start()
        advanceUntilIdle()
        assertEquals(false, controller.state.value.proximityNear)

        proximity.flow.emit(true)
        advanceUntilIdle()
        assertTrue(controller.state.value.proximityNear, "near should set %AAB_Proximity in state")

        proximity.flow.emit(false)
        advanceUntilIdle()
        assertEquals(false, controller.state.value.proximityNear, "far should clear it")
        scope.cancel()
    }

    @Test
    fun firstRun_throttleDrop_acceptAfterWindow() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        var nowMs = 1000L
        val (controller, scope) = newController(sensor, brightness, clock = { nowMs })
        controller.start()

        sensor.flow.emit(sample(lux = 10.0))
        advanceUntilIdle()
        assertTrue(brightness.writes.isNotEmpty(), "first run should write a brightness")
        assertEquals(10.0, controller.state.value.lastRawLux)
        assertEquals(1000L, controller.state.value.lastAcceptedMs)

        // Throttle drops outside-band reading.
        sensor.flow.emit(sample(lux = 5000.0))
        advanceUntilIdle()
        assertEquals(10.0, controller.state.value.lastRawLux, "throttled tick must not update state")
        assertEquals(1000L, controller.state.value.lastAcceptedMs)

        // Past throttle window: accepted.
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

        sensor.flow.emit(sample(lux = 10.0))
        assertTrue(brightness.writes.isNotEmpty(), "cycle should have begun writing")
        assertNull(controller.state.value.lastRawLux, "cycle 1 not yet committed")

        // Mid-cycle reading dropped by %AAB_MainLoop mutex.
        sensor.flow.emit(sample(lux = 5000.0))

        advanceUntilIdle()
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

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        assertNotNull(controller.state.value.smoothedLux)

        brightness.current = 200
        observer.flow.emit(200) // Genuine override (after settle wait)
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
    fun emergencyStop_restoresMaxBrightnessAndFullStops() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        assertTrue(controller.state.value.serviceOn)

        controller.emergencyStop()
        advanceUntilIdle()

        assertEquals(255, brightness.writes.last(), "panic restores max brightness")
        assertTrue(!controller.state.value.serviceOn, "panic is a full stop (%AAB_Service=Off)")
        assertNull(controller.state.value.smoothedLux)

        val writesAfter = brightness.writes.size
        sensor.flow.emit(sample(lux = 5000.0))
        advanceUntilIdle()
        assertEquals(writesAfter, brightness.writes.size, "no writes after emergency stop")
        scope.cancel()
    }

    // D-139: panic effect ordered after in-flight cycle. emergencyStop() JOINS consumer before panic 255.
    @Test
    fun emergencyStop_joinsInFlightCycle_beforePanicWrite_D139() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        var animationUnwound = false
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        // Park animation mid-flight until cancelled, record unwinding.
        val runner = AnimationRunner(
            brightness,
            sleep = {
                try {
                    awaitCancellation()
                } finally {
                    animationUnwound = true
                }
            },
        )
        val controller = BrightnessPipelineController(
            lightSensor = sensor,
            brightness = brightness,
            brightnessObserver = FakeObserver(),
            settingsProvider = { settings },
            scope = scope,
            clock = { 1000L },
            animationRunner = runner,
        )
        controller.start()
        advanceUntilIdle()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle() // Parks in animation sleep()
        assertTrue(brightness.writes.isNotEmpty(), "cycle should be mid-animation")

        controller.emergencyStop()

        assertTrue(
            animationUnwound,
            "emergencyStop must join the in-flight cycle before the panic write (D-139)",
        )
        assertEquals(255, brightness.writes.last(), "panic 255 must be the FINAL write — nothing may trail it")
        assertTrue(!controller.state.value.serviceOn)
        scope.cancel()
    }

    // G2R-F11/F12: settings change reaches pipeline; minBrightness floors target.
    @Test
    fun minBrightness_isHonouredAtRuntime() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val high = settings.copy(minBrightness = 90) // Floors ~5 to 90
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { high }, scope = scope, clock = { 1000L },
        )
        controller.start()
        sensor.flow.emit(sample(lux = 1.0))
        advanceUntilIdle()
        assertEquals(90, controller.state.value.targetBrightness, "min brightness must floor the target")
        assertEquals(90, brightness.writes.last())
        scope.cancel()
    }

    // G2R-F27/D-050/D-109: PWM-sensitive floors hardware at threshold, readout tracks perceived (un-floored).
    @Test
    fun pwmSensitive_floorsHardwareButReadoutTracksPerceived() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val pwm = settings.copy(minBrightness = 1, pwmSensitive = true, dimmingThreshold = 40)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { pwm }, scope = scope, clock = { 1000L },
        )
        controller.start()
        sensor.flow.emit(sample(lux = 1.0)) // Maps to ~5, below threshold 40
        advanceUntilIdle()
        assertEquals(40, brightness.writes.last(), "PWM floor holds the hardware write at threshold")
        assertEquals(40, controller.state.value.lastAppliedBrightness, "hardware-applied value is floored")
        val perceived = controller.state.value.targetBrightness!!
        assertTrue(perceived < 40, "read-out follows the perceived (un-floored) brightness, not the floor; was $perceived")
        scope.cancel()
    }

    // G2R-F26/D-049: handleOverride waits %AAB_CycleTime + RE-READS. Transient must not pause.
    @Test
    fun rapidLightChange_doesNotFalsePause_butRealOverrideDoes() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        var nowMs = 1000L
        val (controller, scope) = newController(sensor, brightness, observer, clock = { nowMs += 50; nowMs })
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        val applied = controller.state.value.lastAppliedBrightness!!
        assertEquals(applied, brightness.current)

        // Transient: value settles back to our applied during wait.
        brightness.current = applied + 30
        observer.flow.emit(applied + 30)
        brightness.current = applied
        advanceUntilIdle()
        assertTrue(!controller.state.value.paused, "transient settling to our value must not pause")

        // Genuine: value stays external through settle wait.
        brightness.current = applied + 60
        observer.flow.emit(applied + 60)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused, "a real external write must pause")
        assertEquals(1, controller.state.value.overrideHistory.size)
        scope.cancel()
    }

    // S12.7a/F64: Set Initial Brightness suppresses override echo during settle window.
    @Test
    fun initialWrite_suppressesOverrideEcho_thenPausesAfterWindow() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        var now = 1000L
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = observer,
            settingsProvider = { settings }, scope = scope, clock = { now },
        )
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        controller.onContextChanged() // Arms settle window (now=1000 → 2500)
        advanceUntilIdle()
        val applied = controller.state.value.lastAppliedBrightness!!

        brightness.current = applied + 80
        observer.flow.emit(applied + 80) // During window: suppressed
        advanceUntilIdle()
        assertTrue(!controller.state.value.paused, "own init-time echo must not pause during the window (F64)")

        now = 4000L // Past window
        observer.flow.emit(applied + 80)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused, "a real external write after the window must pause")
        scope.cancel()
    }

    // S12.7b/G2R-F65: PWM-sensitive floors hardware, but dimming sees UN-FLOORED target.
    @Test
    fun pwmSensitive_superDimmingSeesUnflooredTarget() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val dimming = FakeDimming()
        val pwm = settings.copy(
            minBrightness = 1,
            pwmSensitive = true,
            dimmingThreshold = 40,
            dimmingEnabled = true,
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { pwm }, scope = scope, clock = { 1000L }, dimming = dimming,
        )
        controller.start()
        sensor.flow.emit(sample(lux = 1.0)) // Maps to ~5, below threshold 40
        advanceUntilIdle()

        assertEquals(40, brightness.writes.last(), "PWM floor holds the hardware write at threshold")
        assertTrue(dimming.applied.isNotEmpty(), "dimming should be asked to apply")
        assertTrue(
            dimming.applied.last() < pwm.dimmingThreshold,
            "dimming must see the un-floored target (< threshold) so Extra Dim engages (F65)",
        )
        scope.cancel()
    }

    // S12.7b/G2R-F35: DETECTED override sets pausedByOverride; user Pause does not.
    @Test
    fun detectedOverride_setsPausedByOverride_userPauseDoesNot() = runTest {
        val observer = FakeObserver()
        val (controller, scope) = newController(observer = observer, clock = { 1000L })
        controller.start()

        observer.flow.emit(200)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused)
        assertTrue(controller.state.value.pausedByOverride, "a detected override flags pausedByOverride")

        controller.resume()
        advanceUntilIdle()
        assertTrue(!controller.state.value.pausedByOverride, "resume clears the override flag")

        controller.pause()
        advanceUntilIdle()
        assertTrue(controller.state.value.paused)
        assertTrue(!controller.state.value.pausedByOverride, "a user Pause is not an override")
        scope.cancel()
    }

    // G2R-F71: override settle waits %AAB_CycleTime (task567), NOT %AAB_Throttle.
    @Test
    fun override_settleIsNotGatedByThrottleCooldown() = runTest {
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val longThrottle = settings.copy(throttleDefaultMs = 60_000L)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = FakeSensor(), brightness = brightness, brightnessObserver = observer,
            settingsProvider = { longThrottle }, scope = scope,
            clock = { testScheduler.currentTime },
        )
        controller.start()

        val before = testScheduler.currentTime
        brightness.current = 200
        observer.flow.emit(200)
        advanceUntilIdle()
        val elapsed = testScheduler.currentTime - before

        assertTrue(controller.state.value.paused, "override must pause even within the throttle cooldown")
        assertTrue(elapsed < 1_000L, "override settle must not borrow the throttle window (was ${elapsed}ms)")
        scope.cancel()
    }

    // G2R-F78: published throttle is ACTUAL engine value (loops×wait+10), not ceiling.
    @Test
    fun publishedThrottle_isActualEngineValue_notCeiling() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val st = controller.state.value
        val expected = st.animationSteps!!.toLong() * st.animationWaitMs!! + 10L
        assertEquals(expected, st.throttleMs, "throttle should be the actual steps×wait+10")
        val ceiling = settings.animSteps.toLong() * settings.maxWaitMs + 10L
        assertTrue(st.throttleMs!! < ceiling, "actual throttle must be below the MaxSteps×MaxWait+10 ceiling")
        scope.cancel()
    }

    // G2R-F58: Super Dimming readout populated below threshold.
    @Test
    fun dimmingReadout_populatedBelowThreshold() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val dim = settings.copy(minBrightness = 1, dimmingEnabled = true, dimmingThreshold = 40)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { dim }, scope = scope, clock = { 1000L },
        )
        controller.start()
        sensor.flow.emit(sample(lux = 1.0)) // maps to ~5, below the threshold 40
        advanceUntilIdle()

        val st = controller.state.value
        assertTrue(st.dimmingDS > 0.0, "abs dimming level should be positive below the threshold")
        assertTrue(st.dimmingCurrent > 0.0, "relative dimming strength should be positive below the threshold")
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

    /** Records the [detectOverrides] each animate call received, then lands on the target. */
    private class SpyAnimationRunner(
        private val brightness: ScreenBrightnessController,
    ) : AnimationRunner(brightness) {
        var lastDetectOverrides: Boolean? = null
        override suspend fun animate(
            from: Int,
            to: Int,
            steps: Int,
            waitMs: Long,
            detectOverrides: Boolean,
        ): AnimationOutcome {
            lastDetectOverrides = detectOverrides
            return AnimationOutcome.Completed(brightness.write(to))
        }
    }

    // D-126: settle window suppresses IN-ANIMATION override detection too.
    @Test
    fun cycleDuringSettleWindow_suppressesInAnimationOverrideDetection_D126() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val spy = SpyAnimationRunner(brightness)
        val fast = settings.copy(animSteps = 1, minWaitMs = 1, maxWaitMs = 1) // Tiny throttle
        var now = 1000L
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { fast }, scope = scope, clock = { now }, animationRunner = spy,
        )
        controller.start()

        sensor.flow.emit(sample(lux = 10.0))
        advanceUntilIdle()
        assertEquals(true, spy.lastDetectOverrides, "a normal cycle detects overrides")

        controller.onContextChanged() // Arms settle window (1000 → 2500)
        advanceUntilIdle()

        spy.lastDetectOverrides = null
        now = 1100L // Inside window
        sensor.flow.emit(sample(lux = 1000.0))
        advanceUntilIdle()
        assertEquals(false, spy.lastDetectOverrides, "override detection is suppressed during the settle window")
        assertTrue(!controller.state.value.paused, "a cycle in the settle window must not re-pause")

        spy.lastDetectOverrides = null
        now = 10_000L // Past window
        sensor.flow.emit(sample(lux = 100_000.0))
        advanceUntilIdle()
        assertEquals(true, spy.lastDetectOverrides, "after the window, override detection resumes")
        scope.cancel()
    }

    // DB-082, issue #123. Screen-off hibernate nulls smoothedLux AND lastRawLux, so on wake
    // setInitialBrightness returns at its first line — and armInitialSettle sits BELOW that return,
    // so the one transition where the framework re-asserts SCREEN_BRIGHTNESS is the one transition
    // that arms no suppression at all. The stale self-write marker is from before the sleep, so the
    // framework's wake write reads as external and pauses the pipeline the user never touched.
    @Test
    fun frameworkWriteOnWake_isNotAnOverride_DB082() = runTest {
        val brightness = FakeBrightness()
        val observer = FakeObserver()
        var now = 1000L
        val (controller, scope) = newController(
            brightness = brightness, observer = observer, clock = { now },
        )
        controller.start()
        advanceUntilIdle()

        controller.onScreenOff()
        advanceUntilIdle()
        controller.onScreenOn()
        advanceUntilIdle()

        // The display comes back and the framework re-asserts its own brightness.
        observer.flow.emit(200)
        advanceUntilIdle()

        assertTrue(
            !controller.state.value.pausedByOverride,
            "a wake-time framework write must not read as a manual override",
        )
        assertTrue(!controller.state.value.paused, "and must not pause the pipeline")
        scope.cancel()
    }

    // The other half: suppression must not swallow a real override once the wake has settled.
    @Test
    fun genuineOverrideAfterTheWakeWindow_stillPauses_DB082() = runTest {
        val brightness = FakeBrightness()
        val observer = FakeObserver()
        var now = 1000L
        val (controller, scope) = newController(
            brightness = brightness, observer = observer, clock = { now },
        )
        controller.start()
        advanceUntilIdle()

        controller.onScreenOn()
        advanceUntilIdle()

        now = 10_000L // well past the wake settle window
        observer.flow.emit(200)
        advanceUntilIdle()

        assertTrue(controller.state.value.pausedByOverride, "a real slider move still pauses")
        scope.cancel()
    }

    // --- DC-004 / DC-005 / DC-006 / DC-007 ---

    // Change 4's deadband, pinned in BOTH directions: 1 domain step is representational drift, 2 is not.
    @Test
    fun settledWithinOneDomainStep_doesNotPause_butTwoDoes() = runTest {
        for ((drift, shouldPause) in listOf(1 to false, 2 to true)) {
            val sensor = FakeSensor()
            val observer = FakeObserver()
            val brightness = FakeBrightness()
            val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
            controller.start()
            sensor.flow.emit(sample(lux = 50.0))
            advanceUntilIdle()

            val applied = controller.state.value.lastAppliedBrightness!!
            brightness.current = applied + drift
            observer.flow.emit(applied + drift)
            advanceUntilIdle()

            assertEquals(
                shouldPause, controller.state.value.paused,
                "a $drift-step settled deviation: paused should be $shouldPause",
            )
            scope.cancel()
        }
    }

    // #127: a non-MANUAL mode at commit time means Tideo no longer owns the mode it writes against.
    @Test
    fun nonManualModeAtCommit_dismissesAndRecovers_manualModePauses() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        brightness.manualMode = false
        val forcedBefore = brightness.manualModeForced
        brightness.current = 42
        observer.flow.emit(42)
        advanceUntilIdle()

        assertFalse(controller.state.value.paused, "an ambiguous mode must not be labelled a manual override")
        assertTrue(brightness.manualModeForced > forcedBefore, "the mode must be reclaimed")
        assertEquals(
            OverrideDisposition.DISMISSED_MODE,
            controller.state.value.overrideDiagnostic?.disposition,
        )
        scope.cancel()
    }

    @Test
    fun sameWriteWithManualMode_stillPauses() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        brightness.manualMode = true
        brightness.current = 42
        observer.flow.emit(42)
        advanceUntilIdle()

        assertTrue(controller.state.value.paused, "the control: MANUAL mode with the same write MUST pause")
        assertEquals(OverrideDisposition.PAUSED, controller.state.value.overrideDiagnostic?.disposition)
        scope.cancel()
    }

    // A mode recovery that FAILS still must not pause — pausing would print the misattribution this fixes.
    @Test
    fun failedModeRecovery_stillDoesNotPause_andIsRecorded() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        brightness.manualMode = false
        brightness.forceManualSucceeds = false
        brightness.current = 42
        observer.flow.emit(42)
        advanceUntilIdle()

        assertFalse(controller.state.value.paused)
        assertEquals(
            OverrideDisposition.MODE_RECOVERY_FAILED,
            controller.state.value.overrideDiagnostic?.disposition,
        )
        scope.cancel()
    }

    // DC-004: the baseline is what the provider ACKNOWLEDGED, not what we asked for.
    @Test
    fun lastAppliedBrightness_isTheAcknowledgedValue_notTheRequestedOne() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness(normalize = { it - 3 })
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val requested = brightness.writes.last()
        assertEquals(requested - 3, controller.state.value.lastAppliedBrightness)
        scope.cancel()
    }

    // DC-007: the continuous record must exist on a device that never fires an override at all.
    @Test
    fun lastBrightnessWrite_isPopulatedByAnOrdinaryCycle_withNoOverrideAnywhere() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness(normalize = { it - 3 })
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val write = controller.state.value.lastBrightnessWrite
        assertNotNull(write, "device check 3 reads this without an override having fired")
        assertEquals(brightness.writes.last(), write.requestedDomain)
        assertEquals(brightness.writes.last() - 3, write.acknowledgedDomain)
        assertNull(controller.state.value.overrideDiagnostic, "nothing was detected, so nothing is recorded")
        scope.cancel()
    }

    // DC-007: the diagnostic reports the detector the EVENT carried, not a re-derivation.
    @Test
    fun observerRoute_recordsOBSERVER_asTheSource() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, observer, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        brightness.current = 42
        observer.flow.emit(42)
        advanceUntilIdle()

        assertEquals(OverrideSource.OBSERVER, controller.state.value.overrideDiagnostic?.source)
        scope.cancel()
    }

    @Test
    fun animationAbort_recordsANIMATION_BAND_andTheTriggeringRead() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(
            sensor,
            brightness,
            clock = { 1000L },
            animationRunner = OverridingAnimationRunner(brightness, trigger = 9),
        )
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val diagnostic = controller.state.value.overrideDiagnostic
        assertEquals(OverrideSource.ANIMATION_BAND, diagnostic?.source)
        assertEquals(9, diagnostic?.observed, "the read that tripped the detector, not a later re-read")
        // DC-004: without the pre-post baseline refresh this PAUSES, so pin the disposition too.
        assertEquals(brightness.current, controller.state.value.lastAppliedBrightness)
        assertEquals(OverrideDisposition.DISMISSED_DRIFT, diagnostic?.disposition)
        assertFalse(controller.state.value.paused, "our own aborted sweep must not pause the pipeline")
        scope.cancel()
    }

    // DC-008: unconfirmed frames must follow the same baseline rule as the direct-write path.
    @Test
    fun animatedCycleWithUnacknowledgedFrames_recordsTheRequestedBaseline() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness(status = WriteStatus.WRITTEN_UNACKNOWLEDGED)
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val st = controller.state.value
        assertEquals(brightness.writes.last(), st.lastAppliedBrightness, "requested is the only estimate")
        assertEquals(WriteStatus.WRITTEN_UNACKNOWLEDGED, st.lastBrightnessWrite?.status)
        scope.cancel()
    }

    /** Always aborts with [trigger] as the read that tripped the band detector. */
    private class OverridingAnimationRunner(
        private val brightness: ScreenBrightnessController,
        private val trigger: Int,
    ) : AnimationRunner(brightness) {
        override suspend fun animate(
            from: Int,
            to: Int,
            steps: Int,
            waitMs: Long,
            detectOverrides: Boolean,
        ): AnimationOutcome {
            val ack = brightness.write(to)
            return AnimationOutcome.Overridden(ack, triggerObserved = trigger)
        }
    }
}
