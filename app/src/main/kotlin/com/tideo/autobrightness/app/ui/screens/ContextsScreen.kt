package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tideo.autobrightness.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.tideo.autobrightness.app.ui.components.AppPickerList
import com.tideo.autobrightness.app.ui.components.DayPicker
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.TimeField
import com.tideo.autobrightness.app.ui.components.TimeTokenRow
import com.tideo.autobrightness.app.ui.components.TriggerSection
import com.tideo.autobrightness.app.ui.components.UsageAccessPromptCard
import com.tideo.autobrightness.app.ui.components.summary
import com.tideo.autobrightness.app.ui.theme.Dimens
import java.util.UUID

// Legacy standalone surface for screen tests; live Contexts moved to ProfilesContextsScreen.
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

// Context-rules: rule list + editor in modal. Reused by ProfilesContextsScreen and ContextsContent.
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
    /** D-113: the winning rule's name (%AAB_ActiveContext) — highlighted in the list. */
    activeContext: String? = null,
) {
    var editing by remember { mutableStateOf<ContextRule?>(null) }

    Text(
        stringResource(R.string.contexts_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(
        onClick = { editing = ContextRule(id = UUID.randomUUID().toString(), name = "", profile = profileNames.firstOrNull() ?: "Default") },
        modifier = Modifier.fillMaxWidth().testTag("add_context_rule"),
    ) { Text(stringResource(R.string.contexts_add_rule)) }

    // Per-app rules need usage access to fire; show prompt on list too.
    if (rules.any { !it.triggers.apps.isNullOrEmpty() } && !hasUsageAccess()) {
        UsageAccessPromptCard(
            messageRes = R.string.contexts_usage_warning,
            cardTag = "list_usage_access_prompt",
            buttonTag = "list_grant_usage_access",
            onRequest = onRequestUsageAccess,
        )
    }

    if (rules.isEmpty()) {
        EmptyState(stringResource(R.string.contexts_no_rules), testTag = "empty_rules")
    }
    rules.forEach { rule ->
        RuleCard(
            rule,
            onEdit = { editing = rule },
            onDelete = { onDelete(rule.id) },
            isActive = rule.name.isNotBlank() && rule.name == activeContext,
        )
    }

    // Edit/add opens in modal; editor owns its scroll, host provides full-screen Surface.
    val current = editing
    if (current != null) {
        Dialog(
            onDismissRequest = { editing = null },
            // Edge-to-edge; top inset positions field below status bar, bottom handled in RuleEditor (D-098).
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
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
private fun RuleCard(rule: ContextRule, onEdit: () -> Unit, onDelete: () -> Unit, isActive: Boolean = false) {
    // D-113: active rule gets gold edge + tag; target profile shown in gold.
    val cardModifier = Modifier.testTag("rule_${rule.id}").let {
        if (isActive) it.border(1.5.dp, AabGold, MaterialTheme.shapes.medium) else it
    }
    // D-114: confirm before deleting.
    var confirmDelete by remember { mutableStateOf(false) }
    AabCard(
        cardModifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rule.name.ifBlank { stringResource(R.string.contexts_unnamed) }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isActive) {
                Text(
                    stringResource(R.string.profiles_active_tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = AabGold,
                    modifier = Modifier.testTag("rule_active_${rule.id}"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.contexts_loads), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                rule.profile,
                style = MaterialTheme.typography.titleSmall,
                color = AabGold,
                modifier = Modifier.testTag("rule_target_${rule.id}"),
            )
            Text(stringResource(R.string.contexts_priority_suffix, rule.priority), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(rule.triggers.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("edit_${rule.id}")) { Text(stringResource(R.string.action_edit)) }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_${rule.id}")) { Text(stringResource(R.string.confirm_delete)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_rule_title),
            message = stringResource(R.string.confirm_delete_rule_msg, rule.name.ifBlank { stringResource(R.string.contexts_unnamed) }),
            confirmLabel = stringResource(R.string.confirm_delete),
            confirmTag = "confirm_delete_${rule.id}",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

// D-156: `internal` so A6 a11y audit can render the editor (lives in a separate Dialog window).
@Composable
internal fun RuleEditor(
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
    // D-113: priority 1–100; seed with real value (legacy 0→1), clamp on save.
    var priorityText by remember { mutableStateOf(rule.priority.takeIf { it >= 1 }?.toString() ?: "1") }
    val priorityOverMax = (priorityText.trim().toIntOrNull() ?: 0) > 100
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
            days = if (timeEnabled) selectedDays.value.takeIf { it.isNotEmpty() && it.size < 7 }?.sorted() else null,
        )
        if (triggers.apps != null && !hasUsageAccess()) onRequestUsageAccess()
        onSave(
            rule.copy(
                name = name,
                profile = profile,
                // D-113: clamp to 1–100.
                priority = priorityText.trim().toIntOrNull()?.coerceIn(1, 100) ?: 1,
                triggers = triggers,
            ),
        )
    }

    // Editor: scrollable fields, Save/Cancel at end (not sticky bar). statusBarsPadding insets top,
    // imePadding handles keyboard. Bottom not padded (D-098), trailing Spacer lets buttons scroll clear.
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(stringResource(R.string.contexts_rule_header), divider = true)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_name"),
            )

            Text(stringResource(R.string.contexts_switch_profile), style = MaterialTheme.typography.labelMedium)
            // D-114(b): emphasize selected profile in gold; wrap DropdownMenu in Box with anchor.
            Box {
                OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.testTag("rule_profile")) {
                    Text(profile, style = MaterialTheme.typography.titleSmall, color = AabGold)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profileNames.forEach { p ->
                        DropdownMenuItem(text = { Text(p) }, onClick = { profile = p; profileMenu = false })
                    }
                }
            }

            OutlinedTextField(
                value = priorityText,
                onValueChange = { priorityText = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.contexts_priority_label)) },
                singleLine = true,
                isError = priorityOverMax,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("rule_priority"),
            )
            if (priorityOverMax) {
                Text(
                    stringResource(R.string.rule_priority_over_max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("rule_priority_clamp_hint"),
                )
            }

            SectionHeader(stringResource(R.string.contexts_triggers_header), divider = true)
            Text(
                stringResource(R.string.contexts_triggers_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TriggerSection(stringResource(R.string.contexts_trigger_wifi), wifiEnabled, { wifiEnabled = it }, "wifi") {
                OutlinedTextField(
                    value = wifi, onValueChange = { wifi = it }, label = { Text(stringResource(R.string.contexts_wifi_ssids)) },
                    modifier = Modifier.fillMaxWidth().testTag("rule_wifi"),
                )
                TextButton(
                    // D-113: APPEND SSID to list (not replace); a rule can match multiple networks.
                    onClick = {
                        onUseCurrentSsid { ssid ->
                            val existing = wifi.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (ssid.isNotBlank() && ssid !in existing) {
                                wifi = (existing + ssid).joinToString(", ")
                            }
                        }
                    },
                    modifier = Modifier.testTag("use_current_ssid"),
                ) { Text(stringResource(R.string.contexts_use_current_wifi)) }
            }

            TriggerSection(stringResource(R.string.contexts_trigger_time), timeEnabled, { timeEnabled = it }, "time") {
                Text(stringResource(R.string.contexts_time_window), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        TimeField(stringResource(R.string.contexts_time_from), startTime, "start") { startTime = it }
                        TimeTokenRow("start", solarLabel) { startTime = it }
                    }
                    Column(Modifier.weight(1f)) {
                        TimeField(stringResource(R.string.contexts_time_to), endTime, "end") { endTime = it }
                        TimeTokenRow("end", solarLabel) { endTime = it }
                    }
                }
                if (startTime.isNotBlank() || endTime.isNotBlank()) {
                    TextButton(
                        onClick = { startTime = ""; endTime = "" },
                        modifier = Modifier.testTag("clear_time"),
                    ) { Text(stringResource(R.string.contexts_clear_time)) }
                }
                Text(stringResource(R.string.contexts_days), style = MaterialTheme.typography.labelMedium)
                DayPicker(selectedDays.value) { day ->
                    selectedDays.value = if (day in selectedDays.value) selectedDays.value - day else selectedDays.value + day
                }
            }

            TriggerSection(stringResource(R.string.contexts_trigger_location), locationEnabled, { locationEnabled = it }, "location") {
                OutlinedButton(
                    onClick = { onUseCurrentLocation { la, lo -> lat = "%.5f".format(la); lon = "%.5f".format(lo) } },
                    modifier = Modifier.testTag("use_current_location"),
                ) { Text(stringResource(R.string.action_use_current_location)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat, onValueChange = { lat = it }, label = { Text(stringResource(R.string.field_latitude)) },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("rule_lat"),
                    )
                    OutlinedTextField(
                        value = lon, onValueChange = { lon = it }, label = { Text(stringResource(R.string.field_longitude)) },
                        singleLine = true, modifier = Modifier.weight(1f).testTag("rule_lon"),
                    )
                }
                OutlinedTextField(
                    value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.contexts_radius)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rule_radius"),
                )
            }

            TriggerSection(stringResource(R.string.contexts_trigger_battery), batteryEnabled, { batteryEnabled = it }, "battery") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // D-156: name the switch; sibling Text doesn't announce to TalkBack.
                    val onlyChargingLabel = stringResource(R.string.contexts_only_charging)
                    Switch(
                        checked = charging,
                        onCheckedChange = { charging = it },
                        modifier = Modifier.testTag("rule_charging")
                            .semantics { contentDescription = onlyChargingLabel },
                    )
                    Text(onlyChargingLabel, style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.contexts_battery_pct), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = battMin, onValueChange = { battMin = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.contexts_from_pct)) }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("rule_batt_min"),
                    )
                    OutlinedTextField(
                        value = battMax, onValueChange = { battMax = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.contexts_to_pct)) }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("rule_batt_max"),
                    )
                }
            }

            if (apps.isNotEmpty()) {
                TriggerSection(stringResource(R.string.contexts_trigger_apps), appsEnabled, { appsEnabled = it }, "apps") {
                    if (selectedApps.value.isNotEmpty() && !hasUsageAccess()) {
                        UsageAccessPromptCard(
                            messageRes = R.string.contexts_usage_required,
                            cardTag = "usage_access_prompt",
                            buttonTag = "grant_usage_access",
                            onRequest = onRequestUsageAccess,
                        )
                    }
                    AppPickerList(apps, selectedApps.value) { pkg, checked ->
                        selectedApps.value =
                            if (checked) selectedApps.value + pkg else selectedApps.value - pkg
                    }
                }
            }
            // Save/Cancel at end of scroll, not sticky (D-098); trailing Spacer clears gesture pill.
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).testTag("cancel_rule"),
                ) { Text(stringResource(R.string.confirm_cancel)) }
                Button(
                    onClick = { saveRule() },
                    modifier = Modifier.weight(1f).testTag("save_rule"),
                ) { Text(stringResource(R.string.contexts_save_rule)) }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

// D-150: TriggerSection components moved to TriggerEditors.kt.
