package com.tideo.autobrightness.domain.brightness

/**
 * Wake-time initial brightness logic: on display-on / resume-after-override, set a starting
 * brightness immediately without waiting for the full smoothing pipeline to stabilise.
 *
 * Tasker: task618 "Set Initial Brightness (Java) V3" block#1 (XML L25827-L25845).
 * The platform entry point (task618 acn0-42) handles sensor polling, context evaluation,
 * dimming, and profile tasks; this object encapsulates only the pure math (block#1).
 */
object InitialBrightness {
    /**
     * Derive the initial smoothed-lux values from a raw sensor reading.
     *
     * Tasker task618 block#1:
     *   smoothed_lux (0-dp)   = Math.round(raw_lux)
     *   SmoothedLux  (2-dp)   = Math.round(raw_lux * 100.0) / 100.0
     *
     * Both use Java `Math.round` (ties toward +∞). The 0-dp value seeds the pipeline's
     * %smoothed_lux; the 2-dp value is the display-facing %SmoothedLux global.
     *
     * @param rawLux Raw sensor lux value parsed from %as_values1.
     * @return A pair of (smoothedLux0dp, smoothedLux2dp).
     */
    fun computeInitialLux(rawLux: Double): Pair<Long, Double> {
        val smoothed0dp = Math.round(rawLux)
        val smoothed2dp = Math.round(rawLux * 100.0) / 100.0
        return smoothed0dp to smoothed2dp
    }
}
