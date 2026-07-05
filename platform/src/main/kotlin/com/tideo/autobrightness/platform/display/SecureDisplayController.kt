package com.tideo.autobrightness.platform.display

import android.content.ContentResolver
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.tideo.autobrightness.platform.privilege.PrivilegeManager
import com.tideo.autobrightness.platform.privilege.Tier

/**
 * Privileged Display Control (rebuild-only feature, no Tasker source — D-149, reworked into
 * profile fields by D-151/D-152): read/write the AOSP display toggles that
 * `WRITE_SECURE_SETTINGS` unlocks. All keys are the stock AOSP ones, observed live by their
 * framework services (ColorDisplayService watches the night-display and accessibility keys);
 * OEM skins that relocate a key simply see a no-op write — documented device variance, never
 * an alternate code path (same policy as the Extra-Dim keys, D-048).
 *
 * Deliberately NOT here: `reduce_bright_colors_*` (Extra Dim) — owned by the super-dimming
 * pipeline ([com.tideo.autobrightness.platform.brightness.SecureDimmingController], D-144).
 *
 * Reads need no privilege (secure settings are world-readable); writes are ELEVATED-gated and
 * `runCatching`-wrapped because a revoked/stale grant can still throw at write time.
 */
interface SecureDisplayController {
    /** Night Light master switch (`night_display_activated`). */
    fun readNightLight(): Boolean
    fun setNightLight(on: Boolean): Result<Unit>

    /** Night Light intensity in Kelvin (`night_display_color_temperature`); null = never set
     *  (device default in effect). Lower Kelvin = warmer/stronger filter. */
    fun readNightLightTemperature(): Int?
    fun setNightLightTemperature(kelvin: Int): Result<Unit>

    /** Night Light schedule (`night_display_auto_mode`) — read-only v1: the page shows a caveat
     *  when a schedule is active, because the system may re-flip the master switch. */
    fun readNightLightAutoMode(): NightLightAutoMode

    /** Color-correction mode (daltonizer). [DaltonizerMode.GRAYSCALE] is "black & white". */
    fun readDaltonizer(): DaltonizerMode
    fun setDaltonizer(mode: DaltonizerMode): Result<Unit>

    /** Color inversion (`accessibility_display_inversion_enabled`). */
    fun readInversion(): Boolean
    fun setInversion(on: Boolean): Result<Unit>

    /** Always-on display (`doze_always_on`). */
    fun readAlwaysOnDisplay(): Boolean
    fun setAlwaysOnDisplay(on: Boolean): Result<Unit>

    /** Keep the screen on while charging (`Settings.Global.STAY_ON_WHILE_PLUGGED_IN`). */
    fun readStayAwakePlugged(): Boolean
    fun setStayAwakePlugged(on: Boolean): Result<Unit>

    /** Force-SDR (disable all HDR formats) — experimental, Android 14+ only. Glue-review note:
     *  the OFF path restores "all formats allowed" — a pre-existing PARTIAL disable list (set via
     *  a TV's format picker or adb) is intentionally reset, not preserved (documented v1 scope). */
    val hdrForceSdrAvailable: Boolean
    fun readHdrForceSdr(): Boolean
    fun setHdrForceSdr(on: Boolean): Result<Unit>
}

/** `night_display_auto_mode` values (AOSP ColorDisplayService). */
enum class NightLightAutoMode(val value: Int) {
    MANUAL(0), CUSTOM_SCHEDULE(1), TWILIGHT(2);

    companion object {
        fun fromValue(value: Int): NightLightAutoMode =
            entries.firstOrNull { it.value == value } ?: MANUAL
    }
}

/**
 * `accessibility_display_daltonizer` values (AOSP AccessibilityShaderConstants): 0 = monochromacy
 * (grayscale), 11/12/13 = protanomaly/deuteranomaly/tritanomaly correction. OFF = the
 * `…_daltonizer_enabled` flag is 0 (the mode value is preserved, like the system Settings app).
 */
enum class DaltonizerMode(val value: Int) {
    OFF(-1), GRAYSCALE(0), PROTANOMALY(11), DEUTERANOMALY(12), TRITANOMALY(13);

    companion object {
        /** null for values this enum doesn't model (OEM extras) — callers decide the fallback. */
        fun fromValue(value: Int): DaltonizerMode? = entries.firstOrNull { it.value == value }
    }
}

class AndroidSecureDisplayController(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    /** Injectable for tests; production uses the real SDK level. */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : SecureDisplayController {
    private val resolver: ContentResolver get() = context.contentResolver

    private inline fun elevatedWrite(crossinline write: () -> Unit): Result<Unit> {
        if (privilegeManager.currentTier() < Tier.ELEVATED) {
            return Result.failure(SecurityException("WRITE_SECURE_SETTINGS not granted"))
        }
        return runCatching { write() }
    }

    // --- Night Light -----------------------------------------------------------------------

    override fun readNightLight(): Boolean =
        Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_ACTIVATED, 0) == 1

    override fun setNightLight(on: Boolean): Result<Unit> = elevatedWrite {
        Settings.Secure.putInt(resolver, KEY_NIGHT_DISPLAY_ACTIVATED, if (on) 1 else 0)
    }

    override fun readNightLightTemperature(): Int? {
        // getInt-with-sentinel rather than getString: works both on device (putInt stores a
        // stringified int) and under Robolectric's typed-value settings shadow.
        val kelvin = Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_TEMPERATURE, Int.MIN_VALUE)
        return if (kelvin == Int.MIN_VALUE) null else kelvin
    }

    override fun setNightLightTemperature(kelvin: Int): Result<Unit> = elevatedWrite {
        // Sanity band only — the device's real min/max live in its framework config
        // (config_nightDisplayColorTemperature*); ColorDisplayService clamps what it applies.
        Settings.Secure.putInt(resolver, KEY_NIGHT_DISPLAY_TEMPERATURE, kelvin.coerceIn(1_000, 10_000))
    }

    override fun readNightLightAutoMode(): NightLightAutoMode =
        NightLightAutoMode.fromValue(Settings.Secure.getInt(resolver, KEY_NIGHT_DISPLAY_AUTO_MODE, 0))

    // --- Daltonizer / inversion --------------------------------------------------------------

    override fun readDaltonizer(): DaltonizerMode {
        val enabled = Settings.Secure.getInt(resolver, KEY_DALTONIZER_ENABLED, 0) == 1
        if (!enabled) return DaltonizerMode.OFF
        val value = Settings.Secure.getInt(resolver, KEY_DALTONIZER_VALUE, DaltonizerMode.GRAYSCALE.value)
        // Enabled with an unrecognized matrix value (OEM extra): surface as OFF so the UI never
        // claims a mode it can't represent; a subsequent explicit set writes a known value.
        return DaltonizerMode.fromValue(value)?.takeIf { it != DaltonizerMode.OFF } ?: DaltonizerMode.OFF
    }

    override fun setDaltonizer(mode: DaltonizerMode): Result<Unit> = elevatedWrite {
        if (mode == DaltonizerMode.OFF) {
            // Disable the shader but preserve the last mode value — same as the system Settings UI.
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 0)
        } else {
            // Value BEFORE enabled: enabling first would flash the previous matrix for one frame.
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_VALUE, mode.value)
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 1)
        }
    }

    override fun readInversion(): Boolean =
        Settings.Secure.getInt(resolver, KEY_INVERSION_ENABLED, 0) == 1

    override fun setInversion(on: Boolean): Result<Unit> = elevatedWrite {
        Settings.Secure.putInt(resolver, KEY_INVERSION_ENABLED, if (on) 1 else 0)
    }

    // --- Screen ------------------------------------------------------------------------------

    override fun readAlwaysOnDisplay(): Boolean =
        Settings.Secure.getInt(resolver, KEY_DOZE_ALWAYS_ON, 0) == 1

    override fun setAlwaysOnDisplay(on: Boolean): Result<Unit> = elevatedWrite {
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

    // --- HDR force-SDR (experimental) ---------------------------------------------------------

    override val hdrForceSdrAvailable: Boolean
        get() = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    override fun readHdrForceSdr(): Boolean {
        if (!hdrForceSdrAvailable) return false
        val enforced = Settings.Global.getInt(resolver, KEY_HDR_FORMATS_ALLOWED, 1) == 0
        val formats = Settings.Global.getString(resolver, KEY_HDR_DISABLED_FORMATS).orEmpty()
        return enforced && formats.isNotBlank()
    }

    override fun setHdrForceSdr(on: Boolean): Result<Unit> {
        if (!hdrForceSdrAvailable) {
            return Result.failure(UnsupportedOperationException("HDR format control needs Android 14+"))
        }
        return elevatedWrite {
            if (on) {
                // Disable-list BEFORE flipping enforcement on, so enforcement never sees an empty list.
                Settings.Global.putString(resolver, KEY_HDR_DISABLED_FORMATS, ALL_HDR_FORMATS)
                Settings.Global.putInt(resolver, KEY_HDR_FORMATS_ALLOWED, 0)
            } else {
                // Enforcement off BEFORE clearing the list (mirror-image ordering).
                Settings.Global.putInt(resolver, KEY_HDR_FORMATS_ALLOWED, 1)
                Settings.Global.putString(resolver, KEY_HDR_DISABLED_FORMATS, "")
            }
        }
    }

    private companion object {
        // AOSP ColorDisplayService keys (observed live by its SettingsObserver).
        const val KEY_NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
        const val KEY_NIGHT_DISPLAY_TEMPERATURE = "night_display_color_temperature"
        const val KEY_NIGHT_DISPLAY_AUTO_MODE = "night_display_auto_mode"

        // AOSP accessibility keys.
        const val KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_DALTONIZER_VALUE = "accessibility_display_daltonizer"
        const val KEY_INVERSION_ENABLED = "accessibility_display_inversion_enabled"

        // AOSP Always-on-display key (AmbientDisplayConfiguration).
        const val KEY_DOZE_ALWAYS_ON = "doze_always_on"

        /** BatteryManager.BATTERY_PLUGGED_AC | USB | WIRELESS — every common charger type. */
        const val STAY_ON_ANY_CHARGER = BatteryManager.BATTERY_PLUGGED_AC or
            BatteryManager.BATTERY_PLUGGED_USB or BatteryManager.BATTERY_PLUGGED_WIRELESS

        // Android 14+ user-disabled HDR formats (DisplayManagerService). The CSV values are the
        // public Display.HdrCapabilities.HDR_TYPE_* constants: 1=Dolby Vision, 2=HDR10, 3=HLG,
        // 4=HDR10+. Enforced when `are_user_disabled_hdr_formats_allowed` == 0. May need a
        // screen re-init (or reboot) on some devices — flagged Experimental in the UI.
        const val KEY_HDR_DISABLED_FORMATS = "user_disabled_hdr_formats"
        const val KEY_HDR_FORMATS_ALLOWED = "are_user_disabled_hdr_formats_allowed"
        const val ALL_HDR_FORMATS = "1,2,3,4"
    }
}
