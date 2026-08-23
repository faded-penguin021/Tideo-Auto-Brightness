package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode

/**
 * DB-034: what the seven Privileged Display keys actually read as on the device, so the screen can
 * show the device instead of asserting the stored profile. A null is "no value this app can state":
 * feature unavailable ([nightLight], [alwaysOn]), device holds something unrepresentable
 * ([daltonizer] DB-066, [hdrForceSdr] DB-049, [stayAwake] DB-077), or no explicit
 * value ([temperatureK]).
 */
data class DeviceDisplaySnapshot(
    val nightLight: Boolean?,
    val temperatureK: Int?,
    val daltonizer: DaltonizerMode?,
    val inversion: Boolean,
    val alwaysOn: Boolean?,
    val stayAwake: Boolean?,
    val hdrForceSdr: Boolean?,
)

/**
 * DB-034: merge a device read-back into a draft. Two fields are deliberately not device-sourced —
 * `nightLightCircadianEnabled` has no Android counterpart, and while it is on the coordinator's
 * ticker owns the temperature key, so reading it would freeze one ramp sample into a static value.
 */
fun AabSettings.withDeviceSnapshot(snapshot: DeviceDisplaySnapshot): AabSettings = copy(
    // DB-042: hidden, unsupported fields must not be erased by read-back.
    nightLightEnabled = snapshot.nightLight ?: nightLightEnabled,
    nightLightTemperature = if (snapshot.nightLight == null || nightLightCircadianEnabled) {
        nightLightTemperature
    } else {
        snapshot.temperatureK
    },
    daltonizerMode = snapshot.daltonizer?.name ?: daltonizerMode,
    inversionEnabled = snapshot.inversion,
    alwaysOnDisplayEnabled = snapshot.alwaysOn ?: alwaysOnDisplayEnabled,
    stayAwakeChargingEnabled = snapshot.stayAwake ?: stayAwakeChargingEnabled,
    hdrForceSdrEnabled = snapshot.hdrForceSdr ?: hdrForceSdrEnabled,
)

/**
 * DB-040: the eight fields the read-back owns. Comparisons must be scoped to these — the draft also
 * carries global fields (`serviceEnabled`, `contextOverride`, `debugLevel`, `panicSensitivity`, …)
 * that `DraftSettingsViewModel`'s collector rewrites from DataStore whenever the service, a context
 * rule or the QS tile moves them. Whole-object equality reads those writes as a user edit.
 */
private fun AabSettings.displayFieldsEqual(other: AabSettings): Boolean =
    nightLightEnabled == other.nightLightEnabled &&
        nightLightTemperature == other.nightLightTemperature &&
        nightLightCircadianEnabled == other.nightLightCircadianEnabled &&
        daltonizerMode == other.daltonizerMode &&
        inversionEnabled == other.inversionEnabled &&
        alwaysOnDisplayEnabled == other.alwaysOnDisplayEnabled &&
        stayAwakeChargingEnabled == other.stayAwakeChargingEnabled &&
        hdrForceSdrEnabled == other.hdrForceSdrEnabled

/**
 * DB-039/DB-040: the draft to show for [snapshot], or null to leave it alone. Gating on "draft
 * differs from the profile" cannot work — the read-back's own write makes it differ, so the guard
 * fires once and refuses every later device change. What must block a re-merge is a USER edit of a
 * display field, which is [lastMerged]: the draft this returned last time. Matching the profile
 * covers the first read-back and a Discard.
 */
fun readBackDraft(
    draft: AabSettings,
    committed: AabSettings,
    lastMerged: AabSettings?,
    snapshot: DeviceDisplaySnapshot,
): AabSettings? {
    val untouched = draft.displayFieldsEqual(committed) ||
        (lastMerged != null && draft.displayFieldsEqual(lastMerged))
    if (!untouched) return null
    return draft.withDeviceSnapshot(snapshot).takeIf { it != draft }
}
