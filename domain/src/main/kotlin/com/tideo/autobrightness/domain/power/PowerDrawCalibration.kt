package com.tideo.autobrightness.domain.power

import kotlin.math.abs
import kotlin.math.pow

/** One measured calibration point from Tasker task524 `_CalibratePowerDraw` JSON (brightness, current_ma, power_w), after post-processing. */
data class PowerDrawSample(
    val brightness: Int,
    val currentMa: Double,
    val powerW: Double,
)

/** Pure math for Tasker task524 `_CalibratePowerDraw` (geometric step distribution, current normalization, post-processing). Device orchestration in PowerDrawCalibrator + PowerMeter. Ported verbatim from extracted Java. */
object PowerDrawCalibration {
    // task524 CONFIGURATION block (verbatim).
    const val TARGET_POINTS = 16
    const val DISTRIBUTION_EXPONENT = 0.45
    const val MIN_STEP_DIFF = 5
    const val NUDGE_THRESHOLD_MS = 3_500L
    const val MAX_WAIT_MS = 12_000L
    const val POST_LATCH_DELAY_MS = 2_000L
    const val POLL_INTERVAL_MS = 200L
    const val INITIAL_SETTLE_MS = 6_000L
    // Baseline sanity capture (task524 step 3): poll up to 20× (1 s apart) for current < 150 mA.
    const val BASELINE_MAX_MA = 150.0
    const val BASELINE_MAX_CHECKS = 20

    /** task524 step 1: GENERATE GEOMETRIC STEPS using exponent 0.45. Keep step if ≥MIN_STEP_DIFF advance or is 255; always append final 255. */
    fun generateSteps(): List<Int> {
        val steps = ArrayList<Int>()
        var last = 0
        for (i in 1..TARGET_POINTS) {
            val ratio = i.toDouble() / TARGET_POINTS
            val v = (255.0 * ratio.pow(DISTRIBUTION_EXPONENT)).toInt().coerceAtMost(255)
            if (v - last >= MIN_STEP_DIFF) {
                steps.add(v); last = v
            } else if (v == 255 && last != 255) {
                steps.add(255); last = 255
            }
        }
        if (steps.isNotEmpty() && steps.last() != 255) steps.add(255)
        return steps
    }

    /** task524: BATTERY_PROPERTY_CURRENT_NOW reported in µA on most devices, mA on some. If |raw| > 50000, treat as µA → mA. */
    fun normalizeCurrentMa(rawProperty: Long): Double {
        val a = abs(rawProperty)
        return if (a > 50_000) a / 1000.0 else a.toDouble()
    }

    /** task524 step 5: POST-PROCESS. If sample[0] mA > sample[1], zero baseline. Subtract per-run min (mA/W) from every point (net-of-idle). */
    fun postProcess(
        brightness: List<Int>,
        rawMa: List<Double>,
        rawW: List<Double>,
    ): List<PowerDrawSample> {
        if (brightness.isEmpty()) return emptyList()
        val ma = rawMa.toMutableList()
        val w = rawW.toMutableList()
        if (ma.size >= 2 && ma[0] > ma[1]) {
            ma[0] = 0.0
            w[0] = 0.0
        }
        val minMa = ma.min()
        val minW = w.min()
        return brightness.indices.map { i ->
            PowerDrawSample(
                brightness = brightness[i],
                currentMa = (ma[i] - minMa).coerceAtLeast(0.0),
                powerW = (w[i] - minW).coerceAtLeast(0.0),
            )
        }
    }
}
