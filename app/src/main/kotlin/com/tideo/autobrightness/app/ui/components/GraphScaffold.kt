package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * One swipeable chart page in a [ChartPager] (G2R-F81). [content] is the actual chart composable.
 * S12 passed a deferred placeholder here; **S13d swapped in the real charts** (the `ui.graph` chart
 * composables) for the same `title`/`testTag` without touching the pager or the host screen. The slot
 * list is the single coordination point between the host screens and the chart library.
 */
class ChartSlot(
    val title: String,
    val testTag: String,
    val content: @Composable () -> Unit,
)

/**
 * Pager over a screen's **relevant graphs** (G2R-F81): the relevant graph sits **above** its settings,
 * and the user pages between related graphs rather than scrolling past a vertical stack.
 *
 * The charts themselves are **interactive** (drag-scrub readout — owner requirement), which consumes
 * the horizontal drag the pager would otherwise swipe on. So page navigation is by **tap**: ‹ › arrows
 * flanking the title + clickable page dots (`pagerState.userScrollEnabled=false`). A single-slot pager
 * renders just the chart. Carries the `chart_pager` test tag.
 */
@Composable
fun ChartPager(slots: List<ChartSlot>, modifier: Modifier = Modifier) {
    if (slots.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { slots.size })
    val scope = rememberCoroutineScope()
    val multi = slots.size > 1
    Column(modifier.fillMaxWidth().testTag("chart_pager"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multi) {
                PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chart", "chart_pager_prev") {
                    val target = (pagerState.currentPage - 1 + slots.size) % slots.size
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
            }
            Text(
                slots[pagerState.currentPage].title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).testTag("chart_pager_title"),
                textAlign = TextAlign.Center,
            )
            if (multi) {
                PagerArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chart", "chart_pager_next") {
                    val target = (pagerState.currentPage + 1) % slots.size
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
            }
        }
        // userScrollEnabled=false: the interactive chart owns horizontal drags; pages change via taps.
        HorizontalPager(state = pagerState, userScrollEnabled = false) { page ->
            slots[page].content()
        }
        if (multi) {
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
                            .size(if (selected) 10.dp else 8.dp)
                            .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                            .testTag("chart_pager_dot_$i"),
                    ) {}
                }
            }
        }
    }
}

/** A tappable page-step affordance for the [ChartPager] (swipe is consumed by the chart scrub).
 *  Uses a real Material chevron icon rather than a ‹ / › text glyph (UI-consistency: icons, not glyphs). */
@Composable
private fun PagerArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, testTag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary)
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
