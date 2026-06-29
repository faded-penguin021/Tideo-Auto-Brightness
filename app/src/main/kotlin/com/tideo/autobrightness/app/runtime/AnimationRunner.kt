package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlinx.coroutines.delay

/**
 * Executes an animated brightness transition with per-frame band-checked override detection.
 *
 * Tasker: task696 "Smooth Brightness Transition V5 (Java)" (XML L35734-L35886, java/task696_1) and
 * the DC-like dimming variant task698. Steps `loops` times writing an intermediate brightness with
 * `wait` ms between frames; while [detectOverrides] is on it re-reads the system value and aborts
 * with [Result.OVERRIDDEN] when the observed brightness leaves our animation band.
 *
 * **Override band (S12.7a, F34 — the *real* task696 logic, not the old exact-match self-write
 * check).** The legacy `isOnScreenSelfWrite()` equality test produced false positives (any OEM
 * round-trip drift / a delayed callback for an adjacent frame looked like an override). task696
 * instead defines a tolerance band spanning the whole sweep and only fires after the observed value
 * stays out of it for [OVERRIDE_TRIGGER_THRESHOLD] consecutive checks:
 *   - `minTarget = from < to ? from : to - 1`, `maxTarget = from < to ? to + 1 : from` (java L49-56).
 *   - an override is `actual > maxTarget + 2 || actual < minTarget - 2`, i.e. a value in the *wrong
 *     direction* or *overshooting beyond our step* (java L126); a value consistent with our in-flight
 *     interpolation is ours. The ±2 tolerance also absorbs the domain↔device round-trip drift that
 *     made the equality check fire on every multi-frame transition (D-049 #4).
 *   - the trigger requires two consecutive out-of-band reads (java L129) so a single transient does
 *     not pause the loop.
 *
 * Tasker checked only every 5th frame purely to dodge per-frame IPC cost (java L98-101, an explicit
 * optimization comment); that is a *how*, not a *what*, so the Kotlin port checks every frame — the
 * band + 2-consecutive-read debounce is the behaviour that matters.
 *
 * Super-dimming (task698) writes are wired in S9b; this runner handles the brightness-only path
 * (task696). It is a plain suspend function with no Android dependencies beyond the
 * ScreenBrightnessController interface, so it is unit-testable with a fake controller.
 */
// `open` so a test double can spy the per-call arguments (e.g. the D-126 settle-window
// `detectOverrides` gating) — the controller injects this collaborator.
open class AnimationRunner(
    private val controller: ScreenBrightnessController,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    enum class Result {
        /** Animation finished; the final target is on screen. */
        COMPLETED,

        /** An external write was observed mid-animation — aborted, caller should pause. */
        OVERRIDDEN,
    }

    /**
     * Animate the system brightness from [from] to [to] over [steps] frames, [waitMs] apart.
     *
     * @param from    Brightness at the start of the transition (domain 0–255).
     * @param to      Target brightness (domain 0–255).
     * @param steps   Frame count (task543 %loops, ≥1).
     * @param waitMs  Per-frame delay (task543 %wait).
     * @param detectOverrides %AAB_DetectOverrides — when false, the band checks are skipped.
     * @return COMPLETED, or OVERRIDDEN if an external write was detected mid-animation.
     */
    open suspend fun animate(
        from: Int,
        to: Int,
        steps: Int,
        waitMs: Long,
        detectOverrides: Boolean,
    ): Result {
        val frames = steps.coerceAtLeast(1)
        // task696 java L49-56: the band the on-screen value is expected to stay within as we sweep.
        val minTarget = if (from < to) from else to - 1
        val maxTarget = if (from < to) to + 1 else from
        var consecutiveOutOfBounds = 0
        for (i in 1..frames) {
            // Re-read before writing the next frame; an out-of-band value for two consecutive checks
            // is a genuine external override (task696 java L121-137). Skipped on the first frame —
            // our own write has not landed yet, so there is nothing of ours to compare against.
            if (detectOverrides && i > 1) {
                consecutiveOutOfBounds = if (isOutOfBand(minTarget, maxTarget)) consecutiveOutOfBounds + 1 else 0
                if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) return Result.OVERRIDDEN
            }
            // Linear interpolation; the final frame lands exactly on `to`.
            val frame = if (i == frames) to else from + ((to - from) * i) / frames
            controller.write(frame)
            if (waitMs > 0) sleep(waitMs)
        }
        // Final read-back: catch an override that landed during the last frame's wait. The settled
        // value sitting out of band counts as the next consecutive read.
        if (detectOverrides && isOutOfBand(minTarget, maxTarget)) {
            consecutiveOutOfBounds += 1
            if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) return Result.OVERRIDDEN
        }
        return Result.COMPLETED
    }

    // task696 java L126: the observed brightness is out of band when it overshoots either end of the
    // sweep by more than the ±2 tolerance (wrong direction, or beyond our current step).
    private fun isOutOfBand(minTarget: Int, maxTarget: Int): Boolean {
        val actual = controller.read()
        return actual > maxTarget + 2 || actual < minTarget - 2
    }

    private companion object {
        const val OVERRIDE_TRIGGER_THRESHOLD = 2
    }
}
