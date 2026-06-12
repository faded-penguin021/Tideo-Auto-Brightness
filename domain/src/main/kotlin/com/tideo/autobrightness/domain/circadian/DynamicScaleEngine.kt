package com.tideo.autobrightness.domain.circadian

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Inputs for the dynamic-scale tanh-ramp computation.
 *
 * Mirrors the variables read by task90 Java Block #2 (XML L41085).
 * [morningStart]/[morningEnd] and [eveningStart]/[eveningEnd] are in seconds-of-day (0–86400+).
 * [sunlightDurationMinutes] drives the polar branch threshold (> 1380 → full day).
 *
 * Tasker: task90 "Dynamic Scale V13 (Java) App Version", Block #2. XML L41085.
 */
data class DynamicScaleInput(
    /** Current time as seconds into the local day (0..86400); `now = System.currentTimeMillis()/1000 % 86400`. */
    val nowSecOfDay: Double,
    val morningStart: Double,
    val morningEnd: Double,
    val eveningStart: Double,
    val eveningEnd: Double,
    /** %AAB_Sunlightduration — minutes of sunlight today (for polar branch). */
    val sunlightDurationMinutes: Double,
    /** %AAB_PolarState == "true". */
    val isPolar: Boolean,
    /** %AAB_ScaleSteepness, default 4.0. */
    val steepness: Double = 4.0,
    /** %AAB_DimSpread (percent), default 0.0. */
    val dimSpreadPercent: Double = 0.0,
    /** %AAB_ScaleSpread (percent), default 0.0. */
    val scaleSpreadPercent: Double = 0.0,
)

/**
 * Outputs of the dynamic-scale computation.
 *
 * Tasker: task90 Block #2 writes %progress, %modifier, %AAB_DimDynamic, %AAB_ScaleDynamic.
 */
data class DynamicScaleResult(
    val progress: Double,
    val modifier: Double,
    /** BigDecimal(raw).setScale(3, HALF_UP): `2 − (1 + dimSpread/100 * modifier)`. */
    val dimDynamic: Double,
    /** BigDecimal(raw).setScale(3, HALF_UP): `1 + scaleSpread/100 * modifier`. */
    val scaleDynamic: Double,
)

/**
 * Pure-domain dynamic-scale engine.
 *
 * Absorbs and replaces [com.tideo.autobrightness.domain.brightness.BrightnessEngine.computeDynamicScale]
 * and [com.tideo.autobrightness.domain.brightness.BrightnessEngine.rampProgress] from the pre-S6 engine.
 *
 * Parity notes:
 * - Rounding: Java block uses `new BigDecimal(raw).setScale(3, ROUND_HALF_UP)` for the final
 *   dimDynamic/scaleDynamic outputs (not Math.round). Pre-S6 BrightnessEngine used Math.round-based
 *   round3(); corrected here per source.
 * - Duration guard: `morningDuration/eveningDuration < 1 → 60.0` (Java block L48–49), not coerceAtLeast(1).
 * - nowSecOfDay: Java block computes fresh from System.currentTimeMillis(); the platform must
 *   supply this as `(System.currentTimeMillis() / 1000L) % 86400`.
 *
 * Tasker: task90 Java Block #2, XML L41086–L41207.
 */
object DynamicScaleEngine {

    fun compute(input: DynamicScaleInput): DynamicScaleResult {
        val now = input.nowSecOfDay
        val timeV2 = now + 86400.0
        val timePrev = now - 86400.0

        // Safety guards — Tasker Java Block #2 L48–49
        val morningDuration = run {
            val d = input.morningEnd - input.morningStart
            if (d < 1.0) 60.0 else d
        }
        val eveningDuration = run {
            val d = input.eveningEnd - input.eveningStart
            if (d < 1.0) 60.0 else d
        }

        // Progress 0..1 — Tasker Java Block #2 L51–106
        var progress = when {
            input.isPolar -> if (input.sunlightDurationMinutes > 1380.0) 1.0 else 0.0
            else -> rampProgress(
                now, timeV2, timePrev,
                input.morningStart, input.morningEnd, morningDuration,
                input.eveningStart, input.eveningEnd, eveningDuration,
            )
        }
        // Clamp — Tasker Java Block #2 L104–106
        if (progress > 1.0) progress = 1.0
        if (progress < 0.0) progress = 0.0

        // Modifier (tanh sigmoid) — Tasker Java Block #2 L108–116
        val xFactor = (progress - 0.5) * input.steepness
        val tanhMax = tanh(input.steepness / 2.0)
        val modifier = if (abs(tanhMax) > 0.000001) tanh(xFactor) / tanhMax else 0.0

        // Final values — BigDecimal HALF_UP — Tasker Java Block #2 L118–123
        val dimDynamicRaw = 2.0 - (1.0 + (input.dimSpreadPercent / 100.0) * modifier)
        val scaleDynamicRaw = 1.0 + (input.scaleSpreadPercent / 100.0) * modifier

        return DynamicScaleResult(
            progress = progress,
            modifier = modifier,
            dimDynamic = bigScale3(dimDynamicRaw),
            scaleDynamic = bigScale3(scaleDynamicRaw),
        )
    }

    /**
     * Ramp progress (0..1) for a non-polar day given schedule window times.
     *
     * Checks now, now+86400, now-86400 for each window to handle midnight crossings.
     * Tasker: task90 Java Block #2 L65–101.
     */
    fun rampProgress(
        now: Double,
        timeV2: Double,
        timePrev: Double,
        morningStart: Double,
        morningEnd: Double,
        morningDuration: Double,
        eveningStart: Double,
        eveningEnd: Double,
        eveningDuration: Double,
    ): Double {
        fun inRange(t: Double, s: Double, e: Double) = t >= s && t < e

        return when {
            // Morning ramp
            inRange(now, morningStart, morningEnd) -> (now - morningStart) / morningDuration
            inRange(timeV2, morningStart, morningEnd) -> (timeV2 - morningStart) / morningDuration
            inRange(timePrev, morningStart, morningEnd) -> (timePrev - morningStart) / morningDuration
            // Evening ramp
            inRange(now, eveningStart, eveningEnd) -> 1.0 - (now - eveningStart) / eveningDuration
            inRange(timeV2, eveningStart, eveningEnd) -> 1.0 - (timeV2 - eveningStart) / eveningDuration
            inRange(timePrev, eveningStart, eveningEnd) -> 1.0 - (timePrev - eveningStart) / eveningDuration
            // Full-day check (morningEnd .. eveningStart)
            else -> {
                val isDay = (now >= morningEnd && now <= eveningStart) ||
                    (timeV2 >= morningEnd && timeV2 <= eveningStart) ||
                    (timePrev >= morningEnd && timePrev <= eveningStart)
                if (isDay) 1.0 else 0.0
            }
        }
    }

    // BigDecimal(double).setScale(3, HALF_UP) — exact-binary double constructor, Tasker parity
    private fun bigScale3(v: Double): Double =
        BigDecimal(v).setScale(3, RoundingMode.HALF_UP).toDouble()
}
