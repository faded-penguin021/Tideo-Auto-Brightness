package com.tideo.autobrightness.domain.brightness

/** Which task544 path a reading took: act10–17, act19–23, act25–35, or a settling placement (no Tasker path). */
enum class EvaluationOutcome { FIRST_RUN, DEAD_BAND_STOP, SMOOTHED, SETTLED }

data class BrightnessPolicyOutput(
    val targetBrightness: Int,
    val transitionDurationMs: Long,
    val animationSteps: Int,
    val animationWaitMs: Long,
    val luxAlpha: Double,
    val dimmingAlpha: Double,
    val smoothedLux: Double,
    val dynamicThreshold: Double,
    val thresholdLow: Double,
    val thresholdHigh: Double,
    // %AAB_ScaleDynamic — circadian/base scale fed into the taper (task90 output).
    val scaleDynamic: Double,
    // %AAB_ScaleDynamicCompress — the taper's effective (compressed) scale. task561/OverrideRules
    // de-compress recorded overrides with this; gated on scalingUse=true AND value != 0.
    val scaleDynamicCompress: Double,
    // DEAD_BAND_STOP: only the band, the stored percent and lastRawLux mean anything (act20–23).
    val outcome: EvaluationOutcome = EvaluationOutcome.SMOOTHED,
    val threshDynamicPercent: Double = 0.0,
    val lastRawLux: Double = 0.0,
)
