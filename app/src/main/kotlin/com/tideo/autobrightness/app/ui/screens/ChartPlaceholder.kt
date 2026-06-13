package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A reserved slot for a chart whose RENDER is deferred to S13 (the chart-generation handler rows are
 * tagged `deferred-S13` in the anonymous_handlers triage). S12 leaves the host hook; S13 drops the
 * named chart (copied from [com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart]) in here.
 */
@Composable
fun ChartPlaceholder(chartName: String, testTag: String) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$chartName — chart coming in S13",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
