package com.tideo.autobrightness.app.runtime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Android [DebugSink]: shows a Toast for a debug message only when the live `debugLevel` selects that
 * category (G2-F15). Toasts must post on the main thread; the pipeline emits from a background
 * coroutine, so each message is hopped onto the main looper. The category name is prefixed so the
 * tester can see which selector level produced the line during the Gate-2 walkthrough.
 *
 * S12.6b (G2R-F10): the toast uses the **AAB teal** brand colour. System toast text cannot be
 * recoloured reliably, so a custom teal-rounded [TextView] is inflated for the toast — the app shows
 * these while in the foreground during the debug walkthrough, where custom toast views still render.
 */
class ToastDebugSink(context: Context) : DebugSink {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun emit(category: DebugCategory, activeLevel: Int, message: () -> String) {
        if (category.level != activeLevel) return
        val text = "[${category.label}] ${message()}"
        mainHandler.post { tealToast(text).show() }
    }

    @Suppress("DEPRECATION") // Toast.setView: deprecated for background toasts, fine for foreground app toasts.
    private fun tealToast(text: String): Toast {
        val view = TextView(appContext).apply {
            this.text = text
            setTextColor(Color.WHITE)
            val pad = dp(14)
            setPadding(pad, dp(10), pad, dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(AAB_TEAL)
            }
        }
        return Toast(appContext).apply {
            duration = Toast.LENGTH_SHORT
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(80))
            @Suppress("DEPRECATION")
            setView(view)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), appContext.resources.displayMetrics,
    ).toInt()

    private val DebugCategory.label: String
        get() = name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }

    private companion object {
        // AabTeal #007C63 (ui.theme.Color) as an android.graphics ARGB int.
        const val AAB_TEAL = 0xFF007C63.toInt()
    }
}
