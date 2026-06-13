package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.ui.screens.AnimationDimmingContent
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessContent
import com.tideo.autobrightness.app.ui.screens.ReactivityContent
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S12 acceptance: invalid curve input renders a Tasker-faithful red [SettingsValidator] error in the
 * UI (task583/707), plus smoke coverage that the parameter/tools content composables render and edit.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun curveBrightness_invalidForm2C_rendersValidatorError() {
        // form2C (40) > zone1End (10) → task583 advisory error on the Zone-2-offset field.
        val invalid = AabSettings(zone1End = 10, form2C = 40)
        val errors = SettingsValidator.validate(invalid)
        assertTrue(errors.any { it.field == "form2C" }, "fixture should produce a form2C error")

        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(invalid, errors, onBack = {}, onUpdate = {})
            }
        }

        compose.onNodeWithTag("field_form2C").assertExists()
        compose.onNodeWithText("must be ≤ zone1End", substring = true).assertExists()
    }

    @Test
    fun curveBrightness_safetyWarning_rendersBanner() {
        // task707 safety warning surfaces as a banner on the Curve & Brightness screen.
        val errors = listOf(
            com.tideo.autobrightness.app.settings.FieldError(
                "safetyBrightness", "⚠️ Safety Warning: Brightness too low at 1000 Lux.",
            ),
        )
        compose.setContent { MaterialTheme { CurveBrightnessContent(AabSettings(), errors, {}, {}) } }
        compose.onNodeWithTag("error_safetyBrightness").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reactivity_detectOverridesToggle_isWiredAndEdits() {
        var captured: AabSettings? = null
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(detectOverrides = false),
                    onBack = {},
                    onUpdate = { transform -> captured = transform(AabSettings(detectOverrides = false)) },
                )
            }
        }
        compose.onNodeWithTag("switch_detectOverrides").performScrollTo().performClick()
        assertEquals(true, captured?.detectOverrides)
    }

    @Test
    fun animationDimming_dimmingRowsDisabledWithoutElevated() {
        compose.setContent {
            MaterialTheme {
                AnimationDimmingContent(
                    AabSettings(), tier = Tier.BASIC,
                    onBack = {}, onUpdate = {}, onOpenOnboarding = {},
                )
            }
        }
        // The grant link is shown when not ELEVATED.
        compose.onNodeWithTag("dimming_grant_link").performScrollTo().assertExists()
    }

    @Test
    fun tools_debugSelector_showsCurrentLabel() {
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    AabSettings(debugLevel = 3),
                    onBack = {}, onSetDebugLevel = {}, onRunWizard = { null }, onApplyWizard = {},
                )
            }
        }
        compose.onNodeWithText("Debug: Light Eval Thresholds").assertExists()
    }
}
