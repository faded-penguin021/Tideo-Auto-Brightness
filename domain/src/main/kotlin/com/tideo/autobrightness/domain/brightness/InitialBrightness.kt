package com.tideo.autobrightness.domain.brightness

/** Tasker task618 block#1: initial smoothed-lux derivation (pure math). */
object InitialBrightness {
    /** Derive initial smoothed-lux from raw sensor reading. */
    fun computeInitialLux(rawLux: Double): Pair<Long, Double> {
        val smoothed0dp = Math.round(rawLux)
        val smoothed2dp = Math.round(rawLux * 100.0) / 100.0
        return smoothed0dp to smoothed2dp
    }
}
