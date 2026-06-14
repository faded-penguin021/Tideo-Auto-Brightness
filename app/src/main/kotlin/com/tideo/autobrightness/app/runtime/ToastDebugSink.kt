package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Android [DebugSink]: shows a Toast for a debug message only when the live `debugLevel` selects that
 * category (G2-F15). Toasts must post on the main thread; the pipeline emits from a background
 * coroutine, so each message is hopped onto the main looper. The category name is prefixed so the
 * tester can see which selector level produced the line during the Gate-2 walkthrough.
 */
class ToastDebugSink(context: Context) : DebugSink {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) {
        if (category.level != activeLevel) return
        val text = "[${category.label}] ${message()}"
        mainHandler.post { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
    }

    private val DebugCategory.label: String
        get() = name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
}
