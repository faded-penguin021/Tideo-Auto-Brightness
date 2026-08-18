package com.tideo.autobrightness.domain.circadian

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.tanh

/** Inputs for dynamic-scale tanh-ramp (task90 Block #2, XML L41085): times in seconds-of-day, sunlight in minutes. */
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

/** Outputs of dynamic-scale computation (task90 Block #2 variables). */
data class DynamicScaleResult(
    val progress: Double,
    val modifier: Double,
    /** BigDecimal(raw).setScale(3, HALF_UP): `2 − (1 + dimSpread/100 * modifier)`. */
    val dimDynamic: Double,
    /** BigDecimal(raw).setScale(3, HALF_UP): `1 + scaleSpread/100 * modifier`. */
    val scaleDynamic: Double,
)

/** Pure-domain engine; replaces pre-S6 BrightnessEngine methods. Parity with task90 Block #2 (XML L41086–L41207): BigDecimal HALF_UP rounding, 60s min-duration guard. */
object DynamicScaleEngine {

    fun compute(input: DynamicScaleInput): DynamicScaleResult {
        val now = input.nowSecOfDay
        val timeV2 = now + 86400.0
        val timePrev = now - 86400.0

        val morningDuration = run {
            val d = input.morningEnd - input.morningStart
            if (d < 1.0) 60.0 else d
        }
        val eveningDuration = run {
            val d = input.eveningEnd - input.eveningStart
            if (d < 1.0) 60.0 else d
        }

        var progress = when {
            input.isPolar -> if (input.sunlightDurationMinutes > 1380.0) 1.0 else 0.0
            else -> rampProgress(
                now, timeV2, timePrev,
                input.morningStart, input.morningEnd, morningDuration,
                input.eveningStart, input.eveningEnd, eveningDuration,
            )
        }
        if (progress > 1.0) progress = 1.0
        if (progress < 0.0) progress = 0.0

        val xFactor = (progress - 0.5) * input.steepness
        val tanhMax = tanh(input.steepness / 2.0)
        val modifier = if (abs(tanhMax) > 0.000001) tanh(xFactor) / tanhMax else 0.0

        val dimDynamicRaw = 2.0 - (1.0 + (input.dimSpreadPercent / 100.0) * modifier)
        val scaleDynamicRaw = 1.0 + (input.scaleSpreadPercent / 100.0) * modifier

        return DynamicScaleResult(
            progress = progress,
            modifier = modifier,
            dimDynamic = bigScale3(dimDynamicRaw),
            scaleDynamic = bigScale3(scaleDynamicRaw),
        )
    }

    /** Ramp progress (0..1) for non-polar day; checks now, now±86400 to handle midnight crossings (task90 L65–101). */
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

    // BigDecimal HALF_UP rounding (Tasker parity).
    private fun bigScale3(v: Double): Double =
        BigDecimal(v).setScale(3, RoundingMode.HALF_UP).toDouble()
}
