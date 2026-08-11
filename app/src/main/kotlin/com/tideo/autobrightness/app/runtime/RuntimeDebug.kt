package com.tideo.autobrightness.app.runtime

enum class DebugCategory(val level: Int) {
    SKIP_ANIMATIONS(1),
    ANIMATION_DETAILS(2),
    LIGHT_EVAL(3),
    DYNAMIC_SCALE(4),
    SUPER_DIMMING(5),
    OVERLAY_PREVIEW(6),
    GRAPH_METRICS(7),
    CONTEXT_AUTOMATION(8),
    CONTEXT_LOCATION(9),
}

fun interface DebugSink {
    fun emit(category: DebugCategory, activeLevel: Int, message: () -> String)
}

object NoOpDebugSink : DebugSink {
    override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) = Unit
}

// Timing gate for Dynamic Scale debug Flash (G2R-F48). Fire after delayMs, then throttle per intervalMs.
class DynamicScaleDebugGate(
    private val delayMs: Long = 120_000L,
    private val intervalMs: Long = 120_000L,
) {
    private var transitionStartMs: Long? = null
    private var lastEmitMs: Long? = null

    fun shouldEmit(nowMs: Long, transitionActive: Boolean): Boolean {
        if (!transitionActive) {
            transitionStartMs = null
            return false
        }
        val start = transitionStartMs ?: nowMs.also { transitionStartMs = it }
        if (nowMs - start < delayMs) return false
        lastEmitMs?.let { if (nowMs - it < intervalMs) return false }
        lastEmitMs = nowMs
        return true
    }
}
