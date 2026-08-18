package com.tideo.autobrightness.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.goAsync
import com.tideo.autobrightness.app.storage.settingsDataStore

/** Widget buttons' broadcast target (NOT-exported; D-147, D-140). */
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

        internal suspend fun toggle(appContext: Context) {
            val newEnabled = appContext.settingsDataStore.updateData {
                it.copy(serviceEnabled = !it.serviceEnabled)
            }.serviceEnabled
            AutoBrightnessRuntime.onSettingChanged(appContext, newEnabled)
            DashboardWidgetProvider.pushUpdate(appContext, DashboardWidgetProvider.buildModel(newEnabled))
        }
    }
}
