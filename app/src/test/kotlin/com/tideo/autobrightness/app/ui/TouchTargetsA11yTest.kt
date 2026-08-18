package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.ui.components.AppPickerList
import com.tideo.autobrightness.app.ui.components.BrightnessInstrument
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.components.DayPicker
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.DraftApplyBar
import com.tideo.autobrightness.app.ui.components.HeroNavCard
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.KeyValueRow
import com.tideo.autobrightness.app.ui.components.NavRow
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.TimeField
import com.tideo.autobrightness.app.ui.components.TimeTokenRow
import com.tideo.autobrightness.app.ui.components.TriggerSection
import com.tideo.autobrightness.app.ui.components.UsageAccessPromptCard
import com.tideo.autobrightness.app.ui.theme.Dimens
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** D-156 (A7 a11y): motor-accessibility floor (48 dp touch area). Two carve-outs: M3 form primitives + chart pager dots. */
@RunWith(RobolectricTestRunner::class)
class TouchTargetsA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderSettingsControls() {
        compose.setContent {
            TideoTheme {
                Column {
                    SectionHeader("Curve maths", divider = true)
                    NumberSettingField(label = "Zone 1 end", value = 40, onCommit = {}, help = R.string.a11y_back)
                    IntSliderSettingField(label = "Samples", value = 5, range = 1..10, onCommit = {}, help = R.string.a11y_back)
                    SwitchSettingRow(label = "Super dimming", checked = true, onCheckedChange = {}, help = R.string.a11y_back)
                    DerivedReadout(label = "Derived form2A", value = "0.42", testTag = "derived")
                    DraftApplyBar(dirty = true, onApply = {}, onDiscard = {})
                }
            }
        }
    }

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
                    AppPickerList(apps = listOf(AppEntry("com.x", "Example App")), selected = emptySet(), onToggle = { _, _ -> })
                    BrightnessInstrument(state = DashboardUiState(serviceEnabled = true), onToggleService = {})
                    KeyValueRow(key = "Current lux", value = "1234", testTag = "kv_lux")
                }
            }
        }
    }

    private fun renderPager() {
        compose.setContent {
            TideoTheme {
                ChartPager(
                    slots = listOf(
                        ChartSlot("First", "slot_a") { Text("A") },
                        ChartSlot("Second", "slot_b") { Text("B") },
                    ),
                )
            }
        }
    }

    @Test
    fun settingsControlsMeetTheTouchTargetFloor() {
        renderSettingsControls()
        compose.assertInteractiveNodesMeetTouchFloor()
    }

    @Test
    fun sharedComponentsMeetTheTouchTargetFloor() {
        renderComponents()
        compose.assertInteractiveNodesMeetTouchFloor()
    }

    @Test
    fun pagerArrowsMeetTheTouchTargetFloor() {
        renderPager()
        // Test primary paging affordances; dots are excluded carve-out.
        compose.assertTouchFloor(compose.onNodeWithContentDescription("Previous chart"), Dimens.touchTarget)
        compose.assertTouchFloor(compose.onNodeWithContentDescription("Next chart"), Dimens.touchTarget)
    }

    /** Carve-out 1: verify form controls are stock M3 (have Role/SetProgress). */
    @Test
    fun m3FormPrimitivesAreStandardMaterialControls() {
        compose.setContent {
            TideoTheme {
                Column {
                    IntSliderSettingField(label = "Samples", value = 5, range = 1..10, onCommit = {}, help = R.string.a11y_back)
                    SwitchSettingRow(label = "Super dimming", checked = true, onCheckedChange = {}, help = R.string.a11y_back)
                    BrightnessInstrument(state = DashboardUiState(serviceEnabled = true), onToggleService = {})
                    AppPickerList(apps = listOf(AppEntry("com.x", "Example App")), selected = emptySet(), onToggle = { _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("Samples", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        compose.onNodeWithTag("Super dimming", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithTag("service_switch", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithTag("app_check_com.x", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }
}

/** Assert hand-authored nodes meet 48 dp touch floor (excludes carve-outs: pager dots, M3 primitives). */
private fun ComposeContentTestRule.assertInteractiveNodesMeetTouchFloor() {
    val interactive = SemanticsMatcher("hand-authored interactive node") { node ->
        val cfg = node.config
        val isInteractive = cfg.contains(SemanticsActions.OnClick) ||
            cfg.contains(SemanticsActions.SetProgress) ||
            cfg.contains(SemanticsProperties.ToggleableState)
        val isPagerDot = cfg.getOrNull(SemanticsProperties.TestTag)?.startsWith("chart_pager_dot_") == true
        val role = cfg.getOrNull(SemanticsProperties.Role)
        val isM3FormPrimitive = cfg.contains(SemanticsActions.SetProgress) ||
            role == Role.Switch || role == Role.Checkbox
        isInteractive && !isPagerDot && !isM3FormPrimitive
    }
    val nodes = onAllNodes(interactive, useUnmergedTree = false)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val minPx = with(density) { Dimens.touchTarget.toPx() } - 0.5f
    val violations = nodes.mapNotNull { node ->
        val touch = node.touchBoundsInRoot
        if (touch.width < minPx || touch.height < minPx) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "#${node.id}"
            val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            val layout = node.boundsInRoot
            "  [$tag${cd?.let { " \"$it\"" } ?: ""}] touch=${touch.width}×${touch.height} layout=${layout.width}×${layout.height}"
        } else {
            null
        }
    }
    if (violations.isNotEmpty()) {
        throw AssertionError(
            "Interactive nodes below the ${Dimens.touchTarget.value} dp motor-accessibility floor " +
                "(A7, plans/a11y-diagnostics.md):\n" + violations.joinToString("\n"),
        )
    }
}

/** Read tap area from touchBoundsInRoot (BOM lacks assertTouchWidthIsAtLeast). */
private fun ComposeContentTestRule.assertTouchFloor(interaction: SemanticsNodeInteraction, min: Dp) {
    val node: SemanticsNode = interaction.fetchSemanticsNode()
    val touch = node.touchBoundsInRoot
    val minPx = with(density) { min.toPx() } - 0.5f
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "#${node.id}"
    val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
    if (touch.width < minPx || touch.height < minPx) {
        val minDp = min.value
        val layout = node.boundsInRoot
        throw AssertionError(
            "Interactive node [$tag${cd?.let { " \"$it\"" } ?: ""}] has a ${touch.width}×${touch.height} px " +
                "touch target (layout ${layout.width}×${layout.height}), below the ${minDp} dp " +
                "(${with(density) { min.toPx() }} px) motor-accessibility floor. Wrap it so its clickable area " +
                "is at least $minDp dp (Dimens.touchTarget); see A7 in plans/a11y-diagnostics.md.",
        )
    }
}
