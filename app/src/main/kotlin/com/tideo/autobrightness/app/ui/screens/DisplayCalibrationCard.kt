package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.state.CalibrationBadge
import com.tideo.autobrightness.app.state.DisplayCalibrationUi
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.HelpInfoButton
import com.tideo.autobrightness.app.ui.components.SectionHeader

// Tasker: scene "Hand-off Behavior" Display Range Calibration (AAB hand-off export, DD-077).
@Composable
internal fun DisplayCalibrationCard(state: DisplayCalibrationUi, onCalibrate: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val badge = state.badge
    val label = stringResource(R.string.handoff_calibration_label)
    AabCard {
        SectionHeader(stringResource(R.string.handoff_calibration_header), divider = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                HelpInfoButton("handoff_calibration", label) { showHelp = !showHelp }
            }
            Text(
                stringResource(badgeText(badge)),
                style = MaterialTheme.typography.labelLarge,
                color = when (badge) {
                    CalibrationBadge.CALIBRATED -> MaterialTheme.colorScheme.tertiary
                    CalibrationBadge.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.testTag("calibration_badge"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHelp) Note(stringResource(R.string.help_handoff_calibration), "helptext_handoff_calibration")
            Note(stringResource(noteText(badge)), "calibration_note")
            if (badge == CalibrationBadge.FAILED) {
                Text(
                    stringResource(R.string.handoff_calibration_failed_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("calibration_error"),
                )
            }
            if (!state.serviceRunning) Note(stringResource(R.string.handoff_calibration_service_off), "calibration_service_off")
            Button(
                onClick = { if (badge == CalibrationBadge.CALIBRATED) confirm = true else onCalibrate() },
                enabled = state.serviceRunning && badge != null && badge != CalibrationBadge.RUNNING,
                modifier = Modifier.padding(top = 4.dp).testTag("calibration_button"),
            ) { Text(stringResource(buttonText(badge))) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.handoff_recalibrate_title)) },
            text = { Text(stringResource(R.string.handoff_recalibrate_message)) },
            confirmButton = {
                Button(
                    onClick = { confirm = false; onCalibrate() },
                    modifier = Modifier.testTag("calibration_confirm"),
                ) { Text(stringResource(R.string.handoff_recalibrate)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.power_cancel)) } },
            modifier = Modifier.testTag("calibration_dialog"),
        )
    }
}

@Composable
private fun Note(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
    )
}

private fun badgeText(badge: CalibrationBadge?) = when (badge) {
    null -> R.string.handoff_calibration_checking
    CalibrationBadge.RUNNING -> R.string.handoff_calibration_running
    CalibrationBadge.CALIBRATED -> R.string.handoff_calibration_calibrated
    CalibrationBadge.FAILED -> R.string.handoff_calibration_failed
    CalibrationBadge.NEEDED -> R.string.handoff_calibration_needed
    CalibrationBadge.UNCALIBRATED -> R.string.handoff_calibration_uncalibrated
}

private fun noteText(badge: CalibrationBadge?) = when (badge) {
    null -> R.string.handoff_calibration_note_checking
    CalibrationBadge.RUNNING -> R.string.handoff_calibration_note_running
    CalibrationBadge.CALIBRATED -> R.string.handoff_calibration_note_calibrated
    CalibrationBadge.FAILED -> R.string.handoff_calibration_note_failed
    CalibrationBadge.NEEDED -> R.string.handoff_calibration_note_needed
    CalibrationBadge.UNCALIBRATED -> R.string.handoff_calibration_note_uncalibrated
}

private fun buttonText(badge: CalibrationBadge?) = when (badge) {
    CalibrationBadge.RUNNING -> R.string.handoff_calibration_running
    CalibrationBadge.CALIBRATED -> R.string.handoff_recalibrate_display
    CalibrationBadge.FAILED -> R.string.handoff_calibrate_retry
    else -> R.string.handoff_calibrate_display
}
