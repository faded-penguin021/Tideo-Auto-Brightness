package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.changedCount
import com.tideo.autobrightness.app.settings.displayRows
import com.tideo.autobrightness.app.ui.theme.AabGold

/**
 * The Tasker AAB Profile dashboard's "full list of every setting with its value, tuned values shown
 * yellow" (profile.md elements0; S12.7h / G2R-F38). Each row shows the setting label + value; any
 * value that differs from the factory default ([reference]) is rendered in theme gold and marked
 * bold — exactly the Tasker "changed-vs-default" highlight. A header summarises the count.
 */
@Composable
fun SettingsDiffList(
    settings: AabSettings,
    modifier: Modifier = Modifier,
    reference: AabSettings = AabSettings(),
    maxHeight: Int = 320,
) {
    val rows = settings.displayRows(reference)
    val changed = settings.changedCount(reference)
    Column(modifier.fillMaxWidth().testTag("settings_diff_list")) {
        Text(
            if (changed == 0) "All settings at factory defaults"
            else "$changed setting${if (changed == 1) "" else "s"} changed from default (shown in gold)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp).testTag("settings_diff_summary"),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = maxHeight.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rows, key = { it.taskerVariable }) { row ->
                Row(
                    Modifier.fillMaxWidth().testTag("diffrow_${row.taskerVariable}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.changed) AabGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (row.changed) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.changed) AabGold else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (row.changed) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.testTag("diffval_${row.taskerVariable}"),
                    )
                }
            }
        }
    }
}
