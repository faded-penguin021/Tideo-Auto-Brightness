package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Honest "not built yet" screen for the remaining unimplemented destination (About & Guide).
 *
 * S12.9c #8: the old placeholder copy leaked the internal segment plan into the UI and the audit
 * (`PlaceholderScreenAuditTest`) now forbids that phrasing. The destination is real and routable; only
 * its content is deferred.
 *
 * TODO(S13): implement About & Guide (and the user guide) — see S13d.
 */
@Composable
fun PlaceholderScreen(title: String, owner: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Not available yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
