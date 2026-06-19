package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.settingsDataStore

/**
 * The MANIFEST-layer screen receiver: it starts/stops the whole monitoring service on display
 * on/off when the service is enabled. This is a different job from the service's OWN internal
 * `screenReceiver` (registered in [AmbientMonitoringService]): the in-service receiver reacts to
 * on/off *within* a running pipeline (reinit/hibernate via `onScreenOn`/`onScreenOff`), whereas this
 * one is the cold-start/teardown trigger when no service instance exists. The two-layer split is
 * intentional and there is no double-fire — the service unregisters its receiver in `onDestroy`, and
 * `stopMonitoring`/`startMonitoring` are idempotent at the service level (S12.9e, deliverable #7).
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        // S12.9e: goAsync() keeps the broadcast alive until the enable check completes, on the
        // supervised AppProcessScope, instead of a detached per-call CoroutineScope.
        goAsync {
            val enabled = SettingsStore(context.settingsDataStore).readRawSettings().serviceEnabled
            if (!enabled) return@goAsync

            when (action) {
                Intent.ACTION_SCREEN_ON -> AutoBrightnessRuntime.startMonitoring(context, "screen_on")
                Intent.ACTION_SCREEN_OFF -> AutoBrightnessRuntime.stopMonitoring(context)
            }
        }
    }
}
