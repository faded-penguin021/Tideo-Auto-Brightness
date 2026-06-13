package com.tideo.autobrightness.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.ui.onboarding.OnboardingScreen
import com.tideo.autobrightness.app.ui.screens.DashboardScreen
import com.tideo.autobrightness.app.ui.screens.PlaceholderScreen
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * The navigation shell over the [AppRoute] target screen set. S11 wires Dashboard + Onboarding for
 * real; the S12/S13-owned destinations resolve to a labelled [PlaceholderScreen] so navigation works
 * end-to-end. First-run routing sends the user to Onboarding when no write privilege exists yet.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = rememberStartDestination(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoute.Dashboard.route) { DashboardScreen(navController) }
        composable(AppRoute.Onboarding.route) { OnboardingScreen(navController) }
        AppRoute.dashboardDestinations.forEach { route ->
            composable(route.route) { PlaceholderScreen(route.label, route.owner) }
        }
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
