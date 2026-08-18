package com.tideo.autobrightness.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AAB brand palette — teal + gold. All values from Tasker scenes, not invented (S12.5a).
 * Dark scheme is faithful; light scheme is derived courtesy (DayNight per brief).
 * See extraction/scenes/about.md for detailed provenance.
 */

// --- Primary teal family ---
val AabTeal = Color(0xFF007C63)        // banner / primary / all "on" indicator dots
val AabTealAccent = Color(0xFF00A986)  // lighter accent heading (about.md)
val AabTealLink = Color(0xFF00C79E)    // bright link / hyperlink text (about.md)

// --- Gold / amber family ---
val AabGold = Color(0xFFFFC107)        // "strong" accent, warnings, chart current-series

// Chart.js default blue (rgb(54, 162, 235)); Tasker brightness graph curve + override scatter + fit.
val AabChartBlue = Color(0xFF36A2EB)

// --- Neutral surfaces (dark-first, from the scene backgrounds) ---
val AabBackgroundDark = Color(0xFF333333) // scene bg (about.md)
val AabSurfaceDark = Color(0xFF383838)    // card / license box (about.md)
val AabPanelDark = Color(0xFF404040)      // decorative card/panel bg across settings scenes
val AabOnDark = Color(0xFFECECEC)         // legible light text on the charcoal surfaces

// --- Light-scheme neutrals (derived; not in the extraction, kept muted so teal/gold dominate) ---
val AabBackgroundLight = Color(0xFFF6F8F7)
val AabSurfaceLight = Color(0xFFFFFFFF)
val AabSurfaceVariantLight = Color(0xFFDCE5E1)
val AabOnLight = Color(0xFF1A1C1B)

// --- Shared semantics ---
val AabError = Color(0xFFD32F2F)       // invalid-field red (the _RedInvalidFormulae family)
val AabOnTeal = Color(0xFFFFFFFF)
val AabOnGold = Color(0xFF2A2000)      // dark text on the gold accent for contrast
