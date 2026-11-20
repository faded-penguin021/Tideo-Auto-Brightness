package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.domain.brightness.SoftwareDimming

/** task650 act28/act30 readout (G2R-F58): relative `DS × progress` and absolute DS; [NONE] while not engaged (DE-044). */
data class DimmingReadout(val current: Double, val ds: Double) {
    companion object {
        val NONE = DimmingReadout(0.0, 0.0)
    }
}

/** The one dimming calculation behind the level write and its readout (DE-044); two paths, G2-F10. */
internal data class DimmingPlan(val mode: Mode, val readout: DimmingReadout, val level: Int) {
    enum class Mode { PWM, SD }

    companion object {
        /** Null when neither path wants dimming at [target]; [dimDynamic] is task646 act6/act7's multiplier. */
        fun of(target: Int, settings: AabSettings, dimDynamic: Double?): DimmingPlan? {
            if (target >= settings.dimmingThreshold) return null
            return when {
                // task700 (task661 act23): reduce_bright_colors level for PWM-floored hardware (G2R-F65).
                settings.pwmSensitive -> {
                    val ds = SoftwareDimming.finalDimLevel(
                        targetBrightness = target.toDouble(),
                        isElevated = true,
                        dimmingThreshold = settings.dimmingThreshold.toDouble(),
                        pwmExp = settings.pwmExponent.toDouble(),
                    )
                    DimmingPlan(Mode.PWM, DimmingReadout(ds, ds), Math.round(ds).toInt())
                }
                settings.dimmingEnabled -> {
                    val ds = shell(target, settings, dimDynamic)
                    val progress = SoftwareDimming.dimProgress(
                        brightness = target.toDouble(),
                        minBrightness = settings.minBrightness.toDouble(),
                        dimmingThreshold = settings.dimmingThreshold.toDouble(),
                        dimmingExponent = settings.dimmingExponent.toDouble(),
                    )
                    DimmingPlan(Mode.SD, DimmingReadout(ds * progress, ds), Math.round(ds).toInt())
                }
                else -> null
            }
        }

        /** task646 act3-16 dim_shell with the optional circadian DimDynamic (G2R-F90). */
        fun shell(target: Int, settings: AabSettings, dimDynamic: Double?): Double = SoftwareDimming.dimShell(
            brightness = target.toDouble(),
            minBrightness = settings.minBrightness.toDouble(),
            dimmingThreshold = settings.dimmingThreshold.toDouble(),
            dimmingExponent = settings.dimmingExponent.toDouble(),
            dimmingStrength = settings.dimmingStrength.toDouble(),
            dimDynamic = dimDynamic,
        )
    }
}
