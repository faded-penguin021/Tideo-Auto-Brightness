package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlin.test.assertEquals
import org.junit.Test

class PanicHandlerTest {
    @Test
    fun providerFailures_doNotShortCircuitLaterSafetyWrites_DA038() {
        val calls = mutableListOf<String>()
        val brightness = object : ScreenBrightnessController {
            override fun read() = 0
            override fun write(level: Int): BrightnessWriteResult { calls += "brightness:$level"; error("provider") }
            override fun forceManualMode(): Boolean { calls += "manual"; error("provider") }
            override fun restoreMode() { calls += "restore"; error("provider") }
            override fun isManualMode() = true
            override fun isSelfWrite(rawDeviceValue: Int) = false
            override fun clearSelfWriteMarker() = Unit
        }
        val dimming = object : DimmingCoordinator {
            override fun apply(targetBrightness: Int, settings: AabSettings, scaleDynamic: Double) = Unit
            override fun disengage() { calls += "dimmingOff" }
        }

        PanicHandler(brightness, dimming).execute()

        assertEquals(listOf("manual", "brightness:255", "restore", "dimmingOff"), calls)
    }
}
