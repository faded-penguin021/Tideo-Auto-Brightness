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
 * AAB teal + gold brand identity (S12.5a, G2-F18). Colours from Tasker scenes (Color.kt).
 * Dynamic colour OFF by default for stable identity; DayNight kept (dark-first, per Tasker).
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
    // S13a: wire design-system foundation (Type.kt type scale, Shape.kt shapes) for S13b/S13c tuning.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AabTypography,
        shapes = AabShapes,
        content = content,
    )
}

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
