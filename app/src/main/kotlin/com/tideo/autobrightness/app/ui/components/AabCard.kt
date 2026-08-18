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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.ui.theme.AabDataCaption
import com.tideo.autobrightness.app.ui.theme.AabDataDisplay
import com.tideo.autobrightness.app.ui.theme.Dimens

/** S13c' component library: reusable blocks per m3_audit.md, values from Dimens tokens / frozen colorScheme. */

/** Surface ladder (S13c' §04): Resting (L1: hairline+shadow), Hero (L2: raised+teal edge), Well (recessed variant). */
enum class AabCardVariant { Resting, Hero, Well }

/** 1px neutral highlight for card edges (S13c' §04). */
private val Hairline = Color.White.copy(alpha = 0.05f)

/** Elevated section container (m3_audit §4): groups settings/readouts; [variant] picks surface ladder position. */
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

/** High-contrast data readout line (§05): tracked caption [key], gold [value], unit mark; crossfades on change. */
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
            .testTag(testTag)
            // D-156: merge descendants so TalkBack reads as single announcement.
            .semantics(mergeDescendants = true) {},
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

/** Empty-state placeholder (m3_audit §4): optional [icon] above muted [text], centered. */
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
