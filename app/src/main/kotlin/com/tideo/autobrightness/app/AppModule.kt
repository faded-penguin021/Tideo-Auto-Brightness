package com.tideo.autobrightness.app

import android.content.Context
import com.tideo.autobrightness.app.runtime.AndroidContextSignalSource
import com.tideo.autobrightness.app.runtime.AppProfileCatalog
import com.tideo.autobrightness.app.runtime.BrightnessPipelineController
import com.tideo.autobrightness.app.runtime.ContextEngine
import com.tideo.autobrightness.app.runtime.ControllerHookHolder
import com.tideo.autobrightness.app.runtime.DebugSink
import com.tideo.autobrightness.app.runtime.DisplayTogglesCoordinator
import com.tideo.autobrightness.app.runtime.SuperDimmingCoordinator
import com.tideo.autobrightness.app.runtime.ToastContextLoadSink
import com.tideo.autobrightness.app.runtime.ToastDebugSink
import com.tideo.autobrightness.app.runtime.CircadianWindowProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.ContextRuleStore
import com.tideo.autobrightness.app.settings.DataStoreContextBaselineStore
import com.tideo.autobrightness.app.settings.ExperimentPrefsStore
import com.tideo.autobrightness.app.settings.OverridePointStore
import com.tideo.autobrightness.app.settings.UserProfileStore
import com.tideo.autobrightness.app.storage.contextBaselineDataStore
import com.tideo.autobrightness.app.storage.contextRulesDataStore
import com.tideo.autobrightness.app.storage.experimentPrefsDataStore
import com.tideo.autobrightness.app.storage.overridePointsDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.app.storage.userProfilesDataStore
import com.tideo.autobrightness.domain.brightness.TimeContext
import com.tideo.autobrightness.domain.circadian.DynamicScaleEngine
import com.tideo.autobrightness.domain.circadian.DynamicScaleInput
import com.tideo.autobrightness.domain.circadian.NightLightTemperatureRamp
import com.tideo.autobrightness.platform.brightness.AndroidScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.AndroidSecureDimmingController
import com.tideo.autobrightness.platform.context.AndroidLocationReader
import com.tideo.autobrightness.platform.context.GeoIpLocationClient
import com.tideo.autobrightness.platform.display.AndroidSecureDisplayController
import com.tideo.autobrightness.platform.display.SecureDisplayController
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

/** Manual DI composition root for the runtime graph. */
class AppModule(context: Context) {
    private val appContext = context.applicationContext

    val privilegeManager: PrivilegeManager = AndroidPrivilegeManager(appContext)

    val contextRuleStore: ContextRuleStore = ContextRuleStore(appContext.contextRulesDataStore)
    // Recorded override points (G2R-F13/F14).
    val overridePointStore: OverridePointStore = OverridePointStore(appContext.overridePointsDataStore)
    val userProfileStore: UserProfileStore = UserProfileStore(appContext.userProfilesDataStore)

    fun createRuntime(scope: CoroutineScope): RuntimeGraph {
        val brightness = AndroidScreenBrightnessController(appContext)
        val debugSink: DebugSink = ToastDebugSink(appContext)
        // Late-bound hook (engine constructed before controller).
        val controllerHook = ControllerHookHolder()
        val contextEngine = ContextEngine(
            rulesProvider = { contextRuleStore.rules() },
            rulesFlow = contextRuleStore.rulesFlow(),
            settingsProvider = { appContext.settingsDataStore.data.first() },
            // D-170: context load writes through to live settings store; baseline snapshotted and restored.
            settingsWriter = { transform -> appContext.settingsDataStore.updateData(transform) },
            baselineStore = DataStoreContextBaselineStore(appContext.contextBaselineDataStore),
            profileCatalog = AppProfileCatalog(userProfileStore),
            signalSource = AndroidContextSignalSource(appContext),
            onProfileChanged = { controllerHook.fire() },
            debugSink = debugSink,
            contextLoadSink = ToastContextLoadSink(appContext),
        )

        val experimentPrefs = ExperimentPrefsStore(appContext.experimentPrefsDataStore)
        val geoIpClient = GeoIpLocationClient()
        // F73: real solar ramp windows. D-103: cache location for cold-start reuse. D-121: geo-IP fallback.
        val circadianWindows = CircadianWindowProvider(
            scope = scope,
            overrideFlow = experimentPrefs.dateLocation,
            location = AndroidLocationReader(appContext),
            geoIpFallback = { if (experimentPrefs.geoIpEnabled.first()) geoIpClient.resolve() else null },
            loadCachedLocation = { experimentPrefs.readCachedSunLocation() },
            persistLocation = { lat, lon, day -> experimentPrefs.writeCachedSunLocation(lat, lon, day) },
            loadGeoIpAttemptDay = { experimentPrefs.readGeoIpAttemptDay() },
            persistGeoIpAttemptDay = { day -> experimentPrefs.writeGeoIpAttemptDay(day) },
        )

        val controller = BrightnessPipelineController(
            lightSensor = AndroidLightSensorSource(appContext),
            brightness = brightness,
            brightnessObserver = AndroidBrightnessObserver(appContext, brightness),
            settingsProvider = { contextEngine.effectiveSettings() },
            scope = scope,
            circadianWindowsProvider = circadianWindows::current,
            dimming = SuperDimmingCoordinator(
                secureDimming = AndroidSecureDimmingController(appContext, privilegeManager),
                // DB-012: coordinator re-detects tier when dimming and caches expired (rate-limited).
                tierProvider = { privilegeManager.currentTier() },
                refreshTier = { privilegeManager.refresh() },
                debugSink = debugSink,
            ),
            debugSink = debugSink,
            // G2R-F13: persist captured override points.
            overrideSink = { lux, brightness -> overridePointStore.record(lux, brightness) },
            // prof759/task545: proximity damps smoothing alpha ×0.1.
            proximitySource = AndroidProximitySensorSource(appContext),
        )
        controllerHook.hook = controller
        // D-110: recompute when circadian location resolves late.
        circadianWindows.onWindowsRefreshed = { controller.reapply() }

        // D-116: prof769/task528 panic (D-116); DB-009/DB-011: plugged detection.
        val panicSensor = AndroidPanicSensorSource(
            context = appContext,
            sensitivity = { (contextEngine.effectiveSnapshot ?: AabSettings()).panicSensitivity },
            isNear = { controller.state.value.proximityNear },
            requiresPlugged = { contextEngine.effectiveSnapshot?.panicRequiresPlugged },
        )

        // D-151: display-toggle profile fields applied on profile change.
        val displayToggles = DisplayTogglesCoordinator(
            effectiveFlow = contextEngine.effectiveFlow,
            baselineFlow = appContext.settingsDataStore.data,
            display = AndroidSecureDisplayController(appContext, privilegeManager),
            tierProvider = { privilegeManager.currentTier() },
            // D-154: circadian-ramp Kelvin with real solar windows or TimeContext defaults (F73).
            circadianTemperature = { s ->
                val nowSecOfDay = ((System.currentTimeMillis() / 1000L) % 86_400L).toDouble()
                val w = circadianWindows.current(s.scaleTransitionFactor.toDouble())
                val defaults = TimeContext(secondsOfDay = nowSecOfDay)
                val modifier = DynamicScaleEngine.compute(
                    DynamicScaleInput(
                        nowSecOfDay = nowSecOfDay,
                        morningStart = w?.morningStart ?: defaults.morningStart,
                        morningEnd = w?.morningEnd ?: defaults.morningEnd,
                        eveningStart = w?.eveningStart ?: defaults.eveningStart,
                        eveningEnd = w?.eveningEnd ?: defaults.eveningEnd,
                        sunlightDurationMinutes = w?.sunlightDurationMinutes
                            ?: defaults.sunlightDurationMinutes,
                        isPolar = w?.isPolar ?: false,
                        steepness = s.scaleSteepness.toDouble(),
                    ),
                ).modifier
                NightLightTemperatureRamp.temperature(
                    modifier = modifier,
                    nightKelvin = s.nightLightTemperature
                        ?: SecureDisplayController.NIGHT_LIGHT_DEFAULT_K,
                    dayKelvin = SecureDisplayController.NIGHT_LIGHT_MAX_K,
                )
            },
        )

        return RuntimeGraph(controller, contextEngine, panicSensor, privilegeManager, displayToggles)
    }
}

/** Composed runtime: pipeline + context engine + panic source. */
class RuntimeGraph(
    val controller: BrightnessPipelineController,
    val contextEngine: ContextEngine,
    val panicSensor: PanicSensorSource,
    val privilegeManager: PrivilegeManager,
    // D-151: display-toggle profile fields.
    val displayToggles: DisplayTogglesCoordinator,
) {
    val activeContext: StateFlow<String?> = contextEngine.activeContext
}
