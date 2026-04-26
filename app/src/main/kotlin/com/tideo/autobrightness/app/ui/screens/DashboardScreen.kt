package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.Routes
import com.tideo.autobrightness.app.state.SettingsState

@Composable
fun DashboardScreen(
    navController: NavHostController,
    state: SettingsState,
    onToggle: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Auto Brightness")
        Switch(checked = state.enabled, onCheckedChange = onToggle)
        Button(onClick = { navController.navigate(Routes.BrightnessSettings) }) { Text("Brightness Settings") }
        Button(onClick = { navController.navigate(Routes.Dimming) }) { Text("Dimming") }
        Button(onClick = { navController.navigate(Routes.Reactivity) }) { Text("Reactivity") }
        Button(onClick = { navController.navigate(Routes.Experiment) }) { Text("Experiment") }
        Button(onClick = { navController.navigate(Routes.Misc) }) { Text("Misc") }
        Button(onClick = { navController.navigate(Routes.GraphBrightness) }) { Text("Brightness Graph") }
        Button(onClick = { navController.navigate(Routes.GraphAlpha) }) { Text("Alpha Graph") }
        Button(onClick = { navController.navigate(Routes.GraphDimming) }) { Text("Dimming Graph") }
        Button(onClick = { navController.navigate(Routes.GraphCircadian) }) { Text("Circadian Graph") }
        Button(onClick = { navController.navigate(Routes.GraphTaper) }) { Text("Taper Graph") }
        Button(onClick = { navController.navigate(Routes.GraphPowerDraw) }) { Text("Power Draw Graph") }
    }
}
