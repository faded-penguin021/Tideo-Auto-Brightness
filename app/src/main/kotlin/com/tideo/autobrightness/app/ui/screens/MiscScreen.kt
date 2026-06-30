package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.FieldError
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.KeyValueRow
import com.tideo.autobrightness.app.ui.components.MiscDiagnosticCardContent
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
    val live by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    MiscContent(
        draft, committed, errors, epoch, dirty,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        criticalError = criticalError,
        live = live,
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
            toast(R.string.toast_reset_defaults)
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
    live: PipelineState = PipelineState(),
) {
    DraftSettingsScaffold(stringResource(R.string.title_misc), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            // Tasker live readout (current_throttle_and_alpha, misc_settings.md elements31, G2R-F58):
            // current throttle (ms) + current smoothing α.
            MiscDiagnosticCardContent(live)

            // Labels + verbatim long-press help re-derived from extraction/scenes/misc_settings.md
            // (S12.6e, G2R-F19/F21). S13c (m3_audit §3 row 7): the bare slider stack is grouped into
            // labelled `AabCard` sections; derived readouts use the gold `KeyValueRow` data-pop (§4 B4).
            AabCard {
                SectionHeader(stringResource(R.string.misc_brightness_range), divider = true)
                // Tasker Misc sliders: Min 0–75, Max 150–255 (misc_settings.md elements4/6).
                IntSliderSettingField(
                    stringResource(R.string.misc_min_brightness), draft.minBrightness, 0..75,
                    { onEdit { s -> s.copy(minBrightness = it) } },
                    committed = committed.minBrightness,
                    help = TaskerHelp.MIN_BRIGHT, testTag = "slider_minBrightness",
                )
                IntSliderSettingField(
                    stringResource(R.string.misc_max_brightness), draft.maxBrightness, 150..255,
                    { onEdit { s -> s.copy(maxBrightness = it) } },
                    committed = committed.maxBrightness,
                    help = TaskerHelp.MAX_BRIGHT, testTag = "slider_maxBrightness",
                )
                NumberSettingField(
                    stringResource(R.string.misc_offset), draft.offset, { onEdit { s -> s.copy(offset = it.toInt()) } },
                    epoch = epoch, committed = committed.offset,
                    help = TaskerHelp.OFFSET, testTag = "field_offset",
                )
                // G2R-F60: when circadian (dynamic) scaling is on, the static Scale field is overridden by
                // the runtime dynamic scale — Tasker shows it as a read-only "(auto)" derived value
                // (%AAB_ScaleDynamicCompress, misc_settings.md scale_dynamic). Otherwise it stays editable.
                if (draft.scalingEnabled) {
                    KeyValueRow(
                        stringResource(R.string.misc_scale_auto),
                        String.format("%.3f", live.scaleDynamicCompress),
                        testTag = "derived_scaleDynamic",
                    )
                } else {
                    NumberSettingField(
                        stringResource(R.string.misc_scale), draft.scale, { onEdit { s -> s.copy(scale = it.toFloat()) } },
                        epoch = epoch, committed = committed.scale, isInt = false,
                        error = errors.forField("scale"),
                        help = TaskerHelp.SCALE, testTag = "field_scale",
                    )
                }
            }

            AabCard {
                SectionHeader(stringResource(R.string.misc_animation), divider = true)
                // Tasker Misc sliders: AnimSteps 0–100, MinWait 1–99, MaxWait 2–100 (elements20/22/23).
                IntSliderSettingField(
                    stringResource(R.string.misc_anim_steps), draft.animSteps, 0..100,
                    { onEdit { s -> s.copy(animSteps = it) } },
                    committed = committed.animSteps,
                    help = TaskerHelp.ANIM_STEPS, testTag = "slider_animSteps",
                )
                IntSliderSettingField(
                    stringResource(R.string.misc_min_wait), draft.minWaitMs, 1..99,
                    { onEdit { s -> s.copy(minWaitMs = it) } },
                    committed = committed.minWaitMs,
                    help = TaskerHelp.MIN_WAIT, testTag = "slider_minWaitMs",
                )
                IntSliderSettingField(
                    stringResource(R.string.misc_max_wait), draft.maxWaitMs, 2..100,
                    { onEdit { s -> s.copy(maxWaitMs = it) } },
                    committed = committed.maxWaitMs,
                    help = TaskerHelp.MAX_WAIT, testTag = "slider_maxWaitMs",
                )
                // task714 throttle derivation: AnimSteps*MaxWait+10 (read-only live readout, elements31).
                val derivedThrottle = draft.animSteps * draft.maxWaitMs + 10
                // S13c' §05: the unit is split out so it stays muted (never inherits the gold value).
                KeyValueRow(stringResource(R.string.misc_throttle_derived), "$derivedThrottle", unit = stringResource(R.string.unit_ms), testTag = "derived_throttle")
                if (draft.minWaitMs > draft.maxWaitMs) {
                    ErrorBanner(stringResource(R.string.misc_err_waits), "error_waits")
                }
            }

            AabCard {
                SectionHeader(stringResource(R.string.misc_notifications), divider = true)
                SwitchSettingRow(
                    stringResource(R.string.misc_use_notifications), draft.notificationsEnabled,
                    { onEdit { s -> s.copy(notificationsEnabled = it) } },
                    help = TaskerHelp.NOTIFY_USE,
                    testTag = "switch_notifications",
                )
            }

            AabCard {
                SectionHeader(stringResource(R.string.misc_language_header), divider = true)
                LanguageSelector()
                Text(
                    stringResource(R.string.misc_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * App-language selector (D-131). Scaffolded but **not yet functional** — English is the only available
 * language, so the menu lists it alone and selecting it is a no-op. Once translated `values-<lang>/`
 * resources land (see CONTRIBUTING.md), add their display names here and wire the choice to an
 * `AppCompatDelegate.setApplicationLocales(...)` / per-app language preference. The control exists now so
 * the surface is discoverable and translators can see where their work will appear.
 */
@Composable
private fun LanguageSelector() {
    var expanded by remember { mutableStateOf(false) }
    val english = stringResource(R.string.language_english)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("language_selector"),
        ) {
            Text(stringResource(R.string.misc_language_label) + ": " + english)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Only English for now; future translated locales are added here.
            DropdownMenuItem(text = { Text(english) }, onClick = { expanded = false })
        }
    }
}
