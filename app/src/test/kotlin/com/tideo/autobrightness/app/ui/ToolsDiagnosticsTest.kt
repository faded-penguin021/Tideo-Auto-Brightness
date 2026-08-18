package com.tideo.autobrightness.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// C1 acceptance (D-158): Tools → Diagnostics crash trace copy + a11y.
@RunWith(RobolectricTestRunner::class)
class ToolsDiagnosticsTest {

    @get:Rule val compose = createComposeRule()

    private fun tools(latestCrashLog: String?) {
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    onBack = {}, onRunWizard = { _, _ -> null }, onApplyWizard = {},
                    latestCrashLog = latestCrashLog,
                )
            }
        }
    }

    @Test fun copiesTheCrashLogToTheClipboard() {
        val trace = "Tideo Auto Brightness crash at 2026-07-07T00:00:00Z\n\n" +
            "java.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.kt:42)"
        tools(trace)

        compose.onNodeWithTag("copy_crash_log").performScrollTo().performClick()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(trace, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test fun showsNoneRecordedWhenThereIsNoCrash() {
        tools(null)
        compose.onNodeWithTag("no_crash_log").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("copy_crash_log").assertDoesNotExist()
    }

    @Test fun copyButtonIsLabeledForTalkBack() {
        tools("some captured trace")
        compose.assertAllInteractiveNodesAreLabeled()
    }
}
