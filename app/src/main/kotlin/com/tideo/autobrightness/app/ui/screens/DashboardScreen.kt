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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.state.DashboardViewModel
import com.tideo.autobrightness.app.state.ServiceHealthUiState
import com.tideo.autobrightness.app.ui.components.AabTopBar
import com.tideo.autobrightness.platform.privilege.Tier

/** Stateful wrapper: wires the [DashboardViewModel] and navigation. */
@Composable
fun DashboardScreen(navController: NavHostController, viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onToggleService = viewModel::setEnabled,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
        onBack = { navController.popBackStack() },
    )
}

/**
 * Stateless, fully driven by [state] + callbacks so it can render under a Robolectric compose test.
 *
 * S12.6a (G2R-F1/F2): the AAB Menu is now the home hub — the Profiles/Contexts hero cards and the
 * nav drawer moved there ([MenuScreen]); the Dashboard is a focused live-status screen reached from
 * the Menu, with a back arrow that returns to it. The "Last sensor sample" age is now driven by the
 * live pipeline (G2R-F5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onToggleService: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { AabTopBar(title = "Dashboard", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierBadge(tier = state.tier, onClick = onOpenOnboarding)
            ServiceSwitchCard(state.serviceEnabled, onToggleService)
            LiveReadoutCard(state, onPause, onResume)
            HealthCard(state.lastSampleMs, state.health)
        }
    }
}

@Composable
private fun TierBadge(tier: Tier, onClick: () -> Unit) {
    val (label, color) = when (tier) {
        Tier.NONE -> "No write access — tap to set up" to MaterialTheme.colorScheme.error
        Tier.BASIC -> "Basic access (Modify system settings)" to MaterialTheme.colorScheme.primary
        Tier.ELEVATED -> "Elevated access (super dimming ready)" to MaterialTheme.colorScheme.tertiary
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag("tier_badge"),
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

@Composable
private fun ServiceSwitchCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Auto brightness", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("service_switch"),
            )
        }
    }
}

@Composable
private fun LiveReadoutCard(state: DashboardUiState, onPause: () -> Unit, onResume: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val status = when {
                !state.serviceRunning -> "Stopped"
                state.paused -> "Paused (manual override)"
                else -> "Monitoring ambient light"
            }
            Text(status, style = MaterialTheme.typography.titleMedium)
            Text("Lux: ${state.rawLux.fmt()} raw · ${state.smoothedLux.fmt()} smoothed")
            Text("Brightness: ${state.currentBrightness.fmtInt()} → ${state.targetBrightness.fmtInt()} (target)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.paused) {
                    FilledTonalButton(
                        onClick = onResume,
                        enabled = state.serviceRunning,
                        modifier = Modifier.testTag("resume_button"),
                    ) { Text("Resume") }
                } else {
                    FilledTonalButton(
                        onClick = onPause,
                        enabled = state.serviceRunning,
                        modifier = Modifier.testTag("pause_button"),
                    ) { Text("Pause") }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(lastSampleMs: Long?, health: ServiceHealthUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Service health", style = MaterialTheme.typography.labelMedium)
            // G2R-F5: drive the last-sample readout from the live pipeline (PipelineState.lastSampleMs),
            // not the never-written health store; recomputed each recomposition as a relative age.
            Text(
                "Last sensor sample: ${lastSampleMs.toRelativeAge()}",
                modifier = Modifier.testTag("last_sample_age"),
            )
            if (health.degradedMode) {
                Text(
                    "Degraded: ${health.degradedReason ?: "unknown"}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
