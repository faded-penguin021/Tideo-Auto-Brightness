package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
class TestTagsAsResourceIdTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rootSurface_exportsDescendantTestTagsAsResourceIds() {
        compose.setContent {
            TideoRootSurface { Box(Modifier.testTag("service_switch")) }
        }

        compose.onNodeWithTag("service_switch", useUnmergedTree = true)
            .assert(hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsPropertiesAndroid.TestTagsAsResourceId, true)))
    }
}
