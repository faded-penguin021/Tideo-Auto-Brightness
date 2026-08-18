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
        // D-159: opt into edge-to-edge. Inset design assumes it; IME switches to inset-dispatch (API 31–34 parity).
        enableEdgeToEdge()
        // Ask for POST_NOTIFICATIONS up front (Android 13+). Full onboarding (WRITE_SETTINGS/ELEVATED) in S11; writes degrade gracefully.
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
