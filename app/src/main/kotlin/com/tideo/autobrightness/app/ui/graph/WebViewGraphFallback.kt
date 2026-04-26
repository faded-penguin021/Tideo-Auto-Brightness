package com.tideo.autobrightness.app.ui.graph

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebViewGraphFallback(html: String) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    })
}
