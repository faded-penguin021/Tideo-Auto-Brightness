package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
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
        private var lastWrite: Int? = null
        override fun read(): Int = current
        override fun write(level: Int) { current = level; lastWrite = level; writes += level }
        override fun forceManualMode() = Unit
        override fun restoreMode() = Unit
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastWrite
        override fun isOnScreenSelfWrite(): Boolean = current == lastWrite
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
    fun proximityNear_propagatesToPipelineState() = runTest {
        // prof759/task545: the proximity collector flips %AAB_Proximity in the runtime state, which the
        // cycle runner feeds into BrightnessPolicyInput.proximityNear (×0.1 alpha damp). It never pauses.
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

        // Simulate the external write landing on the system setting, then the observer firing: after
        // the settle wait the on-screen value (200) is no longer our last applied → genuine override.
        brightness.current = 200
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

        // task528: brightness restored to max, service fully off, runtime state cleared.
        assertEquals(255, brightness.writes.last(), "panic restores max brightness")
        assertTrue(!controller.state.value.serviceOn, "panic is a full stop (%AAB_Service=Off)")
        assertNull(controller.state.value.smoothedLux)

        // A further sensor reading must NOT revive the pipeline (sensor unsubscribed).
        val writesAfter = brightness.writes.size
        sensor.flow.emit(sample(lux = 5000.0))
        advanceUntilIdle()
        assertEquals(writesAfter, brightness.writes.size, "no writes after emergency stop")
        scope.cancel()
    }

    // D-139 (F-backlog U1): the panic effect must be ORDERED AFTER the in-flight cycle. emergencyStop
    // used to fire-and-forget cancel the consumer and then write the panic 255 immediately — on the
    // real multi-threaded dispatcher an animation frame already past its delay could serialize AFTER
    // the panic write, leaving the screen at a stale mid-animation value on the safety path. The fix
    // JOINS the consumer first; this locks the contract: when emergencyStop() returns, the in-flight
    // animation has fully unwound and the panic 255 is the final write.
    @Test
    fun emergencyStop_joinsInFlightCycle_beforePanicWrite_D139() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        var animationUnwound = false
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        // Park the animation mid-flight: the first frame's sleep suspends until cancelled, and the
        // finally records that the cycle coroutine actually unwound (vs merely being marked cancelled).
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
        advanceUntilIdle() // collectors subscribe (Standard dispatcher runs nothing eagerly)
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle() // cycle runs to the first animation frame and parks in sleep()
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

    // G2R-F11/F12: a settings change must reach the pipeline. The controller reads settingsProvider()
    // freshly each cycle, so a higher minBrightness floors the applied target on the next reading.
    @Test
    fun minBrightness_isHonouredAtRuntime() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        // High min brightness; low lux → mapped target would be ~5, but must be floored to 90.
        val high = settings.copy(minBrightness = 90)
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

    // G2R-F27/D-050: in PWM-sensitive mode the HARDWARE brightness is floored at the dimming
    // threshold when the target would fall below it (task698 step 3). D-109: the read-out
    // (targetBrightness) must still track the PERCEIVED (un-floored) brightness, NOT stick at the floor.
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
        sensor.flow.emit(sample(lux = 1.0)) // maps to ~5, below threshold 40
        advanceUntilIdle()
        // Hardware write + lastAppliedBrightness are floored to the threshold (D-050).
        assertEquals(40, brightness.writes.last(), "PWM floor holds the hardware write at threshold")
        assertEquals(40, controller.state.value.lastAppliedBrightness, "hardware-applied value is floored")
        // The read-out tracks the perceived, un-floored calculated brightness (D-109).
        val perceived = controller.state.value.targetBrightness!!
        assertTrue(perceived < 40, "read-out follows the perceived (un-floored) brightness, not the floor; was $perceived")
        scope.cancel()
    }

    // G2R-F26/D-049: handleOverride waits %AAB_CycleTime then RE-READS — a value that settles back to
    // our applied brightness during the wait must NOT pause; one still external after it MUST.
    @Test
    fun rapidLightChange_doesNotFalsePause_butRealOverrideDoes() = runTest {
        val sensor = FakeSensor()
        val observer = FakeObserver()
        val brightness = FakeBrightness()
        // Advancing clock so the cycle records a non-zero %AAB_CycleTime → the settle wait is real.
        var nowMs = 1000L
        val (controller, scope) = newController(sensor, brightness, observer, clock = { nowMs += 50; nowMs })
        controller.start()

        // Establish a committed cycle so lastAppliedBrightness is set and on-screen.
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()
        val applied = controller.state.value.lastAppliedBrightness!!
        assertEquals(applied, brightness.current)

        // False positive: an external value is present when the observer fires, but our pipeline
        // restores our value before the cycle-time settle completes → must NOT pause (D-049 #1).
        brightness.current = applied + 30
        observer.flow.emit(applied + 30)
        brightness.current = applied // settled back to our value during the wait
        advanceUntilIdle()
        assertTrue(!controller.state.value.paused, "transient settling to our value must not pause")

        // Genuine override: the external value stays on screen through the settle wait.
        brightness.current = applied + 60
        observer.flow.emit(applied + 60)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused, "a real external write must pause")
        assertEquals(1, controller.state.value.overrideHistory.size)
        scope.cancel()
    }

    // S12.7a/F64: after a Set Initial Brightness self-write (context swap / reinit / resume), the
    // ContentObserver echo of our OWN write must NOT be flagged as an override during the settle
    // window — but a genuine external write after the window still pauses.
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

        // Seed a reading so Set Initial Brightness has a lux to act on; runCycle does NOT arm the window.
        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        // A context swap re-runs Set Initial Brightness → arms the settle window (now=1000 → 2500).
        controller.onContextChanged()
        advanceUntilIdle()
        val applied = controller.state.value.lastAppliedBrightness!!

        // A divergent value on screen (e.g. the AUTO→MANUAL mode-flip recompute) echoes through the
        // observer DURING the settle window → must be suppressed even though it differs from ours.
        brightness.current = applied + 80
        observer.flow.emit(applied + 80)
        advanceUntilIdle()
        assertTrue(!controller.state.value.paused, "own init-time echo must not pause during the window (F64)")

        // Past the settle window the same divergent external value still pauses.
        now = 4000L
        observer.flow.emit(applied + 80)
        advanceUntilIdle()
        assertTrue(controller.state.value.paused, "a real external write after the window must pause")
        scope.cancel()
    }

    // S12.7b/G2R-F65: with PWM-sensitive on, the HARDWARE write is floored to the dimming threshold,
    // but super dimming must decide off the UN-FLOORED engine target so reduce_bright_colors actually
    // engages below the threshold (otherwise floored==threshold and dimming never turns on).
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
        sensor.flow.emit(sample(lux = 1.0)) // maps to ~5, below threshold 40
        advanceUntilIdle()

        assertEquals(40, brightness.writes.last(), "PWM floor holds the hardware write at threshold")
        assertTrue(dimming.applied.isNotEmpty(), "dimming should be asked to apply")
        assertTrue(
            dimming.applied.last() < pwm.dimmingThreshold,
            "dimming must see the un-floored target (< threshold) so Extra Dim engages (F65)",
        )
        scope.cancel()
    }

    // S12.7b/G2R-F35: a DETECTED manual override latches pausedByOverride (drives the high-priority
    // notification + toast); a user-initiated Pause does NOT.
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

    // G2R-F71: the manual-override settle waits %AAB_CycleTime (task567 act7), NOT the %AAB_Throttle
    // reactivity cooldown. prof755→task567 override detection is a separate Tasker profile from the
    // task544 main loop the throttle gates, so a genuine override must still be acted on inside the
    // cooldown window. With a deliberately long throttle and no measured CycleTime yet, the override
    // pauses promptly (settle ≈ 0) — a throttle-coupled settle would have stalled it ~60s.
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

    // G2R-F78: after a cycle that changes brightness, the published throttle (%AAB_Throttle) is the
    // engine's ACTUAL animation duration (loops×wait+10, first cycle has no prior cycle-time), NOT
    // floored at MaxSteps×MaxWait+10 — so it must be able to read below the ceiling.
    @Test
    fun publishedThrottle_isActualEngineValue_notCeiling() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val (controller, scope) = newController(sensor, brightness, clock = { 1000L })
        controller.start()

        sensor.flow.emit(sample(lux = 50.0))
        advanceUntilIdle()

        val st = controller.state.value
        // First cycle: prev=null → no cycle-time term, so throttle == loops×wait+10.
        val expected = st.animationSteps!!.toLong() * st.animationWaitMs!! + 10L
        assertEquals(expected, st.throttleMs, "throttle should be the actual steps×wait+10")
        val ceiling = settings.animSteps.toLong() * settings.maxWaitMs + 10L
        assertTrue(st.throttleMs!! < ceiling, "actual throttle must be below the MaxSteps×MaxWait+10 ceiling")
        scope.cancel()
    }

    // G2R-F58: the Super Dimming live readout (%AAB_DimmingDS abs / %AAB_DimmingCurrent rel) is
    // populated from SoftwareDimming whenever the target falls below the dimming threshold.
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
        override suspend fun animate(from: Int, to: Int, steps: Int, waitMs: Long, detectOverrides: Boolean): Result {
            lastDetectOverrides = detectOverrides
            brightness.write(to)
            return Result.COMPLETED
        }
    }

    // D-126: the post-init/resume settle window (the Tasker Set-Initial-Brightness mutex) must suppress
    // the IN-ANIMATION override detection too, not just the ContentObserver echo — otherwise a Resume's
    // brightness jump followed by a light-change cycle inside the window has its own transition (or the
    // OEM's lingering mode-flip adjustment) mis-seen as a manual override, re-pausing in a loop.
    @Test
    fun cycleDuringSettleWindow_suppressesInAnimationOverrideDetection_D126() = runTest {
        val sensor = FakeSensor()
        val brightness = FakeBrightness()
        val spy = SpyAnimationRunner(brightness)
        // Minimal animation → tiny throttle (transitionDurationMs ≈ wait+10), so a second cycle can run
        // INSIDE the 1500 ms settle window without the throttle gate dropping it.
        val fast = settings.copy(animSteps = 1, minWaitMs = 1, maxWaitMs = 1)
        var now = 1000L
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor, brightness = brightness, brightnessObserver = FakeObserver(),
            settingsProvider = { fast }, scope = scope, clock = { now }, animationRunner = spy,
        )
        controller.start()

        // Seed a cycle (no settle armed yet) → detection is ON. (Rising lux each phase guarantees the
        // target moves past smoothing inertia, so animate() is actually called every cycle.)
        sensor.flow.emit(sample(lux = 10.0))
        advanceUntilIdle()
        assertEquals(true, spy.lastDetectOverrides, "a normal cycle detects overrides")

        // Resume/context swap re-runs Set Initial Brightness → arms the settle window (1000 → 2500).
        controller.onContextChanged()
        advanceUntilIdle()

        // A light-change cycle INSIDE the window: its animation must NOT detect overrides (D-126).
        spy.lastDetectOverrides = null
        now = 1100L
        sensor.flow.emit(sample(lux = 1000.0))
        advanceUntilIdle()
        assertEquals(false, spy.lastDetectOverrides, "override detection is suppressed during the settle window")
        assertTrue(!controller.state.value.paused, "a cycle in the settle window must not re-pause")

        // Past the window: detection resumes for the next cycle.
        spy.lastDetectOverrides = null
        now = 10_000L
        sensor.flow.emit(sample(lux = 100_000.0))
        advanceUntilIdle()
        assertEquals(true, spy.lastDetectOverrides, "after the window, override detection resumes")
        scope.cancel()
    }
}
