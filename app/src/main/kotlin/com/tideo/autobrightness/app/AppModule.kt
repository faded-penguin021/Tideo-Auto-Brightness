package com.tideo.autobrightness.app

import android.content.Context
import com.tideo.autobrightness.app.runtime.BrightnessPipelineController
import com.tideo.autobrightness.app.runtime.SuperDimmingCoordinator
import com.tideo.autobrightness.app.storage.settingsDataStore
import com.tideo.autobrightness.platform.brightness.AndroidScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.AndroidSecureDimmingController
import com.tideo.autobrightness.platform.observe.AndroidBrightnessObserver
import com.tideo.autobrightness.platform.privilege.AndroidPrivilegeManager
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.sensor.AndroidLightSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/**
 * Manual DI composition root for the runtime graph (rebuilt in S9b — the legacy toy use-case graph
 * is gone). Composes the real S7 platform adapters + S9a pipeline + S9b super-dimming layer.
 * Replace with Hilt/Koin later without touching domain contracts.
 */
class AppModule(context: Context) {
    private val appContext = context.applicationContext

    val privilegeManager: PrivilegeManager = AndroidPrivilegeManager(appContext)

    /**
     * Build a fresh pipeline controller for a service lifetime. The brightness writer and observer
     * SHARE one instance so the suppress-echo marker is per-instance (D-034: the observer must see
     * exactly the writer's last value).
     */
    fun createController(scope: CoroutineScope): BrightnessPipelineController {
        val brightness = AndroidScreenBrightnessController(appContext)
        return BrightnessPipelineController(
            lightSensor = AndroidLightSensorSource(appContext),
            brightness = brightness,
            brightnessObserver = AndroidBrightnessObserver(appContext, brightness),
            settingsProvider = { appContext.settingsDataStore.data.first() },
            scope = scope,
            dimming = SuperDimmingCoordinator(
                secureDimming = AndroidSecureDimmingController(appContext, privilegeManager),
                tierProvider = privilegeManager::currentTier,
            ),
        )
    }
}
