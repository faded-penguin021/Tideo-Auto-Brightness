package com.tideo.autobrightness.app.settings

import com.tideo.autobrightness.R
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F38 settings-display helper coverage: changed-vs-default rows. */
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
        // Runtime/identity keys excluded from profile parameters.
        val vars = AabSettings().displayRows().map { it.taskerVariable }
        assertTrue("%AAB_Service" !in vars)
        assertTrue("%AAB_ContextOverride" !in vars)
    }

    @Test
    fun globalAndDerivedKeys_areExcludedFromTheList() {
        // G2R-F84: global prefs (debug/overrides/QS/notify) + derived midpoint excluded from profile diff.
        val vars = AabSettings().displayRows().map { it.taskerVariable }
        assertTrue("%AAB_Debug" !in vars)
        assertTrue("%AAB_DetectOverrides" !in vars)
        assertTrue("%AAB_QSUse" !in vars)
        assertTrue("%AAB_NotifyUse" !in vars)
        assertTrue("%AAB_ThreshMidpoint" !in vars)
    }

    @Test
    fun crypticKeys_useLabelResources() {
        // G2R-F84: friendly labels instead of raw "form1A"/"form2C" names.
        val rows = AabSettings().displayRows().associateBy { it.taskerVariable }
        assertEquals(R.string.curve_form1a, rows.getValue("%AAB_Form1A").labelRes)
        assertEquals(R.string.curve_form2c, rows.getValue("%AAB_Form2C").labelRes)
        assertEquals(R.string.misc_min_brightness, rows.getValue("%AAB_MinBright").labelRes)
    }
}
