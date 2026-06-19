package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.settingsDataStore

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // S12.9e: goAsync() keeps the broadcast alive until the DataStore read + start finish (the old
        // detached CoroutineScope.launch could be killed mid-read), on the supervised AppProcessScope.
        goAsync {
            val enabled = SettingsStore(context.settingsDataStore).readRawSettings().serviceEnabled
            AutoBrightnessRuntime.scheduleMaintenance(context)
            if (enabled) {
                AutoBrightnessRuntime.startMonitoring(context, "boot_completed")
            }
        }
    }
}
