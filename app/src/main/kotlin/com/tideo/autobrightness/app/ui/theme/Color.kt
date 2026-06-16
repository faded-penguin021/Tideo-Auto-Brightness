package com.tideo.autobrightness.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Advanced Auto Brightness (AAB) brand palette — **teal + gold**.
 *
 * Every value is lifted directly from the Tasker project's scenes, not invented (S12.5a; CLAUDE.md
 * "port behaviour exactly"). Provenance:
 *   - extraction/scenes/about.md L51 (the most explicit theme statement): "bg #333333, banner
 *     #007C63, accent headings #007C63/#00A986, links #00C79E, strong #FFC107, license box #383838".
 *   - #007C63 (teal) is also every "on" indicator dot (brightness_settings.md, reactivity_settings.md,
 *     superdimming_settings.md — "#FF007C63"), the curve-wizard Flash overlay (task038 L26, task090
 *     act48), and the power-draw chart's power series (power_draw_graph.md L35).
 *   - #FFC107 (gold/amber) is the "strong" accent + the invalid-formula red/gold field highlight
 *     (features_spec.md L179) + the power-draw current series (power_draw_graph.md L36).
 *   - #404040 ("#FF404040") is the decorative card/panel background used across the settings scenes
 *     (brightness_settings.md L27, misc_settings.md L28, reactivity_settings.md L27, etc.).
 *
 * The Tasker UI is dark-first (black/charcoal scenes with teal + gold accents); the dark scheme is
 * therefore the faithful one and the light scheme is a derived courtesy (DayNight kept per the brief).
 */

// --- Primary teal family ---
val AabTeal = Color(0xFF007C63)        // banner / primary / all "on" indicator dots
val AabTealAccent = Color(0xFF00A986)  // lighter accent heading (about.md)
val AabTealLink = Color(0xFF00C79E)    // bright link / hyperlink text (about.md)

// --- Gold / amber family ---
val AabGold = Color(0xFFFFC107)        // "strong" accent, warnings, chart current-series

// Chart.js default dataset[0] blue — `rgb(54, 162, 235)`, the `Yo[0]` palette entry the Tasker
// brightness graph (task663 `_GenerateGraph`) uses for the curve + the override scatter + the
// suggested fit. Gate-2(5th) obs: the rebuild rendered those gold; this restores the Tasker blue.
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
