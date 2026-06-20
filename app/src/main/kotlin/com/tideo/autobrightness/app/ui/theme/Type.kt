package com.tideo.autobrightness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * S13a design-system foundation — the app's finalized type scale, wired into [TideoTheme].
 *
 * The app uses the default-font M3 type scale; this file makes the scale **owned and explicit** rather
 * than relying on `Typography()`'s implicit defaults, so the S13c restyle has a named place to tune
 * emphasis (and so the per-role intent is documented). The roles the UI actually renders today —
 * `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge` — are restated
 * explicitly with their standard M3 metrics; every other role keeps the M3 default (via [baseline]).
 *
 * Semantic role mapping (see also `docs/rebuild/design/m3_audit.md`):
 *  - **titleLarge** — hero card titles, screen-level prominent headings.
 *  - **titleMedium** — `SectionHeader` (primary-tinted group labels).
 *  - **bodyLarge** — field labels, switch/slider primary text.
 *  - **bodyMedium** — derived readouts, hero subtitles, list secondary text.
 *  - **bodySmall** — helper/validation/long-press-help supporting text.
 *  - **labelLarge** — button text (M3 default for `Button`/`TextButton`).
 *
 * Guardrail (S13 brief): values equal the M3 defaults → behaviour-preserving. S13c owns any emphasis
 * change (e.g. bumping section headers to SemiBold); make it here so it lands everywhere at once.
 */
private val baseline = Typography()

val AabTypography = baseline.copy(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
