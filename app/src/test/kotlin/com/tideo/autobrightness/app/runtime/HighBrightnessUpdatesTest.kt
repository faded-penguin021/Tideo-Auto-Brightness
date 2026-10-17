package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
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
}
