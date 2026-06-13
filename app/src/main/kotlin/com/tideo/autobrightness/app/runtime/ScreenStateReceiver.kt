package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CoroutineScope(Dispatchers.Default).launch {
            val enabled = SettingsStore(context.settingsDataStore).readRawSettings().serviceEnabled
            if (!enabled) return@launch

            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> AutoBrightnessRuntime.startMonitoring(context, "screen_on")
                Intent.ACTION_SCREEN_OFF -> AutoBrightnessRuntime.stopMonitoring(context)
            }
        }
    }
}
