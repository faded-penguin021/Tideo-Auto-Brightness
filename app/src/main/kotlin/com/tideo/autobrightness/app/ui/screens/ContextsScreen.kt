package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.BatteryTrigger
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.ContextsViewModel
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import java.util.UUID

/** Contexts (per-app / Wi-Fi / time / charging override rules — Tasker AAB Profile + contexts.json). */
@Composable
fun ContextsScreen(navController: NavHostController, vm: ContextsViewModel = viewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { apps = runCatching { vm.installedApps() }.getOrDefault(emptyList()) }
    ContextsContent(
        rules = rules,
        profileNames = vm.profileNames,
        apps = apps,
        onBack = { navController.popBackStack() },
        onSave = vm::save,
        onDelete = { vm.delete(it) },
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
) {
    var name by remember { mutableStateOf(rule.name) }
    var profile by remember { mutableStateOf(rule.profile) }
    var priorityText by remember { mutableStateOf(rule.priority.toString()) }
    var wifi by remember { mutableStateOf(rule.triggers.wifi?.joinToString(", ") ?: "") }
    var startTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(0) ?: "") }
    var endTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(1) ?: "") }
    var charging by remember { mutableStateOf(rule.triggers.battery?.onPower == true) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startTime, onValueChange = { startTime = it }, label = { Text("From (HH:MM)") },
            singleLine = true, modifier = Modifier.weight(1f).testTag("rule_start"),
        )
        OutlinedTextField(
            value = endTime, onValueChange = { endTime = it }, label = { Text("To (HH:MM)") },
            singleLine = true, modifier = Modifier.weight(1f).testTag("rule_end"),
        )
    }
    SwitchSettingRow("Only while charging", charging, { charging = it }, testTag = "rule_charging")

    if (apps.isNotEmpty()) {
        Text("Foreground apps:", style = MaterialTheme.typography.labelMedium)
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
                    )
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(
            onClick = {
                val triggers = ContextTriggers(
                    apps = selectedApps.value.takeIf { it.isNotEmpty() }?.toList(),
                    wifi = wifi.split(",").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() },
                    battery = if (charging) BatteryTrigger(onPower = true) else null,
                    timeRange = if (startTime.isNotBlank() && endTime.isNotBlank()) listOf(startTime.trim(), endTime.trim()) else null,
                )
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
