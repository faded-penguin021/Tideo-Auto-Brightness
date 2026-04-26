package com.tideo.autobrightness.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tideo.autobrightness.app.state.GraphType
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.screens.BrightnessSettingsScreen
import com.tideo.autobrightness.app.ui.screens.DashboardScreen
import com.tideo.autobrightness.app.ui.screens.GraphScreen
import com.tideo.autobrightness.app.ui.screens.SettingsGroupScreen

object Routes {
    const val Dashboard = "dashboard"
    const val BrightnessSettings = "brightness_settings"
    const val Dimming = "dimming"
    const val Reactivity = "reactivity"
    const val Experiment = "experiment"
    const val Misc = "misc"
    const val GraphBrightness = "graph_brightness"
    const val GraphAlpha = "graph_alpha"
    const val GraphDimming = "graph_dimming"
    const val GraphCircadian = "graph_circadian"
    const val GraphTaper = "graph_taper"
    const val GraphPowerDraw = "graph_power_draw"
}

@Composable
fun AppNavGraph(navController: NavHostController, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val graphState by viewModel.graphState.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.Dashboard) {
        composable(Routes.Dashboard) {
            DashboardScreen(navController = navController, state = state, healthState = healthState, onToggle = viewModel::setEnabled)
        }
        composable(Routes.BrightnessSettings) {
            BrightnessSettingsScreen(state = state, onUpdate = viewModel::updateBrightness)
        }
        composable(Routes.Dimming) { SettingsGroupScreen("Dimming", state.dimming, viewModel::updateDimming) }
        composable(Routes.Reactivity) { SettingsGroupScreen("Reactivity", state.reactivity, viewModel::updateReactivity) }
        composable(Routes.Experiment) { SettingsGroupScreen("Experiment", state.experiment, viewModel::updateExperiment) }
        composable(Routes.Misc) { SettingsGroupScreen("Misc", state.misc, viewModel::updateMisc) }
        composable(Routes.GraphBrightness) { GraphScreen(GraphType.Brightness, graphState) }
        composable(Routes.GraphAlpha) { GraphScreen(GraphType.Alpha, graphState) }
        composable(Routes.GraphDimming) { GraphScreen(GraphType.Dimming, graphState) }
        composable(Routes.GraphCircadian) { GraphScreen(GraphType.Circadian, graphState) }
        composable(Routes.GraphTaper) { GraphScreen(GraphType.Taper, graphState) }
        composable(Routes.GraphPowerDraw) { GraphScreen(GraphType.PowerDraw, graphState) }
    }
}
