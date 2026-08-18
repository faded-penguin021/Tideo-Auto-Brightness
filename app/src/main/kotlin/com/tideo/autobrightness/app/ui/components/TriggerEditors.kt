package com.tideo.autobrightness.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.ui.theme.AabGold

/** Shared trigger-editor building blocks (D-150/D-151 extraction; ContextsScreen sole user). */

internal val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/** Collapsible trigger block (G3 owner finding; enable to reveal). */
@Composable
fun TriggerSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    key: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                // D-156: label for TalkBack (title is sibling Text)
                modifier = Modifier.testTag("trigger_toggle_$key")
                    .semantics { contentDescription = title },
            )
        }
        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/** Tappable time field opening Material3 [TimePicker] modal (G2R-F28). Accepts HH:MM or SUNRISE/SUNSET tokens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, tag: String, onSet: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val (initialH, initialM) = remember(value) { parseHhMm(value) ?: (8 to 0) }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().testTag("rule_$tag"),
    ) { Text(stringResource(R.string.contexts_picker_value, label, value.ifBlank { "—" })) }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = initialH, initialMinute = initialM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = { onSet("%02d:%02d".format(state.hour, state.minute)); showPicker = false },
                    modifier = Modifier.testTag("${tag}_time_ok"),
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.confirm_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun parseHhMm(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h to m else null
}

/** SUNRISE/SUNSET tokens for time fields (G2-F14). G2R-F68: show resolved time in gold (one-line layout). */
@Composable
fun TimeTokenRow(which: String, solarLabel: Pair<String, String>?, onPick: (String) -> Unit) {
    Column {
        TextButton(
            onClick = { onPick("SUNRISE") },
            modifier = Modifier.fillMaxWidth().testTag("${which}_sunrise"),
        ) {
            Text(
                buildString { append("Sunrise"); solarLabel?.first?.let { append(" ($it)") } },
                color = AabGold, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(
            onClick = { onPick("SUNSET") },
            modifier = Modifier.fillMaxWidth().testTag("${which}_sunset"),
        ) {
            Text(
                buildString { append("Sunset"); solarLabel?.second?.let { append(" ($it)") } },
                color = AabGold, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Day-of-week multi-select (G2R-F67). None selected = every day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_LABELS.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(label) },
                modifier = Modifier.testTag("day_$day"),
            )
        }
    }
}

/** "App rules need usage access" error card (G2-F14). Parameterized for distinct Contexts placements. */
@Composable
fun UsageAccessPromptCard(
    @StringRes messageRes: Int,
    cardTag: String,
    buttonTag: String,
    onRequest: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().testTag(cardTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onRequest,
                modifier = Modifier.testTag(buttonTag),
            ) { Text(stringResource(R.string.contexts_grant_usage)) }
        }
    }
}

/** Launchable-apps multi-select for app-scoped rules. G2R-F87: taller column for more visibility. */
@Composable
fun AppPickerList(
    apps: List<AppEntry>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
        items(apps, key = { it.packageName }) { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = entry.packageName in selected,
                    onCheckedChange = { checked -> onToggle(entry.packageName, checked) },
                    // D-156: name checkbox for TalkBack (label is sibling Text)
                    modifier = Modifier.testTag("app_check_${entry.packageName}")
                        .semantics { contentDescription = entry.label },
                )
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    entry.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                }
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

internal fun ContextTriggers.summary(): String {
    val parts = buildList {
        apps?.takeIf { it.isNotEmpty() }?.let { add("${it.size} app(s)") }
        wifi?.takeIf { it.isNotEmpty() }?.let { add("Wi-Fi ${it.joinToString()}") }
        timeRange?.takeIf { it.size == 2 }?.let { add("${it[0]}–${it[1]}") }
        days?.takeIf { it.isNotEmpty() }?.let { add(it.sorted().joinToString("") { d -> DAY_LABELS.getOrElse(d - 1) { "?" } }) }
        battery?.let { add(if (it.onPower == true) "charging" else if (it.onPower == false) "on battery" else "battery ${it.min}-${it.max}%") }
        // DB-061: name the circle; "near location" read identically for every rule.
        location?.let { add("near ${formatCoord(it.lat)}, ${formatCoord(it.lon)} (${it.radius.toInt()} m)") }
    }
    return if (parts.isEmpty()) "Always active" else parts.joinToString(" · ")
}
