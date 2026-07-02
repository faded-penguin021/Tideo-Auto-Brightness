package com.tideo.autobrightness.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.goAsync
import com.tideo.autobrightness.app.storage.settingsDataStore

/**
 * The widget buttons' broadcast target — a separate, NOT-exported receiver (D-147).
 *
 * [DashboardWidgetProvider] must stay exported for the system's APPWIDGET_UPDATE broadcasts, so any
 * co-installed app could send it explicit intents; hosting the state-changing button actions there let
 * a third-party app toggle the service or force a reapply without any permission. The buttons'
 * PendingIntents are created by this app and a PendingIntent may target a non-exported component of
 * its own package, so the actions live here — unreachable from other apps.
 *
 * **toggle** flips `serviceEnabled` and starts/stops the runtime (mirrors the QS tile, task551);
 * **reset** re-applies the pipeline to snap brightness back to the computed value (clearing a manual
 * override pause; no-op service-side when not running, D-140).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> goAsync { toggle(context.applicationContext) }
            ACTION_RESET -> {
                AutoBrightnessRuntime.reapply(context.applicationContext)
                DashboardWidgetProvider.refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.tideo.autobrightness.widget.action.TOGGLE"
        const val ACTION_RESET = "com.tideo.autobrightness.widget.action.RESET"

        /** Flip `serviceEnabled`, start/stop the runtime, repaint the widget (mirrors the QS tile). */
        internal suspend fun toggle(appContext: Context) {
            val newEnabled = appContext.settingsDataStore.updateData {
                it.copy(serviceEnabled = !it.serviceEnabled)
            }.serviceEnabled
            AutoBrightnessRuntime.onSettingChanged(appContext, newEnabled)
            DashboardWidgetProvider.pushUpdate(appContext, DashboardWidgetProvider.buildModel(newEnabled))
        }
    }
}
