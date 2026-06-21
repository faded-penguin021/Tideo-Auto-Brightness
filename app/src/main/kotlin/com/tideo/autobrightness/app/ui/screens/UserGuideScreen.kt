package com.tideo.autobrightness.app.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.theme.AabBackgroundDark

/**
 * AAB User Guide scene (Tasker: sceneAAB User Guide, extraction/scenes/user_guide.md). Tasker rendered
 * this as a styled **WebView/HTML** manual, so the rebuild does too — a static HTML document themed to
 * the AAB palette (teal headers, gold accents, dark surface) rendered in a `WebView` (no JS, no network,
 * no new dependency). The copy still comes from `strings.xml` (i18n preserved); only the markup is local.
 *
 * Shown from the Menu Info & Help group and once as the **post-onboarding first-run destination**
 * (G2R-F80) with the Menu seeded beneath, so "Got it" / Back return to the hub.
 */
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
    val banner = stringResource(R.string.guide_banner)
    val outro = stringResource(R.string.guide_outro)
    val sections = GUIDE_SECTIONS.map { (t, b) -> stringResource(t) to stringResource(b) }
    val html = remember(banner, outro, sections) { buildGuideHtml(banner, sections, outro) }
    val pageBg = AabBackgroundDark.toArgb()

    SettingsScaffold(stringResource(R.string.title_user_guide), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("guide_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        setBackgroundColor(pageBg)
                    }
                },
                update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
            )
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("guide_done"),
            ) { Text(stringResource(R.string.guide_done)) }
        }
    }
}

/** Assemble the AAB-themed HTML manual from the (already-i18n) section strings. Bodies use `•` bullets
 *  and `\n` line breaks (user_guide.md); `•` lines become `<li>`, the rest `<p>`. */
private fun buildGuideHtml(
    banner: String,
    sections: List<Pair<String, String>>,
    outro: String,
): String {
    val body = buildString {
        sections.forEach { (title, text) ->
            append("<h2>").append(esc(title)).append("</h2>")
            append(sectionBodyHtml(text))
        }
        append("<p class=\"outro\">").append(esc(outro)).append("</p>")
    }
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: dark; }
          body { background:#333333; color:#ECECEC; font-family:sans-serif; line-height:1.5;
                 margin:0; padding:16px 18px 28px; font-size:15px; }
          h1 { color:#FFC107; font-size:20px; margin:0 0 4px; }
          .banner { background:#007C63; color:#FFFFFF; margin:-16px -18px 16px; padding:18px;
                    font-size:18px; font-weight:600; }
          h2 { color:#00A986; font-size:16px; margin:22px 0 6px; border-bottom:1px solid #4a4a4a;
               padding-bottom:4px; }
          p { margin:6px 0; }
          ul { margin:6px 0 6px 2px; padding-left:18px; }
          li { margin:4px 0; }
          strong, b { color:#FFC107; }
          .outro { color:#00C79E; font-weight:600; margin-top:22px; }
        </style></head>
        <body><div class="banner">${esc(banner)}</div>$body</body></html>
    """.trimIndent()
}

private fun sectionBodyHtml(text: String): String {
    val sb = StringBuilder()
    var inList = false
    text.split("\n").forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith("•")) {
            if (!inList) { sb.append("<ul>"); inList = true }
            sb.append("<li>").append(esc(line.removePrefix("•").trim())).append("</li>")
        } else {
            if (inList) { sb.append("</ul>"); inList = false }
            sb.append("<p>").append(esc(line)).append("</p>")
        }
    }
    if (inList) sb.append("</ul>")
    return sb.toString()
}

/** Minimal HTML escaping for the (trusted, local) guide copy. */
private fun esc(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
