package com.tideo.autobrightness.app.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import java.util.Locale

/** AAB User Guide scene (Tasker: sceneAAB User Guide): static HTML manual in WebView (no JS, no network).
 * Shown from Menu Info & Help and as post-onboarding destination (G2R-F80). */
@Composable
fun UserGuideScreen(navController: NavHostController) {
    UserGuideContent(onBack = { navController.popBackStack() })
}

private val GUIDE_SECTIONS = listOf(
    R.string.guide_welcome_title to R.string.guide_welcome_body,
    R.string.guide_s1_title to R.string.guide_s1_body,
    R.string.guide_s2_title to R.string.guide_s2_body,
    R.string.guide_s3_title to R.string.guide_s3_body,
    R.string.guide_s4_title to R.string.guide_s4_body,
    R.string.guide_s5_title to R.string.guide_s5_body,
    R.string.guide_s6_title to R.string.guide_s6_body,
    R.string.guide_s7_title to R.string.guide_s7_body,
    R.string.guide_s8_title to R.string.guide_s8_body,
    R.string.guide_s9_title to R.string.guide_s9_body,
)

@Composable
fun UserGuideContent(onBack: () -> Unit) {
    val welcomeTitle = stringResource(R.string.guide_welcome_title)
    val welcomeBody = stringResource(R.string.guide_welcome_body)
    val outro = stringResource(R.string.guide_outro)
    val sections = GUIDE_SECTIONS.drop(1).map { (t, b) -> stringResource(t) to stringResource(b) }
    val colors = MaterialTheme.colorScheme
    val html = remember(welcomeTitle, welcomeBody, sections, outro, colors) {
        buildGuideHtml(welcomeTitle, welcomeBody, sections, outro, colors)
    }
    val pageBg = colors.background.toArgb()

    SettingsScaffold(stringResource(R.string.title_user_guide), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("guide_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                    }
                },
                update = {
                    it.setBackgroundColor(pageBg)
                    it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                },
            )
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("guide_done"),
            ) { Text(stringResource(R.string.guide_done)) }
        }
    }
}

/** Assemble AAB-themed HTML manual from i18n section strings. Markup: • = bullet, **x** = emphasis,
 * [TIP]/[WARN] = callout boxes (user_guide.md). */
private fun buildGuideHtml(
    welcomeTitle: String,
    welcomeBody: String,
    sections: List<Pair<String, String>>,
    outro: String,
    colors: ColorScheme,
): String {
    val dark = colors.background.luminance() < 0.5f
    val scheme = if (dark) "dark" else "light"
    val accent = if (dark) "#00a986" else "#007c63"
    val emphasis = if (dark) "#ffc107" else "#8a6500"
    val quoteText = if (dark) "#cfeee6" else "#244b40"
    val quoteBackground = if (dark) "#2e3633" else "#e8f3ee"
    val lead = if (dark) "#00c79e" else "#007c63"
    val tipBackground = if (dark) "#26302e" else "#e4f4ed"
    val warningBackground = if (dark) "#3a2b2a" else "#fff0ee"
    val warningText = if (dark) "#ff8a80" else "#b3342c"
    val divider = if (dark) "#4a4a4a" else colors.outlineVariant.cssColor()
    val body = buildString {
        // Welcome → intro blockquote (Tasker styles the welcome as a highlighted lead-in).
        append("<h2>").append(esc(welcomeTitle)).append("</h2>")
        append("<blockquote>").append(inline(welcomeBody)).append("</blockquote>")
        sections.forEach { (title, text) ->
            append("<h2>").append(esc(title)).append("</h2>")
            append(sectionBodyHtml(text))
        }
        append("<blockquote class=\"outro\">").append(inline(outro)).append("</blockquote>")
    }
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: $scheme; }
          body { background:${colors.background.cssColor()}; color:${colors.onBackground.cssColor()}; font-family:sans-serif; line-height:1.55;
                 margin:0; padding:16px 18px 28px; font-size:15px; }
          h2 { color:$accent; font-size:16px; margin:24px 0 6px; border-bottom:1px solid $divider;
               padding-bottom:4px; }
          p { margin:6px 0; }
          ul { margin:6px 0 6px 2px; padding-left:18px; }
          li { margin:5px 0; }
          strong, b { color:$emphasis; font-weight:600; }
          blockquote { border-left:3px solid #007c63; margin:8px 0; padding:8px 14px; color:$quoteText;
                       background:$quoteBackground; font-style:italic; border-radius:0 6px 6px 0; }
          .outro { color:$lead; font-weight:600; font-style:normal; }
          .tip { border-left:3px solid $accent; background:$tipBackground; padding:8px 12px; margin:8px 0;
                 border-radius:0 6px 6px 0; }
          .tip .lead { color:$lead; font-weight:600; }
          .warn { border-left:3px solid #e5534b; background:$warningBackground; padding:8px 12px; margin:8px 0;
                  border-radius:0 6px 6px 0; }
          .warn .lead { color:$warningText; font-weight:600; }
          .warn strong, .warn b { color:$warningText; }
        </style></head>
        <body>$body</body></html>
    """.trimIndent()
}

private fun Color.cssColor(): String = String.format(Locale.ROOT, "#%06x", toArgb() and 0xffffff)

private fun sectionBodyHtml(text: String): String {
    val sb = StringBuilder()
    var inList = false
    fun closeList() { if (inList) { sb.append("</ul>"); inList = false } }
    text.split("\n").forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        when {
            line.startsWith("[TIP]") -> {
                closeList()
                sb.append("<p class=\"tip\"><span class=\"lead\">Tip:</span> ")
                    .append(inline(line.removePrefix("[TIP]").trim())).append("</p>")
            }
            line.startsWith("[WARN]") -> {
                closeList()
                sb.append("<p class=\"warn\"><span class=\"lead\">Warning:</span> ")
                    .append(inline(line.removePrefix("[WARN]").trim())).append("</p>")
            }
            line.startsWith("•") -> {
                if (!inList) { sb.append("<ul>"); inList = true }
                sb.append("<li>").append(inline(line.removePrefix("•").trim())).append("</li>")
            }
            else -> {
                closeList()
                sb.append("<p>").append(inline(line)).append("</p>")
            }
        }
    }
    closeList()
    return sb.toString()
}

/** Escape the (trusted, local) copy, then convert `**x**` emphasis to `<strong>`. */
private fun inline(s: String): String {
    val escaped = esc(s)
    return Regex("""\*\*(.+?)\*\*""").replace(escaped) { "<strong>${it.groupValues[1]}</strong>" }
}

/** Minimal HTML escaping for the (trusted, local) guide copy. */
private fun esc(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
