package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.DraftSettingsViewModel
import com.tideo.autobrightness.app.ui.components.DraftSettingsScaffold
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.app.ui.components.rememberToaster
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * Super Dimming (Tasker AAB Superdimming Settings + Color Filter). Renamed from "Animation & Dimming"
 * in S12.6a (G2R-F3) — its content is super dimming (ELEVATED) + PWM/software dimming after the
 * animation fields moved to Misc (G2-F2). Draft → Apply (S12.5b). Super dimming and PWM are
 * **mutually exclusive** (G2-F10); the circadian dim-spread field is gated on circadian scaling (G2-F11).
 */
@Composable
fun SuperDimmingScreen(navController: NavHostController, vm: DraftSettingsViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val committed by vm.committed.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val epoch by vm.epoch.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val criticalError by vm.hasCriticalError.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    SuperDimmingContent(
        draft, committed, epoch, dirty, tier,
        onEdit = vm::edit, onApply = vm::apply, onDiscard = vm::discard,
        onBack = { navController.popBackStack() },
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
        criticalError = criticalError,
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
    onEdit: ((AabSettings) -> AabSettings) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    criticalError: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    DraftSettingsScaffold("Super Dimming", dirty, onApply, onDiscard, onBack, criticalError, onReset) { padding ->
        SettingsColumn(padding) {
            SectionHeader("Super dimming")
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
            val dimEnabled = tier == Tier.ELEVATED
            // task509/511 _DimmingUIToggle — ELEVATED-gated (secure reduce_bright_colors path, D-040a).
            // Mutually exclusive with PWM/software dimming (G2-F10): enabling super dimming disables PWM.
            SwitchSettingRow(
                "Enable super dimming", draft.dimmingEnabled,
                { on -> onEdit { s -> s.copy(dimmingEnabled = on, pwmSensitive = if (on) false else s.pwmSensitive) } },
                enabled = dimEnabled,
                helper = "Extra dimming below the threshold (Android Extra Dim).",
                testTag = "switch_dimmingEnabled",
            )
            NumberSettingField(
                "Dimming strength", draft.dimmingStrength, { onEdit { s -> s.copy(dimmingStrength = it.toInt()) } },
                epoch = epoch, committed = committed.dimmingStrength, enabled = dimEnabled,
                helper = "Maximum super-dimming strength (0–100).", testTag = "field_dimmingStrength",
            )
            NumberSettingField(
                "Dimming exponent", draft.dimmingExponent, { onEdit { s -> s.copy(dimmingExponent = it.toFloat()) } },
                epoch = epoch, committed = committed.dimmingExponent, isInt = false, enabled = dimEnabled,
                helper = "How gradually dimming kicks in.", testTag = "field_dimmingExponent",
            )
            NumberSettingField(
                "Dimming threshold", draft.dimmingThreshold, { onEdit { s -> s.copy(dimmingThreshold = it.toInt()) } },
                epoch = epoch, committed = committed.dimmingThreshold, enabled = dimEnabled,
                helper = "Screen brightness below which dimming engages.", testTag = "field_dimmingThreshold",
            )
            // task513/610: threshold must not sit below minimum brightness.
            if (draft.dimmingThreshold < draft.minBrightness) {
                ErrorBanner("Dimming threshold is below minimum brightness.", "error_dimmingThreshold")
            }
            // G2-F11: dim spread is the CIRCADIAN dim-strength spread (task646 DimDynamic) — it only
            // does anything when circadian scaling is on, so gate the field on it + correct the label.
            NumberSettingField(
                "Circadian dim spread", draft.dimSpread, { onEdit { s -> s.copy(dimSpread = it.toInt()) } },
                epoch = epoch, committed = committed.dimSpread,
                enabled = dimEnabled && draft.scalingEnabled,
                helper = "How much the dimming strength shifts across the day (needs circadian scaling).",
                testTag = "field_dimSpread",
            )

            SectionHeader("PWM (flicker) handling")
            // Software dimming / PWM-sensitive — no ELEVATED needed (superdimming_settings.md note);
            // mutually exclusive with super dimming (G2-F10).
            SwitchSettingRow(
                "Use software dimming (PWM-sensitive)", draft.pwmSensitive,
                { on -> onEdit { s -> s.copy(pwmSensitive = on, dimmingEnabled = if (on) false else s.dimmingEnabled) } },
                helper = "Gamma-like compression for flicker-sensitive eyes (disables super dimming).",
                testTag = "switch_pwmSensitive",
            )
            NumberSettingField(
                "PWM exponent", draft.pwmExponent, { onEdit { s -> s.copy(pwmExponent = it.toFloat()) } },
                epoch = epoch, committed = committed.pwmExponent, isInt = false,
                helper = "Shape of the PWM compression curve.", testTag = "field_pwmExponent",
            )

            // Circadian dimming chart (re-homed here per D-026) is render-deferred to S13.
            ChartPlaceholder("DimmingChart / CircadianChart", "dimming_chart")
        }
    }
}
