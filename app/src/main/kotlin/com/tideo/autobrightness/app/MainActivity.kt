package com.tideo.autobrightness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.ui.AutoBrightnessApp

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* visibility only */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // D-159: opt into edge-to-edge explicitly (WindowCompat.setDecorFitsSystemWindows(false)). The
        // whole inset design already assumes it — every screen consumes system-bar insets via M3
        // Scaffold / statusBarsPadding / navigationBarsPadding, and the sticky Apply bar lifts over the
        // keyboard with imePadding(). Without this call, targetSdk-36 on Android 15+ still draws the
        // window edge-to-edge but leaves the IME in the legacy ADJUST_RESIZE mode, so a focused field
        // BOTH shrinks the window AND triggers imePadding() → the Apply bar is lifted twice (a
        // keyboard-tall empty gap). enableEdgeToEdge() switches the IME to inset-dispatch so imePadding()
        // is the single source of truth, and makes the behavior identical on API 31–34.
        enableEdgeToEdge()
        // Ask for POST_NOTIFICATIONS up front (Android 13+) so the foreground-service notification is
        // visible. Full onboarding (WRITE_SETTINGS / ELEVATED) lands in S11; the runtime no longer
        // crashes when those are missing (G1-F1 — writes degrade gracefully).
        maybeRequestNotificationPermission()
        AutoBrightnessRuntime.bootstrap(this)
        setContent {
            AutoBrightnessApp()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
