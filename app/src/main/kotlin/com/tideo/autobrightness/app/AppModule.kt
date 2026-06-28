package com.tideo.autobrightness.app

import android.content.Context
import com.tideo.autobrightness.app.runtime.AndroidContextSignalSource
import com.tideo.autobrightness.app.runtime.AppProfileCatalog
import com.tideo.autobrightness.app.runtime.BrightnessPipelineController
import com.tideo.autobrightness.app.runtime.ContextEngine
import com.tideo.autobrightness.app.runtime.ControllerHookHolder
import com.tideo.autobrightness.app.runtime.DebugSink
import com.tideo.autobrightness.app.runtime.SuperDimmingCoordinator
import com.tideo.autobrightness.app.runtime.ToastContextLoadSink
import com.tideo.autobrightness.app.runtime.ToastDebugSink
import com.tideo.autobrightness.app.runtime.CircadianWindowProvider
import com.tideo.autobrightness.app.settings.ContextRuleStore
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.settings.OverridePointStore
import com.tideo.autobrightness.app.settings.UserProfileStore
import com.tideo.autobrightness.app.storage.contextRulesDataStore
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
import com.tideo.autobrightness.app.storage.overridePointsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.storage.userProfilesDataStore
import com.tideo.autobrightness.platform.brightness.AndroidScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.AndroidSecureDimmingController
import com.tideo.autobrightness.platform.context.AndroidLocationReader
import com.tideo.autobrightness.platform.context.GeoIpLocationClient
import com.tideo.autobrightness.platform.observe.AndroidBrightnessObserver
import com.tideo.autobrightness.platform.privilege.AndroidPrivilegeManager
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.sensor.AndroidLightSensorSource
import com.tideo.autobrightness.platform.sensor.AndroidPanicSensorSource
import com.tideo.autobrightness.platform.sensor.AndroidProximitySensorSource
import com.tideo.autobrightness.platform.sensor.PanicSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Manual DI composition root for the runtime graph (rebuilt in S9b; extended in S10 with the
 * context-override engine). Composes the real S7 platform adapters + S9a pipeline + S9b super-dimming
 * layer + S10 [ContextEngine]. Replace with Hilt/Koin later without touching domain contracts.
 */
class AppModule(context: Context) {
    private val appContext = context.applicationContext

    val privilegeManager: PrivilegeManager = AndroidPrivilegeManager(appContext)

    /** Persistence + CRUD for context rules (exposed for the S12 rule-editing UI). */
    val contextRuleStore: ContextRuleStore = ContextRuleStore(appContext.contextRulesDataStore)

    /** Recorded manual-override training points — captured by the pipeline, read by the wizard +
     *  curve overlay (G2R-F13/F14). */
    val overridePointStore: OverridePointStore = OverridePointStore(appContext.overridePointsDataStore)

    /** User-editable named profiles (S12.6d, G2R-F15): the Profiles screen + the context catalog. */
    val userProfileStore: UserProfileStore = UserProfileStore(appContext.userProfilesDataStore)

    /**
     * Build a fresh runtime graph for a service lifetime. The brightness writer and observer SHARE
     * one instance so the suppress-echo marker is per-instance (D-034). The pipeline reads its
     * settings through the [ContextEngine] so an active context override swaps the whole profile.
     */
    fun createRuntime(scope: CoroutineScope): RuntimeGraph {
        val brightness = AndroidScreenBrightnessController(appContext)
        // Runtime debug Flashes (the 10 %AAB_Debug categories, D-023/G2-F15) shown only when the
        // matching debugLevel is selected; shared by the pipeline, dimming + context engine.
        val debugSink: DebugSink = ToastDebugSink(appContext)

        // S12.9e: late-bound hook instead of `lateinit var controller` — the engine is constructed
        // before the controller (the controller's settingsProvider reads through it), so the engine
        // calls the controller through this holder, whose hook is assigned right after construction.
        val controllerHook = ControllerHookHolder()
        val contextEngine = ContextEngine(
            rulesProvider = { contextRuleStore.rules() },
            // React to rule add/edit/delete at runtime (a new app/location rule starts its listener
            // immediately, not only at next screen-on/reboot).
            rulesFlow = contextRuleStore.rulesFlow(),
            baselineProvider = { appContext.settingsDataStore.data.first() },
            profileCatalog = AppProfileCatalog(userProfileStore),
            signalSource = AndroidContextSignalSource(appContext),
            onProfileChanged = { controllerHook.fire() },
            debugSink = debugSink,
            // G2R-F25: toast on a runtime context-rule profile load (unconditional, not debug-gated).
            contextLoadSink = ToastContextLoadSink(appContext),
        )

        // F73: real solar ramp windows for the dynamic-scale engine (today + last-known location, or
        // the F39 fixed date/location override), so %AAB_ScaleDynamic tracks the actual sunrise.
        val experimentPrefs = ExperimentPrefsStore(appContext.experimentPrefsDataStore)
        val geoIpClient = GeoIpLocationClient()
        val circadianWindows = CircadianWindowProvider(
            scope = scope,
            overrideFlow = experimentPrefs.dateLocation,
            location = AndroidLocationReader(appContext),
            // F83: ip-api.com geo-IP fallback when no Android fix is available (task90 act28). G3-F12 /
            // D-105 (privacy): gated on the user's opt-IN (default off) — the app contacts ip-api.com
            // only when the user has explicitly enabled the fallback.
            geoIpFallback = { if (experimentPrefs.geoIpEnabled.first()) geoIpClient.resolve() else null },
            // D-103: persist/restore the once-a-day resolved location so a cold start reuses it
            // instead of falling back to TimeContext defaults until re-acquired (Tasker %AAB_SunLat/…).
            loadCachedLocation = { experimentPrefs.readCachedSunLocation() },
            persistLocation = { lat, lon, day -> experimentPrefs.writeCachedSunLocation(lat, lon, day) },
        )

        val controller = BrightnessPipelineController(
            lightSensor = AndroidLightSensorSource(appContext),
            brightness = brightness,
            brightnessObserver = AndroidBrightnessObserver(appContext, brightness),
            // Effective settings = active context profile merged over the baseline, or the baseline.
            settingsProvider = { contextEngine.effectiveSettings() },
            scope = scope,
            circadianWindowsProvider = circadianWindows::current,
            dimming = SuperDimmingCoordinator(
                secureDimming = AndroidSecureDimmingController(appContext, privilegeManager),
                // Read the CACHED tier (no IPC) each cycle. The previous `refresh()`-per-cycle ran
                // checkSelfPermission + Settings.System.canWrite (two Binder calls) on every dimming
                // evaluation. The cache is refreshed at the resume points instead (service start /
                // screen-on, AmbientMonitoringService) and after an in-app grant (PrivilegeManager),
                // so a post-start ADB/Shizuku grant is still picked up on the next wake (G1-F5 intent
                // preserved, the per-cycle permission check dropped).
                tierProvider = { privilegeManager.currentTier() },
                debugSink = debugSink,
            ),
            debugSink = debugSink,
            // Persist captured override points so the wizard + curve overlay have real input (G2R-F13).
            overrideSink = { lux, brightness -> overridePointStore.record(lux, brightness) },
            // prof759/task545: proximity-near damps the smoothing alpha ×0.1 (never pauses).
            proximitySource = AndroidProximitySensorSource(appContext),
        )
        // Wire the late-bound hook now that the controller exists (replaces the lateinit cycle).
        controllerHook.hook = controller
        // D-110: when the circadian location resolves late (cache seed / async geo-IP or GPS fix),
        // recompute brightness so the modifier updates even in steady light (where prof760 drops cycles
        // and the initial brightness was set with the default windows). Wired post-construction because
        // the provider feeds `current` into the controller above.
        circadianWindows.onWindowsRefreshed = { controller.reapply() }

        // prof769/task528 panic: upside-down + shake (display on) → SOS + restore + full stop (F77).
        val panicSensor = AndroidPanicSensorSource(appContext)

        return RuntimeGraph(controller, contextEngine, panicSensor, privilegeManager)
    }
}

/**
 * The composed runtime: the brightness pipeline + the context engine that feeds it + the panic
 * gesture source. The service drives both lifecycles together and reads [activeContext] for the
 * notification's context line; [panicSensor] fires the task528 panic on the prof769 gesture.
 */
class RuntimeGraph(
    val controller: BrightnessPipelineController,
    val contextEngine: ContextEngine,
    val panicSensor: PanicSensorSource,
    /** Shared tier source. The service [refresh][PrivilegeManager.refresh]es it at resume points so a
     *  post-start ADB/Shizuku grant is seen without re-checking the permission on every dimming cycle. */
    val privilegeManager: PrivilegeManager,
) {
    val activeContext: StateFlow<String?> = contextEngine.activeContext
}
