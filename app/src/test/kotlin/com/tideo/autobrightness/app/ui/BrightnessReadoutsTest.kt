package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.state.LiveDebugUiState
import com.tideo.autobrightness.app.ui.components.BrightnessInstrument
import com.tideo.autobrightness.app.ui.components.CircadianDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.SuperDimmingDiagnosticCardContent
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessContent
import com.tideo.autobrightness.app.ui.screens.LiveDebugContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Light refactor Unit 2: each readout names the value it shows (D2). */
@RunWith(RobolectricTestRunner::class)
class BrightnessReadoutsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun liveDebug(p: PipelineState) = compose.setContent {
        MaterialTheme {
            LiveDebugContent(state = LiveDebugUiState(pipeline = p, serviceRunning = true), onSelectDebug = {}, onBack = {})
        }
    }

    @Test
    fun liveDebug_labelsTheTwoValues_hardwareAndPerceived() {
        liveDebug(PipelineState(lastAppliedBrightness = 88, targetBrightness = 33))
        compose.onNodeWithTag("debug_current_bright").performScrollTo().assertTextContains("Hardware (written): 88")
        compose.onNodeWithTag("debug_target_bright").performScrollTo().assertTextContains("Perceived: 33")
    }

    @Test
    fun liveDebug_perceivedLine_showsAndroidsReadingWhileHandedOff() {
        liveDebug(PipelineState(handedOff = true, targetBrightness = 200, systemBrightness = 291))
        compose.onNodeWithTag("debug_current_bright").performScrollTo().assertTextContains("Hardware (written): 291")
        compose.onNodeWithTag("debug_target_bright").performScrollTo().assertTextContains("Perceived: 291")
    }

    @Test
    fun liveDebug_perceivedLine_fallsBackToTheAppliedBaseline() {
        liveDebug(PipelineState(lastAppliedBrightness = 88))
        compose.onNodeWithTag("debug_target_bright").performScrollTo().assertTextContains("Perceived: 88")
    }

    @Test
    fun curveCard_labelsItsValuePerceived() {
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    AabSettings(minBrightness = 10, maxBrightness = 255), AabSettings(), emptyList(),
                    epoch = 0, dirty = false, onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    live = PipelineState(smoothedLux = 123.4, lastAppliedBrightness = 88, targetBrightness = 33),
                )
            }
        }
        compose.onNodeWithTag("diag_curve_current_bright").performScrollTo()
            .assertTextContains("Perceived brightness", substring = true)
            .assertTextContains("33", substring = true)
    }

    @Test
    fun circadianCard_namesTheHardwareValue() {
        val p = PipelineState(scaleDynamic = 1.5, scaleDynamicCompress = 0.8, lastAppliedBrightness = 120, targetBrightness = 90)
        compose.setContent {
            MaterialTheme { CircadianDiagnosticCardContent(p, minBrightness = 10, maxBrightness = 255, timeLabel = "14:30") }
        }
        compose.onNodeWithTag("diag_circadian_true").assertTextContains("120 hardware brightness", substring = true)
    }

    @Test
    fun dimmingCard_namesTheHardwareValue() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    SuperDimmingDiagnosticCardContent(PipelineState(dimmingDS = 45.6, lastAppliedBrightness = 8, targetBrightness = 5))
                }
            }
        }
        compose.onNodeWithTag("diag_dimming_abs").assertTextContains("8 hardware brightness", substring = true)
    }

    @Test
    fun liveMarker_isThePerceivedValue_whileTheServiceRunsAndTideoOwnsBrightness() {
        val on = PipelineState(serviceOn = true, lastAppliedBrightness = 88, targetBrightness = 33)
        assertEquals(33, on.liveMarkerBrightness)
        assertEquals(88, on.copy(targetBrightness = null).liveMarkerBrightness, "the applied-baseline fallback")
        assertNull(on.copy(serviceOn = false).liveMarkerBrightness)
        assertNull(on.copy(handedOff = true, systemBrightness = 291).liveMarkerBrightness)
    }

    @Test
    fun dashboard_showsThePerceivedValue_underItsName() {
        compose.setContent {
            MaterialTheme {
                BrightnessInstrument(DashboardUiState(serviceEnabled = true, shownBrightness = 33, currentBrightness = 88), onToggleService = {})
            }
        }
        compose.onNodeWithText("PERCEIVED BRIGHTNESS").assertExists()
        compose.onNodeWithTag("dashboard_brightness").assertTextEquals("33")
    }
}
