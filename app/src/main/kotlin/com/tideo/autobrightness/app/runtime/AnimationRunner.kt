package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlinx.coroutines.delay

/**
 * Executes an animated brightness transition with per-frame read-back override detection.
 *
 * Tasker: task696 "Smooth Brightness Transition" (XML, java/task696_1) and the DC-like dimming
 * variant task698. Steps `loops` times writing an intermediate brightness with `wait` ms between
 * frames; before each frame it re-reads the system value and, if it drifted away from our last
 * self-write while override detection is on, aborts and signals a manual override
 * (→ prof755/task567). Our own writes are expected (suppress-echo); an unexpected external write
 * is an override.
 *
 * Super-dimming (task698) writes are wired in S9b; this S9a runner handles the brightness-only
 * path (task696). It is a plain suspend function with no Android dependencies beyond the
 * ScreenBrightnessController interface, so it is unit-testable with a fake controller.
 */
class AnimationRunner(
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
     * @param detectOverrides %AAB_DetectOverrides — when false, read-back checks are skipped.
     * @return COMPLETED, or OVERRIDDEN if an external write was detected mid-animation.
     */
    suspend fun animate(
        from: Int,
        to: Int,
        steps: Int,
        waitMs: Long,
        detectOverrides: Boolean,
    ): Result {
        val frames = steps.coerceAtLeast(1)
        for (i in 1..frames) {
            // task696: re-read before writing the next frame; if the on-screen value is no longer
            // our last self-write, an external app/user changed it → abort + override. The check is
            // device-exact ([isOnScreenSelfWrite]) so OEM ranges where read() ≠ identity do not fire
            // a spurious override on every frame (D-049 #4).
            if (detectOverrides && i > 1) {
                if (!controller.isOnScreenSelfWrite()) return Result.OVERRIDDEN
            }
            // Linear interpolation; the final frame lands exactly on `to`.
            val frame = if (i == frames) to else from + ((to - from) * i) / frames
            controller.write(frame)
            if (waitMs > 0) sleep(waitMs)
        }
        // Final read-back: catch an override that landed during the last frame's wait.
        if (detectOverrides) {
            if (!controller.isOnScreenSelfWrite()) return Result.OVERRIDDEN
        }
        return Result.COMPLETED
    }
}
