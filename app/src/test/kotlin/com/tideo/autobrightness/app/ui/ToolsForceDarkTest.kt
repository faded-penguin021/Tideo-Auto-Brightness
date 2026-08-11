package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D-172 acceptance: Force dark card (Shizuku). Switch drives opt-in; status shows live prop tri-state.
 */
@RunWith(RobolectricTestRunner::class)
class ToolsForceDarkTest {

    @get:Rule val compose = createComposeRule()

    private fun string(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun tools(
        enabled: Boolean = false,
        liveState: Boolean? = null,
        probed: Boolean = true,
        onSet: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {},
                    forceDarkEnabled = enabled,
                    forceDarkLiveState = liveState,
                    forceDarkProbed = probed,
                    onSetForceDarkEnabled = onSet,
                )
            }
        }
    }

    @Test fun switchReflectsTheStoredState() {
        tools(enabled = true)
        compose.onNodeWithTag("force_dark_toggle").performScrollTo().assertIsOn()
    }

    @Test fun togglingTheSwitchDrivesTheStore() {
        var latest: Boolean? = null
        tools(enabled = false) { latest = it }
        compose.onNodeWithTag("force_dark_toggle").performScrollTo().assertIsOff().performClick()
        assertEquals(true, latest)
    }

    @Test fun statusIsHiddenUntilTheProbeReturns() {
        tools(probed = false)
        compose.onNodeWithTag("force_dark_status").assertDoesNotExist()
    }

    @Test fun statusShowsTheLivePropState() {
        tools(liveState = true)
        compose.onNodeWithTag("force_dark_status").performScrollTo()
            .assertTextEquals(string(R.string.tools_force_dark_state_on))
    }

    @Test fun statusShowsUnreachableWhenShizukuIsDown() {
        tools(liveState = null, probed = true)
        compose.onNodeWithTag("force_dark_status").performScrollTo()
            .assertTextEquals(string(R.string.tools_force_dark_unreachable))
    }

    @Test fun forceDarkCardIsLabeledForTalkBack() {
        tools()
        compose.assertAllInteractiveNodesAreLabeled()
    }
}
