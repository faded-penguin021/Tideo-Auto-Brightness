package com.tideo.autobrightness.platform.display

import android.content.ContentResolver
import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier

/** Privileged Display Control (D-149, reworked into profile fields by D-151/D-152): read/write AOSP
 *  display toggles via WRITE_SECURE_SETTINGS. Extra Dim keys (D-048, D-144) owned elsewhere. Writes
 *  ELEVATED-gated and runCatching-wrapped for revoked/stale grant safety. */
interface SecureDisplayController {
    val nightLightAvailable: Boolean
    fun readNightLight(): Boolean
    fun setNightLight(on: Boolean): Result<Unit>

    /** Night Light intensity Kelvin; null = device default. Lower = warmer. */
    fun readNightLightTemperature(): Int?
    fun setNightLightTemperature(kelvin: Int): Result<Unit>

    fun readNightLightAutoMode(): NightLightAutoMode

    fun readDaltonizer(): DaltonizerMode
    fun setDaltonizer(mode: DaltonizerMode): Result<Unit>

    fun readInversion(): Boolean
    fun setInversion(on: Boolean): Result<Unit>

    val alwaysOnDisplayAvailable: Boolean
    fun readAlwaysOnDisplay(): Boolean
    fun setAlwaysOnDisplay(on: Boolean): Result<Unit>

    fun readStayAwakePlugged(): Boolean
    fun setStayAwakePlugged(on: Boolean): Result<Unit>

    /** Disabled: this requires DisplayManager's service path, not direct Settings mutation. */
    val hdrForceSdrAvailable: Boolean
    fun readHdrForceSdr(): Boolean
    fun setHdrForceSdr(on: Boolean): Result<Unit>

    companion object {
        /** AOSP Night Light Kelvin bounds/default (D-149). Shared by slider UI and D-154 circadian ramp. */
        const val NIGHT_LIGHT_MIN_K = 2596
        const val NIGHT_LIGHT_MAX_K = 4082
        const val NIGHT_LIGHT_DEFAULT_K = 2850
    }
}

enum class NightLightAutoMode(val value: Int) {
    MANUAL(0), CUSTOM_SCHEDULE(1), TWILIGHT(2);

    companion object {
        fun fromValue(value: Int): NightLightAutoMode =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}

/** Daltonizer modes (0=grayscale, 11/12/13=protanomaly/deuteranomaly/tritanomaly). OFF = disabled. */
enum class DaltonizerMode(val value: Int) {
    OFF(-1), GRAYSCALE(0), PROTANOMALY(11), DEUTERANOMALY(12), TRITANOMALY(13);

    companion object {
        /** null for OEM extras; callers decide fallback. */
        fun fromValue(value: Int): DaltonizerMode? = entries.firstOrNull { it.value == value }
    }
}

class AndroidSecureDisplayController(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    override val nightLightAvailable: Boolean = context.frameworkBoolean(
        "config_nightDisplayAvailable",
    ),
    override val alwaysOnDisplayAvailable: Boolean = context.frameworkBoolean(
        "config_dozeAlwaysOnDisplayAvailable",
    ),
) : SecureDisplayController {
    // DB-041: backing rows do not establish display-feature support or a live service update path.
    private val resolver: ContentResolver get() = context.contentResolver

    private inline fun elevatedWrite(crossinline write: () -> Unit): Result<Unit> {
        if (privilegeManager.currentTier() < Tier.ELEVATED) {
            return Result.failure(SecurityException("WRITE_SECURE_SETTINGS not granted"))
        }
        return runCatching { write() }
    }

    override fun readNightLight(): Boolean = nightLightAvailable &&
        Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_ACTIVATED, 0) == 1

    override fun setNightLight(on: Boolean): Result<Unit> = capabilityWrite(nightLightAvailable) {
        Settings.Secure.putInt(resolver, KEY_NIGHT_DISPLAY_ACTIVATED, if (on) 1 else 0)
    }

    override fun readNightLightTemperature(): Int? {
        if (!nightLightAvailable) return null
        val kelvin = Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_TEMPERATURE, Int.MIN_VALUE)
        return if (kelvin == Int.MIN_VALUE) null else kelvin
    }

    override fun setNightLightTemperature(kelvin: Int): Result<Unit> = capabilityWrite(nightLightAvailable) {
        Settings.Secure.putInt(resolver, KEY_NIGHT_DISPLAY_TEMPERATURE, kelvin.coerceIn(1_000, 10_000))
    }

    override fun readNightLightAutoMode(): NightLightAutoMode = if (nightLightAvailable) {
        NightLightAutoMode.fromValue(Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_AUTO_MODE, 0))
    } else {
        NightLightAutoMode.MANUAL
    }

    override fun readDaltonizer(): DaltonizerMode {
        val enabled = Settings.Secure.getInt(resolver, KEY_DALTONIZER_ENABLED, 0) == 1
        if (!enabled) return DaltonizerMode.OFF
        val value = Settings.Secure.getInt(resolver, KEY_DALTONIZER_VALUE, DaltonizerMode.GRAYSCALE.value)
        // Unrecognized matrix (OEM extra): surface as OFF so UI can't claim an unsupported mode.
        return DaltonizerMode.fromValue(value)?.takeIf { it != DaltonizerMode.OFF } ?: DaltonizerMode.OFF
    }

    override fun setDaltonizer(mode: DaltonizerMode): Result<Unit> = elevatedWrite {
        if (mode == DaltonizerMode.OFF) {
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 0)
        } else {
            // Value before enabled to avoid frame flash of previous matrix.
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_VALUE, mode.value)
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 1)
        }
    }

    override fun readInversion(): Boolean =
        Settings.Secure.getInt(resolver, KEY_INVERSION_ENABLED, 0) == 1

    override fun setInversion(on: Boolean): Result<Unit> = elevatedWrite {
        Settings.Secure.putInt(resolver, KEY_INVERSION_ENABLED, if (on) 1 else 0)
    }

    override fun readAlwaysOnDisplay(): Boolean = alwaysOnDisplayAvailable &&
        Settings.Secure.getInt(resolver, KEY_DOZE_ALWAYS_ON, 0) == 1

    override fun setAlwaysOnDisplay(on: Boolean): Result<Unit> = capabilityWrite(alwaysOnDisplayAvailable) {
        Settings.Secure.putInt(resolver, KEY_DOZE_ALWAYS_ON, if (on) 1 else 0)
    }

    override fun readStayAwakePlugged(): Boolean =
        Settings.Global.getInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0

    override fun setStayAwakePlugged(on: Boolean): Result<Unit> = elevatedWrite {
        Settings.Global.putInt(
            resolver,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            if (on) STAY_ON_ANY_CHARGER else 0,
        )
    }

    override val hdrForceSdrAvailable: Boolean = false

    override fun readHdrForceSdr(): Boolean = false

    override fun setHdrForceSdr(on: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Force SDR requires the DisplayManager service API"))

    private companion object {
        const val KEY_NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
        const val KEY_NIGHT_DISPLAY_TEMPERATURE = "night_display_color_temperature"
        const val KEY_NIGHT_DISPLAY_AUTO_MODE = "night_display_auto_mode"

        const val KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_DALTONIZER_VALUE = "accessibility_display_daltonizer"
        const val KEY_INVERSION_ENABLED = "accessibility_display_inversion_enabled"

        const val KEY_DOZE_ALWAYS_ON = "doze_always_on"

        const val STAY_ON_ANY_CHARGER = BatteryManager.BATTERY_PLUGGED_AC or
            BatteryManager.BATTERY_PLUGGED_USB or BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    private inline fun capabilityWrite(available: Boolean, crossinline write: () -> Unit): Result<Unit> =
        if (available) elevatedWrite(write) else Result.success(Unit)
}

private fun Context.frameworkBoolean(name: String): Boolean = runCatching {
    val id = resources.getIdentifier(name, "bool", "android")
    id != 0 && resources.getBoolean(id)
}.getOrDefault(false)
