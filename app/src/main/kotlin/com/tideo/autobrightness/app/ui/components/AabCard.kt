package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * S13b component library — the reusable "Lego blocks" that replace the per-screen ad-hoc compositions
 * catalogued in `docs/rebuild/design/m3_audit.md` (§2.5 owner "Pro-Tool" blueprints, §4 cross-cutting
 * gaps). These are *built* here and *applied* screen-by-screen in S13c; this file is behaviour-neutral
 * until a screen adopts it. Every value comes from the S13a [Dimens] tokens / the frozen teal+gold
 * `colorScheme` role-map — no raw `dp` literals, no recoloring.
 */

/**
 * The single elevated section container (m3_audit §4 "No shared card style"). Every place a screen
 * groups settings/readouts into a bare `Column` or a raw `Card` becomes an [AabCard]: medium shape,
 * resting [Dimens.cardElevation] (or [Dimens.cardElevationRaised] when [raised]), uniform
 * [Dimens.cardPadding], and a default [Dimens.fieldSpacing] vertical rhythm between children.
 */
@Composable
fun AabCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Dimens.cardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Dimens.fieldSpacing),
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (raised) Dimens.cardElevationRaised else Dimens.cardElevation,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * B4 — the **critical high-contrast data readout** the owner spec flags (m3_audit §2.5/§4). A
 * `SpaceBetween` row: the [key] left (`bodyLarge`, `onSurface`) and the [value] right in **bold gold**
 * (`secondary`, the frozen `AabGold`) for the "data-pop", with a subtle `outlineVariant` bottom border.
 *
 * Use for every derived/live numeric — Dashboard lux/target, derived form2A/form3A, throttle, dimming
 * readouts. The gold value uses the *theme role* ([androidx.compose.material3.ColorScheme.secondary]),
 * not a raw `AabGold`, so it tracks the scheme; [valueColor] overrides only when a row needs it.
 */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.secondary,
    showDivider: Boolean = true,
    testTag: String = key,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(testTag)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.rowGap / 2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                key,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = Dimens.rowGap),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.testTag("value_$testTag"),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * The shared empty-state placeholder (m3_audit §4): an optional [icon] above a muted [text], centered.
 * Replaces the scattered inline "no location yet / empty log / no profiles" hints with one consistent
 * surface (Circadian, Live Debug, Profiles/Contexts, Tools). Caller supplies the (i18n) [text].
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "empty_state",
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Dimens.cardPadding).testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
