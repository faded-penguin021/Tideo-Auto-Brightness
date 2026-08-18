package com.tideo.autobrightness.app.runtime

/**
 * Tasker throttle window + reinitialization watchdog (task566/prof754, G2R-F78).
 * Drops sensor readings within %AAB_Throttle of the last accepted one.
 * After brightness changes, throttle = actual animation duration.
 * After ~10s idle, throttle raises to ceiling to stop polling.
 */
class ThrottleController(private val idleMs: Long = 10_000L) {

    /** Throttle window (ms); @Volatile for cross-thread visibility. */
    @Volatile
    var throttleMs: Long = 0L
        private set

    // TIMEMS of last significant change; watchdog measures idle time from here.
    @Volatile
    private var lastChangeMs: Long? = null

    /** Seed the throttle from the user setting (service start / first cycle). */
    fun seed(baselineMs: Long) {
        throttleMs = baselineMs
        lastChangeMs = null
    }

    /** task566 act0: the throttle ceiling = AnimSteps × MaxWait + 10 (ms). */
    fun ceiling(animSteps: Int, maxWaitMs: Int): Long = animSteps.toLong() * maxWaitMs + 10L

    /** Throttle watchdog on every delivered sample (G2R-F78): re-anchor on significant changes, raise on idle. */
    fun onSample(now: Long, significant: Boolean, ceilingMs: Long) {
        if (significant) {
            lastChangeMs = now
        } else {
            val anchor = lastChangeMs ?: now.also { lastChangeMs = it }
            if (now - anchor > idleMs) throttleMs = ceilingMs
        }
    }

    /** Update throttle after cycle: use actual duration if brightness changed, else raise to ceiling if idle. */
    fun onCycleComplete(
        now: Long,
        brightnessChanged: Boolean,
        actualThrottleMs: Long,
        ceilingMs: Long,
    ) {
        if (brightnessChanged) {
            throttleMs = actualThrottleMs.coerceAtLeast(0L) // No setting floor (F78)
            lastChangeMs = now
        } else {
            val since = lastChangeMs ?: now.also { lastChangeMs = it }
            if (now - since > idleMs) throttleMs = ceilingMs // task566 act7: stop polling
        }
    }
}
