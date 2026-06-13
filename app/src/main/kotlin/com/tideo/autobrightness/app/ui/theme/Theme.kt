package com.tideo.autobrightness.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme: Material 3 with dynamic (Material You) color and automatic day/night. minSdk is 31, so
 * `dynamicLightColorScheme`/`dynamicDarkColorScheme` are always available — no static fallback
 * palette is needed (D-027g: Compose M3 generates its own theming; the XML theme is now just a
 * launch-window placeholder).
 */
@Composable
fun TideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
