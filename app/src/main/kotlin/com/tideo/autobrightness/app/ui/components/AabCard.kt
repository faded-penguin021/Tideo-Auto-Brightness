package com.tideo.autobrightness.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.ui.theme.AabDataCaption
import com.tideo.autobrightness.app.ui.theme.AabDataDisplay
import com.tideo.autobrightness.app.ui.theme.Dimens

/**
 * S13b component library → **S13c' surface & data pass**. The reusable "Lego blocks" that replace the
 * per-screen ad-hoc compositions catalogued in `docs/rebuild/design/m3_audit.md`. Every value comes from
 * the S13a [Dimens] tokens / the frozen teal+gold `colorScheme` role-map — no raw brand recolouring.
 */

/**
 * The surface ladder (S13c' §04). M3 tonal elevation on a dark theme only uniformly lightens, so 14
 * resting cards read as one mass. Instead each card declares **intent**, and depth comes from a 1px
 * hairline highlight + honest shadow rather than glow/bevel:
 *  - **[Resting]** — the default content card (ladder L1): hairline edge + soft shadow.
 *  - **[Hero]** — the single focal/instrument card per screen (L2): raised elevation + teal accent edge.
 *  - **[Well]** — a recessed diagnostic/log surface (the glass-box well): variant fill, reads "behind".
 */
enum class AabCardVariant { Resting, Hero, Well }

/** The 1px highlight that defines a card edge on the dark ladder (S13c' §04 — a neutral compositing
 *  highlight, NOT a brand colour; the frozen palette is untouched). */
private val Hairline = Color.White.copy(alpha = 0.05f)

/**
 * The single elevated section container (m3_audit §4 "No shared card style"). Every place a screen
 * groups settings/readouts becomes an [AabCard]; the [variant] picks where it sits on the surface
 * ladder. Medium shape, uniform [Dimens.cardPadding], and a [Dimens.fieldSpacing] vertical rhythm.
 */
@Composable
fun AabCard(
    modifier: Modifier = Modifier,
    variant: AabCardVariant = AabCardVariant.Resting,
    contentPadding: PaddingValues = PaddingValues(Dimens.cardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Dimens.fieldSpacing),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val elevation = when (variant) {
        AabCardVariant.Hero -> Dimens.cardElevationHero
        AabCardVariant.Well -> 0.dp
        AabCardVariant.Resting -> Dimens.cardElevation
    }
    val container = when (variant) {
        AabCardVariant.Well -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(Dimens.dividerThickness, Hairline, shape),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
    ) {
        if (variant == AabCardVariant.Hero) {
            // L2 hero: a teal accent edge marks the one focal card per screen.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(Dimens.accentEdge)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column(
                    modifier = Modifier.padding(contentPadding),
                    verticalArrangement = verticalArrangement,
                    content = content,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/**
 * B4 → **S13c' readout line** (§05). The critical high-contrast data readout, recomposed as an
 * instrument line instead of a bolded sentence: a **tracked mono caption** above ([key]), then the big
 * **tabular gold value** ([value], the [AabDataDisplay] role), with the [unit] demoted to a small
 * `onSurfaceVariant` mark trailing the figure (a separate param so it never inherits gold). A subtle
 * `outlineVariant` bottom border closes the line.
 *
 * The value crossfades on change ([AabMotion.valueSpec]) so the glass box feels alive. The row keeps its
 * [testTag] and the value keeps `value_<testTag>` for the existing test contracts.
 */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.secondary,
    showDivider: Boolean = true,
    testTag: String = key,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.space2)
            .testTag(testTag),
    ) {
        Text(
            key.uppercase(),
            style = AabDataCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = Dimens.space1),
            verticalAlignment = Alignment.Bottom,
        ) {
            Crossfade(targetState = value, animationSpec = AabMotion.valueSpec(), label = "kv_$testTag") { v ->
                Text(
                    v,
                    style = AabDataDisplay,
                    color = valueColor,
                    modifier = Modifier.testTag("value_$testTag"),
                )
            }
            if (unit != null) {
                Text(
                    unit,
                    style = AabDataCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.space2, bottom = Dimens.space1),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = Dimens.space3),
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
