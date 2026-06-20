package com.tideo.autobrightness.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * S13a design-system foundation — the app's M3 shape scale, wired into [TideoTheme].
 *
 * The values match the Material 3 default shape scale (extraSmall 4 / small 8 / medium 12 / large 16 /
 * extraLarge 28); formalising them here makes the scale **owned and explicit** so S13b's `AabCard` and
 * the S13c restyle reference `MaterialTheme.shapes.*` instead of ad-hoc `RoundedCornerShape(…)` literals
 * (m3_audit.md flags the flat/inconsistent corners as a wireframe gap).
 *
 * Guardrail (S13 brief): behaviour-preserving — equal to the M3 defaults the app already rendered, so
 * wiring this changes nothing today. Role mapping (which token for which component) is documented in
 * `docs/rebuild/design/m3_audit.md`.
 */
val AabShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, small inline affordances
    small = RoundedCornerShape(8.dp),        // text fields, buttons
    medium = RoundedCornerShape(12.dp),      // standard AabCard / section containers
    large = RoundedCornerShape(16.dp),       // hero cards, dialogs, bottom sheets
    extraLarge = RoundedCornerShape(28.dp),  // full-bleed banners / prominent surfaces
)
