package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.DraftApplyBar
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A0 acceptance (D-156) — the S12.5b settings primitives are TalkBack-usable: every interactive
 * node announces a label, section headers are headings, and read-only label/value readouts merge
 * into one announcement. This is the worked template for the a11y backlog
 * (plans/a11y-diagnostics.md): render the unit's surface, then funnel it through the
 * [assertAllInteractiveNodesAreLabeled] gate plus targeted per-control assertions.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsControlsA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderControls() {
        compose.setContent {
            TideoTheme {
                Column {
                    SectionHeader("Curve maths", divider = true)
                    NumberSettingField(
                        label = "Zone 1 end",
                        value = 40,
                        onCommit = {},
                        help = R.string.a11y_back,
                    )
                    IntSliderSettingField(
                        label = "Samples",
                        value = 5,
                        range = 1..10,
                        onCommit = {},
                        help = R.string.a11y_back,
                    )
                    SwitchSettingRow(
                        label = "Super dimming",
                        checked = true,
                        onCheckedChange = {},
                        help = R.string.a11y_back,
                    )
                    DerivedReadout(label = "Derived form2A", value = "0.42", testTag = "derived")
                    DraftApplyBar(dirty = true, onApply = {}, onDiscard = {})
                }
            }
        }
    }

    @Test
    fun allInteractiveControlsAreLabeled() {
        renderControls()
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun sectionHeaderIsAHeading() {
        renderControls()
        compose.assertHeadingExists("Curve maths")
    }

    @Test
    fun helpButtonAnnouncesItsField() {
        renderControls()
        compose.onNodeWithTag("help_Samples")
            .assertContentDescriptionContains("Samples", substring = true)
    }

    @Test
    fun sliderAndSwitchAnnounceTheirLabels() {
        renderControls()
        compose.onNodeWithTag("Samples").assertContentDescriptionContains("Samples")
        compose.onNodeWithTag("Super dimming").assertContentDescriptionContains("Super dimming")
    }

    @Test
    fun derivedReadoutMergesLabelAndValue() {
        renderControls()
        compose.onNodeWithTag("derived")
            .assertTextContains("Derived form2A")
        compose.onNodeWithTag("derived")
            .assertTextContains("0.42")
    }
}
