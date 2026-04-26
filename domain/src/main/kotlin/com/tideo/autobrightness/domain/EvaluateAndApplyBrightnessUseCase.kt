package com.tideo.autobrightness.domain

data class BrightnessEvaluationResult(
    val target: Int? = null,
    val sampledLux: Float? = null,
    val applied: Boolean = false,
)

class EvaluateAndApplyBrightnessUseCase(
    private val luxSource: AmbientLuxSource,
    private val contextProvider: ContextProvider,
    private val permissionStateProvider: PermissionStateProvider,
    private val engine: BrightnessPolicyEngine,
    private val brightnessApplier: BrightnessApplier,
    private val overlayController: OverlayController,
) {
    fun run(): BrightnessEvaluationResult {
        if (!permissionStateProvider.canAdjustSystemBrightness()) {
            overlayController.showPausedBanner("Missing system write permission")
            return BrightnessEvaluationResult(applied = false)
        }

        val sampledLux = luxSource.currentLux()
        val target = engine.computeTarget(
            lux = sampledLux,
            context = contextProvider.currentContext(),
        )

        brightnessApplier.apply(target)
        overlayController.hidePausedBanner()
        return BrightnessEvaluationResult(target = target, sampledLux = sampledLux, applied = true)
    }
}
