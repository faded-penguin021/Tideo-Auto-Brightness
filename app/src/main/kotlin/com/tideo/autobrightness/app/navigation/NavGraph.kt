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
import com.tideo.autobrightness.app.ui.screens.AboutScreen
import com.tideo.autobrightness.app.ui.screens.CircadianScreen
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessScreen
import com.tideo.autobrightness.app.ui.screens.DashboardScreen
import com.tideo.autobrightness.app.ui.screens.LiveDebugScreen
import com.tideo.autobrightness.app.ui.screens.MenuScreen
import com.tideo.autobrightness.app.ui.screens.MiscScreen
import com.tideo.autobrightness.app.ui.screens.PrivilegedDisplayScreen
import com.tideo.autobrightness.app.ui.screens.ProfilesContextsScreen
import com.tideo.autobrightness.app.ui.screens.ReactivityScreen
import com.tideo.autobrightness.app.ui.screens.SuperDimmingScreen
import com.tideo.autobrightness.app.ui.screens.ToolsScreen
import com.tideo.autobrightness.app.ui.screens.UserGuideScreen
import com.tideo.autobrightness.platform.privilege.Tier

/** Navigation shell: Menu hub (S12.6a), onboarding when tier == NONE. */
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
        // D-149: always registered; the screen self-guards below ELEVATED (grant card).
        composable(AppRoute.PrivilegedDisplay.route) { PrivilegedDisplayScreen(navController) }
        // S13d: real static reference screens (charts + About/Guide replace the placeholders).
        composable(AppRoute.UserGuide.route) { UserGuideScreen(navController) }
        composable(AppRoute.About.route) { AboutScreen(navController) }
    }
}

/** Top-level nav to Menu hub (S12.6a, G2R-F1). */
fun NavHostController.navigateTopLevel(route: AppRoute) {
    navigate(route.route) {
        popUpTo(AppRoute.Menu.route) { inclusive = false }
        launchSingleTop = true
    }
}

/** Finish onboarding → User Guide (G2R-F57, G2R-F80). */
fun NavHostController.completeOnboarding() {
    navigate(AppRoute.Menu.route) {
        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
        launchSingleTop = true
    }
    navigate(AppRoute.UserGuide.route) { launchSingleTop = true }
}

@Composable
private fun rememberStartDestination(): String {
    val context = LocalContext.current
    return remember {
        val tier = AppModule(context.applicationContext).privilegeManager.currentTier()
        if (tier == Tier.NONE) AppRoute.Onboarding.route else AppRoute.Menu.route
    }
}
