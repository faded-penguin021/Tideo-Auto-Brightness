package com.tideo.autobrightness.domain.brightness

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
)
