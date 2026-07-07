package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.state.LiveDebugUiState
import com.tideo.autobrightness.app.state.PrivilegedDisplayUiState
import com.tideo.autobrightness.app.ui.screens.LiveDebugContent
import com.tideo.autobrightness.app.ui.screens.MiscContent
import com.tideo.autobrightness.app.ui.screens.PrivilegedDisplayContent
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A5 acceptance (D-156). Renders the group-2 settings screens — Misc, Tools, Privileged Display,
 * Live Debug — under the [assertAllInteractiveNodesAreLabeled] gate, plus heading assertions for the
 * `SectionHeader` sections. Most controls are the A0 primitives, but each of Tools / Privileged
 * Display / Live Debug hosts a RAW M3 [androidx.compose.material3.Slider] whose visible label is a
 * sibling `Text` (does not merge onto the slider node) — the audit flagged those, so A5 gave each its
 * own `a11y_*` contentDescription; the targeted assertions below lock that in. Template:
 * SettingsControlsA11yTest (A0) / SettingsScreensA11yTest (A4).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensGroup2A11yTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun misc_allInteractiveNodesAreLabeled() {
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(), AabSettings(), emptyList<FieldError>(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onReset = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun misc_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(), AabSettings(), emptyList<FieldError>(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.assertHeadingExists("Brightness range")
        compose.assertHeadingExists("Animation")
        compose.assertHeadingExists("Notifications")
    }

    @Test
    fun tools_allInteractiveNodesAreLabeled() {
        compose.setContent {
            MaterialTheme {
                ToolsContent(onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {})
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun tools_powerHeaderIsAHeading() {
        compose.setContent {
            MaterialTheme {
                ToolsContent(onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {})
            }
        }
        compose.assertHeadingExists("Power-draw calibration")
    }

    @Test
    fun tools_wizardTauSliderIsLabeled() {
        compose.setContent {
            MaterialTheme {
                ToolsContent(onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {})
            }
        }
        compose.onNodeWithTag("wizard_tau").assertContentDescriptionContains("Curve fit inertia")
    }

    @Test
    fun privilegedDisplay_allInteractiveNodesAreLabeled_grantCard() {
        // Below ELEVATED renders the three-channel grant card (adb copy / Shizuku / root buttons).
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(state = PrivilegedDisplayUiState(tier = Tier.BASIC), onBack = {})
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun privilegedDisplay_allInteractiveNodesAreLabeled_elevated() {
        // ELEVATED + HDR renders every profile control incl. the raw night-light temperature slider.
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(tier = Tier.ELEVATED, hdrAvailable = true),
                    onBack = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun privilegedDisplay_sectionHeadersAreHeadings() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(tier = Tier.ELEVATED, hdrAvailable = true),
                    onBack = {},
                )
            }
        }
        compose.assertHeadingExists("Night Light")
        compose.assertHeadingExists("Color")
        compose.assertHeadingExists("Screen")
        compose.assertHeadingExists("Experimental")
    }

    @Test
    fun privilegedDisplay_nightLightTempSliderIsLabeled() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(tier = Tier.ELEVATED, hdrAvailable = true),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("slider_nightLightTemp")
            .assertContentDescriptionContains("Night light color temperature")
    }

    @Test
    fun liveDebug_allInteractiveNodesAreLabeled() {
        // Includes the debug-category dropdown button, the global-flash button, and the panic slider.
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(state = LiveDebugUiState(), onSelectDebug = {}, onBack = {})
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun liveDebug_panicSensitivitySliderIsLabeled() {
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(state = LiveDebugUiState(), onSelectDebug = {}, onBack = {})
            }
        }
        compose.onNodeWithTag("panic_sensitivity_slider")
            .assertContentDescriptionContains("Panic gesture sensitivity")
    }
}
