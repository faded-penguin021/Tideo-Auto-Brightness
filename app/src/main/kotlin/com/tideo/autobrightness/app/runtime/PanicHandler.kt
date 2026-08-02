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
        // Every operation is best-effort and independent (DA-038). A SettingsProvider/OEM failure
        // in an earlier write must never prevent the later screen-safety operations, especially the
        // Extra Dim OFF attempt. restoreMode also runs even when the brightness write fails.
        runCatching { brightness.forceManualMode() }
        runCatching { brightness.write(PANIC_BRIGHTNESS) } // task528 act6
        runCatching { brightness.restoreMode() }
        runCatching { dimming.disengage() } // task528 act7/8
    }

    companion object {
        const val PANIC_BRIGHTNESS = 255
    }
}
