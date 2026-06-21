package com.tideo.autobrightness.app.ui.screens

import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.R
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.CircadianExtrasViewModel
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.GraphSettingsGroup
import com.tideo.autobrightness.app.ui.components.SuperDimmingDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.app.ui.graph.CircadianDimmingChart
import com.tideo.autobrightness.app.ui.graph.DimmingChart
import com.tideo.autobrightness.app.settings.toDynamicScalingConfig
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * Super Dimming (Tasker AAB Superdimming Settings + Color Filter). Renamed from "Animation & Dimming"
 * in S12.6a (G2R-F3) — its content is super dimming (ELEVATED) + PWM/software dimming after the
 * animation fields moved to Misc (G2-F2). Draft → Apply (S12.5b). Super dimming and PWM are
 * **mutually exclusive** (G2-F10); the circadian dim-spread field is gated on circadian scaling (G2-F11).
 */
@Composable
fun SuperDimmingScreen(
    navController: NavHostController,
    vm: DraftSettingsViewModel = viewModel(),
    extras: CircadianExtrasViewModel = viewModel(),
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val live by LiveRuntimeState.pipeline.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    // The Circadian Dimming chart shares the F39 fixed date/location override (Circadian screen) so it
    // tracks the date too. Read-only here (no editor on this screen).
    val dateLocation by extras.dateLocation.collectAsStateWithLifecycle()
    var defaultLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) { defaultLatLon = runCatching { extras.defaultLatLon() }.getOrNull() }
    SuperDimmingContent(
        draft, committed, epoch, dirty, tier, live,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
        criticalError = criticalError,
        circadianLat = dateLocation.latitude ?: defaultLatLon?.first,
        circadianLon = dateLocation.longitude ?: defaultLatLon?.second,
        circadianDateSec = chartDateEpochSec(dateLocation.date),
        // G2R-F17: reset only the super-dimming + PWM fields to the task570 baseline.
        onReset = {
            vm.edit { s ->
                val d = AabSettings()
                s.copy(
                    dimmingEnabled = d.dimmingEnabled, dimmingStrength = d.dimmingStrength,
                    dimmingExponent = d.dimmingExponent, dimmingThreshold = d.dimmingThreshold,
                    dimSpread = d.dimSpread, pwmSensitive = d.pwmSensitive, pwmExponent = d.pwmExponent,
                )
            }
            toast("Reset to defaults")
        },
    )
}

@Composable
fun SuperDimmingContent(
    draft: AabSettings,
    committed: AabSettings,
    epoch: Int,
    dirty: Boolean,
    tier: Tier,
    live: PipelineState = PipelineState(),
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
    circadianLat: Double? = null,
    circadianLon: Double? = null,
    circadianDateSec: Long = System.currentTimeMillis() / 1000L,
) {
    DraftSettingsScaffold(stringResource(R.string.title_super_dimming), dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            val dimEnabled = tier == Tier.ELEVATED
            // G2R-F81 + Gate-2(5th) obs: two relevant graphs sit ABOVE the settings and are swiped
            // between — the lux→dim "Dimming curve" (AAB Dimming Graph) and the day/night "Circadian
            // Dimming" spread graph (AAB Circadian Dimming Graph, re-homed here per D-026). S13 fills both.
            ChartPager(
                listOf(
                    ChartSlot("Dimming curve", "dimming_chart") {
                        DimmingChart(
                            minBrightness = draft.minBrightness,
                            dimmingThreshold = draft.dimmingThreshold,
                            dimmingExponent = draft.dimmingExponent.toDouble(),
                            dimmingStrength = draft.dimmingStrength,
                            modifier = Modifier.testTag("dimming_chart"),
                            // Live "Now" line at the current brightness, only while Extra Dim is engaged.
                            currentBrightness = live.targetBrightness
                                ?.takeIf { live.serviceOn && (live.dimmingDS > 0.0 || live.dimmingCurrent > 0.0) },
                        )
                    },
                    ChartSlot("Circadian Dimming", "circadian_dimming_chart") {
                        CircadianDimmingChart(
                            draft.toDynamicScalingConfig(),
                            Modifier.testTag("circadian_dimming_chart"),
                            latitude = circadianLat, longitude = circadianLon,
                            dateEpochSec = circadianDateSec,
                            transitionFactor = draft.scaleTransitionFactor.toDouble(),
                        )
                    },
                ),
            )

            // G2R-F58 live readout: %AAB_DimmingCurrent (rel) / %AAB_DimmingDS (abs) at %AAB_CurrentBright.
            SuperDimmingDiagnosticCardContent(live)

            // G2R-F82: the super-dimming + PWM controls shape the lux→dim "Dimming curve" graph above.
            GraphSettingsGroup("Dimming curve") {
                SectionHeader("Super dimming", divider = true)
                if (tier != Tier.ELEVATED) {
                    Text(
                        "Super dimming needs elevated access (WRITE_SECURE_SETTINGS).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    TextButton(onClick = onOpenOnboarding, modifier = Modifier.testTag("dimming_grant_link")) {
                        Text("Set up elevated access")
                    }
                }
                // Labels + verbatim long-press help from extraction/scenes/superdimming_settings.md (S12.6e).
                // task509/511 _DimmingUIToggle — ELEVATED-gated (secure reduce_bright_colors path, D-040a).
                // Mutually exclusive with PWM/software dimming (G2-F10): enabling super dimming disables PWM.
                SwitchSettingRow(
                    "Use super dimming", draft.dimmingEnabled,
                    { on -> onEdit { s -> s.copy(dimmingEnabled = on, pwmSensitive = if (on) false else s.pwmSensitive) } },
                    enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_ENABLED,
                    testTag = "switch_dimmingEnabled",
                )
                NumberSettingField(
                    "Strength setpoint", draft.dimmingStrength, { onEdit { s -> s.copy(dimmingStrength = it.toInt()) } },
                    epoch = epoch, committed = committed.dimmingStrength, enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_STRENGTH, testTag = "field_dimmingStrength",
                )
                NumberSettingField(
                    "SD exponent", draft.dimmingExponent, { onEdit { s -> s.copy(dimmingExponent = it.toFloat()) } },
                    epoch = epoch, committed = committed.dimmingExponent, isInt = false, enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_EXPONENT, testTag = "field_dimmingExponent",
                )
                NumberSettingField(
                    "Threshold", draft.dimmingThreshold, { onEdit { s -> s.copy(dimmingThreshold = it.toInt()) } },
                    epoch = epoch, committed = committed.dimmingThreshold, enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_THRESHOLD, testTag = "field_dimmingThreshold",
                )
                // task513/610: threshold must not sit below minimum brightness.
                if (draft.dimmingThreshold < draft.minBrightness) {
                    ErrorBanner("Dimming threshold is below minimum brightness.", "error_dimmingThreshold")
                }

                SectionHeader("PWM (flicker) handling", divider = true)
                // Software dimming / PWM-sensitive — no ELEVATED needed (superdimming_settings.md note);
                // mutually exclusive with super dimming (G2-F10).
                SwitchSettingRow(
                    "Use software dimming (PWM-sensitive)", draft.pwmSensitive,
                    { on -> onEdit { s -> s.copy(pwmSensitive = on, dimmingEnabled = if (on) false else s.dimmingEnabled) } },
                    help = TaskerHelp.PWM_SENSITIVE,
                    testTag = "switch_pwmSensitive",
                )
                NumberSettingField(
                    "Software exponent (PWM)", draft.pwmExponent, { onEdit { s -> s.copy(pwmExponent = it.toFloat()) } },
                    epoch = epoch, committed = committed.pwmExponent, isInt = false,
                    help = TaskerHelp.PWM_EXPONENT, testTag = "field_pwmExponent",
                )
            }

            // Gate-2(5th) obs: "Spread" is the CIRCADIAN dim-strength spread (task646 DimDynamic) — it
            // drives the *Circadian Dimming* graph (how dim strength varies across the day), NOT the
            // lux→dim curve, so it is grouped under that graph (matching Tasker). G2-F11: only effective
            // when circadian scaling is on, so the field stays gated on it.
            GraphSettingsGroup("Circadian Dimming") {
                SectionHeader("Circadian dim spread", divider = true)
                NumberSettingField(
                    "Spread (circadian)", draft.dimSpread, { onEdit { s -> s.copy(dimSpread = it.toInt()) } },
                    epoch = epoch, committed = committed.dimSpread,
                    enabled = dimEnabled && draft.scalingEnabled,
                    help = TaskerHelp.DIM_SPREAD,
                    testTag = "field_dimSpread",
                )
            }
        }
    }
}
