package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.state.SettingsState

@Composable
fun BrightnessSettingsScreen(
    state: SettingsState,
    onUpdate: (min: Float, max: Float) -> Unit,
) {
    var min by remember(state.minBrightness) { mutableFloatStateOf(state.minBrightness) }
    var max by remember(state.maxBrightness) { mutableFloatStateOf(state.maxBrightness) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Minimum Brightness: $min")
        Slider(value = min, onValueChange = {
            min = it
            onUpdate(min, max)
        })
        Text("Maximum Brightness: $max")
        Slider(value = max, onValueChange = {
            max = it
            onUpdate(min, max)
        })
    }
}
