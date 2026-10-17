package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessSample
import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.observe.BrightnessObserver
import com.tideo.autobrightness.platform.sensor.LightSample
import com.tideo.autobrightness.platform.sensor.LightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import org.junit.Test

/** Bright-light hand-off in the pipeline (#136, DD-070, DD-071, DD-073). Default curve: entry 25 132 lx, exit 20 105.6 lx. */
@OptIn(ExperimentalCoroutinesApi::class)
class HandoffPipelineTest {

    private class Sensor(private val log: MutableList<String>) : LightSensorSource {
        val flow = MutableSharedFlow<LightSample>(extraBufferCapacity = 16)
        override fun samples(onRegistered: (Boolean) -> Unit, onCallback: (LightSample) -> Unit): Flow<LightSample> =
            flow.onStart { log += "register"; onRegistered(true) }
    }

    private class Observer : BrightnessObserver {
        val flow = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        override fun externalChanges(): Flow<Int> = flow
    }

    private class ModeBrightness(private val log: MutableList<String>) : ScreenBrightnessController {
        val writes = mutableListOf<Int>()
        var writesWhileAutomatic = 0
        var current = 100
        var automatic = false
        var marker = false
        var handOffLands = true
        var reclaimLands = true
        var handOffs = 0
        var reclaims = 0
        var manualForced = 0
        var restores = 0
        var onHandOff: () -> Unit = {}
        private var lastWrite: Int? = null

        override fun read(): Int = current
        override fun write(level: Int): BrightnessWriteResult {
            if (automatic) writesWhileAutomatic++
            writes += level
            current = level
            lastWrite = level
            return ackWrite(level)
        }
        override fun forceManualMode(): Boolean {
            manualForced++
            automatic = false
            marker = false
            return true
        }
        override fun handOffToSystem(): Boolean {
            handOffs++
            log += "handOff"
            onHandOff()
            if (handOffLands) { automatic = true; marker = true }
            return handOffLands
        }
        override fun reclaimFromSystem(): Boolean {
            reclaims++
            log += "reclaim"
            if (reclaimLands) { automatic = false; marker = false }
            return reclaimLands
        }
        override fun reconcileHandoff(): Boolean {
            log += "reconcile"
            if (marker && !automatic) marker = false
            return marker
        }
        override fun restoreMode() { restores++; automatic = false; marker = false }
        override fun isManualMode(): Boolean = !automatic
        override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastWrite
        override fun clearSelfWriteMarker() { lastWrite = null }
    }

    /** A panel whose dumpsys value is 16 x the level; [answers] false models no DUMP, root or Shizuku. */
    private class Display(private val brightness: ModeBrightness) : DisplayBrightnessReader {
        var answers = true
        var raw: (Int) -> Double = { it * 16.0 }
        override suspend fun read(field: String?): DisplayBrightnessSample? =
            if (answers) DisplayBrightnessSample("mScreenBrightness", raw(brightness.current)) else null
    }

    private class Calibrations : DisplayCalibrationSink {
        var started = 0
        val finished = mutableListOf<DisplayCalibrationRecord?>()
        override suspend fun started() { started++ }
        override suspend fun finished(record: DisplayCalibrationRecord?) { finished += record }
    }

    private class Dimming(private val log: MutableList<String>) : DimmingCoordinator {
        val applied = mutableListOf<Int>()
        override fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double) { applied += targetBrightness }
        override fun disengage() { log += "disengage" }
    }

    private val handoffOn = AabSettings(
        serviceEnabled = true,
        detectOverrides = true,
        trustUnreliableSensor = true,
        scalingEnabled = false,
        handoffEnabled = true,
    )

    private inner class Rig(val test: TestScope, initial: AabSettings = handoffOn) {
        val log = mutableListOf<String>()
        val sensor = Sensor(log)
        val observer = Observer()
        val brightness = ModeBrightness(log)
        val dimming = Dimming(log)
        val flashes = mutableListOf<Boolean>()
        val display = Display(brightness)
        val calibrations = Calibrations()
        var settings = initial
        var settingsReadMs = 0L
        var nowMs = 1_000L
        val scope = CoroutineScope(UnconfinedTestDispatcher(test.testScheduler))
        val controller = BrightnessPipelineController(
            lightSensor = sensor,
            brightness = brightness,
            brightnessObserver = observer,
            settingsProvider = { if (settingsReadMs > 0) delay(settingsReadMs); settings },
            scope = scope,
            clock = { nowMs + test.testScheduler.currentTime },
            dimming = dimming,
            handoffSink = { flashes += it },
            displayReader = display,
            calibrationSink = calibrations,
        )
        val state: PipelineState get() = controller.state.value

        suspend fun reading(lux: Double) {
            sensor.flow.emit(LightSample(lux.toFloat(), 3, 0L, 0))
            test.advanceUntilIdle()
            nowMs += 60_000L
        }

        /** Screen off and on, so the next reading seeds smoothing exactly (hibernate clears it). */
        suspend fun seeded(lux: Double) {
            controller.onScreenOff(); test.advanceUntilIdle()
            controller.onScreenOn(); test.advanceUntilIdle()
            reading(lux)
        }

        suspend fun handedOffAt(lux: Double = 30_000.0) {
            controller.start()
            reading(lux)
            assertTrue(state.handedOff, "precondition: handed off at $lux lx")
        }

        fun close() {
            assertEquals(0, brightness.writesWhileAutomatic, "nothing may be written while Android owns the mode")
            scope.cancel()
        }
    }

    @Test
    fun calibration_sweepsThirteenLevels_restores_andReappliesTheCurve_DD077() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.reading(500.0)
        val applied = rig.brightness.current
        val before = rig.brightness.writes.size

        rig.controller.calibrateDisplay()
        advanceUntilIdle()

        val record = assertNotNull(rig.calibrations.finished.single())
        assertEquals(1, rig.calibrations.started)
        assertEquals("mScreenBrightness", record.field)
        assertEquals(DisplayCalibration.LEVELS.map { Point(it, it * 16.0) }, record.calibration.points)
        val writes = rig.brightness.writes.drop(before)
        assertEquals(DisplayCalibration.LEVELS + applied, writes.take(14), "13 levels, then the level found")
        assertEquals(applied, rig.brightness.current, "Set Initial Brightness runs again afterwards")
        assertFalse(rig.state.paused)
        rig.close()
    }

    @Test
    fun calibration_whileHandedOff_handsBackSilently_andTheCurveHandsOffAgain_DD077() = runTest {
        val rig = Rig(this)
        rig.handedOffAt(30_000.0)
        assertEquals(listOf(true), rig.flashes)

        rig.controller.calibrateDisplay()
        advanceUntilIdle()

        assertNotNull(rig.calibrations.finished.single())
        assertEquals(1, rig.brightness.reclaims)
        assertTrue(rig.state.handedOff, "30 000 lx is still above the threshold")
        assertEquals(listOf(true, true), rig.flashes, "the hand-back is silent, as Pause's")
        rig.close()
    }

    @Test
    fun calibration_refusesWhenPausedUnderAdaptiveBrightness_andWritesNothing_DD077() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.reading(500.0)
        rig.controller.pause()
        advanceUntilIdle()
        rig.brightness.automatic = true
        val writes = rig.brightness.writes.size
        val forced = rig.brightness.manualForced

        rig.controller.calibrateDisplay()
        advanceUntilIdle()

        assertEquals(listOf<DisplayCalibrationRecord?>(null), rig.calibrations.finished)
        assertEquals(writes, rig.brightness.writes.size)
        assertEquals(forced, rig.brightness.manualForced, "never forces MANUAL")
        assertTrue(rig.brightness.automatic)
        rig.brightness.automatic = false
        rig.close()
    }

    @Test
    fun calibration_fails_withoutAReadableDisplay_orOnAFlatPanel_andStillRestores_DD077() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.reading(500.0)
        val applied = rig.brightness.current

        rig.display.answers = false
        rig.controller.calibrateDisplay()
        advanceUntilIdle()
        assertEquals(listOf<DisplayCalibrationRecord?>(null), rig.calibrations.finished)
        assertEquals(applied, rig.brightness.current)

        rig.display.answers = true
        rig.display.raw = { minOf(it, 100) * 16.0 }
        rig.controller.calibrateDisplay()
        advanceUntilIdle()
        assertEquals(listOf<DisplayCalibrationRecord?>(null, null), rig.calibrations.finished, "raw must keep rising")
        assertEquals(applied, rig.brightness.current)
        rig.close()
    }

    @Test
    fun entryAndExit_atTheBandEdges_writeAndDimNothingInBetween() = runTest {
        val rig = Rig(this)
        rig.controller.start()

        rig.reading(25_132.0)
        assertEquals(25_132.0, rig.state.smoothedLux)
        assertFalse(rig.state.handedOff, "entry is strict: 25 132 is not above 25 132")
        assertEquals(0, rig.brightness.handOffs)

        val writes = rig.brightness.writes.size
        val dimmed = rig.dimming.applied.size
        rig.seeded(25_133.0)
        assertTrue(rig.state.handedOff)
        assertTrue(rig.brightness.automatic)
        assertNull(rig.state.lastAppliedBrightness, "Android drives the screen, so the baseline is UNKNOWN")
        assertEquals(0.0, rig.state.dimmingCurrent)

        rig.seeded(20_106.0)
        assertTrue(rig.state.handedOff, "20 106 is not below the exit point 20 105.6")
        assertEquals(writes, rig.brightness.writes.size, "no write while handed off")
        assertEquals(dimmed, rig.dimming.applied.size, "no dimming while handed off")
        assertEquals(20_106.0, rig.state.smoothedLux, "lux is still measured and published")

        rig.seeded(20_105.0)
        assertFalse(rig.state.handedOff)
        assertEquals(1, rig.brightness.reclaims)
        assertTrue(rig.brightness.writes.size > writes, "the hand-back writes in the same cycle")
        assertEquals(dimmed + 1, rig.dimming.applied.size)
        rig.close()
    }

    @Test
    fun aLandedEntryAndExitFlash_aRefusedOneAndStopDoNot_DD076() = runTest {
        val rig = Rig(this)
        rig.brightness.handOffLands = false
        rig.controller.start()
        rig.reading(30_000.0)
        assertEquals(emptyList(), rig.flashes, "a refused entry hands nothing to Android")

        rig.brightness.handOffLands = true
        rig.seeded(30_000.0)
        assertEquals(listOf(true), rig.flashes)

        rig.brightness.reclaimLands = false
        rig.seeded(1_000.0)
        assertEquals(listOf(true), rig.flashes, "a refused reclaim leaves Android in charge")

        rig.brightness.reclaimLands = true
        rig.seeded(1_000.0)
        assertEquals(listOf(true, false), rig.flashes)

        rig.seeded(30_000.0)
        rig.controller.stop()
        assertEquals(listOf(true, false, true), rig.flashes, "stop() restores the user's mode; Tideo is not back in control")
        rig.close()
    }

    @Test
    fun entry_setsTheFlagAndDropsDimming_beforeTheModeFlip() = runTest {
        val rig = Rig(this)
        var flagAtFlip = false
        rig.brightness.onHandOff = { flagAtFlip = rig.state.handedOff }
        rig.handedOffAt()

        assertTrue(flagAtFlip, "both override gates must see the flag before the mode flip's echo")
        val flip = rig.log.indexOf("handOff")
        assertTrue(rig.log.subList(0, flip).contains("disengage"), "dimming is dropped before Auto goes on: ${rig.log}")
        rig.close()
    }

    @Test
    fun everyHandedOffCycle_retriesTheDimmingRelease() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val releases = rig.log.count { it == "disengage" }

        rig.reading(40_000.0)
        assertTrue(rig.state.handedOff)
        assertTrue(rig.log.count { it == "disengage" } > releases, "a release refused at entry is retried while handed off")
        rig.close()
    }

    @Test
    fun failedEntry_writesNormally_andTheNextDecisionRetries() = runTest {
        val rig = Rig(this)
        rig.brightness.handOffLands = false
        rig.controller.start()

        rig.reading(30_000.0)
        assertFalse(rig.state.handedOff, "a refused entry rolls the flag back")
        assertEquals(1, rig.brightness.handOffs)
        assertTrue(rig.brightness.writes.isNotEmpty(), "and the cycle writes as before")
        assertFalse(rig.state.paused)

        rig.brightness.handOffLands = true
        rig.seeded(30_000.0)
        assertEquals(2, rig.brightness.handOffs)
        assertTrue(rig.state.handedOff)
        rig.close()
    }

    @Test
    fun failedReclaim_staysHandedOffWithoutWrites_andTheNextDecisionRetries() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val writes = rig.brightness.writes.size
        rig.brightness.reclaimLands = false

        rig.seeded(1_000.0)
        assertTrue(rig.state.handedOff)
        assertEquals(1, rig.brightness.reclaims)
        assertEquals(writes, rig.brightness.writes.size, "a write against AUTOMATIC would be overwritten or read as an override")

        rig.brightness.reclaimLands = true
        rig.seeded(1_000.0)
        assertFalse(rig.state.handedOff)
        assertEquals(2, rig.brightness.reclaims)
        assertTrue(rig.brightness.writes.size > writes)
        rig.close()
    }

    @Test
    fun observerChanges_whileHandedOff_areNotOverrides() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val forced = rig.brightness.manualForced

        rig.brightness.current = 200
        rig.observer.flow.emit(200)
        advanceUntilIdle()

        assertFalse(rig.state.paused)
        assertNull(rig.state.overrideDiagnostic)
        assertEquals(forced, rig.brightness.manualForced, "no override reclaim may undo the hand-off")
        assertTrue(rig.brightness.automatic)
        rig.close()
    }

    @Test
    fun anOverrideQueuedBeforeTheEntry_isNotCommittedAfterIt() = runTest {
        val rig = Rig(this, handoffOn.copy(handoffEnabled = false))
        rig.controller.start()
        rig.reading(30_000.0)

        // The first override's settle delay queues the Apply, a reading and a second change with the observer
        // gate open; the reading's slow settings read carries that change past the entry's settle window.
        rig.observer.flow.emit(rig.brightness.current)
        rig.settings = handoffOn
        rig.settingsReadMs = 10_000L
        rig.controller.reapply()
        rig.sensor.flow.emit(LightSample(40_000f, 3, 0L, 0))
        rig.observer.flow.emit(200)
        advanceUntilIdle()

        assertTrue(rig.state.handedOff)
        assertEquals(1, rig.brightness.handOffs)
        assertTrue(rig.brightness.automatic, "the commit path's reclaim would have undone the hand-off")
        assertFalse(rig.state.paused)
        rig.close()
    }

    @Test
    fun restartWithTheMarker_andAutomatic_startsHandedOff_andKeepsIt() = runTest {
        val rig = Rig(this)
        rig.brightness.marker = true
        rig.brightness.automatic = true

        rig.controller.start()
        assertTrue(rig.state.handedOff)
        assertTrue(rig.log.indexOf("reconcile") < rig.log.indexOf("register"), "the marker is read before sensing: ${rig.log}")

        rig.reading(30_000.0)
        assertTrue(rig.state.handedOff)
        assertEquals(0, rig.brightness.handOffs, "a still-valid hand-off is kept without a mode flip")
        assertTrue(rig.brightness.writes.isEmpty())
        rig.close()
    }

    @Test
    fun restartWithTheMarker_andManual_startsNormally() = runTest {
        val rig = Rig(this)
        rig.brightness.marker = true

        rig.controller.start()
        assertFalse(rig.state.handedOff)
        assertFalse(rig.brightness.marker, "MANUAL clears a stale marker")

        rig.reading(1_000.0)
        assertTrue(rig.brightness.writes.isNotEmpty())
        assertEquals(0, rig.brightness.reclaims)
        rig.close()
    }

    @Test
    fun applyThatSwitchesTheFeatureOff_whileHibernated_reclaimsWithNoWrite() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        rig.controller.onScreenOff(); advanceUntilIdle()
        assertTrue(rig.state.handedOff, "the flag survives hibernate")
        val writes = rig.brightness.writes.size

        rig.settings = handoffOn.copy(handoffEnabled = false)
        rig.controller.reapply(); advanceUntilIdle()

        assertFalse(rig.state.handedOff)
        assertEquals(1, rig.brightness.reclaims)
        assertFalse(rig.brightness.automatic)
        assertEquals(writes, rig.brightness.writes.size, "no lux is held, so the next wake writes")
        rig.close()
    }

    @Test
    fun applyThatKeepsTheFeatureOn_whileHibernated_keepsTheHandoff() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        rig.controller.onScreenOff(); advanceUntilIdle()

        rig.controller.reapply(); advanceUntilIdle()
        assertTrue(rig.state.handedOff, "with no lux held, only an exit that needs none is decided")
        assertEquals(0, rig.brightness.reclaims)
        rig.close()
    }

    @Test
    fun contextSwapToAProfileWithTheFeatureOff_exitsAtOnce_underAHeldDeadBand() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        rig.settings = handoffOn.copy(handoffEnabled = false)

        rig.reading(30_010.0)
        assertEquals(30_010.0, rig.state.lastRawLux)
        assertEquals(30_000.0, rig.state.smoothedLux, "precondition: the dead band refused the reading")
        assertTrue(rig.state.handedOff, "a dead-band refusal decides nothing")

        val writes = rig.brightness.writes.size
        rig.controller.onContextChanged(); advanceUntilIdle()
        assertFalse(rig.state.handedOff)
        assertEquals(1, rig.brightness.reclaims)
        assertTrue(rig.brightness.writes.size > writes, "the hand-back writes the initial brightness")
        rig.close()
    }

    @Test
    fun applyThatSwitchesTheFeatureOff_exitsAtOnce() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val writes = rig.brightness.writes.size

        rig.settings = handoffOn.copy(handoffEnabled = false)
        rig.controller.reapply(); advanceUntilIdle()

        assertFalse(rig.state.handedOff)
        assertFalse(rig.brightness.automatic)
        assertTrue(rig.brightness.writes.size > writes)
        rig.close()
    }

    @Test
    fun pause_handsBackFirst() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val writes = rig.brightness.writes.size

        rig.controller.pause(); advanceUntilIdle()

        assertTrue(rig.state.paused)
        assertFalse(rig.state.pausedByOverride)
        assertFalse(rig.state.handedOff)
        assertEquals(1, rig.brightness.reclaims)
        assertEquals(writes, rig.brightness.writes.size, "Pause writes nothing")
        assertEquals(listOf(true), rig.flashes, "Pause hands back silently: Tideo pauses, it does not take control (DD-076)")
        rig.close()
    }

    @Test
    fun pauseWithARefusedReclaim_pauses_keepsTheFlag_andResumeReDecides() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        rig.brightness.reclaimLands = false

        rig.controller.pause(); advanceUntilIdle()
        assertTrue(rig.state.paused)
        assertTrue(rig.state.handedOff, "Android still owns the mode, and the state says so")

        rig.brightness.reclaimLands = true
        rig.settings = handoffOn.copy(handoffEnabled = false)
        rig.controller.resume(); advanceUntilIdle()
        assertFalse(rig.state.handedOff)
        assertFalse(rig.brightness.automatic)
        rig.close()
    }

    @Test
    fun stopFromAnotherThread_waitsForAnEntryInFlight_soItsRestoreComesLast_DD074() = runTest {
        val rig = Rig(this)
        var stopper: Thread? = null
        val stopReturned = AtomicBoolean(false)
        rig.brightness.onHandOff = {
            val t = thread { rig.controller.stop(); stopReturned.set(true) }
            stopper = t
            val deadline = System.nanoTime() + 5_000_000_000L
            while (t.state != Thread.State.BLOCKED && t.state != Thread.State.TERMINATED && System.nanoTime() < deadline) Thread.yield()
            assertEquals(Thread.State.BLOCKED, t.state, "stop() must wait on the entry in flight, not run past it")
            assertFalse(stopReturned.get())
        }
        rig.controller.start()
        rig.reading(30_000.0)
        stopper!!.join(5_000)

        assertTrue(stopReturned.get())
        assertEquals(1, rig.brightness.restores)
        assertFalse(rig.brightness.automatic, "the restore ran after the AUTOMATIC write, not before it")
        rig.close()
    }

    @Test
    fun resume_keepsAValidHandoff_withoutAModeFlip() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()
        val writes = rig.brightness.writes.size

        rig.controller.resume(); advanceUntilIdle()

        assertTrue(rig.state.handedOff)
        assertEquals(1, rig.brightness.handOffs)
        assertEquals(0, rig.brightness.reclaims)
        assertEquals(writes, rig.brightness.writes.size)
        rig.close()
    }

    @Test
    fun stop_restoresTheRealMode_andClearsTheFlag() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()

        rig.controller.stop()

        assertFalse(rig.state.handedOff)
        assertEquals(1, rig.brightness.restores, "restoreMode() returns the pre-service mode (DD-073)")
        assertFalse(rig.brightness.automatic)
        rig.close()
    }

    @Test
    fun screenOffAndOn_keepTheFlag_andTheFirstReadingReDecides() = runTest {
        val rig = Rig(this)
        rig.handedOffAt()

        rig.controller.onScreenOff(); advanceUntilIdle()
        assertTrue(rig.state.handedOff)
        rig.controller.onScreenOn(); advanceUntilIdle()
        assertTrue(rig.state.handedOff, "wake holds no lux, so it decides nothing")
        assertEquals(0, rig.brightness.reclaims)

        rig.reading(30_000.0)
        assertTrue(rig.state.handedOff)
        assertEquals(1, rig.brightness.handOffs, "still above the exit: kept without a mode flip")

        rig.seeded(1_000.0)
        assertFalse(rig.state.handedOff)
        rig.close()
    }
}
