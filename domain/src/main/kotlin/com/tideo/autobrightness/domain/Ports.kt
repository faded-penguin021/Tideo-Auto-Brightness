package com.tideo.autobrightness.domain

interface AmbientLuxSource {
    fun currentLux(): Float
}

interface BrightnessApplier {
    fun apply(level: Int)
}

interface ContextProvider {
    fun currentContext(): UserContext
}

interface PermissionStateProvider {
    fun canAdjustSystemBrightness(): Boolean
    fun canDrawOverlay(): Boolean
    fun canReadUsageStats(): Boolean
}

interface OverlayController {
    fun showPausedBanner(reason: String)
    fun hidePausedBanner()
}

data class UserContext(
    val packageName: String? = null,
    val isCharging: Boolean = false,
    val hourOfDay: Int,
    val isAtHomeWifi: Boolean = false,
)
