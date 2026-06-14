package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.LocationTrigger
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.ContextsViewModel
import com.tideo.autobrightness.platform.context.SsidResult
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import kotlinx.coroutines.launch
import java.util.UUID

/** Contexts (per-app / Wi-Fi / time / charging override rules — Tasker AAB Profile + contexts.json). */
@Composable
fun ContextsScreen(navController: NavHostController, vm: ContextsViewModel = viewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val profileNames by vm.profileNames.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { apps = runCatching { vm.installedApps() }.getOrDefault(emptyList()) }
    val toast = rememberToaster()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ContextsContent(
        rules = rules,
        profileNames = profileNames.ifEmpty { listOf("Default") },
        apps = apps,
        onBack = { navController.popBackStack() },
        onSave = { toast("Rule saved"); vm.save(it) },
        onDelete = { vm.delete(it); toast("Rule deleted") },
        onUseCurrentSsid = { setSsid ->
            scope.launch {
                // G2R-F22: targeted message per failure mode, not a blanket "Not connected".
                when (val result = vm.currentSsid()) {
                    is SsidResult.Connected -> { setSsid(result.ssid); toast("Wi-Fi: ${result.ssid}") }
                    SsidResult.NotOnWifi -> toast("Not connected to Wi-Fi")
                    SsidResult.NeedsLocationPermission ->
                        toast("Reading the Wi-Fi name needs Location permission (grant it in Setup)")
                    SsidResult.LocationServicesOff ->
                        toast("Turn on Location services to read the Wi-Fi name")
                    SsidResult.Unknown -> toast("Could not read the current Wi-Fi name")
                }
            }
        },
        onUseCurrentLocation = { setLatLon ->
            // G2R-F22 (live location): fill lat/lon from the last known device location.
            val loc = vm.currentLocation()
            if (loc != null) {
                setLatLon(loc.latitude, loc.longitude)
                toast("Location: %.4f, %.4f".format(loc.latitude, loc.longitude))
            } else {
                toast("No location available (grant Location permission and try again)")
            }
        },
        hasUsageAccess = vm::hasUsageAccess,
        onRequestUsageAccess = {
            toast("Grant usage access so per-app rules can detect the foreground app")
            runCatching { context.startActivity(vm.usageAccessIntent()) }
        },
    )
}

@Composable
fun ContextsContent(
    rules: List<ContextRule>,
    profileNames: List<String>,
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onSave: (ContextRule) -> Unit,
    onDelete: (String) -> Unit,
    onUseCurrentSsid: ((String) -> Unit) -> Unit = {},
    onUseCurrentLocation: ((Double, Double) -> Unit) -> Unit = {},
    hasUsageAccess: () -> Boolean = { true },
    onRequestUsageAccess: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<ContextRule?>(null) }

    SettingsScaffold("Contexts", onBack) { padding ->
        SettingsColumn(padding) {
            val current = editing
            if (current == null) {
                Text(
                    "Switch to a different profile automatically based on the foreground app, " +
                        "Wi-Fi, time of day or charging state.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { editing = ContextRule(id = UUID.randomUUID().toString(), name = "", profile = profileNames.firstOrNull() ?: "Default") },
                    modifier = Modifier.fillMaxWidth().testTag("add_context_rule"),
                ) { Text("Add rule") }

                if (rules.isEmpty()) {
                    Text("No rules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rules.forEach { rule ->
                    RuleCard(rule, onEdit = { editing = rule }, onDelete = { onDelete(rule.id) })
                }
            } else {
                RuleEditor(
                    rule = current,
                    profileNames = profileNames,
                    apps = apps,
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
    Card(Modifier.fillMaxWidth().testTag("rule_${rule.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rule.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.titleMedium)
            Text("→ ${rule.profile}  ·  priority ${rule.priority}", style = MaterialTheme.typography.bodyMedium)
            Text(rule.triggers.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("edit_${rule.id}")) { Text("Edit") }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_${rule.id}")) { Text("Delete") }
            }
        }
    }
}

private fun ContextTriggers.summary(): String {
    val parts = buildList {
        apps?.takeIf { it.isNotEmpty() }?.let { add("${it.size} app(s)") }
        wifi?.takeIf { it.isNotEmpty() }?.let { add("Wi-Fi ${it.joinToString()}") }
        timeRange?.takeIf { it.size == 2 }?.let { add("${it[0]}–${it[1]}") }
        battery?.let { add(if (it.onPower == true) "charging" else if (it.onPower == false) "on battery" else "battery ${it.min}-${it.max}%") }
        location?.let { add("near location") }
    }
    return if (parts.isEmpty()) "Always active" else parts.joinToString(" · ")
}

@Composable
private fun RuleEditor(
    rule: ContextRule,
    profileNames: List<String>,
    apps: List<AppEntry>,
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
    var charging by remember { mutableStateOf(rule.triggers.battery?.onPower == true) }
    // Location window (G2R-F22): lat/lon/radius editor + "use current location".
    var lat by remember { mutableStateOf(rule.triggers.location?.lat?.toString() ?: "") }
    var lon by remember { mutableStateOf(rule.triggers.location?.lon?.toString() ?: "") }
    var radius by remember { mutableStateOf(rule.triggers.location?.radius?.toString() ?: "") }
    // Battery percentage window (G2R-F31, owner-reported): 0/100 means "any level" → omit the bound.
    var battMin by remember { mutableStateOf(rule.triggers.battery?.min?.takeIf { it > 0 }?.toString() ?: "") }
    var battMax by remember { mutableStateOf(rule.triggers.battery?.max?.takeIf { it < 100 }?.toString() ?: "") }
    val selectedApps = remember { mutableStateOf(rule.triggers.apps?.toSet() ?: emptySet()) }
    var profileMenu by remember { mutableStateOf(false) }

    SectionHeader("Rule")
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

    SectionHeader("Triggers")
    OutlinedTextField(
        value = wifi, onValueChange = { wifi = it }, label = { Text("Wi-Fi SSIDs (comma-separated)") },
        modifier = Modifier.fillMaxWidth().testTag("rule_wifi"),
    )
    TextButton(
        onClick = { onUseCurrentSsid { ssid -> wifi = ssid } },
        modifier = Modifier.testTag("use_current_ssid"),
    ) { Text("Use current Wi-Fi") }

    // G2R-F28: time inputs open the system TimePicker modal; SUNRISE/SUNSET tokens are kept as
    // one-tap alternatives (the resolver accepts them, G2-F14).
    Text("Time window:", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            TimeField("From", startTime, "start") { startTime = it }
            TimeTokenRow("start") { startTime = it }
        }
        Column(Modifier.weight(1f)) {
            TimeField("To", endTime, "end") { endTime = it }
            TimeTokenRow("end") { endTime = it }
        }
    }

    SectionHeader("Location")
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

    SwitchSettingRow("Only while charging", charging, { charging = it }, testTag = "rule_charging")

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

    if (apps.isNotEmpty()) {
        Text("Foreground apps:", style = MaterialTheme.typography.labelMedium)
        // Per-app rules need usage access to read the foreground app (G2-F14): prompt when one is set.
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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(
            onClick = {
                val minPct = battMin.trim().toIntOrNull()?.coerceIn(0, 100)
                val maxPct = battMax.trim().toIntOrNull()?.coerceIn(0, 100)
                val hasBattery = charging || minPct != null || maxPct != null
                val latV = lat.trim().toDoubleOrNull()
                val lonV = lon.trim().toDoubleOrNull()
                val radiusV = radius.trim().toDoubleOrNull()
                val triggers = ContextTriggers(
                    apps = selectedApps.value.takeIf { it.isNotEmpty() }?.toList(),
                    wifi = wifi.split(",").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() },
                    battery = if (hasBattery) {
                        BatteryTrigger(
                            min = minPct ?: 0,
                            max = maxPct ?: 100,
                            onPower = if (charging) true else null,
                        )
                    } else {
                        null
                    },
                    location = if (latV != null && lonV != null && radiusV != null && radiusV > 0) {
                        LocationTrigger(lat = latV, lon = lonV, radius = radiusV)
                    } else {
                        null
                    },
                    timeRange = if (startTime.isNotBlank() && endTime.isNotBlank()) listOf(startTime.trim(), endTime.trim()) else null,
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
            },
            modifier = Modifier.testTag("save_rule"),
        ) { Text("Save rule") }
        TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_rule")) { Text("Cancel") }
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

/** SUNRISE/SUNSET quick-insert tokens for a time field (the resolver accepts them, G2-F14). */
@Composable
private fun TimeTokenRow(which: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onPick("SUNRISE") }, modifier = Modifier.testTag("${which}_sunrise")) { Text("Sunrise") }
        TextButton(onClick = { onPick("SUNSET") }, modifier = Modifier.testTag("${which}_sunset")) { Text("Sunset") }
    }
}
