package com.tideo.autobrightness.app.runtime

/**
 * The single runtime callback the [ContextEngine] fires when a context swap needs the pipeline to
 * re-apply Set Initial Brightness with the new effective profile (task43 act21). Implemented by
 * [BrightnessPipelineController].
 *
 * Exists to break the engine⇄controller construction cycle without a `lateinit var controller`
 * (S12.9e, deliverable #2): the engine is built first (the controller's `settingsProvider` reads
 * through it), so the controller cannot be passed to the engine's constructor. Instead the engine
 * calls through a [ControllerHookHolder] whose hook is assigned once the controller exists.
 */
fun interface ControllerHook {
    fun onContextChanged()
}

/**
 * Late-bound holder for the [ControllerHook]. [hook] is `@Volatile` because the [ContextEngine]'s
 * `onProfileChanged` callback can fire from any of its signal coroutines (battery/wifi/app/location),
 * so the assignment made on the constructing thread must be visible to them.
 *
 * A fire BEFORE assignment is a safe no-op (`hook?.onContextChanged()`), not a crash: the engine's
 * first evaluation only seeds the effective-settings snapshot, and the controller's own `start()`
 * applies it — there is nothing to re-apply until the controller is live and wired.
 */
class ControllerHookHolder {
    @Volatile
    var hook: ControllerHook? = null

    /** Fire the hook if one is wired; a no-op before assignment (see class doc). */
    fun fire() {
        hook?.onContextChanged()
    }
}
