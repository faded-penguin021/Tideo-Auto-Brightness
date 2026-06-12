package com.tideo.autobrightness.platform

import com.tideo.autobrightness.domain.AmbientLuxSource
import com.tideo.autobrightness.domain.BrightnessApplier
import com.tideo.autobrightness.domain.ContextProvider
import com.tideo.autobrightness.domain.OverlayController
import com.tideo.autobrightness.domain.PermissionStateProvider
import com.tideo.autobrightness.domain.UserContext
import kotlin.random.Random

@Deprecated("S9b removes — replaced by AndroidLightSensorSource")
class FakeAmbientLuxSensorAdapter : AmbientLuxSource {
    override fun currentLux(): Float = Random.nextDouble(from = 1.0, until = 180.0).toFloat()
}

@Deprecated("S9b removes — replaced by AndroidScreenBrightnessController")
class SystemBrightnessAdapter : BrightnessApplier {
    override fun apply(level: Int) {
        println("[platform] Applying brightness=$level")
    }
}

@Deprecated("S9b removes — replaced by AndroidForegroundAppMonitor + context readers")
class UsageStatsContextAdapter : ContextProvider {
    override fun currentContext(): UserContext {
        return UserContext(
            packageName = "com.example.reader",
            isCharging = false,
            hourOfDay = 21,
            isAtHomeWifi = true,
        )
    }
}

@Deprecated("S9b removes — replaced by AndroidPrivilegeManager")
class AndroidPermissionStateAdapter : PermissionStateProvider {
    override fun canAdjustSystemBrightness(): Boolean = true
    override fun canDrawOverlay(): Boolean = true
    override fun canReadUsageStats(): Boolean = true
}

@Deprecated("S9b removes — no direct replacement (overlay UI not ported)")
class TaskerOverlayControllerAdapter : OverlayController {
    override fun showPausedBanner(reason: String) {
        println("[platform] Overlay ON: $reason")
    }

    override fun hidePausedBanner() {
        println("[platform] Overlay OFF")
    }
}
