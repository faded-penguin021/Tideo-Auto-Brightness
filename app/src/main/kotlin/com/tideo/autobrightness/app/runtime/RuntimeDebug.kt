package com.tideo.autobrightness.app.runtime

/**
 * The 10 `%AAB_Debug` categories from the Debug-scene selector (D-023). [level] is the `debugLevel`
 * value that activates the category (the selector index in `LiveDebugScreen.DEBUG_LABELS`); level 0
 * ("Off") has no category, so it is absent here. In Tasker the selector is single-valued — exactly
 * ONE category is live at a time — and each Flashes its info on the matching pipeline event.
 */
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

/**
 * Runtime debug surface (the Kotlin rebuild of Tasker AAB's Flash-on-eval debug, G2-F15). The
 * pipeline / dimming / context engine emit category-tagged messages; the Android impl
 * ([ToastDebugSink]) surfaces a Toast only when the live `debugLevel` selects that category. The
 * [message] is a lambda so it is only built when the category is actually live (no per-cycle string
 * formatting when debug is Off).
 */
fun interface DebugSink {
    fun emit(category: DebugCategory, activeLevel: Int, message: () -> String)
}

/** Default used when no debug surface is wired (unit tests + the disabled-debug path). */
object NoOpDebugSink : DebugSink {
    override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) = Unit
}

/**
 * Timing gate for the **Dynamic Scale** debug Flash (G2R-F48). In Tasker this fired ~2 minutes into a
 * dawn/dusk transition, NOT on every light change. The dynamic scale is a function of time-of-day
 * only (the circadian ramp), so a "transition is in progress" whenever its value is actively changing
 * between cycles; while it is settled (daytime / nighttime plateau) the gate stays closed and resets.
 *
 * Behaviour: once a transition has been active for [delayMs] the first Flash fires; subsequent Flashes
 * are then throttled to at most once per [intervalMs]. When the transition settles the gate resets so
 * the next ramp again waits [delayMs] before flashing. Pure state machine so the timing is unit-tested
 * without the pipeline ([BrightnessPipelineController] feeds it `now` + whether the scale is ramping).
 */
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
