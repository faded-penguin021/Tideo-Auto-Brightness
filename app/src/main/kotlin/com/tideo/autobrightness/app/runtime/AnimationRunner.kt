package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlinx.coroutines.delay

/** Animated brightness transition (task696, task698) with per-frame band-checked override detection.
 * Band spans sweep ±2 tolerance (absorbs round-trip drift, D-049 #4); requires 2 consecutive out-of-band
 * reads to trigger. Unit-testable (no Android deps beyond ScreenBrightnessController). */
// `open` for test double injection (D-126 settle-window detectOverrides gating).
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

    /** Animate brightness from [from] to [to] over [steps] frames, [waitMs] apart. Returns COMPLETED or
     * OVERRIDDEN if detectOverrides finds an external write. */
    open suspend fun animate(
        from: Int,
        to: Int,
        steps: Int,
        waitMs: Long,
        detectOverrides: Boolean,
    ): Result {
        val frames = steps.coerceAtLeast(1)
        val minTarget = if (from < to) from else to - 1
        val maxTarget = if (from < to) to + 1 else from
        var consecutiveOutOfBounds = 0
        for (i in 1..frames) {
            // Band check from frame 2 onward (skip frame 1: our write hasn't landed yet).
            if (detectOverrides && i > 1) {
                consecutiveOutOfBounds = if (isOutOfBand(minTarget, maxTarget)) consecutiveOutOfBounds + 1 else 0
                if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) return Result.OVERRIDDEN
            }
            val frame = if (i == frames) to else from + ((to - from) * i) / frames
            controller.write(frame)
            if (waitMs > 0) sleep(waitMs)
        }
        // Final read-back after last frame's wait.
        if (detectOverrides && isOutOfBand(minTarget, maxTarget)) {
            consecutiveOutOfBounds += 1
            if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) return Result.OVERRIDDEN
        }
        return Result.COMPLETED
    }

    // Out of band: overshoots either end by more than ±2 tolerance (task696 java L126).
    private fun isOutOfBand(minTarget: Int, maxTarget: Int): Boolean {
        val actual = controller.read()
        return actual > maxTarget + 2 || actual < minTarget - 2
    }

    private companion object {
        const val OVERRIDE_TRIGGER_THRESHOLD = 2
    }
}
