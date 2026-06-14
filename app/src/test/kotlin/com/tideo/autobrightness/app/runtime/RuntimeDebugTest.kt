package com.tideo.autobrightness.app.runtime

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S12.5c (G2-F15): the runtime debug sink Flashes a category only when the live debugLevel selects
 * it (the %AAB_Debug selector is single-valued, D-023). NoOp never surfaces anything.
 */
@RunWith(RobolectricTestRunner::class)
class RuntimeDebugTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun toastSink_emitsOnlyWhenLevelMatchesCategory() {
        val sink = ToastDebugSink(context)

        // Active level 5 == SUPER_DIMMING → the matching category surfaces, others stay silent.
        sink.emit(DebugCategory.LIGHT_EVAL, activeLevel = 5) { "should not show" }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(ShadowToast.getLatestToast(), "non-matching category must not toast")

        sink.emit(DebugCategory.SUPER_DIMMING, activeLevel = 5) { "dim ON" }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val text = ShadowToast.getTextOfLatestToast()
        assertTrue(text.contains("dim ON"), "matching category toasts its message: $text")
        assertTrue(text.contains("Super Dimming"), "toast is labelled with the category: $text")
    }

    @Test
    fun noOpSink_neverToasts() {
        NoOpDebugSink.emit(DebugCategory.LIGHT_EVAL, activeLevel = 3) { "nope" }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun categoryLevels_matchSelectorIndices() {
        // The enum levels must line up with MiscScreen.DEBUG_LABELS indices (D-023).
        assertEquals(1, DebugCategory.SKIP_ANIMATIONS.level)
        assertEquals(5, DebugCategory.SUPER_DIMMING.level)
        assertEquals(9, DebugCategory.CONTEXT_LOCATION.level)
    }
}
