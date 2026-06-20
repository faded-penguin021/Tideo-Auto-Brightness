package com.tideo.autobrightness.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.state.DashboardUiState
import com.tideo.autobrightness.app.ui.theme.AabDataCaption
import com.tideo.autobrightness.app.ui.theme.AabMono
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * S13c' §06 — the Dashboard **hero instrument**. One glance answers "what is my screen doing right now?":
 * a single large brightness readout (the applied 0–255 level in near-white Plex Mono), a thin teal track
 * that eases to the new value, and a status pill. All values already live on [DashboardUiState] — this is
 * composition, not new data. When the master switch is OFF the instrument greys out so on/off is
 * unmistakable. The switch stays one tap away (inline, top-right).
 *
 * Test contract preserved: the big number keeps `dashboard_brightness`, the status keeps
 * `dashboard_status`, the switch keeps `service_switch`.
 */
@Composable
fun BrightnessInstrument(
    state: DashboardUiState,
    onToggleService: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = state.serviceEnabled
    val applied = state.currentBrightness ?: state.targetBrightness
    val target = state.targetBrightness

    // Greyed out when off; near-white instrument readout when running.
    val numberColor by animateColorAsState(
        if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(AabMotion.DURATION_MEDIUM),
        label = "instrument_number",
    )

    AabCard(modifier = modifier, variant = AabCardVariant.Hero) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.dashboard_applied_brightness).uppercase(),
                style = AabDataCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(state)
        }

        // The big number: applied 0–255 level, tabular Plex Mono.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                applied?.toString() ?: "—",
                style = AabDataDisplayLarge,
                color = numberColor,
                modifier = Modifier.testTag("dashboard_brightness"),
            )
            Text(
                "/ 255",
                style = AabDataCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.space3, bottom = Dimens.space3),
            )
            Box(Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = onToggleService,
                modifier = Modifier.testTag("service_switch"),
            )
        }

        BrightnessTrack(applied = applied, target = target, enabled = on)
    }
}

/** A small high-emphasis variant of the data-display role for the hero's single large figure. */
private val AabDataDisplayLarge = androidx.compose.ui.text.TextStyle(
    fontFamily = AabMono,
    fontWeight = FontWeight.Medium,
    fontSize = 64.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
)

/** The teal 0–255 track — the only chart-free visualisation on the Dashboard. Eases to its new value. */
@Composable
private fun BrightnessTrack(applied: Int?, target: Int?, enabled: Boolean) {
    val fraction = ((applied ?: target ?: 0).coerceIn(0, 255)) / 255f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(AabMotion.DURATION_MEDIUM),
        label = "brightness_track",
    )
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val fill =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.space2)
            .clip(CircleShape)
            .testTag("brightness_track"),
    ) {
        val r = size.height / 2f
        drawRoundRect(color = trackBg, cornerRadius = CornerRadius(r, r))
        if (animated > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset.Zero,
                size = Size(size.width * animated, size.height),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

/** Status pill: a teal/gold/red dot + the existing status string (tagged `dashboard_status`). */
@Composable
private fun StatusPill(state: DashboardUiState) {
    val (label, dot) = when {
        !state.serviceEnabled -> stringResource(R.string.dashboard_status_off) to MaterialTheme.colorScheme.error
        !state.serviceRunning -> stringResource(R.string.dashboard_status_starting) to MaterialTheme.colorScheme.secondary
        state.pausedByOverride -> stringResource(R.string.dashboard_status_paused_override) to MaterialTheme.colorScheme.secondary
        state.paused -> stringResource(R.string.dashboard_status_paused) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.dashboard_status_active) to MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(Dimens.dividerThickness, Color.White.copy(alpha = 0.10f), CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.space3, vertical = Dimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Box(Modifier.size(Dimens.space2).clip(CircleShape).background(dot))
        Text(
            label,
            style = AabDataCaption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("dashboard_status"),
        )
    }
}
