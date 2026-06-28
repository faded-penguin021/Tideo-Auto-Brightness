package com.tideo.autobrightness.app.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * B1 (m3_audit §2.5) — a teal group label above a settings cluster. The optional [divider] draws a
 * thin `outlineVariant` rule immediately below (the owner "Pro-Tool" structure cue). [divider]
 * defaults to `false` so existing call sites are unchanged; S13c opts rows in as it groups screens.
 */
@Composable
fun SectionHeader(text: String, divider: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
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

/**
 * The "ⓘ" affordance that reveals a control's **Tasker long-press help** (G2R-F19/F20/F21). In the
 * Tasker scenes each parameter label carries a `longclick` help task that Flashes an explanatory
 * string; we port that verbatim and surface it on tap (the modern equivalent of Tasker's long-tap),
 * keeping the help text out of the way until requested. [tag] keys the button so a test can reveal it.
 */
@Composable
private fun HelpInfoButton(tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("help_$tag")) {
        Text("ⓘ", color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Outlined numeric field for the draft-edit model (S12.5b). The text is seeded **once per draft
 * epoch** ([epoch]) rather than re-keyed on the incoming [value]: re-seeding on every emission of a
 * committed value corrupted input mid-keystroke ("8.8" → backspace → "8.80.0", G2-F7). An empty
 * field is allowed and simply leaves the draft unchanged (no forced 0).
 *
 * When the draft [value] differs from the [committed]/active value, the committed value is shown in
 * `[brackets]` next to the label — Tasker's `_UpdateStaticSceneElements` behaviour (G2-F1).
 * [error] (from SettingsValidator) renders red; otherwise [helper] shows as supporting text.
 */
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
    help: String? = null,
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
            { HelpInfoButton(testTag) { showHelp = !showHelp } }
        } else {
            null
        },
        supportingText = {
            // Tasker long-press help wins when revealed; validation errors always take priority.
            val msg = error ?: help?.takeIf { showHelp } ?: helper
            if (msg != null) Text(msg, modifier = Modifier.testTag("helptext_$testTag"))
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal,
        ),
        modifier = modifier.fillMaxWidth().testTag(testTag),
    )
}

/**
 * A bounded M3 [Slider] for an integer setting (S12.5b, G2-F3/F13). The Tasker Misc/Experiment scenes
 * render these six values as sliders with hard ranges (per the extraction scene docs) — free-text was wrong.
 * The committed/active value is shown in `[brackets]` when the draft differs.
 */
@Composable
fun IntSliderSettingField(
    label: String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    committed: Int? = null,
    helper: String? = null,
    help: String? = null,
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
            if (help != null) HelpInfoButton(testTag) { showHelp = !showHelp }
        }
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { onCommit(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = modifier.fillMaxWidth().testTag(testTag),
        )
        val msg = help?.takeIf { showHelp } ?: helper
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

/** A labelled M3 switch row (collapses Tasker's overlaid on/off Switch pairs into one — D-017). */
@Composable
fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    help: String? = null,
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
            val msg = help?.takeIf { showHelp } ?: helper
            if (msg != null) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("helptext_$testTag"),
                )
            }
        }
        if (help != null) HelpInfoButton(testTag) { showHelp = !showHelp }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/** A read-only derived value (e.g. live form2A/form3A, derived throttle) shown beneath its inputs. */
@Composable
fun DerivedReadout(label: String, value: String, testTag: String = label) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The Apply / Discard control bar (Tasker scenes' Apply + Reset buttons), enabled only when dirty.
 * Apply confirms with a toast (Tasker Flashes "Applied", G2-F12).
 *
 * When [criticalError] is set the Apply button is DISABLED even while dirty (G2R-F18 / D-052): a
 * critical curve error (form2A<0, form3A<0, form2C>zone1End) must not be appliable. This is a
 * sanctioned deviation from Tasker's advisory-only model (owner-decision 2); a hint row explains it.
 */
@Composable
fun DraftApplyBar(
    dirty: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    criticalError: Boolean = false,
) {
    val toast = rememberToaster()
    Surface(tonalElevation = 3.dp) {
        // Edge-to-edge (targetSdk 35, enforced on Android 15+): this sticky bottomBar draws behind the
        // system navigation bar, so pad its content up clear of it — otherwise Discard/Apply sit under
        // the nav bar (worst with 3-button navigation, which is taller than the gesture pill). Unlike
        // the per-rule editor Dialog (D-098, where the nav-bar inset is never delivered to the dialog
        // window), this bar lives in the MainActivity window, so navigationBarsPadding() resolves
        // correctly; it reads 0 on pre-15 non-edge-to-edge windows, so no double padding. imePadding()
        // lifts the bar above the keyboard while a field is being edited.
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (criticalError) {
                Text(
                    "Fix the highlighted curve error before applying.",
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
                ) { Text("Discard") }
                Button(
                    onClick = { onApply(); toast("Settings applied") },
                    enabled = dirty && !criticalError,
                    modifier = Modifier.weight(1f).testTag("apply_settings"),
                ) { Text(if (dirty) "Apply" else "Applied") }
            }
        }
    }
}

/**
 * Scaffold for the draft-edit parameter screens (S12.5b): a back arrow that confirms before
 * discarding unsaved edits, and a sticky [DraftApplyBar] at the bottom (Apply/Discard). Leaving the
 * screen throws the draft away (the per-screen VM is NavBackStackEntry-scoped), so a dirty back is
 * confirmed first to match Tasker's preview→Apply expectation.
 */
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = attemptBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Per-screen reset to the task570 baseline (G2R-F17): edits the draft, so the user
                    // sees the defaults previewed and commits them with Apply.
                    if (onReset != null) {
                        TextButton(onClick = onReset, modifier = Modifier.testTag("reset_screen")) {
                            Text("Reset")
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
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes on this screen. Discard them and leave?") },
            confirmButton = {
                TextButton(
                    onClick = { showConfirm = false; onDiscard(); onNavigateBack() },
                    modifier = Modifier.testTag("confirm_discard"),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}
