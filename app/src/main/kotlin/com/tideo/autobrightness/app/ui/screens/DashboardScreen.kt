package com.tideo.autobrightness.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tideo.autobrightness.platform.privilege.Tier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        onNavigate = { route -> navController.navigate(route.route) },
    )
}

/** Stateless, fully driven by [state] + callbacks so it can render under a Robolectric compose test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onToggleService: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Tideo Auto Brightness") }) },
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
            state.activeContext?.let { ContextCard(it) }
            HealthCard(state.health)
            HorizontalDivider()
            AppRoute.dashboardDestinations.forEach { route ->
                OutlinedButton(
                    onClick = { onNavigate(route) },
                    modifier = Modifier.fillMaxWidth().testTag("nav_${route.route}"),
                ) { Text(route.label) }
            }
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
private fun ContextCard(activeContext: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Active context", style = MaterialTheme.typography.labelMedium)
            Text(activeContext, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HealthCard(health: ServiceHealthUiState) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Service health", style = MaterialTheme.typography.labelMedium)
            Text("Last sensor sample: ${health.lastSensorTimestampMs.toHumanTime()}")
            Text("Last brightness apply: ${health.lastApplyTimestampMs.toHumanTime()}")
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

private fun Long?.toHumanTime(): String {
    if (this == null) return "Never"
    return DateTimeFormatter.ISO_LOCAL_TIME.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0),
    )
}
