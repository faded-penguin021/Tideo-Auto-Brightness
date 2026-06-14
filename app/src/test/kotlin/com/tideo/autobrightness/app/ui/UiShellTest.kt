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
import com.tideo.autobrightness.app.navigation.completeOnboarding
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
    fun menuContexts_distinguishesManualOverrideFromActiveContextRule() {
        // S12.7a/F46: a manual profile load IS the override; a context rule being active is NOT.
        compose.setContent {
            MaterialTheme {
                MenuContent(activeContext = "Evening", manualOverride = false, onNavigate = {}, onRecheckPermissions = {})
            }
        }
        // A context rule active is surfaced as automation, never as an "override".
        compose.onNodeWithText("Context active: Evening").assertExists()
    }

    @Test
    fun menuContexts_showsManualOverrideWhenLatched() {
        // S12.7a/F46: with the manual-load lock latched, the card says override + Resume.
        compose.setContent {
            MaterialTheme {
                MenuContent(activeContext = null, manualOverride = true, onNavigate = {}, onRecheckPermissions = {})
            }
        }
        compose.onNodeWithText("Manual override active — Resume on Profiles").assertExists()
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
                    onRequestLocation = {},
                    onOpenAppInfo = {},
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
        // G2R-F41 (perm half): the Location grant step is part of Setup.
        compose.onNodeWithTag("step_location").performScrollTo().assertExists()
        compose.onNodeWithTag("step_elevated").assertExists()
        compose.onNodeWithTag("step_usage_access").assertExists()
        compose.onNodeWithTag("onboarding_done").performScrollTo().performClick()
        assertTrue(done)
    }

    @Test
    fun onboardingContent_showsRestrictedSettingsHintWhenSideloaded() {
        // G2R-F33: a sideloaded install surfaces the "Allow restricted settings" guidance; a store
        // install does not.
        var appInfoOpened = false
        compose.setContent {
            MaterialTheme {
                OnboardingContent(
                    state = OnboardingUiState(sideloaded = true),
                    onRequestNotifications = {},
                    onRequestWriteSettings = {},
                    onRequestLocation = {},
                    onOpenAppInfo = { appInfoOpened = true },
                    onCopyAdb = {},
                    onRequestShizuku = {},
                    onTryRoot = {},
                    onRequestUsageAccess = {},
                    onDone = {},
                )
            }
        }
        compose.onNodeWithTag("restricted_settings_hint").assertExists()
        compose.onNodeWithTag("open_app_info").performScrollTo().performClick()
        assertTrue(appInfoOpened)
    }

    @Test
    fun completeOnboarding_landsOnMenuAndDropsOnboarding() {
        // G2R-F57: finishing onboarding routes to the Menu hub, not the Dashboard, and Onboarding is
        // removed from the back stack so Back from the Menu exits rather than returning to setup.
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = AppRoute.Onboarding.route) {
                AppRoute.entries.forEach { route ->
                    composable(route.route) { PlaceholderScreen(route.label, route.owner) }
                }
            }
        }

        compose.runOnUiThread { nav.completeOnboarding() }
        compose.onNodeWithText(AppRoute.Menu.label).assertExists()
        assertEquals(AppRoute.Menu.route, nav.currentDestination?.route)
        // Onboarding is gone from the back stack: a back press has nothing to pop above the Menu.
        var popped = true
        compose.runOnUiThread { popped = nav.popBackStack() }
        assertTrue(!popped)
    }
}
