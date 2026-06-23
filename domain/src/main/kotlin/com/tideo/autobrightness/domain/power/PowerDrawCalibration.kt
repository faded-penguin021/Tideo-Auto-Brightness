package com.tideo.autobrightness.domain.power

import kotlin.math.abs
import kotlin.math.pow

/**
 * One measured calibration point — the Tasker task524 `_CalibratePowerDraw` JSON `data` row
 * (`{brightness, current_ma, power_w}`), after net-of-idle post-processing.
 */
data class PowerDrawSample(
    val brightness: Int,
    val currentMa: Double,
    val powerW: Double,
)

/**
 * Pure math for the Tasker task524 `_CalibratePowerDraw` routine (Java block, XML L14247–14702): the
 * geometric brightness-step distribution, the battery-current normalization, and the net-of-idle
 * post-processing. The device orchestration (driving the screen, the latch-breaker timing, the battery
 * reads) lives in `app/runtime/PowerDrawCalibrator` + `platform/context/PowerMeter`; this is the
 * testable algorithmic core, ported verbatim from the extracted Java.
 *
 * Provenance: `docs/rebuild/extraction/_source/java/task524_1_calibratepowerdraw.java.txt`.
 */
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

    /**
     * task524 step 1 "GENERATE GEOMETRIC STEPS": `val = (int)(255·(i/16)^0.45)` for i in 1..16, keep a
     * step only when it advances ≥ [MIN_STEP_DIFF] from the last (or it is 255), and always append a
     * final 255. Ported verbatim from the extracted Java (geometric distribution, exponent 0.45).
     */
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

    /**
     * task524: `BATTERY_PROPERTY_CURRENT_NOW` is reported in µA on most devices but mA on some. Take the
     * magnitude (sign = charge/discharge direction) and, if `|raw| > 50000`, treat it as µA → mA (÷1000).
     */
    fun normalizeCurrentMa(rawProperty: Long): Double {
        val a = abs(rawProperty)
        return if (a > 50_000) a / 1000.0 else a.toDouble()
    }

    /**
     * task524 step 5 "Post-Process": if sample[0] mA > sample[1] mA the baseline is bogus → zero it,
     * then subtract the per-run minimum (mA and W) from every point so each value is **net-of-idle**
     * (`max(0, raw − min)`). [rawW] is the per-point `mA/1000 · V`; using `min(rawW)` matches the Java's
     * `minMa/1000 · V` idle-power floor (same voltage frame, see the extraction note).
     */
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
