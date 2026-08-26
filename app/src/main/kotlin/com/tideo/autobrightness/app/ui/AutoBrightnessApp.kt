package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppNavGraph
import com.tideo.autobrightness.app.runtime.DebugCategory
import com.tideo.autobrightness.app.runtime.ToastDebugSink
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.ui.components.AabFlashHost
import com.tideo.autobrightness.app.ui.graph.GraphMetricsSink
import com.tideo.autobrightness.app.ui.graph.LocalGraphMetricsSink
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import kotlinx.coroutines.flow.map

@Composable
fun AutoBrightnessApp() {
    TideoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // F88: host the in-app tap-to-dismiss flash surface above the nav graph so confirmations
            // ("Applied") and foreground debug flashes can be tapped away (a plain Toast cannot).
            AabFlashHost {
                val navController = rememberNavController()
                CompositionLocalProvider(LocalGraphMetricsSink provides rememberGraphMetricsSink()) {
                    AppNavGraph(navController)
                }
            }
        }
    }
}

/**
 * D-023 Graph Metrics (%AAB_Debug = 7), DC-001: a chart-render Flash sink, non-null ONLY on this
 * exact debug level (RuntimeDebug's exact-match), so charts do no timing work on any other level.
 */
@Composable
private fun rememberGraphMetricsSink(): GraphMetricsSink? {
    val context = LocalContext.current
    val debugLevelFlow = remember(context) { context.settingsDataStore.data.map { it.debugLevel } }
    val level by debugLevelFlow.collectAsState(initial = 0)
    val toastSink = remember(context) { ToastDebugSink(context) }
    val sink = remember(toastSink) {
        GraphMetricsSink { ms ->
            // Locale-free one-decimal render time (no String.format → no DefaultLocale lint).
            val tenths = Math.round(ms * 10.0)
            toastSink.emit(DebugCategory.GRAPH_METRICS, DebugCategory.GRAPH_METRICS.level) {
                "redraw ${tenths / 10}.${tenths % 10}ms"
            }
        }
    }
    return if (level == DebugCategory.GRAPH_METRICS.level) sink else null
}
