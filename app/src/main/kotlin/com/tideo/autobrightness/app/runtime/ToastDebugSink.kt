package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Android [DebugSink]: shows a flash for a debug message only when the live `debugLevel` selects that
 * category (G2-F15). Flashes must post on the main thread; the pipeline emits from a background
 * coroutine, so each message is hopped onto the main looper. The category name is prefixed so the
 * tester can see which selector level produced the line during the Gate-2 walkthrough.
 *
 * S12.7e: routes through the shared [AabFlash] channel so each new flash cancels the previous instead
 * of stacking (G2R-F51) and renders system-wide when the opt-in Accessibility overlay is enabled
 * (G2R-F50). [AabFlash] owns the AAB-teal styling (G2R-F10).
 */
class ToastDebugSink(context: Context) : DebugSink {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) {
        if (category.level != activeLevel) return
        val text = "[${category.label}] ${message()}"
        mainHandler.post { AabFlash.show(appContext, text) }
    }

    private val DebugCategory.label: String
        get() = name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
}

/**
 * Android [ContextLoadSink] (S12.6e, G2R-F25): an unconditional user-visible flash when a context
 * rule loads its profile at runtime (Tasker flashes on a context load). Posts on the main looper
 * since the engine emits from a background coroutine; routes through [AabFlash] (S12.7e) so it shares
 * the cancel-previous + global-overlay behaviour with the debug flashes.
 */
class ToastContextLoadSink(context: Context) : ContextLoadSink {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onContextLoaded(contextName: String, profileName: String) {
        val text = "Context \"$contextName\" → profile \"$profileName\""
        mainHandler.post { AabFlash.show(appContext, text) }
    }
}
