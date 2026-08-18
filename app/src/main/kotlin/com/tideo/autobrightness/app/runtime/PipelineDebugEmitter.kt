package com.tideo.autobrightness.app.runtime

import kotlin.math.abs

/** Pipeline's runtime-debug surface (S12.9e: extracted from BrightnessPipelineController). Wraps DebugSink with per-category gating. Single-writer from pipeline consumer (D-027). */
class PipelineDebugEmitter(
    private val debugSink: DebugSink,
    private val dynamicScaleGate: DynamicScaleDebugGate = DynamicScaleDebugGate(),
) {
    // Consumer-only: the last circadian scale seen, used to detect an in-progress dawn/dusk ramp
    // (the scale is time-driven, so a change between cycles means a transition is active, G2R-F48).
    private var lastScaleDynamicSeen: Double? = null

    /** Emit runtime debug Flash for [category] gated on live [debugLevel] (D-023, G2-F15). */
    fun emit(category: DebugCategory, debugLevel: Int, message: () -> String) =
        debugSink.emit(category, debugLevel, message)

    /** %AAB_Debug 4: fire only ~2 min into dawn/dusk transition (G2R-F48). DynamicScaleDebugGate throttles ≤ once per 2 min. */
    fun maybeDynamicScale(now: Long, scaleDynamic: Double, debugLevel: Int, message: () -> String) {
        val prev = lastScaleDynamicSeen
        val transitionActive = prev != null && abs(scaleDynamic - prev) > 1e-4
        lastScaleDynamicSeen = scaleDynamic
        if (dynamicScaleGate.shouldEmit(now, transitionActive)) {
            debugSink.emit(DebugCategory.DYNAMIC_SCALE, debugLevel, message)
        }
    }
}
