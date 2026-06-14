package com.tideo.autobrightness.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.tideo.autobrightness.app.ui.components.rememberToaster

/**
 * Misc / General (Tasker "AAB Misc Settings" scene). Re-adds the field grouping Tasker actually uses
 * (G2-F2): the brightness range (min/max as **sliders**, offset/scale as text), animation (steps +
 * min/max wait as **sliders**, derived throttle) and notifications. The debug-category selector moved
 * to the global Live Debug scene in S12.6b (G2R-F9). Slider ranges are the exact Tasker SliderElement
 * bounds (extraction/scenes/misc_settings.md).
 */
@Composable
fun MiscScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    MiscContent(
        draft, committed, errors, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        criticalError = criticalError,
        // G2R-F17: reset only the Misc brightness-range/animation/notification fields to defaults.
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    minBrightness = d.minBrightness, maxBrightness = d.maxBrightness, offset = d.offset,
                    scale = d.scale, animSteps = d.animSteps, minWaitMs = d.minWaitMs,
                    maxWaitMs = d.maxWaitMs, throttleDefaultMs = d.throttleDefaultMs,
                    notificationsEnabled = d.notificationsEnabled,
                )
            }
            toast("Reset to defaults")
        },
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
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    DraftSettingsScaffold("Misc", dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
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

            SectionHeader("Notifications")
            SwitchSettingRow(
                "Show notifications", draft.notificationsEnabled,
                { onEdit { s -> s.copy(notificationsEnabled = it) } },
                helper = "Show the ongoing auto-brightness notification.",
                testTag = "switch_notifications",
            )
        }
    }
}
