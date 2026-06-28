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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.tideo.autobrightness.app.ui.components.rememberToaster
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.navigation.AppRoute
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.state.DashboardViewModel
import com.tideo.autobrightness.app.state.ServiceHealthUiState
import com.tideo.autobrightness.app.ui.components.AabCard
import com.tideo.autobrightness.app.ui.components.AabTopBar
import com.tideo.autobrightness.app.ui.components.BrightnessInstrument
import com.tideo.autobrightness.app.ui.theme.AabDataCaption
import com.tideo.autobrightness.app.ui.theme.AabDataDisplay
import com.tideo.autobrightness.app.runtime.CircadianLocationStatus
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.AabOnGold
import com.tideo.autobrightness.app.ui.theme.Dimens
import com.tideo.autobrightness.platform.privilege.Tier

/** Stateful wrapper: wires the [DashboardViewModel] and navigation. */
@Composable
fun DashboardScreen(navController: NavHostController, viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberToaster()
    val tileAddSuccess = stringResource(R.string.dashboard_tile_add_success)
    val tileAlreadyAdded = stringResource(R.string.dashboard_tile_added)
    val tileFailed = stringResource(R.string.dashboard_tile_request_failed)
    // Evaluated once when the dashboard is shown (cheap, non-reactive — these only change after the
    // user adds a tile/widget, at which point they navigate away and back).
    val canAddTile = remember { viewModel.canAddTile() }
    val canAddWidget = remember { viewModel.canAddWidget() }
    DashboardContent(
        state = state,
        onToggleService = viewModel::setEnabled,
        onResume = viewModel::resume,
        onResetToAuto = viewModel::resetToAuto,
        canAddTile = canAddTile,
        canAddWidget = canAddWidget,
        onAddTile = {
            viewModel.addTile { result ->
                // Android 13+ StatusBarManager result codes (only reachable there via canAddTile()):
                //   2 = TILE_ADD_REQUEST_RESULT_TILE_ADDED (success),
                //   1 = TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                //   0 = TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED (user dismissed → no toast).
                // G3-F4 (Gate 3): result 2 was wrongly mapped to "already added", so a successful FIRST
                // add reported "tile already exists". 2 is success; 1 is the already-added case.
                when (result) {
                    2 -> toast(tileAddSuccess)
                    1 -> toast(tileAlreadyAdded)
                    DashboardViewModel.RESULT_REQUEST_FAILED -> toast(tileFailed)
                }
            }
        },
        onAddWidget = viewModel::addWidget,
        onOpenOnboarding = { navController.navigate(AppRoute.Onboarding.route) },
        onBack = { navController.popBackStack() },
    )
}

/**
 * Stateless, fully driven by [state] + callbacks so it can render under a Robolectric compose test.
 *
 * S13c' (§06) — the screen now leads with a single **brightness instrument** hero (the applied 0–255
 * level + teal track + status pill + master switch) that answers "what is my screen doing?" at a glance,
 * then a quiet **readout strip** (lux · circadian · context) demoted below it. The stale / override /
 * degraded banners keep their tinted surfaces — those encode state. There is no Pause control (G2R-F79):
 * stopping = the master switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onToggleService: (Boolean) -> Unit,
    onResume: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onBack: () -> Unit,
    onResetToAuto: () -> Unit = {},
    canAddTile: Boolean = false,
    canAddWidget: Boolean = false,
    onAddTile: () -> Unit = {},
    onAddWidget: () -> Unit = {},
) {
    Scaffold(
        topBar = { AabTopBar(title = stringResource(R.string.title_dashboard), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = Dimens.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            TierBadge(tier = state.tier, onClick = onOpenOnboarding)
            // S12.9d: the published live snapshot has aged past STALE while the service still claims to
            // be running — the loop may be wedged. Warn rather than show a confidently-wrong readout.
            if (state.stale && state.serviceRunning) {
                StaleBanner()
            }
            // The hero instrument — the one focal element (carries the status, switch + applied level).
            BrightnessInstrument(state = state, onToggleService = onToggleService)
            // The Resume-after-override affordance only appears when the engine paused itself because
            // the user changed brightness manually (prof755/task567) — the one case Resume is for.
            if (state.pausedByOverride) {
                OverrideCard(state.serviceRunning, onResume)
            }
            ReadoutStrip(state)
            // D-110: dynamic scaling is on but the live modifier is running on a stale (day-old) or
            // missing location — a quiet hint to turn Location on briefly so circadian tracks the real sun.
            state.circadianLocation?.let { cl ->
                if (cl.isStale || !cl.hasLocation) CircadianStaleHint(cl)
            }
            QuickActionsCard(
                serviceRunning = state.serviceRunning,
                canAddTile = canAddTile,
                canAddWidget = canAddWidget,
                onResetToAuto = onResetToAuto,
                onAddTile = onAddTile,
                onAddWidget = onAddWidget,
            )
            HealthCard(state.health)
        }
    }
}

/**
 * Owner-requested quick actions: a "Reset to auto" control (re-apply / snap brightness to the
 * computed value) shown while the service runs, plus one-tap shortcuts to add the Quick Settings tile
 * and the home-screen widget — surfaced only when they can be added (the launcher supports pinning and
 * no widget is placed yet; the tile prompt is Android 13+). Hidden entirely when nothing applies.
 */
@Composable
private fun QuickActionsCard(
    serviceRunning: Boolean,
    canAddTile: Boolean,
    canAddWidget: Boolean,
    onResetToAuto: () -> Unit,
    onAddTile: () -> Unit,
    onAddWidget: () -> Unit,
) {
    if (!serviceRunning && !canAddTile && !canAddWidget) return
    AabCard(verticalArrangement = Arrangement.spacedBy(Dimens.fieldSpacing)) {
        Text(stringResource(R.string.dashboard_quick_actions), style = MaterialTheme.typography.labelMedium)
        if (serviceRunning) {
            FilledTonalButton(
                onClick = onResetToAuto,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_reset"),
            ) { Text(stringResource(R.string.dashboard_reset_auto)) }
            // G3-F11: the owner couldn't tell what "Reset to auto" did — spell it out.
            Text(
                stringResource(R.string.dashboard_reset_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canAddTile) {
            OutlinedButton(
                onClick = onAddTile,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_add_tile"),
            ) { Text(stringResource(R.string.dashboard_add_tile)) }
        }
        if (canAddWidget) {
            OutlinedButton(
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_add_widget"),
            ) { Text(stringResource(R.string.dashboard_add_widget)) }
        }
    }
}

/** D-110: amber circadian staleness hint — the live modifier is on a day-old cached or default sun
 *  position. Same gold convention as [StaleBanner]; tapping the Circadian screen lets the user refresh. */
@Composable
private fun CircadianStaleHint(status: CircadianLocationStatus) {
    val text = if (status.isStale) {
        stringResource(R.string.dashboard_circadian_stale, status.ageDays ?: 0L)
    } else {
        stringResource(R.string.dashboard_circadian_no_location)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("circadian_stale_hint"),
        colors = CardDefaults.cardColors(containerColor = AabGold, contentColor = AabOnGold),
    ) {
        Text(text, modifier = Modifier.padding(Dimens.cardPadding), style = MaterialTheme.typography.bodyMedium)
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
            modifier = Modifier.padding(Dimens.cardPadding),
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

/** Shown only while [pausedByOverride]: explains the pause and offers the one Resume control. Styled as
 *  Tasker's gold "Automation Paused" bar — vivid brand gold (not the muted dark `secondaryContainer`)
 *  with a high-contrast dark RESUME button carrying a ▶ icon (D-111). */
@Composable
private fun OverrideCard(serviceRunning: Boolean, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("override_card"),
        colors = CardDefaults.cardColors(containerColor = AabGold, contentColor = AabOnGold),
    ) {
        Column(
            Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Text(stringResource(R.string.dashboard_manual_override), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.dashboard_override_explain),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onResume,
                enabled = serviceRunning,
                colors = ButtonDefaults.buttonColors(containerColor = AabOnGold, contentColor = AabGold),
                modifier = Modifier.testTag("resume_button"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.dashboard_resume_button))
            }
        }
    }
}

/**
 * The quiet readout strip below the hero (S13c' §06): lux · circadian · context, each shown only when
 * meaningful (existing `takeIf` guards). The raw-lux + last-sample age and the super-dimming line sit
 * beneath as supporting detail. Values render in tabular Plex Mono via [ReadoutCell].
 */
@Composable
private fun ReadoutStrip(state: DashboardUiState) {
    AabCard(verticalArrangement = Arrangement.spacedBy(Dimens.fieldSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        ) {
            ReadoutCell(
                caption = stringResource(R.string.dashboard_ambient_light),
                value = state.smoothedLux.fmt(),
                valueTag = "dashboard_lux",
                modifier = Modifier.weight(1f),
            )
            state.circadianScale?.takeIf { kotlin.math.abs(it - 1.0) > 0.001 }?.let {
                ReadoutCell(
                    caption = stringResource(R.string.dashboard_circadian),
                    value = "%.2f×".format(it),
                    valueTag = "dashboard_scale",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Profile + active context, always shown so the user can see which profile is in force and
        // whether a context rule selected it (owner request).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        ) {
            ReadoutCell(
                caption = stringResource(R.string.dashboard_active_profile),
                value = state.activeProfile ?: "—",
                valueTag = "dashboard_profile",
                mono = false,
                modifier = Modifier.weight(1f),
            )
            ReadoutCell(
                caption = stringResource(R.string.dashboard_active_context),
                value = state.activeContext ?: stringResource(R.string.dashboard_context_none),
                valueTag = "dashboard_context",
                mono = false,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.dashboard_lux_raw, state.rawLux.fmt(), state.lastSampleMs.toRelativeAge()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("last_sample_age"),
        )
        // Only surface dimming when engaged.
        state.dimmingStrength.takeIf { it > 0.0 }?.let {
            Text(
                stringResource(R.string.dashboard_super_dimming) + " %.0f%%".format(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_dimming"),
            )
        }
    }
}

/** One labelled readout in the strip: a tracked caption above a tabular-mono (or plain) value. */
@Composable
private fun ReadoutCell(
    caption: String,
    value: String,
    valueTag: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            caption.uppercase(),
            style = AabDataCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (mono) AabDataDisplay else MaterialTheme.typography.titleMedium,
            color = if (mono) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Dimens.space1).testTag(valueTag),
        )
    }
}

@Composable
private fun HealthCard(health: ServiceHealthUiState) {
    if (!health.degradedMode) return
    AabCard(verticalArrangement = Arrangement.spacedBy(Dimens.fieldRowPaddingTight)) {
        Text(stringResource(R.string.dashboard_service_health), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(R.string.dashboard_degraded, health.degradedReason ?: "unknown"),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("dashboard_degraded"),
        )
    }
}

private fun Double?.fmt(): String = this?.let { "%.0f".format(it) } ?: "—"

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
