package com.tideo.autobrightness.app.ui.graph

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * D-023 Graph Metrics (%AAB_Debug = 7), DC-001: reports one chart (re)generation for the debug Flash.
 * Null = uninstrumented (unit tests, previews); AutoBrightnessApp supplies one only while the debug
 * selector is on Graph Metrics, so charts stay free on every other level.
 */
fun interface GraphMetricsSink {
    /** One chart (re)draw completed in [renderMs] ms. */
    fun onChartDrawn(renderMs: Double)
}

val LocalGraphMetricsSink = staticCompositionLocalOf<GraphMetricsSink?> { null }
