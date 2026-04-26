package com.tideo.autobrightness.app

import com.tideo.autobrightness.domain.BrightnessPolicyEngine
import com.tideo.autobrightness.domain.EvaluateAndApplyBrightnessUseCase
import com.tideo.autobrightness.platform.AndroidPermissionStateAdapter
import com.tideo.autobrightness.platform.FakeAmbientLuxSensorAdapter
import com.tideo.autobrightness.platform.SystemBrightnessAdapter
import com.tideo.autobrightness.platform.TaskerOverlayControllerAdapter
import com.tideo.autobrightness.platform.UsageStatsContextAdapter

/**
 * Manual DI composition root.
 * Replace with Hilt/Koin later without touching domain contracts.
 */
class AppModule {
    private val luxSource = FakeAmbientLuxSensorAdapter()
    private val contextProvider = UsageStatsContextAdapter()
    private val permissionStateProvider = AndroidPermissionStateAdapter()
    private val brightnessApplier = SystemBrightnessAdapter()
    private val overlayController = TaskerOverlayControllerAdapter()
    private val policyEngine = BrightnessPolicyEngine()

    val evaluateAndApplyBrightnessUseCase = EvaluateAndApplyBrightnessUseCase(
        luxSource = luxSource,
        contextProvider = contextProvider,
        permissionStateProvider = permissionStateProvider,
        engine = policyEngine,
        brightnessApplier = brightnessApplier,
        overlayController = overlayController,
    )
}
