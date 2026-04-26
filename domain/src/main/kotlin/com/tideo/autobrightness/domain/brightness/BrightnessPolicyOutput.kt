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
    val scaleDynamic: Double,
)
