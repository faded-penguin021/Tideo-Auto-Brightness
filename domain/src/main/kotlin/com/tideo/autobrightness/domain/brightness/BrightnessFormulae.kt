package com.tideo.autobrightness.domain.brightness

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes the curve continuity coefficients form2A and form3A from the user-facing settings.
 *
 * Tasker: task659 "_UpdateBrightnessFormulae" XML L33337-L33347 (code 547 DoMaths). These are
 * DERIVED values (D-002/D-004): they guarantee C0 continuity across the 3 brightness zones and
 * are recomputed whenever curve settings change — not stored settings themselves.
 *
 * Parse tree for form3A (verbatim expression from task659 act1):
 *   ( zone2End * ( MaxBright - ( form2A + form2B * ( (zone2End-form2C)^0.33 - (zone1End-form2C)^0.33 ) ) ) / MaxBright )
 *   `*` and `/` are left-associative, equal precedence → ( ( zone2End * INNER ) / MaxBright )
 *   where INNER = MaxBright - ( form2A + form2B * ( A - B ) )
 *         A = (zone2End-form2C)^0.33,  B = (zone1End-form2C)^0.33
 * No rounding (DoMaths).
 */
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
        // act0: form2a = form1a * sqrt(zone1end)
        val form2A = form1A * sqrt(zone1End)
        // act1: form3a = (zone2end*(MaxBright-(form2a+form2b*((zone2end-form2c)^0.33-(zone1end-form2c)^0.33)))/MaxBright)
        val a = (zone2End - form2C).pow(0.33)
        val b = (zone1End - form2C).pow(0.33)
        val inner = maxBrightness - (form2A + form2B * (a - b))
        val form3A = zone2End * inner / maxBrightness
        return ContinuityCoefficients(form2A, form3A)
    }
}
