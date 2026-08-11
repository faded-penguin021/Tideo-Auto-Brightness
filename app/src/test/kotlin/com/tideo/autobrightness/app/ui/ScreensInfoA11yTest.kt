package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.ContextRule
import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.LegacyConfigEntry
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.ui.screens.AboutContent
import com.tideo.autobrightness.app.ui.screens.ContextsContent
import com.tideo.autobrightness.app.ui.screens.ProfilesContent
import com.tideo.autobrightness.app.ui.screens.RuleEditor
import com.tideo.autobrightness.app.ui.screens.UserGuideContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A6 acceptance (D-156): profiles/contexts + info surfaces under TalkBack gate. */
@RunWith(RobolectricTestRunner::class)
class ScreensInfoA11yTest {

    @get:Rule
    val compose = createComposeRule()

    private val profiles = listOf(
        SavedProfile("Default", AabSettings(), builtIn = true),
        SavedProfile("Mine", AabSettings()),
    )
    private val legacy = listOf(LegacyConfigEntry("legacy_a", android.net.Uri.parse("content://x/a")))

    private fun renderProfiles() {
        compose.setContent {
            MaterialTheme {
                ProfilesContent(
                    profiles = profiles, legacyEntries = legacy, contextLocked = true, status = null,
                    onBack = {}, onApplyProfile = {}, onOverwriteProfile = {}, onDeleteProfile = {},
                    onSaveCurrentAs = {}, onRestoreFactory = {}, onResumeContext = {}, onReset = {},
                    onExport = {}, onImport = {}, onChooseLegacyFolder = {}, onLoadLegacy = {},
                    loadError = "unreadable", onDismissLoadError = {},
                )
            }
        }
    }

    @Test
    fun about_allInteractiveNodesAreLabeled() {
        compose.setContent { MaterialTheme { AboutContent(version = "1.8.0", onBack = {}) } }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun about_sectionHeadersAreHeadings() {
        compose.setContent { MaterialTheme { AboutContent(version = "1.8.0", onBack = {}) } }
        compose.assertHeadingExists("About & License")
        compose.assertHeadingExists("Acknowledgments")
        compose.assertHeadingExists("MIT License")
    }

    @Test
    fun userGuide_allInteractiveNodesAreLabeled() {
        // The manual body is a WebView (AndroidView); the compose-side controls are the back arrow and
        // the "Got it" button, both text-labeled.
        compose.setContent { MaterialTheme { UserGuideContent(onBack = {}) } }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    // --- Profiles (standalone) -------------------------------------------------------------------

    @Test
    fun profiles_allInteractiveNodesAreLabeled_withSectionsExpanded() {
        renderProfiles()
        // Reveal the collapsible manage + legacy sections so their actions render under the audit.
        compose.onNodeWithTag("manage_section").performScrollTo().performClick()
        compose.onNodeWithTag("legacy_section").performScrollTo().performClick()
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun profiles_savedHeaderIsAHeading() {
        renderProfiles()
        compose.assertHeadingExists("Saved profiles")
    }

    // --- Contexts rule list ----------------------------------------------------------------------

    @Test
    fun contexts_allInteractiveNodesAreLabeled_ruleList() {
        val rule = ContextRule(id = "r1", name = "Cinema", profile = "Default")
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = listOf(rule), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.assertAllInteractiveNodesAreLabeled()
    }

    // --- Rule editor (rendered directly — it lives in a second Dialog window) --------------------

    private fun renderEditorAllSectionsOpen() {
        val rule = ContextRule(id = "r1", name = "Cinema", profile = "Default", triggers = ContextTriggers())
        compose.setContent {
            MaterialTheme {
                RuleEditor(
                    rule = rule,
                    profileNames = listOf("Default", "Movies"),
                    apps = listOf(AppEntry("com.example", "Example App")),
                    solarLabel = "06:42" to "20:15",
                    onCancel = {}, onSave = {},
                    onUseCurrentSsid = {}, onUseCurrentLocation = {},
                    hasUsageAccess = { false }, onRequestUsageAccess = {},
                )
            }
        }
        listOf("wifi", "time", "location", "battery", "apps").forEach {
            compose.onNodeWithTag("trigger_toggle_$it").performScrollTo().performClick()
        }
    }

    @Test
    fun ruleEditor_allInteractiveNodesAreLabeled_everyTriggerOpen() {
        renderEditorAllSectionsOpen()
        compose.assertAllInteractiveNodesAreLabeled()
    }

    @Test
    fun ruleEditor_chargingSwitchIsLabeled() {
        // D-156: the battery section's "only while charging" Switch label is a sibling Text — the fix
        // gives the switch node its own contentDescription so TalkBack reads more than "switch".
        renderEditorAllSectionsOpen()
        compose.onNodeWithTag("rule_charging").assertContentDescriptionEquals("Only while charging")
    }

    @Test
    fun ruleEditor_sectionHeadersAreHeadings() {
        renderEditorAllSectionsOpen()
        compose.assertHeadingExists("Rule")
        compose.assertHeadingExists("Triggers")
    }
}
