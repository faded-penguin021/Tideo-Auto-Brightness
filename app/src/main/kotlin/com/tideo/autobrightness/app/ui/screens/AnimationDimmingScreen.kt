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
import com.tideo.autobrightness.app.state.SettingsViewModel
import com.tideo.autobrightness.app.ui.components.DerivedReadout
import com.tideo.autobrightness.app.ui.components.NumberSettingField
import com.tideo.autobrightness.app.ui.components.SectionHeader
import com.tideo.autobrightness.app.ui.components.SettingsColumn
import com.tideo.autobrightness.app.ui.components.SettingsScaffold
import com.tideo.autobrightness.app.ui.components.SwitchSettingRow
import com.tideo.autobrightness.platform.privilege.Tier

/** Animation & Dimming (Tasker AAB Superdimming Settings + Misc anim fields + Color Filter). */
@Composable
fun AnimationDimmingScreen(navController: NavHostController, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    AnimationDimmingContent(
        settings, tier,
        onBack = { navController.popBackStack() },
        onUpdate = vm::update,
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
    )
}

@Composable
fun AnimationDimmingContent(
    settings: AabSettings,
    tier: Tier,
    onBack: () -> Unit,
    onUpdate: ((AabSettings) -> AabSettings) -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    SettingsScaffold("Animation & Dimming", onBack) { padding ->
        SettingsColumn(padding) {
            SectionHeader("Animation")
            NumberSettingField(
                "Animation steps", settings.animSteps, { onUpdate { s -> s.copy(animSteps = it.toInt()) } },
                helper = "Number of steps in a brightness change animation (0–100).", testTag = "field_animSteps",
            )
            NumberSettingField(
                "Min wait (ms)", settings.minWaitMs, { onUpdate { s -> s.copy(minWaitMs = it.toInt()) } },
                helper = "Shortest delay between animation frames.", testTag = "field_minWaitMs",
            )
            NumberSettingField(
                "Max wait (ms)", settings.maxWaitMs, { onUpdate { s -> s.copy(maxWaitMs = it.toInt()) } },
                helper = "Longest delay between animation frames.", testTag = "field_maxWaitMs",
            )
            // task714 throttle derivation: AnimSteps*MaxWait+10 (floored at 1001 in Tasker; we surface raw).
            val derivedThrottle = settings.animSteps * settings.maxWaitMs + 10
            DerivedReadout("Throttle (derived)", "$derivedThrottle ms", testTag = "derived_throttle")
            if (settings.minWaitMs > settings.maxWaitMs) {
                ErrorBanner("Minimum wait cannot exceed maximum wait.", "error_waits")
            }

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
            // task509/511 _DimmingUIToggle — surfaces the DimmingEnabled toggle deferred from Gate 1
            // (D-041 F5). Gated to ELEVATED (the secure reduce_bright_colors path, D-040a).
            SwitchSettingRow(
                "Enable super dimming", settings.dimmingEnabled,
                { onUpdate { s -> s.copy(dimmingEnabled = it) } },
                enabled = dimEnabled,
                helper = "Extra dimming below the threshold (Android Extra Dim).",
                testTag = "switch_dimmingEnabled",
            )
            NumberSettingField(
                "Dimming strength", settings.dimmingStrength, { onUpdate { s -> s.copy(dimmingStrength = it.toInt()) } },
                enabled = dimEnabled, helper = "Maximum super-dimming strength (0–100).", testTag = "field_dimmingStrength",
            )
            NumberSettingField(
                "Dimming exponent", settings.dimmingExponent, { onUpdate { s -> s.copy(dimmingExponent = it.toFloat()) } },
                isInt = false, enabled = dimEnabled, helper = "How gradually dimming kicks in.", testTag = "field_dimmingExponent",
            )
            NumberSettingField(
                "Dimming threshold", settings.dimmingThreshold, { onUpdate { s -> s.copy(dimmingThreshold = it.toInt()) } },
                enabled = dimEnabled, helper = "Screen brightness below which dimming engages.", testTag = "field_dimmingThreshold",
            )
            // task513/610: threshold must not sit below minimum brightness.
            if (settings.dimmingThreshold < settings.minBrightness) {
                ErrorBanner("Dimming threshold is below minimum brightness.", "error_dimmingThreshold")
            }
            NumberSettingField(
                "Dim spread", settings.dimSpread, { onUpdate { s -> s.copy(dimSpread = it.toInt()) } },
                enabled = dimEnabled, helper = "How wide the dim shifts over the brightness range.", testTag = "field_dimSpread",
            )

            SectionHeader("PWM (flicker) handling")
            SwitchSettingRow(
                "PWM-sensitive mode", settings.pwmSensitive,
                { onUpdate { s -> s.copy(pwmSensitive = it) } },
                helper = "Gamma-like compression for flicker-sensitive eyes.",
                testTag = "switch_pwmSensitive",
            )
            NumberSettingField(
                "PWM exponent", settings.pwmExponent, { onUpdate { s -> s.copy(pwmExponent = it.toFloat()) } },
                isInt = false, helper = "Shape of the PWM compression curve.", testTag = "field_pwmExponent",
            )

            // Circadian dimming chart (re-homed here per D-026) is render-deferred to S13.
            ChartPlaceholder("DimmingChart / CircadianChart", "dimming_chart")
        }
    }
}
