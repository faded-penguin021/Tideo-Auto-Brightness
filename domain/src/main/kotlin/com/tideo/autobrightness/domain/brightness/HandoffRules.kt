package com.tideo.autobrightness.domain.brightness

object HandoffRules {
    const val MAX_LUX = 100_000
    const val VALIDITY_FRACTION = 0.9

    enum class Decision { ENTER, STAY, EXIT, NONE }

    private val engine = BrightnessEngine()

    // Tasker: "_DetermineMinHand-offThresh" (AAB hand-off export, DD-070), scanned rather than bisected (DD-071).
    fun minimumHandoffLux(cfg: BrightnessCurveConfig): Int? {
        val target = VALIDITY_FRACTION * cfg.maxBrightness
        var lux = MAX_LUX
        while (lux >= 1 && engine.mapLuxToBrightness(lux.toDouble(), cfg) >= target) lux--
        return if (lux == MAX_LUX) null else lux + 1
    }

    fun effectiveThreshold(storedLux: Int, cfg: BrightnessCurveConfig): Int? =
        minimumHandoffLux(cfg)?.let { maxOf(storedLux, it) }

    fun exitLux(thresholdLux: Int, hysteresis: Double): Double = thresholdLux * hysteresis

    // Tasker: "_Hand-offDecider" (AAB hand-off export, DD-070).
    fun next(
        active: Boolean,
        enabled: Boolean,
        smoothedLux: Double,
        effectiveThreshold: Int?,
        hysteresis: Double,
    ): Decision = when {
        !active ->
            if (enabled && effectiveThreshold != null && smoothedLux > effectiveThreshold) Decision.ENTER else Decision.NONE
        !enabled || effectiveThreshold == null || smoothedLux < exitLux(effectiveThreshold, hysteresis) -> Decision.EXIT
        else -> Decision.STAY
    }
}
