package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.DisplayRule
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.AppPickerList
import com.tideo.autobrightness.app.ui.components.DayPicker
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.TimeField
import com.tideo.autobrightness.app.ui.components.TimeTokenRow
import com.tideo.autobrightness.app.ui.components.TriggerSection
import com.tideo.autobrightness.app.ui.components.UsageAccessPromptCard
import com.tideo.autobrightness.app.ui.components.summary
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.Dimens
import com.tideo.autobrightness.domain.display.DisplayAction
import java.util.UUID

/**
 * The "Schedules" surface of the Privileged Display screen (D-150, `plans/privileged-display.md`
 * Segment 4): the display-rule list + a modal editor, mirroring the Contexts rule UX
 * ([ContextRulesSection]) with the shared trigger components. A rule holds its action ON while
 * the triggers match; releasing restores the pre-engage state (the runtime contract lives in
 * `DisplayRulesCoordinator` — this section is plumbing only). The v1 editor exposes the
 * action + time/days + optional foreground apps.
 */
@Composable
fun DisplaySchedulesSection(
    rules: List<DisplayRule>,
    apps: List<AppEntry>,
    onSave: (DisplayRule) -> Unit,
    onDelete: (String) -> Unit,
    hasUsageAccess: () -> Boolean = { true },
    onRequestUsageAccess: () -> Unit = {},
) {
    var editing by remember { mutableStateOf<DisplayRule?>(null) }

    Text(
        stringResource(R.string.pd_schedules_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = {
            editing = DisplayRule(
                id = UUID.randomUUID().toString(),
                name = "",
                action = DisplayAction.GRAYSCALE.name,
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("add_display_rule"),
    ) { Text(stringResource(R.string.pd_add_schedule)) }

    // An app-scoped schedule can't trigger without usage access — surface it on the list, like
    // the Contexts list does, so a schedule that silently never fires explains itself.
    if (rules.any { it.enabled && !it.triggers.apps.isNullOrEmpty() } && !hasUsageAccess()) {
        UsageAccessPromptCard(
            messageRes = R.string.contexts_usage_warning,
            cardTag = "pd_list_usage_access_prompt",
            buttonTag = "pd_list_grant_usage_access",
            onRequest = onRequestUsageAccess,
        )
    }

    if (rules.isEmpty()) {
        EmptyState(stringResource(R.string.pd_no_schedules), testTag = "empty_display_rules")
    }
    rules.forEach { rule ->
        DisplayRuleCard(
            rule = rule,
            onEdit = { editing = rule },
            onToggleEnabled = { onSave(rule.copy(enabled = it)) },
            onDelete = { onDelete(rule.id) },
        )
    }

    val current = editing
    if (current != null) {
        Dialog(
            onDismissRequest = { editing = null },
            // Edge-to-edge for the same D-098 reason as the context rule editor: the dialog window
            // never delivers a bottom inset; scroll + trailing spacer handle the gesture pill.
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("display_rule_editor"),
                tonalElevation = Dimens.cardElevationRaised,
            ) {
                DisplayScheduleEditor(
                    rule = current,
                    apps = apps,
                    onCancel = { editing = null },
                    onSave = { onSave(it); editing = null },
                    hasUsageAccess = hasUsageAccess,
                    onRequestUsageAccess = onRequestUsageAccess,
                )
            }
        }
    }
}

@Composable
private fun DisplayRuleCard(
    rule: DisplayRule,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AabCard(
        Modifier.testTag("display_rule_${rule.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rule.name.ifBlank { stringResource(R.string.contexts_unnamed) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // Quick enable/disable without opening the editor: a disabled schedule is fully
            // inert (no match, no wake scheduling — the resolver contract).
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("display_rule_enabled_${rule.id}"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.pd_schedule_action_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(rule.actionLabelRes()),
                style = MaterialTheme.typography.titleSmall,
                color = AabGold,
                modifier = Modifier.testTag("display_rule_action_${rule.id}"),
            )
        }
        Text(
            rule.triggers.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_display_rule_${rule.id}"),
            ) { Text(stringResource(R.string.action_edit)) }
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.testTag("delete_display_rule_${rule.id}"),
            ) { Text(stringResource(R.string.confirm_delete)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_rule_title),
            message = stringResource(
                R.string.confirm_delete_rule_msg,
                rule.name.ifBlank { stringResource(R.string.contexts_unnamed) },
            ),
            confirmLabel = stringResource(R.string.confirm_delete),
            confirmTag = "confirm_delete_display_rule_${rule.id}",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow (action chips wrap on narrow screens)
@Composable
private fun DisplayScheduleEditor(
    rule: DisplayRule,
    apps: List<AppEntry>,
    onCancel: () -> Unit,
    onSave: (DisplayRule) -> Unit,
    hasUsageAccess: () -> Boolean,
    onRequestUsageAccess: () -> Unit,
) {
    var name by remember { mutableStateOf(rule.name) }
    var action by remember { mutableStateOf(rule.action) }
    var startTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(0) ?: "") }
    var endTime by remember { mutableStateOf(rule.triggers.timeRange?.getOrNull(1) ?: "") }
    val selectedDays = remember { mutableStateOf(rule.triggers.days?.toSet() ?: emptySet()) }
    val selectedApps = remember { mutableStateOf(rule.triggers.apps?.toSet() ?: emptySet()) }

    // Trigger sections are collapsible, seeded from what the rule uses (the context editor's G3
    // owner-finding pattern).
    var timeEnabled by remember { mutableStateOf(rule.triggers.timeRange != null || rule.triggers.days != null) }
    var appsEnabled by remember { mutableStateOf(rule.triggers.apps != null) }

    fun saveRule() {
        val triggers = ContextTriggers(
            apps = if (appsEnabled) selectedApps.value.takeIf { it.isNotEmpty() }?.toList() else null,
            timeRange = if (timeEnabled && startTime.isNotBlank() && endTime.isNotBlank()) {
                listOf(startTime.trim(), endTime.trim())
            } else {
                null
            },
            // All 7 (or none) selected = "every day" → omit (G2R-F67 semantics, shared resolver).
            days = if (timeEnabled) selectedDays.value.takeIf { it.isNotEmpty() && it.size < 7 }?.sorted() else null,
        )
        if (triggers.apps != null && !hasUsageAccess()) onRequestUsageAccess()
        onSave(rule.copy(name = name, action = action, triggers = triggers))
    }

    // Same scroll/inset layout as the context RuleEditor: statusBarsPadding for the top, Save/
    // Cancel at the END of the scroll + trailing spacer for the un-inset dialog bottom (D-098).
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
            SectionHeader(stringResource(R.string.pd_schedule_editor_header), divider = true)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("display_rule_name"),
            )

            Text(stringResource(R.string.pd_schedule_action_label), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                DisplayAction.entries.forEach { a ->
                    FilterChip(
                        selected = action == a.name,
                        onClick = { action = a.name },
                        label = { Text(stringResource(a.labelRes())) },
                        modifier = Modifier.testTag("display_action_${a.name.lowercase()}"),
                    )
                }
            }

            TriggerSection(stringResource(R.string.contexts_trigger_time), timeEnabled, { timeEnabled = it }, "time") {
                Text(stringResource(R.string.contexts_time_window), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        TimeField(stringResource(R.string.contexts_time_from), startTime, "start") { startTime = it }
                        TimeTokenRow("start", null) { startTime = it }
                    }
                    Column(Modifier.weight(1f)) {
                        TimeField(stringResource(R.string.contexts_time_to), endTime, "end") { endTime = it }
                        TimeTokenRow("end", null) { endTime = it }
                    }
                }
                if (startTime.isNotBlank() || endTime.isNotBlank()) {
                    TextButton(
                        onClick = { startTime = ""; endTime = "" },
                        modifier = Modifier.testTag("clear_time"),
                    ) { Text(stringResource(R.string.contexts_clear_time)) }
                }
                // Overnight windows wrap; the shared resolver attributes the post-midnight tail
                // to the previous day's membership (ContextMatching — the D-014 rule).
                Text(stringResource(R.string.contexts_days), style = MaterialTheme.typography.labelMedium)
                DayPicker(selectedDays.value) { day ->
                    selectedDays.value =
                        if (day in selectedDays.value) selectedDays.value - day else selectedDays.value + day
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

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).testTag("cancel_display_rule"),
                ) { Text(stringResource(R.string.confirm_cancel)) }
                Button(
                    onClick = { saveRule() },
                    modifier = Modifier.weight(1f).testTag("save_display_rule"),
                ) { Text(stringResource(R.string.contexts_save_rule)) }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun DisplayAction.labelRes(): Int = when (this) {
    DisplayAction.GRAYSCALE -> R.string.pd_action_grayscale
    DisplayAction.NIGHT_LIGHT -> R.string.pd_action_night_light
    DisplayAction.INVERSION -> R.string.pd_action_inversion
}

/** Label for a stored action NAME. An unknown name (written by a newer schema) renders the
 *  grayscale label as a harmless placeholder — the runtime treats such a rule as inert anyway
 *  (`DisplayRule.toSpec` returns null for it). */
private fun DisplayRule.actionLabelRes(): Int =
    (DisplayAction.entries.firstOrNull { it.name == action } ?: DisplayAction.GRAYSCALE).labelRes()
