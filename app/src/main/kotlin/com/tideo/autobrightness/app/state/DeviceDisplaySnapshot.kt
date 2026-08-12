package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode

/**
 * DB-034: what the seven Privileged Display keys actually read as on the device, so the screen can
 * show the device instead of asserting the stored profile. [hdrForceSdr] is null when the key is
 * unavailable (below API 34); [temperatureK] is null when Android holds no explicit value.
 */
data class DeviceDisplaySnapshot(
    val nightLight: Boolean,
    val temperatureK: Int?,
    val daltonizer: DaltonizerMode,
    val inversion: Boolean,
    val alwaysOn: Boolean,
    val stayAwake: Boolean,
    val hdrForceSdr: Boolean?,
)

/**
 * DB-034: merge a device read-back into a draft. Two fields are deliberately not device-sourced —
 * `nightLightCircadianEnabled` has no Android counterpart, and while it is on the coordinator's
 * ticker owns the temperature key, so reading it would freeze one ramp sample into a static value.
 */
fun AabSettings.withDeviceSnapshot(snapshot: DeviceDisplaySnapshot): AabSettings = copy(
    nightLightEnabled = snapshot.nightLight,
    nightLightTemperature = if (nightLightCircadianEnabled) nightLightTemperature else snapshot.temperatureK,
    daltonizerMode = snapshot.daltonizer.name,
    inversionEnabled = snapshot.inversion,
    alwaysOnDisplayEnabled = snapshot.alwaysOn,
    stayAwakeChargingEnabled = snapshot.stayAwake,
    hdrForceSdrEnabled = snapshot.hdrForceSdr ?: hdrForceSdrEnabled,
)
