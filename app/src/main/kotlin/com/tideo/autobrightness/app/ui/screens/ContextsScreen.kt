package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.LocationTrigger
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.theme.Dimens
import java.util.UUID

/**
 * Legacy standalone Contexts surface, retained only for the existing screen tests. S12.9f folded the
 * live Contexts destination into the unified Profiles & Contexts screen
 * ([ProfilesContextsScreen]); the rule list + editor now render via [ContextRulesSection], reused by
 * both. The per-rule editor opens in a modal (see [ContextRulesSection]).
 */
@Composable
fun ContextsContent(
    rules: List<ContextRule>,
    profileNames: List<String>,
    apps: List<AppEntry>,
    solarLabel: Pair<String, String>? = null,
    onBack: () -> Unit,
    onSave: (ContextRule) -> Unit,
    onDelete: (String) -> Unit,
    onUseCurrentSsid: ((String) -> Unit) -> Unit = {},
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
    hasUsageAccess: () -> Boolean = { true },
    onRequestUsageAccess: () -> Unit = {},
) {
    SettingsScaffold(stringResource(R.string.title_contexts), onBack) { padding ->
        SettingsColumn(padding) {
            ContextRulesSection(
                rules = rules,
                profileNames = profileNames,
                apps = apps,
                solarLabel = solarLabel,
                onSave = onSave,
                onDelete = onDelete,
                onUseCurrentSsid = onUseCurrentSsid,
                onUseCurrentLocation = onUseCurrentLocation,
                hasUsageAccess = hasUsageAccess,
                onRequestUsageAccess = onRequestUsageAccess,
            )
        }
    }
}

/**
 * The Context-rules surface (S12.9f IA merge): a description, an "Add rule" action, and the
 * priority-ordered rule list (each card showing its target profile + priority + trigger summary).
 * Editing or adding a rule opens the full [RuleEditor] in a modal full-screen [Dialog] — this is the
 * Tasker UX where a profile owns its context rules and a rule targets a saved profile. Emitted into a
 * scrolling [Column] (e.g. [SettingsColumn]); reused by the unified [ProfilesContextsScreen] and the
 * legacy [ContextsContent]. Plumbing only — S13 restyles.
 */
@Composable
fun ContextRulesSection(
    rules: List<ContextRule>,
    profileNames: List<String>,
    apps: List<AppEntry>,
    solarLabel: Pair<String, String>? = null,
    onSave: (ContextRule) -> Unit,
    onDelete: (String) -> Unit,
    onUseCurrentSsid: ((String) -> Unit) -> Unit = {},
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
    hasUsageAccess: () -> Boolean = { true },
    onRequestUsageAccess: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<ContextRule?>(null) }

    Text(
        "Switch to a different profile automatically based on the foreground app, " +
            "Wi-Fi, time of day or charging state.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(
        onClick = { editing = ContextRule(id = UUID.randomUUID().toString(), name = "", profile = profileNames.firstOrNull() ?: "Default") },
        modifier = Modifier.fillMaxWidth().testTag("add_context_rule"),
    ) { Text("Add rule") }

    // A saved per-app rule can't trigger without usage access (it has no way to read the foreground
    // app). Surface it on the list too, not only inside the editor, so a rule that silently never
    // fires explains itself.
    if (rules.any { !it.triggers.apps.isNullOrEmpty() } && !hasUsageAccess()) {
        Card(
            Modifier.fillMaxWidth().testTag("list_usage_access_prompt"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Per-app rules need usage access to detect the foreground app. " +
                        "Without it they never trigger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(
                    onClick = onRequestUsageAccess,
                    modifier = Modifier.testTag("list_grant_usage_access"),
                ) { Text("Grant usage access") }
            }
        }
    }

    if (rules.isEmpty()) {
        EmptyState("No rules yet.", testTag = "empty_rules")
    }
    rules.forEach { rule ->
        RuleCard(rule, onEdit = { editing = rule }, onDelete = { onDelete(rule.id) })
    }

    // S12.9f: edit/add opens the per-rule editor in a modal. The editor owns its own scroll + a sticky
    // bottom Save/Cancel bar (G3 owner finding: top buttons + everything-expanded read as inferior to
    // Tasker), so the host just provides the full-screen Surface.
    val current = editing
    if (current != null) {
        Dialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize().testTag("rule_editor_modal"), tonalElevation = Dimens.cardElevationRaised) {
                RuleEditor(
                    rule = current,
                    profileNames = profileNames,
                    apps = apps,
                    solarLabel = solarLabel,
                    onCancel = { editing = null },
                    onSave = { onSave(it); editing = null },
                    onUseCurrentSsid = onUseCurrentSsid,
                    onUseCurrentLocation = onUseCurrentLocation,
                    hasUsageAccess = hasUsageAccess,
                    onRequestUsageAccess = onRequestUsageAccess,
                )
            }
        }
    }
}

@Composable
private fun RuleCard(rule: ContextRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    // S13c restyle (m3_audit §3 row 10): rule rows are elevated `AabCard`s.
    AabCard(
        Modifier.testTag("rule_${rule.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(rule.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
        Text("→ ${rule.profile}  ·  priority ${rule.priority}", style = MaterialTheme.typography.bodyMedium)
        Text(rule.triggers.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("edit_${rule.id}")) { Text("Edit") }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_${rule.id}")) { Text("Delete") }
        }
    }
}

private fun ContextTriggers.summary(): String {
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

/** Calendar.DAY_OF_WEEK index (1=Sun..7=Sat) → short label; the day picker maps positions to these. */
private val DAY_LABELS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

@Composable
private fun RuleEditor(
    rule: ContextRule,
    profileNames: List<String>,
    apps: List<AppEntry>,
    solarLabel: Pair<String, String>?,
    onCancel: () -> Unit,
    onSave: (ContextRule) -> Unit,
    onUseCurrentSsid: ((String) -> Unit) -> Unit,
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit,
    hasUsageAccess: () -> Boolean,
    onRequestUsageAccess: () -> Unit,
) {
    var name by remember { mutableStateOf(rule.name) }
    var profile by remember { mutableStateOf(rule.profile) }
    var priorityText by remember { mutableStateOf(rule.priority.toString()) }
    var wifi by remember { mutableStateOf(rule.triggers.wifi?.joinToString(", ") ?: "") }
    var startTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(0) ?: "") }
    var endTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(1) ?: "") }
    // Day-of-week selection (G2R-F67): Calendar.DAY_OF_WEEK values 1=Sun..7=Sat; empty = all days.
    val selectedDays = remember { mutableStateOf(rule.triggers.days?.toSet() ?: emptySet()) }
    var charging by remember { mutableStateOf(rule.triggers.battery?.onPower == true) }
    // Location window (G2R-F22): lat/lon/radius editor + "use current location".
    var lat by remember { mutableStateOf(rule.triggers.location?.lat?.toString() ?: "") }
    var lon by remember { mutableStateOf(rule.triggers.location?.lon?.toString() ?: "") }
    // G3 owner finding: radius defaults to 200 m (never blank); the user can still edit it.
    var radius by remember { mutableStateOf(rule.triggers.location?.radius?.let { it.toInt().toString() } ?: "200") }
    // Battery percentage window (G2R-F31, owner-reported): 0/100 means "any level" → omit the bound.
    var battMin by remember { mutableStateOf(rule.triggers.battery?.min?.takeIf { it > 0 }?.toString() ?: "") }
    var battMax by remember { mutableStateOf(rule.triggers.battery?.max?.takeIf { it < 100 }?.toString() ?: "") }
    val selectedApps = remember { mutableStateOf(rule.triggers.apps?.toSet() ?: emptySet()) }
    var profileMenu by remember { mutableStateOf(false) }

    // G3 owner finding: triggers are collapsible (Tasker gated each block behind a check). Each
    // section's enabled state is seeded from whether that trigger exists on the rule, so editing an
    // existing rule re-opens exactly the triggers it uses; a new rule starts with all collapsed.
    var wifiEnabled by remember { mutableStateOf(rule.triggers.wifi != null) }
    var timeEnabled by remember { mutableStateOf(rule.triggers.timeRange != null || rule.triggers.days != null) }
    var locationEnabled by remember { mutableStateOf(rule.triggers.location != null) }
    var batteryEnabled by remember { mutableStateOf(rule.triggers.battery != null) }
    var appsEnabled by remember { mutableStateOf(rule.triggers.apps != null) }

    fun saveRule() {
        val minPct = battMin.trim().toIntOrNull()?.coerceIn(0, 100)
        val maxPct = battMax.trim().toIntOrNull()?.coerceIn(0, 100)
        val hasBattery = batteryEnabled && (charging || minPct != null || maxPct != null)
        val latV = lat.trim().toDoubleOrNull()
        val lonV = lon.trim().toDoubleOrNull()
        val radiusV = radius.trim().toDoubleOrNull()
        // Each trigger is included only when its section is enabled (and has valid data).
        val triggers = ContextTriggers(
            apps = if (appsEnabled) selectedApps.value.takeIf { it.isNotEmpty() }?.toList() else null,
            wifi = if (wifiEnabled) {
                wifi.split(",").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
            } else {
                null
            },
            battery = if (hasBattery) {
                BatteryTrigger(min = minPct ?: 0, max = maxPct ?: 100, onPower = if (charging) true else null)
            } else {
                null
            },
            location = if (locationEnabled && latV != null && lonV != null && radiusV != null && radiusV > 0) {
                LocationTrigger(lat = latV, lon = lonV, radius = radiusV)
            } else {
                null
            },
            timeRange = if (timeEnabled && startTime.isNotBlank() && endTime.isNotBlank()) {
                listOf(startTime.trim(), endTime.trim())
            } else {
                null
            },
            // All 7 (or none) selected = "every day" → omit (G2R-F67).
            days = if (timeEnabled) selectedDays.value.takeIf { it.isNotEmpty() && it.size < 7 }?.sorted() else null,
        )
        // Prompt for usage access on save if the rule targets apps and it is not granted.
        if (triggers.apps != null && !hasUsageAccess()) onRequestUsageAccess()
        onSave(
            rule.copy(
                name = name,
                profile = profile,
                priority = priorityText.trim().toIntOrNull() ?: 0,
                triggers = triggers,
            ),
        )
    }

    // The editor owns its layout: a scrollable field area + a sticky bottom Save/Cancel bar (G3 owner
    // finding — top buttons + an all-expanded form read as inferior to the Tasker editor). The full-
    // screen Dialog doesn't apply window insets, so pad for the status + navigation bars or the bottom
    // action bar is clipped behind the system nav bar (owner: "Save/Cancel get clipped").
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader("Rule", divider = true)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_name"),
            )

            Text("Switch to profile:", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.testTag("rule_profile")) { Text(profile) }
            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                profileNames.forEach { p ->
                    DropdownMenuItem(text = { Text(p) }, onClick = { profile = p; profileMenu = false })
                }
            }

            OutlinedTextField(
                value = priorityText, onValueChange = { priorityText = it }, label = { Text("Priority (higher wins)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_priority"),
            )

            SectionHeader("Triggers", divider = true)
            Text(
                "Turn on only the conditions this rule should match. The rule loads its profile when " +
                    "ALL enabled triggers are satisfied.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TriggerSection("Wi-Fi", wifiEnabled, { wifiEnabled = it }, "wifi") {
                OutlinedTextField(
                    value = wifi, onValueChange = { wifi = it }, label = { Text("Wi-Fi SSIDs (comma-separated)") },
                    modifier = Modifier.fillMaxWidth().testTag("rule_wifi"),
                )
                TextButton(
                    onClick = { onUseCurrentSsid { ssid -> wifi = ssid } },
                    modifier = Modifier.testTag("use_current_ssid"),
                ) { Text("Use current Wi-Fi") }
            }

            TriggerSection("Time & day", timeEnabled, { timeEnabled = it }, "time") {
                // G2R-F28: time inputs open the system TimePicker modal; SUNRISE/SUNSET tokens are kept
                // as one-tap alternatives (the resolver accepts them, G2-F14).
                Text("Time window:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        TimeField("From", startTime, "start") { startTime = it }
                        TimeTokenRow("start", solarLabel) { startTime = it }
                    }
                    Column(Modifier.weight(1f)) {
                        TimeField("To", endTime, "end") { endTime = it }
                        TimeTokenRow("end", solarLabel) { endTime = it }
                    }
                }
                // G2R-F72: clear blanks both fields → on save timeRange becomes null (time-agnostic again).
                if (startTime.isNotBlank() || endTime.isNotBlank()) {
                    TextButton(
                        onClick = { startTime = ""; endTime = "" },
                        modifier = Modifier.testTag("clear_time"),
                    ) { Text("Clear time") }
                }
                // Day-of-week picker (G2R-F67): overnight windows wrap; the resolver attributes the
                // post-midnight tail to the previous day's membership (D-014).
                Text("Days (none = every day):", style = MaterialTheme.typography.labelMedium)
                DayPicker(selectedDays.value) { day ->
                    selectedDays.value = if (day in selectedDays.value) selectedDays.value - day else selectedDays.value + day
                }
            }

            TriggerSection("Location", locationEnabled, { locationEnabled = it }, "location") {
                OutlinedButton(
                    onClick = { onUseCurrentLocation { la, lo -> lat = "%.5f".format(la); lon = "%.5f".format(lo) } },
                    modifier = Modifier.testTag("use_current_location"),
                ) { Text("Use current location") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat, onValueChange = { lat = it }, label = { Text("Latitude") },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("rule_lat"),
                    )
                    OutlinedTextField(
                        value = lon, onValueChange = { lon = it }, label = { Text("Longitude") },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("rule_lon"),
                    )
                }
                OutlinedTextField(
                    value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Radius (metres)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rule_radius"),
                )
            }

            TriggerSection("Battery", batteryEnabled, { batteryEnabled = it }, "battery") {
                // Owner finding: put this toggle on the LEFT (the section-enable switches are on the
                // right) so it doesn't read as another section toggle.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = charging,
                        onCheckedChange = { charging = it },
                        modifier = Modifier.testTag("rule_charging"),
                    )
                    Text("Only while charging", style = MaterialTheme.typography.bodyMedium)
                }
                // Battery percentage window (G2R-F31). Either bound may be left blank for "any".
                Text("Battery percentage:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = battMin, onValueChange = { battMin = it.filter(Char::isDigit) },
                        label = { Text("From %") }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("rule_batt_min"),
                    )
                    OutlinedTextField(
                        value = battMax, onValueChange = { battMax = it.filter(Char::isDigit) },
                        label = { Text("To %") }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("rule_batt_max"),
                    )
                }
            }

            if (apps.isNotEmpty()) {
                TriggerSection("Foreground apps", appsEnabled, { appsEnabled = it }, "apps") {
                    // Per-app rules need usage access to read the foreground app (G2-F14): prompt when set.
                    if (selectedApps.value.isNotEmpty() && !hasUsageAccess()) {
                        Card(
                            Modifier.fillMaxWidth().testTag("usage_access_prompt"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Usage access is required to detect the foreground app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                OutlinedButton(onClick = onRequestUsageAccess, modifier = Modifier.testTag("grant_usage_access")) {
                                    Text("Grant usage access")
                                }
                            }
                        }
                    }
                    // G2R-F87: the app picker is taller (still scrollable) so more apps are visible at once.
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(apps, key = { it.packageName }) { entry ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.packageName in selectedApps.value,
                                    onCheckedChange = { checked ->
                                        selectedApps.value = if (checked) selectedApps.value + entry.packageName
                                        else selectedApps.value - entry.packageName
                                    },
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
            }
        }

        // Sticky bottom action bar (Save/Cancel always reachable, no scrolling past the trigger list).
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).testTag("cancel_rule"),
            ) { Text("Cancel") }
            Button(
                onClick = { saveRule() },
                modifier = Modifier.weight(1f).testTag("save_rule"),
            ) { Text("Save rule") }
        }
    }
}

/**
 * A collapsible trigger block (G3 owner finding — mirror Tasker's "enable to reveal" gating). The
 * header carries the trigger [title] and an on/off [Switch] (`trigger_toggle_<key>`); the [content]
 * (its fields) is shown only while enabled, so the editor only displays what the rule actually uses.
 */
@Composable
private fun TriggerSection(
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
private fun TimeField(label: String, value: String, tag: String, onSet: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val (initialH, initialM) = remember(value) { parseHhMm(value) ?: (8 to 0) }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth().testTag("rule_$tag"),
    ) { Text("$label: ${value.ifBlank { "—" }}") }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = initialH, initialMinute = initialM, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = { onSet("%02d:%02d".format(state.hour, state.minute)); showPicker = false },
                    modifier = Modifier.testTag("${tag}_time_ok"),
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
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
private fun TimeTokenRow(which: String, solarLabel: Pair<String, String>?, onPick: (String) -> Unit) {
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
private fun DayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
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
