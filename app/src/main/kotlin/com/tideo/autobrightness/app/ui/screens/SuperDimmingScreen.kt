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

/** Super Dimming UI (super dimming + PWM mutually exclusive G2-F10; circadian spread gated on scaling G2-F11). */
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
    // Circadian chart shares F39 date/location override; read-only here.
    val dateLocation by extras.dateLocation.collectAsStateWithLifecycle()
    var defaultLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) { defaultLatLon = runCatching { extras.defaultLatLon() }.getOrNull() }
    // DB-008: Apply clamps strength > 65 down; user learns why (not a typo).
    LaunchedEffect(vm) {
        vm.dimmingStrengthClamped.collect { toast(R.string.toast_dimming_strength_clamped, it) }
    }
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
            toast(R.string.toast_reset_defaults)
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
            // G2R-F81: dimming curve + circadian dimming graphs (D-026).
            ChartPager(
                listOf(
                    ChartSlot(stringResource(R.string.sd_graph_dimming), "dimming_chart") {
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
                    ChartSlot(stringResource(R.string.sd_graph_circadian), "circadian_dimming_chart") {
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

            // G2R-F58: live readout.
            SuperDimmingDiagnosticCardContent(live)

            // G2R-F82: super-dimming + PWM controls.
            GraphSettingsGroup(stringResource(R.string.sd_graph_dimming)) {
                SectionHeader(stringResource(R.string.sd_header_super), divider = true)
                if (tier != Tier.ELEVATED) {
                    Text(
                        stringResource(R.string.sd_needs_elevated),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    TextButton(onClick = onOpenOnboarding, modifier = Modifier.testTag("dimming_grant_link")) {
                        Text(stringResource(R.string.superdimming_setup_elevated))
                    }
                }
                // task509/511 _DimmingUIToggle — ELEVATED-gated (D-040(a); G2-F10: exclusive with PWM).
                SwitchSettingRow(
                    stringResource(R.string.sd_use_super), draft.dimmingEnabled,
                    { on -> onEdit { s -> s.copy(dimmingEnabled = on, pwmSensitive = if (on) false else s.pwmSensitive) } },
                    enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_ENABLED,
                    testTag = "switch_dimmingEnabled",
                )
                NumberSettingField(
                    stringResource(R.string.sd_strength), draft.dimmingStrength, { onEdit { s -> s.copy(dimmingStrength = it.toInt()) } },
                    epoch = epoch, committed = committed.dimmingStrength, enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_STRENGTH, testTag = "field_dimmingStrength",
                )
                NumberSettingField(
                    stringResource(R.string.sd_exponent), draft.dimmingExponent, { onEdit { s -> s.copy(dimmingExponent = it.toFloat()) } },
                    epoch = epoch, committed = committed.dimmingExponent, isInt = false, enabled = dimEnabled,
                    help = TaskerHelp.DIMMING_EXPONENT, testTag = "field_dimmingExponent",
                )
                // The threshold field is SHARED between super dimming and software dimming (D-168):
                // Tasker relabels it "PWM Thresh" and flashes a different help when software dimming is
                // on (there it is the hardware floor, not the super-dimming activation point).
                NumberSettingField(
                    stringResource(if (draft.pwmSensitive) R.string.sd_pwm_threshold else R.string.sd_threshold),
                    draft.dimmingThreshold, { onEdit { s -> s.copy(dimmingThreshold = it.toInt()) } },
                    epoch = epoch, committed = committed.dimmingThreshold, enabled = dimEnabled,
                    help = if (draft.pwmSensitive) TaskerHelp.PWM_THRESHOLD else TaskerHelp.DIMMING_THRESHOLD,
                    testTag = "field_dimmingThreshold",
                )
                // task513/610: threshold ≥ minBrightness.
                if (draft.dimmingThreshold < draft.minBrightness) {
                    ErrorBanner(stringResource(R.string.sd_err_threshold), "error_dimmingThreshold")
                }

                SectionHeader(stringResource(R.string.sd_header_pwm), divider = true)
                // PWM-sensitive; exclusive with super dimming (G2-F10).
                SwitchSettingRow(
                    stringResource(R.string.sd_use_pwm), draft.pwmSensitive,
                    { on -> onEdit { s -> s.copy(pwmSensitive = on, dimmingEnabled = if (on) false else s.dimmingEnabled) } },
                    help = TaskerHelp.PWM_SENSITIVE,
                    testTag = "switch_pwmSensitive",
                )
                NumberSettingField(
                    stringResource(R.string.sd_pwm_exponent), draft.pwmExponent, { onEdit { s -> s.copy(pwmExponent = it.toFloat()) } },
                    epoch = epoch, committed = committed.pwmExponent, isInt = false,
                    help = TaskerHelp.PWM_EXPONENT, testTag = "field_pwmExponent",
                )
            }

            // task646 DimDynamic (circadian dimming spread); gated on scaling (G2-F11).
            GraphSettingsGroup(stringResource(R.string.sd_graph_circadian)) {
                SectionHeader(stringResource(R.string.sd_header_circadian_spread), divider = true)
                NumberSettingField(
                    stringResource(R.string.sd_spread), draft.dimSpread, { onEdit { s -> s.copy(dimSpread = it.toInt()) } },
                    epoch = epoch, committed = committed.dimSpread,
                    enabled = dimEnabled && draft.scalingEnabled,
                    help = TaskerHelp.DIM_SPREAD,
                    testTag = "field_dimSpread",
                )
            }
        }
    }
}
