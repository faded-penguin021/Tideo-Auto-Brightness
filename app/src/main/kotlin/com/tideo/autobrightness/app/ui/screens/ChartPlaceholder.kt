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
 * A reserved slot for a chart whose RENDER is deferred (the chart-generation handler rows are tagged
 * `deferred-S13` in the anonymous_handlers triage). The host hook is in place; the named chart (copied
 * from [com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart]) drops in here later.
 *
 * S12.9c #8: the user-facing copy no longer names the internal segment (audit-forbidden).
 * TODO(S13): implement this chart — see S13d.
 */
@Composable
fun ChartPlaceholder(chartName: String, testTag: String) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$chartName — chart not available yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
