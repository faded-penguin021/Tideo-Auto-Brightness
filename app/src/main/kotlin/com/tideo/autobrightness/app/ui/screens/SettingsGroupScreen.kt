package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.state.SettingGroup

@Composable
fun SettingsGroupScreen(
    title: String,
    group: SettingGroup,
    onUpdate: (SettingGroup) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title)
        Text("Slider A: ${group.sliderA}")
        Slider(value = group.sliderA, onValueChange = { onUpdate(group.copy(sliderA = it)) })
        Text("Slider B: ${group.sliderB}")
        Slider(value = group.sliderB, onValueChange = { onUpdate(group.copy(sliderB = it)) })
        Text("Toggle A")
        Switch(checked = group.toggleA, onCheckedChange = { onUpdate(group.copy(toggleA = it)) })
    }
}
