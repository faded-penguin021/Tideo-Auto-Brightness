package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.WriteStatus
import kotlinx.coroutines.delay

/** The result of one animated transition, carrying what the caller needs to attribute it (DC-004). */
sealed interface AnimationOutcome {
    /** The latest ACKNOWLEDGED frame write; null only if no frame was acknowledged. */
    val lastAcknowledged: BrightnessWriteResult?

    data class Completed(override val lastAcknowledged: BrightnessWriteResult?) : AnimationOutcome

    data class Overridden(
        override val lastAcknowledged: BrightnessWriteResult?,
        /** The read that tripped the two-read detector (domain), not a later re-read. */
        val triggerObserved: Int,
    ) : AnimationOutcome
}

/** Animated brightness transition (task696, task698) with per-frame band-checked override detection.
 * Band spans sweep ±2 tolerance (absorbs round-trip drift, D-049 #4); requires 2 consecutive out-of-band
 * reads to trigger. Unit-testable (no Android deps beyond ScreenBrightnessController). */
// `open` for test double injection (D-126 settle-window detectOverrides gating).
open class AnimationRunner(
    private val controller: ScreenBrightnessController,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    /** Animate brightness from [from] to [to] over [steps] frames, [waitMs] apart. */
    open suspend fun animate(
        from: Int,
        to: Int,
        steps: Int,
        waitMs: Long,
        detectOverrides: Boolean,
    ): AnimationOutcome {
        val frames = steps.coerceAtLeast(1)
        val minTarget = if (from < to) from else to - 1
        val maxTarget = if (from < to) to + 1 else from
        var consecutiveOutOfBounds = 0
        var lastAcknowledged: BrightnessWriteResult? = null
        for (i in 1..frames) {
            // Band check from frame 2 onward (skip frame 1: our write hasn't landed yet).
            if (detectOverrides && i > 1) {
                val observed = controller.read()
                if (isForeignRead(observed, minTarget, maxTarget, lastAcknowledged)) {
                    consecutiveOutOfBounds += 1
                    if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) {
                        return AnimationOutcome.Overridden(lastAcknowledged, observed)
                    }
                } else {
                    consecutiveOutOfBounds = 0
                }
            }
            val frame = if (i == frames) to else from + ((to - from) * i) / frames
            val result = controller.write(frame)
            // DC-004: only an ACKNOWLEDGED frame says anything about what is on screen.
            if (result.status == WriteStatus.ACKNOWLEDGED) lastAcknowledged = result
            if (waitMs > 0) sleep(waitMs)
        }
        // Final read-back after last frame's wait.
        if (detectOverrides) {
            val observed = controller.read()
            if (isForeignRead(observed, minTarget, maxTarget, lastAcknowledged)) {
                consecutiveOutOfBounds += 1
                if (consecutiveOutOfBounds >= OVERRIDE_TRIGGER_THRESHOLD) {
                    return AnimationOutcome.Overridden(lastAcknowledged, observed)
                }
            }
        }
        return AnimationOutcome.Completed(lastAcknowledged)
    }

    // Out of band: overshoots either end by more than ±2 tolerance (task696 java L126). DC-004 ANDs in
    // an exact match against the acknowledged frame — safe with no tolerance, since a device that
    // stored our frame and left it alone reports toDomain of the very raw the acknowledgement holds.
    private fun isForeignRead(
        observed: Int,
        minTarget: Int,
        maxTarget: Int,
        acknowledged: BrightnessWriteResult?,
    ): Boolean {
        val outOfBand = observed > maxTarget + 2 || observed < minTarget - 2
        return outOfBand && observed != acknowledged?.acknowledgedDomain
    }

    private companion object {
        const val OVERRIDE_TRIGGER_THRESHOLD = 2
    }
}
