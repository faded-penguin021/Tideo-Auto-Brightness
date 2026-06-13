package com.tideo.autobrightness.domain.brightness

data class BrightnessPolicyInput(
    val lux: Double,
    val time: TimeContext,
    val context: BrightnessContext = BrightnessContext(),
    val overrides: BrightnessOverrides = BrightnessOverrides(),
    val thresholds: ThresholdConfig = ThresholdConfig(),
    val curve: BrightnessCurveConfig = BrightnessCurveConfig(),
    val animation: AnimationConfig = AnimationConfig(),
    val dynamicScaling: DynamicScalingConfig = DynamicScalingConfig(),
    val previous: PreviousState? = null,
)

data class TimeContext(
    val secondsOfDay: Double,
    val morningStart: Double = 6 * 3600.0,
    val morningEnd: Double = 8 * 3600.0,
    val eveningStart: Double = 18 * 3600.0,
    val eveningEnd: Double = 20 * 3600.0,
    val sunlightDurationMinutes: Double = 720.0,
)

data class BrightnessContext(
    val isPolarDayNight: Boolean = false,
)

data class BrightnessOverrides(
    val manualBrightness: Int? = null,
    val baseScaleOverride: Double? = null,
)

data class ThresholdConfig(
    val threshDark: Double = 0.3,
    val threshDim: Double = 0.25,
    val threshBright: Double = 0.08,
    val threshSteepness: Double = 2.1,
    // Tasker: task570 act39 %AAB_ThreshMidpoint = log10(%AAB_Zone2End) = log10(10000) = 4 (D-004/D-008)
    val threshMidpoint: Double = 4.0,
    val zone1End: Double = 35.0,
    val deltaFactor: Double = 1.8,
)

data class BrightnessCurveConfig(
    val form1A: Double = 5.0,
    // Derived continuity defaults (task659) for the default settings; the mapper recomputes
    // them via BrightnessFormulae.deriveContinuityCoefficients. Exact = 5*sqrt(35), etc.
    val form2A: Double = 29.58039891549808,
    val form2B: Double = 8.8,
    val form2C: Double = 18.0,
    val zone1End: Double = 35.0,
    val zone2End: Double = 10_000.0,
    val form3A: Double = 2513.1533352729266,
    val minBrightness: Int = 10,
    val maxBrightness: Int = 255,
    val offset: Double = 0.0,
    val taperMidpoint: Double = 190.0,
    val taperSteepness: Double = 0.075,
    // Tasker task661 act10/14: %AAB_ScalingUse gates the taper (task548) vs. the linear
    // `mapped * %AAB_Scale + %AAB_Offset` branch. %AAB_Scale is the static base multiplier.
    val scalingUse: Boolean = true,
    val scale: Double = 1.0,
)

data class AnimationConfig(
    // Tasker: task570 act26/27/28: AAB_AnimSteps=20, AAB_MinWait=25ms, AAB_MaxWait=65ms (D-004/D-008)
    val maxSteps: Int = 20,
    val minWaitMs: Long = 25,
    val maxWaitMs: Long = 65,
)

data class DynamicScalingConfig(
    val enabled: Boolean = false,
    val spreadPercent: Double = 15.0,
    val dimSpreadPercent: Double = 100.0,
    val steepness: Double = 6.0,
)

data class PreviousState(
    val smoothedLux: Double,
    val lastRawLux: Double,
    val cycleTimeMs: Double? = null,
)
