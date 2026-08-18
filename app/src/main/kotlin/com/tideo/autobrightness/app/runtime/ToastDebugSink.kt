package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Android DebugSink (G2-F15): flashes debug message per category via AabFlash (S12.7e, G2R-F51/F50).
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
 * Android ContextLoadSink (S12.6e, G2R-F25): flash on context profile load via AabFlash.
 */
class ToastContextLoadSink(context: Context) : ContextLoadSink {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onContextLoaded(contextName: String, profileName: String) {
        val text = "Context \"$contextName\" → profile \"$profileName\""
        mainHandler.post { AabFlash.show(appContext, text) }
    }
}
