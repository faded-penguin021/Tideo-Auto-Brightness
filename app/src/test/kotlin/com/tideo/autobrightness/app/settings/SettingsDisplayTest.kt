package com.tideo.autobrightness.app.settings

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure-JVM coverage for the F38 settings-display helper (changed-vs-default rows). */
class SettingsDisplayTest {

    @Test
    fun defaults_haveNoChangedRows() {
        assertEquals(0, AabSettings().changedCount())
        assertTrue(AabSettings().displayRows().none { it.changed })
    }

    @Test
    fun tunedValues_areFlaggedChanged() {
        val tuned = AabSettings(minBrightness = 99, scale = 1.5f)
        assertEquals(2, tuned.changedCount())
        val rows = tuned.displayRows().filter { it.changed }.map { it.taskerVariable }
        assertTrue("%AAB_MinBright" in rows)
        assertTrue("%AAB_Scale" in rows)
    }

    @Test
    fun runtimeKeys_areExcludedFromTheList() {
        // serviceEnabled/contextOverride are runtime/identity, not profile parameters — never listed.
        val vars = AabSettings().displayRows().map { it.taskerVariable }
        assertTrue("%AAB_Service" !in vars)
        assertTrue("%AAB_ContextOverride" !in vars)
    }
}
