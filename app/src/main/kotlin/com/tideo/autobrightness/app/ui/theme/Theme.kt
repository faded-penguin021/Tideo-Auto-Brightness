package com.tideo.autobrightness.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme: the AAB **teal + gold** brand identity (S12.5a, replacing S11's dynamic Material-You
 * scheme — Gate-2 G2-F18: "the app does not look or feel like AAB"). Colours are derived from the
 * Tasker scenes (see [Color.kt] for per-value provenance), NOT invented.
 *
 * Dynamic colour is OFF by default so the brand identity is stable across devices; it is left as an
 * opt-in [dynamicColor] flag per the brief. DayNight is kept: the dark scheme is the faithful one
 * (Tasker is dark-first) and the light scheme is a derived courtesy.
 */
@Composable
fun TideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AabDarkColorScheme
        else -> AabLightColorScheme
    }
    // S13a: wire the design-system foundation — the explicit AAB type scale (Type.kt) and the M3
    // shape scale (Shape.kt). Both equal the values the app already rendered (behaviour-preserving);
    // they give S13b/S13c a single owned place to tune typography emphasis and corner radii.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AabTypography,
        shapes = AabShapes,
        content = content,
    )
}

/** Dark-first AAB scheme: charcoal surfaces, teal primary, gold secondary. */
private val AabDarkColorScheme = darkColorScheme(
    primary = AabTeal,
    onPrimary = AabOnTeal,
    primaryContainer = AabTeal,
    onPrimaryContainer = AabOnTeal,
    secondary = AabGold,
    onSecondary = AabOnGold,
    secondaryContainer = AabPanelDark,
    onSecondaryContainer = AabOnDark,
    tertiary = AabTealAccent,
    onTertiary = AabOnTeal,
    background = AabBackgroundDark,
    onBackground = AabOnDark,
    surface = AabSurfaceDark,
    onSurface = AabOnDark,
    surfaceVariant = AabPanelDark,
    onSurfaceVariant = AabOnDark,
    error = AabError,
    onError = AabOnTeal,
)

/** Derived light scheme (kept muted so teal/gold remain the identity). */
private val AabLightColorScheme = lightColorScheme(
    primary = AabTeal,
    onPrimary = AabOnTeal,
    primaryContainer = AabTealAccent,
    onPrimaryContainer = AabOnTeal,
    secondary = AabGold,
    onSecondary = AabOnGold,
    secondaryContainer = AabGold,
    onSecondaryContainer = AabOnGold,
    tertiary = AabTealAccent,
    onTertiary = AabOnTeal,
    background = AabBackgroundLight,
    onBackground = AabOnLight,
    surface = AabSurfaceLight,
    onSurface = AabOnLight,
    surfaceVariant = AabSurfaceVariantLight,
    onSurfaceVariant = AabOnLight,
    error = AabError,
    onError = AabOnTeal,
)
