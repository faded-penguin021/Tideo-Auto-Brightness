package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.ui.screens.CircadianContent
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessContent
import com.tideo.autobrightness.app.ui.screens.ReactivityContent
import com.tideo.autobrightness.app.ui.screens.SuperDimmingContent
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A4 acceptance (D-156). Renders the group-1 settings screens — Curve & Brightness, Reactivity,
 * Circadian, Super Dimming — under the [assertAllInteractiveNodesAreLabeled] gate, plus a heading
 * assertion per screen section so TalkBack users can jump section-to-section. The controls are the
 * A0 primitives (labeled sliders/switches) and text-carrying buttons, so no screen-local label
 * fixes were needed; this locks that in. Template: SettingsControlsA11yTest (A0) / SettingsScreensTest.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensA11yTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun curveBrightness_allInteractiveNodesAreLabeled() {
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    AabSettings(), AabSettings(), emptyList<FieldError>(),
                    epoch = 0, dirty = false, onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    onReset = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun curveBrightness_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    AabSettings(), AabSettings(), emptyList<FieldError>(),
                    epoch = 0, dirty = false, onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.assertHeadingExists("Curve zones")
        compose.assertHeadingExists("Derived (continuity)")
    }

    @Test
    fun reactivity_allInteractiveNodesAreLabeled() {
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onReset = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun reactivity_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.assertHeadingExists("Reactivity thresholds")
        compose.assertHeadingExists("Sensor smoothing")
        compose.assertHeadingExists("Override & trust")
    }

    @Test
    fun circadian_allInteractiveNodesAreLabeled() {
        // Default state renders every section incl. the date/location card and the geo-IP toggle.
        compose.setContent {
            MaterialTheme {
                CircadianContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onReset = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun circadian_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                CircadianContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.assertHeadingExists("Circadian scaling")
        compose.assertHeadingExists("Compression taper")
        compose.assertHeadingExists("Date & location")
    }

    @Test
    fun superDimming_allInteractiveNodesAreLabeled_basic() {
        // BASIC tier shows the grant link (dimming rows disabled) — audit that surface too.
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.BASIC,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                    onReset = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun superDimming_allInteractiveNodesAreLabeled_elevated() {
        // ELEVATED tier enables every dimming control.
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.ELEVATED,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun superDimming_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.ELEVATED,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                )
            }
        }
        compose.assertHeadingExists("Super dimming")
        compose.assertHeadingExists("PWM (flicker) handling")
        compose.assertHeadingExists("Circadian dim spread")
    }
}
