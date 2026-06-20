package com.tideo.autobrightness.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.ui.components.AabMotion
import com.tideo.autobrightness.app.ui.onboarding.OnboardingScreen
import com.tideo.autobrightness.app.ui.screens.CircadianScreen
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessScreen
import com.tideo.autobrightness.app.ui.screens.DashboardScreen
import com.tideo.autobrightness.app.ui.screens.LiveDebugScreen
import com.tideo.autobrightness.app.ui.screens.MenuScreen
import com.tideo.autobrightness.app.ui.screens.MiscScreen
import com.tideo.autobrightness.app.ui.screens.PlaceholderScreen
import com.tideo.autobrightness.app.ui.screens.ProfilesContextsScreen
import com.tideo.autobrightness.app.ui.screens.ReactivityScreen
import com.tideo.autobrightness.app.ui.screens.SuperDimmingScreen
import com.tideo.autobrightness.app.ui.screens.ToolsScreen
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * The navigation shell over the [AppRoute] target screen set. S12.6a makes [AppRoute.Menu] the home
 * hub and the start destination after onboarding; every other screen is reached from it and returns
 * to it (see [navigateTopLevel]). First-run routing sends the user to Onboarding when tier == NONE.
 * About & Guide remains a labelled [PlaceholderScreen] until S13.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = rememberStartDestination(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // S13c (m3_audit §4 "No motion"): consistent screen enter/exit via the S13b motion helpers.
        enterTransition = { AabMotion.screenEnter },
        exitTransition = { AabMotion.screenExit },
        popEnterTransition = { AabMotion.screenEnter },
        popExitTransition = { AabMotion.screenExit },
    ) {
        composable(AppRoute.Menu.route) { MenuScreen(navController) }
        composable(AppRoute.Dashboard.route) { DashboardScreen(navController) }
        composable(AppRoute.Onboarding.route) { OnboardingScreen(navController) }
        composable(AppRoute.CurveBrightness.route) { CurveBrightnessScreen(navController) }
        composable(AppRoute.Reactivity.route) { ReactivityScreen(navController) }
        composable(AppRoute.SuperDimming.route) { SuperDimmingScreen(navController) }
        composable(AppRoute.Circadian.route) { CircadianScreen(navController) }
        composable(AppRoute.Misc.route) { MiscScreen(navController) }
        composable(AppRoute.Tools.route) { ToolsScreen(navController) }
        composable(AppRoute.LiveDebug.route) { LiveDebugScreen(navController) }
        // S12.9f (D-070): Profiles + Contexts merged into one destination.
        composable(AppRoute.Profiles.route) { ProfilesContextsScreen(navController) }
        // S13-owned:
        composable(AppRoute.About.route) { PlaceholderScreen(AppRoute.About.label, AppRoute.About.owner) }
    }
}

/**
 * Navigate to a top-level destination from the Menu hub (or the Dashboard's quick links), always
 * rooting the back stack at [AppRoute.Menu] so a back press from any settings/tool screen returns to
 * the Menu, never the Dashboard (S12.6a owner decision 1, G2R-F1). `launchSingleTop` avoids stacking
 * duplicates when re-selecting the current destination.
 */
fun NavHostController.navigateTopLevel(route: AppRoute) {
    navigate(route.route) {
        popUpTo(AppRoute.Menu.route) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Finish onboarding (G2R-F57): land on the [AppRoute.Menu] hub — NOT the Dashboard — and drop
 * Onboarding from the back stack (`popUpTo … inclusive`) so the hardware Back from the Menu exits
 * the app rather than returning to a half-finished setup. Fixes the "first boot loads a
 * non-functional Dashboard you can't navigate away from" report.
 */
fun NavHostController.completeOnboarding() {
    navigate(AppRoute.Menu.route) {
        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** Start on Onboarding when tier == NONE (no brightness-write access), else the Menu hub. */
@Composable
private fun rememberStartDestination(): String {
    val context = LocalContext.current
    return remember {
        val tier = AppModule(context.applicationContext).privilegeManager.currentTier()
        if (tier == Tier.NONE) AppRoute.Onboarding.route else AppRoute.Menu.route
    }
}
