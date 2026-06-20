package com.tideo.autobrightness.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * S13a design-system foundation — the single source of truth for spacing & sizing.
 *
 * Before S13 the screens scattered ad-hoc `dp` literals (2/4/6/8/10/12/16/20/24 — see
 * `docs/rebuild/design/m3_audit.md`). This token set replaces them with an intent-named scale so the
 * S13b component library and the S13c restyle read declaratively and stay internally consistent.
 *
 * Two layers:
 *  - **[space1]..[space7]** — the raw 4 dp grid (plus the single 2 dp sub-step the Tasker-port
 *    settings fields already used for tight vertical rhythm). Use these when no semantic token fits.
 *  - **semantic tokens** — named for *purpose* (page gutter, field spacing, card padding…). Each maps
 *    onto the grid; screens reference these so a later spacing decision changes one value, not 30.
 *
 * Guardrail (S13 brief): values chosen here are **behaviour-preserving** — every semantic token equals
 * the literal it replaces, so wiring it into a screen is a no-op visually. S13c may re-tune individual
 * semantic tokens (e.g. normalising the off-grid 10 dp [fieldSpacing]); that is a single-line change here.
 */
object Dimens {
    // --- Raw spacing scale (4 dp grid + one 2 dp sub-step) ---
    val space1 = 2.dp
    val space2 = 4.dp
    val space3 = 8.dp
    val space4 = 12.dp
    val space5 = 16.dp
    val space6 = 24.dp
    val space7 = 32.dp

    // --- Semantic spacing ---
    /** Horizontal page gutter for every scrollable screen body (was the scattered `16.dp`). */
    val screenPaddingHorizontal = space5
    /** Vertical page padding for the Menu hub's content column (was `12.dp`). */
    val screenPaddingVertical = space4
    /** Vertical rhythm between stacked settings fields in [com.tideo.autobrightness.app.ui.components.SettingsColumn].
     *  Off-grid 10 dp carried over from S12.5b's draft-edit screens; flagged in m3_audit.md for S13c review. */
    val fieldSpacing = 10.dp
    /** Gap between grouped menu rows / section blocks (was `8.dp`). */
    val sectionSpacing = space3
    /** Tight vertical padding inside a single field row (slider/derived readout, was `2.dp`). */
    val fieldRowPaddingTight = space1
    /** Standard inter-control gap inside a row (button bar, hero row, was `12.dp`/`16.dp` — see [rowGapWide]). */
    val rowGap = space4
    val rowGapWide = space5

    // --- Cards / containers ---
    /** Inner padding for the standard `AabCard` (S13b) and section containers (was `16.dp`). */
    val cardPadding = space5
    /** Inner padding for the prominent Menu hero cards (was `20.dp`). */
    val heroCardPadding = 20.dp
    /** Resting elevation for content cards. */
    val cardElevation = 1.dp
    /** Raised elevation for the sticky Apply bar / overlays (was the `tonalElevation = 3.dp`). */
    val cardElevationRaised = 3.dp

    // --- Components ---
    val iconSize = space6
    /** Minimum interactive target (Material accessibility floor). */
    val touchTarget = 48.dp
    val dividerThickness = 1.dp
    /** Default rendered height for the S13d chart slots / `ChartCanvas` hosts. */
    val chartHeight = 200.dp
}
