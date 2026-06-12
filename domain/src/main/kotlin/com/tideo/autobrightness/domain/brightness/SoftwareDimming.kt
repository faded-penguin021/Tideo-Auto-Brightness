package com.tideo.autobrightness.domain.brightness

import kotlin.math.pow

/**
 * Pure domain math for the software super-dimming feature (reduce_bright_colors).
 *
 * Privilege split is the platform's concern (S7/S9b); the math is domain-only.
 * Two computation paths mirror the Tasker pipeline:
 *  - [finalDimLevel] — task700 "Software Dimming V2": given a target brightness, compute the
 *    reduce_bright_colors level to write.
 *  - [dimProgress] / [dimShell] — task646/647 "Calculate Super Dimming": given current display
 *    brightness, compute the fractional dimming progress and the strength-weighted dim shell.
 */
object SoftwareDimming {

    /**
     * Compute the reduce_bright_colors level for a given target brightness.
     *
     * Tasker: task700 "Software Dimming V2" XML L36474-L36712 (code 547 DoMaths).
     *
     * @param targetBrightness  The computed display brightness (%par1 in task700).
     * @param isElevated        True when privilege tier allows WRITE_SECURE_SETTINGS (%AAB_Privilege != None).
     * @param dimmingThreshold  %AAB_DimmingThreshold — brightness level below which dimming engages.
     * @param pwmExp            %AAB_PWMExp — exponent shaping the dim curve.
     * @return                  The reduce_bright_colors level to apply (0-99 when elevated, 0-252 otherwise).
     */
    fun finalDimLevel(
        targetBrightness: Double,
        isElevated: Boolean,
        dimmingThreshold: Double,
        pwmExp: Double,
    ): Double {
        // act0-4: max_dim = 99 (privileged secure-settings range) or 252.45 (99% of 0-255 overlay range)
        val maxDim = if (isElevated) 99.0 else 252.45

        // act5-8: safe_thresh = max(DimmingThreshold, 1) — prevents divide-by-zero
        val safeThresh = maxOf(dimmingThreshold, 1.0)

        // act9: dark_floor = 0.95 — screen at 0 brightness is at most 95% opaque
        val darkFloor = 0.95

        // act10: k_factor = (1 - dark_floor) ^ (1 / PWMExp)
        val kFactor = (1.0 - darkFloor).pow(1.0 / pwmExp)

        // act11-14: bias = max((k_factor * safe_thresh) / (1 - k_factor), 10)
        var bias = (kFactor * safeThresh) / (1.0 - kFactor)
        if (bias < 10.0) bias = 10.0

        // act15-18: ratio = min((par1 + bias) / (safe_thresh + bias), 1)
        var ratio = (targetBrightness + bias) / (safeThresh + bias)
        if (ratio > 1.0) ratio = 1.0

        // act19: final_dim = max_dim * (1 - ratio ^ PWMExp)
        var finalDim = maxDim * (1.0 - ratio.pow(pwmExp))

        // act20-24: safety clamps (should mathematically never trigger, but prevent black screens)
        if (finalDim > maxDim && isElevated) finalDim = 99.0
        else if (!isElevated && finalDim > maxDim) finalDim = 253.0

        return finalDim
    }

    /**
     * Compute the dimming progress fraction (0.0–1.0) based on current display brightness.
     *
     * Tasker: task646 act3 / task647 act2 "Calculate Super Dimming" (DoMaths).
     * dim_progress = (1 - (brightness - MinBright) / (DimmingThreshold - MinBright)) ^ DimmingExponent
     *
     * @param brightness         Current (or target) display brightness.
     * @param minBrightness      %AAB_MinBright.
     * @param dimmingThreshold   %AAB_DimmingThreshold.
     * @param dimmingExponent    %AAB_DimmingExponent.
     * @return                   Dimming progress clamped to [0.0, 1.0].
     */
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

    /**
     * Compute the dim shell (strength × progress) with clamped strength.
     *
     * Tasker: task646 act16 / task647 act15 dim_shell = clamped_strength * dim_progress.
     * Strength is clamped to [0, 65] to prevent screens too dark (owner comment, L31057).
     *
     * @param brightness         Current display brightness.
     * @param minBrightness      %AAB_MinBright.
     * @param dimmingThreshold   %AAB_DimmingThreshold.
     * @param dimmingExponent    %AAB_DimmingExponent.
     * @param dimmingStrength    %AAB_DimmingStrength (user setting).
     * @param dimDynamic         %AAB_DimDynamic multiplier when dynamic scaling is active; null otherwise.
     * @return                   The dim shell value (0.0–65.0).
     */
    fun dimShell(
        brightness: Double,
        minBrightness: Double,
        dimmingThreshold: Double,
        dimmingExponent: Double,
        dimmingStrength: Double,
        dimDynamic: Double?,
    ): Double {
        val rawStrength = if (dimDynamic != null) dimmingStrength * dimDynamic else dimmingStrength
        // Clamp to [0, 65] — owner comment: prevents screens that are too dark
        val clampedStrength = rawStrength.coerceIn(0.0, 65.0)
        val progress = dimProgress(brightness, minBrightness, dimmingThreshold, dimmingExponent)
        return clampedStrength * progress
    }
}
