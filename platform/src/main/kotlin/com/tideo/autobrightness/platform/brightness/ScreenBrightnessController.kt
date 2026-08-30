package com.tideo.autobrightness.platform.brightness

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.provider.Settings

// DC-002: WRITTEN_UNACKNOWLEDGED is not a failure, and DENIED is not REFUSED.
enum class WriteStatus { ACKNOWLEDGED, WRITTEN_UNACKNOWLEDGED, REFUSED, DENIED }

/** One write and what the provider stored; `acknowledged*` non-null only when ACKNOWLEDGED (DC-002). */
data class BrightnessWriteResult(
    val requestedDomain: Int,
    val requestedRaw: Int,
    val acknowledgedRaw: Int?,
    val acknowledgedDomain: Int?,
    val deviceMax: Int,
    val status: WriteStatus,
)

// Tasker: task696/698 write Settings.System.SCREEN_BRIGHTNESS; task554 reads it back.
// Domain 0–255 (Tasker parity); device range may differ.
interface ScreenBrightnessController {
    fun read(): Int
    fun write(level: Int): BrightnessWriteResult
    fun forceManualMode(): Boolean
    fun restoreMode()
    fun isManualMode(): Boolean
    /** True if raw device value equals last write (task567 self-write vs override). */
    fun isSelfWrite(rawDeviceValue: Int): Boolean
    fun clearSelfWriteMarker()
}

class AndroidScreenBrightnessController(
    private val context: Context,
    deviceMaxOverride: Int? = null,
    // Test seam (DC-002): Robolectric stores what it is given; normalization/refusal/read-back failure need one.
    private val rawWrite: ((Int) -> Boolean)? = null,
    private val rawRead: (() -> Int?)? = null,
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

    // DC-002: read on the observer thread; sound only while one write is in flight at a time.
    @Volatile
    private var selfWriteInProgress: Boolean = false

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

    private fun writeRaw(raw: Int): Boolean =
        rawWrite?.invoke(raw) ?: Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)

    // DC-002: the read-back, never read()'s 128 default — a default must not read as acknowledged.
    private fun readRawOrNull(): Int? = runCatching {
        val seam = rawRead
        if (seam != null) seam()
        else Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1).takeIf { it >= 0 }
    }.getOrNull()

    override fun read(): Int = toDomain(readRawOrNull() ?: 128)

    override fun write(level: Int): BrightnessWriteResult {
        val requestedDomain = level.coerceIn(0, 255)
        val requestedRaw = toDevice(level)
        val previous = lastSelfWriteDevice
        // DC-002: arm BEFORE putInt — the echo can be dispatched before the marker would exist.
        selfWriteInProgress = true
        lastSelfWriteDevice = requestedRaw
        var keepMarker = false
        try {
            if (!writeRaw(requestedRaw)) return unlanded(requestedDomain, requestedRaw, WriteStatus.REFUSED)
            val acknowledgedRaw = readRawOrNull() ?: run {
                // DC-002: keep the requested raw — it is the likeliest thing on screen.
                keepMarker = true
                return unlanded(requestedDomain, requestedRaw, WriteStatus.WRITTEN_UNACKNOWLEDGED)
            }
            lastSelfWriteDevice = acknowledgedRaw
            keepMarker = true
            return BrightnessWriteResult(
                requestedDomain = requestedDomain,
                requestedRaw = requestedRaw,
                acknowledgedRaw = acknowledgedRaw,
                acknowledgedDomain = toDomain(acknowledgedRaw),
                deviceMax = deviceMax,
                status = WriteStatus.ACKNOWLEDGED,
            )
        } catch (_: SecurityException) {
            return unlanded(requestedDomain, requestedRaw, WriteStatus.DENIED)
        } finally {
            if (!keepMarker) lastSelfWriteDevice = previous
            selfWriteInProgress = false
        }
    }

    private fun unlanded(requestedDomain: Int, requestedRaw: Int, status: WriteStatus) =
        BrightnessWriteResult(requestedDomain, requestedRaw, null, null, deviceMax, status)

    override fun forceManualMode(): Boolean =
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
        }.getOrElse { if (it is SecurityException) false else throw it }

    override fun isManualMode(): Boolean = runCatching {
        Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    }.getOrDefault(true)

    override fun restoreMode() {
        savedMode()?.let {
            runCatching {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it)
            }.exceptionOrNull()?.let { e -> if (e !is SecurityException) throw e }
        }
        prefs.edit().remove(KEY_SAVED_MODE).commit()
    }

    override fun isSelfWrite(rawDeviceValue: Int): Boolean =
        selfWriteInProgress || rawDeviceValue == lastSelfWriteDevice

    override fun clearSelfWriteMarker() {
        lastSelfWriteDevice = null
    }

    companion object {
        internal const val PREFS_NAME = "screen_brightness_controller"
        internal const val KEY_SAVED_MODE = "saved_brightness_mode"
    }
}
