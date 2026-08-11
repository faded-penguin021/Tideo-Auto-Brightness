package com.tideo.autobrightness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tideo.autobrightness.R

/** S13c' typographic pass: precision-instrument styling with IBM Plex Sans (interface) + Mono (numeric).
 * Two named roles [AabDataDisplay]/[AabDataCaption] for readout figure + caption (see S13c' spec §03, §05). */

/** IBM Plex Sans: interface text. */
val AabSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

/** IBM Plex Mono: numeric readouts with tabular figures. */
val AabMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private val baseline = Typography()

/** Data readout role (S13c' §05): Plex Mono Medium, tabular, 26 sp. Colour applied by call site. */
val AabDataDisplay = TextStyle(
    fontFamily = AabMono,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum", // tabular: digits stay same width
)

/** Readout caption (S13c' §03/§05): Plex Mono, tracked-out, small. Sits above value ("SMOOTHED LUX"). */
val AabDataCaption = TextStyle(
    fontFamily = AabMono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.6.sp, // tracked-out
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
