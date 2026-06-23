package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.power.PowerDrawCalibration
import com.tideo.autobrightness.platform.context.PowerMeter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * S14: the task524 `_CalibratePowerDraw` orchestration. A fake [PowerMeter] whose current tracks the
 * last-set brightness makes every latch break immediately, so the whole sweep runs deterministically
 * with instant delays — no device needed.
 */
class PowerDrawCalibratorTest {

    /** Idle (brightness 0) draws ~100 mA; +1 mA per brightness level. Reported in µA. */
    private class FakeMeter(
        private val hasSensor: Boolean = true,
        private val charging: Boolean = false,
        private val brightnessRef: () -> Int,
    ) : PowerMeter {
        override fun readCurrentRaw(): Long = 100_000L + brightnessRef() * 1000L
        override fun readVoltageVolts(): Double = 4.0
        override fun isCharging(): Boolean = charging
        override fun hasCurrentSensor(): Boolean = hasSensor
    }

    private fun calibrator(
        meter: PowerMeter,
        onSet: (Int) -> Unit = {},
    ) = PowerDrawCalibrator(
        meter = meter,
        setScreenBrightness = { onSet(it) },
        delayMs = { /* instant */ },
        clock = { 0L }, // the wait loop exits on the first changed reading, so the clock is irrelevant
    )

    @Test
    fun sensorUnavailable_isReported() = runTest {
        var b = 0
        val result = calibrator(FakeMeter(hasSensor = false) { b }) { b = it }.calibrate()
        assertIs<PowerDrawCalibrator.Result.SensorUnavailable>(result)
    }

    @Test
    fun charging_isReported() = runTest {
        var b = 0
        val result = calibrator(FakeMeter(charging = true) { b }) { b = it }.calibrate()
        assertIs<PowerDrawCalibrator.Result.Charging>(result)
    }

    @Test
    fun cancelled_isReported() = runTest {
        var b = 0
        val result = calibrator(FakeMeter { b }) { b = it }.calibrate(isCancelled = { true })
        assertIs<PowerDrawCalibrator.Result.Cancelled>(result)
    }

    @Test
    fun fullSweep_producesNetOfIdleCurve_andDrivesTheScreen() = runTest {
        var b = 0
        val driven = mutableListOf<Int>()
        val result = calibrator(FakeMeter { b }) { b = it; driven += it }.calibrate(startBrightness = 20)

        val success = assertIs<PowerDrawCalibrator.Result.Success>(result)
        val samples = success.samples
        assertEquals(PowerDrawCalibration.generateSteps().size + 1, samples.size, "baseline + one per step")
        assertEquals(0, samples.first().brightness)
        assertEquals(255, samples.last().brightness)
        // Net-of-idle: the baseline point is zeroed and current rises with brightness.
        assertEquals(0.0, samples.first().currentMa, 1e-9)
        assertTrue(samples.last().currentMa > samples.first().currentMa, "current rises with brightness")
        // The screen was actually ramped to 0 and driven to full at the last step.
        assertTrue(driven.contains(0), "ramped down to 0")
        assertTrue(driven.contains(255), "drove the final 255 step")
    }
}
