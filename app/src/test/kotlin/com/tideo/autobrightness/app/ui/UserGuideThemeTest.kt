package com.tideo.autobrightness.app.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.children
import com.tideo.autobrightness.app.ui.screens.UserGuideContent
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UserGuideThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightTheme_appliesToHtmlAndWebViewBackground() {
        render(mutableStateOf(false))
        compose.runOnIdle { assertPalette(webView(), false) }
    }

    @Test
    fun darkTheme_appliesToHtmlAndWebViewBackground() {
        render(mutableStateOf(true))
        compose.runOnIdle { assertPalette(webView(), true) }
    }

    @Test
    fun themeChanges_updateTheExistingWebViewInBothDirections() {
        val dark = mutableStateOf(false)
        render(dark)
        lateinit var initialView: WebView
        compose.runOnIdle {
            initialView = webView()
            assertPalette(initialView, false)
            dark.value = true
        }
        compose.runOnIdle {
            assertSame(initialView, webView())
            assertPalette(initialView, true)
            dark.value = false
        }
        compose.runOnIdle {
            assertSame(initialView, webView())
            assertPalette(initialView, false)
        }
    }

    private fun render(dark: State<Boolean>) {
        compose.setContent {
            TideoTheme(darkTheme = dark.value) { UserGuideContent(onBack = {}) }
        }
    }

    private fun webView(): WebView = checkNotNull(
        compose.activity.findViewById<View>(android.R.id.content).findWebView(),
    )

    private fun View.findWebView(): WebView? =
        if (this is WebView) this else (this as? ViewGroup)?.children?.firstNotNullOfOrNull { it.findWebView() }

    private fun assertPalette(view: WebView, dark: Boolean) {
        val background = if (dark) "#333333" else "#f6f8f7"
        val foreground = if (dark) "#ececec" else "#1a1c1b"
        val scheme = if (dark) "dark" else "light"
        val shadow = shadowOf(view)
        assertEquals(Color.parseColor(background), shadow.backgroundColor)
        val html = checkNotNull(shadow.lastLoadDataWithBaseURL).data.lowercase(Locale.ROOT)
        assertTrue(html.contains("color-scheme: $scheme;"), "HTML color scheme should follow the theme")
        assertTrue(html.contains("background:$background; color:$foreground;"), "HTML body should follow the theme")
        if (dark) {
            val originalColors = mapOf(
                ("h2" to "color") to "#00a986",
                ("strong, b" to "color") to "#ffc107",
                ("blockquote" to "color") to "#cfeee6",
                ("blockquote" to "background") to "#2e3633",
                (".outro" to "color") to "#00c79e",
                (".tip" to "background") to "#26302e",
                (".tip .lead" to "color") to "#00c79e",
                (".warn" to "background") to "#3a2b2a",
                (".warn .lead" to "color") to "#ff8a80",
                (".warn strong, .warn b" to "color") to "#ff8a80",
            )
            originalColors.forEach { (property, expected) ->
                assertEquals(expected, cssProperty(html, property.first, property.second), property.toString())
            }
        } else {
            val gold = cssProperty(html, "strong, b", "color")
            val tip = cssProperty(html, ".tip .lead", "color")
            val warning = cssProperty(html, ".warn .lead", "color")
            val goldRgb = Color.parseColor(gold)
            assertTrue(Color.red(goldRgb) >= 128 && Color.red(goldRgb) > Color.green(goldRgb) && Color.blue(goldRgb) < 32,
                "Emphasis should remain visibly golden")
            assertGreen(tip)
            assertGreen(cssProperty(html, "blockquote", "background"))
            assertGreen(cssProperty(html, ".tip", "background"))
            val warningRgb = Color.parseColor(warning)
            assertTrue(Color.red(warningRgb) >= 128 && Color.red(warningRgb) > 2 * Color.green(warningRgb),
                "Warning should keep its coral color")
            val warningBackground = cssProperty(html, ".warn", "background")
            val warningBackgroundRgb = Color.parseColor(warningBackground)
            assertTrue(Color.red(warningBackgroundRgb) - Color.green(warningBackgroundRgb) >= 10 &&
                Color.red(warningBackgroundRgb) - Color.blue(warningBackgroundRgb) >= 10,
                "Warning background should retain its coral tint")
            assertTrue(contrast(gold, background) >= 4.5f, "Gold emphasis should be readable")
            assertTrue(contrast(tip, cssProperty(html, ".tip", "background")) >= 4.5f, "Tip label should be readable")
            assertTrue(contrast(warning, warningBackground) >= 4.5f, "Warning label should be readable")
        }
        assertFalse(view.settings.javaScriptEnabled)
    }

    private fun cssProperty(html: String, selector: String, property: String): String {
        val block = checkNotNull(Regex("${Regex.escape(selector)}\\s*\\{([^}]+)}").find(html)).groupValues[1]
        return block.split(';').map { it.trim().split(':', limit = 2) }
            .first { it.size == 2 && it[0] == property }[1].trim()
    }

    private fun assertGreen(hex: String) {
        val rgb = Color.parseColor(hex)
        assertTrue(Color.green(rgb) - Color.red(rgb) >= 10 && Color.green(rgb) - Color.blue(rgb) >= 3,
            "$hex should have a green tint")
    }

    private fun contrast(foreground: String, background: String): Float {
        val front = Color.luminance(Color.parseColor(foreground))
        val back = Color.luminance(Color.parseColor(background))
        return (max(front, back) + 0.05f) / (min(front, back) + 0.05f)
    }
}
