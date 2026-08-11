package com.tideo.autobrightness.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** S13a M3 shape scale (S13 brief: behaviour-preserving, matches M3 defaults). Role mapping per m3_audit.md. */
val AabShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, small inline affordances
    small = RoundedCornerShape(8.dp),        // text fields, buttons
    medium = RoundedCornerShape(12.dp),      // standard AabCard / section containers
    large = RoundedCornerShape(16.dp),       // hero cards, dialogs, bottom sheets
    extraLarge = RoundedCornerShape(28.dp),  // full-bleed banners / prominent surfaces
)
