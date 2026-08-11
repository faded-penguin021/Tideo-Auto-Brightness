package com.tideo.autobrightness.domain.brightness

import kotlin.math.pow

/** Software super-dimming math (task700 finalDimLevel, task646/647 dimProgress/dimShell). */
object SoftwareDimming {

    /** task700: reduce_bright_colors level for target brightness. */
    fun finalDimLevel(
        targetBrightness: Double,
        isElevated: Boolean,
        dimmingThreshold: Double,
        pwmExp: Double,
    ): Double {
        val maxDim = if (isElevated) 99.0 else 252.45
        val safeThresh = maxOf(dimmingThreshold, 1.0)
        val darkFloor = 0.95  // Screen at 0 brightness is at most 95% opaque.
        val kFactor = (1.0 - darkFloor).pow(1.0 / pwmExp)
        var bias = (kFactor * safeThresh) / (1.0 - kFactor)
        if (bias < 10.0) bias = 10.0
        var ratio = (targetBrightness + bias) / (safeThresh + bias)
        if (ratio > 1.0) ratio = 1.0
        var finalDim = maxDim * (1.0 - ratio.pow(pwmExp))
        // Safety clamps (prevent black screens).
        if (finalDim > maxDim && isElevated) finalDim = 99.0
        else if (!isElevated && finalDim > maxDim) finalDim = 253.0
        return finalDim
    }

    /** task646 act3 / task647 act2: dimming progress (0.0–1.0). */
    fun dimProgress(
        brightness: Double,
        minBrightness: Double,
        dimmingThreshold: Double,
        dimmingExponent: Double,
    ): Double {
        val span = dimmingThreshold - minBrightness
        if (span <= 0.0) return 1.0
        var progress = (1.0 - (brightness - minBrightness) / span).pow(dimmingExponent)
        if (progress < 0.0) progress = 0.0
        if (progress > 1.0) progress = 1.0
        return progress
    }

    /** task646 act16 / task647 act15: dim shell = clamped_strength * dim_progress. */
    fun dimShell(
        brightness: Double,
        minBrightness: Double,
        dimmingThreshold: Double,
        dimmingExponent: Double,
        dimmingStrength: Double,
        dimDynamic: Double?,
    ): Double {
        val rawStrength = if (dimDynamic != null) dimmingStrength * dimDynamic else dimmingStrength
        // Clamp to [0, 65]: prevent screens too dark.
        val clampedStrength = rawStrength.coerceIn(0.0, 65.0)
        val progress = dimProgress(brightness, minBrightness, dimmingThreshold, dimmingExponent)
        return clampedStrength * progress
    }
}
