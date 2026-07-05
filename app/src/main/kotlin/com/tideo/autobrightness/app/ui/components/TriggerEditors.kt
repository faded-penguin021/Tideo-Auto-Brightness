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
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.ui.theme.AabGold

/**
 * Shared trigger-editor building blocks, extracted verbatim from `ContextsScreen`'s private
 * composables (D-150, for the since-removed display-schedule editor; D-151 deleted that second
 * consumer, so ContextsScreen is again the sole user — the extraction stays as the shared home
 * for any future rule editor). Behavior and test tags are unchanged — the ContextsScreen suites
 * are the proof.
 */

/** Calendar.DAY_OF_WEEK index (1=Sun..7=Sat) → short label; the day picker maps positions to these. */
internal val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/**
 * A collapsible trigger block (G3 owner finding — mirror Tasker's "enable to reveal" gating). The
 * header carries the trigger [title] and an on/off [Switch] (`trigger_toggle_<key>`); the [content]
 * (its fields) is shown only while enabled, so the editor only displays what the rule actually uses.
 */
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
            // Owner finding: the trigger labels read a bit large — use the lighter body style.
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("trigger_toggle_$key"),
            )
        }
        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/**
 * A tappable time field that opens the Material3 [TimePicker] modal (G2R-F28). Shows the current
 * value (an "HH:MM" time or a SUNRISE/SUNSET token); tapping opens the picker, seeded from the current
 * "HH:MM" when present. Replaces the previous free-text `OutlinedTextField`.
 */
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

/** Parse an "HH:MM" string to (hour, minute), or null for blank/token values (SUNRISE/SUNSET). */
private fun parseHhMm(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h to m else null
}

/**
 * SUNRISE/SUNSET quick-insert tokens for a time field (the resolver accepts them, G2-F14). G2R-F68:
 * when today's resolved sunrise/sunset is known, show it in theme gold (e.g. "Sunrise (06:42)").
 *
 * G2R-F68 (UI bug): the tokens live inside a half-width From/To column, so "Sunset (22:00)" used to
 * char-wrap one letter per line. They are now stacked vertically (each gets the full column width)
 * with `maxLines = 1` / `softWrap = false` so the resolved-time label always renders on one line.
 */
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

/**
 * Day-of-week multi-select (G2R-F67): one filter chip per day, Calendar.DAY_OF_WEEK 1=Sun..7=Sat.
 * Wraps so all seven fit on narrow screens. None selected = every day.
 */
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

/**
 * The "app rules need usage access" error card (G2-F14): shown wherever an app-scoped rule exists
 * (or is being edited) without the usage-stats grant, with a button opening the system grant page.
 * Message + tags are parameters because the two Contexts placements use distinct wording/tags.
 */
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

/**
 * The launchable-apps multi-select for app-scoped rules (icon + label + checkbox per row,
 * `app_check_<package>`). G2R-F87: taller (still scrollable) so more apps are visible at once.
 */
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
                    modifier = Modifier.testTag("app_check_${entry.packageName}"),
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

/** One-line trigger summary for a rule card (shared by the Contexts and Schedules lists). */
internal fun ContextTriggers.summary(): String {
    val parts = buildList {
        apps?.takeIf { it.isNotEmpty() }?.let { add("${it.size} app(s)") }
        wifi?.takeIf { it.isNotEmpty() }?.let { add("Wi-Fi ${it.joinToString()}") }
        timeRange?.takeIf { it.size == 2 }?.let { add("${it[0]}–${it[1]}") }
        days?.takeIf { it.isNotEmpty() }?.let { add(it.sorted().joinToString("") { d -> DAY_LABELS.getOrElse(d - 1) { "?" } }) }
        battery?.let { add(if (it.onPower == true) "charging" else if (it.onPower == false) "on battery" else "battery ${it.min}-${it.max}%") }
        location?.let { add("near location") }
    }
    return if (parts.isEmpty()) "Always active" else parts.joinToString(" · ")
}
