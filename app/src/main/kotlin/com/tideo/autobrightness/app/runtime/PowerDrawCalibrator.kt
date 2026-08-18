package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.power.PowerDrawCalibration
import com.tideo.autobrightness.domain.power.PowerDrawSample
import com.tideo.autobrightness.platform.context.PowerMeter
import kotlinx.coroutines.delay

/** Live progress for the calibration UI (step n of total + a status line). */
data class PowerDrawProgress(val step: Int, val total: Int, val message: String)

/** Orchestrate task524 _CalibratePowerDraw: safety checks, ramp, baseline capture, latch-breaker sweep, post-process. Injected for testability. */
class PowerDrawCalibrator(
    private val meter: PowerMeter,
    private val setScreenBrightness: suspend (Int) -> Unit,
    private val onProgress: (PowerDrawProgress) -> Unit = {},
    private val delayMs: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data class Success(val samples: List<PowerDrawSample>) : Result
        data object SensorUnavailable : Result
        data object Charging : Result
        data object Cancelled : Result
    }

    suspend fun calibrate(startBrightness: Int = 128, isCancelled: () -> Boolean = { false }): Result {
        if (!meter.hasCurrentSensor()) return Result.SensorUnavailable
        if (meter.isCharging()) return Result.Charging

        val steps = PowerDrawCalibration.generateSteps()
        val total = steps.size
        val xVals = ArrayList<Int>()
        val rawMa = ArrayList<Double>()
        val rawW = ArrayList<Double>()

        onProgress(PowerDrawProgress(0, total, "Ramping down to 0…"))
        var b = startBrightness
        while (b >= 0) {
            if (isCancelled()) return Result.Cancelled
            setScreenBrightness(b)
            delayMs(10)
            b -= 2
        }

        onProgress(PowerDrawProgress(0, total, "Stabilizing baseline (0/255)…"))
        delayMs(PowerDrawCalibration.INITIAL_SETTLE_MS)
        var lastMa = 0.0
        var checks = 0
        while (checks < PowerDrawCalibration.BASELINE_MAX_CHECKS) {
            if (isCancelled()) return Result.Cancelled
            val ma = PowerDrawCalibration.normalizeCurrentMa(meter.readCurrentRaw())
            if (ma < PowerDrawCalibration.BASELINE_MAX_MA) {
                lastMa = ma
                break
            }
            checks++
            delayMs(1000)
        }
        recordSample(xVals, rawMa, rawW, brightness = 0, ma = lastMa)

        for ((i, target) in steps.withIndex()) {
            if (isCancelled()) return Result.Cancelled
            onProgress(PowerDrawProgress(i + 1, total, "Target $target/255 — waiting for change…"))
            setScreenBrightness(target)
            val refMa = lastMa
            var nudged = false
            val waitStart = clock()
            while (clock() - waitStart < PowerDrawCalibration.MAX_WAIT_MS) {
                if (isCancelled()) return Result.Cancelled
                if (!nudged && clock() - waitStart > PowerDrawCalibration.NUDGE_THRESHOLD_MS) {
                    nudged = true
                    val nudge = if (target + 1 <= 255) target + 1 else target - 1
                    setScreenBrightness(nudge)
                    delayMs(200)
                    setScreenBrightness(target)
                }
                val currMa = PowerDrawCalibration.normalizeCurrentMa(meter.readCurrentRaw())
                if (currMa != refMa) break
                delayMs(PowerDrawCalibration.POLL_INTERVAL_MS)
            }
            delayMs(PowerDrawCalibration.POST_LATCH_DELAY_MS)
            val finalMa = PowerDrawCalibration.normalizeCurrentMa(meter.readCurrentRaw())
            recordSample(xVals, rawMa, rawW, brightness = target, ma = finalMa)
            lastMa = finalMa
        }

        return Result.Success(PowerDrawCalibration.postProcess(xVals, rawMa, rawW))
    }

    private fun recordSample(
        xVals: MutableList<Int>,
        rawMa: MutableList<Double>,
        rawW: MutableList<Double>,
        brightness: Int,
        ma: Double,
    ) {
        val v = meter.readVoltageVolts()
        xVals.add(brightness)
        rawMa.add(ma)
        rawW.add(ma / 1000.0 * v)
    }
}
