package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import java.util.concurrent.CopyOnWriteArraySet

// Tasker: task696/698 write Settings.System.SCREEN_BRIGHTNESS; task554 reads it back.
// OEM normalization: domain always 0–255 (Tasker parity); device range may differ.
interface ScreenBrightnessController {
    fun read(): Int
    fun write(level: Int)
    fun forceManualMode()
    fun restoreMode()
    /** Mark a domain-scale value as a pending self-write so BrightnessObserver can filter it. */
    fun registerExpectedWrite(domainLevel: Int)
    fun clearExpectedWrites()
    /** True and removes one occurrence if the raw device-scale value is a registered self-write. */
    fun isSelfWrite(rawDeviceValue: Int): Boolean
}

class AndroidScreenBrightnessController(private val context: Context) : ScreenBrightnessController {
    private val resolver: ContentResolver get() = context.contentResolver

    // config_screenBrightnessSettingMaximum is an internal integer resource on AOSP/OEM ROMs.
    // Returns 0 if absent → fallback to 255 (standard scale, Tasker parity).
    private val deviceMax: Int by lazy {
        val id = Resources.getSystem().getIdentifier(
            "config_screenBrightnessSettingMaximum", "integer", "android"
        )
        if (id != 0) Resources.getSystem().getInteger(id).takeIf { it > 0 } ?: 255 else 255
    }

    private var savedMode: Int = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

    // Stored as device-scale to match what ContentObserver delivers.
    private val expectedWrites = CopyOnWriteArraySet<Int>()

    private fun toDevice(domainLevel: Int): Int =
        if (deviceMax == 255) domainLevel
        else ((domainLevel.toDouble() / 255.0) * deviceMax).toInt().coerceIn(0, deviceMax)

    private fun toDomain(deviceLevel: Int): Int =
        if (deviceMax == 255) deviceLevel
        else ((deviceLevel.toDouble() / deviceMax.toDouble()) * 255).toInt().coerceIn(0, 255)

    override fun read(): Int {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return toDomain(raw)
    }

    override fun write(level: Int) {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, toDevice(level))
    }

    override fun forceManualMode() {
        savedMode = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    override fun restoreMode() {
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, savedMode)
    }

    override fun registerExpectedWrite(domainLevel: Int) {
        expectedWrites.add(toDevice(domainLevel))
    }

    override fun clearExpectedWrites() {
        expectedWrites.clear()
    }

    override fun isSelfWrite(rawDeviceValue: Int): Boolean = expectedWrites.remove(rawDeviceValue)
}
