package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.navigation.navigateTopLevel
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.ui.onboarding.OnboardingContent
import com.tideo.autobrightness.app.ui.onboarding.OnboardingUiState
import com.tideo.autobrightness.app.ui.screens.DashboardContent
import com.tideo.autobrightness.app.ui.screens.MenuContent
import com.tideo.autobrightness.app.ui.screens.PlaceholderScreen
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric compose smoke test: the Menu hub renders + navigates, the Dashboard renders its live
 * readout, the S12.6a renames resolve, and back from a settings screen lands on the Menu. Drives the
 * stateless content composables + the [AppRoute] table directly, so it exercises the shell without
 * standing up the ViewModels / DataStore.
 */
@RunWith(RobolectricTestRunner::class)
class UiShellTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun menuContent_rendersHeroCardsAndNavigatesEveryDestination() {
        // S12.6a (G2R-F2): the Profiles + Contexts hero cards live on the Menu hub, not the Dashboard.
        var navigated: AppRoute? = null
        var recheck = false
        compose.setContent {
            MaterialTheme {
                MenuContent(
                    activeContext = null,
                    onNavigate = { navigated = it },
                    onRecheckPermissions = { recheck = true },
                )
            }
        }

        compose.onNodeWithTag("hero_profiles").performScrollTo().performClick()
        assertEquals(AppRoute.Profiles, navigated)
        compose.onNodeWithTag("hero_contexts").performScrollTo().performClick()
        assertEquals(AppRoute.Contexts, navigated)

        // Every plain nav row (Dashboard + Settings + Info & Help groups) navigates.
        AppRoute.menuNavDestinations.forEach { route ->
            compose.onNodeWithTag("menu_${route.route}").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            assertEquals(route, navigated)
        }
        compose.onNodeWithTag("menu_recheck_permissions").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(recheck)
    }

    @Test
    fun renamedRoutes_resolveToTaskerNames() {
        // S12.6a (G2R-F3/F4): the routes carry the Tasker names.
        assertEquals("super_dimming", AppRoute.SuperDimming.route)
        assertEquals("Super Dimming", AppRoute.SuperDimming.label)
        assertEquals("circadian", AppRoute.Circadian.route)
        assertEquals("Circadian", AppRoute.Circadian.label)
    }

    @Test
    fun dashboardContent_rendersLiveReadoutAndToggles() {
        var toggled: Boolean? = null
        var backed = false
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
                    onBack = { backed = true },
                )
            }
        }

        compose.onNodeWithText("Dashboard").assertExists()
        compose.onNodeWithTag("tier_badge").assertExists()
        compose.onNodeWithTag("service_switch").performClick()
        assertEquals(true, toggled)
        // The live last-sample age renders (G2R-F5).
        compose.onNodeWithTag("last_sample_age").performScrollTo().assertExists()
        // Back returns to the Menu hub.
        compose.onNodeWithTag("back_button").performClick()
        assertTrue(backed)
    }

    @Test
    fun startsOnMenu_andBackFromSettingsLandsOnMenu() {
        // Menu is the start destination; navigating to a settings screen and pressing back via the
        // Menu-rooted pop returns to the Menu (S12.6a owner decision 1, G2R-F1).
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = AppRoute.Menu.route) {
                AppRoute.entries.forEach { route ->
                    composable(route.route) { PlaceholderScreen(route.label, route.owner) }
                }
            }
        }

        compose.onNodeWithText(AppRoute.Menu.label).assertExists()
        compose.runOnUiThread { nav.navigateTopLevel(AppRoute.SuperDimming) }
        compose.onNodeWithText(AppRoute.SuperDimming.label).assertExists()
        compose.runOnUiThread { nav.popBackStack() }
        compose.onNodeWithText(AppRoute.Menu.label).assertExists()
    }

    @Test
    fun routeTable_navigatesToEveryDestination() {
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = AppRoute.Menu.route) {
                AppRoute.entries.forEach { route ->
                    composable(route.route) { PlaceholderScreen(route.label, route.owner) }
                }
            }
        }

        AppRoute.entries.filter { it != AppRoute.Menu }.forEach { route ->
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
