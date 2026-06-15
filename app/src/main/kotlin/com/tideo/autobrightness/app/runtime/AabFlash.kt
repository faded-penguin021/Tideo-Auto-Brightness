package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
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

    @Volatile private var presenter: Presenter? = null
    private var lastToast: Toast? = null

    /**
     * Register (or clear, with `null`) the global presenter. Called by [AabToastAccessibilityService]
     * on connect/unbind. Switching surfaces drops anything currently showing on the old one.
     */
    fun register(presenter: Presenter?) {
        cancel()
        this.presenter = presenter
    }

    /** True when a global presenter (the Accessibility overlay) is active. */
    fun isGlobal(): Boolean = presenter != null

    /** Show [text], cancelling any in-flight flash first (G2R-F51). */
    fun show(context: Context, text: String) {
        cancel()
        val p = presenter
        if (p != null) {
            p.show(text)
        } else {
            val toast = tealToast(context.applicationContext, text)
            lastToast = toast
            toast.show()
        }
    }

    /** Cancel the current flash immediately (instant debug-off, G2R-F52). */
    fun cancel() {
        lastToast?.cancel()
        lastToast = null
        presenter?.hide()
    }

    /**
     * Build the AAB-teal fallback toast. System toast text cannot be recoloured reliably, so a custom
     * teal-rounded [TextView] is inflated; built via [Toast.makeText] first so the message is still
     * recorded (Robolectric ShadowToast + a11y) before the custom view is attached (G2R-F10).
     */
    @Suppress("DEPRECATION") // Toast.setView: deprecated for background toasts, fine for foreground app toasts.
    private fun tealToast(appContext: Context, text: String): Toast {
        val view = TextView(appContext).apply {
            this.text = text
            setTextColor(Color.WHITE)
            val pad = dp(appContext, 14)
            setPadding(pad, dp(appContext, 10), pad, dp(appContext, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(appContext, 12).toFloat()
                setColor(AAB_TEAL)
            }
        }
        return Toast.makeText(appContext, text, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(appContext, 80))
            setView(view)
        }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics,
    ).toInt()

    // AabTeal #007C63 (ui.theme.Color) as an android.graphics ARGB int.
    private const val AAB_TEAL = 0xFF007C63.toInt()
}
