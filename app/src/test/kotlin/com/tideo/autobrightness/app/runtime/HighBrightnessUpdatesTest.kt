package com.tideo.autobrightness.app.runtime

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tideo.autobrightness.app.settings.DisplayCalibrationStore
import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tasker "High Brightness Updates" → `_ReadBrightness` (DD-077). */
@OptIn(ExperimentalCoroutinesApi::class)
class HighBrightnessUpdatesTest {

    private val linear = DisplayCalibrationRecord(
        DisplayCalibration.of(DisplayCalibration.LEVELS.map { Point(it, it * 16.0) })!!,
        "mScreenBrightness",
    )

    private class Panel : DisplayBrightnessReader {
        var raw: Double? = 1_600.0
        val fields = mutableListOf<String?>()
        override suspend fun read(field: String?): DisplayBrightnessSample? {
            fields += field
            return raw?.let { DisplayBrightnessSample("mScreenBrightness", it) }
        }
    }

    private class Rig(record: DisplayCalibrationRecord?) {
        val panel = Panel()
        val flashes = mutableListOf<Boolean>()
        var setting: Int? = 77
        val updates = HighBrightnessUpdates(panel, { record }, { setting }, object : HandoffSink {
            override fun onHandoff(toAndroid: Boolean) = Unit
            override fun onHighBrightness(active: Boolean) { flashes += active }
        })
    }

    @Test
    fun calibratedReadoutGoesAbove255_andFlashesOnEachCrossing() = runTest {
        val rig = Rig(linear)
        rig.updates.read()
        assertEquals(100, rig.updates.readout.value)
        assertEquals(listOf<String?>("mScreenBrightness"), rig.panel.fields, "reads the field the calibration locked")

        rig.panel.raw = 4_656.0
        rig.updates.read()
        assertEquals(291, rig.updates.readout.value)
        rig.panel.raw = 4_096.0
        rig.updates.read()
        assertEquals(256, rig.updates.readout.value)
        assertEquals(listOf(true), rig.flashes, "activated once")

        rig.panel.raw = 4_080.0
        rig.updates.read()
        assertEquals(255, rig.updates.readout.value)
        assertEquals(listOf(true, false), rig.flashes)
    }

    @Test
    fun aStoredBoundaryFlagsFromRaw_whileTheRoundedReadoutStaysAt255_DE031() = runTest {
        val pixel = DisplayCalibrationRecord(
            DisplayCalibration.of(
                DisplayCalibration.LEVELS.filter { it <= 150 }.map { Point(it, (it - 1) / 254.0) } + Point(255, 0.63),
            )!!,
            "mScreenBrightness",
            0.63,
        )
        val rig = Rig(pixel)
        rig.panel.raw = 0.62
        rig.updates.read()
        assertEquals(231, rig.updates.readout.value)
        assertEquals(emptyList(), rig.flashes)

        rig.panel.raw = 0.6301
        rig.updates.read()
        assertEquals(255, rig.updates.readout.value, "AAB's slope rounds just past the boundary to 255")
        assertEquals(listOf(true), rig.flashes, "the raw crossing turns the flag on")
        rig.panel.raw = 0.6302
        rig.updates.read()
        assertEquals(255, rig.updates.readout.value)
        assertEquals(listOf(true), rig.flashes, "reported once")

        rig.panel.raw = 0.63
        rig.updates.read()
        assertEquals(255, rig.updates.readout.value)
        assertEquals(listOf(true, false), rig.flashes, "back on the boundary turns it off, readout unchanged")

        val atTop = Rig(pixel)
        atTop.panel.raw = 0.63
        atTop.updates.read()
        atTop.panel.raw = 0.6301
        atTop.updates.read()
        assertEquals(255, atTop.updates.readout.value)
        assertEquals(listOf(true), atTop.flashes, "crossing while already showing 255")
    }

    @Test
    fun withoutABoundary_theFlagStillFollowsTheRoundedReadout() = runTest {
        val rig = Rig(linear)
        rig.panel.raw = 4_080.1
        rig.updates.read()
        assertEquals(255, rig.updates.readout.value)
        assertEquals(emptyList(), rig.flashes, "past the 255 point's raw, but rounds to 255: AAB's level > 255 keeps it off")
    }

    @Test
    fun aFailedReadKeepsTheLastValue() = runTest {
        val rig = Rig(linear)
        rig.updates.read()
        rig.panel.raw = null
        rig.updates.read()
        assertEquals(100, rig.updates.readout.value)
    }

    @Test
    fun uncalibrated_showsTheBrightnessSetting_andNeverReadsDumpsys() = runTest {
        val rig = Rig(null)
        rig.updates.read()
        assertEquals(77, rig.updates.readout.value)
        assertEquals(emptyList(), rig.panel.fields)
        assertEquals(emptyList(), rig.flashes)
    }

    @Test
    fun pollsEveryTwoSeconds_onlyWhileHandedOffOnAndAwake_andAHandBackClearsTheFlag() = runTest {
        val rig = Rig(linear)
        val state = MutableStateFlow(PipelineState(serviceOn = true))
        val job = rig.updates.start(backgroundScope, state)
        runCurrent()
        assertEquals(emptyList(), rig.panel.fields, "not handed off")

        rig.panel.raw = 4_656.0
        state.value = state.value.copy(handedOff = true)
        runCurrent()
        assertEquals(291, rig.updates.readout.value)
        advanceTimeBy(HighBrightnessUpdates.PERIOD_MS + 1)
        assertEquals(2, rig.panel.fields.size)

        state.value = state.value.copy(hibernated = true)
        runCurrent()
        assertNull(rig.updates.readout.value)
        advanceTimeBy(10 * HighBrightnessUpdates.PERIOD_MS)
        assertEquals(2, rig.panel.fields.size, "screen off stops the reads")

        state.value = state.value.copy(hibernated = false)
        runCurrent()
        assertEquals(listOf(true), rig.flashes, "a screen-off keeps %AAB_HBMFlag, so waking above 255 is silent")

        rig.panel.raw = 1_600.0
        advanceTimeBy(HighBrightnessUpdates.PERIOD_MS + 1)
        assertEquals(100, rig.updates.readout.value, "at or below 255 it keeps polling while handed off")
        assertEquals(listOf(true, false), rig.flashes)
        val reads = rig.panel.fields.size
        state.value = state.value.copy(handedOff = false)
        runCurrent()
        assertNull(rig.updates.readout.value)
        advanceTimeBy(10 * HighBrightnessUpdates.PERIOD_MS)
        assertEquals(reads, rig.panel.fields.size, "the hand-back ends the loop")

        rig.panel.raw = 4_656.0
        state.value = state.value.copy(handedOff = true)
        runCurrent()
        assertEquals(listOf(true, false, true), rig.flashes, "a new hand-off starts with the flag cleared")
        job.cancel()
    }

    @Test
    fun theStoredBoundaryTravelsWithItsPoints_andEveryOtherWriteRemovesIt_DE030() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("display_prefs_test", ".preferences_pb").apply { delete() }
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = DisplayCalibrationStore(prefs, "build-1")
        val ceiling = DisplayCalibrationRecord(
            DisplayCalibration.of(listOf(Point(1, 0.0), Point(150, 0.5866142), Point(255, 0.63)))!!,
            "mScreenBrightness",
            0.63,
        )
        try {
            runBlocking {
                store.write(DisplayCalibrationOutcome.Calibrated(ceiling))
                val stored = assertNotNull(store.current())
                assertEquals(ceiling.calibration.encode(), stored.calibration.encode())
                assertEquals(0.63, stored.hbmBoundary)

                store.write(DisplayCalibrationOutcome.Failed(CalibrationFailure.CEILING_UNCONFIRMED))
                assertEquals(DisplayCalibrationStore.Status.Failed(CalibrationFailure.CEILING_UNCONFIRMED), store.status.first())
                assertEquals("error", prefs.data.first()[stringPreferencesKey("display_calibration")], "AAB's sentinel stays")
                assertNull(prefs.data.first()[doublePreferencesKey("display_calibration_hbm")], "a failure removes the key")

                store.write(DisplayCalibrationOutcome.Calibrated(ceiling))
                store.write(DisplayCalibrationOutcome.Calibrated(linear))
                assertNull(assertNotNull(store.current()).hbmBoundary, "a rising panel's success removes it")
            }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun aFailureKeepsItsCause_aSuccessDropsIt_andALegacyErrorReadsNeutral_DE032() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("display_prefs_test", ".preferences_pb").apply { delete() }
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = DisplayCalibrationStore(prefs, "build-1")
        val cause = stringPreferencesKey("display_calibration_error")
        try {
            runBlocking {
                for (failure in CalibrationFailure.entries) {
                    store.write(DisplayCalibrationOutcome.Failed(failure))
                    assertEquals(DisplayCalibrationStore.Status.Failed(failure), store.status.first())
                }
                store.write(DisplayCalibrationOutcome.Calibrated(linear))
                assertNull(prefs.data.first()[cause], "a success removes the cause")
                assertNotNull(store.current())

                prefs.edit { it[stringPreferencesKey("display_calibration")] = "error" }
                assertEquals(DisplayCalibrationStore.Status.Failed(CalibrationFailure.NO_READING), store.status.first(), "an earlier build's error")
                prefs.edit { it[cause] = "SOMETHING_NEWER" }
                assertEquals(DisplayCalibrationStore.Status.Failed(CalibrationFailure.NO_READING), store.status.first(), "an unknown cause")
            }
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
