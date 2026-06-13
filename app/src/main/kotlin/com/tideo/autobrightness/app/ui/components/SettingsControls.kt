package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/** A bold section label between groups of fields. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Outlined numeric field with Tasker-faithful red-error state. The text is local state so typing is
 * never fought; every parse-able edit is committed via [onCommit]. [error] (from SettingsValidator)
 * renders red; otherwise [helper] (the ported longclick tooltip text) shows as supporting text.
 *
 * Replaces the EditTextElement + valueselected/focuschange handler rows (anonymous_handlers triage
 * buckets (a) help-text + (b) settings-mutation).
 */
@Composable
fun NumberSettingField(
    label: String,
    value: Number,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
    isInt: Boolean = true,
    testTag: String = label,
) {
    val displayed = if (isInt) value.toInt().toString() else value.toFloat().toString()
    var text by remember(displayed) { mutableStateOf(displayed) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.trim().replace(',', '.').toDoubleOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        singleLine = true,
        supportingText = {
            val msg = error ?: helper
            if (msg != null) Text(msg)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal,
        ),
        modifier = modifier.fillMaxWidth().testTag(testTag),
    )
}

/** A labelled M3 switch row (collapses Tasker's overlaid on/off Switch pairs into one — D-017). */
@Composable
fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
    testTag: String = label,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
