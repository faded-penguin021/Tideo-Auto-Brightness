package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.widget.Toast

// Process-wide flash channel (S12.7e): cancel-previous + system-wide presenter (G2R-F50/F51/F52).
object AabFlash {

    /** A global flash surface — implemented by the AccessibilityService overlay. */
    interface Presenter {
        fun show(text: String)
        fun hide()
    }

    // S12.9e: @Volatile for visibility-only handoff; no compound invariant.
    @Volatile private var presenter: Presenter? = null
    // F88: foreground in-app tap-to-dismiss surface (AabFlashHost); lower priority than overlay.
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

    // Show text, cancel previous (G2R-F51). Priority: global → foreground → plain Toast (A11+ blocks custom-view from background).
    fun show(context: Context, text: String) {
        cancel()
        val p = presenter ?: foregroundPresenter
        if (p != null) {
            p.show(text)
        } else {
            val toast = Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT)
            lastToast = toast
            toast.show()
        }
    }

    fun cancel() {
        lastToast?.cancel()
        lastToast = null
        presenter?.hide()
        foregroundPresenter?.hide()
    }
}
