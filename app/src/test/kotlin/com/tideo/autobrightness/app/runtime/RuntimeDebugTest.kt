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
        assertEquals(6, DebugCategory.OVERLAY_PREVIEW.level)
        assertEquals(9, DebugCategory.CONTEXT_LOCATION.level)
    }

    // ---- F48: Dynamic Scale debug timing gate (~2 min into a transition, never per light change) ----

    @Test
    fun dynamicScaleGate_firesOnlyTwoMinIntoTransitionThenThrottles() {
        val gate = DynamicScaleDebugGate(delayMs = 120_000L, intervalMs = 120_000L)

        // Transition in progress, but not yet 2 min in → silent.
        assertFalse(gate.shouldEmit(0L, transitionActive = true))
        assertFalse(gate.shouldEmit(60_000L, transitionActive = true))
        // 2 min into the transition → first Flash.
        assertTrue(gate.shouldEmit(120_000L, transitionActive = true))
        // Throttled: < 2 min since the last Flash.
        assertFalse(gate.shouldEmit(180_000L, transitionActive = true))
        // 2 min later → next Flash.
        assertTrue(gate.shouldEmit(240_000L, transitionActive = true))
    }

    @Test
    fun dynamicScaleGate_neverFiresWithoutAnActiveTransition() {
        val gate = DynamicScaleDebugGate()
        // "Not on every light change": when the scale isn't ramping the gate never opens, and a
        // settled stretch resets the 2-min clock so the next ramp must wait again.
        repeat(10) { i -> assertFalse(gate.shouldEmit(i * 200_000L, transitionActive = false)) }
        assertFalse(gate.shouldEmit(2_000_000L, transitionActive = true)) // first cycle of a new ramp
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
            // Each show cancels (hides) the previous flash first (F51); the trailing hide is the
            // explicit instant-off cancel (F52).
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
