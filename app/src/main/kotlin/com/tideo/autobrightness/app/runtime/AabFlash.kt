package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.widget.Toast

/**
 * Process-wide AAB "flash" channel (S12.7e). Every runtime flash — the debug-category toasts
 * (G2-F15) and the context-load toast (G2R-F25) — funnels through here so that:
 *
 *  - **Each new flash CANCELS the previous one** instead of stacking (G2R-F51): the prior toast is
 *    cancelled before the next is posted, so disabling a category does not leave a queue draining for
 *    seconds afterwards.
 *  - **An opt-in [Presenter]** (the [AabToastAccessibilityService] overlay) renders flashes
 *    **system-wide** instead of only while the app is in the foreground (G2R-F50). When no presenter
 *    is registered it degrades gracefully to a foreground [Toast].
 *  - **[cancel] clears the current flash immediately** — used for instant debug-off (G2R-F52).
 *
 * Tasker showed a single flash at a time (a Flash action replaces the previous), so a single shared
 * channel with cancel-previous is the faithful model. All UI work must run on the main thread; the
 * sinks post here from the main looper and the AccessibilityService callbacks are already on main.
 */
object AabFlash {

    /** A global flash surface — implemented by the AccessibilityService overlay. */
    interface Presenter {
        fun show(text: String)
        fun hide()
    }

    // S12.9e volatile audit: both presenter references are @Volatile because they are ASSIGNED from the
    // AccessibilityService connect/unbind + the UI host's composition (main thread) and READ from the
    // runtime sinks that call show()/cancel() — a single-reference handoff with no compound invariant,
    // so visibility-only volatility (not a lock) is the correct, minimal guarantee.
    @Volatile private var presenter: Presenter? = null
    // F88: an in-app, tap-to-dismiss surface registered by the foreground UI ([AabFlashHost]). Used
    // when the global Accessibility overlay is NOT enabled, so in-app flashes ("Applied", debug toasts
    // while the app is foreground) are still dismissible by tap — a plain Toast is not tappable.
    @Volatile private var foregroundPresenter: Presenter? = null
    private var lastToast: Toast? = null

    /**
     * Register (or clear, with `null`) the global presenter. Called by [AabToastAccessibilityService]
     * on connect/unbind. Switching surfaces drops anything currently showing on the old one.
     */
    fun register(presenter: Presenter?) {
        cancel()
        this.presenter = presenter
    }

    /**
     * Register (or clear) the foreground in-app presenter ([AabFlashHost]); lower priority than the
     * global overlay. Cleared when the host leaves composition (app backgrounded). (F88)
     */
    fun registerForeground(presenter: Presenter?) {
        if (presenter == null) foregroundPresenter?.hide()
        this.foregroundPresenter = presenter
    }

    /** True when a global presenter (the Accessibility overlay) is active. */
    fun isGlobal(): Boolean = presenter != null

    /**
     * Show [text], cancelling any in-flight flash first (G2R-F51). Surface priority: the global
     * Accessibility overlay → the foreground in-app surface → a plain Toast. The first two are
     * tap-to-dismiss AND show system-wide / styled. The Toast fallback is used only when neither is
     * available — which is precisely the BACKGROUND case (e.g. the user pulled the shade to grab the
     * brightness slider). Android 11+ silently blocks **custom-view** toasts (`Toast.setView`) from a
     * background app, so the fallback MUST be a plain text toast or the manual-override flash vanishes.
     */
    fun show(context: Context, text: String) {
        cancel()
        val p = presenter ?: foregroundPresenter
        if (p != null) {
            p.show(text)
        } else {
            // Plain text toast only — NO setView (blocked from the background on Android 11+).
            val toast = Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT)
            lastToast = toast
            toast.show()
        }
    }

    /** Cancel the current flash immediately (instant debug-off, G2R-F52). */
    fun cancel() {
        lastToast?.cancel()
        lastToast = null
        presenter?.hide()
        foregroundPresenter?.hide()
    }
}
