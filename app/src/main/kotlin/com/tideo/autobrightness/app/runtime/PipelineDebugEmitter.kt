package com.tideo.autobrightness.app.runtime

import kotlin.math.abs

/**
 * The pipeline's runtime-debug surface (S12.9e: extracted from BrightnessPipelineController). Wraps
 * the [DebugSink] with the per-category gating helper and owns the **Dynamic Scale** flash timing
 * gate so the controller orchestrator stays focused on the state machine.
 *
 * Single-writer: every method is called only from the pipeline consumer coroutine (D-027), so the
 * `lastScaleDynamicSeen` tracker needs no synchronization.
 */
class PipelineDebugEmitter(
    private val debugSink: DebugSink,
    private val dynamicScaleGate: DynamicScaleDebugGate = DynamicScaleDebugGate(),
) {
    // Consumer-only: the last circadian scale seen, used to detect an in-progress dawn/dusk ramp
    // (the scale is time-driven, so a change between cycles means a transition is active, G2R-F48).
    private var lastScaleDynamicSeen: Double? = null

    /** Emit a runtime debug Flash for [category] gated on the live [debugLevel] (D-023, G2-F15). */
    fun emit(category: DebugCategory, debugLevel: Int, message: () -> String) =
        debugSink.emit(category, debugLevel, message)

    /**
     * %AAB_Debug 4 "Dynamic Scale Calcs": fire only ~2 min into a dawn/dusk transition, not on every
     * light change (G2R-F48). A transition is the time-driven circadian scale [scaleDynamic] changing
     * between cycles; [DynamicScaleDebugGate] then throttles to ≤ once per 2 min.
     */
    fun maybeDynamicScale(now: Long, scaleDynamic: Double, debugLevel: Int, message: () -> String) {
        val prev = lastScaleDynamicSeen
        val transitionActive = prev != null && abs(scaleDynamic - prev) > 1e-4
        lastScaleDynamicSeen = scaleDynamic
        if (dynamicScaleGate.shouldEmit(now, transitionActive)) {
            debugSink.emit(DebugCategory.DYNAMIC_SCALE, debugLevel, message)
        }
    }
}
