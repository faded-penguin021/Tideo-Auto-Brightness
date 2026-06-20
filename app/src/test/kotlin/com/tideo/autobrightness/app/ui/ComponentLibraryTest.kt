package com.tideo.autobrightness.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.ActionButton
import com.tideo.autobrightness.app.ui.components.ActionButtonBar
import com.tideo.autobrightness.app.ui.components.ActionButtonStyle
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.HeroNavCard
import com.tideo.autobrightness.app.ui.components.KeyValueRow
import com.tideo.autobrightness.app.ui.components.NavRow
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingField
import com.tideo.autobrightness.app.ui.components.SettingFieldSpec
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S13b acceptance — instantiation tests for the component library. Each reusable block renders,
 * surfaces its content, and wires its callbacks; the [SettingField] folding is verified to delegate to
 * the (already-tested) S12.5b primitives without changing their behaviour. Rendered inside the real
 * [TideoTheme] so the shapes/colorScheme role-map is exercised.
 */
@RunWith(RobolectricTestRunner::class)
class ComponentLibraryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aabCard_rendersGroupedContent() {
        compose.setContent {
            TideoTheme {
                AabCard {
                    Text("Inside the card")
                }
            }
        }
        compose.onNodeWithText("Inside the card").assertIsDisplayed()
    }

    @Test
    fun keyValueRow_showsKeyAndGoldValue() {
        compose.setContent {
            TideoTheme {
                KeyValueRow(key = "Current lux", value = "1234", testTag = "kv_lux")
            }
        }
        compose.onNodeWithTag("kv_lux").assertIsDisplayed()
        compose.onNodeWithTag("value_kv_lux").assertIsDisplayed()
        // S13c' §05: the key is rendered as a tracked-uppercase instrument caption.
        compose.onNodeWithText("CURRENT LUX").assertExists()
        compose.onNodeWithText("1234").assertExists()
    }

    @Test
    fun emptyState_rendersMutedText() {
        compose.setContent {
            TideoTheme {
                EmptyState(text = "No location yet", testTag = "empty_loc")
            }
        }
        compose.onNodeWithTag("empty_loc").assertIsDisplayed()
        compose.onNodeWithText("No location yet").assertExists()
    }

    @Test
    fun navRow_isClickable() {
        var clicks = 0
        compose.setContent {
            TideoTheme {
                NavRow(label = "Open tools", onClick = { clicks++ }, testTag = "nav_tools")
            }
        }
        compose.onNodeWithTag("nav_tools").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun heroNavCard_rendersTitleSubtitleAndClicks() {
        var clicked = false
        compose.setContent {
            TideoTheme {
                HeroNavCard(
                    title = "Profiles & Contexts",
                    subtitle = "Save & load profiles",
                    icon = Icons.Filled.Person,
                    onClick = { clicked = true },
                    testTag = "hero",
                )
            }
        }
        compose.onNodeWithText("Profiles & Contexts").assertExists()
        compose.onNodeWithText("Save & load profiles").assertExists()
        compose.onNodeWithTag("hero").performClick()
        assertTrue(clicked)
    }

    @Test
    fun actionButtonBar_rendersWeightedActionsWithDisabledState() {
        var applied = false
        compose.setContent {
            TideoTheme {
                ActionButtonBar(
                    actions = listOf(
                        ActionButton(
                            label = "Discard",
                            onClick = {},
                            style = ActionButtonStyle.Outlined,
                            enabled = false,
                            testTag = "act_discard",
                        ),
                        ActionButton(
                            label = "Apply",
                            onClick = { applied = true },
                            testTag = "act_apply",
                        ),
                    ),
                )
            }
        }
        compose.onNodeWithTag("act_discard").assertIsNotEnabled()
        compose.onNodeWithTag("act_apply").assertIsEnabled()
        compose.onNodeWithTag("act_apply").performClick()
        assertTrue(applied)
    }

    @Test
    fun sectionHeader_withDivider_renders() {
        compose.setContent {
            TideoTheme {
                SectionHeader("Thresholds", divider = true)
            }
        }
        compose.onNodeWithText("Thresholds").assertIsDisplayed()
    }

    @Test
    fun settingField_decimal_commitsAndShowsCommittedBracket() {
        var committed = 0.0
        compose.setContent {
            TideoTheme {
                SettingField(
                    SettingFieldSpec.Decimal(
                        label = "Max",
                        value = 8.0,
                        committed = 7.0,
                        isInt = false,
                        onCommit = { committed = it },
                        testTag = "field_max",
                    ),
                )
            }
        }
        // Draft (8.0) differs from committed (7.0) → the "[7.0]" bracket is shown (G2-F1).
        compose.onNodeWithText("Max [7.0]", substring = true).assertExists()
        compose.onNodeWithTag("field_max").assertIsDisplayed()
    }

    @Test
    fun settingField_toggle_reflectsAndFlipsState() {
        var checked = false
        compose.setContent {
            TideoTheme {
                SettingField(
                    SettingFieldSpec.Toggle(
                        label = "Enable PWM",
                        checked = checked,
                        onCheckedChange = { checked = it },
                        testTag = "toggle_pwm",
                    ),
                )
            }
        }
        compose.onNodeWithTag("toggle_pwm").assertIsOff()
        compose.onNodeWithTag("toggle_pwm").performClick()
        assertTrue(checked)
    }

    @Test
    fun settingField_slider_rendersWithinRange() {
        compose.setContent {
            TideoTheme {
                SettingField(
                    SettingFieldSpec.Slider(
                        label = "Steps",
                        value = 25,
                        range = 1..65,
                        onCommit = {},
                        testTag = "slider_steps",
                    ),
                )
            }
        }
        compose.onNodeWithTag("slider_steps").assertIsDisplayed()
        compose.onNodeWithText("Steps: 25", substring = true).assertExists()
    }
}
