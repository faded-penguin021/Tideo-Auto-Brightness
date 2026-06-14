package com.tideo.autobrightness.app

import android.content.Context
import com.tideo.autobrightness.app.runtime.AndroidContextSignalSource
import com.tideo.autobrightness.app.runtime.AppProfileCatalog
import com.tideo.autobrightness.app.runtime.BrightnessPipelineController
import com.tideo.autobrightness.app.runtime.ContextEngine
import com.tideo.autobrightness.app.runtime.DebugSink
import com.tideo.autobrightness.app.runtime.SuperDimmingCoordinator
import com.tideo.autobrightness.app.runtime.ToastDebugSink
import com.tideo.autobrightness.app.settings.ContextRuleStore
import com.tideo.autobrightness.app.storage.contextRulesDataStore
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.brightness.AndroidScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.AndroidSecureDimmingController
import com.tideo.autobrightness.platform.observe.AndroidBrightnessObserver
import com.tideo.autobrightness.platform.privilege.AndroidPrivilegeManager
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.sensor.AndroidLightSensorSource
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

        lateinit var controller: BrightnessPipelineController
        val contextEngine = ContextEngine(
            rulesProvider = { contextRuleStore.rules() },
            baselineProvider = { appContext.settingsDataStore.data.first() },
            profileCatalog = AppProfileCatalog,
            signalSource = AndroidContextSignalSource(appContext),
            onProfileChanged = { controller.onContextChanged() },
            debugSink = debugSink,
        )

        controller = BrightnessPipelineController(
            lightSensor = AndroidLightSensorSource(appContext),
            brightness = brightness,
            brightnessObserver = AndroidBrightnessObserver(appContext, brightness),
            // Effective settings = active context profile merged over the baseline, or the baseline.
            settingsProvider = { contextEngine.effectiveSettings() },
            scope = scope,
            dimming = SuperDimmingCoordinator(
                secureDimming = AndroidSecureDimmingController(appContext, privilegeManager),
                // Re-detect each cycle so a WRITE_SECURE_SETTINGS grant made AFTER the service
                // started (Shizuku/ADB) is picked up without a restart (G1-F5).
                tierProvider = { privilegeManager.refresh(); privilegeManager.currentTier() },
                debugSink = debugSink,
            ),
            debugSink = debugSink,
        )

        return RuntimeGraph(controller, contextEngine)
    }
}

/**
 * The composed runtime: the brightness pipeline + the context engine that feeds it. The service
 * drives both lifecycles together and reads [activeContext] for the notification's context line.
 */
class RuntimeGraph(
    val controller: BrightnessPipelineController,
    val contextEngine: ContextEngine,
) {
    val activeContext: StateFlow<String?> = contextEngine.activeContext
}
