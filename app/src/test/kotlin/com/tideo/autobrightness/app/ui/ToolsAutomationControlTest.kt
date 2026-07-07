package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D-157 (U4) acceptance. The Tools → "Automation control" card: the Switch reflects and drives the
 * opt-in [ControlPrefsStore][com.tideo.autobrightness.app.control.ControlPrefsStore] gate, and the
 * "Show actions" button opens the verb-list help dialog. Renders under the a11y gate so the new
 * interactive nodes stay TalkBack-labeled (D-156 convention).
 */
@RunWith(RobolectricTestRunner::class)
class ToolsAutomationControlTest {

    @get:Rule val compose = createComposeRule()

    private fun tools(enabled: Boolean, onSet: (Boolean) -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {},
                    externalControlEnabled = enabled,
                    onSetExternalControlEnabled = onSet,
                )
            }
        }
    }

    @Test fun switchReflectsTheStoredState() {
        tools(enabled = true)
        compose.onNodeWithTag("automation_toggle").performScrollTo().assertIsOn()
    }

    @Test fun togglingTheSwitchDrivesTheStore() {
        var latest: Boolean? = null
        tools(enabled = false) { latest = it }
        compose.onNodeWithTag("automation_toggle").performScrollTo().assertIsOff().performClick()
        assertEquals(true, latest)
    }

    @Test fun showActionsOpensTheHelpDialog() {
        tools(enabled = false)
        compose.onNodeWithTag("automation_actions_dialog").assertDoesNotExist()
        compose.onNodeWithTag("automation_show_actions").performScrollTo().performClick()
        compose.onNodeWithTag("automation_actions_dialog").assertIsDisplayed()
        compose.onNodeWithTag("automation_actions_close").performClick()
        compose.onNodeWithTag("automation_actions_dialog").assertDoesNotExist()
    }

    @Test fun automationCardIsLabeledForTalkBack() {
        tools(enabled = false)
        compose.assertAllInteractiveNodesAreLabeled()
    }
}
