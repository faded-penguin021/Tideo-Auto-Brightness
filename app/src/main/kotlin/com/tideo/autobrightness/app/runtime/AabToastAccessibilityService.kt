package com.tideo.autobrightness.app.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

/**
 * Opt-in [AccessibilityService] that renders the AAB flash messages **system-wide** (G2R-F50).
 *
 * The Tasker original flashed its debug/context messages everywhere — over other apps, not just while
 * the AAB UI was foreground. Android blocks custom toast views from the background on API 30+, so a
 * foreground-only [android.widget.Toast] (the [AabFlash] fallback) cannot reproduce that. An
 * AccessibilityService can host a `TYPE_ACCESSIBILITY_OVERLAY` window that draws above other apps
 * without `SYSTEM_ALERT_WINDOW`, which is the mechanism here.
 *
 * The service is **purely a presentation surface**: it consumes no accessibility events and never
 * reads window content (`canRetrieveWindowContent=false`). It registers itself as the [AabFlash]
 * [AabFlash.Presenter] on connect and unregisters on unbind, so when the user has not enabled it the
 * app degrades gracefully to the foreground toast. Distribution is F-Droid/GitHub (not Play Store),
 * so the sensitive permission is acceptable per the owner answer (STATE.md, F50).
 */
class AabToastAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private val hideRunnable = Runnable { removeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        AabFlash.register(object : AabFlash.Presenter {
            override fun show(text: String) { handler.post { showOverlay(text) } }
            override fun hide() { handler.post { removeOverlay() } }
        })
    }

    private fun showOverlay(text: String) {
        val wm = windowManager ?: return
        removeOverlay()
        val view = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            val pad = dp(14)
            setPadding(pad, dp(10), pad, dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(AAB_TEAL)
            }
            // F88: tap the flash to dismiss it immediately (Tasker lets you tap a flash away).
            setOnClickListener { removeOverlay() }
        }
        // NOTE (F88): the window is focusable-NOT but touchABLE so the tap-to-dismiss click lands; it
        // is small + bottom-anchored, so it does not steal general input.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(80)
        }
        runCatching { wm.addView(view, params) }
            .onSuccess {
                overlayView = view
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, OVERLAY_DURATION_MS)
            }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideRunnable)
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = removeOverlay()

    override fun onUnbind(intent: Intent?): Boolean {
        AabFlash.register(null)
        removeOverlay()
        return super.onUnbind(intent)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
    ).toInt()

    private companion object {
        const val OVERLAY_DURATION_MS = 2_500L
        const val AAB_TEAL = 0xFF007C63.toInt()
    }
}
