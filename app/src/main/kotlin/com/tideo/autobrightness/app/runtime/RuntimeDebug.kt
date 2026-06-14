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
