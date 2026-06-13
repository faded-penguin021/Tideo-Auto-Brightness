package com.tideo.autobrightness.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.ui.onboarding.OnboardingScreen
import com.tideo.autobrightness.app.ui.screens.AnimationDimmingScreen
import com.tideo.autobrightness.app.ui.screens.ContextsScreen
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessScreen
import com.tideo.autobrightness.app.ui.screens.DashboardScreen
import com.tideo.autobrightness.app.ui.screens.DynamicScaleScreen
import com.tideo.autobrightness.app.ui.screens.MiscScreen
import com.tideo.autobrightness.app.ui.screens.PlaceholderScreen
import com.tideo.autobrightness.app.ui.screens.ProfilesScreen
import com.tideo.autobrightness.app.ui.screens.ReactivityScreen
import com.tideo.autobrightness.app.ui.screens.ToolsScreen
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * The navigation shell over the [AppRoute] target screen set. S11 wired Dashboard + Onboarding; S12
 * fills the parameter/tool/profile destinations with real screens. About & Guide remains a labelled
 * [PlaceholderScreen] until S13. First-run routing sends the user to Onboarding when tier == NONE.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = rememberStartDestination(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoute.Dashboard.route) { DashboardScreen(navController) }
        composable(AppRoute.Onboarding.route) { OnboardingScreen(navController) }
        composable(AppRoute.CurveBrightness.route) { CurveBrightnessScreen(navController) }
        composable(AppRoute.Reactivity.route) { ReactivityScreen(navController) }
        composable(AppRoute.AnimationDimming.route) { AnimationDimmingScreen(navController) }
        composable(AppRoute.DynamicScale.route) { DynamicScaleScreen(navController) }
        composable(AppRoute.Misc.route) { MiscScreen(navController) }
        composable(AppRoute.Contexts.route) { ContextsScreen(navController) }
        composable(AppRoute.Tools.route) { ToolsScreen(navController) }
        composable(AppRoute.Profiles.route) { ProfilesScreen(navController) }
        // S13-owned:
        composable(AppRoute.About.route) { PlaceholderScreen(AppRoute.About.label, AppRoute.About.owner) }
    }
}

/** Start on Onboarding when tier == NONE (no brightness-write access), else the Dashboard. */
@Composable
private fun rememberStartDestination(): String {
    val context = LocalContext.current
    return remember {
        val tier = AppModule(context.applicationContext).privilegeManager.currentTier()
        if (tier == Tier.NONE) AppRoute.Onboarding.route else AppRoute.Dashboard.route
    }
}
