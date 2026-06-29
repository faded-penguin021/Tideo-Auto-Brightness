package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * Transient, process-scoped bridge for a curve-suggestion **preview**, mirroring Tasker's global
 * `%suggestion_*` vars: task38 (the wizard) writes them on a **user** run; the Brightness Graph scene
 * reads them to draw the suggested line; task655 applies them to the live `%AAB_*` curve only on a
 * separate, user-confirmed step. They are NOT the live curve and are NOT persisted.
 *
 * Flow here: the Tools wizard [request]s a preview (the user tapped "Preview graph" on a successful
 * fit) — handing over an opaque draft transform that maps the current curve → the suggested one — and
 * navigates to Curve & Brightness. That screen's freshly-created [DraftSettingsViewModel] [consume]s it
 * **as part of its initial seed**, so the suggested values populate the input fields (the current
 * values move to the `[brackets]`) and the chart's live curve traces the fit, all on the same atomic
 * seed that already populates the seed-once fields (avoids the mid-life epoch/draft staleness that left
 * the fields showing the committed values). Leaving the screen discards that draft (the per-screen VM
 * is NavBackStackEntry-scoped), so the suggested line disappears on close — Tasker's preview→Apply
 * lifecycle.
 *
 * The transform is an opaque `(AabSettings) -> AabSettings` so this holder (and the generic VM that
 * applies it) stay decoupled from the wizard engine — the curve-specific math lives at the call site
 * (ToolsScreen). [consume] is one-shot (atomically clears), and the holder is only ever [request]ed en
 * route to Curve & Brightness, so its VM is the next to seed.
 *
 * Deliberately NOT auto-driven (D-125): a fit is shown only because the user ran the wizard and chose
 * to preview it — never merely because ≥ 9 override points exist (the old auto-fit behaviour).
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
