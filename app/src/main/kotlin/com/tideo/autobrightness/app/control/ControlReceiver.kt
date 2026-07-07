package com.tideo.autobrightness.app.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.goAsync
import com.tideo.autobrightness.app.storage.controlPrefsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.widget.DashboardWidgetProvider
import kotlinx.coroutines.flow.first

/**
 * D-157: the **exported** external-control surface for automation frameworks (Tasker / MacroDroid).
 *
 * Deliberately re-opens the class of surface D-147 closed (a third-party app can send these
 * broadcasts), but made safe by an **opt-in runtime gate**: [ControlPrefsStore.externalControlEnabled]
 * defaults OFF and is the receiver's **FIRST** check — while off, every action is ignored (the
 * OFF-ignores-everything property, pinned by a D-147-style negative test). There is no shared secret:
 * the exposed verbs are exactly what the notification / QS tile / widget already give the user, and no
 * data leaves the app, so a token would be pure friction in the Tasker/MacroDroid UIs (plan decision 1).
 *
 * Every verb maps onto an already-hardened path — the service-side D-140 zombie gates already handle
 * "sent while not running", so all verbs except [ACTION_SERVICE_ON] are safe no-ops then.
 *
 * Platform caveat: [ACTION_SERVICE_ON] arriving while the app is background-restricted may throw
 * `ForegroundServiceStartNotAllowedException` (API 31+ FGS launch rules); [AutoBrightnessRuntime.startMonitoring]
 * catches it and marks the service degraded (surfaced on the Dashboard). Users exempt Tideo from
 * battery optimization for reliable external enable (in-app help text, U4).
 */
class ControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // goAsync: the gate read is a DataStore lookup; keep the broadcast alive off the main thread.
        goAsync { handle(context.applicationContext, action) }
    }

    /**
     * The gate is the FIRST check (D-157 security property): read the opt-in flag and drop everything
     * when it is off, BEFORE any verb touches settings or the service.
     */
    internal suspend fun handle(appContext: Context, action: String) {
        val enabled = ControlPrefsStore(appContext.controlPrefsDataStore).externalControlEnabled.first()
        if (!enabled) return
        route(appContext, action)
    }

    /** Dispatch a verb onto its existing, already-hardened runtime path. Unknown actions are ignored. */
    internal suspend fun route(appContext: Context, action: String) {
        when (action) {
            ACTION_SERVICE_ON -> setServiceEnabled(appContext) { true }
            ACTION_SERVICE_OFF -> setServiceEnabled(appContext) { false }
            ACTION_SERVICE_TOGGLE -> setServiceEnabled(appContext) { current -> !current }
            ACTION_PAUSE -> AutoBrightnessRuntime.pause(appContext)
            ACTION_RESUME -> AutoBrightnessRuntime.resume(appContext)
            ACTION_REAPPLY -> AutoBrightnessRuntime.reapply(appContext)
            ACTION_PANIC -> AutoBrightnessRuntime.panic(appContext)
            else -> Unit // unknown action ignored (U3 adds LOAD_PROFILE / CONTEXTS_RESUME)
        }
    }

    /**
     * Set `serviceEnabled` to `target(current)`, drive the runtime and repaint the widget — the
     * `WidgetActionReceiver.toggle` shipped dance (D-147), parameterized by the target-state function
     * (ON forces true, OFF forces false, TOGGLE flips). Copied, not shared: the tile/widget keep their
     * own working code untouched (plan Design table).
     */
    private suspend fun setServiceEnabled(appContext: Context, target: (Boolean) -> Boolean) {
        val newEnabled = appContext.settingsDataStore.updateData {
            it.copy(serviceEnabled = target(it.serviceEnabled))
        }.serviceEnabled
        AutoBrightnessRuntime.onSettingChanged(appContext, newEnabled)
        DashboardWidgetProvider.pushUpdate(appContext, DashboardWidgetProvider.buildModel(newEnabled))
    }

    companion object {
        private const val NS = "com.tideo.autobrightness.control"
        const val ACTION_SERVICE_ON = "$NS.SERVICE_ON"
        const val ACTION_SERVICE_OFF = "$NS.SERVICE_OFF"
        const val ACTION_SERVICE_TOGGLE = "$NS.SERVICE_TOGGLE"
        const val ACTION_PAUSE = "$NS.PAUSE"
        const val ACTION_RESUME = "$NS.RESUME"
        const val ACTION_REAPPLY = "$NS.REAPPLY"
        const val ACTION_PANIC = "$NS.PANIC"
    }
}
