package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.IntSliderSettingField
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow

/** %AAB_Debug 10 named categories, verbatim (D-023). Index == debugLevel. */
val DEBUG_LABELS = listOf(
    "Off", "Skip Animations", "Animation Details", "Light Eval Thresholds", "Dynamic Scale Calcs",
    "Super Dimming Info", "Overlay Preview", "Graph Metrics", "Context Automation", "Context Location",
)

/**
 * Misc / General (Tasker "AAB Misc Settings" scene). Re-adds the field grouping Tasker actually uses
 * (G2-F2): the brightness range (min/max as **sliders**, offset/scale as text), animation (steps +
 * min/max wait as **sliders**, derived throttle), notifications and the debug-category selector.
 * Slider ranges are the exact Tasker SliderElement bounds (extraction/scenes/misc_settings.md).
 */
@Composable
fun MiscScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    MiscContent(
        draft, committed, errors, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
    )
}

@Composable
fun MiscContent(
    draft: AabSettings,
    committed: AabSettings,
    errors: List<FieldError>,
    epoch: Int,
    dirty: Boolean,
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
) {
    DraftSettingsScaffold("Misc", dirty, onApply, onDiscard, onBack) { padding ->
        SettingsColumn(padding) {
            SectionHeader("Brightness range")
            // Tasker Misc sliders: Min 0–75, Max 150–255 (misc_settings.md elements4/6).
            IntSliderSettingField(
                "Min brightness", draft.minBrightness, 0..75,
                { onEdit { s -> s.copy(minBrightness = it) } },
                committed = committed.minBrightness,
                helper = "Lowest level the screen will use.", testTag = "slider_minBrightness",
            )
            IntSliderSettingField(
                "Max brightness", draft.maxBrightness, 150..255,
                { onEdit { s -> s.copy(maxBrightness = it) } },
                committed = committed.maxBrightness,
                helper = "Highest level the screen will use.", testTag = "slider_maxBrightness",
            )
            NumberSettingField(
                "Offset", draft.offset, { onEdit { s -> s.copy(offset = it.toInt()) } },
                epoch = epoch, committed = committed.offset,
                helper = "A flat boost or cut applied to the whole curve.", testTag = "field_offset",
            )
            NumberSettingField(
                "Scale", draft.scale, { onEdit { s -> s.copy(scale = it.toFloat()) } },
                epoch = epoch, committed = committed.scale, isInt = false,
                error = errors.forField("scale"),
                helper = "Global multiplier for the entire curve.", testTag = "field_scale",
            )

            SectionHeader("Animation")
            // Tasker Misc sliders: AnimSteps 0–100, MinWait 1–99, MaxWait 2–100 (elements20/22/23).
            IntSliderSettingField(
                "Animation steps", draft.animSteps, 0..100,
                { onEdit { s -> s.copy(animSteps = it) } },
                committed = committed.animSteps,
                helper = "Steps in a brightness-change animation.", testTag = "slider_animSteps",
            )
            IntSliderSettingField(
                "Min wait (ms)", draft.minWaitMs, 1..99,
                { onEdit { s -> s.copy(minWaitMs = it) } },
                committed = committed.minWaitMs,
                helper = "Shortest delay between animation frames.", testTag = "slider_minWaitMs",
            )
            IntSliderSettingField(
                "Max wait (ms)", draft.maxWaitMs, 2..100,
                { onEdit { s -> s.copy(maxWaitMs = it) } },
                committed = committed.maxWaitMs,
                helper = "Longest delay between animation frames.", testTag = "slider_maxWaitMs",
            )
            // task714 throttle derivation: AnimSteps*MaxWait+10 (read-only live readout, elements31).
            val derivedThrottle = draft.animSteps * draft.maxWaitMs + 10
            DerivedReadout("Throttle (derived)", "$derivedThrottle ms", testTag = "derived_throttle")
            if (draft.minWaitMs > draft.maxWaitMs) {
                ErrorBanner("Minimum wait cannot exceed maximum wait.", "error_waits")
            }

            SectionHeader("Notifications & debug")
            SwitchSettingRow(
                "Show notifications", draft.notificationsEnabled,
                { onEdit { s -> s.copy(notificationsEnabled = it) } },
                helper = "Show the ongoing auto-brightness notification.",
                testTag = "switch_notifications",
            )
            DebugLevelSelector(draft.debugLevel) { level -> onEdit { s -> s.copy(debugLevel = level) } }
        }
    }
}

/** The %AAB_Debug 10-category selector (D-023). Persisted now; runtime toasts wired in S12.5c. */
@Composable
fun DebugLevelSelector(current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("debug_selector")) {
        Text("Debug: ${DEBUG_LABELS.getOrElse(current) { "Off" }}")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DEBUG_LABELS.forEachIndexed { level, label ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSelect(level); expanded = false },
            )
        }
    }
}
