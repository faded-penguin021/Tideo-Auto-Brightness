package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.provider.Settings

// Tasker: task696/698 write Settings.System.SCREEN_BRIGHTNESS; task554 reads it back.
// OEM normalization: domain always 0–255 (Tasker parity); device range may differ.
interface ScreenBrightnessController {
    fun read(): Int
    fun write(level: Int)
    fun forceManualMode()
    fun restoreMode()
    /**
     * True if the raw device-scale value equals the most recent [write] from this process.
     * Tasker: task567 compares the observed brightness against %LastAAB (the last value the
     * pipeline wrote) — equality means self-write, mismatch means manual override. Matching
     * the LATEST write (rather than consuming per-write tokens) is required because
     * ContentObserver.onChange re-reads the CURRENT setting: during an N-frame animation a
     * delayed callback for frame N observes frame N+1's value.
     */
    fun isSelfWrite(rawDeviceValue: Int): Boolean
    /**
     * True if the value CURRENTLY on screen is our most recent self-write. Compares in DEVICE space
     * (the raw setting value vs the last written device value), so it is immune to the
     * domain↔device round-trip drift that [read] incurs when `config_screenBrightnessSettingMaximum
     * != 255` (D-049 #4): `read()` is `toDomain(toDevice(x))`, which is NOT identity on such OEM
     * ranges, so an animation read-back comparing domain values would spuriously fire OVERRIDDEN on
     * every multi-frame transition. The animation runner uses this instead of a domain comparison.
     */
    fun isOnScreenSelfWrite(): Boolean
    /** Forget the self-write marker (e.g. when auto-control pauses and the user owns the slider). */
    fun clearSelfWriteMarker()
}

class AndroidScreenBrightnessController(
    private val context: Context,
    deviceMaxOverride: Int? = null,
) : ScreenBrightnessController {
    private val resolver: ContentResolver get() = context.contentResolver

    // config_screenBrightnessSettingMaximum is an internal integer resource on AOSP/OEM ROMs.
    // Returns 0 if absent → fallback to 255 (standard scale, Tasker parity).
    private val deviceMax: Int by lazy {
        deviceMaxOverride ?: run {
            val id = Resources.getSystem().getIdentifier(
                "config_screenBrightnessSettingMaximum", "integer", "android"
            )
            if (id != 0) Resources.getSystem().getInteger(id).takeIf { it > 0 } ?: 255 else 255
        }
    }

    // Null until forceManualMode saves the user's mode; null again after restoreMode.
    // Guarding on null keeps repeated forceManualMode calls from overwriting the saved
    // AUTOMATIC mode with MANUAL.
    private var savedMode: Int? = null

    @Volatile
    private var lastSelfWriteDevice: Int? = null

    // Math.round both directions so the round-trip write(x) → read() is identity for all
    // 0–255 on any deviceMax ≥ 255 (truncation drifted by −1 per conversion).
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
        // Settings.System.putInt throws SecurityException without WRITE_SETTINGS. Swallow only that
        // so an unprivileged install degrades (no brightness change) instead of crashing the pipeline
        // coroutine / process (Gate 1 G1-F1). The marker is only updated on a successful write.
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
            if (savedMode == null) {
                savedMode = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            }
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }.exceptionOrNull()?.let { if (it !is SecurityException) throw it }
    }

    override fun restoreMode() {
        savedMode?.let {
            runCatching {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it)
            }.exceptionOrNull()?.let { e -> if (e !is SecurityException) throw e }
        }
        savedMode = null
    }

    override fun isSelfWrite(rawDeviceValue: Int): Boolean = rawDeviceValue == lastSelfWriteDevice

    override fun isOnScreenSelfWrite(): Boolean {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        return raw >= 0 && raw == lastSelfWriteDevice
    }

    override fun clearSelfWriteMarker() {
        lastSelfWriteDevice = null
    }
}
