package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.state.GraphState
import com.tideo.autobrightness.app.state.GraphType
import com.tideo.autobrightness.app.ui.graph.LineGraph

@Composable
fun GraphScreen(
    type: GraphType,
    state: GraphState,
    webViewParityRequired: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("${type.name} Graph")
        val points = state.points[type].orEmpty()
        if (webViewParityRequired) {
            // Keep fallback for parity-only scenarios where legacy JS rendering must be matched.
            Text("WebView fallback can be mounted here when parity is mandatory.")
        } else {
            LineGraph(points)
        }
    }
}
