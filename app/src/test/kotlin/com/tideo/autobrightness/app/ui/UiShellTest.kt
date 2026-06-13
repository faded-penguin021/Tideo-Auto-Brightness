package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.ui.components.AabNavDrawer
import com.tideo.autobrightness.app.ui.onboarding.OnboardingContent
import com.tideo.autobrightness.app.ui.onboarding.OnboardingUiState
import com.tideo.autobrightness.app.ui.screens.DashboardContent
import com.tideo.autobrightness.app.ui.screens.PlaceholderScreen
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric compose smoke test (S11 acceptance): the Dashboard renders its live readout and every
 * target route navigates. Drives the stateless content composables + the [AppRoute] table directly,
 * so it exercises the shell without standing up the ViewModels / DataStore.
 */
@RunWith(RobolectricTestRunner::class)
class UiShellTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardContent_rendersLiveReadoutAndToggles() {
        var toggled: Boolean? = null
        compose.setContent {
            MaterialTheme {
                DashboardContent(
                    state = DashboardUiState(
                        serviceEnabled = false,
                        tier = Tier.BASIC,
                        serviceRunning = true,
                        smoothedLux = 42.0,
                        currentBrightness = 100,
                        targetBrightness = 120,
                    ),
                    onToggleService = { toggled = it },
                    onPause = {},
                    onResume = {},
                    onOpenOnboarding = {},
                    onNavigate = {},
                )
            }
        }

        compose.onNodeWithText("Tideo Auto Brightness").assertExists()
        compose.onNodeWithTag("tier_badge").assertExists()
        compose.onNodeWithTag("service_switch").performClick()
        assertEquals(true, toggled)

        // The Profiles + Contexts hero cards (G2-F18) replace flat nav buttons on the Dashboard.
        compose.onNodeWithTag("hero_profiles").assertExists()
        compose.onNodeWithTag("hero_contexts").assertExists()

        // Every tunable destination is reachable from the AAB-Menu drawer.
        AppRoute.dashboardDestinations.forEach {
            compose.onNodeWithTag("nav_${it.route}").assertExists()
        }
    }

    @Test
    fun navDrawer_navigatesToEveryDestination() {
        // The AAB-Menu drawer (rebuild of scene menu.md) is stateless, so drive it directly —
        // deterministic, no modal open-animation. Confirms every destination + Recheck Permissions.
        var navigated: AppRoute? = null
        var recheck = false
        compose.setContent {
            MaterialTheme {
                AabNavDrawer(
                    current = AppRoute.Dashboard,
                    onNavigate = { navigated = it },
                    onRecheckPermissions = { recheck = true },
                )
            }
        }

        // Invoke the OnClick semantics action directly: deterministic in Robolectric (no gesture
        // injection / display dependency, which NavigationDrawerItem's selectable does not honour here).
        AppRoute.dashboardDestinations.forEach { route ->
            compose.onNodeWithTag("nav_${route.route}").performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(route, navigated)
        }
        compose.onNodeWithTag("nav_recheck_permissions").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(recheck)
    }

    @Test
    fun heroCards_navigateToProfilesAndContexts() {
        var navigated: AppRoute? = null
        compose.setContent {
            MaterialTheme {
                DashboardContent(
                    state = DashboardUiState(tier = Tier.BASIC, serviceRunning = true),
                    onToggleService = {},
                    onPause = {},
                    onResume = {},
                    onOpenOnboarding = {},
                    onNavigate = { navigated = it },
                )
            }
        }

        compose.onNodeWithTag("hero_profiles").performScrollTo().performClick()
        assertEquals(AppRoute.Profiles, navigated)
        compose.onNodeWithTag("hero_contexts").performScrollTo().performClick()
        assertEquals(AppRoute.Contexts, navigated)
    }

    @Test
    fun routeTable_navigatesToEveryDestination() {
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = AppRoute.Dashboard.route) {
                AppRoute.entries.forEach { route ->
                    composable(route.route) { PlaceholderScreen(route.label, route.owner) }
                }
            }
        }

        AppRoute.entries.filter { it != AppRoute.Dashboard }.forEach { route ->
            compose.runOnUiThread { nav.navigate(route.route) }
            compose.onNodeWithText(route.label).assertExists()
        }
    }

    @Test
    fun onboardingContent_rendersStepsAndCompletes() {
        var done = false
        compose.setContent {
            MaterialTheme {
                OnboardingContent(
                    state = OnboardingUiState(
                        notificationsGranted = false,
                        canWrite = false,
                        tier = Tier.NONE,
                        needsUsageAccess = true,
                        adbCommand = "adb shell pm grant ...",
                    ),
                    onRequestNotifications = {},
                    onRequestWriteSettings = {},
                    onCopyAdb = {},
                    onRequestShizuku = {},
                    onTryRoot = {},
                    onRequestUsageAccess = {},
                    onDone = { done = true },
                )
            }
        }

        compose.onNodeWithTag("step_notifications").assertExists()
        compose.onNodeWithTag("step_write_settings").assertExists()
        compose.onNodeWithTag("step_elevated").assertExists()
        compose.onNodeWithTag("step_usage_access").assertExists()
        compose.onNodeWithTag("onboarding_done").performScrollTo().performClick()
        assertTrue(done)
    }
}
