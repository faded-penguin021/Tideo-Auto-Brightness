package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * Transient, process-scoped bridge for a curve-suggestion **preview**, mirroring Tasker's
 * `%suggestion_*` globals: task38 writes them on a user run, the graph scene draws the suggested
 * line, task655 applies them to the live `%AAB_*` curve only on a separate confirmed step. NOT the
 * live curve and NOT persisted.
 * The wizard [request]s a preview with an opaque `(AabSettings) -> AabSettings` transform, keeping
 * this holder and the generic VM decoupled from the wizard engine. Curve & Brightness's fresh
 * [DraftSettingsViewModel] [consume]s it (one-shot) **as part of its initial seed**, so the values
 * land on the same atomic seed as the seed-once fields — which is what avoids the mid-life draft
 * staleness that left the fields showing committed values. Leaving the screen discards it.
 * D-125: shown only on an explicit user preview, never auto-fitted from ≥ 9 override points.
 */
object CurveSuggestionPreview {
    private val pending = AtomicReference<((AabSettings) -> AabSettings)?>(null)

    /** The wizard asks that the next Curve & Brightness draft seed apply [transform] (the fit). */
    fun request(transform: (AabSettings) -> AabSettings) { pending.set(transform) }

    /** Take the pending transform exactly once (atomically clearing it), or null if none. */
    fun consume(): ((AabSettings) -> AabSettings)? = pending.getAndSet(null)

    /** Drop any pending preview (e.g. to discard a stale request). */
    fun clear() { pending.set(null) }
}
