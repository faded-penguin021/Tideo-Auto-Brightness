package com.tideo.autobrightness.domain.brightness

import kotlin.math.pow
import kotlin.math.sqrt

/** Curve continuity coefficients form2A and form3A guarantee C0 continuity (D-002/D-004).
 * Tasker: task659 "_UpdateBrightnessFormulae" L33337-L33347. Derived values, recomputed on change. */
object BrightnessFormulae {
    data class ContinuityCoefficients(val form2A: Double, val form3A: Double)

    fun deriveContinuityCoefficients(
        form1A: Double,
        form2B: Double,
        form2C: Double,
        zone1End: Double,
        zone2End: Double,
        maxBrightness: Double,
    ): ContinuityCoefficients {
        val form2A = form1A * sqrt(zone1End)
        val a = (zone2End - form2C).pow(0.33)
        val b = (zone1End - form2C).pow(0.33)
        val inner = maxBrightness - (form2A + form2B * (a - b))
        val form3A = zone2End * inner / maxBrightness
        return ContinuityCoefficients(form2A, form3A)
    }

    /** Zone2End brightness: minimum MaxBright needed for curve validity (D-169). */
    fun zone2EndBrightness(
        form1A: Double,
        form2B: Double,
        form2C: Double,
        zone1End: Double,
        zone2End: Double,
    ): Double {
        val form2A = form1A * sqrt(zone1End)
        val a = (zone2End - form2C).pow(0.33)
        val b = (zone1End - form2C).pow(0.33)
        return form2A + form2B * (a - b)
    }
}
