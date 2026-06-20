package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.ui.theme.AabGold
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * S13b component library (cont.) — navigation + action blocks from `m3_audit.md` §2.5 blueprints
 * B2/B3/B5. Built here, applied by S13c. The Menu's existing private `MenuHeroCard`/`MenuNavRow`
 * remain until S13c migrates the screens onto these shared versions (behaviour-preserving).
 */

/**
 * B2 — the prominent navigation card (promoted from `MenuScreen`'s private hero card). A `large`-shape
 * [ElevatedCard] on `primaryContainer`, a **teal left-edge accent** bar, a gold-sun [icon] (the one
 * sanctioned raw-`AabGold` tint, m3_audit §2.4), a [title] + optional [subtitle], and a right-aligned
 * chevron. Presses give a subtle scale feedback (the §4 "press feedback on hero/CTA" motion).
 */
@Composable
fun HeroNavCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    testTag: String = title,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) AabMotion.PRESS_SCALE else 1f
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = Dimens.cardElevationRaised),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Teal left-edge accent bar (m3_audit §2.5 B2).
            Box(
                modifier = Modifier
                    .width(Dimens.space2)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(Dimens.heroCardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.rowGapWide),
            ) {
                Icon(icon, contentDescription = null, tint = AabGold)
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/**
 * B3 — a clickable navigation row designed to live *inside* an [AabCard] group (no full-width
 * dividers; the card is the grouping). Optional leading [icon] (`onSurface`), [label] left, grey
 * (`onSurfaceVariant`) chevron right.
 */
@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = label,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.rowGap)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Visual style for an [ActionButton] in an [ActionButtonBar] (B5). */
enum class ActionButtonStyle { Tonal, Outlined }

/**
 * One action in an [ActionButtonBar]. The (i18n) [label] is supplied by the caller; [icon] is the
 * optional leading mark; [style] picks tonal (primary) vs outlined (secondary) emphasis.
 */
data class ActionButton(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val style: ActionButtonStyle = ActionButtonStyle.Tonal,
    val enabled: Boolean = true,
    val testTag: String = label,
)

/**
 * B5 — a horizontal, weight-even row of action buttons, each with an optional leading icon
 * (generalised from the `DraftApplyBar` Apply/Discard pattern). Tonal for primary, outlined for
 * secondary; equal `weight(1f)`; [Dimens.rowGap] between.
 */
@Composable
fun ActionButtonBar(
    actions: List<ActionButton>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            when (action.style) {
                ActionButtonStyle.Tonal -> FilledTonalButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.weight(1f).testTag(action.testTag),
                ) { ActionButtonContent(action) }
                ActionButtonStyle.Outlined -> OutlinedButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.weight(1f).testTag(action.testTag),
                ) { ActionButtonContent(action) }
            }
        }
    }
}

@Composable
private fun RowScope.ActionButtonContent(action: ActionButton) {
    if (action.icon != null) {
        Icon(
            action.icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSize),
        )
    }
    Text(
        action.label,
        modifier = Modifier.padding(start = if (action.icon != null) Dimens.space3 else 0.dp),
    )
}
