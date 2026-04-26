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
    val threshMidpoint: Double = 3.0,
    val zone1End: Double = 35.0,
    val deltaFactor: Double = 1.8,
)

data class BrightnessCurveConfig(
    val form1A: Double = 5.0,
    val form2A: Double = 29.58,
    val form2B: Double = 8.8,
    val form2C: Double = 18.0,
    val zone2End: Double = 10_000.0,
    val form3A: Double = 2513.0,
    val minBrightness: Int = 10,
    val maxBrightness: Int = 255,
    val offset: Double = 0.0,
    val taperMidpoint: Double = 190.0,
    val taperSteepness: Double = 0.075,
)

data class AnimationConfig(
    val maxSteps: Int = 50,
    val minWaitMs: Long = 5,
    val maxWaitMs: Long = 30,
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
