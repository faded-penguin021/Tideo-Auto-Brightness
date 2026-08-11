package com.tideo.autobrightness.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.graph.AlphaResponseChart
import com.tideo.autobrightness.app.ui.graph.BrightnessCurveChart
import com.tideo.autobrightness.app.ui.graph.CircadianDimmingChart
import com.tideo.autobrightness.app.ui.graph.CircadianScaleChart
import com.tideo.autobrightness.app.ui.graph.DimmingChart
import com.tideo.autobrightness.app.ui.graph.PowerDrawChart
import com.tideo.autobrightness.app.ui.graph.ReactivityChart
import com.tideo.autobrightness.app.ui.graph.TaperChart
import com.tideo.autobrightness.app.ui.theme.TideoTheme
import com.tideo.autobrightness.domain.brightness.BrightnessCurveConfig
import com.tideo.autobrightness.domain.brightness.DynamicScalingConfig
import com.tideo.autobrightness.domain.brightness.ThresholdConfig
import com.tideo.autobrightness.domain.power.PowerDrawSample
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A2 acceptance (D-156): Canvas graphs expose contentDescription text alternative. */
@RunWith(RobolectricTestRunner::class)
class GraphsA11yTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun brightnessCurveGraphExposesDescriptionWithRange() {
        compose.setContent { TideoTheme { BrightnessCurveChart(curve = BrightnessCurveConfig()) } }
        compose.onNodeWithContentDescription("Brightness curve graph", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("from 10 to 255", substring = true).assertIsDisplayed()
    }

    @Test
    fun dimmingGraphExposesDescriptionWithParams() {
        compose.setContent {
            TideoTheme {
                DimmingChart(minBrightness = 5, dimmingThreshold = 30, dimmingExponent = 2.5, dimmingStrength = 80)
            }
        }
        compose.onNodeWithContentDescription("Super dimming graph", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("below 30", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("80 percent", substring = true).assertIsDisplayed()
    }

    @Test
    fun reactivityGraphExposesDescription() {
        compose.setContent { TideoTheme { ReactivityChart(threshold = ThresholdConfig()) } }
        compose.onNodeWithContentDescription("Reactivity graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun alphaGraphExposesDescription() {
        compose.setContent { TideoTheme { AlphaResponseChart(deltaFactor = 1.8) } }
        compose.onNodeWithContentDescription("Smoothing response graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun circadianDimmingGraphExposesDescription() {
        compose.setContent { TideoTheme { CircadianDimmingChart(scaling = DynamicScalingConfig()) } }
        compose.onNodeWithContentDescription("Circadian dimming graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun circadianScaleGraphExposesDescription() {
        compose.setContent { TideoTheme { CircadianScaleChart(scaling = DynamicScalingConfig()) } }
        compose.onNodeWithContentDescription("Circadian scaling graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun taperGraphExposesDescription() {
        compose.setContent { TideoTheme { TaperChart(curve = BrightnessCurveConfig(), scaleSpreadPercent = 15) } }
        compose.onNodeWithContentDescription("Taper graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun powerGraphExposesDescriptionWhenMeasured() {
        compose.setContent {
            TideoTheme {
                PowerDrawChart(samples = listOf(PowerDrawSample(0, 0.0, 0.0), PowerDrawSample(255, 220.0, 0.88)))
            }
        }
        compose.onNodeWithContentDescription("Power draw graph", substring = true).assertIsDisplayed()
    }

    @Test
    fun chartPagerArrowsAreLabeledAndAudited() {
        compose.setContent {
            TideoTheme {
                ChartPager(
                    slots = listOf(
                        ChartSlot("First", "slot_a") { Text("A") },
                        ChartSlot("Second", "slot_b") { Text("B") },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Previous chart").assertHasClickAction()
        compose.onNodeWithContentDescription("Next chart").assertHasClickAction()
        compose.assertAllInteractiveNodesAreLabeled()
    }
}
