package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.provider.Settings

// Tasker: task696/698 write Settings.System.SCREEN_BRIGHTNESS; task554 reads it back.
// Domain 0–255 (Tasker parity); device range may differ.
interface ScreenBrightnessController {
    fun read(): Int
    fun write(level: Int)
    fun forceManualMode()
    fun restoreMode()
    /** True if raw device value equals last write (task567 self-write vs override). */
    fun isSelfWrite(rawDeviceValue: Int): Boolean
    /** True if on-screen value is last self-write in device space (immune to D-049 round-trip drift). */
    fun isOnScreenSelfWrite(): Boolean
    fun clearSelfWriteMarker()
}

class AndroidScreenBrightnessController(
    private val context: Context,
    deviceMaxOverride: Int? = null,
) : ScreenBrightnessController {
    private val resolver: ContentResolver get() = context.contentResolver

    // Falls back to 255 if absent (standard Tasker parity).
    private val deviceMax: Int by lazy {
        deviceMaxOverride ?: run {
            val id = Resources.getSystem().getIdentifier(
                "config_screenBrightnessSettingMaximum", "integer", "android"
            )
            if (id != 0) Resources.getSystem().getInteger(id).takeIf { it > 0 } ?: 255 else 255
        }
    }

    // D-134: persisted pre-service SCREEN_BRIGHTNESS_MODE survives process death.
    // commit() for durability (not apply()).
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private fun savedMode(): Int? =
        prefs.getInt(KEY_SAVED_MODE, -1).takeIf { it >= 0 }

    @Volatile
    private var lastSelfWriteDevice: Int? = null

    private fun toDevice(domainLevel: Int): Int {
        val clamped = domainLevel.coerceIn(0, 255)
        return if (deviceMax == 255) clamped
        else Math.round(clamped.toDouble() / 255.0 * deviceMax).toInt()
    }

    private fun toDomain(deviceLevel: Int): Int {
        val clamped = deviceLevel.coerceIn(0, deviceMax)
        return if (deviceMax == 255) clamped
        else Math.round(clamped.toDouble() / deviceMax * 255.0).toInt()
    }

    override fun read(): Int {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return toDomain(raw)
    }

    override fun write(level: Int) {
        val device = toDevice(level)
        // Swallow SecurityException for unprivileged installs (no crash); update marker on success.
        when (val error = runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, device)
        }.exceptionOrNull()) {
            null -> lastSelfWriteDevice = device
            is SecurityException -> Unit
            else -> throw error
        }
    }

    override fun forceManualMode() {
        runCatching {
            val current = Settings.System.getInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            // MANUAL is ambiguous (may be our residue); any non-MANUAL current mode is unambiguously user's.
            if (current != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL || savedMode() == null) {
                prefs.edit().putInt(KEY_SAVED_MODE, current).commit()
            }
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }.exceptionOrNull()?.let { if (it !is SecurityException) throw it }
    }

    override fun restoreMode() {
        savedMode()?.let {
            runCatching {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it)
            }.exceptionOrNull()?.let { e -> if (e !is SecurityException) throw e }
        }
        prefs.edit().remove(KEY_SAVED_MODE).commit()
    }

    override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastSelfWriteDevice

    override fun isOnScreenSelfWrite(): Boolean {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        return raw >= 0 && raw == lastSelfWriteDevice
    }

    override fun clearSelfWriteMarker() {
        lastSelfWriteDevice = null
    }

    companion object {
        internal const val PREFS_NAME = "screen_brightness_controller"
        internal const val KEY_SAVED_MODE = "saved_brightness_mode"
    }
}
