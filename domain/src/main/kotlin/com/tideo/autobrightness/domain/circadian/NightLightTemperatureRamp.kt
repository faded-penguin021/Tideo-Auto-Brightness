package com.tideo.autobrightness.domain.circadian

import kotlin.math.roundToInt

/**
 * Circadian Night Light temperature (rebuild-only feature, no Tasker source — D-154): maps the
 * [DynamicScaleEngine] tanh `modifier` onto a Kelvin ramp so the Night Light color temperature
 * "ticks along with" the circadian scaling — warmest (reddest) at night, relaxing toward the
 * coolest (weakest-filter) value in full daylight.
 *
 * Pure math only; the Kelvin anchors are parameters (the AOSP bounds/defaults live in
 * `:platform`'s SecureDisplayController — `:domain` stays Android-fact-free).
 */
object NightLightTemperatureRamp {

    /**
     * Kelvin for the given circadian [modifier] (tanh output, −1 = deepest night … +1 = full
     * day, the same value that drives `%AAB_ScaleDynamic`): a linear blend from [nightKelvin]
     * (modifier −1) to [dayKelvin] (modifier +1), rounded and clamped to the anchor range.
     * Out-of-range modifiers (defensive; the engine already clamps progress) pin to an anchor.
     */
    fun temperature(modifier: Double, nightKelvin: Int, dayKelvin: Int): Int {
        val dayFraction = ((modifier + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val raw = nightKelvin + (dayKelvin - nightKelvin) * dayFraction
        return raw.roundToInt()
            .coerceIn(minOf(nightKelvin, dayKelvin), maxOf(nightKelvin, dayKelvin))
    }
}
