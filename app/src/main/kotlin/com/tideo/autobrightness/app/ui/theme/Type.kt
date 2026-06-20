package com.tideo.autobrightness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tideo.autobrightness.R

/**
 * S13a design-system foundation → **S13c' typographic pass**.
 *
 * The biggest untapped "wow" lever (S13c' spec §03): Tideo is a deterministic-math glass box for screen
 * brightness, so it is typeset like a precision instrument rather than a settings menu. Two bundled,
 * intentional families replace `FontFamily.Default`:
 *  - **[AabSans]** — IBM Plex Sans, for all interface text (labels, switches, headings). Reads as
 *    precise engineering, not consumer-app default.
 *  - **[AabMono]** — IBM Plex Mono, for **every numeric readout**. Its figures are tabular (every digit
 *    the same width), so live values don't jitter as they tick.
 *
 * Both are SIL-OFL (bundled `.ttf` in `res/font` — a static asset, not a gradle dependency; licence in
 * `docs/licenses/IBM-Plex-OFL.txt`). The palette is frozen; this pass is type, layout, depth and motion.
 *
 * Semantic role mapping (see also `docs/rebuild/design/m3_audit.md`):
 *  - **titleLarge** — Plex Sans **SemiBold** (S13c' bump: the one weight change that makes headings feel
 *    authored) — hero card titles, prominent headings.
 *  - **titleMedium** — Plex Sans Medium — `SectionHeader` (primary-tinted group labels).
 *  - **bodyLarge / bodyMedium** — Plex Sans Normal — field labels, switch/slider text, secondary text.
 *  - **bodySmall** — Plex Sans Normal — helper/validation/long-press-help supporting text.
 *  - **labelLarge** — Plex Sans Medium — button text.
 *
 * Plus two named instrument roles ([AabDataDisplay], [AabDataCaption]) layered on top of the M3 scale —
 * the readout figure + its tracked caption — so the data-pop lands everywhere at once (KeyValueRow,
 * DiagnosticCard, the Dashboard hero). See the S13c' spec §03 role map.
 */

/** IBM Plex Sans — the interface family. */
val AabSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

/** IBM Plex Mono — the readout family (true tabular figures). */
val AabMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private val baseline = Typography()

/**
 * The instrument **data readout** role (S13c' §05): Plex Mono Medium, tabular, ~26 sp — every live
 * number renders in this so the figures are the loudest, calmest thing on screen. Colour is applied by
 * the call site (gold for live data, near-white for the Dashboard hero); this role owns size + family.
 */
val AabDataDisplay = TextStyle(
    fontFamily = AabMono,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum", // tabular figures: digits don't shift width as values tick
)

/**
 * The readout **caption** role (S13c' §03/§05): Plex Mono, tracked-out uppercase, small — the label that
 * sits *above* a value ("SMOOTHED LUX"). Rendered in `onSurfaceVariant` by the call site.
 */
val AabDataCaption = TextStyle(
    fontFamily = AabMono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.6.sp, // tracked-out, instrument-panel feel
)

val AabTypography = baseline.copy(
    titleLarge = baseline.titleLarge.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = baseline.titleMedium.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = baseline.titleSmall.copy(fontFamily = AabSans),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = AabSans),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = AabSans),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = AabSans),
    bodyLarge = baseline.bodyLarge.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = baseline.bodyMedium.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = baseline.bodySmall.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = baseline.labelLarge.copy(
        fontFamily = AabSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = baseline.labelMedium.copy(fontFamily = AabSans),
    labelSmall = baseline.labelSmall.copy(fontFamily = AabSans),
)
