package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController

/**
 * prof769/task528 panic EFFECT (S12.9e: extracted from BrightnessPipelineController) — restore a sane
 * brightness and drop super dimming. The job teardown + `%AAB_Service=Off` state reset stay in the
 * orchestrator's `emergencyStop()` (they are pipeline-lifecycle concerns); this owns only the device
 * effect so it is independently testable.
 */
class PanicHandler(
    private val brightness: ScreenBrightnessController,
    private val dimming: DimmingCoordinator,
) {
    /** task528 act6-8: force manual mode, write 255, restore mode, disable super dimming. */
    fun execute() {
        brightness.forceManualMode()
        brightness.write(PANIC_BRIGHTNESS) // task528 act6: Set Display Brightness 255
        brightness.restoreMode()
        dimming.disengage() // task528 act7/8: Disable Super Dimming
    }

    companion object {
        const val PANIC_BRIGHTNESS = 255
    }
}
