package com.tideo.autobrightness.app.runtime

/**
 * The Tasker throttle window (`%AAB_Throttle`) + Throttle Reinitialization watchdog (task566 /
 * prof754), ported as a small stateful policy so the runtime [BrightnessPipelineController] can ask
 * "may a reading be accepted now?" and "what is the throttle after this cycle?" (G2R-F78).
 *
 * Tasker model:
 *  - The main-loop throttle gate (task544) drops sensor readings that arrive within `%AAB_Throttle`
 *    of the last accepted one.
 *  - After a brightness change, `%AAB_Throttle` is set to the **actual** animation duration
 *    (`steps * wait`) — NOT a hard `MaxSteps * MaxWait` default — so the loop re-evaluates no faster
 *    than it can animate (the previous rebuild hard-defaulted it to the setting, F78).
 *  - When the brightness has not changed for ~10 s (the watchdog [idleMs]), prof754 → task566 pushes
 *    the throttle up to the **ceiling** `AnimSteps * MaxWait + 10` (task566 act0) so the sensor stops
 *    polling in unchanging light (battery). The next change resets it back to the actual duration.
 *
 * Single-writer: invoked only from the pipeline consumer coroutine (D-027), so no synchronization.
 */
class ThrottleController(private val idleMs: Long = 10_000L) {

    /** The throttle window currently in force (ms). Read by the gate; published to Live Debug. */
    var throttleMs: Long = 0L
        private set

    // TIMEMS of the last brightness change; the watchdog measures idle time from here.
    private var lastChangeMs: Long? = null

    /** Seed the throttle from the user setting (service start / first cycle). */
    fun seed(baselineMs: Long) {
        throttleMs = baselineMs
        lastChangeMs = null
    }

    /** task566 act0: the throttle ceiling = AnimSteps × MaxWait + 10 (ms). */
    fun ceiling(animSteps: Int, maxWaitMs: Int): Long = animSteps.toLong() * maxWaitMs + 10L

    /**
     * Update the throttle after a cycle completes.
     *
     * @param now                monotonic clock
     * @param brightnessChanged  true when this cycle actually wrote a new brightness
     * @param stepsTimesWaitMs   the actual `animationSteps × animationWaitMs` used this cycle
     * @param ceilingMs          the [ceiling] for the current settings
     * @param baselineMs         the user's throttle setting (the floor for the active window)
     */
    fun onCycleComplete(
        now: Long,
        brightnessChanged: Boolean,
        stepsTimesWaitMs: Long,
        ceilingMs: Long,
        baselineMs: Long,
    ) {
        if (brightnessChanged) {
            // Effective throttle = the real animation duration, never below the user's setting.
            throttleMs = stepsTimesWaitMs.coerceAtLeast(baselineMs)
            lastChangeMs = now
        } else {
            val since = lastChangeMs ?: now.also { lastChangeMs = it }
            // task566 act7: after >10 s of no change, raise the throttle to the ceiling (stop polling).
            if (now - since > idleMs) throttleMs = ceilingMs
        }
    }
}
