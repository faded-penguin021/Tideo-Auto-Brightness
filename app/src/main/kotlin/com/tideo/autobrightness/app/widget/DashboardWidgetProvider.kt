package com.tideo.autobrightness.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.MainActivity
import com.tideo.autobrightness.app.runtime.AppProcessScope
import com.tideo.autobrightness.app.runtime.LiveRuntimeState
import com.tideo.autobrightness.app.settings.SettingsStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.launch

/** Dashboard home-screen widget (RemoteViews; battery-efficient: no polling, refresh on pipeline cycles). */
class DashboardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    // NB (D-147): this receiver is exported (the system's APPWIDGET_UPDATE requires it), so it must
    // carry NO custom state-changing actions — any co-installed app can send it explicit intents.
    // The toggle/reset button actions live in the non-exported [WidgetActionReceiver].

    companion object {
        private const val REQ_OPEN = 0
        private const val REQ_TOGGLE = 1
        private const val REQ_RESET = 2

        private fun provider(context: Context) =
            ComponentName(context.applicationContext, DashboardWidgetProvider::class.java)

        fun hasInstances(context: Context): Boolean = runCatching {
            AppWidgetManager.getInstance(context.applicationContext)
                .getAppWidgetIds(provider(context)).isNotEmpty()
        }.getOrDefault(false)

        // Repaint all widgets; battery-efficient no-op when none placed.

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            // Battery: skip DataStore read if no widget placed.
            if (!hasInstances(appContext)) return
            AppProcessScope.launch {
                val enabled = runCatching {
                    SettingsStore(appContext.settingsDataStore).readRawSettings().serviceEnabled
                }.getOrDefault(false)
                pushUpdate(appContext, buildModel(enabled))
            }
        }

        internal fun buildModel(enabled: Boolean): WidgetModel {
            val p = LiveRuntimeState.pipeline.value
            return WidgetModel(
                enabled = enabled,
                running = LiveRuntimeState.serviceRunning.value,
                paused = p.paused,
                brightness = p.targetBrightness ?: p.lastAppliedBrightness,
                lux = p.smoothedLux,
                profile = LiveRuntimeState.activeProfile.value,
                context = LiveRuntimeState.activeContext.value,
            )
        }

        internal fun pushUpdate(context: Context, model: WidgetModel) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = runCatching { manager.getAppWidgetIds(provider(context)) }.getOrDefault(IntArray(0))
            if (ids.isEmpty()) return
            val views = renderViews(context, model)
            manager.updateAppWidget(ids, views)
        }

        private fun renderViews(context: Context, model: WidgetModel): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setTextViewText(R.id.widget_status, context.getString(statusLabelRes(model)))
            views.setTextViewText(R.id.widget_brightness, model.brightness?.toString() ?: DASH)
            views.setTextViewText(R.id.widget_lux, model.lux?.let { "%.0f".format(it) } ?: DASH)
            views.setTextViewText(R.id.widget_profile, model.profile ?: DASH)
            views.setTextViewText(R.id.widget_context, model.context ?: DASH)
            views.setTextViewText(
                R.id.widget_toggle,
                context.getString(if (model.enabled) R.string.widget_turn_off else R.string.widget_turn_on),
            )

            // Body → open the app; buttons → broadcast to the non-exported action receiver (D-147).
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, WidgetActionReceiver.ACTION_TOGGLE, REQ_TOGGLE))
            views.setOnClickPendingIntent(R.id.widget_reset, broadcast(context, WidgetActionReceiver.ACTION_RESET, REQ_RESET))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            // Explicit component + package (avoid implicit intent lint).
            val intent = Intent(context, MainActivity::class.java)
            intent.setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, REQ_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
            // Explicit component: a PendingIntent from this app may target its own NON-exported
            // receiver, which is exactly why the state-changing actions can live there (D-147).
            val intent = Intent(context, WidgetActionReceiver::class.java)
            intent.setPackage(context.packageName)
            intent.action = action
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun statusLabelRes(model: WidgetModel): Int = when {
            !model.enabled -> R.string.widget_status_off
            model.running && model.paused -> R.string.widget_status_paused
            model.running -> R.string.widget_status_active
            else -> R.string.widget_status_starting
        }

        private const val DASH = "—"
    }
}

data class WidgetModel(
    val enabled: Boolean,
    val running: Boolean,
    val paused: Boolean,
    val brightness: Int?,
    val lux: Double?,
    val profile: String?,
    val context: String?,
)
