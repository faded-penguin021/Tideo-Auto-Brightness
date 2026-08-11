package com.tideo.autobrightness.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.R
import kotlin.math.roundToInt

/** B1 (m3_audit §2.5): teal group label. Optional [divider] draws rule below. Defaults false. */
@Composable
fun SectionHeader(text: String, divider: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        // D-156: headings let TalkBack users jump section-to-section instead of row-by-row.
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    if (divider) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatNumber(value: Number, isInt: Boolean): String =
    if (isInt) value.toInt().toString() else value.toFloat().toString()

private fun sameNumber(a: Number, b: Number, isInt: Boolean): Boolean =
    if (isInt) a.toInt() == b.toInt() else a.toFloat() == b.toFloat()

/** The "ⓘ" affordance that reveals a control's Tasker long-press help (G2R-F19/F20/F21). Tap = long-tap. D-156: bare glyph with per-field contentDescription. */
@Composable
private fun HelpInfoButton(tag: String, label: String, onClick: () -> Unit) {
    val description = stringResource(R.string.a11y_help_for, label)
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("help_$tag").semantics { contentDescription = description },
    ) {
        Text(
            stringResource(R.string.info_glyph),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Numeric field for draft-edit model (S12.5b). Text seeded once per epoch to avoid mid-keystroke corruption. Committed value shown in [brackets] (Tasker G2-F1). */
@Composable
fun NumberSettingField(
    label: String,
    value: Number,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    epoch: Int = 0,
    committed: Number? = null,
    error: String? = null,
    helper: String? = null,
    @StringRes help: Int? = null,
    enabled: Boolean = true,
    isInt: Boolean = true,
    testTag: String = label,
) {
    var text by remember(epoch) { mutableStateOf(formatNumber(value, isInt)) }
    var showHelp by remember { mutableStateOf(false) }
    val bracket = committed?.takeIf { !sameNumber(it, value, isInt) }
        ?.let { " [${formatNumber(it, isInt)}]" } ?: ""
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.trim().replace(',', '.').toDoubleOrNull()?.let(onCommit)
        },
        label = { Text(label + bracket) },
        enabled = enabled,
        isError = error != null,
        singleLine = true,
        trailingIcon = if (help != null) {
            { HelpInfoButton(testTag, label) { showHelp = !showHelp } }
        } else {
            null
        },
        supportingText = {
            // Tasker long-press help wins when revealed; validation errors always take priority.
            val helpText = help?.let { stringResource(it) }
            val msg = error ?: helpText?.takeIf { showHelp } ?: helper
            if (msg != null) Text(msg, modifier = Modifier.testTag("helptext_$testTag"))
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal,
        ),
        modifier = modifier.fillMaxWidth().testTag(testTag),
    )
}

/** Bounded M3 Slider for integer setting (S12.5b, G2-F3/F13). Committed value shown in [brackets] when draft differs. */
@Composable
fun IntSliderSettingField(
    label: String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    committed: Int? = null,
    helper: String? = null,
    @StringRes help: Int? = null,
    enabled: Boolean = true,
    testTag: String = label,
) {
    val bracket = committed?.takeIf { it != value }?.let { " [$it]" } ?: ""
    var showHelp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$label: $value$bracket",
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (help != null) HelpInfoButton(testTag, label) { showHelp = !showHelp }
        }
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { onCommit(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
            // D-156: name the slider so TalkBack announces "<label>, slider" instead of "percentage".
            modifier = modifier.fillMaxWidth().testTag(testTag).semantics { contentDescription = label },
        )
        val msg = help?.let { stringResource(it) }?.takeIf { showHelp } ?: helper
        if (msg != null) {
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("helptext_$testTag"),
            )
        }
    }
}

/** Labeled M3 switch row (collapses Tasker's overlaid on/off pairs — D-017). */
@Composable
fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    @StringRes help: Int? = null,
    enabled: Boolean = true,
    testTag: String = label,
) {
    var showHelp by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val msg = help?.let { stringResource(it) }?.takeIf { showHelp } ?: helper
            if (msg != null) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("helptext_$testTag"),
                )
            }
        }
        if (help != null) HelpInfoButton(testTag, label) { showHelp = !showHelp }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            // D-156: label the switch node itself (its label Text is a sibling). Deliberately NOT a
            // toggleable row — that would change tap behavior and the testTag the existing tests click.
            modifier = Modifier.testTag(testTag).semantics { contentDescription = label },
        )
    }
}

/** Read-only derived value (e.g. live form2A/form3A, throttle). D-156: label + value merge for TalkBack. */
@Composable
fun DerivedReadout(label: String, value: String, testTag: String = label) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag(testTag)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Apply / Discard bar (Tasker scenes' Apply + Reset). Apply toasts "Applied" (G2-F12). When criticalError is set, Apply disabled even while dirty (G2R-F18/D-052). */
@Composable
fun DraftApplyBar(
    dirty: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    criticalError: Boolean = false,
) {
    val toast = rememberToaster()
    Surface(tonalElevation = 3.dp) {
        // Edge-to-edge (targetSdk 35): sticky bottomBar pads content clear of nav bar. D-159: keyboard lift at Scaffold level (not here).
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (criticalError) {
                Text(
                    stringResource(R.string.settings_fix_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp).testTag("apply_blocked_hint"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = dirty,
                    modifier = Modifier.weight(1f).testTag("discard_settings"),
                ) { Text(stringResource(R.string.action_discard)) }
                Button(
                    onClick = { onApply(); toast(R.string.toast_settings_applied) },
                    enabled = dirty && !criticalError,
                    modifier = Modifier.weight(1f).testTag("apply_settings"),
                ) { Text(stringResource(if (dirty) R.string.action_apply else R.string.action_applied)) }
            }
        }
    }
}

/** Scaffold for draft-edit screens (S12.5b): back arrow confirms before discarding; sticky DraftApplyBar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftSettingsScaffold(
    title: String,
    dirty: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onNavigateBack: () -> Unit,
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val attemptBack: () -> Unit = { if (dirty) showConfirm = true else onNavigateBack() }
    BackHandler(enabled = true) { attemptBack() }

    // D-159: Scaffold-level imePadding() shrinks scaffold above keyboard; sticky bar sits just over it (no dead zone).
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = attemptBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                    }
                },
                actions = {
                    // Per-screen reset (G2R-F17): edits draft so user sees defaults previewed before Apply.
                    if (onReset != null) {
                        TextButton(onClick = onReset, modifier = Modifier.testTag("reset_screen")) {
                            Text(stringResource(R.string.action_reset))
                        }
                    }
                },
            )
        },
        bottomBar = { DraftApplyBar(dirty, onApply, onDiscard, criticalError) },
        content = content,
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = false; onDiscard(); onNavigateBack() },
                    modifier = Modifier.testTag("confirm_discard"),
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
}
