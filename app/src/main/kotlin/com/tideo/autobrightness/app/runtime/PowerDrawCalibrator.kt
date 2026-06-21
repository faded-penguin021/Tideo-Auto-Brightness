package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.power.PowerDrawCalibration
import com.tideo.autobrightness.domain.power.PowerDrawSample
import com.tideo.autobrightness.platform.context.PowerMeter
import kotlinx.coroutines.delay

/** Live progress for the calibration UI (step n of total + a status line). */
data class PowerDrawProgress(val step: Int, val total: Int, val message: String)

/**
 * Runtime orchestration of the Tasker task524 `_CalibratePowerDraw` measurement. The algorithm is the
 * domain [PowerDrawCalibration]; the battery reads are the platform [PowerMeter]. This is a faithful
 * port of the extracted Java worker thread:
 *
 *  1. safety checks (current sensor present, not charging);
 *  2. generate the geometric brightness steps;
 *  3. ramp the screen down to 0 (−2 / 10 ms);
 *  4. capture an idle **baseline** (settle 6 s, poll up to 20× for < 150 mA);
 *  5. **latch-breaker sweep** — at each step set the brightness and wait (≤ 12 s) for the latching
 *     battery-current reading to actually CHANGE from the previous step, nudging ±1 after 3.5 s if it
 *     stalls, then settle 2 s and sample mA + voltage;
 *  6. net-of-idle post-process → the brightness→power curve.
 *
 * Every side effect is injected ([meter], [setScreenBrightness], [delayMs], [clock]) so the whole
 * sequence is unit-testable with a scripted fake meter and instant delays. The host (Tools screen)
 * supplies [setScreenBrightness] by driving its Activity window's `screenBrightness` (the native
 * equivalent of the Tasker fullscreen-dialog brightness override — no WRITE_SETTINGS needed).
 */
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
        // task524 safety checks (abort → should_stop).
        if (!meter.hasCurrentSensor()) return Result.SensorUnavailable
        if (meter.isCharging()) return Result.Charging

        val steps = PowerDrawCalibration.generateSteps()
        val total = steps.size
        val xVals = ArrayList<Int>()
        val rawMa = ArrayList<Double>()
        val rawW = ArrayList<Double>()

        // 2. Pre-flight ramp down to 0 (−2 increments, 10 ms each).
        onProgress(PowerDrawProgress(0, total, "Ramping down to 0…"))
        var b = startBrightness
        while (b >= 0) {
            if (isCancelled()) return Result.Cancelled
            setScreenBrightness(b)
            delayMs(10)
            b -= 2
        }

        // 3. Baseline sanity capture: settle, then poll up to 20× (1 s) for current < 150 mA.
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
        // Record the x=0 baseline once (the accepted value, or the last seen / 0 on timeout).
        recordSample(xVals, rawMa, rawW, brightness = 0, ma = lastMa)

        // 4. Main latch-breaker loop.
        for ((i, target) in steps.withIndex()) {
            if (isCancelled()) return Result.Cancelled
            onProgress(PowerDrawProgress(i + 1, total, "Target $target/255 — waiting for change…"))
            setScreenBrightness(target)
            val refMa = lastMa
            var nudged = false
            val waitStart = clock()
            while (clock() - waitStart < PowerDrawCalibration.MAX_WAIT_MS) {
                if (isCancelled()) return Result.Cancelled
                // Nudge ±1 once if the (latching) sensor hasn't moved after the threshold.
                if (!nudged && clock() - waitStart > PowerDrawCalibration.NUDGE_THRESHOLD_MS) {
                    nudged = true
                    val nudge = if (target + 1 <= 255) target + 1 else target - 1
                    setScreenBrightness(nudge)
                    delayMs(200)
                    setScreenBrightness(target)
                }
                val currMa = PowerDrawCalibration.normalizeCurrentMa(meter.readCurrentRaw())
                if (currMa != refMa) break // latch broken
                delayMs(PowerDrawCalibration.POLL_INTERVAL_MS)
            }
            // Settle after the change (or timeout), then take the final sample.
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
