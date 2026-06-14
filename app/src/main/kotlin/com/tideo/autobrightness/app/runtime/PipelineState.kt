package com.tideo.autobrightness.app.runtime

/**
 * The single Tasker-runtime state holder (pipeline_spec.md §5). All fields are written ONLY from
 * the pipeline coroutine (BrightnessPipelineController); UI/notification read immutable snapshots
 * via the controller's StateFlow (D-027 concurrency model).
 *
 * Maps the runtime (non-settings) `%AAB_*` variables that survive between sensor ticks.
 */
data class PipelineState(
    /** %AAB_Service — master enable; false stops the loop. */
    val serviceOn: Boolean = false,
    /** true == paused by a manual override (%AAB_Manual_Override). */
    val paused: Boolean = false,
    /** %SmoothedLux — EMA-smoothed lux; null until the first reading seeds it. */
    val smoothedLux: Double? = null,
    /** %AAB_LastRawLux — last raw lux (round3). */
    val lastRawLux: Double? = null,
    /** %LastAAB — TIMEMS of the last accepted tick (throttle anchor); null until first accept. */
    val lastAcceptedMs: Long? = null,
    /** TIMEMS of the last sensor sample the collector saw (any sample, not just accepted ticks);
     *  drives the Dashboard "last sample: Xs ago" health readout (G2R-F5). */
    val lastSampleMs: Long? = null,
    /** %AAB_ThreshAbsLow — absolute dead-band low (task546); null until seeded. */
    val threshAbsLow: Double? = null,
    /** %AAB_ThreshAbsHigh — absolute dead-band high (task546). */
    val threshAbsHigh: Double? = null,
    /** %AAB_ThreshDynamic — last dynamic (lux-adaptive) reactivity threshold (task544); null until
     *  the first cycle. Surfaced for the Reactivity diagnostic card / Live Debug scene (G2R-F6/F7). */
    val threshDynamic: Double? = null,
    /** %AAB_CycleTime — measured duration of the last cycle, feeds task543's throttle. */
    val cycleTimeMs: Double? = null,
    /** %LuxAlpha — last smoothing alpha (1 - exp(-deltaFactor·effectiveDelta)); Live Debug (G2R-F29). */
    val luxAlpha: Double? = null,
    /** Last animation step count (%AAB_AnimSteps used this cycle); Live Debug "Last Animation" (G2R-F29). */
    val animationSteps: Int? = null,
    /** Last per-step wait (ms) used this cycle; Live Debug "Last Animation" (G2R-F29). */
    val animationWaitMs: Long? = null,
    /** %AAB_Throttle — reactivity cooldown window (ms) in force; Live Debug (G2R-F29). */
    val throttleMs: Long? = null,
    /** TIMEMS of the last brightness update this pipeline wrote (%AAB_LastUpdate); Live Debug (G2R-F29). */
    val lastUpdateMs: Long? = null,
    /** %AAB_ScaleDynamic — last UNCOMPRESSED circadian/base scale (task90 output, pre-taper); null
     *  until the first cycle. Surfaced for the Circadian diagnostic card / Live Debug scene (G2R-F8). */
    val scaleDynamic: Double? = null,
    /** %AAB_ScaleDynamicCompress — last taper effective scale (task561 de-compression input). */
    val scaleDynamicCompress: Double = 1.0,
    /** Last %AAB_ScalingUse seen (task561 gate). */
    val scalingUse: Boolean = true,
    /** Last brightness this pipeline applied (domain 0–255); null before the first write. */
    val lastAppliedBrightness: Int? = null,
    /** Last target the engine produced (for the notification "target" readout). */
    val targetBrightness: Int? = null,
    /** Recorded manual-override points (newest first), %AAB_Overrides (cap 50). */
    val overrideHistory: List<Pair<Double, Double>> = emptyList(),
)

/** Events serialized through the single pipeline consumer (one runs to completion, D-027). */
sealed interface PipelineEvent {
    /** A gated light-sensor reading that passed prof760; carries raw lux + accuracy. */
    data class SensorTick(val lux: Double, val accuracy: Int) : PipelineEvent

    /** Display OFF → hibernate (prof753 / task585). */
    data object ScreenOff : PipelineEvent

    /** Display ON → reinit: throttle reset (task566) + initial brightness (task618). */
    data object ScreenOn : PipelineEvent

    /** User asked to pause auto-control. */
    data object Pause : PipelineEvent

    /** User tapped Resume (task569). */
    data object Resume : PipelineEvent

    /** An external brightness write was detected as a manual override (prof755 / task567). */
    data class OverrideDetected(val observedBrightness: Int) : PipelineEvent

    /** A context override swapped the active profile → re-run Set Initial Brightness (task43 act21). */
    data object ContextChanged : PipelineEvent
}
