package com.tideo.autobrightness.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LineGraph(points: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas
        val xStep = size.width / (points.size - 1).coerceAtLeast(1)
        val max = points.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
        var previous: Offset? = null
        points.forEachIndexed { index, value ->
            val point = Offset(index * xStep, size.height - (value / max) * size.height)
            previous?.let { drawLine(Color.Cyan, it, point, strokeWidth = 4f) }
            previous = point
        }
    }
}
