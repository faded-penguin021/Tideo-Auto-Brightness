package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController

/** task528 panic EFFECT (S12.9e): restore sane brightness, drop super dimming (device effect, independently testable). */
class PanicHandler(
    private val brightness: ScreenBrightnessController,
    private val dimming: DimmingCoordinator,
) {
    fun execute() {
        // Each operation independent, best-effort (DA-038): failure doesn't block later screen-safety ops.
        runCatching { brightness.forceManualMode() }
        runCatching { brightness.write(PANIC_BRIGHTNESS) }
        runCatching { brightness.restoreMode() }
        runCatching { dimming.disengage() }
    }

    companion object {
        const val PANIC_BRIGHTNESS = 255
    }
}
