package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.ui.components.AppPickerList
import com.tideo.autobrightness.app.ui.components.BrightnessInstrument
import com.tideo.autobrightness.app.ui.components.DayPicker
import com.tideo.autobrightness.app.ui.components.FlashPill
import com.tideo.autobrightness.app.ui.components.HeroNavCard
import com.tideo.autobrightness.app.ui.components.KeyValueRow
import com.tideo.autobrightness.app.ui.components.NavRow
import com.tideo.autobrightness.app.ui.components.TimeField
import com.tideo.autobrightness.app.ui.components.TimeTokenRow
import com.tideo.autobrightness.app.ui.components.TriggerSection
import com.tideo.autobrightness.app.ui.components.UsageAccessPromptCard
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A1 acceptance (D-156). Renders the remaining shared components under the [assertAllInteractiveNodesAreLabeled]
 * gate — icon-only toggleables (the master service switch, the trigger switch, the app-picker
 * checkboxes) were the flagged violations; clickable nav rows/cards/buttons already merge their text
 * labels. Each fix gets one targeted assertion below. Template: SettingsControlsA11yTest (A0).
 */
@RunWith(RobolectricTestRunner::class)
class ComponentsA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderComponents() {
        compose.setContent {
            TideoTheme {
                Column {
                    NavRow(label = "Open tools", onClick = {})
                    HeroNavCard(title = "Profiles", subtitle = "Save & load", icon = Icons.Filled.Person, onClick = {})
                    TriggerSection(title = "Time window", enabled = true, onEnabledChange = {}, key = "time") {}
                    TimeField(label = "From", value = "08:00", tag = "from", onSet = {})
                    TimeTokenRow(which = "from", solarLabel = null, onPick = {})
                    DayPicker(selected = setOf(1), onToggle = {})
                    UsageAccessPromptCard(messageRes = R.string.a11y_back, cardTag = "usage", buttonTag = "usage_btn", onRequest = {})
                    AppPickerList(
                        apps = listOf(AppEntry("com.x", "Example App")),
                        selected = emptySet(),
                        onToggle = { _, _ -> },
                    )
                    BrightnessInstrument(state = DashboardUiState(serviceEnabled = true), onToggleService = {})
                    KeyValueRow(key = "Current lux", value = "1234", testTag = "kv_lux")
                    FlashPill(text = "Applied") {}
                }
            }
        }
    }

    @Test
    fun allInteractiveComponentsAreLabeled() {
        renderComponents()
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun triggerSwitchAnnouncesItsTitle() {
        renderComponents()
        compose.onNodeWithTag("trigger_toggle_time").assertContentDescriptionContains("Time window")
    }

    @Test
    fun appCheckboxAnnouncesItsAppLabel() {
        renderComponents()
        compose.onNodeWithTag("app_check_com.x").assertContentDescriptionContains("Example App")
    }

    @Test
    fun serviceSwitchIsLabeled() {
        renderComponents()
        compose.onNodeWithTag("service_switch").assertContentDescriptionContains("Auto brightness service")
    }

    @Test
    fun keyValueRowMergesKeyAndValue() {
        renderComponents()
        // Merged into one announcement: the tagged row node carries both the (uppercased) key and value.
        compose.onNodeWithTag("kv_lux").assertTextContains("CURRENT LUX")
        compose.onNodeWithTag("kv_lux").assertTextContains("1234")
    }

    @Test
    fun flashPillIsAPoliteLiveRegion() {
        renderComponents()
        compose.onNodeWithTag("aab_flash").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }
}
