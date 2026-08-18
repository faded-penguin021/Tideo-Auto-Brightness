package com.tideo.autobrightness.domain.circadian

import kotlin.math.roundToInt

/** D-154: circadian Night Light temperature. Tanh modifier → Kelvin ramp (pure math, no Android facts). */
object NightLightTemperatureRamp {

    /** Kelvin for given circadian modifier (−1=night, +1=day); linear blend, clamped to anchor range. */
    fun temperature(modifier: Double, nightKelvin: Int, dayKelvin: Int): Int {
        val dayFraction = ((modifier + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val raw = nightKelvin + (dayKelvin - nightKelvin) * dayFraction
        return raw.roundToInt()
            .coerceIn(minOf(nightKelvin, dayKelvin), maxOf(nightKelvin, dayKelvin))
    }
}
