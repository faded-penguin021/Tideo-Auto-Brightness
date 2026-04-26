package com.tideo.autobrightness.domain

class EvaluateAndApplyBrightnessUseCase(
    private val luxSource: AmbientLuxSource,
    private val contextProvider: ContextProvider,
    private val permissionStateProvider: PermissionStateProvider,
    private val engine: BrightnessPolicyEngine,
    private val brightnessApplier: BrightnessApplier,
    private val overlayController: OverlayController,
) {
    fun run(): Int? {
        if (!permissionStateProvider.canAdjustSystemBrightness()) {
            overlayController.showPausedBanner("Missing system write permission")
            return null
        }

        val target = engine.computeTarget(
            lux = luxSource.currentLux(),
            context = contextProvider.currentContext(),
        )

        brightnessApplier.apply(target)
        overlayController.hidePausedBanner()
        return target
    }
}
