package com.tideo.autobrightness.app.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.runtime.CircadianLocationStatus
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.state.ServiceHealthUiState
import com.tideo.autobrightness.app.ui.onboarding.OnboardingContent
import com.tideo.autobrightness.app.ui.onboarding.OnboardingUiState
import com.tideo.autobrightness.app.ui.screens.DashboardContent
import com.tideo.autobrightness.app.ui.screens.MenuContent
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A3 acceptance (D-156): Dashboard, Menu, Onboarding under TalkBack gate. */
@RunWith(RobolectricTestRunner::class)
class ScreensA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val fullDashboard = DashboardUiState(
        serviceEnabled = true,
        tier = Tier.ELEVATED,
        serviceRunning = true,
        pausedByOverride = true,
        rawLux = 120.0,
        smoothedLux = 118.0,
        currentBrightness = 88,
        targetBrightness = 90,
        circadianScale = 0.85,
        dimmingStrength = 12.0,
        activeContext = "Home",
        activeProfile = "Default",
        lastSampleMs = System.currentTimeMillis(),
        stale = true,
        health = ServiceHealthUiState(degradedMode = true, degradedReason = "sensor timeout"),
        circadianLocation = CircadianLocationStatus(),
    )

    private fun renderDashboard() {
        compose.setContent {
            TideoTheme {
                DashboardContent(
                    state = fullDashboard,
                    onToggleService = {}, onResume = {}, onOpenOnboarding = {}, onBack = {},
                    onResetToAuto = {}, canAddTile = true, canAddWidget = true,
                    onAddTile = {}, onAddWidget = {},
                )
            }
        }
    }

    @Test
    fun dashboard_allInteractiveNodesAreLabeled() {
        renderDashboard()
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun dashboard_staleBanner_isPoliteLiveRegion() {
        renderDashboard()
        compose.onNodeWithTag("stale_banner").assert(isPoliteLiveRegion())
    }

    @Test
    fun dashboard_overrideCard_isPoliteLiveRegion() {
        renderDashboard()
        compose.onNodeWithTag("override_card").assert(isPoliteLiveRegion())
    }

    @Test
    fun dashboard_circadianStaleHint_isPoliteLiveRegion() {
        renderDashboard()
        compose.onNodeWithTag("circadian_stale_hint").assert(isPoliteLiveRegion())
    }

    @Test
    fun menu_allInteractiveNodesAreLabeled() {
        compose.setContent {
            TideoTheme {
                MenuContent(
                    activeContext = "Home", manualOverride = true, tier = Tier.ELEVATED,
                    onNavigate = {}, onRecheckPermissions = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun menu_dashboardRow_isNavigable() {
        compose.setContent {
            TideoTheme {
                MenuContent(activeContext = null, onNavigate = {}, onRecheckPermissions = {})
            }
        }
        compose.onNodeWithTag("menu_${AppRoute.Dashboard.route}").assertExists()
    }

    @Test
    fun onboarding_allInteractiveNodesAreLabeled() {
        compose.setContent {
            TideoTheme {
                OnboardingContent(
                    state = OnboardingUiState(
                        notificationsGranted = false,
                        canWrite = false,
                        tier = Tier.NONE,
                        shizukuAvailability = ShizukuAvailability.RUNNING,
                        needsUsageAccess = true,
                        sideloaded = true,
                        adbCommand = "adb shell pm grant …",
                    ),
                    onRequestNotifications = {}, onRequestWriteSettings = {}, onRequestLocation = {},
                    onOpenAppInfo = {}, onCopyAdb = {}, onRequestShizuku = {}, onTryRoot = {},
                    onRequestUsageAccess = {}, onDone = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    private fun isPoliteLiveRegion() =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
}
