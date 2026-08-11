package com.tideo.autobrightness.app.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tideo.autobrightness.app.AppModule
import com.tideo.autobrightness.app.runtime.AutoBrightnessRuntime
import com.tideo.autobrightness.app.runtime.goAsync
import com.tideo.autobrightness.app.settings.ProfileApplier
import com.tideo.autobrightness.app.storage.controlPrefsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.widget.DashboardWidgetProvider
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/** D-157: Exported external-control surface (Tasker/MacroDroid). Opt-in gate [ControlPrefsStore.externalControlEnabled].
 * D-147/D-160: safe re-opening; D-155: PANIC always executes. Per-verb semantics while service not running.
 * FGS caveat: API 31+ may throw ForegroundServiceStartNotAllowedException; caught and marked degraded. */
class ControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // DA-043: reject unknown verbs before admission gate.
        if (action !in KNOWN_ACTIONS) return
        // DA-039: serialize commands globally (resource bound, not auth).
        if (!commandInFlight.compareAndSet(false, true)) return
        val profileName = runCatching { intent.getStringExtra(EXTRA_PROFILE_NAME) }.getOrNull()
        try {
            goAsync {
                try {
                    handle(context.applicationContext, action, profileName)
                } finally {
                    commandInFlight.set(false)
                }
            }
        } catch (failure: Throwable) {
            commandInFlight.set(false)
            throw failure
        }
    }

    /** D-157 security: gate check first, drop all when disabled. */
    internal suspend fun handle(appContext: Context, action: String, profileName: String? = null) {
        val enabled = ControlPrefsStore(appContext.controlPrefsDataStore).externalControlEnabled.first()
        if (!enabled) return
        route(appContext, action, profileName)
    }

    internal suspend fun route(appContext: Context, action: String, profileName: String? = null) {
        when (action) {
            ACTION_SERVICE_ON -> setServiceEnabled(appContext) { true }
            ACTION_SERVICE_OFF -> setServiceEnabled(appContext) { false }
            ACTION_SERVICE_TOGGLE -> setServiceEnabled(appContext) { current -> !current }
            ACTION_PAUSE -> AutoBrightnessRuntime.pause(appContext)
            // D-160: RESUME gated on serviceEnabled (D-140 zombie class).
            ACTION_RESUME ->
                if (appContext.settingsDataStore.data.first().serviceEnabled) {
                    AutoBrightnessRuntime.resume(appContext)
                }
            ACTION_REAPPLY -> AutoBrightnessRuntime.reapply(appContext)
            ACTION_PANIC -> AutoBrightnessRuntime.panic(appContext)
            // D-157 U3: LOAD_PROFILE latches context lock; CONTEXTS_RESUME clears it.
            ACTION_LOAD_PROFILE -> profileName?.let { profileApplier(appContext).applyProfile(it) }
            ACTION_CONTEXTS_RESUME -> profileApplier(appContext).resumeContextAutomation()
            else -> Unit
        }
    }

    /** The shared VM-free profile logic (built off the same [AppModule.userProfileStore] the UI uses). */
    private fun profileApplier(appContext: Context) =
        ProfileApplier(appContext, AppModule(appContext).userProfileStore)

    /** D-147: toggle dance (ON=true, OFF=false, TOGGLE=flip). Tile/widget keep their own copies. */
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
        const val ACTION_LOAD_PROFILE = "$NS.LOAD_PROFILE"
        const val ACTION_CONTEXTS_RESUME = "$NS.CONTEXTS_RESUME"
        const val EXTRA_PROFILE_NAME = "name"

        internal val KNOWN_ACTIONS = setOf(
            ACTION_SERVICE_ON,
            ACTION_SERVICE_OFF,
            ACTION_SERVICE_TOGGLE,
            ACTION_PAUSE,
            ACTION_RESUME,
            ACTION_REAPPLY,
            ACTION_PANIC,
            ACTION_LOAD_PROFILE,
            ACTION_CONTEXTS_RESUME,
        )

        private val commandInFlight = AtomicBoolean(false)

        internal fun tryAcquireCommand(): Boolean = commandInFlight.compareAndSet(false, true)
        internal fun releaseCommand() = commandInFlight.set(false)
    }
}
