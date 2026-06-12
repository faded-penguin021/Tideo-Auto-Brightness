package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Validation error for a specific field.
 * Used by S12 UI to render red-invalid state on curve-settings fields.
 */
data class FieldError(val field: String, val message: String)

/**
 * Tasker-faithful validation of brightness-curve parameters.
 *
 * Implements exactly 5 rules from two Tasker tasks:
 *   - task583 `_RedInvalidFormulae` (3 advisory): form2A<0, form3A<0, form2C>zone1End
 *   - task707 `_ValidateBrightnessParams` (2 safety): predicted brightness@1000lux<25, MaxBright default 255
 *
 * Tasker: task583 L23084–L23190; task707 L38095–L38256.
 */
object SettingsValidator {

    fun validate(settings: AabSettings): List<FieldError> {
        val errors = mutableListOf<FieldError>()

        val maxBright = settings.maxBrightness.toDouble()

        // Derive continuity coefficients (these are what task583 calls %aab_form2a / %aab_form3a)
        val coeffs = BrightnessFormulae.deriveContinuityCoefficients(
            form1A = settings.form1A.toDouble(),
            form2B = settings.form2B.toDouble(),
            form2C = settings.form2C.toDouble(),
            zone1End = settings.zone1End.toDouble(),
            zone2End = settings.zone2End.toDouble(),
            maxBrightness = maxBright,
        )

        // task583 rule 1: form2A < 0 → advisory red field
        if (coeffs.form2A < 0.0) {
            errors += FieldError("form2A", "%.3f (automatic) — form2A < 0, adjust form1A or zone1End".format(coeffs.form2A))
        }

        // task583 rule 2: form3A < 0 → advisory red field
        if (coeffs.form3A < 0.0) {
            errors += FieldError("form3A", "%.0f (auto) — form3A < 0, adjust curve parameters".format(coeffs.form3A))
        }

        // task583 rule 3: form2C > zone1End → advisory red Zone 2 Offset label
        if (settings.form2C > settings.zone1End) {
            errors += FieldError("form2C", "Zone 2 Offset (${settings.form2C}) must be ≤ zone1End (${settings.zone1End})")
        }

        // task707: compute predicted brightness at 1000 lux using the correct zone formula
        // %aab_form2d = zone1End (derived coefficient form2D = zone1End, defaults_audit)
        val safeVal: Double = when {
            settings.zone1End > 1000 -> {
                // Zone 1: form1A * sqrt(1000)
                settings.form1A.toDouble() * sqrt(1000.0)
            }
            settings.zone2End > 1000 -> {
                // Zone 2: form2A + form2B * ((1000-form2C)^0.33 - (form2D-form2C)^0.33)
                // form2D = zone1End (D-004)
                val a = (1000.0 - settings.form2C).pow(0.33)
                val b = (settings.zone1End.toDouble() - settings.form2C).pow(0.33)
                coeffs.form2A + settings.form2B.toDouble() * (a - b)
            }
            else -> {
                // Zone 3: MaxBright - (form3A / 1000) * MaxBright
                maxBright - (coeffs.form3A / 1000.0) * maxBright
            }
        }

        // task707 safety rule: predicted brightness@1000lux < 25 → warning + is_safe=no
        if (safeVal < 25.0) {
            errors += FieldError(
                "safetyBrightness",
                "⚠️ Safety Warning: Brightness too low at 1000 Lux (%.0f / 255). Please adjust parameters.".format(safeVal),
            )
        }

        return errors
    }
}
