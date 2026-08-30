package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult

/** Which detector saw the change (DC-007) — the two paths are otherwise indistinguishable. */
enum class OverrideSource { OBSERVER, ANIMATION_BAND }

/** What the commit guard decided about a detected override (DC-007). */
enum class OverrideDisposition { PAUSED, DISMISSED_DRIFT, DISMISSED_MODE, MODE_RECOVERY_FAILED }

/** Event-scoped record of one detected override and its disposition (DC-007). */
data class OverrideDiagnostic(
    val source: OverrideSource,
    val disposition: OverrideDisposition,
    /** The event's value: for the animation path, the read that tripped the detector. */
    val observed: Int,
    val settled: Int,
    val expected: Int?,
    val manualMode: Boolean,
    val write: BrightnessWriteResult?,
    val timestampMs: Long,
)

/** Tasker runtime state holder (pipeline_spec.md §5). All writes from pipeline coroutine only. */
data class PipelineState(
    val serviceOn: Boolean = false,
    // Atomic snapshot (D-027): serviceOn/autoRunning/paused/initializing.
    val autoRunning: Boolean = false,
    val initializing: Boolean = false,
    val paused: Boolean = false,
    // G2R-F35: detected manual override (drives high-priority notification).
    val pausedByOverride: Boolean = false,
    // prof759/task545: proximity damps smoothing alpha ×0.1.
    val proximityNear: Boolean = false,
    val smoothedLux: Double? = null,
    val lastRawLux: Double? = null,
    val lastAcceptedMs: Long? = null,
    // G2R-F5: health readout ("last sample: Xs ago").
    val lastSampleMs: Long? = null,
    val threshAbsLow: Double? = null,
    val threshAbsHigh: Double? = null,
    // G2R-F6/F7: surfaced for Reactivity diagnostic card.
    val threshDynamic: Double? = null,
    val cycleTimeMs: Double? = null,
    val luxAlpha: Double? = null,
    val animationSteps: Int? = null,
    val animationWaitMs: Long? = null,
    val throttleMs: Long? = null,
    val lastUpdateMs: Long? = null,
    // G2R-F8: surfaced for Circadian diagnostic card.
    val scaleDynamic: Double? = null,
    val scaleDynamicCompress: Double = 1.0,
    val scalingUse: Boolean = true,
    // G2R-F58: Super Dimming live readout.
    val dimmingCurrent: Double = 0.0,
    val dimmingDS: Double = 0.0,
    // D-109: hardware write (floored in PWM-sensitive mode), distinct from targetBrightness.
    val lastAppliedBrightness: Int? = null,
    // D-109: perceived brightness (un-floored); darkened by secure layer if needed.
    val targetBrightness: Int? = null,
    // DC-007: continuous, so it is readable on a device that never fires an override.
    val lastBrightnessWrite: BrightnessWriteResult? = null,
    // DC-007: event-scoped, written where an override is detected OR dismissed.
    val overrideDiagnostic: OverrideDiagnostic? = null,
    val overrideHistory: List<Pair<Double, Double>> = emptyList(),
    // S12.9d: drives Dashboard staleness gate (FRESH/AGING/STALE).
    val lastPublishMs: Long? = null,
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
    data class OverrideDetected(val observedBrightness: Int, val source: OverrideSource) : PipelineEvent

    /** A context override swapped the active profile → re-run Set Initial Brightness (task43 act21). */
    data object ContextChanged : PipelineEvent
}
