package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.domain.wizard.CurveSuggestionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transient, process-scoped bridge for a curve-suggestion **preview**, mirroring Tasker's global
 * `%suggestion_*` vars: task38 (the wizard) writes them on a **user** run; the Brightness Graph scene
 * reads them to draw the suggested line; task655 applies them to the live `%AAB_*` curve only on a
 * separate, user-confirmed step. They are NOT the live curve and are NOT persisted.
 *
 * Flow here: the Tools wizard [request]s a preview (the user tapped "Preview graph" on a successful
 * fit) and navigates to Curve & Brightness, which [consume]s it once into its editable **draft** — so
 * the suggested values populate the input fields (the current values move to the `[brackets]`) and the
 * chart's live curve traces the fit. Leaving the screen discards that draft (the per-screen
 * `DraftSettingsViewModel` is NavBackStackEntry-scoped), so the suggested line disappears on close —
 * exactly Tasker's preview→Apply lifecycle.
 *
 * Deliberately NOT auto-driven (D-125): a fit is shown only because the user ran the wizard and chose
 * to preview it — never merely because ≥ 9 override points exist (the old auto-fit behaviour).
 */
object CurveSuggestionPreview {
    private val _pending = MutableStateFlow<CurveSuggestionResult?>(null)

    /** The fit awaiting preview on the next Curve & Brightness visit, or null. */
    val pending: StateFlow<CurveSuggestionResult?> = _pending.asStateFlow()

    /** The wizard asks that the next Curve & Brightness visit preview [result] in its draft. */
    fun request(result: CurveSuggestionResult) { _pending.value = result }

    /** Drop the pending preview (after it is consumed, or to discard a stale request). */
    fun clear() { _pending.value = null }
}
