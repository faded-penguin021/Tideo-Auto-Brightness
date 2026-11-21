package com.tideo.autobrightness.app.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.ui.screens.SuperDimmingContent
import com.tideo.autobrightness.domain.brightness.SoftwareDimming
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import org.robolectric.RobolectricTestRunner

/** DE-046: the Super Dimming chart plots the dimming mode the runtime would use. */
@RunWith(RobolectricTestRunner::class)
class DimmingChartModeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(settings: AabSettings) = compose.setContent {
        MaterialTheme {
            SuperDimmingContent(
                settings, settings, epoch = 0, dirty = false, tier = Tier.ELEVATED,
                onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
            )
        }
    }

    @Test
    fun pwmDraft_chartDescribesPwmLevel() {
        show(AabSettings(pwmSensitive = true, dimmingEnabled = false, dimmingThreshold = 40))
        compose.onNodeWithContentDescription("PWM mode", substring = true).assertIsDisplayed()
    }

    @Test
    fun ordinaryDraft_chartKeepsTaskerDescription() {
        show(AabSettings(pwmSensitive = false, dimmingEnabled = true, dimmingThreshold = 40))
        compose.onNodeWithContentDescription("reaching", substring = true).assertIsDisplayed()
    }

    @Test
    fun pwmPoints_areTheRuntimeLevelOverTheTaskerRange() {
        val points = pwmDimLevelPoints(minBrightness = 5, dimmingThreshold = 40, pwmExponent = 0.8, span = 35)
        assertEquals((5..40).map { it.toFloat() }, points.map { it.x })
        for (p in points.dropLast(1)) {
            val expected = SoftwareDimming.finalDimLevel(p.x.toDouble(), isElevated = true, dimmingThreshold = 40.0, pwmExp = 0.8)
            assertEquals(expected.toFloat(), p.y, 1e-4f)
        }
        assertEquals(0f, points.last().y)
    }

    private fun showBelowMinimum(pwm: Boolean) = compose.setContent {
        MaterialTheme {
            DimmingChart(
                minBrightness = 25, dimmingThreshold = 4, dimmingExponent = 2.5, dimmingStrength = 80,
                currentBrightness = 25, pwmSensitive = pwm,
            )
        }
    }

    @Test
    fun nowMarker_survivesADraftThresholdBelowTheMinimum() {
        showBelowMinimum(pwm = false)
        compose.onNodeWithContentDescription("below 4", substring = true).assertIsDisplayed()
    }

    @Test
    fun pwmNowMarker_survivesADraftThresholdBelowTheMinimum() {
        showBelowMinimum(pwm = true)
        compose.onNodeWithContentDescription("below 4", substring = true).assertIsDisplayed()
    }
}
