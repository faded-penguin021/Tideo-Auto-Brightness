package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.Default).launch {
            val enabled = SettingsStore(context.settingsDataStore).readSettings().enabled
            AutoBrightnessRuntime.scheduleMaintenance(context)
            if (enabled) {
                AutoBrightnessRuntime.startMonitoring(context, "boot_completed")
            }
        }
    }
}
