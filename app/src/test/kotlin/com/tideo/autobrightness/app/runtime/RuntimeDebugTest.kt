package com.tideo.autobrightness.app.runtime

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runtime debug sink: Flash category only when debugLevel selects it (D-023, G2-F15). */
@RunWith(RobolectricTestRunner::class)
class RuntimeDebugTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun toastSink_emitsOnlyWhenLevelMatchesCategory() {
        val sink = ToastDebugSink(context)

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
        assertEquals(6, DebugCategory.OVERLAY_PREVIEW.level)
        assertEquals(9, DebugCategory.CONTEXT_LOCATION.level)
    }

    // ---- F48: Dynamic Scale debug timing gate (~2 min into a transition, never per light change) ----

    @Test
    fun dynamicScaleGate_firesOnlyTwoMinIntoTransitionThenThrottles() {
        val gate = DynamicScaleDebugGate(delayMs = 120_000L, intervalMs = 120_000L)

        assertFalse(gate.shouldEmit(0L, transitionActive = true))
        assertFalse(gate.shouldEmit(60_000L, transitionActive = true))
        assertTrue(gate.shouldEmit(120_000L, transitionActive = true))
        assertFalse(gate.shouldEmit(180_000L, transitionActive = true))
        assertTrue(gate.shouldEmit(240_000L, transitionActive = true))
    }

    @Test
    fun dynamicScaleGate_neverFiresWithoutAnActiveTransition() {
        val gate = DynamicScaleDebugGate()
        // Never emit without active transition; settled stretch resets 2-min clock.
        repeat(10) { i -> assertFalse(gate.shouldEmit(i * 200_000L, transitionActive = false)) }
        assertFalse(gate.shouldEmit(2_000_000L, transitionActive = true))
    }

    // ---- F50/F51/F52: AabFlash cancel-previous + global presenter + instant cancel ----

    @Test
    fun aabFlash_cancelsPreviousBeforeShowingNext_andCancelClearsImmediately() {
        val events = mutableListOf<String>()
        val presenter = object : AabFlash.Presenter {
            override fun show(text: String) { events += "show:$text" }
            override fun hide() { events += "hide" }
        }
        AabFlash.register(presenter)
        try {
            AabFlash.show(context, "a")
            AabFlash.show(context, "b")
            AabFlash.cancel()
            // Each show cancels previous (F51); trailing hide is explicit cancel (F52).
            assertEquals(listOf("hide", "show:a", "hide", "show:b", "hide"), events)
        } finally {
            AabFlash.register(null)
        }
    }

    @Test
    fun aabFlash_fallsBackToForegroundToastWhenNoPresenter() {
        AabFlash.register(null)
        AabFlash.show(context, "fallback flash")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("fallback flash", ShadowToast.getTextOfLatestToast())
    }
}
