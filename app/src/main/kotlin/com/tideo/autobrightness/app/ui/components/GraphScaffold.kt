package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * One swipeable chart page in a [ChartPager] (G2R-F81). [content] is the actual chart composable;
 * S12 passes the deferred [com.tideo.autobrightness.app.ui.screens.ChartPlaceholder], and **S13
 * swaps in the real chart** for the same `title`/`testTag` without touching the pager or the host
 * screen. Keeping the slot list here is the single coordination point promised to S13.
 */
class ChartSlot(
    val title: String,
    val testTag: String,
    val content: @Composable () -> Unit,
)

/**
 * Horizontal swipe pager over a screen's **relevant graphs** (G2R-F81). Replaces the previous
 * vertical stacking of multiple charts: the relevant graph sits **above** its settings on every
 * chart-host screen, and the user swipes between the related graphs rather than scrolling past a
 * stack. A dot indicator + the current chart's title make the paging discoverable; a single-slot
 * pager renders just the chart (no indicator). Carries the `chart_pager` test tag.
 */
@Composable
fun ChartPager(slots: List<ChartSlot>, modifier: Modifier = Modifier) {
    if (slots.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { slots.size })
    Column(modifier.fillMaxWidth().testTag("chart_pager"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                slots[pagerState.currentPage].title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("chart_pager_title"),
            )
            if (slots.size > 1) {
                Text(
                    "Swipe to compare ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(state = pagerState) { page ->
            slots[page].content()
        }
        if (slots.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.indices.forEach { i ->
                    val selected = i == pagerState.currentPage
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .testTag("chart_pager_dot_$i"),
                    ) {}
                }
            }
        }
    }
}

/**
 * Subtly groups the settings that feed ONE graph and names which graph they affect (G2R-F82, pairs
 * with the [ChartPager]). An outlined card with a small caption ("Affects the {graph} graph") so the
 * user can see the link between a control and the chart above it. Carries a `group_<graph>` test tag.
 */
@Composable
fun GraphSettingsGroup(
    graph: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier.fillMaxWidth().testTag("group_${graph}")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Affects the $graph graph",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
