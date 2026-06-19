package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.state.DashboardViewModel
import com.tideo.autobrightness.app.state.ServiceHealthUiState
import com.tideo.autobrightness.app.ui.components.AabTopBar
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnGold
import com.tideo.autobrightness.platform.privilege.Tier

/** Stateful wrapper: wires the [DashboardViewModel] and navigation. */
@Composable
fun DashboardScreen(navController: NavHostController, viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onToggleService = viewModel::setEnabled,
        onResume = viewModel::resume,
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
        onBack = { navController.popBackStack() },
    )
}

/**
 * Stateless, fully driven by [state] + callbacks so it can render under a Robolectric compose test.
 *
 * S12.8b (G2R-F79): redesigned to be a focused, insightful live-status screen. The confusing **Pause**
 * control is gone (stopping = disable the service via the master switch); only **Resume** remains, and
 * only when a manual brightness override has paused auto-control (`pausedByOverride`). The live state
 * is broken into purposeful cards — status, light, brightness/circadian/dimming — so the screen
 * actually explains what the engine is doing right now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onToggleService: (Boolean) -> Unit,
    onResume: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { AabTopBar(title = stringResource(R.string.title_dashboard), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierBadge(tier = state.tier, onClick = onOpenOnboarding)
            // S12.9d: the published live snapshot has aged past STALE while the service still claims to
            // be running — the loop may be wedged. Warn rather than show a confidently-wrong readout.
            if (state.stale && state.serviceRunning) {
                StaleBanner()
            }
            StatusCard(state, onToggleService)
            // The Resume-after-override affordance only appears when the engine paused itself because
            // the user changed brightness manually (prof755/task567) — the one case Resume is for.
            if (state.pausedByOverride) {
                OverrideCard(state.serviceRunning, onResume)
            }
            LightCard(state)
            BrightnessCard(state)
            if (state.activeContext != null) {
                ContextCard(state.activeContext)
            }
            HealthCard(state.lastSampleMs, state.health)
        }
    }
}

/** Amber "live data may be stale" banner (S12.9d). */
@Composable
private fun StaleBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("stale_banner"),
        colors = CardDefaults.cardColors(
            containerColor = AabGold,
            contentColor = AabOnGold,
        ),
    ) {
        Text(
            stringResource(R.string.dashboard_stale_banner),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TierBadge(tier: Tier, onClick: () -> Unit) {
    val (label, color) = when (tier) {
        Tier.NONE -> stringResource(R.string.dashboard_tier_none) to MaterialTheme.colorScheme.error
        Tier.BASIC -> stringResource(R.string.dashboard_tier_basic) to MaterialTheme.colorScheme.primary
        Tier.ELEVATED -> stringResource(R.string.dashboard_tier_elevated) to MaterialTheme.colorScheme.tertiary
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag("tier_badge"),
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

/** Status headline + the master switch (the only on/off control — there is no Pause, G2R-F79). */
@Composable
private fun StatusCard(state: DashboardUiState, onToggle: (Boolean) -> Unit) {
    val status = when {
        !state.serviceEnabled -> stringResource(R.string.dashboard_status_off)
        !state.serviceRunning -> stringResource(R.string.dashboard_status_starting)
        state.pausedByOverride -> stringResource(R.string.dashboard_status_paused_override)
        state.paused -> stringResource(R.string.dashboard_status_paused)
        else -> stringResource(R.string.dashboard_status_active)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_auto_brightness), style = MaterialTheme.typography.titleMedium)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("dashboard_status"),
                    )
                }
                Switch(
                    checked = state.serviceEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("service_switch"),
                )
            }
        }
    }
}

/** Shown only while [pausedByOverride]: explains the pause and offers the one Resume control. */
@Composable
private fun OverrideCard(serviceRunning: Boolean, onResume: () -> Unit) {
    Card(
        modifier = Modifier.testTag("override_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dashboard_manual_override), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.dashboard_override_explain),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = onResume,
                enabled = serviceRunning,
                modifier = Modifier.testTag("resume_button"),
            ) { Text(stringResource(R.string.dashboard_resume_button)) }
        }
    }
}

@Composable
private fun LightCard(state: DashboardUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.dashboard_ambient_light), style = MaterialTheme.typography.labelMedium)
            Text(
                "${state.rawLux.fmt()} lux raw · ${state.smoothedLux.fmt()} smoothed",
                modifier = Modifier.testTag("dashboard_lux"),
            )
            Text(
                stringResource(R.string.dashboard_last_sample, state.lastSampleMs.toRelativeAge()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("last_sample_age"),
            )
        }
    }
}

@Composable
private fun BrightnessCard(state: DashboardUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.dashboard_brightness), style = MaterialTheme.typography.labelMedium)
            // Gate-2(5th) obs: the pipeline only publishes its state AFTER the animation settles, so the
            // applied and target values are always equal here (mid-animation frames are never surfaced) —
            // showing "X → Y" was confusing because X==Y. Render the single applied level, and only fall
            // back to the "→ target" form on the rare snapshot where they genuinely differ.
            val cur = state.currentBrightness
            val tgt = state.targetBrightness
            Text(
                when {
                    cur != null && tgt != null && cur != tgt -> "$cur → $tgt (target)"
                    else -> "${(cur ?: tgt).fmtInt()} / 255"
                },
                modifier = Modifier.testTag("dashboard_brightness"),
            )
            // Only surface circadian scale when it is actually shifting the curve.
            state.circadianScale?.takeIf { kotlin.math.abs(it - 1.0) > 0.001 }?.let {
                Text(
                    "Circadian scale: ${"%.2f".format(it)}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dashboard_scale"),
                )
            }
            // Only surface dimming when engaged.
            state.dimmingStrength.takeIf { it > 0.0 }?.let {
                Text(
                    "Super dimming: ${"%.0f".format(it)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("dashboard_dimming"),
                )
            }
        }
    }
}

@Composable
private fun ContextCard(activeContext: String) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.dashboard_active_context), style = MaterialTheme.typography.labelMedium)
            Text(activeContext, modifier = Modifier.testTag("dashboard_context"))
        }
    }
}

@Composable
private fun HealthCard(lastSampleMs: Long?, health: ServiceHealthUiState) {
    if (!health.degradedMode) return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.dashboard_service_health), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.dashboard_degraded, health.degradedReason ?: "unknown"),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("dashboard_degraded"),
            )
        }
    }
}

private fun Double?.fmt(): String = this?.let { "%.0f".format(it) } ?: "—"
private fun Int?.fmtInt(): String = this?.toString() ?: "—"

/** Relative "Xs ago" age for a millis timestamp; "never" when the sensor has not fired yet. */
private fun Long?.toRelativeAge(now: Long = System.currentTimeMillis()): String {
    if (this == null) return "never"
    val secs = ((now - this) / 1000L).coerceAtLeast(0L)
    return when {
        secs < 1L -> "just now"
        secs < 60L -> "${secs}s ago"
        secs < 3600L -> "${secs / 60L}m ago"
        else -> "${secs / 3600L}h ago"
    }
}
